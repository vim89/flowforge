// scalafix:off DisableSyntax.var DisableSyntax.throw DisableSyntax.null DisableSyntax.noUnsafeRunSync
package com.flowforge.core.safety

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.flowforge.core.algebra.EffectSystem
import com.flowforge.core.instances.EffectInstances
import com.flowforge.core.logging.CoreLogger
import com.flowforge.core.types.FlowForgeError
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class SafetyLogLeftSpec extends AnyFunSuite with Matchers {
  implicit val F: EffectSystem[IO] = EffectInstances.catsEffectSystemInstance

  class TestLogger extends CoreLogger[IO] {
    @volatile var errors: List[String] = Nil
    def info(msg: String): IO[Unit]  = IO.unit
    def warn(msg: String): IO[Unit]  = IO.unit
    def error(msg: String): IO[Unit] = IO.delay { errors = msg :: errors }
  }

  test("logLeft logs error messages for Left results") {
    val R  = Safety.in[IO]
    val tl = new TestLogger
    implicit val L: CoreLogger[IO] = tl
    val fr = IO.pure[Safety.Result[Int]](Left(FlowForgeError.ValidationError("bad")))
    val _ = R.logLeft(fr).unsafeRunSync()
    tl.errors.headOption.getOrElse("").toLowerCase should include ("bad")
  }
}
