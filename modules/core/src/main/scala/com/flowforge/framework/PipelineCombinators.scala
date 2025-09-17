package com.flowforge.framework

import cats.data.{ Kleisli, NonEmptyList }
import cats.syntax.all._
import com.flowforge.core.algebra.EffectSystem

/**
 * Real, minimal pipeline and combinators built on Kleisli and EffectSystem. Focuses on practical,
 * production-friendly composition without placeholders.
 */
import com.flowforge.core.algebra.EffectSystem
import com.flowforge.core.types.{ ExecutionStatus, PipelineMetrics, PipelineResult }
import java.time.Instant
import scala.concurrent.duration._

final case class Pipeline[F[_], A, B](run: Kleisli[F, A, B], metadata: PipelineMetadata) {
  def name: String                                     = metadata.name
  def stages: List[String]                             = metadata.stages
  def execute(a: A)(implicit F: EffectSystem[F]): F[B] = run(a)

  def executeWithMonitoring(a: A)(implicit F: EffectSystem[F]): F[PipelineResult[B]] = {
    val start = System.currentTimeMillis()
    F.attempt(run(a)).flatMap {
      case Right(out) =>
        val end = System.currentTimeMillis()
        val pr = PipelineResult[B](
          pipelineId = name,
          input = a.toString,
          output = Some(out.toString),
          status = ExecutionStatus.Success,
          startTime = Instant.ofEpochMilli(start),
          endTime = Instant.ofEpochMilli(end),
          duration = (end - start).millis,
          metrics = PipelineMetrics(pipelineName = name, processingTime = (end - start).millis),
        )
        F.pure(pr)
      case Left(e) =>
        val end = System.currentTimeMillis()
        val pr = PipelineResult[B](
          pipelineId = name,
          input = a.toString,
          output = None,
          status = ExecutionStatus.Failed,
          startTime = Instant.ofEpochMilli(start),
          endTime = Instant.ofEpochMilli(end),
          duration = (end - start).millis,
          metrics = PipelineMetrics(pipelineName = name, processingTime = (end - start).millis),
          errors = List(e.getMessage),
        )
        F.pure(pr)
    }
  }
  def map[C](f: B => C)(implicit F: EffectSystem[F]): Pipeline[F, A, C] =
    Pipeline(
      Kleisli(a => F.map(run(a))(f)),
      metadata.copy(transformations = metadata.transformations + 1),
    )

  def flatMap[C](f: B => Kleisli[F, A, C])(implicit F: EffectSystem[F]): Pipeline[F, A, C] =
    Pipeline(
      Kleisli(a => F.flatMap(run(a))(b => f(b)(a))),
      metadata.copy(transformations = metadata.transformations + 1),
    )

  def andThen[C](next: Pipeline[F, B, C])(implicit F: EffectSystem[F]): Pipeline[F, A, C] =
    Pipeline(Kleisli(a => F.flatMap(run(a))(b => next.run(b))), metadata.combine(next.metadata))

  def compose[Z](prev: Pipeline[F, Z, A])(implicit F: EffectSystem[F]): Pipeline[F, Z, B] =
    prev.andThen(this)
}

object Pipeline {
  def lift[F[_]: EffectSystem, A, B](f: A => F[B], name: String = "anonymous"): Pipeline[F, A, B] =
    Pipeline(Kleisli(f), PipelineMetadata.single(name))

  def pure[F[_]: EffectSystem, A, B](f: A => B, name: String = "pure"): Pipeline[F, A, B] =
    Pipeline(Kleisli(a => EffectSystem[F].pure(f(a))), PipelineMetadata.single(name))

  def identity[F[_]: EffectSystem, A]: Pipeline[F, A, A] =
    Pipeline(Kleisli(a => EffectSystem[F].pure(a)), PipelineMetadata.single("identity"))
}

final case class PipelineMetadata(
  name: String,
  stages: List[String] = Nil,
  transformations: Int = 0,
  qualityChecks: Int = 0,
  tags: Map[String, String] = Map.empty) {
  def combine(other: PipelineMetadata): PipelineMetadata =
    PipelineMetadata(
      name = s"$name >> ${other.name}",
      stages = stages ++ other.stages,
      transformations = transformations + other.transformations,
      qualityChecks = qualityChecks + other.qualityChecks,
      tags = tags ++ other.tags,
    )
}

object PipelineMetadata {
  def single(name: String): PipelineMetadata = PipelineMetadata(name, stages = List(name))
}

object PipelineCombinators {
  def sequence[F[_]: EffectSystem, A](
    pipelines: NonEmptyList[Pipeline[F, A, A]],
  ): Pipeline[F, A, A] =
    pipelines.reduceLeft(_ andThen _)

  def parallel[F[_]: EffectSystem, A, B, C](
    left: Pipeline[F, A, B],
    right: Pipeline[F, A, C],
  )(
    combine: (B, C) => Pipeline[F, A, (B, C)],
  ): Pipeline[F, A, (B, C)] = {
    val F = EffectSystem[F]
    val run = Kleisli { a: A =>
      F.parProduct(left.run(a), right.run(a)).flatMap { case (b, c) => combine(b, c).run(a) }
    }
    val md = left.metadata
      .combine(right.metadata)
      .copy(name = s"parallel(${left.metadata.name}, ${right.metadata.name})")
    Pipeline(run, md)
  }

  def conditional[F[_]: EffectSystem, A](
    predicate: A => Boolean,
    ifTrue: Pipeline[F, A, A],
    ifFalse: Pipeline[F, A, A],
  ): Pipeline[F, A, A] = {
    val run = Kleisli { a: A => if (predicate(a)) ifTrue.run(a) else ifFalse.run(a) }
    val md = PipelineMetadata(name = s"conditional", stages = List("conditional"))
      .combine(ifTrue.metadata)
      .combine(ifFalse.metadata)
    Pipeline(run, md)
  }

  def retry[F[_]: EffectSystem, A, B](
    pipeline: Pipeline[F, A, B],
    maxRetries: Int,
    initialDelay: scala.concurrent.duration.FiniteDuration,
  ): Pipeline[F, A, B] = {
    val F   = EffectSystem[F]
    val run = Kleisli { a: A => F.retryWithBackoff(pipeline.run(a), maxRetries, initialDelay) }
    Pipeline(run, pipeline.metadata.copy(name = s"retry(${pipeline.metadata.name}, $maxRetries)"))
  }

  def batch[F[_]: EffectSystem, A, B](
    pipeline: Pipeline[F, List[A], List[B]],
    batchSize: Int = 1000,
  ): Pipeline[F, List[A], List[B]] = {
    val F = EffectSystem[F]
    val run = Kleisli { input: List[A] =>
      val groups = input.grouped(batchSize).toList
      F.parTraverse(groups)(g => pipeline.run(g)).map(_.flatten)
    }
    Pipeline(run, pipeline.metadata.copy(name = s"batch(${pipeline.metadata.name}, $batchSize)"))
  }
}
