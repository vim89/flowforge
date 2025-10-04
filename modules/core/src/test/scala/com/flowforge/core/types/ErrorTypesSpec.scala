// scalafix:off DisableSyntax.throw DisableSyntax.var
package com.flowforge.core.types

import cats.data.NonEmptyList
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.time.Instant
import scala.concurrent.duration._

class ErrorTypesSpec extends AnyFunSuite with Matchers {

  // ===============================
  // ERROR CATEGORY TESTS
  // ===============================

  // Removed: ErrorCategory Show instance tests - Show instance not available
  // Test that ErrorCategory ADT values can be constructed and pattern matched
  test("ErrorCategory ADT values should be accessible") {
    val categories = List(
      ErrorCategory.Validation,
      ErrorCategory.System,
      ErrorCategory.Business,
      ErrorCategory.Configuration,
      ErrorCategory.Network,
      ErrorCategory.Security,
      ErrorCategory.Unknown
    )
    categories should have size 7

    // Verify pattern matching works
    ErrorCategory.Validation match {
      case ErrorCategory.Validation => succeed
      case _ => fail("Pattern matching failed")
    }
  }

  // ===============================
  // ERROR SEVERITY TESTS
  // ===============================

  test("ErrorSeverity.Debug should have correct properties") {
    ErrorSeverity.Debug.level shouldBe 0
    ErrorSeverity.Debug.shouldAlert shouldBe false
    ErrorSeverity.Debug.shouldBlock shouldBe false
  }

  test("ErrorSeverity.Info should have correct properties") {
    ErrorSeverity.Info.level shouldBe 1
    ErrorSeverity.Info.shouldAlert shouldBe false
    ErrorSeverity.Info.shouldBlock shouldBe false
  }

  test("ErrorSeverity.Warning should have correct properties") {
    ErrorSeverity.Warning.level shouldBe 2
    ErrorSeverity.Warning.shouldAlert shouldBe false
    ErrorSeverity.Warning.shouldBlock shouldBe false
  }

  test("ErrorSeverity.Error should have correct properties") {
    ErrorSeverity.Error.level shouldBe 3
    ErrorSeverity.Error.shouldAlert shouldBe true
    ErrorSeverity.Error.shouldBlock shouldBe false
  }

  test("ErrorSeverity.Critical should have correct properties") {
    ErrorSeverity.Critical.level shouldBe 4
    ErrorSeverity.Critical.shouldAlert shouldBe true
    ErrorSeverity.Critical.shouldBlock shouldBe true
  }

  test("ErrorSeverity levels should be in ascending order") {
    ErrorSeverity.Debug.level < ErrorSeverity.Info.level shouldBe true
    ErrorSeverity.Info.level < ErrorSeverity.Warning.level shouldBe true
    ErrorSeverity.Warning.level < ErrorSeverity.Error.level shouldBe true
    ErrorSeverity.Error.level < ErrorSeverity.Critical.level shouldBe true
  }

  // ===============================
  // VALIDATION ERROR TESTS
  // ===============================

  test("ValidationError.SchemaViolation should construct with defaults") {
    val error = ValidationError.SchemaViolation("userId", "String", "Int")
    error.field shouldBe "userId"
    error.expected shouldBe "String"
    error.actual shouldBe "Int"
    error.category shouldBe ErrorCategory.Validation
    error.severity shouldBe ErrorSeverity.Error
    error.isRetryable shouldBe false
    error.recoveryHints should not be empty
  }

  test("ValidationError.SchemaViolation should support withContext") {
    val error = ValidationError.SchemaViolation("userId", "String", "Int")
    val enriched = error.withContext("pipelineId", "test-pipeline")
    enriched.context should contain("pipelineId" -> "test-pipeline")
  }

  test("ValidationError.SchemaViolation should support withCause") {
    val error = ValidationError.SchemaViolation("userId", "String", "Int")
    val cause = new RuntimeException("Parse error")
    val enriched = error.withCause(cause)
    enriched.cause shouldBe Some(cause)
    enriched.getCause shouldBe cause
  }

  test("ValidationError.SchemaViolation should preserve all custom fields on copy") {
    val error = ValidationError.SchemaViolation(
      field = "userId",
      expected = "String",
      actual = "Int",
      schemaName = Some("UserSchema"),
      message = "Custom message"
    )
    error.schemaName shouldBe Some("UserSchema")
    error.message shouldBe "Custom message"
  }

  test("ValidationError.QualityViolation should construct with defaults") {
    val error = ValidationError.QualityViolation("completeness", "0.5")
    error.constraint shouldBe "completeness"
    error.violatedValue shouldBe "0.5"
    error.severity shouldBe ErrorSeverity.Warning
    error.isRetryable shouldBe false
  }

