package com.flowforge.core.types

import java.time.Instant

/**
 * Stage-level metrics for monitoring and optimization.
 */
case class StageMetrics(
  recordsIn: Long = 0,
  recordsOut: Long = 0,
  recordsFiltered: Long = 0,
  processingTimeMs: Long = 0,
  errors: Long = 0,
  lastExecuted: Option[Instant] = None) {

  def throughput: Double =
    if (processingTimeMs > 0) recordsOut.toDouble / (processingTimeMs / 1000.0) else 0.0

  def filterRate: Double =
    if (recordsIn > 0) recordsFiltered.toDouble / recordsIn else 0.0

  def errorRate: Double =
    if (recordsIn > 0) errors.toDouble / recordsIn else 0.0

  def +(other: StageMetrics): StageMetrics = StageMetrics(
    recordsIn = recordsIn + other.recordsIn,
    recordsOut = recordsOut + other.recordsOut,
    recordsFiltered = recordsFiltered + other.recordsFiltered,
    processingTimeMs = processingTimeMs + other.processingTimeMs,
    errors = errors + other.errors,
    lastExecuted = {
      val both = lastExecuted.toList ++ other.lastExecuted.toList
      if (both.isEmpty) None
      else Some(both.maxBy(_.toEpochMilli))
    },
  )
}

object StageMetrics {
  val empty: StageMetrics = StageMetrics()
}
