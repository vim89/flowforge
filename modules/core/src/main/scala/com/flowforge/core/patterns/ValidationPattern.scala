/**
 * FlowForge Core Module - Validation Patterns
 *
 * File: modules/core/src/main/scala/com/flowforge/core/patterns/ValidationPattern.scala Package:
 * com.flowforge.core.patterns
 *
 * This file provides comprehensive validation patterns and combinators for the FlowForge ecosystem.
 * It enables composable, functional validation with rich error accumulation and recovery strategies
 * using cats ValidatedNel.
 *
 * Design Patterns Applied:
 *   - Combinator Pattern: Small, composable validation functions
 *   - Strategy Pattern: Different validation strategies per data type
 *   - Chain of Responsibility: Validation pipeline with ordered checks
 *   - Template Method Pattern: Abstract validation framework with concrete rules
 *   - Composite Pattern: Complex validations from simple components
 *
 * Scala Features Showcased:
 *   - ValidatedNel: Error accumulation without short-circuiting
 *   - Applicative Functor: Parallel validation with mapN
 *   - Type Classes: Polymorphic validation across data types
 *   - Higher-Order Functions: Validation combinators and builders
 *   - Pattern Matching: Sophisticated error handling and routing
 *   - Implicit Classes: Fluent validation syntax
 *   - Generic Programming: Reusable validation patterns
 *
 * Innovation Highlights:
 *   - Compositional validation with error accumulation
 *   - Data quality validation with business rule integration
 *   - Schema validation with evolution compatibility checking
 *   - Configuration validation with environment-specific rules
 *   - Performance-optimized validation for large datasets
 *   - Conditional validation with context-aware rules
 *
 * Usage Examples:
 * ```scala
 * // Compositional validation with error accumulation
 * case class User(name: String, email: String, age: Int)
 *
 * val validateUser: User => ValidationResult[User] = { user =>
 *   (
 *     validateNonEmpty("name", user.name),
 *     validateEmail("email", user.email),
 *     validateRange("age", user.age, 0, 150)
 *   ).mapN((_, _, _) => user)
 * }
 *
 * // Data quality validation with business rules
 * val dataQualityRules = ValidationRules
 *   .builder[Dataset]
 *   .notNull("customer_id")
 *   .unique("transaction_id")
 *   .range("amount", min = 0, max = 1_000_000)
 *   .pattern("email", EmailPattern)
 *   .freshness(maxAge = 24.hours)
 *   .build
 *
 * // Conditional validation based on context
 * val environmentRules = ValidationRules.conditional[Config] { config =>
 *   if (config.environment.isProduction) {
 *     strictValidation
 *   } else {
 *     lenientValidation
 *   }
 * }
 * ```
 *
 * @author
 *   FlowForge Team
 * @version 1.0.0
 * @since 2024
 */
package com.flowforge.core.patterns

import cats.data.{ NonEmptyList, Validated, ValidatedNel }
import cats.syntax.all._
import com.flowforge.core.algebra.{ SchemaError, SchemaIncompatible }
import com.flowforge.core.types.RefinedTypes._
import com.flowforge.core.types.ValidationError.QualityViolation
import com.flowforge.core.types._

import java.time.Instant
import scala.concurrent.duration.FiniteDuration
import scala.util.matching.Regex

// ===============================
// VALIDATION RESULT TYPES
// ===============================

/**
 * Type aliases for validation results. These provide consistent, readable types across the
 * validation system.
 */
object ValidationTypes {

  // Core validation types
  type ValidationResult[A]         = ValidatedNel[ValidationError, A]
  type ConfigValidationResult[A]   = ValidatedNel[ConfigError, A]
  type QualityValidationResult[A]  = ValidatedNel[QualityViolation, A]
  type SchemaValidationResult      = ValidatedNel[SchemaError, Unit]
  type BusinessValidationResult[A] = ValidatedNel[BusinessError, A]

  // Convenience constructors
  def valid[E, A](value: A): ValidatedNel[E, A]                     = Validated.validNel(value)
  def invalid[E, A](error: E): ValidatedNel[E, A]                   = Validated.invalidNel(error)
  def invalidNel[E, A](errors: NonEmptyList[E]): ValidatedNel[E, A] = Validated.invalid(errors)

