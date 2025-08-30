package com.flowforge.core.types

import cats.Parallel
import cats.data.Kleisli
import com.flowforge.core.algebra.EffectSystem

import scala.concurrent.duration.FiniteDuration

/**
 * Typed combinators for PipelineBuilder2.
 */
object PipelineBuilder2Combinators {

  /**
   * Sequentially compose two PipelineBuilder2s.
   */
  def sequence[F[_]: EffectSystem, In, Mid, Out](
    first: PipelineBuilder2[F, In, Mid],
    second: PipelineBuilder2[F, Mid, Out]
  ): PipelineBuilder2[F, In, Out] =
    PipelineBuilder2[F, In, Out](
      name = first.name,
      description = first.description,
      stages = first.stages ++ second.stages,
      config = first.config
    )

  /**
   * Run two PipelineBuilder2s in parallel and combine their outputs.
   */
  def parallel[F[_]: EffectSystem: Parallel, In, Out1, Out2](
    left: PipelineBuilder2[F, In, Out1],
    right: PipelineBuilder2[F, In, Out2]
  ): PipelineBuilder2[F, In, (Out1, Out2)] = {
    require(left.stages.nonEmpty, s"Left pipeline ${left.name} must have at least one stage")
    require(right.stages.nonEmpty, s"Right pipeline ${right.name} must have at least one stage")

    val leftPipe  = left.build()
    val rightPipe = right.build()

    val customStage = PipelineStage.Custom[F, In, (Out1, Out2)](
      name = s"parallel-${left.name}-${right.name}",
      description = s"Parallel execution of ${left.name} and ${right.name}",
      logic = { in =>
        val _ = implicitly[EffectSystem[F]]
        val _ = implicitly[Parallel[F]]
        import cats.syntax.parallel._
        (leftPipe.execute(in), rightPipe.execute(in)).parTupled
      },
      execute = Kleisli { in =>
        val _ = implicitly[EffectSystem[F]]
        val _ = implicitly[Parallel[F]]
        import cats.syntax.parallel._
        (leftPipe.execute(in), rightPipe.execute(in)).parTupled
      }
    )

    PipelineBuilder2[F, In, (Out1, Out2)](
      name = s"parallel(${left.name},${right.name})",
      description = s"Parallel execution of ${left.name} and ${right.name}",
      stages = List(customStage),
      config = left.config
    )
  }

  /**
   * Retry a PipelineBuilder2 on failure up to maxRetries.
   */
  def retry[F[_]: EffectSystem, In, Out](
    builder: PipelineBuilder2[F, In, Out],
    maxRetries: Int
  ): PipelineBuilder2[F, In, Out] = {
    val F = implicitly[EffectSystem[F]]
    val retryStage = PipelineStage.Custom[F, In, Out](
      name = s"retry-${builder.name}",
      description = s"Retry ${builder.name} up to $maxRetries times",
      logic = { in =>
        F.retryWithBackoff(
          builder.build().execute(in),
          maxRetries,
          FiniteDuration(1, scala.concurrent.duration.SECONDS)
        )
      },
      execute = Kleisli { in =>
        F.retryWithBackoff(
          builder.build().execute(in),
          maxRetries,
          FiniteDuration(1, scala.concurrent.duration.SECONDS)
        )
      }
    )
    PipelineBuilder2[F, In, Out](
      name = s"retry($maxRetries,${builder.name})",
      description = s"Retry ${builder.name} up to $maxRetries times",
      stages = List(retryStage),
      config = builder.config
    )
  }

  /**
   * Conditional pipeline execution.
   */
  def conditional[F[_]: EffectSystem, In, Out](
    cond: In => Boolean,
    ifTrue: PipelineBuilder2[F, In, Out],
    ifFalse: PipelineBuilder2[F, In, Out]
  ): PipelineBuilder2[F, In, Out] = {
    val conditionalStage = PipelineStage.Custom[F, In, Out](
      name = s"conditional-${ifTrue.name}-${ifFalse.name}",
      description = "Conditional pipeline execution",
      logic = { in =>
        if (cond(in)) ifTrue.build().execute(in)
        else ifFalse.build().execute(in)
      },
      execute = Kleisli { in =>
        if (cond(in)) ifTrue.build().execute(in)
        else ifFalse.build().execute(in)
      }
    )
    PipelineBuilder2[F, In, Out](
      name = s"conditional(${ifTrue.name},${ifFalse.name})",
      description = "Conditional pipeline execution",
      stages = List(conditionalStage),
      config = ifTrue.config
    )
  }
}
