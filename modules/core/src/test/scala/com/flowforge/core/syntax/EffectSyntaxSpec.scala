// scalafix:off DisableSyntax.var DisableSyntax.throw DisableSyntax.null DisableSyntax.noUnsafeRunSync
package com.flowforge.core.syntax

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.flowforge.core.algebra.EffectSystem
import com.flowforge.core.instances.EffectInstances
import com.flowforge.core.syntax.effect._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._

class EffectSyntaxSpec extends AnyFunSuite with Matchers {
  implicit val F: EffectSystem[IO] = EffectInstances.catsEffectSystemInstance

  test("timeoutAfter cancels slow effect") {
    val out = (IO.sleep(200.millis) *> IO.pure(1)).timeoutAfter(50.millis).attempt.unsafeRunSync()
    out.isLeft shouldBe true
  }

  test("retryOnFailure retries and succeeds") {
    @volatile var c = 0
    val io          = IO.delay { c += 1; if (c < 2) throw new RuntimeException("boom") else 7 }
    val out         = io.retryOnFailure(5, 5.millis).unsafeRunSync()
    out shouldBe 7
  }

  test("recoverFrom handles specific error") {
    val ex  = new IllegalArgumentException("bad")
    val io  = IO.raiseError[Int](ex)
    val out = io.recoverFrom { case _: IllegalArgumentException => IO.pure(9) }.unsafeRunSync()
    out shouldBe 9
  }

  test("guaranteeCleanup runs finalizer") {
    @volatile var ran = false
    val out           = IO.pure(1).guaranteeCleanup(IO.delay { ran = true }).unsafeRunSync()
    out shouldBe 1
    ran shouldBe true
  }
}

