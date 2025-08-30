package com.flowforge.core.patterns

import cats.data.{ NonEmptyList, Validated, ValidatedNel }
import com.flowforge.core.algebra.SchemaError
import com.flowforge.core.types.ValidationError.QualityViolation
import com.flowforge.core.types.{ BusinessError, ConfigError, ValidationError }

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
