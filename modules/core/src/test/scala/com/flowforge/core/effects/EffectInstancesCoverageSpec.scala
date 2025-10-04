package com.flowforge.core.effects

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.flowforge.core.algebra.EffectSystem
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._

class EffectInstancesCoverageSpec extends AnyFunSuite with Matchers {
  implicit val es: EffectSystem[IO] = com.flowforge.core.instances.EffectInstances.catsEffectSystemInstance

  test("bracketCase passes Completed on success and Error on failure") {
    @volatile var exitWasCompleted = false
    @volatile var exitWasError     = false

    val acquire = IO.pure(1)
    val useOk   = (_: Int) => IO.pure(42)
    val release = (_: Int, ec: es.ExitCase[Throwable]) => IO {
      ec match {
        case _ : Any => ()
      }
      ec match {
        case es.ExitCase.Completed => exitWasCompleted = true
        case _                     => ()
      }
    }
    es.bracketCase(acquire)(useOk)(release).unsafeRunSync()
    exitWasCompleted shouldBe true

    val releaseErr = (_: Int, ec: es.ExitCase[Throwable]) => IO {
      ec match {
        case es.ExitCase.Error(_) => exitWasError = true
        case _                    => ()
      }
    }
    val boom = (_: Int) => IO.raiseError[Int](new RuntimeException("boom"))
    intercept[RuntimeException] { es.bracketCase(acquire)(boom)(releaseErr).unsafeRunSync() }
    exitWasError shouldBe true
  }

  test("parallel and timeout/retry utilities") {
    val a  = IO.sleep(10.millis) *> IO.pure(1)
    val b  = IO.sleep(10.millis) *> IO.pure(2)
    val pp = es.parProduct(a, b).unsafeRunSync()
    pp shouldBe (1, 2)

    val fast = es.timeout(IO.pure(5), 100.millis).unsafeRunSync()
    fast shouldBe 5

    var attempts = 0
    val flakey = IO {
      attempts += 1
      if (attempts < 2) throw new RuntimeException("retry") else 7
    }
    val retried = es.retryWithBackoff(flakey, maxRetries = 3, initialDelay = 1.millis, backoffFactor = 1.0).unsafeRunSync()
    retried shouldBe 7
  }
}