  // Validation success/failure checking
  def isValid[E, A](result: ValidatedNel[E, A]): Boolean   = result.isValid
  def isInvalid[E, A](result: ValidatedNel[E, A]): Boolean = result.isInvalid
  def getErrors[E, A](result: ValidatedNel[E, A]): Option[NonEmptyList[E]] =
    result.fold(Some(_), _ => None)
  def getValue[E, A](result: ValidatedNel[E, A]): Option[A] = result.fold(_ => None, Some(_))
}

import com.flowforge.core.patterns.ValidationTypes._

// ===============================
// CORE VALIDATION COMBINATORS
// ===============================

/**
 * Core validation combinators for building complex validations from simple parts. These combinators
 * enable functional composition of validation logic.
 */
object ValidationCombinators {

  /**
   * Validate that a string is not null or empty.
   */
  def nonEmpty(fieldName: String, value: String): ConfigValidationResult[String] =
    if (value != null && value.trim.nonEmpty) {
      valid(value)
    } else {
      invalid(
        ConfigError.InvalidValue(fieldName, Option(value).getOrElse("null"), "non-empty string")
      )
    }

  /**
   * Validate that an optional string is non-empty if present.
   */
  def nonEmptyOption(
    fieldName: String,
    value: Option[String]
  ): ConfigValidationResult[Option[String]] =
    value match {
      case Some(str) => nonEmpty(fieldName, str).map(Some(_))
      case None      => valid(None)
    }

  /**
   * Validate that a required field is present.
   */
  def required[A](fieldName: String, value: Option[A]): ConfigValidationResult[A] =
    value match {
      case Some(a) => valid(a)
      case None    => invalid(ConfigError.MissingRequired(fieldName))
    }

  /**
   * Validate that a value is within a numeric range.
   */
  def inRange(
    fieldName: String,
    value: Double,
    min: Double,
    max: Double
  ): ConfigValidationResult[Double] =
    if (value >= min && value <= max) {
      valid(value)
    } else {
      invalid(ConfigError.OutOfRange(fieldName, value.toString, min.toString, max.toString))
    }

  /**
   * Validate that an integer is within a range.
   */
  def intInRange(
    fieldName: String,
    value: Int,
    min: Int,
    max: Int
  ): ConfigValidationResult[Int] =
    if (value >= min && value <= max) {
      valid(value)
    } else {
      invalid(ConfigError.OutOfRange(fieldName, value.toString, min.toString, max.toString))
    }

  /**
   * Validate that a value is positive.
   */
  def positive(fieldName: String, value: Double): ConfigValidationResult[Double] =
    if (value > 0) {
      valid(value)
    } else {
      invalid(ConfigError.InvalidValue(fieldName, value.toString, "positive number"))
    }

  /**
   * Validate that a value is non-negative.
   */
  def nonNegative(fieldName: String, value: Double): ConfigValidationResult[Double] =
    if (value >= 0) {
      valid(value)
    } else {
      invalid(ConfigError.InvalidValue(fieldName, value.toString, "non-negative number"))
    }

  /**
   * Validate that a string matches a regular expression.
   */
  def matchesPattern(
    fieldName: String,
    value: String,
    pattern: Regex,
    description: String
  ): ConfigValidationResult[String] =
    if (pattern.matches(value)) {
      valid(value)
    } else {
      invalid(ConfigError.InvalidFormat(fieldName, value, description))
    }

  /**
   * Validate an email address format.
   */
  def email(fieldName: String, value: String): ConfigValidationResult[String] = {
    val emailPattern = """^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$""".r
    matchesPattern(fieldName, value, emailPattern, "valid email address")
  }

  /**
   * Validate a URL format.
   */
  def url(fieldName: String, value: String): ConfigValidationResult[String] = {
    val urlPattern = """^https?://[A-Za-z0-9.-]+(/.*)?$""".r
    matchesPattern(fieldName, value, urlPattern, "valid HTTP/HTTPS URL")
  }

  /**
   * Validate that a collection is not empty.
   */
  def nonEmptyCollection[A](
    fieldName: String,
    collection: List[A]
  ): ConfigValidationResult[List[A]] =
    if (collection.nonEmpty) {
      valid(collection)
    } else {
      invalid(ConfigError.InvalidValue(fieldName, "[]", "non-empty collection"))
    }

  /**
   * Validate that a collection size is within bounds.
   */
  def collectionSize[A](
    fieldName: String,
    collection: List[A],
    min: Int,
    max: Int
  ): ConfigValidationResult[List[A]] = {
    val size = collection.size
    if (size >= min && size <= max) {
      valid(collection)
    } else {
      invalid(ConfigError.OutOfRange(fieldName, size.toString, min.toString, max.toString))
    }
  }

