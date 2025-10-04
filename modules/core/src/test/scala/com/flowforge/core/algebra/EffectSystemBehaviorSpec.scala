// scalafix:off DisableSyntax.var DisableSyntax.throw DisableSyntax.null DisableSyntax.noUnsafeRunSync
package com.flowforge.core.algebra

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.flowforge.core.instances.EffectInstances
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._

class EffectSystemBehaviorSpec extends AnyFunSuite with Matchers {
  implicit val F: EffectSystem[IO] = EffectInstances.catsEffectSystemInstance

  test("timeout fails slow effects") {
    val slow = IO.sleep(200.millis) *> IO.pure(1)
    val res  = F.timeout(slow, 50.millis).attempt.unsafeRunSync()
    res.isLeft shouldBe true
  }

  test("racePair returns winner with loser fiber") {
    val fast = IO.sleep(10.millis) *> IO.pure("fast")
    val slow = IO.sleep(200.millis) *> IO.pure("slow")
    val res  = F.racePair(fast, slow).unsafeRunSync()
    res match {
      case Left((a, loser))  => a shouldBe "fast"; loser.cancel.unsafeRunSync()
      case Right((loser, b)) => fail(s"unexpected winner: $b with loser $loser")
    }
  }

  test("retryWithBackoff retries and eventually succeeds") {
    @volatile var attempts = 0
    val flaky = IO.delay { attempts += 1; if (attempts < 3) throw new RuntimeException("boom") else 42 }
    val out   = F.retryWithBackoff(flaky, maxRetries = 5, initialDelay = 5.millis).unsafeRunSync()
    out shouldBe 42
    attempts shouldBe 3
  }

  test("timed measures positive durations") {
    val (v, d) = F.timed(IO.sleep(5.millis) *> IO.pure(123)).unsafeRunSync()
    v shouldBe 123
    d.toMillis should be >= 5L
  }

  test("parTraverse computes in parallel") {
    val started = System.currentTimeMillis()
    val ios     = List(1, 2, 3).map(i => IO.sleep(50.millis) *> IO.pure(i))
    val out     = F.parSequence(ios).unsafeRunSync()
    out shouldBe List(1, 2, 3)
    val elapsed = System.currentTimeMillis() - started
    elapsed should be < 120L
  }
}
