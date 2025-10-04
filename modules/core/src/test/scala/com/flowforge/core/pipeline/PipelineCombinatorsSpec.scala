// scalafix:off DisableSyntax.var DisableSyntax.throw DisableSyntax.null DisableSyntax.noUnsafeRunSync
package com.flowforge.core.pipeline

import cats.data.NonEmptyList
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.flowforge.core.algebra.EffectSystem
import com.flowforge.core.instances.EffectInstances
import com.flowforge.framework.{ Pipeline, PipelineCombinators }
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._

class PipelineCombinatorsSpec extends AnyFunSuite with Matchers {
  implicit val F: EffectSystem[IO] = EffectInstances.catsEffectSystemInstance

  test("sequence composes steps in order") {
    val p1 = Pipeline.pure[IO, Int, Int](_ + 1, "inc")
    val p2 = Pipeline.pure[IO, Int, Int](_ * 2, "double")
    val seq = PipelineCombinators.sequence(NonEmptyList.of(p1, p2))
    val out = seq.execute(10).unsafeRunSync()
    out shouldBe 22
  }

  test("parallel runs both and combines") {
    val left  = Pipeline.pure[IO, Int, Int](_ + 1, "inc")
    val right = Pipeline.pure[IO, Int, Int](_ * 3, "triple")
    val par   = PipelineCombinators.parallel(left, right)((a, b) => Pipeline.pure((_: Int) => (a, b)))
    val out   = par.execute(4).unsafeRunSync()
    out shouldBe (5 -> 12)
  }

  test("conditional chooses correct branch") {
    val t = Pipeline.pure[IO, Int, Int](_ + 1, "t")
    val f = Pipeline.pure[IO, Int, Int](_ - 1, "f")
    val c = PipelineCombinators.conditional[IO, Int](_ % 2 == 0, t, f)
    c.execute(2).unsafeRunSync() shouldBe 3
    c.execute(3).unsafeRunSync() shouldBe 2
  }

  test("retry re-executes underlying pipeline") {
    @volatile var attempts = 0
    val base = Pipeline.lift[IO, Int, Int] { _ =>
      IO.delay { attempts += 1; if (attempts < 2) throw new RuntimeException("boom") else 7 }
    }
    val retry = PipelineCombinators.retry(base, maxRetries = 3, 5.millis)
    val out   = retry.execute(0).unsafeRunSync()
    out shouldBe 7
    attempts shouldBe 2
  }

  test("batch groups and flattens results") {
    val base = Pipeline.lift[IO, List[Int], List[Int]](xs => IO.pure(xs.map(_ * 2)))
    val bat  = PipelineCombinators.batch(base, batchSize = 4)
    val out  = bat.execute((1 to 10).toList).unsafeRunSync()
    out shouldBe (1 to 10).map(_ * 2).toList
  }
}

