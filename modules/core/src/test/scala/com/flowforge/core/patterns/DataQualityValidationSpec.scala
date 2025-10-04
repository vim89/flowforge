package com.flowforge.core.patterns

import com.flowforge.core.patterns.ValidationTypes._
import com.flowforge.core.types.ErrorSeverity
import com.flowforge.core.types.ValidationError.QualityViolation
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.time.Instant
import scala.concurrent.duration._

/**
 * Comprehensive test suite for DataQualityValidation.
 *
 * Coverage targets:
 *   - Statement coverage: 100%
 *   - Branch coverage: 100%
 *   - All edge cases and error paths
 */
class DataQualityValidationSpec extends AnyFunSuite with Matchers {

  test("freshness validates recent timestamp") {
    val recentTimestamp = Instant.now().minusSeconds(30)
    val result          = DataQualityValidation.freshness[Unit]("field", recentTimestamp, 1.minute)
    result.isValid shouldBe true
  }

  test("freshness validates timestamp at exact max age") {
    val timestamp = Instant.now().minusSeconds(60)
    val result    = DataQualityValidation.freshness[Unit]("field", timestamp, 1.minute)
    result.isValid shouldBe true
  }

  test("freshness rejects old timestamp") {
    val oldTimestamp = Instant.now().minusSeconds(120)
    val result       = DataQualityValidation.freshness[Unit]("field", oldTimestamp, 1.minute)
    result.isInvalid shouldBe true

    getErrors(result).map(_.head) match {
      case Some(violation: QualityViolation) =>
        violation.constraint shouldBe "freshness"
        violation.violatedValue shouldBe "field"
        violation.threshold shouldBe Some("1 minute")
        violation.severity shouldBe ErrorSeverity.Error
        violation.message should include("too old")
      case _ => fail("Expected QualityViolation")
    }
  }

  test("freshness validates with millisecond precision") {
    val timestamp = Instant.now().minusMillis(500)
    val result    = DataQualityValidation.freshness[Unit]("field", timestamp, 1.second)
    result.isValid shouldBe true
  }

  test("completeness validates 100% complete data") {
    val values = List(Some(1), Some(2), Some(3))
    val result = DataQualityValidation.completeness("field", values, 1.0)
    result.isValid shouldBe true
    getValue(result) shouldBe Some(values)
  }

  test("completeness validates data meeting minimum threshold") {
    val values = List(Some(1), Some(2), None, Some(4)) // 75% complete
    val result = DataQualityValidation.completeness("field", values, 0.75)
    result.isValid shouldBe true
  }

  test("completeness validates data above minimum threshold") {
    val values = List(Some(1), Some(2), None, Some(4)) // 75% complete
    val result = DataQualityValidation.completeness("field", values, 0.5)
    result.isValid shouldBe true
  }

  test("completeness rejects data below threshold") {
    val values = List(Some(1), None, None, None) // 25% complete
    val result = DataQualityValidation.completeness("field", values, 0.5)
    result.isInvalid shouldBe true

    getErrors(result).map(_.head) match {
      case Some(violation: QualityViolation) =>
        violation.constraint shouldBe "completeness"
        violation.violatedValue shouldBe "field"
        violation.threshold shouldBe Some("0.5")
        violation.severity shouldBe ErrorSeverity.Error
        violation.message should include("25.0%")
        violation.message should include("50.0%")
      case _ => fail("Expected QualityViolation")
    }
  }

  test("completeness handles empty list") {
    val values = List.empty[Option[Int]]
    val result = DataQualityValidation.completeness("field", values, 0.5)
    result.isInvalid shouldBe true
    getErrors(result).map(_.head) match {
      case Some(violation: QualityViolation) =>
        violation.message should include("0.0%")
      case _ => fail("Expected QualityViolation")
    }
  }

  test("completeness handles all None values") {
    val values = List(None, None, None)
    val result = DataQualityValidation.completeness("field", values, 0.1)
    result.isInvalid shouldBe true
  }

  test("uniqueness validates list with all unique values") {
    val values = List(1, 2, 3, 4, 5)
    val result = DataQualityValidation.uniqueness("field", values)
    result.isValid shouldBe true
    getValue(result) shouldBe Some(values)
  }

