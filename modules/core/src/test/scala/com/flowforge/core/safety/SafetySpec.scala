// scalafix:off DisableSyntax.var DisableSyntax.throw DisableSyntax.null DisableSyntax.noUnsafeRunSync
package com.flowforge.core.safety

import cats.data.NonEmptyList
import com.flowforge.core.types.FlowForgeError
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class SafetySpec extends AnyFunSuite with Matchers {

  test("safely returns Right on success") {
    val res = Safety.safely(1 + 1)(DefaultErrorMapper)
    res shouldBe Right(2)
  }

  test("safely maps exceptions to FlowForgeError via ErrorMapper") {
    val res = Safety.safely[Int](throw new IllegalArgumentException("bad arg"))(DefaultErrorMapper)
    res.left.getOrElse(fail("expected Left")) match {
      case v: FlowForgeError.ValidationError => v.message.toLowerCase should include("bad arg")
      case other                             => fail(s"unexpected error mapping: $other")
    }
  }

  test("safelyV lifts to ValidatedResult with NonEmptyList on error") {
    val v = Safety.safelyV[Int](throw new IllegalArgumentException("bad"))(DefaultErrorMapper)
    v.isValid shouldBe false
  }

  test("sequenceV accumulates errors from Results (bounded)") {
    val e1: Safety.Result[Int]  = Left(FlowForgeError.ValidationError("e1"))
    val e2: Safety.Result[Int]  = Left(FlowForgeError.ValidationError("e2"))
    val ok1: Safety.Result[Int] = Right(1)
    val ok2: Safety.Result[Int] = Right(2)

    // all valid -> valid list
    val v1 = Safety.sequenceV(List(ok1, ok2))
    v1.isValid shouldBe true

    // mixed -> accumulate
    val v2 = Safety.sequenceV(List(e1, ok1, e2))
    v2.isValid shouldBe false
    val errs = v2.swap.getOrElse(NonEmptyList.one(FlowForgeError.ValidationError("missing")))
    errs.toList.map(_.message).toSet shouldBe Set("e1", "e2")
  }
}
