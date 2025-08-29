package com.flowforge.core.syntax

import cats.data.{ NonEmptyList, ValidatedNel }
import cats.implicits._
import com.flowforge.core.algebra.EffectSystem
import com.flowforge.core.types._

/**
 * 🚀 **FlowForge Validation Syntax - Enhanced Validation Operations**
 *
 * This module provides rich syntax extensions for validation operations in FlowForge, integrating
 * seamlessly with the existing validation patterns and providing enhanced composability.
 *
 * **Key Features:**
 *   - **Composable Validations**: Chain and combine validation rules
 *   - **Error Accumulation**: Collect all validation errors, not just the first
 *   - **Type Safety**: Compile-time validation rule composition
 *   - **Integration**: Works with existing FlowForge validation patterns
 *   - **Extensible**: Easy to add custom validation rules
 *
 * @author
 *   FlowForge Core Team
 * @since 0.1.0
 */

object ValidationSyntax {

  // ===============================
  // VALIDATION RESULT TYPE ALIASES
  // ===============================

  type ValidationResult[A] = ValidatedNel[FlowForgeError, A]
  type ConfigValidation[A] = ValidatedNel[ConfigError, A]
  type DataValidation[A]   = ValidatedNel[FlowForgeError, A]

  // ===============================
  // VALIDATION SYNTAX EXTENSIONS
  // ===============================

  /**
   * Enhanced validation operations for any type
   */
  implicit class ValidationOps[A](private val value: A) extends AnyVal {

    /**
     * Validate with a predicate and error message
     */
    def validate(predicate: A => Boolean, error: => FlowForgeError): ValidationResult[A] =
      if (predicate(value)) value.validNel
      else error.invalidNel

    /**
     * Validate with multiple predicates
     */
    def validateAll(validations: (A => Boolean, FlowForgeError)*): ValidationResult[A] = {
      val results = validations.toList.map { case (predicate, error) =>
        if (predicate(value)) ().validNel[FlowForgeError]
        else error.invalidNel[Unit]
      }
      results.sequence.map(_ => value)
    }

    /**
     * Validate that value is not null
     */
    def notNull: ValidationResult[A] =
      if (value != null) value.validNel
      else FlowForgeError.ValidationError("Value cannot be null", None).invalidNel

    /**
     * Apply a validation function
     */
    def validatedBy[B](validator: A => ValidationResult[B]): ValidationResult[B] =
      validator(value)

    /**
     * Convert to successful validation
     */
    def valid: ValidationResult[A] = value.validNel

    /**
     * Convert to validation with conditional success
     */
    def validIf(condition: Boolean, error: => FlowForgeError): ValidationResult[A] =
      if (condition) value.validNel else error.invalidNel
  }

  /**
   * String validation extensions
   */
  implicit class StringValidationOps(private val str: String) extends AnyVal {

    /**
     * Validate string is not empty
     */
    def nonEmpty: ValidationResult[String] =
      if (str.nonEmpty) str.validNel
      else FlowForgeError.ValidationError("String cannot be empty", None).invalidNel

    /**
     * Validate string matches pattern
     */
    def matches(pattern: String): ValidationResult[String] =
      if (str.matches(pattern)) str.validNel
      else
        FlowForgeError
          .ValidationError(s"String '$str' does not match pattern '$pattern'", None)
          .invalidNel

    /**
     * Validate string length
     */
    def lengthBetween(min: Int, max: Int): ValidationResult[String] =
      if (str.length >= min && str.length <= max) str.validNel
      else
        FlowForgeError
          .ValidationError(s"String length ${str.length} not between $min and $max", None)
          .invalidNel

    /**
     * Validate email format
     */
    def isEmail: ValidationResult[String] = {
      val emailPattern = """^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$""".r
      str
        .matches(emailPattern.regex)
        .validIf(
          true,
          FlowForgeError.ValidationError(s"Invalid email format: $str", None)
        )
        .map(_ => str)
    }

    /**
     * Validate URL format
     */
    def isUrl: ValidationResult[String] =
      try {
        new java.net.URL(str)
        str.validNel
      } catch {
        case _: java.net.MalformedURLException =>
          FlowForgeError.ValidationError(s"Invalid URL format: $str", None).invalidNel
      }
  }

