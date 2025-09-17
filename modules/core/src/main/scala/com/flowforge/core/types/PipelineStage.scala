package com.flowforge.core.types

import cats.data.Kleisli

/**
 * Pipeline stage representing a discrete processing step.
 */
sealed trait PipelineStage[F[_], -A, B] extends Product with Serializable {
  def name: String
  def description: String
  def execute: Kleisli[F, A, B]
  def metrics: StageMetrics
}

object PipelineStage {

  /**
   * Source stage - reads data from external systems
   */
  case class Source[F[_], B](
    name: String,
    description: String,
    dataSource: DataSource,
    execute: Kleisli[F, Unit, B],
    metrics: StageMetrics = StageMetrics.empty)
      extends PipelineStage[F, Unit, B]

  /**
   * Transformation stage - processes data
   */
  case class Transform[F[_], A, B](
    name: String,
    description: String,
    execute: Kleisli[F, A, B],
    metrics: StageMetrics = StageMetrics.empty)
      extends PipelineStage[F, A, B]

  /**
   * Filter stage - removes unwanted records
   */
  case class Filter[F[_], A](
    name: String,
    description: String,
    predicate: A => Boolean,
    execute: Kleisli[F, A, A],
    metrics: StageMetrics = StageMetrics.empty)
      extends PipelineStage[F, A, A]

  /**
   * Branch stage - splits pipeline into multiple paths
   */
  case class Branch[F[_], A, B, C](
    name: String,
    description: String,
    left: PipelineStage[F, A, B],
    right: PipelineStage[F, A, C],
    execute: Kleisli[F, A, (B, C)],
    metrics: StageMetrics = StageMetrics.empty)
      extends PipelineStage[F, A, (B, C)]

  /**
   * Join stage - combines multiple pipeline paths
   */
  case class Join[F[_], A, B, C](
    name: String,
    description: String,
    joiner: (A, B) => C,
    execute: Kleisli[F, (A, B), C],
    metrics: StageMetrics = StageMetrics.empty)
      extends PipelineStage[F, (A, B), C]

  /**
   * Sink stage - writes data to external systems
   */
  case class Sink[F[_], A](
    name: String,
    description: String,
    dataSink: DataSink,
    execute: Kleisli[F, A, Unit],
    metrics: StageMetrics = StageMetrics.empty)
      extends PipelineStage[F, A, Unit]

  /**
   * Custom stage - user-defined processing
   */
  case class Custom[F[_], A, B](
    name: String,
    description: String,
    logic: A => F[B],
    execute: Kleisli[F, A, B],
    metrics: StageMetrics = StageMetrics.empty)
      extends PipelineStage[F, A, B]
}
