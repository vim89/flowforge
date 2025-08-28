package com.flowforge.core

/**
 * FlowForge Core Module - Comprehensive Validation Framework
 *
 * File: modules/core/src/main/scala/com/flowforge/core/Validation.scala Package: com.flowforge.core
 *
 * This file implements a comprehensive validation framework that leverages Cats' ValidatedNel for
 * error accumulation and provides composable validation rules without external library
 * dependencies.
 *
 * Design Patterns Applied:
 *   - Composite Pattern: Combining validation rules
 *   - Strategy Pattern: Different validation strategies
 *   - Builder Pattern: Validation rule construction
 *   - Chain of Responsibility: Validation rule chaining
 *   - Visitor Pattern: Validation rule application
 *
 * Scala Features Showcased:
 *   - ValidatedNel for error accumulation
 *   - Type Classes for validation behaviors
 *   - Applicative Functors for independent validation
 *   - Pattern Matching for validation logic
 *   - Implicits for seamless validation syntax
 *   - Higher-Kinded Types for generic validation
 *   - Self Types for validation capabilities
 *   - Phantom Types for validation state tracking
 *
 * Innovation Highlights:
 *   - Zero external validation library dependencies
 *   - Composable validation rules with accumulation
 *   - Type-safe validation state progression
 *   - Custom DSL for validation rule construction
 *   - Integration with refined types for compile-time safety
 *
 * @author
 * FlowForge Team
 * @version 1.0.0
 * @since 2024
 */

import types._
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.implicits._
import cats.{Applicative, Functor, Semigroup}
import com.flowforge.core.error.{FormatError, LengthError, PatternError, RangeError, RequiredFieldError}

import scala.language.{higherKinds, implicitConversions}
import scala.util.{Failure, Success, Try}
import java.time.{Instant, LocalDate}
import java.util.regex.Pattern

/**
 * The validation framework provides composable, type-safe validation with comprehensive error
 * accumulation and reporting.
 *
 * Key features:
 *   1. **Error Accumulation**: Collect all validation errors, not just the first 2.
 *      **Composability**: Combine simple validators into complex ones 3. **Type Safety**: Leverage
 *      the type system to prevent invalid states 4. **Performance**: Zero-cost abstractions where
 *      possible 5. **Extensibility**: Easy to add new validation rules
 */
