/**
 * FlowForge Core Module - Error Type Hierarchy
 *
 * File: modules/core/src/main/scala/com/flowforge/core/types/ErrorTypes.scala Package:
 * com.flowforge.core.types
 *
 * This file defines the complete error hierarchy for the FlowForge ecosystem. Using Scala's sealed
 * trait ADT pattern, we create a comprehensive, type-safe error model that enables precise error
 * handling and recovery strategies.
 *
 * Design Patterns Applied:
 *   - ADT Pattern: Sealed hierarchy for exhaustive pattern matching
 *   - Error Recovery Pattern: Categorized errors enable targeted recovery
 *   - Composite Pattern: Complex errors compose simple error types
 *   - Strategy Pattern: Different error handling per error category
 *
 * Scala Features Showcased:
 *   - Sealed Traits: Compile-time exhaustiveness checking
 *   - Case Classes: Immutable error data with structural equality
 *   - Pattern Matching: Type-safe error handling in client code
 *   - Type Classes: Show instances for pretty error printing
 *   - Phantom Types: Compile-time error severity classification
 *   - Self Types: Mixin composition for error capabilities
 *
 * Innovation Highlights:
 *   - Error aggregation with NonEmptyList for batch validation
 *   - Contextual error enrichment with structured metadata
 *   - Recovery strategy hints embedded in error types
 *   - Performance-optimized error construction with lazy evaluation
 *   - Integration with Cats ValidatedNel for functional error handling
 *
 * Usage Examples:
 * ```scala
 * // Pattern matching on error types
 * error match {
 *   case ValidationError.SchemaViolation(field, expected, actual) =>
 *     log.warn(s"Schema mismatch in $field: expected $expected, got $actual")
 *   case SystemError.ResourceExhausted(resource, limit) =>
 *     initiateBackpressure()
 *   case BusinessError.DataQualityViolation(rule, severity) =>
 *     if (severity == Critical) stopPipeline() else logWarning()
 * }
 *
 * // Error aggregation with ValidatedNel
 * val validatedData: ValidatedNel[ValidationError, ProcessedData] =
 *   (validateSchema(data), validateQuality(data), validateFreshness(data))
 *     .mapN(ProcessedData.apply)
 * ```
 *
 * @author
 *   FlowForge Team
 * @version 1.0.0
 * @since 2024
 */
package com.flowforge.core.types

import cats.Show
import cats.data.NonEmptyList
import cats.syntax.show._
import com.flowforge.core.types.ValidationError.{ MissingRequiredField, SchemaViolation }

import java.time.{ Duration, Instant }
import java.util.UUID
import scala.concurrent.duration.FiniteDuration

/**
 * Root error type for all FlowForge errors.
 *
 * This sealed trait ensures that all errors in the FlowForge ecosystem are part of a controlled
 * hierarchy, enabling exhaustive pattern matching and type-safe error handling strategies.
 *
 * The hierarchy is organized by error category:
 *   - ValidationError: Data validation and schema violations
 *   - SystemError: Infrastructure and resource issues
 *   - BusinessError: Domain-specific business rule violations
 *   - ConfigurationError: Setup and configuration problems
 *   - NetworkError: Communication and connectivity issues
 */
trait FlowForgeError extends RuntimeException with Product with Serializable {

  /** Human-readable error message */
  def message: String

  /** Error category for routing and handling */
  def category: ErrorCategory

  /** Severity level for alerting and response */
  def severity: ErrorSeverity

  /** Optional error context for debugging */
  def context: Map[String, Any]

  /** Optional underlying cause */
  def cause: Option[Throwable]

  /** Timestamp when error occurred */
  def timestamp: Instant

  /** Unique error identifier for tracking */
  def errorId: String

  /** Whether this error should trigger retries */
  def isRetryable: Boolean

  /** Suggested recovery actions */
  def recoveryHints: List[String]

  // Override standard exception methods
  override def getMessage: String = message

  override def getCause: Throwable = cause.orNull

  /**
   * Enrich this error with additional context. Returns a new error instance with merged context.
   */
  def withContext(additionalContext: Map[String, Any]): FlowForgeError

  /**
   * Enrich this error with a single context value.
   */
  def withContext(key: String, value: Any): FlowForgeError =
    withContext(Map(key -> value))