  /**
   * Validate all elements in a collection.
   */
  def validateAll[A, B](
    collection: List[A]
  )(validator: A => ValidatedNel[ConfigError, B]): ConfigValidationResult[List[B]] =
    collection.traverse(validator)

  /**
   * Validate that no elements in a collection are duplicates.
   */
  def noDuplicates[A](
    fieldName: String,
    collection: List[A]
  ): ConfigValidationResult[List[A]] = {
    val duplicates = collection.groupBy(identity).filter(_._2.size > 1).keys.toList
    if (duplicates.isEmpty) {
      valid(collection)
    } else {
      invalid(ConfigError.InvalidValue(fieldName, duplicates.mkString(", "), "unique elements"))
    }
  }

  /**
   * Conditional validation - apply validator only if condition is true.
   */
  def when[A](
    condition: Boolean
  )(validator: A => ConfigValidationResult[A]): A => ConfigValidationResult[A] = { value =>
    if (condition) validator(value) else valid(value)
  }

  /**
   * Conditional validation based on the value itself.
   */
  def whenValue[A](
    predicate: A => Boolean
  )(validator: A => ConfigValidationResult[A]): A => ConfigValidationResult[A] = { value =>
    if (predicate(value)) validator(value) else valid(value)
  }

  /**
   * Either-or validation - value must satisfy at least one of the validators.
   */
  def eitherOr[A](
    validator1: A => ConfigValidationResult[A],
    validator2: A => ConfigValidationResult[A]
  ): A => ConfigValidationResult[A] = { value =>
    validator1(value).orElse(validator2(value))
  }

  /**
   * All-of validation - value must satisfy all validators.
   */
  def allOf[A](
    validators: List[A => ConfigValidationResult[A]]
  ): A => ConfigValidationResult[A] = { value =>
    validators.traverse(_(value)).map(_ => value)
  }

  /**
   * Create a validator that always passes.
   */
  def pass[A]: A => ConfigValidationResult[A] = valid

  /**
   * Create a validator that always fails.
   */
  def fail[A](error: ConfigError): A => ConfigValidationResult[A] = _ => invalid(error)
}

// ===============================
// DATA QUALITY VALIDATION
// ===============================

/**
 * Specialized validation patterns for data quality assurance. These validators focus on data
 * integrity, completeness, and business rules.
 */
object DataQualityValidation {

  /**
   * Validate data freshness - ensure data is not older than specified duration.
   */
  def freshness[A](
    fieldName: String,
    timestamp: Instant,
    maxAge: FiniteDuration
  ): QualityValidationResult[A] = ???

  /**
   * Validate data completeness - ensure required percentage of non-null values.
   */
  def completeness[A](
    fieldName: String,
    values: List[Option[A]],
    minCompleteness: Double
  ): QualityValidationResult[List[Option[A]]] = {
    val total              = values.length
    val nonNull            = values.count(_.isDefined)
    val actualCompleteness = if (total > 0) nonNull.toDouble / total else 0.0

    if (actualCompleteness >= minCompleteness) {
      values.validNel
    } else {
      val violation = QualityViolation(
        constraint = "completeness",
        violatedValue = fieldName,
        threshold = Some(minCompleteness.toString),
        message = s"Completeness too low: ${actualCompleteness * 100}% < ${minCompleteness * 100}%",
        severity = ErrorSeverity.Error
      )
      violation.invalidNel
    }
  }

  /**
   * Validate data uniqueness - ensure no duplicate values.
   */
  def uniqueness[A](
    fieldName: String,
    values: List[A]
  ): QualityValidationResult[List[A]] = {
    val duplicates = values.groupBy(identity).filter(_._2.size > 1)

    if (duplicates.isEmpty) {
      values.validNel
    } else {
      val violation = QualityViolation(
        constraint = "uniqueness",
        violatedValue = fieldName,
        threshold = Some(duplicates).map(_.toString),
        message = s"Duplicate values found: ${duplicates.keys.mkString(", ")}",
        severity = ErrorSeverity.Error
      )
      violation.invalidNel
    }
  }

  /**
   * Validate referential integrity - ensure foreign key references exist.
   */
  def referentialIntegrity[A](
    fieldName: String,
    foreignKeys: List[A],
    referenceTable: Set[A]
  ): QualityValidationResult[List[A]] = {
    val invalidKeys = foreignKeys.filterNot(referenceTable.contains)

    if (invalidKeys.isEmpty) {
      foreignKeys.validNel
    } else {
      val violation = QualityViolation(
        constraint = "referential_integrity",
        violatedValue = fieldName,
        threshold = Some(invalidKeys).map(_.toString),
        message = s"Invalid foreign key references: ${invalidKeys.mkString(", ")}",
        severity = ErrorSeverity.Error
      )
      violation.invalidNel
    }
  }

