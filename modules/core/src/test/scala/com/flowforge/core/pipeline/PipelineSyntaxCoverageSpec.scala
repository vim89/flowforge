package com.flowforge.core.pipeline

import cats.data.Validated
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.syntax.all._
import com.flowforge.core.algebra.{ DataAlgebra, EffectSystem }
import com.flowforge.core.impl.InMemoryDataAlgebra
import com.flowforge.core.syntax.PipelineSyntax._
import com.flowforge.core.types._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class PipelineSyntaxCoverageSpec extends AnyFunSuite with Matchers {
  implicit val es: EffectSystem[IO] = com.flowforge.core.instances.EffectInstances.catsEffectSystemInstance
  val algebra: DataAlgebra[IO]      = new InMemoryDataAlgebra[IO]

  test("EnhancedPipelineBuilder build and execute happy path") {
    val src  = DataSource.local("/tmp/in", DataFormat.JSON)
    val sink = DataSink.local("/tmp/out", DataFormat.JSON)
    val cfg  = PipelineConfig.builder
      .withName("cov")
      .withEnvironment(Environment.Development)
      .withSource(src)
      .withSink(sink)
      .withSparkConfig(SparkConfig.local("app"))
      .build
      .toOption
      .get

    val builder = EnhancedPipelineBuilder.from[IO, String]("cov", src)
      .map(_.trim)
      .transform(s => IO.pure(s"x_$s"))
      .validate(s => if (s.startsWith("x_")) Validated.valid(s) else Validated.invalidNel(FlowForgeError.ConfigurationError("bad")))
      .quality(s => IO.pure(DataAlgebra.QualityResult(s, passed = true, Nil, 1.0)))
      .to(sink)
      .withRetry(1)
      .withTimeout(scala.concurrent.duration.Duration("1s"))
      .withConfig(cfg)

    val built = builder.build
    built.isRight shouldBe true
    val out = builder.execute(" data ").unsafeRunSync()
    out should startWith ("x_")
  }
}