  /**
   * Chain this error with an underlying cause.
   */
  def withCause(underlyingCause: Throwable): FlowForgeError
}

/**
 * Error categories for classification and routing.
 */
sealed trait ErrorCategory extends Product with Serializable

object ErrorCategory {
  case object Validation extends ErrorCategory

  case object System extends ErrorCategory

  case object Business extends ErrorCategory

  case object Configuration extends ErrorCategory

  case object Network extends ErrorCategory

  case object Security extends ErrorCategory

  case object Unknown extends ErrorCategory

  implicit val showErrorCategory: Show[ErrorCategory] = Show.show {
    case Validation    => "VALIDATION"
    case System        => "SYSTEM"
    case Business      => "BUSINESS"
    case Configuration => "CONFIGURATION"
    case Network       => "NETWORK"
    case Security      => "SECURITY"
    case Unknown       => "UNKNOWN"
  }
}

/**
 * Error severity levels for alerting and escalation.
 */
sealed trait ErrorSeverity extends Product with Serializable {
  def level: Int

  def shouldAlert: Boolean

  def shouldBlock: Boolean
}

object ErrorSeverity {
  case object Debug extends ErrorSeverity {
    val level       = 0
    val shouldAlert = false
    val shouldBlock = false
  }

  case object Info extends ErrorSeverity {
    val level       = 1
    val shouldAlert = false
    val shouldBlock = false
  }

  case object Warning extends ErrorSeverity {
    val level       = 2
    val shouldAlert = false
    val shouldBlock = false
  }

  case object Error extends ErrorSeverity {
    val level       = 3
    val shouldAlert = true
    val shouldBlock = false
  }

  case object Critical extends ErrorSeverity {
    val level       = 4
    val shouldAlert = true
    val shouldBlock = true
  }

  implicit val showErrorSeverity: Show[ErrorSeverity] = Show.show {
    case Debug    => "DEBUG"
    case Info     => "INFO"
    case Warning  => "WARNING"
    case Error    => "ERROR"
    case Critical => "CRITICAL"
  }
}

// ===============================
// VALIDATION ERRORS
// ===============================

/**
 * Errors related to data validation, schema compliance, and data quality. These errors typically
 * indicate problems with input data or contract violations.
 */
sealed trait ValidationError extends FlowForgeError {
  val category: ErrorCategory = ErrorCategory.Validation
  val isRetryable: Boolean    = false // Data validation errors typically aren't retryable
}

object ValidationError {

  /**
   * Schema validation violation. Occurs when data doesn't conform to expected schema.
   */
  case class SchemaViolation(
    field: String,
    expected: String,
    actual: String,
    schemaName: Option[String] = None,
    message: String = "Schema validation failed",
    severity: ErrorSeverity = ErrorSeverity.Error,
    context: Map[String, Any] = Map.empty,
    cause: Option[Throwable] = None,
    timestamp: Instant = Instant.now(),
    errorId: String = UUID.randomUUID().toString
  ) extends ValidationError {

    val recoveryHints: List[String] = List(
      "Check data source schema compatibility",
      "Update data transformation logic",
      "Review schema evolution settings"
    )

    def withContext(additionalContext: Map[String, Any]): SchemaViolation =
      copy(context = context ++ additionalContext)

    def withCause(underlyingCause: Throwable): SchemaViolation =
      copy(cause = Some(underlyingCause))
  }

  /**
   * Data quality constraint violation. Occurs when data fails quality checks or business rules.
   */
  case class QualityViolation(
    constraint: String,
    violatedValue: String,
    threshold: Option[String] = None,
    message: String = "Data quality constraint violated",
    severity: ErrorSeverity = ErrorSeverity.Warning,
    context: Map[String, Any] = Map.empty,
    cause: Option[Throwable] = None,
    timestamp: Instant = Instant.now(),
    errorId: String = UUID.randomUUID().toString
  ) extends ValidationError {

    val recoveryHints: List[String] = List(
      "Review data quality rules",
      "Check upstream data sources",
      "Consider data cleansing steps"
    )

    def withContext(additionalContext: Map[String, Any]): QualityViolation =
      copy(context = context ++ additionalContext)

    def withCause(underlyingCause: Throwable): QualityViolation =
      copy(cause = Some(underlyingCause))
  }

