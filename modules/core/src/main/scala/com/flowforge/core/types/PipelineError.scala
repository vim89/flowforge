package com.flowforge.core.types

import java.time.Instant
import java.util.UUID

/**
 * Pipeline-specific errors.
 */
sealed trait PipelineError extends FlowForgeError

object PipelineError {

  case class EmptyPipeline(name: String) extends PipelineError {
    val message                  = s"Pipeline '$name' has no stages"
    val category: ErrorCategory  = ErrorCategory.Configuration
    val severity: ErrorSeverity  = ErrorSeverity.Error
    val context                  = Map("pipeline" -> name)
    val cause: Option[Throwable] = None
    val timestamp: Instant       = Instant.now()
    val errorId: String          = UUID.randomUUID().toString
    val isRetryable              = false
    val recoveryHints            = List("Add at least one stage to the pipeline")

    def withContext(additionalContext: Map[String, Any]): EmptyPipeline = this
    def withCause(underlyingCause: Throwable): EmptyPipeline            = this
  }

  case class InvalidConfiguration(details: String) extends PipelineError {
    val message                  = s"Invalid pipeline configuration: $details"
    val category: ErrorCategory  = ErrorCategory.Configuration
    val severity: ErrorSeverity  = ErrorSeverity.Error
    val context                  = Map("details" -> details)
    val cause: Option[Throwable] = None
    val timestamp: Instant       = Instant.now()
    val errorId: String          = UUID.randomUUID().toString
    val isRetryable              = false
    val recoveryHints            = List("Review pipeline configuration")

    def withContext(additionalContext: Map[String, Any]): InvalidConfiguration = this
    def withCause(underlyingCause: Throwable): InvalidConfiguration            = this
  }

  case class StageExecutionError(stageName: String, reason: String) extends PipelineError {
    val message                  = s"Stage '$stageName' failed: $reason"
    val category: ErrorCategory  = ErrorCategory.System
    val severity: ErrorSeverity  = ErrorSeverity.Error
    val context                  = Map("stage" -> stageName, "reason" -> reason)
    val cause: Option[Throwable] = None
    val timestamp: Instant       = Instant.now()
    val errorId: String          = UUID.randomUUID().toString
    val isRetryable              = true
    val recoveryHints            = List("Retry the stage", "Check stage configuration")

    def withContext(additionalContext: Map[String, Any]): StageExecutionError = this
    def withCause(underlyingCause: Throwable): StageExecutionError            = this
  }
}
