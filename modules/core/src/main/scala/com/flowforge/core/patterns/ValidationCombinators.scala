package com.flowforge.core.patterns

import cats.data.ValidatedNel
import cats.syntax.all._
import com.flowforge.core.patterns.ValidationTypes.{ invalid, valid, ConfigValidationResult }
import com.flowforge.core.types.ConfigError

import scala.util.matching.Regex

/**
 * Core validation combinators for building complex validations from simple parts. These combinators enable
 * functional composition of validation logic.
 */
object ValidationCombinators {

  /**
   * Validate that a string is not null or empty.
   */
  def nonEmpty(fieldName: String, value: String): ConfigValidationResult[String] =
    Option(value).filter(_.trim.nonEmpty) match {
      case Some(v) => valid(v)
      case None =>
        invalid(
          ConfigError.InvalidValue(fieldName, Option(value).getOrElse("null"), "non-empty string"),
        )
    }

  /**
   * Validate that an optional string is non-empty if present.
   */
  def nonEmptyOption(
    fieldName: String,
    value: Option[String],
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
    max: Double,
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
    max: Int,
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
    description: String,
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
    collection: List[A],
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
    max: Int,
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
    collection: List[A],
  )(
    validator: A => ValidatedNel[ConfigError, B],
  ): ConfigValidationResult[List[B]] =
    collection.traverse(validator)

  /**
   * Validate that no elements in a collection are duplicates.
   */
  def noDuplicates[A](
    fieldName: String,
    collection: List[A],
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
    condition: Boolean,
  )(
    validator: A => ConfigValidationResult[A],
  ): A => ConfigValidationResult[A] = { value =>
    if (condition) validator(value) else valid(value)
  }

  /**
   * Conditional validation based on the value itself.
   */
  def whenValue[A](
    predicate: A => Boolean,
  )(
    validator: A => ConfigValidationResult[A],
  ): A => ConfigValidationResult[A] = { value =>
    if (predicate(value)) validator(value) else valid(value)
  }

  /**
   * Either-or validation - value must satisfy at least one of the validators.
   */
  def eitherOr[A](
    validator1: A => ConfigValidationResult[A],
    validator2: A => ConfigValidationResult[A],
  ): A => ConfigValidationResult[A] = { value =>
    validator1(value).orElse(validator2(value))
  }

  /**
   * All-of validation - value must satisfy all validators.
   */
  def allOf[A](
    validators: List[A => ConfigValidationResult[A]],
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
