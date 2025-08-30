package com.flowforge.core.types

import java.time.Instant
import scala.concurrent.duration.FiniteDuration

/**
 * Pipeline execution result with metrics.
 */
case class PipelineResult[A](
  pipelineId: String,
  input: String,
  output: Option[String],
  status: ExecutionStatus,
  startTime: Instant,
  endTime: Instant,
  duration: FiniteDuration,
  metrics: PipelineMetrics,
  errors: List[String] = List.empty
)