  test("ValidationError.QualityViolation should support threshold") {
    val error = ValidationError.QualityViolation("completeness", "0.5", Some("0.95"))
    error.threshold shouldBe Some("0.95")
  }

  test("ValidationError.MissingRequiredField should construct correctly") {
    val error = ValidationError.MissingRequiredField("email")
    error.fieldName shouldBe "email"
    error.recordId shouldBe None
    error.category shouldBe ErrorCategory.Validation
  }

  test("ValidationError.MissingRequiredField should support recordId") {
    val error = ValidationError.MissingRequiredField("email", Some("user-123"))
    error.recordId shouldBe Some("user-123")
  }

  test("ValidationError.TypeMismatch should construct correctly") {
    val error = ValidationError.TypeMismatch("age", "Integer", "String", "thirty")
    error.field shouldBe "age"
    error.expectedType shouldBe "Integer"
    error.actualType shouldBe "String"
    error.value shouldBe "thirty"
    error.category shouldBe ErrorCategory.Validation
  }

  // ===============================
  // SYSTEM ERROR TESTS
  // ===============================

  test("SystemError.ResourceExhausted should construct with defaults") {
    val error = SystemError.ResourceExhausted("memory", "4GB", "3.9GB")
    error.resource shouldBe "memory"
    error.limit shouldBe "4GB"
    error.current shouldBe "3.9GB"
    error.category shouldBe ErrorCategory.System
    error.severity shouldBe ErrorSeverity.Critical
    error.isRetryable shouldBe true
  }

  test("SystemError.ServiceUnavailable should construct correctly") {
    val error = SystemError.ServiceUnavailable("database", Some("localhost:5432"))
    error.serviceName shouldBe "database"
    error.endpoint shouldBe Some("localhost:5432")
    error.category shouldBe ErrorCategory.System
    error.isRetryable shouldBe true
  }

  test("SystemError.OperationTimeout should construct with durations") {
    val timeout = 30.seconds
    val elapsed = 35.seconds
    val error = SystemError.OperationTimeout("query-execution", timeout, elapsed)
    error.operation shouldBe "query-execution"
    error.timeout shouldBe timeout
    error.elapsed shouldBe elapsed
    error.category shouldBe ErrorCategory.System
    error.isRetryable shouldBe true
  }

  // ===============================
  // BUSINESS ERROR TESTS
  // ===============================

  test("BusinessError.DataContractViolation should construct correctly") {
    val error = BusinessError.DataContractViolation("UserContract", "email_format", "user-dataset-123")
    error.contractName shouldBe "UserContract"
    error.violatedRule shouldBe "email_format"
    error.datasetId shouldBe "user-dataset-123"
    error.category shouldBe ErrorCategory.Business
    error.isRetryable shouldBe false
  }

  test("BusinessError.SlaViolation should construct correctly") {
    val duration = java.time.Duration.ofMinutes(5)
    val error = BusinessError.SlaViolation("latency", "100ms", "500ms", duration)
    error.slaMetric shouldBe "latency"
    error.expectedValue shouldBe "100ms"
    error.actualValue shouldBe "500ms"
    error.violationDuration shouldBe duration
    error.category shouldBe ErrorCategory.Business
    error.severity shouldBe ErrorSeverity.Warning
  }

  // ===============================
  // DATA PROCESSING ERROR TESTS
  // ===============================

  test("DataProcessingError.ProcessingFailure should construct correctly") {
    val error = DataProcessingError.ProcessingFailure("transform-step", "Invalid JSON")
    error.stepName shouldBe "transform-step"
    error.reason shouldBe "Invalid JSON"
    error.category shouldBe ErrorCategory.Business
    error.isRetryable shouldBe true
  }

  // ===============================
  // GENERIC ERROR TESTS
  // ===============================

  test("FlowForgeError.ValidationError should construct correctly") {
    val error = FlowForgeError.ValidationError("Invalid input", Some("email"))
    error.message shouldBe "Invalid input"
    error.field shouldBe Some("email")
    error.category shouldBe ErrorCategory.Validation
    error.isRetryable shouldBe false
  }

  test("FlowForgeError.ConfigurationError should construct correctly") {
    val error = FlowForgeError.ConfigurationError("Missing config", Some("database.url"))
    error.message shouldBe "Missing config"
    error.configKey shouldBe Some("database.url")
    error.category shouldBe ErrorCategory.Configuration
    error.isRetryable shouldBe false
  }