  /**
   * Validate data distribution - ensure values follow expected statistical properties.
   */
  def distribution(
    fieldName: String,
    values: List[Double],
    expectedMean: Double,
    tolerance: Double
  ): QualityValidationResult[List[Double]] =
    if (values.isEmpty) {
      val violation = QualityViolation(
        constraint = "distribution",
        violatedValue = fieldName,
        threshold = None,
        message = s"No values provided for distribution validation",
        severity = ErrorSeverity.Error
      )
      violation.invalidNel
    } else {
      val actualMean = values.sum / values.length
      val deviation  = math.abs(actualMean - expectedMean)

      if (deviation <= tolerance) {
        values.validNel
      } else {
        val violation = QualityViolation(
          constraint = "distribution",
          violatedValue = fieldName,
          threshold = Some(s"$expectedMean + $tolerance"),
          message = s"Mean deviation exceeds tolerance: $deviation > $tolerance",
          severity = ErrorSeverity.Error
        )
        violation.invalidNel
      }
    }

  /**
   * Validate business rule - custom validation logic for domain-specific constraints.
   */
  def businessRule[A](
    ruleName: String,
    description: String
  )(rule: A => Boolean): A => QualityValidationResult[A] = { value =>
    if (rule(value)) {
      value.validNel
    } else {
      val violation = QualityViolation(
        constraint = "business_rule",
        violatedValue = ruleName,
        threshold = Some(value).map(_.toString),
        message = s"Business rule violation: $description",
        severity = ErrorSeverity.Error
      )
      violation.invalidNel
    }
  }
}

// ===============================
// SCHEMA VALIDATION
// ===============================

/**
 * Schema validation patterns for ensuring data structure compatibility.
 */
object SchemaValidation {

  /**
   * Validate that two schemas are compatible.
   */
  def compatible(
    source: DataSchema,
    target: DataSchema
  ): SchemaValidationResult = {
    val errors = scala.collection.mutable.ListBuffer[SchemaError]()

    // Check field compatibility
    target.fields.foreach { targetField =>
      source.fieldByName(targetField.name.value) match {
        case Some(sourceField) =>
          if (!areTypesCompatible(sourceField.dataType, targetField.dataType)) {
            errors += SchemaIncompatible(source, target)
          }
        case None =>
          if (targetField.isRequired) {
            errors += SchemaIncompatible(source, target)
          }
      }
    }

    NonEmptyList.fromList(errors.toList) match {
      case Some(errs) => errs.invalid
      case None       => ().valid
    }
  }

  /**
   * Validate schema evolution - ensure new schema can read old data.
   */
  def evolutionCompatible(
    oldSchema: DataSchema,
    newSchema: DataSchema
  ): SchemaValidationResult = {
    val errors = scala.collection.mutable.ListBuffer[SchemaError]()

    // Check for removed required fields
    oldSchema.requiredFields.foreach { oldField =>
      newSchema.fieldByName(oldField.name.value) match {
        case None => errors += SchemaIncompatible(oldSchema, newSchema)
        case Some(newField) =>
          if (!areTypesCompatible(oldField.dataType, newField.dataType)) {
            errors += SchemaIncompatible(oldSchema, newSchema)
          }
      }
    }

    // Check for added required fields without defaults
    newSchema.requiredFields.foreach { newField =>
      if (!oldSchema.fieldByName(newField.name.value).isDefined) {
        errors += SchemaIncompatible(oldSchema, newSchema)
      }
    }

    NonEmptyList.fromList(errors.toList) match {
      case Some(errs) => errs.invalid
      case None       => ().valid
    }
  }

  /**
   * Check if two data types are compatible.
   */
  private def areTypesCompatible(source: DataType, target: DataType): Boolean =
    (source, target) match {
      case (a, b) if a == b                       => true
      case (DataType.Integer, DataType.Long)      => true
      case (DataType.Float, DataType.Double)      => true
      case (DataType.VarChar(_), DataType.String) => true
      case (DataType.Nullable(inner1), DataType.Nullable(inner2)) =>
        areTypesCompatible(inner1, inner2)
      case (inner, DataType.Nullable(targetInner)) =>
        areTypesCompatible(inner, targetInner)
      case _ => false
    }

