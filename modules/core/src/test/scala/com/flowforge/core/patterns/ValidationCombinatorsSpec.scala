package com.flowforge.core.patterns

import com.flowforge.core.patterns.ValidationTypes._
import com.flowforge.core.types.ConfigError
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

/**
 * Comprehensive test suite for ValidationCombinators.
 *
 * Coverage targets:
 * - Statement coverage: 100%
 * - Branch coverage: 100%
 * - All edge cases and error paths
 */
class ValidationCombinatorsSpec extends AnyFunSuite with Matchers {

  test("nonEmpty validates non-empty strings") {
    val result = ValidationCombinators.nonEmpty("field", "value")
    result.isValid shouldBe true
    getValue(result) shouldBe Some("value")
  }

  test("nonEmpty validates strings with whitespace") {
    val result = ValidationCombinators.nonEmpty("field", "  value  ")
    result.isValid shouldBe true
    getValue(result) shouldBe Some("  value  ")
  }

  test("nonEmpty rejects null") {
    val result = ValidationCombinators.nonEmpty("field", null)
    result.isInvalid shouldBe true
    getErrors(result).map(_.head) should matchPattern {
      case Some(ConfigError.InvalidValue("field", "null", "non-empty string")) =>
    }
  }

  test("nonEmpty rejects empty string") {
    val result = ValidationCombinators.nonEmpty("field", "")
    result.isInvalid shouldBe true
  }

  test("nonEmpty rejects whitespace-only string") {
    val result = ValidationCombinators.nonEmpty("field", "   ")
    result.isInvalid shouldBe true
  }

  test("nonEmptyOption validates Some with non-empty string") {
    val result = ValidationCombinators.nonEmptyOption("field", Some("value"))
    result.isValid shouldBe true
    getValue(result) shouldBe Some(Some("value"))
  }

  test("nonEmptyOption validates None") {
    val result = ValidationCombinators.nonEmptyOption("field", None)
    result.isValid shouldBe true
    getValue(result) shouldBe Some(None)
  }

  test("nonEmptyOption rejects Some with empty string") {
    val result = ValidationCombinators.nonEmptyOption("field", Some(""))
    result.isInvalid shouldBe true
  }

  test("required validates Some") {
    val result = ValidationCombinators.required("field", Some(42))
    result.isValid shouldBe true
    getValue(result) shouldBe Some(42)
  }

  test("required rejects None") {
    val result = ValidationCombinators.required("field", None)
    result.isInvalid shouldBe true
    getErrors(result).map(_.head) should matchPattern {
      case Some(ConfigError.MissingRequired("field")) =>
    }
  }

  test("inRange validates value within range") {
    val result = ValidationCombinators.inRange("field", 5.0, 1.0, 10.0)
    result.isValid shouldBe true
    getValue(result) shouldBe Some(5.0)
  }

  test("inRange validates value at lower bound") {
    val result = ValidationCombinators.inRange("field", 1.0, 1.0, 10.0)
    result.isValid shouldBe true
  }

  test("inRange validates value at upper bound") {
    val result = ValidationCombinators.inRange("field", 10.0, 1.0, 10.0)
    result.isValid shouldBe true
  }

  test("inRange rejects value below range") {
    val result = ValidationCombinators.inRange("field", 0.5, 1.0, 10.0)
    result.isInvalid shouldBe true
    getErrors(result).map(_.head) should matchPattern {
      case Some(ConfigError.OutOfRange("field", "0.5", "1.0", "10.0")) =>
    }
  }

  test("inRange rejects value above range") {
    val result = ValidationCombinators.inRange("field", 11.0, 1.0, 10.0)
    result.isInvalid shouldBe true
  }

  test("intInRange validates integer within range") {
    val result = ValidationCombinators.intInRange("field", 5, 1, 10)
    result.isValid shouldBe true
    getValue(result) shouldBe Some(5)
  }

  test("intInRange validates integer at bounds") {
    ValidationCombinators.intInRange("field", 1, 1, 10).isValid shouldBe true
    ValidationCombinators.intInRange("field", 10, 1, 10).isValid shouldBe true
  }

  test("intInRange rejects integer out of range") {
    ValidationCombinators.intInRange("field", 0, 1, 10).isInvalid shouldBe true
    ValidationCombinators.intInRange("field", 11, 1, 10).isInvalid shouldBe true
  }

