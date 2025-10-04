package com.flowforge.core.patterns

import com.flowforge.core.patterns.ValidationTypes._
import com.flowforge.core.types.ValidationError
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

/**
 * Comprehensive test suite for NamedValidationRule.
 *
 * Coverage targets:
 *   - Statement coverage: 100%
 *   - Branch coverage: 100%
 *   - All edge cases and error paths
 */
class NamedValidationRuleSpec extends AnyFunSuite with Matchers {

  test("validate runs the validator function") {
    val validator: Int => ValidationResult[Int] =
      x => if (x > 0) valid(x) else invalid(TestErrors.error("must be positive"))

    val rule = NamedValidationRule("positive-check", validator)

    rule.validate(5).isValid shouldBe true
    rule.validate(-5).isInvalid shouldBe true
  }

  test("validate returns original value on success") {
    val validator: String => ValidationResult[String] =
      s => if (s.nonEmpty) valid(s) else invalid(TestErrors.error("must not be empty"))

    val rule   = NamedValidationRule("non-empty-check", validator)
    val input  = "hello"
    val result = rule.validate(input)

    getValue(result) shouldBe Some(input)
  }

  test("validate returns error on failure") {
    val error                                   = TestErrors.error("must be even")
    val validator: Int => ValidationResult[Int] = x => if (x % 2 == 0) valid(x) else invalid(error)

    val rule   = NamedValidationRule("even-check", validator)
    val result = rule.validate(5)

    result.isInvalid shouldBe true
    getErrors(result).map(_.head) shouldBe Some(error)
  }

  test("name is stored correctly") {
    val validator: Int => ValidationResult[Int] = valid
    val rule                                    = NamedValidationRule("test-rule", validator)

    rule.name shouldBe "test-rule"
  }

  test("combine creates new rule with concatenated name") {
    val validator1: Int => ValidationResult[Int] =
      x => if (x > 0) valid(x) else invalid(TestErrors.error("must be positive"))

    val validator2: Int => ValidationResult[Int] =
      x => if (x < 100) valid(x) else invalid(TestErrors.error("must be < 100"))

    val rule1 = NamedValidationRule("positive", validator1)
    val rule2 = NamedValidationRule("less-than-100", validator2)

    val combined = rule1.combine(rule2)
    combined.name shouldBe "positive+less-than-100"
  }

  test("combine validates using both validators") {
    val validator1: Int => ValidationResult[Int] =
      x => if (x > 0) valid(x) else invalid(TestErrors.error("must be positive"))

    val validator2: Int => ValidationResult[Int] =
      x => if (x < 100) valid(x) else invalid(TestErrors.error("must be < 100"))

    val rule1 = NamedValidationRule("positive", validator1)
    val rule2 = NamedValidationRule("less-than-100", validator2)

    val combined = rule1.combine(rule2)

    combined.validate(50).isValid shouldBe true
    combined.validate(-5).isInvalid shouldBe true
    combined.validate(150).isInvalid shouldBe true
  }

  test("combine accumulates errors from both validators") {
    val validator1: Int => ValidationResult[Int] =
      x => if (x > 0) valid(x) else invalid(TestErrors.error("must be positive"))

    val validator2: Int => ValidationResult[Int] =
      x => if (x % 2 == 0) valid(x) else invalid(TestErrors.error("must be even"))

    val rule1 = NamedValidationRule("positive", validator1)
    val rule2 = NamedValidationRule("even", validator2)

    val combined = rule1.combine(rule2)
    val result   = combined.validate(-5) // Fails both rules

    result.isInvalid shouldBe true
    getErrors(result).map(_.size) shouldBe Some(2)
  }

