package com.flowforge.core.types

import cats.Parallel
import cats.data.Kleisli
import com.flowforge.core.algebra.EffectSystem

import scala.concurrent.duration.FiniteDuration

/**
 * Combinators for composing pipelines.
 */
object PipelineCombinators {

  /**
   * Compose two pipelines sequentially
   */
  def sequence[F[_]: EffectSystem, A, B, C](
    first: Pipeline[F, A, B],
    second: Pipeline[F, B, C]
  ): Pipeline[F, A, C] =
    Pipeline[F, A, C](
      name = s"${first.name}->${second.name}",
      description = s"Sequential composition of ${first.name} and ${second.name}",
      stages = first.stages ++ second.stages,
      config = first.config // Use first pipeline's config
    )

  /**
   * Run pipelines in parallel and combine results
   */
  def parallel[F[_]: EffectSystem: Parallel, A, B, C](
    left: Pipeline[F, A, B],
    right: Pipeline[F, A, C]
  ): Pipeline[F, A, (B, C)] = {
    require(left.stages.nonEmpty, s"Left pipeline ${left.name} must have at least one stage")
    require(right.stages.nonEmpty, s"Right pipeline ${right.name} must have at least one stage")

    val customStage = PipelineStage.Custom[F, A, (B, C)](
      name = s"parallel-${left.name}-${right.name}",
      description = s"Parallel execution of ${left.name} and ${right.name}",
      logic = { a =>
        val _ = implicitly[EffectSystem[F]]
        val _ = implicitly[Parallel[F]]
        import cats.syntax.parallel._
        (left.execute(a), right.execute(a)).parTupled
      },
      execute = Kleisli { a =>
        val _ = implicitly[EffectSystem[F]]
        val _ = implicitly[Parallel[F]]
        import cats.syntax.parallel._
        (left.execute(a), right.execute(a)).parTupled
      }
    )

    Pipeline[F, A, (B, C)](
      name = s"parallel(${left.name},${right.name})",
      description = s"Parallel execution of ${left.name} and ${right.name}",
      stages = List(customStage),
      config = left.config
    )
  }

  /**
   * Conditional pipeline execution
   */
  def conditional[F[_]: EffectSystem, A, B](
    condition: A => Boolean,
    ifTrue: Pipeline[F, A, B],
    ifFalse: Pipeline[F, A, B]
  ): Pipeline[F, A, B] = {
    val conditionalStage = PipelineStage.Custom[F, A, B](
      name = s"conditional-${ifTrue.name}-${ifFalse.name}",
      description = "Conditional pipeline execution",
      logic = { a =>
        if (condition(a)) ifTrue.execute(a)
        else ifFalse.execute(a)
      },
      execute = Kleisli { a =>
        if (condition(a)) ifTrue.execute(a)
        else ifFalse.execute(a)
      }
    )

    Pipeline[F, A, B](
      name = s"conditional(${ifTrue.name},${ifFalse.name})",
      description = "Conditional pipeline execution",
      stages = List(conditionalStage),
      config = ifTrue.config
    )
  }

  /**
   * Retry a pipeline on failure
   */
  def retry[F[_]: EffectSystem, A, B](
    pipeline: Pipeline[F, A, B],
    maxRetries: Int
  ): Pipeline[F, A, B] = {
    val retryStage = PipelineStage.Custom[F, A, B](
      name = s"retry-${pipeline.name}",
      description = s"Retry ${pipeline.name} up to $maxRetries times",
      logic = { a =>
        val F = implicitly[EffectSystem[F]]
        F.retryWithBackoff(
          pipeline.execute(a),
          maxRetries,
          FiniteDuration(1, scala.concurrent.duration.SECONDS)
        )
      },
      execute = Kleisli { a =>
        val F = implicitly[EffectSystem[F]]
        F.retryWithBackoff(
          pipeline.execute(a),
          maxRetries,
          FiniteDuration(1, scala.concurrent.duration.SECONDS)
        )
      }
    )

    Pipeline[F, A, B](
      name = s"retry($maxRetries,${pipeline.name})",
      description = s"Retry ${pipeline.name} up to $maxRetries times",
      stages = List(retryStage),
      config = pipeline.config
    )
  }
}
