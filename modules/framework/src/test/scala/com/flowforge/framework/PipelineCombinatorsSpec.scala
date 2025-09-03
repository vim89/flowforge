package com.flowforge.framework

import cats.data.NonEmptyList
import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import com.flowforge.core.algebra.EffectSystem
import org.scalatest.funspec.AsyncFunSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._

class PipelineCombinatorsSpec extends AsyncFunSpec with AsyncIOSpec with Matchers {

  implicit val F: EffectSystem[IO] =
    com.flowforge.core.instances.EffectInstances.catsEffectSystemInstance

  describe("Pipeline and combinators") {

    it("maps and composes with metadata tracking") {
      val p1       = Pipeline.pure[IO, Int, Int](_ + 1, "inc")
      val p2       = Pipeline.pure[IO, Int, Int](_ * 2, "double")
      val composed = p1.andThen(p2).map(_ + 3)

      PipelineExecution.execute(composed)(10).asserting { out =>
        out shouldBe 25 // ((10+1)*2)+3
      }
    }

    it("sequences pipelines in order") {
      val inc   = Pipeline.pure[IO, Int, Int](_ + 1, "inc")
      val times = Pipeline.pure[IO, Int, Int](_ * 10, "times10")
      val seq   = PipelineCombinators.sequence(NonEmptyList.of(inc, times))
      PipelineExecution.execute(seq)(2).asserting(_ shouldBe 30)
    }

    it("runs parallel and combines results") {
      val left  = Pipeline.pure[IO, Int, Int](_ + 1, "inc")
      val right = Pipeline.pure[IO, Int, Int](_ * 2, "double")
      val combined = PipelineCombinators.parallel(left, right) { (l, r) =>
        Pipeline.pure[IO, Int, (Int, Int)](_ => (l, r), "pack")
      }
      PipelineExecution.execute(combined)(5).asserting(_ shouldBe (6 -> 10))
    }

    it("conditionally executes based on predicate") {
      val same = Pipeline.identity[IO, Int]
      val inc  = Pipeline.pure[IO, Int, Int](_ + 1, "inc")
      val cond = PipelineCombinators.conditional[IO, Int](_ % 2 == 0, inc, same)
      for {
        a <- PipelineExecution.execute(cond)(2)
        b <- PipelineExecution.execute(cond)(3)
      } yield {
        a shouldBe 3
        b shouldBe 3
      }
    }

    it("retries with backoff on failure") {
      var attempts = 0
      val flaky = Pipeline.lift[IO, Int, Int](
        { x =>
          attempts += 1
          if (attempts < 3) IO.raiseError(new RuntimeException("boom")) else IO.pure(x)
        },
        "flaky"
      )
      val retried = PipelineCombinators.retry(flaky, maxRetries = 5, initialDelay = 10.millis)
      PipelineExecution.execute(retried)(7).asserting { out =>
        out shouldBe 7
        attempts should be >= 3
      }
    }

    it("batches inputs and flattens output") {
      val echo    = Pipeline.lift[IO, List[Int], List[Int]](xs => IO.pure(xs), "echo")
      val batched = PipelineCombinators.batch(echo, batchSize = 3)
      val in      = List(1, 2, 3, 4, 5, 6, 7)
      PipelineExecution.execute(batched)(in).asserting(_ shouldBe in)
    }
  }
}
