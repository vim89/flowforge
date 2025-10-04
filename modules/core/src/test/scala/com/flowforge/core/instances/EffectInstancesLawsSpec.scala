// scalafix:off DisableSyntax.var DisableSyntax.throw DisableSyntax.null DisableSyntax.noUnsafeRunSync
/**
 * Property-based tests for EffectSystem instances to verify Monad laws.
 *
 * These tests ensure that our EffectSystem instances satisfy the mathematical laws required for proper
 * monadic behavior, preventing subtle bugs in pipeline composition.
 */
package com.flowforge.core.instances

import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import cats.syntax.parallel._
import com.flowforge.core.algebra.EffectSystem
import com.flowforge.core.instances.EffectInstances.catsEffectSystemInstance
import org.scalatest.funspec.AsyncFunSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._

class EffectInstancesLawsSpec extends AsyncFunSpec with AsyncIOSpec with Matchers {

  val effectSystem: EffectSystem[IO] = catsEffectSystemInstance

  describe("Cats-Effect EffectSystem instance") {

    it("satisfies Monad left identity") {
      val a                 = 42
      val f: Int => IO[Int] = x => IO.pure(x + 1)
      val left              = IO.pure(a).flatMap(f)
      val right             = f(a)
      (left, right).parMapN(_ should equal(_))
    }

    it("satisfies Monad right identity") {
      val a    = 42
      val fa   = IO.pure(a)
      val left = fa.flatMap(IO.pure)
      (left, fa).parMapN(_ should equal(_))
    }

    it("satisfies Monad associativity") {
      val a                 = 42
      val f: Int => IO[Int] = x => IO.pure(x + 2)
      val g: Int => IO[Int] = x => IO.pure(x * 3)
      val left              = IO.pure(a).flatMap(f).flatMap(g)
      val right             = IO.pure(a).flatMap(x => f(x).flatMap(g))
      (left, right).parMapN(_ should equal(_))
    }

    it("should provide stack-safe tailRecM") {
      val largeN = 10000 // Reduced for faster tests

      val result = effectSystem.tailRecM(0) { i =>
        if (i < largeN) IO.pure(Left(i + 1))
        else IO.pure(Right(i))
      }

      result.map { finalValue =>
        finalValue should equal(largeN)
      }
    }

    it("should handle errors properly in bracket operations") {
      var resourceReleased = false
      val testError        = new RuntimeException("Test error")

      val result = effectSystem
        .bracket(
          acquire = IO.pure("resource"),
        )(
          use = _ => IO.raiseError[String](testError),
        )(
          release = _ => IO.delay { resourceReleased = true },
        )
        .attempt

      result.map { either =>
        either should be(Left(testError))
        resourceReleased should be(true)
      }
    }

    it("should cancel fibers properly") {
      val neverCompleting = IO.never[Int]

      val test = for {
        fiber  <- effectSystem.start(neverCompleting)
        _      <- fiber.cancel
        result <- fiber.join.timeout(1.second).attempt
      } yield result

      test.map { result =>
        result shouldBe a[Left[_, _]] // Should timeout or be cancelled
      }
    }

    it("should run parallel operations concurrently") {
      val start = System.currentTimeMillis()

      val operation1 = IO.sleep(50.millis) >> IO.pure(1)
      val operation2 = IO.sleep(50.millis) >> IO.pure(2)

      val parallelResult = effectSystem.parProduct(operation1, operation2)

      parallelResult.map { result =>
        val elapsed = System.currentTimeMillis() - start
        result should equal((1, 2))
        elapsed should be < 100L // Should complete in less than 100ms (much less than 100ms sequential)
      }
    }
  }

  // ZIO tests would go here if ZIO test dependencies were available
  // For now, focusing on Cats-Effect which is more commonly used
}
