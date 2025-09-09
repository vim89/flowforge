package com.flowforge.core.patterns

import cats.syntax.all._
import com.flowforge.core.patterns.ValidationTypes.{ValidationResult, invalid, valid}
import com.flowforge.core.types.ValidationError

/**
 * Fluent builder for creating complex validation rules. Enables readable, composable validation rule
 * construction.
 */
case class ValidationRuleBuilder[A] private (
  private val rules: List[A => ValidationResult[A]] = List.empty,
  private val name: String = "unnamed-validation") {

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
    error: ValidationError,
  ): ValidationRuleBuilder[A] = {
    val validationRule: A => ValidationResult[A] = { value =>
      if (validator(value)) valid(value) else invalid(error)
    }
    copy(rules = rules :+ validationRule)
  }

  /**
   * Add a conditional validation rule.
   */
  def when(
    condition: A => Boolean,
  )(
    validator: A => ValidationResult[A],
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
