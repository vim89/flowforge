package com.flowforge.core.algebra

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.flowforge.core.algebra.FlowforgeResource
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class FlowforgeResourceCoverageSpec extends AnyFunSuite with Matchers {
  implicit val es: EffectSystem[IO] = com.flowforge.core.instances.EffectInstances.catsEffectSystemInstance

  test("FlowforgeResource.pure uses provided value and FlowforgeResource.make releases") {
    val pure = FlowforgeResource.pure[IO, Int](42)
    val r1   = pure.use(i => IO.pure(i + 1)).unsafeRunSync()
    r1 shouldBe 43

    @volatile var released = false
    val made               = FlowforgeResource.make[IO, String](IO.pure("res"))(_ => IO { released = true })
    val r2                 = made.use(s => IO.pure(s.length)).unsafeRunSync()
    r2 shouldBe 3
    released shouldBe true
  }
}
