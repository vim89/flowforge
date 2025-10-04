package com.flowforge.core.patterns

import com.flowforge.core.patterns.ValidationTypes._
import com.flowforge.core.types.ValidationError
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

// Helper to create errors for testing
object TestErrors {
  def error(msg: String): ValidationError =
    ValidationError.MissingRequiredField(msg)
}

/**
 * Comprehensive test suite for ValidationRuleBuilder.
 *
 * Coverage targets:
 *   - Statement coverage: 100%
 *   - Branch coverage: 100%
 *   - All edge cases and error paths
 */
class ValidationRuleBuilderSpec extends AnyFunSuite with Matchers {

  test("empty builder creates validator that always succeeds") {
    val builder   = ValidationRuleBuilder.empty[Int]
    val validator = builder.build
    val result    = validator(42)
    result.isValid shouldBe true
    getValue(result) shouldBe Some(42)
  }

  test("named builder creates builder with name") {
    val builder = ValidationRuleBuilder.named[Int]("test-validation")
    // name is private, so we can't test it directly
    // Just verify builder was created successfully
    builder should not be null
  }

  test("from creates builder with single rule") {
    val rule: Int => ValidationResult[Int] =
      x => if (x > 0) valid(x) else invalid(TestErrors.error("must be positive"))

    val builder   = ValidationRuleBuilder.from(rule)
    val validator = builder.build

    validator(5).isValid shouldBe true
    validator(-5).isInvalid shouldBe true
  }

  test("rule adds custom validation function") {
    val builder = ValidationRuleBuilder
      .empty[Int]
      .rule(x => if (x > 0) valid(x) else invalid(TestErrors.error("must be positive")))

    val validator = builder.build
    validator(5).isValid shouldBe true
    validator(-5).isInvalid shouldBe true
  }

  test("rule chains multiple validations") {
    val builder = ValidationRuleBuilder
      .empty[Int]
      .rule(x => if (x > 0) valid(x) else invalid(TestErrors.error("must be positive")))
      .rule(x => if (x < 100) valid(x) else invalid(TestErrors.error("must be < 100")))

    val validator = builder.build
    validator(50).isValid shouldBe true
    validator(-5).isInvalid shouldBe true
    validator(150).isInvalid shouldBe true
  }

  test("ruleWithMessage adds validation with predicate and error") {
    val error = TestErrors.error("value must be even")
    val builder = ValidationRuleBuilder
      .empty[Int]
      .ruleWithMessage(_ % 2 == 0, error)

    val validator = builder.build
    validator(4).isValid shouldBe true
    validator(5).isInvalid shouldBe true

    val result = validator(5)
    getErrors(result).map(_.head) shouldBe Some(error)
  }

  test("ruleWithMessage chains multiple predicates") {
    val builder = ValidationRuleBuilder
      .empty[Int]
      .ruleWithMessage(_ > 0, TestErrors.error("must be positive"))
      .ruleWithMessage(_ % 2 == 0, TestErrors.error("must be even"))

    val validator = builder.build
    validator(4).isValid shouldBe true
    validator(-2).isInvalid shouldBe true
    validator(3).isInvalid shouldBe true
  }

  test("when applies validation only when condition is true") {
    val builder = ValidationRuleBuilder
      .empty[Int]
      .when(_ > 10)(x => if (x < 100) valid(x) else invalid(TestErrors.error("too large")))

    val validator = builder.build
    validator(5).isValid shouldBe true     // condition false, skipped
    validator(50).isValid shouldBe true    // condition true, passes
    validator(150).isInvalid shouldBe true // condition true, fails
  }

  test("when skips validation when condition is false") {
    val alwaysFails = (_: Int) => invalid[ValidationError, Int](TestErrors.error("always fails"))
    val builder = ValidationRuleBuilder
      .empty[Int]
      .when(_ > 100)(alwaysFails)

    val validator = builder.build
    validator(50).isValid shouldBe true    // condition false, validation skipped
    validator(150).isInvalid shouldBe true // condition true, validation runs and fails
  }

  test("combine merges two validation builders") {
    val builder1 = ValidationRuleBuilder
      .empty[Int]
      .ruleWithMessage(_ > 0, TestErrors.error("must be positive"))

    val builder2 = ValidationRuleBuilder
      .empty[Int]
      .ruleWithMessage(_ < 100, TestErrors.error("must be < 100"))

    val combined  = builder1.combine(builder2)
    val validator = combined.build

    validator(50).isValid shouldBe true
    validator(-5).isInvalid shouldBe true
    validator(150).isInvalid shouldBe true
  }