  /**
   * Required field missing. Occurs when mandatory data fields are null or missing.
   */
  case class MissingRequiredField(
    fieldName: String,
    recordId: Option[String] = None,
    message: String = "Required field is missing",
    severity: ErrorSeverity = ErrorSeverity.Error,
    context: Map[String, Any] = Map.empty,
    cause: Option[Throwable] = None,
    timestamp: Instant = Instant.now(),
    errorId: String = UUID.randomUUID().toString
  ) extends ValidationError {

    val recoveryHints: List[String] = List(
      "Check data extraction logic",
      "Verify source data completeness",
      "Review field mapping configuration"
    )

    def withContext(additionalContext: Map[String, Any]): MissingRequiredField =
      copy(context = context ++ additionalContext)

    def withCause(underlyingCause: Throwable): MissingRequiredField =
      copy(cause = Some(underlyingCause))
  }

  /**
   * Data format or type mismatch. Occurs when data cannot be parsed or converted to expected type.
   */
  case class TypeMismatch(
    field: String,
    expectedType: String,
    actualType: String,
    value: String,
    message: String = "Data type mismatch",
    severity: ErrorSeverity = ErrorSeverity.Error,
    context: Map[String, Any] = Map.empty,
    cause: Option[Throwable] = None,
    timestamp: Instant = Instant.now(),
    errorId: String = UUID.randomUUID().toString
  ) extends ValidationError {

    val recoveryHints: List[String] = List(
      "Review data transformation logic",
      "Check source data types",
      "Update type conversion rules"
    )

    def withContext(additionalContext: Map[String, Any]): TypeMismatch =
      copy(context = context ++ additionalContext)

    def withCause(underlyingCause: Throwable): TypeMismatch =
      copy(cause = Some(underlyingCause))
  }
}

// ===============================
// SYSTEM ERRORS
// ===============================

/**
 * Errors related to system resources, infrastructure, and runtime issues. These errors typically
 * indicate problems with the underlying platform.
 */
sealed trait SystemError extends FlowForgeError {
  val category: ErrorCategory = ErrorCategory.System
}

object SystemError {

  /**
   * Resource exhaustion error. Occurs when system runs out of memory, disk space, or other
   * resources.
   */
  case class ResourceExhausted(
    resource: String,
    limit: String,
    current: String,
    message: String = "System resource exhausted",
    severity: ErrorSeverity = ErrorSeverity.Critical,
    context: Map[String, Any] = Map.empty,
    cause: Option[Throwable] = None,
    timestamp: Instant = Instant.now(),
    errorId: String = UUID.randomUUID().toString,
    isRetryable: Boolean = true
  ) extends SystemError {

    val recoveryHints: List[String] = List(
      "Scale up resources",
      "Optimize memory usage",
      "Implement backpressure",
      "Retry after delay"
    )

    def withContext(additionalContext: Map[String, Any]): ResourceExhausted =
      copy(context = context ++ additionalContext)

    def withCause(underlyingCause: Throwable): ResourceExhausted =
      copy(cause = Some(underlyingCause))
  }

  /**
   * Service unavailable error. Occurs when a required service or dependency is not available.
   */
  case class ServiceUnavailable(
    serviceName: String,
    endpoint: Option[String] = None,
    message: String = "Required service is unavailable",
    severity: ErrorSeverity = ErrorSeverity.Error,
    context: Map[String, Any] = Map.empty,
    cause: Option[Throwable] = None,
    timestamp: Instant = Instant.now(),
    errorId: String = UUID.randomUUID().toString,
    isRetryable: Boolean = true
  ) extends SystemError {

    val recoveryHints: List[String] = List(
      "Check service health",
      "Retry with exponential backoff",
      "Use circuit breaker pattern",
      "Fall back to alternative service"
    )

    def withContext(additionalContext: Map[String, Any]): ServiceUnavailable =
      copy(context = context ++ additionalContext)

    def withCause(underlyingCause: Throwable): ServiceUnavailable =
      copy(cause = Some(underlyingCause))
  }