  test("combine with passing first and failing second") {
    val validator1: Int => ValidationResult[Int] =
      x => if (x > 0) valid(x) else invalid(TestErrors.error("must be positive"))

    val validator2: Int => ValidationResult[Int] =
      x => if (x < 10) valid(x) else invalid(TestErrors.error("must be < 10"))

    val rule1 = NamedValidationRule("positive", validator1)
    val rule2 = NamedValidationRule("small", validator2)

    val combined = rule1.combine(rule2)
    val result   = combined.validate(50) // Passes first, fails second

    result.isInvalid shouldBe true
    getErrors(result).map(_.size) shouldBe Some(1)
  }

  test("combine with failing first and passing second") {
    val validator1: Int => ValidationResult[Int] =
      x => if (x > 0) valid(x) else invalid(TestErrors.error("must be positive"))

    val validator2: Int => ValidationResult[Int] =
      x => if (x < 10) valid(x) else invalid(TestErrors.error("must be < 10"))

    val rule1 = NamedValidationRule("positive", validator1)
    val rule2 = NamedValidationRule("small", validator2)

    val combined = rule1.combine(rule2)
    val result   = combined.validate(-5) // Fails first, passes second (< 10)

    result.isInvalid shouldBe true
    getErrors(result).map(_.size) shouldBe Some(1)
  }

  test("combine returns original value when both validators pass") {
    val validator1: Int => ValidationResult[Int] =
      x => if (x > 0) valid(x) else invalid(TestErrors.error("must be positive"))

    val validator2: Int => ValidationResult[Int] =
      x => if (x < 100) valid(x) else invalid(TestErrors.error("must be < 100"))

    val rule1 = NamedValidationRule("positive", validator1)
    val rule2 = NamedValidationRule("small", validator2)

    val combined = rule1.combine(rule2)
    val input    = 50
    val result   = combined.validate(input)

    getValue(result) shouldBe Some(input)
  }

  test("multiple combine operations") {
    val validator1: Int => ValidationResult[Int] =
      x => if (x > 0) valid(x) else invalid(TestErrors.error("must be positive"))

    val validator2: Int => ValidationResult[Int] =
      x => if (x % 2 == 0) valid(x) else invalid(TestErrors.error("must be even"))

    val validator3: Int => ValidationResult[Int] =
      x => if (x < 1000) valid(x) else invalid(TestErrors.error("must be < 1000"))

    val rule1 = NamedValidationRule("positive", validator1)
    val rule2 = NamedValidationRule("even", validator2)
    val rule3 = NamedValidationRule("small", validator3)

    val combined = rule1.combine(rule2).combine(rule3)

    combined.name shouldBe "positive+even+small"
    combined.validate(100).isValid shouldBe true
    combined.validate(-2).isInvalid shouldBe true
    combined.validate(5).isInvalid shouldBe true
    combined.validate(2000).isInvalid shouldBe true
  }

  test("validate works with complex domain objects") {
    case class User(name: String, age: Int)

    val validator: User => ValidationResult[User] = user =>
      if (user.age >= 18 && user.name.nonEmpty) valid(user)
      else invalid(TestErrors.error("invalid user"))

    val rule = NamedValidationRule("adult-user-validator", validator)

    rule.validate(User("Alice", 25)).isValid shouldBe true
    rule.validate(User("Bob", 17)).isInvalid shouldBe true
    rule.validate(User("", 25)).isInvalid shouldBe true
  }

  test("combine works with complex domain objects") {
    case class Product(name: String, price: Double)

    val nameValidator: Product => ValidationResult[Product] =
      p => if (p.name.nonEmpty) valid(p) else invalid(TestErrors.error("name required"))

    val priceValidator: Product => ValidationResult[Product] =
      p => if (p.price > 0) valid(p) else invalid(TestErrors.error("price must be positive"))

    val rule1 = NamedValidationRule("name-check", nameValidator)
    val rule2 = NamedValidationRule("price-check", priceValidator)

    val combined = rule1.combine(rule2)

    combined.validate(Product("Item", 10.0)).isValid shouldBe true
    combined.validate(Product("", 10.0)).isInvalid shouldBe true
    combined.validate(Product("Item", -5.0)).isInvalid shouldBe true
    combined.validate(Product("", -5.0)).isInvalid shouldBe true
  }

