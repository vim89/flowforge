/**
 * Contract tests for EffectSystem implementations.
 *
 * These tests verify that different EffectSystem instances behave consistently
 * and satisfy the expected contracts for data pipeline operations.
 */
package com.flowforge.core.algebra

import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import com.flowforge.core.instances.EffectInstances._
import org.scalatest.funspec.AsyncFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

import scala.concurrent.duration._
import scala.util.{ Failure, Success, Try }

class EffectSystemContractSpec 
    extends AsyncFunSpec 
    with AsyncIOSpec 
    with Matchers 
    with ScalaCheckPropertyChecks {

  describe("EffectSystem contract compliance") {

    it("should handle synchronous operations correctly") {
      val es = EffectSystem[IO]
      
      val test = for {
        // Test delay
        delayed <- es.delay(42)
        // Test suspend  
        suspended <- es.suspend(IO.pure(100))
        // Test blocking
        blocked <- es.blocking(Thread.currentThread().getName)
      } yield (delayed, suspended, blocked)

      test.map { case (d, s, b) =>
        d should equal(42)
        s should equal(100)
        b should not be empty
      }
    }

    it("should convert external types correctly") {
      val es = EffectSystem[IO]
      
      val successTry = Try(42)
      val failureTry: Try[Int] = Try(throw new RuntimeException("test error"))
      val successEither: Either[Throwable, Int] = Right(100)
      val failureEither: Either[Throwable, Int] = Left(new RuntimeException("test error"))

      val test = for {
        fromSuccessTry <- es.fromTry(successTry)
        fromFailureTry <- es.fromTry(failureTry).attempt
        fromSuccessEither <- es.fromEither(successEither)
        fromFailureEither <- es.fromEither(failureEither).attempt
      } yield (fromSuccessTry, fromFailureTry, fromSuccessEither, fromFailureEither)

      test.map { case (st, ft, se, fe) =>
        st should equal(42)
        ft shouldBe a[Left[_, _]]
        se should equal(100)
        fe shouldBe a[Left[_, _]]
      }
    }

    it("should provide proper resource management") {
      val es = EffectSystem[IO]
      var acquisitions = 0
      var releases = 0
      var usages = 0

      val resource = "test-resource"
      val testError = new RuntimeException("Test error during use")

      val successfulBracket = es.bracket(
        acquire = es.delay { acquisitions += 1; resource }
      )(
        use = r => es.delay { usages += 1; r.toUpperCase }
      )(
        release = _ => es.delay { releases += 1 }
      )

      val failingBracket = es.bracket(
        acquire = es.delay { acquisitions += 1; resource }
      )(
        use = _ => es.raiseError[String](testError)
      )(
        release = _ => es.delay { releases += 1 }
      ).attempt

      val test = for {
        success <- successfulBracket
        failure <- failingBracket
      } yield (success, failure)

      test.map { case (s, f) =>
        s should equal("TEST-RESOURCE")
        f shouldBe Left(testError)
        acquisitions should equal(2)
        releases should equal(2) // Resources should be released even on failure
        usages should equal(1) // Only successful usage
      }
    }

    it("should provide timing operations") {
      val es = EffectSystem[IO]
      
      val start = System.currentTimeMillis()
      
      val test = for {
        _ <- es.sleep(50.millis)
        elapsed = System.currentTimeMillis() - start
        timeoutResult <- es.timeout(es.sleep(1.second), 100.millis).attempt
      } yield (elapsed, timeoutResult)

      test.map { case (elapsed, timeout) =>
        elapsed should be >= 45L // Should sleep for at least ~50ms
        elapsed should be < 200L  // But not too long
        timeout shouldBe a[Left[_, _]] // Should timeout
      }
    }

    it("should support retries with backoff") {
      val es = EffectSystem[IO]
      var attempts = 0
      
      val failingOperation = es.delay {
        attempts += 1
        if (attempts < 3) throw new RuntimeException("Not ready yet")
        else "success"
      }

      val test = es.retryWithBackoff(
        failingOperation,
        maxRetries = 5,
        initialDelay = 10.millis,
        backoffFactor = 2.0
      )

      test.map { result =>
        result should equal("success")
        attempts should equal(3) // Should succeed on third attempt
      }
    }

    it("should measure execution time accurately") {
      val es = EffectSystem[IO]
      
      val operation = es.sleep(100.millis) >> es.pure("completed")
      val timedOperation = es.timed(operation)

      timedOperation.map { case (result, duration) =>
        result should equal("completed")
        duration should be >= 95.millis  // Should be at least ~100ms
        duration should be < 200.millis  // But not too long
      }
    }

    it("should traverse collections correctly") {
      val es = EffectSystem[IO]
      val input = List(1, 2, 3, 4, 5)
      
      val sequentialTraverse = es.traverse(input)(x => es.delay(x * 2))
      val parallelTraverse = es.parTraverse(input)(x => es.delay(x * 2))
      
      val test = for {
        seqResult <- sequentialTraverse
        parResult <- parallelTraverse
      } yield (seqResult, parResult)

      test.map { case (seq, par) =>
        seq should equal(List(2, 4, 6, 8, 10))
        par should equal(List(2, 4, 6, 8, 10))
      }
    }
  }

  describe("EffectSystem error handling") {
    
    it("should handle and recover from errors") {
      val es = EffectSystem[IO]
      val testError = new RuntimeException("Test error")
      
      val failingOperation = es.raiseError[String](testError)
      val recovered = es.handleError(failingOperation)(_ => es.pure("recovered"))
      
      recovered.map { result =>
        result should equal("recovered")
      }
    }

    it("should chain error handlers properly") {
      val es = EffectSystem[IO]
      val originalError = new RuntimeException("Original")
      val handlerError = new RuntimeException("Handler error")
      
      val operation = es.raiseError[String](originalError)
      val chainedHandling = es.handleErrorWith(operation) { _ =>
        es.raiseError[String](handlerError)
      }.attempt
      
      chainedHandling.map { result =>
        result should equal(Left(handlerError))
      }
    }
  }
}