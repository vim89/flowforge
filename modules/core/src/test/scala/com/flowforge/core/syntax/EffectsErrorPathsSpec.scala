package com.flowforge.core.syntax

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.flowforge.core.algebra.EffectSystem
import com.flowforge.core.algebra.EffectSystem._
import com.flowforge.core.instances.EffectInstances
import com.flowforge.core.syntax.effect._
import com.flowforge.core.testing.How
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import scala.concurrent.TimeoutException
import scala.concurrent.duration._

class EffectsErrorPathsSpec extends AnyFunSuite with Matchers {
  implicit val F: EffectSystem[IO] = EffectInstances.catsEffectSystemInstance

  test("retryWithBackoff fails after max retries", How) {
    val boom   = IO.raiseError[Int](new RuntimeException("boom"))
    val result = boom.retryWithBackoff(maxRetries = 1, initialDelay = 5.millis).attempt.unsafeRunSync()
    result.isLeft shouldBe true
  }

  test("timeoutAfter fails on never-ending effect", How) {
    val timed = IO.never[Unit].timeoutAfter(10.millis).attempt.unsafeRunSync()
    timed.left.getOrElse(fail("expected timeout")) shouldBe a[TimeoutException]
  }

  test("raceWith propagates failure when loser would have succeeded", How) {
    val fastFail = IO.raiseError[Int](new IllegalStateException("x"))
    val slow     = IO.sleep(50.millis) *> IO.pure(2)
    val raced    = fastFail.raceWith(slow).attempt.unsafeRunSync()
    raced.isLeft shouldBe true
  }

  test("parWith propagates failure from either side", How) {
    val ok  = IO.pure(1)
    val bad = IO.raiseError[Int](new RuntimeException("y"))
    val res = ok.parWith(bad).attempt.unsafeRunSync()
    res.isLeft shouldBe true
  }

  test("parMapN propagates failure from any participant", How) {
    val a   = IO.pure(1)
    val b   = IO.raiseError[Int](new RuntimeException("z"))
    val c   = IO.pure(3)
    val res = a.parMapN(b, c)((x, y, z) => x + y + z).attempt.unsafeRunSync()
    res.isLeft shouldBe true
  }
}