  test("uniqueness validates empty list") {
    val values = List.empty[Int]
    val result = DataQualityValidation.uniqueness("field", values)
    result.isValid shouldBe true
  }

  test("uniqueness validates single element list") {
    val values = List(1)
    val result = DataQualityValidation.uniqueness("field", values)
    result.isValid shouldBe true
  }

  test("uniqueness rejects list with duplicate values") {
    val values = List(1, 2, 3, 2, 4, 1)
    val result = DataQualityValidation.uniqueness("field", values)
    result.isInvalid shouldBe true

    getErrors(result).map(_.head) match {
      case Some(violation: QualityViolation) =>
        violation.constraint shouldBe "uniqueness"
        violation.violatedValue shouldBe "field"
        violation.severity shouldBe ErrorSeverity.Error
        violation.message should include("Duplicate values found")
        violation.message should (include("1") and include("2"))
      case _ => fail("Expected QualityViolation")
    }
  }

  test("uniqueness rejects list with multiple duplicates") {
    val values = List(1, 1, 2, 2, 3, 3)
    val result = DataQualityValidation.uniqueness("field", values)
    result.isInvalid shouldBe true
  }

  test("referentialIntegrity validates all valid foreign keys") {
    val foreignKeys    = List(1, 2, 3)
    val referenceTable = Set(1, 2, 3, 4, 5)
    val result         = DataQualityValidation.referentialIntegrity("field", foreignKeys, referenceTable)
    result.isValid shouldBe true
    getValue(result) shouldBe Some(foreignKeys)
  }

  test("referentialIntegrity validates empty foreign key list") {
    val foreignKeys    = List.empty[Int]
    val referenceTable = Set(1, 2, 3)
    val result         = DataQualityValidation.referentialIntegrity("field", foreignKeys, referenceTable)
    result.isValid shouldBe true
  }

  test("referentialIntegrity validates with empty reference table when no foreign keys") {
    val foreignKeys    = List.empty[Int]
    val referenceTable = Set.empty[Int]
    val result         = DataQualityValidation.referentialIntegrity("field", foreignKeys, referenceTable)
    result.isValid shouldBe true
  }

  test("referentialIntegrity rejects invalid foreign keys") {
    val foreignKeys    = List(1, 2, 99)
    val referenceTable = Set(1, 2, 3)
    val result         = DataQualityValidation.referentialIntegrity("field", foreignKeys, referenceTable)
    result.isInvalid shouldBe true

    getErrors(result).map(_.head) match {
      case Some(violation: QualityViolation) =>
        violation.constraint shouldBe "referential_integrity"
        violation.violatedValue shouldBe "field"
        violation.severity shouldBe ErrorSeverity.Error
        violation.message should include("Invalid foreign key references")
        violation.message should include("99")
      case _ => fail("Expected QualityViolation")
    }
  }

  test("referentialIntegrity rejects multiple invalid foreign keys") {
    val foreignKeys    = List(1, 99, 88, 77)
    val referenceTable = Set(1, 2, 3)
    val result         = DataQualityValidation.referentialIntegrity("field", foreignKeys, referenceTable)
    result.isInvalid shouldBe true

    getErrors(result).map(_.head) match {
      case Some(violation: QualityViolation) =>
        violation.message should (include("99") and include("88") and include("77"))
      case _ => fail("Expected QualityViolation")
    }
  }

  test("distribution validates values matching expected mean") {
    val values = List(10.0, 10.0, 10.0)
    val result = DataQualityValidation.distribution("field", values, 10.0, 0.1)
    result.isValid shouldBe true
    getValue(result) shouldBe Some(values)
  }

  test("distribution validates values within tolerance") {
    val values = List(9.0, 10.0, 11.0) // mean = 10.0
    val result = DataQualityValidation.distribution("field", values, 10.0, 1.0)
    result.isValid shouldBe true
  }

  test("distribution validates values at exact tolerance boundary") {
    val values = List(9.0, 11.0) // mean = 10.0, deviation = 0.0
    val result = DataQualityValidation.distribution("field", values, 10.0, 0.0)
    result.isValid shouldBe true
  }