  test("positive validates positive number") {
    val result = ValidationCombinators.positive("field", 5.5)
    result.isValid shouldBe true
    getValue(result) shouldBe Some(5.5)
  }

  test("positive rejects zero") {
    val result = ValidationCombinators.positive("field", 0.0)
    result.isInvalid shouldBe true
    getErrors(result).map(_.head) should matchPattern {
      case Some(ConfigError.InvalidValue("field", "0.0", "positive number")) =>
    }
  }

  test("positive rejects negative number") {
    val result = ValidationCombinators.positive("field", -1.0)
    result.isInvalid shouldBe true
  }

  test("nonNegative validates positive number") {
    val result = ValidationCombinators.nonNegative("field", 5.5)
    result.isValid shouldBe true
  }

  test("nonNegative validates zero") {
    val result = ValidationCombinators.nonNegative("field", 0.0)
    result.isValid shouldBe true
    getValue(result) shouldBe Some(0.0)
  }

  test("nonNegative rejects negative number") {
    val result = ValidationCombinators.nonNegative("field", -1.0)
    result.isInvalid shouldBe true
    getErrors(result).map(_.head) should matchPattern {
      case Some(ConfigError.InvalidValue("field", "-1.0", "non-negative number")) =>
    }
  }

  test("matchesPattern validates matching string") {
    val pattern = "^[0-9]+$".r
    val result  = ValidationCombinators.matchesPattern("field", "123", pattern, "digits only")
    result.isValid shouldBe true
    getValue(result) shouldBe Some("123")
  }

  test("matchesPattern rejects non-matching string") {
    val pattern = "^[0-9]+$".r
    val result  = ValidationCombinators.matchesPattern("field", "abc", pattern, "digits only")
    result.isInvalid shouldBe true
    getErrors(result).map(_.head) should matchPattern {
      case Some(ConfigError.InvalidFormat("field", "abc", "digits only")) =>
    }
  }

  test("email validates valid email") {
    val result = ValidationCombinators.email("field", "user@example.com")
    result.isValid shouldBe true
    getValue(result) shouldBe Some("user@example.com")
  }

  test("email validates email with plus sign") {
    val result = ValidationCombinators.email("field", "user+tag@example.com")
    result.isValid shouldBe true
  }

  test("email validates email with subdomain") {
    val result = ValidationCombinators.email("field", "user@mail.example.com")
    result.isValid shouldBe true
  }

  test("email rejects invalid email without @") {
    val result = ValidationCombinators.email("field", "userexample.com")
    result.isInvalid shouldBe true
  }

  test("email rejects invalid email without domain") {
    val result = ValidationCombinators.email("field", "user@")
    result.isInvalid shouldBe true
  }

  test("email rejects invalid email without TLD") {
    val result = ValidationCombinators.email("field", "user@example")
    result.isInvalid shouldBe true
  }

  test("url validates valid HTTP URL") {
    val result = ValidationCombinators.url("field", "http://example.com")
    result.isValid shouldBe true
    getValue(result) shouldBe Some("http://example.com")
  }

  test("url validates valid HTTPS URL") {
    val result = ValidationCombinators.url("field", "https://example.com")
    result.isValid shouldBe true
  }

  test("url validates URL with path") {
    val result = ValidationCombinators.url("field", "https://example.com/path/to/resource")
    result.isValid shouldBe true
  }

  test("url validates URL with subdomain") {
    val result = ValidationCombinators.url("field", "https://api.example.com")
    result.isValid shouldBe true
  }

  test("url rejects invalid URL without protocol") {
    val result = ValidationCombinators.url("field", "example.com")
    result.isInvalid shouldBe true
  }

  test("url rejects FTP URL") {
    val result = ValidationCombinators.url("field", "ftp://example.com")
    result.isInvalid shouldBe true
  }

  test("nonEmptyCollection validates non-empty list") {
    val result = ValidationCombinators.nonEmptyCollection("field", List(1, 2, 3))
    result.isValid shouldBe true
    getValue(result) shouldBe Some(List(1, 2, 3))
  }

  test("nonEmptyCollection validates single element list") {
    val result = ValidationCombinators.nonEmptyCollection("field", List(1))
    result.isValid shouldBe true
  }

