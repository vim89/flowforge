package com.flowforge.app

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.flowforge.core.algebra.EffectSystem

class EffectsDemoSpec extends AnyFunSuite with Matchers {
  implicit val es: EffectSystem[IO] = com.flowforge.core.instances.EffectInstances.catsEffectSystemInstance

  test("demo computes parallel sum and releases resource") {
    val (sum, closed) = EffectsDemo.demo[IO].unsafeRunSync()
    sum shouldBe 55
    closed shouldBe true
  }
}