  test("validator that always passes") {
    val alwaysPass: Int => ValidationResult[Int] = valid
    val rule                                     = NamedValidationRule("always-pass", alwaysPass)

    rule.validate(Int.MinValue).isValid shouldBe true
    rule.validate(0).isValid shouldBe true
    rule.validate(Int.MaxValue).isValid shouldBe true
  }

  test("validator that always fails") {
    val error                                    = TestErrors.error("always fails")
    val alwaysFail: Int => ValidationResult[Int] = _ => invalid(error)
    val rule                                     = NamedValidationRule("always-fail", alwaysFail)

    rule.validate(Int.MinValue).isInvalid shouldBe true
    rule.validate(0).isInvalid shouldBe true
    rule.validate(Int.MaxValue).isInvalid shouldBe true
  }

  test("combine with always-pass validator") {
    val alwaysPass: Int => ValidationResult[Int] = valid
    val conditionalValidator: Int => ValidationResult[Int] =
      x => if (x > 0) valid(x) else invalid(TestErrors.error("must be positive"))

    val rule1 = NamedValidationRule("always-pass", alwaysPass)
    val rule2 = NamedValidationRule("positive", conditionalValidator)

    val combined = rule1.combine(rule2)

    combined.validate(5).isValid shouldBe true
    combined.validate(-5).isInvalid shouldBe true
  }

  test("combine with always-fail validator") {
    val error                                    = TestErrors.error("always fails")
    val alwaysFail: Int => ValidationResult[Int] = _ => invalid(error)
    val conditionalValidator: Int => ValidationResult[Int] =
      x => if (x > 0) valid(x) else invalid(TestErrors.error("must be positive"))

    val rule1 = NamedValidationRule("always-fail", alwaysFail)
    val rule2 = NamedValidationRule("positive", conditionalValidator)

    val combined = rule1.combine(rule2)

    // Should always fail because first validator always fails
    combined.validate(5).isInvalid shouldBe true
    combined.validate(-5).isInvalid shouldBe true
  }

  test("name with special characters") {
    val validator: Int => ValidationResult[Int] = valid
    val rule                                    = NamedValidationRule("test-rule_v2.0", validator)

    rule.name shouldBe "test-rule_v2.0"
  }

  test("combine preserves name format with special characters") {
    val validator: Int => ValidationResult[Int] = valid
    val rule1                                   = NamedValidationRule("rule-1.0", validator)
    val rule2                                   = NamedValidationRule("rule_2.0", validator)

    val combined = rule1.combine(rule2)
    combined.name shouldBe "rule-1.0+rule_2.0"
  }

  test("validate with Option types") {
    val validator: Option[Int] => ValidationResult[Option[Int]] = opt =>
      opt match {
        case Some(x) if x > 0 => valid(opt)
        case Some(_)          => invalid(TestErrors.error("must be positive"))
        case None             => valid(opt)
      }

    val rule = NamedValidationRule("optional-positive", validator)

    rule.validate(Some(5)).isValid shouldBe true
    rule.validate(Some(-5)).isInvalid shouldBe true
    rule.validate(None).isValid shouldBe true
  }

  test("combine with error accumulation preserves all error details") {
    val error1 = TestErrors.error("error 1")
    val error2 = TestErrors.error("error 2")

    val validator1: Int => ValidationResult[Int] = _ => invalid(error1)
    val validator2: Int => ValidationResult[Int] = _ => invalid(error2)

    val rule1 = NamedValidationRule("rule1", validator1)
    val rule2 = NamedValidationRule("rule2", validator2)

    val combined = rule1.combine(rule2)
    val result   = combined.validate(42)

    val errors = getErrors(result)
    errors.map(_.toList) shouldBe Some(List(error1, error2))
  }
}
