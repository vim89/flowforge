package com.flowforge.framework

import com.flowforge.core.algebra.{ EffectSystem, FlowforgeResource }

/**
 * Helpers to run pipelines with or without managed resources.
 *
 * These utilities centralize execution concerns so individual stages remain focused and testable.
 */
object PipelineExecution {

  /** Execute a pipeline on a single input. */
  def execute[F[_]: EffectSystem, A, B](pipeline: Pipeline[F, A, B])(input: A): F[B] =
    pipeline.run(input)

  /** Execute a pipeline using a managed resource scope (e.g., SparkSession). */
  def executeWithResources[F[_]: EffectSystem, A, B](
    pipeline: Pipeline[F, A, B],
    resources: FlowforgeResource[F, Unit],
  )(
    input: A,
  ): F[B] =
    resources.use(_ => pipeline.run(input))

  /** Execute a pipeline over a list of inputs in parallel, preserving order. */
  def executeBatch[F[_]: EffectSystem, A, B](
    pipeline: Pipeline[F, A, B],
  )(
    inputs: List[A],
  ): F[List[B]] = {
    val F = EffectSystem[F]
    F.parTraverse(inputs)(pipeline.run.run)
  }
}