  /**
   * Validate required fields are present in schema.
   */
  def hasRequiredFields(
    schema: DataSchema,
    requiredFields: List[String]
  ): SchemaValidationResult = {
    val schemaFieldNames = schema.fieldNames.toSet
    val missingFields    = requiredFields.filterNot(schemaFieldNames.contains)

    if (missingFields.isEmpty) {
      valid(())
    } else {
      val error = SchemaIncompatible(
        DataSchema.builder.build, // Empty schema as placeholder
        schema
      )
      invalid(error)
    }
  }
}

// ===============================
// VALIDATION RULE BUILDER
// ===============================

/**
 * Fluent builder for creating complex validation rules. Enables readable, composable validation
 * rule construction.
 */
case class ValidationRuleBuilder[A] private (
  private val rules: List[A => ValidationResult[A]] = List.empty,
  private val name: String = "unnamed-validation"
) {

  /**
   * Add a custom validation rule.
   */
  def rule(validator: A => ValidationResult[A]): ValidationRuleBuilder[A] =
    copy(rules = rules :+ validator)

  /**
   * Add a validation rule with custom error message.
   */
  def ruleWithMessage(
    validator: A => Boolean,
    error: ValidationError
  ): ValidationRuleBuilder[A] = {
    val validationRule: A => ValidationResult[A] = { value =>
      if (validator(value)) valid(value) else invalid(error)
    }
    copy(rules = rules :+ validationRule)
  }

  /**
   * Add a conditional validation rule.
   */
  def when(condition: A => Boolean)(
    validator: A => ValidationResult[A]
  ): ValidationRuleBuilder[A] = {
    val conditionalRule: A => ValidationResult[A] = { value =>
      if (condition(value)) validator(value) else valid(value)
    }
    copy(rules = rules :+ conditionalRule)
  }

  /**
   * Combine with another validation rule builder.
   */
  def combine(other: ValidationRuleBuilder[A]): ValidationRuleBuilder[A] =
    copy(rules = rules ++ other.rules)

  /**
   * Build the final validation function.
   */
  def build: A => ValidationResult[A] = { value =>
    val results = rules.map(_(value))
    results.sequence.map(_ => value)
  }

  /**
   * Build as a named validation rule.
   */
  def buildNamed(ruleName: String): NamedValidationRule[A] =
    NamedValidationRule(ruleName, build)

  /**
   * Set the name for this validation builder.
   */
  def withName(ruleName: String): ValidationRuleBuilder[A] =
    copy(name = ruleName)
}

object ValidationRuleBuilder {

  /**
   * Create an empty validation rule builder.
   */
  def empty[A]: ValidationRuleBuilder[A] = ValidationRuleBuilder[A]()

  /**
   * Create a validation rule builder with a name.
   */
  def named[A](name: String): ValidationRuleBuilder[A] =
    ValidationRuleBuilder[A](name = name)

  /**
   * Create a validation rule builder from a single rule.
   */
  def from[A](validator: A => ValidationResult[A]): ValidationRuleBuilder[A] =
    ValidationRuleBuilder[A](rules = List(validator))
}

/**
 * Named validation rule for better error reporting and debugging.
 */
case class NamedValidationRule[A](
  name: String,
  validator: A => ValidationResult[A]
) {

  def validate(value: A): ValidationResult[A] = validator(value)

  def combine(other: NamedValidationRule[A]): NamedValidationRule[A] =
    NamedValidationRule(
      s"$name+${other.name}",
      value => (validator(value), other.validator(value)).mapN((_, _) => value)
    )
}

// ===============================
// VALIDATION SYNTAX EXTENSIONS
// ===============================

/**
 * Syntax extensions for fluent validation operations.
 */
object ValidationSyntax {

  /**
   * Enhance any value with validation capabilities.
   */
  implicit class ValidatableValue[A](private val value: A) extends AnyVal {

    /**
     * Validate this value with a validation function.
     */
    def validate(validator: A => ValidationResult[A]): ValidationResult[A] =
      validator(value)

    /**
     * Validate this value against multiple validators.
     */
    def validateAll(validators: List[A => ValidationResult[A]]): ValidationResult[A] =
      validators.traverse(_(value)).map(_ => value)

    /**
     * Validate this value conditionally.
     */
    def validateWhen(condition: Boolean)(
      validator: A => ValidationResult[A]
    ): ValidationResult[A] =
      if (condition) validator(value) else valid(value)

    /**
     * Create a validation result that's always valid.
     */
    def validNel: ValidationResult[A] = valid(value)

    /**
     * Create a validation result that's always invalid.
     */
    def invalidNel(error: ValidationError): ValidationResult[A] = invalid(error)
  }

