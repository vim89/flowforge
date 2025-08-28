package com.flowforge.core.error

import com.flowforge.core.types.Metadata

// ===============================
// VALIDATION ERROR TYPES
// ===============================

/**
 * Comprehensive validation error hierarchy. Each error type provides specific context about what
 * went wrong.
 */
sealed abstract class ValidationError(
                                       val field: String,
                                       val message: String,
                                       val code: String,
                                       val context: Metadata = Map.empty
                                     ) extends Product
  with Serializable {

  /**
   * Add context to a validation error. Enables building rich error information.
   */
  def withContext(key: String, value: String): ValidationError = {
    val newContext = context + (key -> value)
    this match {
      case e: RequiredFieldError => e.copy(context = newContext)
      case e: FormatError => e.copy(context = newContext)
      case e: RangeError => e.copy(context = newContext)
      case e: LengthError => e.copy(context = newContext)
      case e: PatternError => e.copy(context = newContext)
      case e: CustomError => e.copy(context = newContext)
      case e: CrossFieldError => e.copy(context = newContext)
      case e: BusinessRuleError => e.copy(context = newContext)
    }
  }

  /**
   * Convert validation error to a user-friendly message.
   */
  def toUserMessage: String = s"$field: $message"

  /**
   * Convert validation error to a detailed technical message.
   */
  def toTechnicalMessage: String =
    s"ValidationError(field=$field, code=$code, message=$message, context=$context)"
}

/**
 * Required field is missing or null.
 */
case class RequiredFieldError(
                               override val field: String,
                               override val context: Metadata = Map.empty
                             ) extends ValidationError(
  field = field,
  message = s"Field '$field' is required but was not provided",
  code = "REQUIRED_FIELD_MISSING",
  context = context
)

/**
 * Field value has invalid format.
 */
case class FormatError(
                        override val field: String,
                        expectedFormat: String,
                        actualValue: String,
                        override val context: Metadata = Map.empty
                      ) extends ValidationError(
  field = field,
  message =
    s"Field '$field' has invalid format. Expected: $expectedFormat, got: $actualValue",
  code = "INVALID_FORMAT",
  context = context
)

/**
 * Field value is outside expected range.
 */
case class RangeError(
                       override val field: String,
                       min: Option[Double],
                       max: Option[Double],
                       actualValue: Double,
                       override val context: Metadata = Map.empty
                     ) extends ValidationError(
  field = field,
  message = {
    val bounds = (min, max) match {
      case (Some(minVal), Some(maxVal)) => s"between $minVal and $maxVal"
      case (Some(minVal), None) => s"at least $minVal"
      case (None, Some(maxVal)) => s"at most $maxVal"
      case (None, None) => "within valid range"
    }
    s"Field '$field' must be $bounds, got: $actualValue"
  },
  code = "VALUE_OUT_OF_RANGE",
  context = context
)

/**
 * Field value has invalid length.
 */
case class LengthError(
                        override val field: String,
                        minLength: Option[Int],
                        maxLength: Option[Int],
                        actualLength: Int,
                        override val context: Metadata = Map.empty
                      ) extends ValidationError(
  field = field,
  message = {
    val bounds = (minLength, maxLength) match {
      case (Some(min), Some(max)) => s"between $min and $max characters"
      case (Some(min), None) => s"at least $min characters"
      case (None, Some(max)) => s"at most $max characters"
      case (None, None) => "valid length"
    }
    s"Field '$field' must be $bounds, got: $actualLength characters"
  },
  code = "INVALID_LENGTH",
  context = context
)

/**
 * Field value doesn't match expected pattern.
 */
case class PatternError(
                         override val field: String,
                         pattern: String,
                         actualValue: String,
                         override val context: Metadata = Map.empty
                       ) extends ValidationError(
  field = field,
  message = s"Field '$field' must match pattern '$pattern', got: '$actualValue'",
  code = "PATTERN_MISMATCH",
  context = context
)

/**
 * Custom validation error.
 */
case class CustomError(
                        override val field: String,
                        override val message: String,
                        override val code: String = "CUSTOM_VALIDATION_ERROR",
                        override val context: Metadata = Map.empty
                      ) extends ValidationError(field, message, code, context)

/**
 * Cross-field validation error.
 */
case class CrossFieldError(
                            fields: List[String],
                            override val message: String,
                            override val code: String = "CROSS_FIELD_VALIDATION_ERROR",
                            override val context: Metadata = Map.empty
                          ) extends ValidationError(
  field = fields.mkString(","),
  message = message,
  code = code,
  context = context
)

/**
 * Business rule validation error.
 */
case class BusinessRuleError(
                              rule: String,
                              override val field: String,
                              override val message: String,
                              override val context: Metadata = Map.empty
                            ) extends ValidationError(
  field = field,
  message = s"Business rule '$rule' violation: $message",
  code = "BUSINESS_RULE_VIOLATION",
  context = context
)

/**
 * Decode error.
 */
case class DecodeError(
                        override val field: String,
                        override val context: Metadata = Map.empty
                      ) extends ValidationError(
  field = field,
  message = s"Cannot decode '$field'",
  code = "DECODE_ERROR",
  context = context
)

/**
 * Timeout error.
 */
case class TimeoutError(
                         override val field: String,
                         error: Throwable,
                         override val context: Metadata = Map.empty
                       ) extends ValidationError(
  field = field,
  message = s"Time out, duration: $field. [${error.getMessage}]",
  code = "TIME_OUT",
  context = context
)

