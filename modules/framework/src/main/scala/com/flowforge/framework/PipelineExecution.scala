package com.flowforge.framework

import cats.effect.{ Resource, Sync }
import com.flowforge.core.algebra.EffectSystem

object PipelineExecution {

  def execute[F[_]: EffectSystem, A, B](pipeline: Pipeline[F, A, B])(input: A): F[B] =
    pipeline.run(input)

  def executeWithResources[F[_]: EffectSystem: Sync, A, B](
    pipeline: Pipeline[F, A, B],
    resources: Resource[F, Unit]
  )(input: A): F[B] =
    resources.use(_ => pipeline.run(input))

  def executeBatch[F[_]: EffectSystem, A, B](
    pipeline: Pipeline[F, A, B]
  )(inputs: List[A]): F[List[B]] = {
    val F = EffectSystem[F]
    F.parTraverse(inputs)(pipeline.run.run)
  }
}
