package com.flowforge.core.observability

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.syntax.flatMap._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class TracerNoopSpec extends AnyFunSuite with Matchers {
  test("Tracer.noop returns the original effect and supports annotations") {
    val t = Tracer.noop[IO]
    val io = t.inSpan("test") {
      t.annotate("k", "v") >> IO.pure(42)
    }
    io.unsafeRunSync() shouldBe 42
  }
}
