package com.flowforge.core.safety

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.flowforge.core.algebra.EffectSystem
import com.flowforge.core.instances.EffectInstances
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class SafetyEffectSpec extends AnyFunSuite with Matchers {
  implicit val F: EffectSystem[IO] = EffectInstances.catsEffectSystemInstance

  test("in.attempt wraps success into Right") {
    val R   = Safety.in[IO]
    val res = R.attempt(IO.pure(123)).unsafeRunSync()
    res shouldBe Right(123)
  }

  test("in.attempt maps failure through ErrorMapper to Left") {
    val R = Safety.in[IO]
    val res =
      R.attempt[Int](IO.raiseError(new IllegalArgumentException("boom")))(DefaultErrorMapper).unsafeRunSync()
    res.isLeft shouldBe true
  }

  test("in.orFail extracts Right value or raises mapped error") {
    val R   = Safety.in[IO]
    val fa  = IO.pure[Safety.Result[Int]](Right(5))
    val out = R.orFail(fa).unsafeRunSync()
    out shouldBe 5
  }
}