  test("nonEmptyCollection rejects empty list") {
    val result = ValidationCombinators.nonEmptyCollection("field", List.empty[Int])
    result.isInvalid shouldBe true
    getErrors(result).map(_.head) should matchPattern {
      case Some(ConfigError.InvalidValue("field", "[]", "non-empty collection")) =>
    }
  }

  test("collectionSize validates collection within bounds") {
    val result = ValidationCombinators.collectionSize("field", List(1, 2, 3), 1, 5)
    result.isValid shouldBe true
    getValue(result) shouldBe Some(List(1, 2, 3))
  }

  test("collectionSize validates collection at lower bound") {
    val result = ValidationCombinators.collectionSize("field", List(1), 1, 5)
    result.isValid shouldBe true
  }

  test("collectionSize validates collection at upper bound") {
    val result = ValidationCombinators.collectionSize("field", List(1, 2, 3, 4, 5), 1, 5)
    result.isValid shouldBe true
  }

  test("collectionSize rejects collection below min size") {
    val result = ValidationCombinators.collectionSize("field", List.empty[Int], 1, 5)
    result.isInvalid shouldBe true
    getErrors(result).map(_.head) should matchPattern {
      case Some(ConfigError.OutOfRange("field", "0", "1", "5")) =>
    }
  }

  test("collectionSize rejects collection above max size") {
    val result = ValidationCombinators.collectionSize("field", List(1, 2, 3, 4, 5, 6), 1, 5)
    result.isInvalid shouldBe true
  }

  test("validateAll validates all elements successfully") {
    val validator = (x: Int) => if (x > 0) valid[ConfigError, Int](x) else invalid(ConfigError.InvalidValue("x", x.toString, "positive"))
    val result    = ValidationCombinators.validateAll(List(1, 2, 3))(validator)
    result.isValid shouldBe true
    getValue(result) shouldBe Some(List(1, 2, 3))
  }

  test("validateAll accumulates errors from multiple elements") {
    val validator = (x: Int) => if (x > 0) valid[ConfigError, Int](x) else invalid(ConfigError.InvalidValue("x", x.toString, "positive"))
    val result    = ValidationCombinators.validateAll(List(-1, 2, -3))(validator)
    result.isInvalid shouldBe true
    getErrors(result).map(_.size) shouldBe Some(2)
  }

  test("validateAll handles empty collection") {
    val validator = (x: Int) => valid[ConfigError, Int](x)
    val result    = ValidationCombinators.validateAll(List.empty[Int])(validator)
    result.isValid shouldBe true
    getValue(result) shouldBe Some(List.empty[Int])
  }

  test("noDuplicates validates list without duplicates") {
    val result = ValidationCombinators.noDuplicates("field", List(1, 2, 3, 4))
    result.isValid shouldBe true
    getValue(result) shouldBe Some(List(1, 2, 3, 4))
  }

  test("noDuplicates validates empty list") {
    val result = ValidationCombinators.noDuplicates("field", List.empty[Int])
    result.isValid shouldBe true
  }

  test("noDuplicates rejects list with duplicates") {
    val result = ValidationCombinators.noDuplicates("field", List(1, 2, 3, 2, 4, 1))
    result.isInvalid shouldBe true
    val error = getErrors(result).map(_.head)
    error should matchPattern { case Some(ConfigError.InvalidValue("field", _, "unique elements")) => }
    // Check that error message contains duplicate values
    error.foreach { err =>
      val msg = err.asInstanceOf[ConfigError.InvalidValue].value
      msg should (include("1") and include("2"))
    }
  }

  test("when applies validator when condition is true") {
    val validator = (x: Int) => if (x > 0) valid[ConfigError, Int](x) else invalid(ConfigError.InvalidValue("x", x.toString, "positive"))
    val conditional = ValidationCombinators.when[Int](condition = true)(validator)
    conditional(5).isValid shouldBe true
    conditional(-5).isInvalid shouldBe true
  }

  test("when skips validator when condition is false") {
    val validator = (x: Int) => invalid[ConfigError, Int](ConfigError.InvalidValue("x", x.toString, "always fails"))
    val conditional = ValidationCombinators.when[Int](condition = false)(validator)
    conditional(5).isValid shouldBe true // Should pass because condition is false
  }

