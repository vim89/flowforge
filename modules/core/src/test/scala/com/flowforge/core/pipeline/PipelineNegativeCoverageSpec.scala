package com.flowforge.core.pipeline

import cats.data.Validated
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.syntax.all._
import com.flowforge.core.syntax.PipelineSyntax
import com.flowforge.core.syntax.PipelineSyntax._
import com.flowforge.core.algebra.{ DataAlgebra, EffectSystem }
import com.flowforge.core.impl.InMemoryDataAlgebra
import com.flowforge.core.types._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class PipelineNegativeCoverageSpec extends AnyFunSuite with Matchers {
  implicit val es: EffectSystem[IO] = com.flowforge.core.instances.EffectInstances.catsEffectSystemInstance
  val algebra: DataAlgebra[IO]      = new InMemoryDataAlgebra[IO]

  private val src  = DataSource.local("/in", DataFormat.JSON)
  private val sink = DataSink.local("/out", DataFormat.JSON)

  test("filter failure path raises IllegalArgumentException") {
    val b = PipelineSyntax.EnhancedPipelineBuilder.from[IO, Int]("neg-filter", src)
      .map(_ + 1)
      .filter(_ => false)
      .to(sink)
    intercept[IllegalArgumentException] { b.execute(1).unsafeRunSync() }
  }

  test("validate failure raises ValidationException") {
    val b = PipelineSyntax.EnhancedPipelineBuilder.from[IO, String]("neg-validate", src)
      .map(_.trim)
      .validate(_ => Validated.invalidNel(FlowForgeError.ValidationError("bad", Some("x"))))
      .to(sink)
    intercept[ValidationException] { b.execute(" data ").unsafeRunSync() }
  }

  test("quality failure raises QualityException") {
    val b = PipelineSyntax.EnhancedPipelineBuilder.from[IO, String]("neg-quality", src)
      .quality(s => IO.pure(DataAlgebra.QualityResult(s, passed = false, violations = Nil, score = 0.0)))
      .to(sink)
    intercept[QualityException] { b.execute("x").unsafeRunSync() }
  }
}