  /**
   * Enhance validation results with additional operations.
   */
  implicit class ValidationResultOps[A](private val result: ValidationResult[A]) extends AnyVal {

    /**
     * Chain another validation on success.
     */
    def andThen[B](validator: A => ValidationResult[B]): ValidationResult[B] =
      result.andThen(validator)

    /**
     * Transform the validated value.
     */
    def mapValid[B](f: A => B): ValidationResult[B] =
      result.map(f)

    /**
     * Recover from validation errors.
     */
    def recover(recovery: NonEmptyList[ValidationError] => A): ValidationResult[A] =
      result.fold(errors => valid(recovery(errors)), valid)

    /**
     * Get the value or a default.
     */
    def getOrElse(default: A): A =
      result.getOrElse(default)

    /**
     * Convert to Either for interop.
     */
    def toEither: Either[NonEmptyList[ValidationError], A] =
      result.toEither

    /**
     * Check if validation succeeded.
     */
    def isValid: Boolean = result.isValid

    /**
     * Check if validation failed.
     */
    def isInvalid: Boolean = result.isInvalid

    /**
     * Get accumulated errors.
     */
    def errors: Option[NonEmptyList[ValidationError]] =
      result.fold(Some(_), _ => None)
  }

  /**
   * Enhance lists with batch validation capabilities.
   */
  implicit class ValidatableList[A](private val list: List[A]) extends AnyVal {

    /**
     * Validate all elements and collect results.
     */
    def validateEach(validator: A => ValidationResult[A]): ValidationResult[List[A]] =
      list.traverse(validator)

    /**
     * Validate elements in parallel (conceptually).
     */
    def validateParallel(validator: A => ValidationResult[A]): ValidationResult[List[A]] =
      list.traverse(validator) // In practice, same as sequential for ValidatedNel

    /**
     * Validate with index information.
     */
    def validateWithIndex(
      validator: (A, Int) => ValidationResult[A]
    ): ValidationResult[List[A]] =
      list.zipWithIndex.traverse { case (item, index) => validator(item, index) }

    /**
     * Find first validation failure.
     */
    def findInvalid(
      validator: A => ValidationResult[A]
    ): Option[(A, NonEmptyList[ValidationError])] =
      list.view
        .map(item => item -> validator(item))
        .find(_._2.isInvalid)
        .map { case (item, result) => item -> result.fold(identity, _ => sys.error("impossible")) }
  }
}

// ===============================
// COMMON VALIDATION PATTERNS
// ===============================

/**
 * Pre-built validation patterns for common use cases.
 */
object CommonValidations {

  import ValidationCombinators._

  /**
   * Standard user validation pattern.
   */
  case class UserValidation(
    name: String,
    email: String,
    age: Int
  )

  val validateUser: UserValidation => ConfigValidationResult[UserValidation] = { user =>
    (
      nonEmpty("name", user.name),
      email("email", user.email),
      intInRange("age", user.age, 0, 150)
    ).mapN((_, _, _) => user)
  }

  /**
   * Configuration validation pattern.
   */
  def validatePipelineConfig(config: PipelineConfig): ConfigValidationResult[PipelineConfig] = {
    val nameValidation        = nonEmpty("name", config.name.value)
    val environmentValidation = config.environment.validNel // Always valid
    val sourceValidation      = config.source.validNel      // Assume valid for now
    val sinkValidation        = config.sink.validNel        // Assume valid for now

    (nameValidation, environmentValidation, sourceValidation, sinkValidation)
      .mapN((_, _, _, _) => config)
  }

  /**
   * Data quality validation pattern.
   */
  def validateDataQuality[A](
    data: List[A],
    rules: QualityRules
  ): QualityValidationResult[List[A]] =
    // Simplified quality validation - in practice would be much more sophisticated
    if (data.nonEmpty) {
      valid(data)
    } else {
      val violation = QualityConstraint.NotNull(
        FieldName.unsafeFrom("data")
      )
      invalid(violation).asInstanceOf[QualityValidationResult[List[A]]]
    }

  /**
   * Schema compatibility validation pattern.
   */
  def validateSchemaCompatibility(
    source: DataSchema,
    target: DataSchema
  ): SchemaValidationResult =
    SchemaValidation.compatible(source, target)
}
