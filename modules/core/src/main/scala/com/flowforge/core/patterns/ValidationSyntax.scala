package com.flowforge.core.patterns

import cats.data.NonEmptyList
import cats.syntax.all._
import com.flowforge.core.patterns.ValidationTypes.{ invalid, valid, ValidationResult }
import com.flowforge.core.types.ValidationError

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