  test("FlowForgeError.CompositeError should aggregate errors correctly") {
    val error1 = FlowForgeError.ValidationError("Error 1")
    val error2 = FlowForgeError.ValidationError("Error 2")
    val composite = FlowForgeError.CompositeError(NonEmptyList.of(error1, error2))

    composite.errors.toList should have size 2
    composite.category shouldBe ErrorCategory.Validation
  }

  test("FlowForgeError.CompositeError isRetryable should be true if any error is retryable") {
    val nonRetryable = FlowForgeError.ValidationError("Error 1")
    val retryable = SystemError.ServiceUnavailable("service")
    val composite = FlowForgeError.CompositeError(NonEmptyList.of(nonRetryable, retryable))

    composite.isRetryable shouldBe true
  }

  test("FlowForgeError.CompositeError isRetryable should be false if all errors are non-retryable") {
    val error1 = FlowForgeError.ValidationError("Error 1")
    val error2 = FlowForgeError.ValidationError("Error 2")
    val composite = FlowForgeError.CompositeError(NonEmptyList.of(error1, error2))

    composite.isRetryable shouldBe false
  }

  // ===============================
  // FACTORY METHOD TESTS
  // ===============================

  test("FlowForgeError.fromThrowable should create SystemError") {
    val throwable = new RuntimeException("Something went wrong")
    val error = FlowForgeError.fromThrowable(throwable)

    error shouldBe a[SystemError.ServiceUnavailable]
    error.cause shouldBe Some(throwable)
    error.getMessage shouldBe "Something went wrong"
  }

  test("FlowForgeError.fromThrowable should include context") {
    val throwable = new RuntimeException("Error")
    val context = Map("pipelineId" -> "test-123", "stage" -> "transform")
    val error = FlowForgeError.fromThrowable(throwable, context)

    error.context shouldBe context
  }

  test("FlowForgeError.missingField should create MissingRequiredField") {
    val error = FlowForgeError.missingField("userId")
    error shouldBe a[ValidationError.MissingRequiredField]
    error.asInstanceOf[ValidationError.MissingRequiredField].fieldName shouldBe "userId"
  }

  test("FlowForgeError.missingField should support recordId") {
    val error = FlowForgeError.missingField("userId", Some("record-123"))
    error.asInstanceOf[ValidationError.MissingRequiredField].recordId shouldBe Some("record-123")
  }

  test("FlowForgeError.schemaViolation should create SchemaViolation") {
    val error = FlowForgeError.schemaViolation("age", "Integer", "String")
    error shouldBe a[ValidationError.SchemaViolation]
    error.asInstanceOf[ValidationError.SchemaViolation].field shouldBe "age"
  }

  // ===============================
  // COMMON ERROR INTERFACE TESTS
  // ===============================

  test("All errors should have unique errorId") {
    val error1 = FlowForgeError.ValidationError("Error 1")
    val error2 = FlowForgeError.ValidationError("Error 2")
    error1.errorId should not equal error2.errorId
  }

  test("All errors should have timestamp") {
    val error = FlowForgeError.ValidationError("Error")
    error.timestamp shouldBe a[Instant]
    error.timestamp.isBefore(Instant.now().plusSeconds(1)) shouldBe true
  }

  test("All errors should support withContext with map") {
    val error = FlowForgeError.ValidationError("Error")
    val enriched = error.withContext(Map("key1" -> "value1", "key2" -> "value2"))
    enriched.context should contain("key1" -> "value1")
    enriched.context should contain("key2" -> "value2")
  }

  test("All errors should support withContext with key-value") {
    val error = FlowForgeError.ValidationError("Error")
    val enriched = error.withContext("pipelineId", "test-pipeline")
    enriched.context should contain("pipelineId" -> "test-pipeline")
  }

  test("Errors should preserve existing context when adding new context") {
    val error = FlowForgeError.ValidationError("Error", context = Map("existing" -> "value"))
    val enriched = error.withContext("new", "data")
    enriched.context should contain("existing" -> "value")
    enriched.context should contain("new" -> "data")
  }

  test("getMessage should return message") {
    val error = FlowForgeError.ValidationError("Test message")
    error.getMessage shouldBe "Test message"
  }

  test("getCause should return cause when present") {
    val cause = new RuntimeException("Underlying error")
    val error = FlowForgeError.ValidationError("Error", cause = Some(cause))
    error.getCause shouldBe cause
  }

  test("getCause should return null when cause is None") {
    val error = FlowForgeError.ValidationError("Error")
    error.getCause shouldBe null
  }

  // ===============================
  // ERROR PROPERTY TESTS
  // ===============================