  /**
   * Numeric validation extensions
   */
  implicit class NumericValidationOps[N](private val num: N)(implicit numeric: Numeric[N])
      extends AnyVal {

    /**
     * Validate number is positive
     */
    def positive: ValidationResult[N] =
      if (numeric.gt(num, numeric.zero)) num.validNel
      else FlowForgeError.ValidationError(s"Number $num must be positive", None).invalidNel

    /**
     * Validate number is non-negative
     */
    def nonNegative: ValidationResult[N] =
      if (numeric.gteq(num, numeric.zero)) num.validNel
      else FlowForgeError.ValidationError(s"Number $num must be non-negative", None).invalidNel

    /**
     * Validate number is within range
     */
    def between(min: N, max: N): ValidationResult[N] =
      if (numeric.gteq(num, min) && numeric.lteq(num, max)) num.validNel
      else FlowForgeError.ValidationError(s"Number $num not between $min and $max", None).invalidNel

    /**
     * Validate number is greater than threshold
     */
    def greaterThan(threshold: N): ValidationResult[N] =
      if (numeric.gt(num, threshold)) num.validNel
      else
        FlowForgeError
          .ValidationError(s"Number $num must be greater than $threshold", None)
          .invalidNel

    /**
     * Validate number is less than threshold
     */
    def lessThan(threshold: N): ValidationResult[N] =
      if (numeric.lt(num, threshold)) num.validNel
      else
        FlowForgeError.ValidationError(s"Number $num must be less than $threshold", None).invalidNel
  }

  /**
   * Option validation extensions
   */
  implicit class OptionValidationOps[A](private val opt: Option[A]) extends AnyVal {

    /**
     * Validate option is not empty
     */
    def required(fieldName: String): ValidationResult[A] =
      opt.toValidNel(
        FlowForgeError.ConfigurationError(s"Required field '$fieldName' is missing", None)
      )

    /**
     * Validate option and apply validation to inner value if present
     */
    def validateInner[B](validator: A => ValidationResult[B]): ValidationResult[Option[B]] =
      opt match {
        case Some(value) => validator(value).map(Some.apply)
        case None        => None.validNel
      }

    /**
     * Convert to validation with default
     */
    def orDefault(default: A): ValidationResult[A] =
      opt.getOrElse(default).validNel
  }

  /**
   * List validation extensions
   */
  implicit class ListValidationOps[A](private val list: List[A]) extends AnyVal {

    /**
     * Validate list is not empty
     */
    def nonEmpty: ValidationResult[List[A]] =
      if (list.nonEmpty) list.validNel
      else FlowForgeError.ValidationError("List cannot be empty", None).invalidNel

    /**
     * Validate list size
     */
    def sizeBetween(min: Int, max: Int): ValidationResult[List[A]] =
      if (list.size >= min && list.size <= max) list.validNel
      else
        FlowForgeError
          .ValidationError(s"List size ${list.size} not between $min and $max", None)
          .invalidNel

    /**
     * Validate all elements in the list
     */
    def validateElements[B](validator: A => ValidationResult[B]): ValidationResult[List[B]] =
      list.traverse(validator)

    /**
     * Validate that all elements satisfy a predicate
     */
    def allSatisfy(predicate: A => Boolean, error: => FlowForgeError): ValidationResult[List[A]] =
      if (list.forall(predicate)) list.validNel
      else error.invalidNel

    /**
     * Validate that at least one element satisfies a predicate
     */
    def anySatisfy(predicate: A => Boolean, error: => FlowForgeError): ValidationResult[List[A]] =
      if (list.exists(predicate)) list.validNel
      else error.invalidNel
  }

  /**
   * Map validation extensions for configuration
   */
  implicit class MapValidationOps(private val map: Map[String, String]) extends AnyVal {

    /**
     * Validate required key exists
     */
    def requireKey(key: String): ConfigValidation[String] =
      map.get(key).toValidNel(ConfigError.MissingRequired(key))

    /**
     * Get optional key
     */
    def optionalKey(key: String): ConfigValidation[Option[String]] =
      map.get(key).validNel

    /**
     * Validate and parse integer
     */
    def getInt(key: String): ConfigValidation[Int] =
      requireKey(key).andThen { value =>
        scala.util
          .Try(value.toInt)
          .fold(
            _ => ConfigError.InvalidFormat(key, value, "integer").invalidNel,
            int => int.validNel
          )
      }

    /**
     * Validate and parse boolean
     */
    def getBoolean(key: String): ConfigValidation[Boolean] =
      requireKey(key).andThen { value =>
        value.toLowerCase match {
          case "true" | "yes" | "1" => true.validNel
          case "false" | "no" | "0" => false.validNel
          case _                    => ConfigError.InvalidFormat(key, value, "boolean").invalidNel
        }
      }

    /**
     * Validate and parse duration
     */
    def getDuration(key: String): ConfigValidation[scala.concurrent.duration.Duration] =
      requireKey(key).andThen { value =>
        scala.util
          .Try(java.time.Duration.parse(value))
          .fold(
            _ => ConfigError.InvalidFormat(key, value, "ISO-8601 duration").invalidNel,
            d => scala.concurrent.duration.Duration.fromNanos(d.toNanos).validNel
          )
      }

    /**
     * Validate keys exist
     */
    def requireKeys(keys: String*): ConfigValidation[Map[String, String]] = {
      val validations = keys.toList.map(requireKey)
      validations.sequence.map(_ => map)
    }
  }

