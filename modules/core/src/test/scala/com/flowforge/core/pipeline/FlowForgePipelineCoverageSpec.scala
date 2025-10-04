package com.flowforge.core.pipeline

import cats.data.Validated
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.flowforge.core.FlowForgePipeline
import com.flowforge.core.algebra.EffectSystem
import com.flowforge.core.types.PipelineTypes._
import com.flowforge.core.types._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class FlowForgePipelineCoverageSpec extends AnyFunSuite with Matchers {
  implicit val es: EffectSystem[IO] = com.flowforge.core.instances.EffectInstances.catsEffectSystemInstance

  test("executeValidated accumulates validations and execute raises on invalid") {
    val src  = DataSource.local("/in", DataFormat.JSON)
    val sink = DataSink.local("/out", DataFormat.JSON)
    val t: PipelineComponent[IO, String, String] = cats.data.Kleisli((s: String) => IO.pure(s.trim))
    val bad: QualityCheck[String] = s => Validated.invalidNel[FlowForgeError, Unit](FlowForgeError.ValidationError("bad", Some("x")))
    val good: QualityCheck[String] = _ => Validated.valid(())

    val p = FlowForgePipeline[IO, String, String]("p", src, sink, t, List(good, bad), None)
    val v = p.executeValidated(" data ").unsafeRunSync()
    v.isInvalid shouldBe true
    intercept[PipelineError.StageExecutionError] {
      p.execute(" data ").unsafeRunSync()
    }
  }

  test("validate returns validNel unit") {
    val src  = DataSource.local("/in", DataFormat.JSON)
    val sink = DataSink.local("/out", DataFormat.JSON)
    val t: PipelineComponent[IO, Int, Int] = cats.data.Kleisli((i: Int) => IO.pure(i + 1))
    val p = FlowForgePipeline[IO, Int, Int]("p2", src, sink, t, Nil, None)
    p.validate.isValid shouldBe true
  }
}