  // Removed: FlowForgeError Show instance tests - Show instance not available
  // Test that FlowForgeError properties can be accessed directly
  test("FlowForgeError should have accessible properties") {
    val error = FlowForgeError.ValidationError("Test error", context = Map("key" -> "value"))

    error.message should include("Test error")
    error.category shouldBe ErrorCategory.Validation
    error.severity shouldBe ErrorSeverity.Error
    error.context should contain("key" -> "value")
    error.isRetryable shouldBe false
    error.errorId should not be empty
  }

  test("FlowForgeError should handle empty context") {
    val error = FlowForgeError.ValidationError("Test error")
    error.context shouldBe empty
  }

  test("FlowForgeError should have recovery hints") {
    val error = FlowForgeError.ValidationError("Test error")
    error.recoveryHints should not be empty
  }

  // ===============================
  // PATTERN MATCHING TESTS
  // ===============================

  test("Pattern matching on ValidationError should be exhaustive") {
    val errors: List[ValidationError] = List(
      ValidationError.SchemaViolation("field", "expected", "actual"),
      ValidationError.QualityViolation("constraint", "value"),
      ValidationError.MissingRequiredField("field"),
      ValidationError.TypeMismatch("field", "expected", "actual", "value")
    )

    errors.foreach {
      case _: ValidationError.SchemaViolation      => // ok
      case _: ValidationError.QualityViolation     => // ok
      case _: ValidationError.MissingRequiredField => // ok
      case _: ValidationError.TypeMismatch         => // ok
    }
  }

  test("Pattern matching on SystemError should be exhaustive") {
    val errors: List[SystemError] = List(
      SystemError.ResourceExhausted("resource", "limit", "current"),
      SystemError.ServiceUnavailable("service"),
      SystemError.OperationTimeout("operation", 1.second, 2.seconds)
    )

    errors.foreach {
      case _: SystemError.ResourceExhausted   => // ok
      case _: SystemError.ServiceUnavailable  => // ok
      case _: SystemError.OperationTimeout    => // ok
    }
  }

  test("Pattern matching on BusinessError should be exhaustive") {
    val duration = java.time.Duration.ofMinutes(1)
    val errors: List[BusinessError] = List(
      BusinessError.DataContractViolation("contract", "rule", "dataset"),
      BusinessError.SlaViolation("metric", "expected", "actual", duration)
    )

    errors.foreach {
      case _: BusinessError.DataContractViolation => // ok
      case _: BusinessError.SlaViolation          => // ok
    }
  }

  // ===============================
  // RECOVERY HINTS TESTS
  // ===============================

  test("ValidationError.SchemaViolation should provide recovery hints") {
    val error = ValidationError.SchemaViolation("field", "expected", "actual")
    error.recoveryHints should not be empty
    error.recoveryHints.exists(_.toLowerCase.contains("schema")) shouldBe true
  }

  test("SystemError.ResourceExhausted should provide recovery hints") {
    val error = SystemError.ResourceExhausted("memory", "4GB", "3.9GB")
    error.recoveryHints should not be empty
    error.recoveryHints.exists(_.toLowerCase.contains("scale")) shouldBe true
  }

  test("BusinessError.DataContractViolation should provide recovery hints") {
    val error = BusinessError.DataContractViolation("contract", "rule", "dataset")
    error.recoveryHints should not be empty
    error.recoveryHints.exists(_.toLowerCase.contains("contract")) shouldBe true
  }

  // ===============================
  // EQUALITY & COPY TESTS
  // ===============================

  test("Error copy should create new instance with same values") {
    val original = ValidationError.SchemaViolation("field", "expected", "actual")
    val copy = original.copy()

    copy.field shouldBe original.field
    copy.expected shouldBe original.expected
    copy.actual shouldBe original.actual
    copy.message shouldBe original.message
  }

  test("Error copy with changes should update only specified fields") {
    val original = ValidationError.SchemaViolation("field", "expected", "actual")
    val modified = original.copy(message = "New message")

    modified.field shouldBe original.field
    modified.message shouldBe "New message"
  }

  test("Errors with same values should be equal") {
    val errorId = "test-id"
    val timestamp = Instant.now()
    val error1 = ValidationError.SchemaViolation(
      "field", "expected", "actual",
      errorId = errorId,
      timestamp = timestamp
    )
    val error2 = ValidationError.SchemaViolation(
      "field", "expected", "actual",
      errorId = errorId,
      timestamp = timestamp
    )

    error1 shouldBe error2
  }

  test("Errors with different values should not be equal") {
    val error1 = ValidationError.SchemaViolation("field1", "expected", "actual")
    val error2 = ValidationError.SchemaViolation("field2", "expected", "actual")

    error1 should not equal error2
  }
}
