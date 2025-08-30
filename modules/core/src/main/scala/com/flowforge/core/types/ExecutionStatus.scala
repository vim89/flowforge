package com.flowforge.core.types

/**
 * Pipeline execution status.
 */
sealed trait ExecutionStatus extends Product with Serializable

object ExecutionStatus {
  case object Success        extends ExecutionStatus
  case object Failed         extends ExecutionStatus
  case object PartialSuccess extends ExecutionStatus
  case object Cancelled      extends ExecutionStatus
  case object TimedOut       extends ExecutionStatus
}
