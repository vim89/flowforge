// scalafix:off DisableSyntax.var DisableSyntax.throw DisableSyntax.null DisableSyntax.noUnsafeRunSync
package com.flowforge.core.framework

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.flowforge.core.algebra.{ EffectSystem, FlowforgeResource }
import com.flowforge.core.instances.EffectInstances
import com.flowforge.framework.{Pipeline, PipelineExecution}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class PipelineExecutionSpec extends AnyFunSuite with Matchers {
  implicit val F: EffectSystem[IO] = EffectInstances.catsEffectSystemInstance

  private val p: Pipeline[IO, Int, Int] =
    Pipeline.lift[IO, Int, Int](n => IO.pure(n + 1), name = "inc")

  test("execute runs pipeline on single input") {
    PipelineExecution.execute(p)(41).unsafeRunSync() shouldBe 42
  }

  test("executeWithResources runs inside resource scope") {
    val res   = FlowforgeResource.pure[IO, Unit](())
    val value = PipelineExecution.executeWithResources(p, res)(41).unsafeRunSync()
    value shouldBe 42
  }

  test("executeBatch preserves order and runs in parallel") {
    val q   = Pipeline.lift[IO, Int, Int](n => IO.pure(n * 2), name = "double")
    val out = PipelineExecution.executeBatch(q)((1 to 5).toList).unsafeRunSync()
    out shouldBe List(2, 4, 6, 8, 10)
  }
}
