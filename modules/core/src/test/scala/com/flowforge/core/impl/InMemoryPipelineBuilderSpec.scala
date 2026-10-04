package com.flowforge.core.impl

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.flowforge.core.algebra.EffectSystem
import com.flowforge.core.instances.EffectInstances
import com.flowforge.core.testing.How
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class InMemoryPipelineBuilderSpec extends AnyFunSuite with Matchers {

  implicit val es: EffectSystem[IO] = EffectInstances.catsEffectSystemInstance

  test("adding a stage keeps the description set before it", How) {
    val built = InMemoryPipelineBuilder
      .create[IO]
      .typed("described")
      .withDescription("keeps me")
      .addStreamTransform[Int](_ => IO.pure(1))
      .build()

    // Every stage method used to rebuild the builder with three of the five fields, dropping this.
    built.metadata.tags.get("description") shouldBe Some("keeps me")
  }

  test("a typed build that cannot run its stages fails instead of returning the input", How) {
    val built = InMemoryPipelineBuilder
      .create[IO]
      .typed("not-runnable")
      .addStreamTransform[Int](_ => IO.pure(1))
      .build()

    // It used to hand the input back cast to Out, so this returned () where an Int was expected.
    val thrown = intercept[UnsupportedOperationException](built.execute(()).unsafeRunSync())
    thrown.getMessage should include("not-runnable")
  }

  test("a streaming build applies the operations it was given", How) {
    val built = InMemoryPipelineBuilder
      .create[IO]
      .streaming("doubler")
      .addStreamingOperation[Int, Int]("double", _.map(_ * 2))
      .addStreamingOperation[Int, Int]("increment", _.map(_ + 1))
      .buildStreaming()

    // It used to return the input stream unchanged, ignoring every registered operation.
    val out = built
      .execute(fs2.Stream[IO, Any](1, 2, 3))
      .flatMap(_.compile.toList)
      .unsafeRunSync()

    out shouldBe List(3, 5, 7)
  }
}