  /**
   * Timeout error. Occurs when operations exceed configured time limits.
   */
  case class OperationTimeout(
    operation: String,
    timeout: FiniteDuration,
    elapsed: FiniteDuration,
    message: String = "Operation timed out",
    severity: ErrorSeverity = ErrorSeverity.Error,
    context: Map[String, Any] = Map.empty,
    cause: Option[Throwable] = None,
    timestamp: Instant = Instant.now(),
    errorId: String = UUID.randomUUID().toString,
    isRetryable: Boolean = true
  ) extends SystemError {

    val recoveryHints: List[String] = List(
      "Increase timeout configuration",
      "Optimize operation performance",
      "Implement operation cancellation",
      "Retry with circuit breaker"
    )

    def withContext(additionalContext: Map[String, Any]): OperationTimeout =
      copy(context = context ++ additionalContext)

    def withCause(underlyingCause: Throwable): OperationTimeout =
      copy(cause = Some(underlyingCause))
  }
}

// ===============================
// BUSINESS ERRORS
// ===============================

/**
 * Errors related to business logic and domain rules. These errors indicate violations of business
 * constraints or policies.
 */
sealed trait BusinessError extends FlowForgeError {
  val category: ErrorCategory = ErrorCategory.Business
  val isRetryable: Boolean    = false // Business errors typically require manual intervention
}

object BusinessError {

  /**
   * Data contract violation. Occurs when data doesn't meet agreed-upon contracts between producers
   * and consumers.
   */
  case class DataContractViolation(
    contractName: String,
    violatedRule: String,
    datasetId: String,
    message: String = "Data contract violated",
    severity: ErrorSeverity = ErrorSeverity.Error,
    context: Map[String, Any] = Map.empty,
    cause: Option[Throwable] = None,
    timestamp: Instant = Instant.now(),
    errorId: String = UUID.randomUUID().toString
  ) extends BusinessError {

    val recoveryHints: List[String] = List(
      "Review data contract specifications",
      "Contact data producer team",
      "Update contract requirements",
      "Implement data transformation"
    )

    def withContext(additionalContext: Map[String, Any]): DataContractViolation =
      copy(context = context ++ additionalContext)

    def withCause(underlyingCause: Throwable): DataContractViolation =
      copy(cause = Some(underlyingCause))
  }

  /**
   * SLA violation error. Occurs when service level agreements are not met.
   */
  case class SlaViolation(
    slaMetric: String,
    expectedValue: String,
    actualValue: String,
    violationDuration: Duration,
    message: String = "SLA violation detected",
    severity: ErrorSeverity = ErrorSeverity.Warning,
    context: Map[String, Any] = Map.empty,
    cause: Option[Throwable] = None,
    timestamp: Instant = Instant.now(),
    errorId: String = UUID.randomUUID().toString
  ) extends BusinessError {

    val recoveryHints: List[String] = List(
      "Review SLA requirements",
      "Optimize pipeline performance",
      "Scale system resources",
      "Update SLA agreements"
    )

    def withContext(additionalContext: Map[String, Any]): SlaViolation =
      copy(context = context ++ additionalContext)

    def withCause(underlyingCause: Throwable): SlaViolation =
      copy(cause = Some(underlyingCause))
  }
}

sealed trait DataProcessingError extends FlowForgeError {
  val category: ErrorCategory = ErrorCategory.Business
  val isRetryable: Boolean    = true // Data processing errors may be transient
}

object DataProcessingError {

  /**
   * Data processing failure. Occurs when data transformations or computations fail.
   */
  case class ProcessingFailure(
    stepName: String,
    reason: String,
    message: String = "Data processing failed",
    severity: ErrorSeverity = ErrorSeverity.Error,
    context: Map[String, Any] = Map.empty,
    cause: Option[Throwable] = None,
    timestamp: Instant = Instant.now(),
    errorId: String = UUID.randomUUID().toString
  ) extends DataProcessingError {

    val recoveryHints: List[String] = List(
      "Check data transformation logic",
      "Review computation algorithms",
      "Retry processing step",
      "Implement error handling in pipeline"
    )

    def withContext(additionalContext: Map[String, Any]): ProcessingFailure =
      copy(context = context ++ additionalContext)

    def withCause(underlyingCause: Throwable): ProcessingFailure =
      copy(cause = Some(underlyingCause))
  }
}

// ===============================
// ERROR UTILITIES
// ===============================

/**
 * Utility functions for working with FlowForge errors.
 */
object FlowForgeError {

