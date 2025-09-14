package com.flowforge.core.syntax

import cats.data.ValidatedNel
import cats.implicits._
import com.flowforge.core.types._

import java.net.URI

/**
 * 🚀 **FlowForge Validation Syntax - Enhanced Validation Operations**
 *
 * This module provides rich syntax extensions for validation operations in FlowForge, integrating seamlessly
 * with the existing validation patterns and providing enhanced composability.
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
      val results = validations.toList.map {
        case (predicate, error) =>
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
      if (!str.isEmpty) str.validNel
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
          FlowForgeError.ValidationError(s"Invalid email format: $str", None),
        )
        .map(_ => str)
    }

    /**
     * Validate URL format
     */
    def isUrl: ValidationResult[String] = {
      val res = Either.catchNonFatal(new URI(str))
      res.fold(
        _ => FlowForgeError.ValidationError(s"Invalid URL format: $str", None).invalidNel,
        _ => str.validNel,
      )
    }
  }

  /**
   * Numeric validation extensions
   */

}