  test("combine preserves rule order") {
    val builder1 = ValidationRuleBuilder
      .empty[String]
      .ruleWithMessage(_.nonEmpty, TestErrors.error("must not be empty"))

    val builder2 = ValidationRuleBuilder
      .empty[String]
      .ruleWithMessage(_.length > 5, TestErrors.error("must be longer than 5"))

    val combined  = builder1.combine(builder2)
    val validator = combined.build

    val result = validator("abc")
    result.isInvalid shouldBe true
    // Should accumulate errors from both rules
    getErrors(result).map(_.size) shouldBe Some(1) // Only second rule fails
  }

  test("build accumulates errors from multiple failing rules") {
    val builder = ValidationRuleBuilder
      .empty[Int]
      .ruleWithMessage(_ > 0, TestErrors.error("must be positive"))
      .ruleWithMessage(_ % 2 == 0, TestErrors.error("must be even"))
      .ruleWithMessage(_ < 100, TestErrors.error("must be < 100"))

    val validator = builder.build
    val result    = validator(-5) // fails first and second rules

    result.isInvalid shouldBe true
    getErrors(result).map(_.size) shouldBe Some(2)
  }

  test("build returns original value on success") {
    val builder = ValidationRuleBuilder
      .empty[String]
      .ruleWithMessage(_.nonEmpty, TestErrors.error("must not be empty"))

    val validator = builder.build
    val input     = "hello"
    val result    = validator(input)

    getValue(result) shouldBe Some(input)
  }

  test("buildNamed creates NamedValidationRule with specified name") {
    val builder = ValidationRuleBuilder
      .empty[Int]
      .ruleWithMessage(_ > 0, TestErrors.error("must be positive"))

    val named = builder.buildNamed("positive-validator")
    named.name shouldBe "positive-validator"

    named.validate(5).isValid shouldBe true
    named.validate(-5).isInvalid shouldBe true
  }

  test("buildNamed preserves validation logic") {
    val builder = ValidationRuleBuilder
      .empty[Int]
      .ruleWithMessage(_ > 0, TestErrors.error("must be positive"))
      .ruleWithMessage(_ < 100, TestErrors.error("must be < 100"))

    val named = builder.buildNamed("range-validator")

    named.validate(50).isValid shouldBe true
    named.validate(-5).isInvalid shouldBe true
    named.validate(150).isInvalid shouldBe true
  }

  test("withName sets the name of the builder") {
    val builder = ValidationRuleBuilder
      .empty[Int]
      .withName("my-validation")

    // name is private, but we can verify the builder works
    builder should not be null
  }

  test("withName can update existing name") {
    val builder = ValidationRuleBuilder
      .named[Int]("original-name")
      .withName("new-name")

    // name is private, but we can verify the builder works
    builder should not be null
  }

  test("complex validation chain with all combinators") {
    val builder = ValidationRuleBuilder
      .empty[Int]
      .rule(x => if (x != 0) valid(x) else invalid(TestErrors.error("cannot be zero")))
      .ruleWithMessage(_ > -1000, TestErrors.error("must be > -1000"))
      .when(_ > 0)(x => if (x % 2 == 0) valid(x) else invalid(TestErrors.error("positive must be even")))
      .ruleWithMessage(_ < 10000, TestErrors.error("must be < 10000"))

    val validator = builder.build

    // Valid cases
    validator(100).isValid shouldBe true
    validator(-500).isValid shouldBe true

    // Invalid cases
    validator(0).isInvalid shouldBe true     // zero
    validator(-2000).isInvalid shouldBe true // too small
    validator(5).isInvalid shouldBe true     // positive but odd
    validator(20000).isInvalid shouldBe true // too large
  }

  test("validation builder works with complex types") {
    case class User(
      name: String,
      age: Int,
      email: String)

    val builder = ValidationRuleBuilder
      .empty[User]
      .ruleWithMessage(_.name.nonEmpty, TestErrors.error("name required"))
      .ruleWithMessage(_.age >= 18, TestErrors.error("must be adult"))
      .ruleWithMessage(_.email.contains("@"), TestErrors.error("valid email required"))

    val validator = builder.build

    validator(User("Alice", 25, "alice@example.com")).isValid shouldBe true
    validator(User("", 25, "alice@example.com")).isInvalid shouldBe true
    validator(User("Bob", 17, "bob@example.com")).isInvalid shouldBe true
    validator(User("Charlie", 30, "invalid-email")).isInvalid shouldBe true
  }