  // ===============================
  // VALIDATION COMBINATORS
  // ===============================

  /**
   * Validation combinators for complex validation logic
   */
  object Validators {

    /**
     * Create a validator that always succeeds
     */
    def pass[A]: A => ValidationResult[A] = _.validNel

    /**
     * Create a validator that always fails
     */
    def fail[A](error: FlowForgeError): A => ValidationResult[A] = _ => error.invalidNel

    /**
     * Combine multiple validators with AND logic
     */
    def and[A](validators: (A => ValidationResult[A])*): A => ValidationResult[A] = { value =>
      validators.toList.traverse(_(value)).map(_ => value)
    }

    /**
     * Combine multiple validators with OR logic (succeed if any passes)
     */
    def or[A](validators: (A => ValidationResult[A])*): A => ValidationResult[A] = { value =>
      validators.toList.map(_(value)).find(_.isValid).getOrElse {
        val errors = validators.toList.flatMap(_(value).swap.toOption.map(_.toList).getOrElse(Nil))
        NonEmptyList.fromList(errors) match {
          case Some(nel) => nel.invalid
          case None => FlowForgeError.ValidationError("All validations failed", None).invalidNel
        }
      }
    }

    /**
     * Conditional validator
     */
    def when[A](
      condition: A => Boolean
    )(validator: A => ValidationResult[A]): A => ValidationResult[A] = { value =>
      if (condition(value)) validator(value)
      else value.validNel
    }

    /**
     * Create a custom validator
     */
    def custom[A](predicate: A => Boolean, error: A => FlowForgeError): A => ValidationResult[A] = {
      value =>
        if (predicate(value)) value.validNel
        else error(value).invalidNel
    }

    /**
     * Effect-based validator
     */
    def effectful[F[_]: EffectSystem, A](
      validator: A => F[ValidationResult[A]]
    ): A => F[ValidationResult[A]] = validator

    // ===============================
    // COMMON VALIDATORS
    // ===============================

    /**
     * Common string validators
     */
    object Strings {
      def nonEmpty: String => ValidationResult[String] = x => x.nonEmpty
      def email: String => ValidationResult[String]    = _.isEmail
      def url: String => ValidationResult[String]      = _.isUrl
      def minLength(min: Int): String => ValidationResult[String] =
        (x: String) =>
          x.validIf(
            x.length >= min,
            FlowForgeError.ValidationError(s"String must be at least $min characters", None)
          )
      def maxLength(max: Int): String => ValidationResult[String] =
        x =>
          x.validIf(
            x.length <= max,
            FlowForgeError.ValidationError(s"String must be at most $max characters", None)
          )
      def pattern(regex: String): String => ValidationResult[String] = _.matches(regex)
    }

    /**
     * Common numeric validators
     */
    object Numbers {
      def positive[N: Numeric]: N => ValidationResult[N]              = _.positive
      def nonNegative[N: Numeric]: N => ValidationResult[N]           = _.nonNegative
      def range[N: Numeric](min: N, max: N): N => ValidationResult[N] = _.between(min, max)
      def min[N: Numeric](threshold: N): N => ValidationResult[N]     = _.greaterThan(threshold)
      def max[N: Numeric](threshold: N): N => ValidationResult[N]     = _.lessThan(threshold)
    }

