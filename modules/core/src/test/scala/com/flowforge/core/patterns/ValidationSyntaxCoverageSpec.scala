package com.flowforge.core.patterns

import com.flowforge.core.patterns.ValidationSyntax._
import com.flowforge.core.patterns.ValidationTypes._
import com.flowforge.core.types.ValidationError
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class ValidationSyntaxCoverageSpec extends AnyFunSuite with Matchers {
  test("validateAll and validateWhen hit both paths") {
    val value = 5
    val pass = value.validateAll(List((x: Int) => valid(x), (x: Int) => valid(x)))
    pass.isValid shouldBe true
    val fail = value.validateWhen(condition = true)(_ => invalid[ValidationError, Int](ValidationError.MissingRequiredField("x")))
    fail.isInvalid shouldBe true
    val skipped = value.validateWhen(condition = false)(_ => invalid[ValidationError, Int](ValidationError.MissingRequiredField("x")))
    skipped.isValid shouldBe true
  }

  test("ValidationResultOps recover/getOrElse/toEither flags") {
    val ok: ValidationResult[Int] = valid(10)
    ok.isValid shouldBe true
    ok.getOrElse(0) shouldBe 10
    ok.toEither shouldBe Right(10)

    val bad: ValidationResult[Int] = invalid(ValidationError.MissingRequiredField("a"))
    val recovered = bad.recover(_ => 42)
    recovered.isValid shouldBe true
    recovered.getOrElse(0) shouldBe 42
    recovered.errors.isDefined shouldBe false
  }
}

