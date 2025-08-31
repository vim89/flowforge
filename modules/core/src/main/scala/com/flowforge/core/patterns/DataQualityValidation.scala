package com.flowforge.core.patterns

import cats.syntax.all._
import com.flowforge.core.patterns.ValidationTypes.QualityValidationResult
import com.flowforge.core.types.ErrorSeverity
import com.flowforge.core.types.ValidationError.QualityViolation

import java.time.Instant
import scala.concurrent.duration.FiniteDuration

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
  ): QualityValidationResult[A] = {
    val now = Instant.now()
    val age = java.time.Duration.between(timestamp, now)
    val maxAgeInMillis = maxAge.toMillis
    
    if (age.toMillis <= maxAgeInMillis) {
      // Can't return A without having an A - this method signature needs fixing
      // For now, return a unit value cast to A as placeholder
      ().asInstanceOf[A].validNel
    } else {
      val violation = QualityViolation(
        constraint = "freshness",
        violatedValue = fieldName,
        threshold = Some(maxAge.toString),
        message = s"Data is too old: ${age.toMillis}ms > ${maxAgeInMillis}ms",
        severity = ErrorSeverity.Error
      )
      violation.invalidNel
    }
  }

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