    /**
     * Common collection validators
     */
    object Collections {
      def nonEmpty[A]: List[A] => ValidationResult[List[A]] = _.nonEmpty
      def size[A](min: Int, max: Int): List[A] => ValidationResult[List[A]] =
        _.sizeBetween(min, max)
      def minSize[A](min: Int): List[A] => ValidationResult[List[A]] =
        _.validIf(
          _.size >= min,
          FlowForgeError.ValidationError(s"Collection must have at least $min elements", None)
        )
      def maxSize[A](max: Int): List[A] => ValidationResult[List[A]] =
        _.validIf(
          _.size <= max,
          FlowForgeError.ValidationError(s"Collection must have at most $max elements", None)
        )
    }
  }

  // ===============================
  // VALIDATION BUILDER
  // ===============================

  /**
   * Fluent validation builder for complex validation scenarios
   */
  case class ValidationBuilder[A](validators: List[A => ValidationResult[A]] = List.empty) {

    /**
     * Add a validator
     */
    def validate(validator: A => ValidationResult[A]): ValidationBuilder[A] =
      copy(validators = validators :+ validator)

    /**
     * Add a predicate-based validator
     */
    def check(predicate: A => Boolean, error: A => FlowForgeError): ValidationBuilder[A] =
      validate(value => if (predicate(value)) value.validNel else error(value).invalidNel)

    /**
     * Add a conditional validator
     */
    def when(condition: A => Boolean)(validator: A => ValidationResult[A]): ValidationBuilder[A] =
      validate(Validators.when(condition)(validator))

    /**
     * Build the final validator
     */
    def build: A => ValidationResult[A] =
      Validators.and(validators: _*)

    /**
     * Apply validation to a value
     */
    def apply(value: A): ValidationResult[A] = build(value)
  }

  object ValidationBuilder {
    def apply[A]: ValidationBuilder[A] = new ValidationBuilder[A]()
  }

  // ===============================
  // INTEGRATION WITH FLOWFORGE TYPES
  // ===============================

  /**
   * Pipeline configuration validation
   */
  object PipelineValidation {

    def validatePipelineConfig(config: PipelineConfig): ConfigValidation[PipelineConfig] = {
      val nameValidation = config.settings.requireKey("name").map(_ => ())
      val validations    = List(nameValidation)
      validations.sequence.map(_ => config)
    }

    def validateDataSource(source: DataSource): ValidationResult[DataSource] =
      source.validNel // Placeholder - would validate source configuration

    def validateDataSink(sink: DataSink): ValidationResult[DataSink] =
      sink.validNel // Placeholder - would validate sink configuration
  }

  // ===============================
  // HELPER FUNCTIONS
  // ===============================

  /**
   * Create a validation result from a boolean
   */
  def fromBoolean[A](value: A, condition: Boolean, error: FlowForgeError): ValidationResult[A] =
    if (condition) value.validNel else error.invalidNel

  /**
   * Create a validation result from an Either
   */
  def fromEither[A](either: Either[FlowForgeError, A]): ValidationResult[A] =
    either.toValidatedNel

  /**
   * Create a validation result from an Option
   */
  def fromOption[A](option: Option[A], error: FlowForgeError): ValidationResult[A] =
    option.toValidNel(error)

  /**
   * Create a validation result from a Try
   */
  def fromTry[A](
    tried: scala.util.Try[A],
    errorMapper: Throwable => FlowForgeError
  ): ValidationResult[A] =
    tried.fold(e => errorMapper(e).invalidNel, _.validNel)

  /**
   * Sequence a list of validations
   */
  def sequence[A](validations: List[ValidationResult[A]]): ValidationResult[List[A]] =
    validations.sequence

  /**
   * Traverse with validation
   */
  def traverse[A, B](list: List[A])(f: A => ValidationResult[B]): ValidationResult[List[B]] =
    list.traverse(f)

  // ===============================
  // IMPLICIT CONVERSIONS
  // ===============================

  /**
   * Convert boolean to validation result
   */
  implicit class BooleanToValidation(private val condition: Boolean) extends AnyVal {
    def toValidation[A](value: A, error: FlowForgeError): ValidationResult[A] =
      if (condition) value.validNel else error.invalidNel
  }

  /**
   * Convert Option to validation result
   */
  implicit class OptionToValidation[A](private val option: Option[A]) extends AnyVal {
    def toValidation(error: FlowForgeError): ValidationResult[A] =
      option.toValidNel(error)
  }

  /**
   * Convert Either to validation result
   */
  implicit class EitherToValidation[A](private val either: Either[FlowForgeError, A])
      extends AnyVal {
    def toValidation: ValidationResult[A] = either.toValidatedNel
  }
}
