package com.flowforge.core.patterns

import cats.syntax.all._
import com.flowforge.core.patterns.ValidationTypes.ValidationResult

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