package object validation {

  // ===============================
  // TYPE ALIASES AND BASIC TYPES
  // ===============================

  /**
   * Core validation result type using ValidatedNel for error accumulation. This allows collecting
   * multiple validation errors instead of failing fast.
   */
  type ValidationResult[A] = ValidatedNel[ValidationError, A]

  /**
   * Validation function type for a specific input type. This makes validation functions first-class
   * values that can be composed.
   */
  type Validator[A] = A => ValidationResult[A]

  /**
   * Cross-field validation function type. Enables validation rules that depend on multiple fields.
   */
  type CrossValidator[A] = A => ValidationResult[A]

  /**
   * Conditional validation function type. Enables validation rules that only apply under certain
   * conditions.
   */
  type ConditionalValidator[A] = (A, A => Boolean) => ValidationResult[A]

  // ===============================
  // VALIDATION RULE DEFINITIONS
  // ===============================

  /**
   * Base trait for all validation rules. Provides a common interface for rule execution and
   * composition.
   */
  trait ValidationRule[A] {

    /**
     * Apply this validation rule to a value.
     */
    def validate(value: A): ValidationResult[A]

    /**
     * Get a description of this validation rule.
     */
    def description: String

    /**
     * Get the severity of this validation rule.
     */
    def severity: ValidationSeverity = ValidationSeverity.Error

    /**
     * Combine this rule with another using AND logic. Both rules must pass for the combined rule to
     * pass.
     */
    def and[B >: A](other: ValidationRule[B]): ValidationRule[B] =
      AndRule(this.asInstanceOf[ValidationRule[B]], other)

    /**
     * Combine this rule with another using OR logic. Either rule can pass for the combined rule to
     * pass.
     */
    def or[B >: A](other: ValidationRule[B]): ValidationRule[B] =
      OrRule(this.asInstanceOf[ValidationRule[B]], other)

    /**
     * Apply this rule conditionally based on a predicate.
     */
    def when(predicate: A => Boolean): ValidationRule[A] =
      ConditionalRule(this, predicate)
  }

  /**
   * Validation severity levels.
   */
  sealed trait ValidationSeverity extends Product with Serializable {
    def name: String

    def level: Int
  }

  object ValidationSeverity {
    case object Info extends ValidationSeverity {
      val name = "info"
      val level = 1
    }

    case object Warning extends ValidationSeverity {
      val name = "warning"
      val level = 2
    }

    case object Error extends ValidationSeverity {
      val name = "error"
      val level = 3
    }

    case object Critical extends ValidationSeverity {
      val name = "critical"
      val level = 4
    }

    implicit val severityOrdering: Ordering[ValidationSeverity] = Ordering.by(_.level)
  }

  // ===============================
  // CONCRETE VALIDATION RULES
  // ===============================

  /**
   * Rule for required (non-null, non-empty) values.
   */
  case class RequiredRule[A](field: String) extends ValidationRule[Option[A]] {
    def validate(value: Option[A]): ValidationResult[Option[A]] = value match {
      case Some(v) =>
        v match {
          case s: String if s.trim.isEmpty => RequiredFieldError(field).invalidNel
          case _ => value.validNel
        }
      case None => RequiredFieldError(field).invalidNel
    }

    def description: String = s"Field '$field' is required"
  }

  /**
   * Rule for string length validation.
   */
  case class LengthRule(
                         field: String,
                         minLength: Option[Int] = None,
                         maxLength: Option[Int] = None
                       ) extends ValidationRule[String] {

    def validate(value: String): ValidationResult[String] = {
      val length = value.length
      val valid = minLength.forall(length >= _) && maxLength.forall(length <= _)

      if (valid) value.validNel
      else LengthError(field, minLength, maxLength, length).invalidNel
    }

    def description: String = {
      val bounds = (minLength, maxLength) match {
        case (Some(min), Some(max)) => s"between $min and $max characters"
        case (Some(min), None) => s"at least $min characters"
        case (None, Some(max)) => s"at most $max characters"
        case (None, None) => "any length"
      }
      s"Field '$field' must be $bounds"
    }
  }

  /**
   * Rule for numeric range validation.
   */
  case class RangeRule[A: Numeric](
                                    field: String,
                                    min: Option[A] = None,
                                    max: Option[A] = None
                                  ) extends ValidationRule[A] {

    private val numeric = implicitly[Numeric[A]]

    def validate(value: A): ValidationResult[A] = {
      val valueDouble = numeric.toDouble(value)
      val valid = min.forall(m => numeric.compare(value, m) >= 0) &&
        max.forall(m => numeric.compare(value, m) <= 0)

      if (valid) value.validNel
      else
        RangeError(
          field,
          min.map(numeric.toDouble),
          max.map(numeric.toDouble),
          valueDouble
        ).invalidNel
    }

    def description: String = {
      val bounds = (min, max) match {
        case (Some(minVal), Some(maxVal)) => s"between $minVal and $maxVal"
        case (Some(minVal), None) => s"at least $minVal"
        case (None, Some(maxVal)) => s"at most $maxVal"
        case (None, None) => "within valid range"
      }
      s"Field '$field' must be $bounds"
    }
  }

  /**
   * Rule for pattern matching validation.
   */
  case class PatternRule(
                          field: String,
                          pattern: Pattern,
                          patternDescription: String
                        ) extends ValidationRule[String] {

    def validate(value: String): ValidationResult[String] =
      if (pattern.matcher(value).matches()) value.validNel
      else PatternError(field, patternDescription, value).invalidNel

    def description: String = s"Field '$field' must match pattern: $patternDescription"
  }

  /**
   * Convenience constructor for pattern rules.
   */
  object PatternRule {
    def apply(field: String, regex: String, description: String): PatternRule =
      PatternRule(field, Pattern.compile(regex), description)
  }

  /**
   * Rule for email validation.
   */
  case class EmailRule(field: String) extends ValidationRule[String] {
    private val emailPattern = Pattern.compile(
      """^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$"""
    )

    def validate(value: String): ValidationResult[String] =
      if (emailPattern.matcher(value).matches()) value.validNel
      else FormatError(field, "valid email address", value).invalidNel

    def description: String = s"Field '$field' must be a valid email address"
  }

  /**
   * Rule for URL validation.
   */
  case class UrlRule(field: String) extends ValidationRule[String] {
    private val urlPattern = Pattern.compile(
      """^https?://[^\s/$.?#].[^\s]*$"""
    )

    def validate(value: String): ValidationResult[String] =
      if (urlPattern.matcher(value).matches()) value.validNel
      else FormatError(field, "valid URL", value).invalidNel

    def description: String = s"Field '$field' must be a valid URL"
  }

  /**
   * Rule for custom validation logic.
   */
  case class CustomRule[A](
                            field: String,
                            predicate: A => Boolean,
                            errorMessage: A => String,
                            ruleDescription: String
                          ) extends ValidationRule[A] {

    def validate(value: A): ValidationResult[A] =
      if (predicate(value)) value.validNel
      else CustomError(field, errorMessage(value)).invalidNel

    def description: String = s"Field '$field': $ruleDescription"
  }

  // ===============================
  // COMPOSITE VALIDATION RULES
  // ===============================

  /**
   * AND composition of validation rules. Both rules must pass for the combined rule to pass.
   */
  case class AndRule[A](left: ValidationRule[A], right: ValidationRule[A])
    extends ValidationRule[A] {
    def validate(value: A): ValidationResult[A] =
      (left.validate(value), right.validate(value)).mapN((_, _) => value)

    def description: String = s"(${left.description}) AND (${right.description})"
  }

  /**
   * OR composition of validation rules. Either rule can pass for the combined rule to pass.
   */
  case class OrRule[A](left: ValidationRule[A], right: ValidationRule[A])
    extends ValidationRule[A] {
    def validate(value: A): ValidationResult[A] =
      left.validate(value) match {
        case valid@Validated.Valid(_) => valid
        case Validated.Invalid(_) => right.validate(value)
      }

    def description: String = s"(${left.description}) OR (${right.description})"
  }

  /**
   * Conditional validation rule. Only applies the rule if the predicate is true.
   */
  case class ConditionalRule[A](
                                 rule: ValidationRule[A],
                                 condition: A => Boolean
                               ) extends ValidationRule[A] {

    def validate(value: A): ValidationResult[A] =
      if (condition(value)) rule.validate(value)
      else value.validNel

    def description: String = s"IF condition THEN (${rule.description})"
  }

  // ===============================
  // VALIDATION COMBINATORS
  // ===============================

  /**
   * Validation combinators provide a DSL for building complex validation rules.
   */
  object Validators {

    /**
     * Create a required field validator.
     */
    def required[A](field: String): ValidationRule[Option[A]] = RequiredRule(field)

    /**
     * Create a string length validator.
     */
    def length(field: String, min: Int, max: Int): ValidationRule[String] =
      LengthRule(field, Some(min), Some(max))

    def minLength(field: String, min: Int): ValidationRule[String] =
      LengthRule(field, Some(min), None)

    def maxLength(field: String, max: Int): ValidationRule[String] =
      LengthRule(field, None, Some(max))

    /**
     * Create numeric range validators.
     */
    def range[A: Numeric](field: String, min: A, max: A): ValidationRule[A] =
      RangeRule(field, Some(min), Some(max))

    def min[A: Numeric](field: String, min: A): ValidationRule[A] =
      RangeRule(field, Some(min), None)

    def max[A: Numeric](field: String, max: A): ValidationRule[A] =
      RangeRule(field, None, Some(max))

    def positive[A: Numeric](field: String): ValidationRule[A] = {
      val numeric = implicitly[Numeric[A]]
      min(field, numeric.one)
    }

    def nonNegative[A: Numeric](field: String): ValidationRule[A] = {
      val numeric = implicitly[Numeric[A]]
      min(field, numeric.zero)
    }

    /**
     * Create pattern validators.
     */
    def pattern(field: String, regex: String, description: String): ValidationRule[String] =
      PatternRule(field, regex, description)

    def email(field: String): ValidationRule[String] = EmailRule(field)

    def url(field: String): ValidationRule[String] = UrlRule(field)

    /**
     * Create custom validators.
     */
    def custom[A](
                   field: String,
                   predicate: A => Boolean,
                   errorMessage: A => String,
                   description: String
                 ): ValidationRule[A] = CustomRule(field, predicate, errorMessage, description)

    def custom[A](
                   field: String,
                   predicate: A => Boolean,
                   errorMessage: String,
                   description: String
                 ): ValidationRule[A] = CustomRule(field, predicate, _ => errorMessage, description)

    /**
     * Validate that a value is in a set of allowed values.
     */
    def oneOf[A](field: String, allowedValues: Set[A]): ValidationRule[A] =
      custom(
        field,
        allowedValues.contains,
        value => s"Value '$value' is not allowed. Allowed values: ${allowedValues.mkString(", ")}",
        s"Must be one of: ${allowedValues.mkString(", ")}"
      )

    /**
     * Validate that a string is not empty.
     */
    def nonEmpty(field: String): ValidationRule[String] =
      custom(field, !_.trim.isEmpty, "Must not be empty", "non-empty string")

    /**
     * Validate that a collection is not empty.
     */
    def nonEmptyCollection[A](field: String): ValidationRule[Iterable[A]] =
      custom(field, _.nonEmpty, "Collection must not be empty", "non-empty collection")

    /**
     * Combine multiple validators with AND logic.
     */
    def all[A](validators: ValidationRule[A]*): ValidationRule[A] =
      validators.reduce(_ and _)

    /**
     * Combine multiple validators with OR logic.
     */
    def any[A](validators: ValidationRule[A]*): ValidationRule[A] =
      validators.reduce(_ or _)
  }

  // ===============================
  // VALIDATION ENGINE
  // ===============================

  /**
   * The validation engine coordinates validation rule execution and provides advanced validation
   * capabilities.
   */
  class ValidationEngine[A] private(private val rules: List[ValidationRule[A]]) {

    /**
     * Add a validation rule to this engine.
     */
    def withRule(rule: ValidationRule[A]): ValidationEngine[A] =
      new ValidationEngine(rules :+ rule)

    /**
     * Add multiple validation rules to this engine.
     */
    def withRules(newRules: ValidationRule[A]*): ValidationEngine[A] =
      new ValidationEngine(rules ++ newRules)

    /**
     * Validate a value against all rules. Returns all validation errors, not just the first one.
     */
    def validate(value: A): ValidationResult[A] = {
      val results = rules.map(_.validate(value))
      results.foldLeft(value.validNel[ValidationError]) { (acc, result) =>
        (acc, result).mapN((_, _) => value)
      }
    }

    /**
     * Validate a value and return a detailed validation report.
     */
    def validateWithReport(value: A): ValidationReport[A] = {
      val ruleResults = rules.map { rule =>
        val result = rule.validate(value)
        ValidationRuleResult(rule, result)
      }

      val allErrors = ruleResults.flatMap(_.result.fold(_.toList, _ => Nil))
      val isValid = allErrors.isEmpty

      ValidationReport(value, ruleResults, isValid, allErrors)
    }

    /**
     * Get all validation rules in this engine.
     */
    def getRules: List[ValidationRule[A]] = rules

    /**
     * Get a description of all validation rules.
     */
    def getDescription: String = rules.map(_.description).mkString("; ")
  }

  object ValidationEngine {

    /**
     * Create an empty validation engine.
     */
    def empty[A]: ValidationEngine[A] = new ValidationEngine(Nil)

    /**
     * Create a validation engine with initial rules.
     */
    def apply[A](rules: ValidationRule[A]*): ValidationEngine[A] =
      new ValidationEngine(rules.toList)
  }

  /**
   * Result of applying a single validation rule.
   */
  case class ValidationRuleResult[A](
                                      rule: ValidationRule[A],
                                      result: ValidationResult[A]
                                    ) {
    def isValid: Boolean = result.isValid

    def errors: List[ValidationError] = result.fold(_.toList, _ => Nil)
  }

  /**
   * Comprehensive validation report.
   */
  case class ValidationReport[A](
                                  value: A,
                                  ruleResults: List[ValidationRuleResult[A]],
                                  isValid: Boolean,
                                  errors: List[ValidationError]
                                ) {

    /**
     * Get errors by severity.
     */
    def errorsBySeverity: Map[ValidationSeverity, List[ValidationError]] =
      ruleResults.groupBy(_.rule.severity).view.mapValues(_.flatMap(_.errors)).toMap

    /**
     * Get the highest severity error.
     */
    def highestSeverity: Option[ValidationSeverity] =
      errorsBySeverity.keys.maxOption

    /**
     * Get summary statistics.
     */
    def summary: ValidationSummary = ValidationSummary(
      totalRules = ruleResults.length,
      passedRules = ruleResults.count(_.isValid),
      failedRules = ruleResults.count(!_.isValid),
      totalErrors = errors.length,
      errorsBySeverity = errorsBySeverity.view.mapValues(_.length).toMap
    )
  }

  /**
   * Validation summary statistics.
   */
  case class ValidationSummary(
                                totalRules: Int,
                                passedRules: Int,
                                failedRules: Int,
                                totalErrors: Int,
                                errorsBySeverity: Map[ValidationSeverity, Int]
                              ) {
    def successRate: Double = if (totalRules > 0) passedRules.toDouble / totalRules else 0.0
  }

  // ===============================
  // DOMAIN-SPECIFIC VALIDATORS
  // ===============================

  /**
   * Validators for FlowForge domain types. These provide ready-to-use validation for common data
   * engineering scenarios.
   */
  object DomainValidators {

    /**
     * Validate pipeline configuration.
     */
    def validatePipelineConfig(
                                config: Map[String, String]
                              ): ValidationResult[Map[String, String]] = {
      val nameValidation = config.get("name") match {
        case Some(name) => Validators.nonEmpty("name").validate(name).map(_ => config)
        case None => RequiredFieldError("name").invalidNel
      }

      val typeValidation = config.get("type") match {
        case Some(pipelineType) =>
          WorkflowType.fromString(pipelineType) match {
            case Some(_) => config.validNel
            case None => FormatError("type", "valid workflow type", pipelineType).invalidNel
          }
        case None => RequiredFieldError("type").invalidNel
      }

      (nameValidation, typeValidation).mapN((_, _) => config)
    }

    /**
     * Validate data source configuration.
     */
    def validateDataSourceConfig(
                                  config: Map[String, String]
                                ): ValidationResult[Map[String, String]] = {
      val pathValidation = config.get("path") match {
        case Some(path) => Validators.nonEmpty("path").validate(path).map(_ => config)
        case None => RequiredFieldError("path").invalidNel
      }

      val formatValidation = config.get("format") match {
        case Some(format) =>
          ContentType.fromString(format) match {
            case Some(_) => config.validNel
            case None => FormatError("format", "valid content type", format).invalidNel
          }
        case None => RequiredFieldError("format").invalidNel
      }

      (pathValidation, formatValidation).mapN((_, _) => config)
    }

    /**
     * Validate S3 configuration.
     */
    def validateS3Config(bucket: String, key: String): ValidationResult[(String, String)] = {
      val bucketValidation = Refined.validS3Bucket(bucket) match {
        case Some(_) => bucket.validNel
        case None => FormatError("bucket", "valid S3 bucket name", bucket).invalidNel
      }

      val keyValidation = if (!key.trim.isEmpty && !key.startsWith("/")) {
        key.validNel
      } else {
        FormatError("key", "valid S3 key (no leading slash)", key).invalidNel
      }

      (bucketValidation, keyValidation).mapN((_, _))
    }

    /**
     * Validate GCS configuration.
     */
    def validateGcsConfig(path: String): ValidationResult[String] =
      Refined.validGcsPath(path) match {
        case Some(_) => path.validNel
        case None => FormatError("path", "valid GCS path (gs://bucket/path)", path).invalidNel
      }

    /**
     * Validate table name.
     */
    def validateTableName(tableName: String): ValidationResult[String] =
      Refined.validTableName(tableName) match {
        case Some(_) => tableName.validNel
        case None =>
          FormatError(
            "tableName",
            "valid table name (alphanumeric + underscore)",
            tableName
          ).invalidNel
      }

    /**
     * Validate batch size.
     */
    def validateBatchSize(size: Int): ValidationResult[BatchSize] =
      BatchSize(size) match {
        case Some(batchSize) => batchSize.validNel
        case None => RangeError("batchSize", Some(1), None, size).invalidNel
      }

    /**
     * Validate refresh type.
     */
    def validateRefreshType(refreshType: String): ValidationResult[RefreshType] =
      RefreshType.fromString(refreshType) match {
        case Some(rt) => rt.validNel
        case None => FormatError("refreshType", "valid refresh type", refreshType).invalidNel
      }
  }

  // ===============================
  // SYNTAX EXTENSIONS
  // ===============================

  /**
   * Syntax extensions for convenient validation.
   */
  implicit class ValidationSyntax[A](private val value: A) extends AnyVal {

    /**
     * Validate using a single rule.
     */
    def validateWith(rule: ValidationRule[A]): ValidationResult[A] = rule.validate(value)

    /**
     * Validate using multiple rules.
     */
    def validateWith(rules: ValidationRule[A]*): ValidationResult[A] =
      ValidationEngine(rules: _*).validate(value)

    /**
     * Validate using a validation engine.
     */
    def validateWith(engine: ValidationEngine[A]): ValidationResult[A] = engine.validate(value)

    /**
     * Get a validation report.
     */
    def validateWithReport(engine: ValidationEngine[A]): ValidationReport[A] =
      engine.validateWithReport(value)
  }

  /**
   * Syntax for building validation rules.
   */
  implicit class ValidationRuleSyntax[A](private val rule: ValidationRule[A]) extends AnyVal {

    /**
     * Make this rule conditional.
     */
    def when(condition: A => Boolean): ValidationRule[A] = ConditionalRule(rule, condition)

    /**
     * Combine with another rule using AND.
     */
    def &&[B >: A](other: ValidationRule[B]): ValidationRule[B] = rule.and(other)

    /**
     * Combine with another rule using OR.
     */
    def ||[B >: A](other: ValidationRule[B]): ValidationRule[B] = rule.or(other)
  }

  // ===============================
  // VALIDATION HELPERS
  // ===============================

  /**
   * Helper functions for common validation patterns.
   */
  object ValidationHelpers {

    /**
     * Convert a Try to a ValidationResult.
     */
    def fromTry[A](field: String, tryValue: Try[A]): ValidationResult[A] = tryValue match {
      case Success(value) => value.validNel
      case Failure(exception) => CustomError(field, exception.getMessage).invalidNel
    }

    /**
     * Convert an Option to a ValidationResult.
     */
    def fromOption[A](
                       field: String,
                       option: Option[A],
                       errorMessage: String = "Value is required"
                     ): ValidationResult[A] =
      option match {
        case Some(value) => value.validNel
        case None => CustomError(field, errorMessage).invalidNel
      }

    /**
     * Convert an Either to a ValidationResult.
     */
    def fromEither[A](field: String, either: Either[String, A]): ValidationResult[A] =
      either match {
        case Right(value) => value.validNel
        case Left(error) => CustomError(field, error).invalidNel
      }

    /**
     * Validate all items in a list.
     */
    def validateList[A](
                         field: String,
                         list: List[A],
                         rule: ValidationRule[A]
                       ): ValidationResult[List[A]] =
      list.zipWithIndex.traverse { case (item, index) =>
        rule
          .validate(item)
          .leftMap(
            _.map(error =>
              error.withContext("index", index.toString).withContext("listField", field)
            )
          )
      }

    /**
     * Validate all values in a map.
     */
    def validateMap[K, V](
                           field: String,
                           map: Map[K, V],
                           valueRule: ValidationRule[V]
                         ): ValidationResult[Map[K, V]] =
      map.toList.traverse { case (key, value) =>
        valueRule
          .validate(value)
          .leftMap(
            _.map(error => error.withContext("key", key.toString).withContext("mapField", field))
          )
          .map(validValue => key -> validValue)
      }.map(_.toMap)
  }
}