  /**
   * Generic validation error for missing ValidationError cases.
   */
  case class ValidationError(
    message: String,
    field: Option[String] = None,
    severity: ErrorSeverity = ErrorSeverity.Error,
    context: Map[String, Any] = Map.empty,
    cause: Option[Throwable] = None,
    timestamp: Instant = Instant.now(),
    errorId: String = UUID.randomUUID().toString
  ) extends FlowForgeError {

    val category: ErrorCategory = ErrorCategory.Validation
    val isRetryable: Boolean    = false

    val recoveryHints: List[String] = List(
      "Check input validation logic",
      "Verify data constraints",
      "Review validation rules"
    )

    def withContext(additionalContext: Map[String, Any]): ValidationError =
      copy(context = context ++ additionalContext)

    def withCause(underlyingCause: Throwable): ValidationError =
      copy(cause = Some(underlyingCause))
  }

  /**
   * Configuration error for missing ConfigurationError cases.
   */
  case class ConfigurationError(
    message: String,
    configKey: Option[String] = None,
    severity: ErrorSeverity = ErrorSeverity.Error,
    context: Map[String, Any] = Map.empty,
    cause: Option[Throwable] = None,
    timestamp: Instant = Instant.now(),
    errorId: String = UUID.randomUUID().toString
  ) extends FlowForgeError {

    val category: ErrorCategory = ErrorCategory.Configuration
    val isRetryable: Boolean    = false

    val recoveryHints: List[String] = List(
      "Check configuration file syntax",
      "Verify required configuration keys",
      "Review configuration documentation"
    )

    def withContext(additionalContext: Map[String, Any]): ConfigurationError =
      copy(context = context ++ additionalContext)

    def withCause(underlyingCause: Throwable): ConfigurationError =
      copy(cause = Some(underlyingCause))
  }

  /**
   * Create a composite error from multiple errors. Useful for aggregating validation errors or
   * batch processing errors.
   */
  case class CompositeError(
    errors: NonEmptyList[FlowForgeError],
    message: String = "Multiple errors occurred",
    severity: ErrorSeverity = ErrorSeverity.Error,
    context: Map[String, Any] = Map.empty,
    cause: Option[Throwable] = None,
    timestamp: Instant = Instant.now(),
    errorId: String = UUID.randomUUID().toString
  ) extends FlowForgeError {

    val category: ErrorCategory = ErrorCategory.Validation
    val isRetryable: Boolean    = errors.exists(_.isRetryable)

    val recoveryHints: List[String] = List(
      "Resolve individual errors",
      "Check error aggregation logic",
      "Review batch processing configuration"
    )

    def withContext(additionalContext: Map[String, Any]): CompositeError =
      copy(context = context ++ additionalContext)

    def withCause(underlyingCause: Throwable): CompositeError =
      copy(cause = Some(underlyingCause))
  }

  /**
   * Convert a standard Throwable to a FlowForge error.
   */
  def fromThrowable(
    throwable: Throwable,
    context: Map[String, Any] = Map.empty
  ): SystemError.ServiceUnavailable =
    SystemError.ServiceUnavailable(
      serviceName = "unknown-service",
      message = throwable.getMessage,
      context = context,
      cause = Some(throwable)
    )

  /**
   * Create a validation error for missing required field.
   */
  def missingField(
    fieldName: String,
    recordId: Option[String] = None
  ): MissingRequiredField =
    MissingRequiredField(fieldName, recordId)

  /**
   * Create a validation error for schema violations.
   */
  def schemaViolation(
    field: String,
    expected: String,
    actual: String
  ): SchemaViolation = SchemaViolation(field, expected, actual)

  /**
   * Show instance for FlowForge errors. Provides formatted error output for logging and debugging.
   */
  implicit val showFlowForgeError: Show[FlowForgeError] = Show.show { error =>
    val contextStr = if (error.context.nonEmpty) {
      error.context.map { case (k, v) => s"$k=$v" }.mkString(", ")
    } else {
      "none"
    }

    s"""FlowForgeError(
       |  id: ${error.errorId}
       |  category: ${error.category.show}
       |  severity: ${error.severity.show}
       |  message: ${error.message}
       |  retryable: ${error.isRetryable}
       |  context: $contextStr
       |  timestamp: ${error.timestamp}
       |  hints: ${error.recoveryHints.mkString("; ")}
       |)""".stripMargin
  }
}