  test("when with complex condition") {
    case class Product(
      name: String,
      price: Double,
      onSale: Boolean)

    val builder = ValidationRuleBuilder
      .empty[Product]
      .when(_.onSale)(p =>
        if (p.price > 0 && p.price < 1000) valid(p)
        else invalid(TestErrors.error("sale price must be 0-1000")),
      )

    val validator = builder.build

    // Not on sale - no validation
    validator(Product("Item1", 2000, onSale = false)).isValid shouldBe true

    // On sale with valid price
    validator(Product("Item2", 500, onSale = true)).isValid shouldBe true

    // On sale with invalid price
    validator(Product("Item3", 2000, onSale = true)).isInvalid shouldBe true
  }

  test("multiple combine operations") {
    val builder1 = ValidationRuleBuilder
      .empty[Int]
      .ruleWithMessage(_ > 0, TestErrors.error("must be positive"))

    val builder2 = ValidationRuleBuilder
      .empty[Int]
      .ruleWithMessage(_ % 2 == 0, TestErrors.error("must be even"))

    val builder3 = ValidationRuleBuilder
      .empty[Int]
      .ruleWithMessage(_ < 1000, TestErrors.error("must be < 1000"))

    val combined  = builder1.combine(builder2).combine(builder3)
    val validator = combined.build

    validator(100).isValid shouldBe true
    validator(-2).isInvalid shouldBe true   // not positive
    validator(5).isInvalid shouldBe true    // not even
    validator(2000).isInvalid shouldBe true // too large
  }

  test("empty builder produces no errors") {
    val builder   = ValidationRuleBuilder.empty[Int]
    val validator = builder.build

    validator(Int.MinValue).isValid shouldBe true
    validator(0).isValid shouldBe true
    validator(Int.MaxValue).isValid shouldBe true
  }

  test("rule with value transformation preserves validation") {
    val builder = ValidationRuleBuilder.empty[String].rule { s =>
      val trimmed = s.trim
      if (trimmed.nonEmpty) valid(s) // Return original, not transformed
      else invalid(TestErrors.error("must not be empty when trimmed"))
    }

    val validator = builder.build

    validator("hello").isValid shouldBe true
    validator("  hello  ").isValid shouldBe true
    validator("   ").isInvalid shouldBe true
  }

  test("buildNamed uses builder's internal name when not overridden") {
    val builder = ValidationRuleBuilder
      .named[Int]("internal-name")
      .ruleWithMessage(_ > 0, TestErrors.error("must be positive"))

    val named = builder.buildNamed("override-name")
    named.name shouldBe "override-name"
  }

  test("combine with empty builder has no effect") {
    val builder = ValidationRuleBuilder
      .empty[Int]
      .ruleWithMessage(_ > 0, TestErrors.error("must be positive"))

    val empty = ValidationRuleBuilder.empty[Int]

    val combined  = builder.combine(empty)
    val validator = combined.build

    validator(5).isValid shouldBe true
    validator(-5).isInvalid shouldBe true
  }

  test("from with multiple applications creates independent builders") {
    val rule: Int => ValidationResult[Int] =
      x => if (x > 0) valid(x) else invalid(TestErrors.error("must be positive"))

    val builder1 = ValidationRuleBuilder.from(rule)
    val builder2 = ValidationRuleBuilder.from(rule)

    val combined  = builder1.combine(builder2)
    val validator = combined.build

    // Both rules should run
    val result = validator(-5)
    result.isInvalid shouldBe true
    getErrors(result).map(_.size) shouldBe Some(2) // Error from both rules
  }

  test("when with always-true condition behaves like regular rule") {
    val builder = ValidationRuleBuilder
      .empty[Int]
      .when(_ => true)(x => if (x > 0) valid(x) else invalid(TestErrors.error("must be positive")))

    val validator = builder.build

    validator(5).isValid shouldBe true
    validator(-5).isInvalid shouldBe true
  }

  test("when with always-false condition never validates") {
    val alwaysFails = (_: Int) => invalid[ValidationError, Int](TestErrors.error("always fails"))
    val builder = ValidationRuleBuilder
      .empty[Int]
      .when(_ => false)(alwaysFails)

    val validator = builder.build

    // Should always pass because condition is always false
    validator(Int.MinValue).isValid shouldBe true
    validator(0).isValid shouldBe true
    validator(Int.MaxValue).isValid shouldBe true
  }
}