  test("distribution rejects values outside tolerance") {
    val values = List(5.0, 6.0, 7.0) // mean = 6.0, deviation = 4.0
    val result = DataQualityValidation.distribution("field", values, 10.0, 1.0)
    result.isInvalid shouldBe true

    getErrors(result).map(_.head) match {
      case Some(violation: QualityViolation) =>
        violation.constraint shouldBe "distribution"
        violation.violatedValue shouldBe "field"
        violation.threshold shouldBe Some("10.0 + 1.0")
        violation.severity shouldBe ErrorSeverity.Error
        violation.message should include("Mean deviation exceeds tolerance")
      case _ => fail("Expected QualityViolation")
    }
  }

  test("distribution rejects empty list") {
    val values = List.empty[Double]
    val result = DataQualityValidation.distribution("field", values, 10.0, 1.0)
    result.isInvalid shouldBe true

    getErrors(result).map(_.head) match {
      case Some(violation: QualityViolation) =>
        violation.constraint shouldBe "distribution"
        violation.message should include("No values provided")
      case _ => fail("Expected QualityViolation")
    }
  }

  test("distribution handles single value") {
    val values = List(10.0)
    val result = DataQualityValidation.distribution("field", values, 10.0, 0.0)
    result.isValid shouldBe true
  }

  test("distribution calculates mean correctly") {
    val values = List(1.0, 2.0, 3.0, 4.0, 5.0) // mean = 3.0
    val result = DataQualityValidation.distribution("field", values, 3.0, 0.01)
    result.isValid shouldBe true
  }

  test("businessRule validates value satisfying rule") {
    val rule      = (x: Int) => x > 0
    val validator = DataQualityValidation.businessRule[Int]("positive", "Value must be positive")(rule)
    val result    = validator(5)
    result.isValid shouldBe true
    getValue(result) shouldBe Some(5)
  }

  test("businessRule rejects value violating rule") {
    val rule      = (x: Int) => x > 0
    val validator = DataQualityValidation.businessRule[Int]("positive", "Value must be positive")(rule)
    val result    = validator(-5)
    result.isInvalid shouldBe true

    getErrors(result).map(_.head) match {
      case Some(violation: QualityViolation) =>
        violation.constraint shouldBe "business_rule"
        violation.violatedValue shouldBe "positive"
        violation.threshold shouldBe Some("-5")
        violation.severity shouldBe ErrorSeverity.Error
        violation.message should include("Business rule violation")
        violation.message should include("Value must be positive")
      case _ => fail("Expected QualityViolation")
    }
  }

  test("businessRule works with complex domain objects") {
    case class User(name: String, age: Int)
    val rule = (u: User) => u.age >= 18 && u.name.nonEmpty
    val validator =
      DataQualityValidation.businessRule[User]("adult-user", "User must be adult with name")(rule)

    validator(User("Alice", 25)).isValid shouldBe true
    validator(User("Bob", 17)).isInvalid shouldBe true
    validator(User("", 25)).isInvalid shouldBe true
  }

  test("businessRule works with string validation") {
    val rule = (s: String) => s.length >= 8 && s.exists(_.isDigit)
    val validator =
      DataQualityValidation.businessRule[String]("strong-password", "Password must be 8+ chars with digit")(
        rule,
      )

    validator("password123").isValid shouldBe true
    validator("short1").isInvalid shouldBe true
    validator("longpassword").isInvalid shouldBe true
  }

  test("businessRule preserves custom rule name in error") {
    val rule      = (x: Int) => x % 2 == 0
    val validator = DataQualityValidation.businessRule[Int]("even-number", "Must be even")(rule)
    val result    = validator(3)

    getErrors(result).map(_.head.asInstanceOf[QualityViolation].violatedValue) shouldBe Some("even-number")
  }

  test("businessRule preserves custom description in error message") {
    val rule = (x: Int) => x < 100
    val validator =
      DataQualityValidation.businessRule[Int]("max-limit", "Value exceeds maximum limit of 100")(rule)
    val result = validator(150)

    getErrors(result).map(_.head.asInstanceOf[QualityViolation].message) match {
      case Some(msg) => msg should include("Value exceeds maximum limit of 100")
      case None      => fail("Expected error message")
    }
  }
}