  test("whenValue applies validator when predicate is true") {
    val validator = (x: Int) => if (x > 10) valid[ConfigError, Int](x) else invalid(ConfigError.InvalidValue("x", x.toString, ">10"))
    val conditional = ValidationCombinators.whenValue[Int](_ > 5)(validator)

    conditional(15).isValid shouldBe true // predicate true, validator passes
    conditional(7).isInvalid shouldBe true // predicate true, validator fails
    conditional(3).isValid shouldBe true // predicate false, skipped
  }

  test("whenValue skips validator when predicate is false") {
    val validator = (x: Int) => invalid[ConfigError, Int](ConfigError.InvalidValue("x", x.toString, "always fails"))
    val conditional = ValidationCombinators.whenValue[Int](_ > 10)(validator)
    conditional(5).isValid shouldBe true // Predicate false, validator skipped
  }

  test("eitherOr passes if first validator succeeds") {
    val validator1 = (x: Int) => if (x > 0) valid[ConfigError, Int](x) else invalid(ConfigError.InvalidValue("x", x.toString, "positive"))
    val validator2 = (x: Int) => invalid[ConfigError, Int](ConfigError.InvalidValue("x", x.toString, "always fails"))
    val combined = ValidationCombinators.eitherOr(validator1, validator2)
    combined(5).isValid shouldBe true
  }

  test("eitherOr passes if second validator succeeds when first fails") {
    val validator1 = (x: Int) => invalid[ConfigError, Int](ConfigError.InvalidValue("x", x.toString, "always fails"))
    val validator2 = (x: Int) => valid[ConfigError, Int](x)
    val combined = ValidationCombinators.eitherOr(validator1, validator2)
    combined(5).isValid shouldBe true
  }

  test("eitherOr fails if both validators fail") {
    val validator1 = (x: Int) => invalid[ConfigError, Int](ConfigError.InvalidValue("x", x.toString, "fails 1"))
    val validator2 = (x: Int) => invalid[ConfigError, Int](ConfigError.InvalidValue("x", x.toString, "fails 2"))
    val combined = ValidationCombinators.eitherOr(validator1, validator2)
    combined(5).isInvalid shouldBe true
  }

  test("allOf passes if all validators succeed") {
    val validator1 = (x: Int) => if (x > 0) valid[ConfigError, Int](x) else invalid(ConfigError.InvalidValue("x", x.toString, "positive"))
    val validator2 = (x: Int) => if (x < 100) valid[ConfigError, Int](x) else invalid(ConfigError.InvalidValue("x", x.toString, "<100"))
    val validator3 = (x: Int) => if (x % 2 == 0) valid[ConfigError, Int](x) else invalid(ConfigError.InvalidValue("x", x.toString, "even"))
    val combined = ValidationCombinators.allOf(List(validator1, validator2, validator3))
    combined(50).isValid shouldBe true
    getValue(combined(50)) shouldBe Some(50)
  }

  test("allOf accumulates errors from all failing validators") {
    val validator1 = (x: Int) => invalid[ConfigError, Int](ConfigError.InvalidValue("x", x.toString, "fails 1"))
    val validator2 = (x: Int) => invalid[ConfigError, Int](ConfigError.InvalidValue("x", x.toString, "fails 2"))
    val validator3 = (x: Int) => invalid[ConfigError, Int](ConfigError.InvalidValue("x", x.toString, "fails 3"))
    val combined = ValidationCombinators.allOf(List(validator1, validator2, validator3))
    val result = combined(5)
    result.isInvalid shouldBe true
    getErrors(result).map(_.size) shouldBe Some(3)
  }

  test("allOf handles empty validator list") {
    val combined = ValidationCombinators.allOf[Int](List.empty)
    combined(5).isValid shouldBe true
    getValue(combined(5)) shouldBe Some(5)
  }

  test("pass always validates successfully") {
    val result = ValidationCombinators.pass[Int](42)
    result.isValid shouldBe true
    getValue(result) shouldBe Some(42)
  }

  test("fail always validates unsuccessfully") {
    val error = ConfigError.InvalidValue("field", "value", "expected")
    val result = ValidationCombinators.fail[Int](error)(42)
    result.isInvalid shouldBe true
    getErrors(result).map(_.head) shouldBe Some(error)
  }

  test("fail preserves custom error") {
    val customError = ConfigError.MissingRequired("important-field")
    val result = ValidationCombinators.fail[String](customError)("anything")
    getErrors(result).map(_.head) shouldBe Some(customError)
  }
}
