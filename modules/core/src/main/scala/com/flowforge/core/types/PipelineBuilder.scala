package com.flowforge.core.types

import cats.data.{ Kleisli, ValidatedNel }
import cats.implicits.catsSyntaxValidatedId
import com.flowforge.core.algebra.EffectSystem
import eu.timepit.refined.api.Refined

/**
 * Fluent builder for pipeline construction (legacy). Prefer PipelineBuilder2 for compile-time type safety.
 */
case class PipelineBuilder[F[_]: EffectSystem, A, B] private (
  name: String,
  description: String = "",
  stages: List[PipelineStage[F, _, _]] = List.empty,
  config: Option[PipelineConfig] = None) {

  def withDescription(desc: String): PipelineBuilder[F, A, B] =
    copy(description = desc)

  def withConfig(pipelineConfig: PipelineConfig): PipelineBuilder[F, A, B] =
    copy(config = Some(pipelineConfig))

  def addSource[C](source: DataSource, reader: DataSource => F[C]): PipelineBuilder[F, Unit, C] = {
    val stage = PipelineStage.Source(
      name = s"source-${stages.size}",
      description = s"Read from ${source.format}",
      dataSource = source,
      execute = Kleisli(_ => reader(source)),
    )
    // FIXED: Create new builder with proper type parameters
    PipelineBuilder[F, Unit, C](
      name = name,
      description = description,
      stages = stages :+ stage.asInstanceOf[PipelineStage[F, _, _]],
      config = config,
    )
  }

  def addTransform[C](transform: B => F[C]): PipelineBuilder[F, A, C] = {
    val stage = PipelineStage.Transform(
      name = s"transform-${stages.size}",
      description = "Data transformation",
      execute = Kleisli(transform),
    )
    // FIXED: Create new builder with proper type parameters
    PipelineBuilder[F, A, C](
      name = name,
      description = description,
      stages = stages :+ stage.asInstanceOf[PipelineStage[F, _, _]],
      config = config,
    )
  }

  def addFilter(predicate: B => Boolean): PipelineBuilder[F, A, B] = {
    val F = implicitly[EffectSystem[F]]
    val stage = PipelineStage.Filter(
      name = s"filter-${stages.size}",
      description = "Filter records",
      predicate = predicate,
      execute = Kleisli[F, B, B](b =>
        if (predicate(b)) F.pure(b)
        else F.raiseError(new RuntimeException("Filtered")),
      ),
      metrics = StageMetrics.empty,
    )
    copy(stages = stages :+ stage)
  }

  def addSink(sink: DataSink, writer: (B, DataSink) => F[Unit]): PipelineBuilder[F, A, Unit] = {
    val stage = PipelineStage.Sink(
      name = s"sink-${stages.size}",
      description = s"Write to ${sink.format}",
      dataSink = sink,
      execute = Kleisli((b: B) => writer(b, sink)),
    )
    // FIXED: Create new builder with proper type parameters
    PipelineBuilder[F, A, Unit](
      name = name,
      description = description,
      stages = stages :+ stage.asInstanceOf[PipelineStage[F, _, _]],
      config = config,
    )
  }

  /**
   * FIXED: Validate stage chain without unsafe operations
   */
  private def validateStageChain(): Either[String, Unit] =
    if (stages.isEmpty) {
      Left("Pipeline must have at least one stage")
    } else {
      // Basic validation - check that all stages have valid execute functions
      val hasInvalidStages = stages.exists(_.execute == null)
      if (hasInvalidStages) {
        Left("One or more stages have invalid execute functions")
      } else {
        Right(())
      }
    }

  def build: ValidatedNel[PipelineError, Pipeline[F, A, B]] =
    // runtime validation of heterogeneous stage chain
    validateStageChain() match {
      case Left(reason) =>
        PipelineError.InvalidConfiguration(reason).invalidNel
      case Right(_) =>
        val defaultConfig = config.getOrElse(
          PipelineConfig(
            name = Refined.unsafeApply(if (name.nonEmpty) name else "default"),
            version = "1.0.0",
            environment = Environment.Development,
            source = stages.collectFirst { case s: PipelineStage.Source[F, _] => s.dataSource }
              .getOrElse(DataSource.gcs("default", "default", DataFormat.Parquet)),
            sink = stages.collectFirst { case s: PipelineStage.Sink[F, _] => s.dataSink }
              .getOrElse(DataSink.gcs("default", "default", DataFormat.Parquet)),
          ),
        )

        Pipeline[F, A, B](
          name = name,
          description = description,
          stages = stages,
          config = defaultConfig,
        ).validate.map(_ =>
          Pipeline[F, A, B](
            name = name,
            description = description,
            stages = stages,
            config = defaultConfig,
          ),
        )
    }
}

object PipelineBuilder {

  def apply[F[_]: EffectSystem](name: String): PipelineBuilder[F, Unit, Unit] =
    PipelineBuilder[F, Unit, Unit](name)
}
