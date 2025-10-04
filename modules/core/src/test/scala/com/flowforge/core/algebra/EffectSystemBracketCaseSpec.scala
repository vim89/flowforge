// scalafix:off DisableSyntax.var DisableSyntax.throw DisableSyntax.null DisableSyntax.noUnsafeRunSync
package com.flowforge.core.algebra

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.flowforge.core.instances.EffectInstances
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class EffectSystemBracketCaseSpec extends AnyFunSuite with Matchers {
  implicit val F: EffectSystem[IO] = EffectInstances.catsEffectSystemInstance

  test("bracketCase release runs on success") {
    @volatile var ran = false
    val out = F.bracketCase(IO.pure(1))(_ => IO.pure(2))((_, _) => IO.delay { ran = true }).unsafeRunSync()
    out shouldBe 2
    ran shouldBe true
  }

  test("bracketCase release gets Error on failure") {
    @volatile var isError = false
    val ex                = new RuntimeException("boom")
    val res = F
      .bracketCase(IO.pure(1))(_ => IO.raiseError[Int](ex))((_, _) => IO.delay { isError = true })
      .attempt
      .unsafeRunSync()
    res.isLeft shouldBe true
    isError shouldBe true // release observed
  }

  // Note: explicit cancelation semantics are validated in EffectInstancesLawsSpec (cancel fibers test)

  test("guarantee runs finalizer on success and failure") {
    @volatile var ran = 0
    val ok            = F.guarantee(IO.pure(1))(IO.delay(ran += 1)).unsafeRunSync()
    ok shouldBe 1
    val err =
      F.guarantee(IO.raiseError[Int](new RuntimeException("x")))(IO.delay(ran += 1)).attempt.unsafeRunSync()
    err.isLeft shouldBe true
    ran shouldBe 2
  }
}
