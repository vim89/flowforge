// scalafix:off DisableSyntax.noUnsafeRunSync DisableSyntax.null
package com.flowforge.core.syntax

import cats.data.{ NonEmptyList, Validated, ValidatedNel }
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.flowforge.core.algebra.EffectSystem
import com.flowforge.core.logging.CoreLogger
import com.flowforge.core.syntax.effect._
import com.flowforge.core.types.{ ConfigError, FlowForgeError }
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }

class EffectSyntaxExtensionsSpec extends AnyFunSuite with Matchers {

  implicit val ioEffectSystem: EffectSystem[IO] =
    com.flowforge.core.instances.EffectInstances.catsEffectSystemInstance
  implicit val ec: ExecutionContext = ExecutionContext.global

  // Simple test logger
  implicit val testLogger: CoreLogger[IO] = new CoreLogger[IO] {
    def info(msg: String): IO[Unit]  = IO.unit
    def warn(msg: String): IO[Unit]  = IO.unit
    def error(msg: String): IO[Unit] = IO.unit
  }

  // ===============================
  // EFFECT OPS TESTS
  // ===============================

  test("EffectOps.timeoutAfter should timeout long operations") {
    val longOp = IO.sleep(1.second) *> IO.pure(42)
    val timed  = longOp.timeoutAfter(100.milliseconds)

    assertThrows[Exception] {
      timed.unsafeRunSync()
    }
  }

  test("EffectOps.timeoutAfter should succeed for quick operations") {
    val quickOp = IO.pure(42)
    val timed   = quickOp.timeoutAfter(1.second)

    timed.unsafeRunSync() shouldBe 42
  }

  test("EffectOps.retryOnFailure should retry on failure") {
    var attempts = 0
    val flakeyOp = IO {
      attempts += 1
      if (attempts < 3) throw new RuntimeException("fail")
      else 42
    }

    val result = flakeyOp.retryOnFailure(5, 1.millisecond).unsafeRunSync()
    result shouldBe 42
    attempts shouldBe 3
  }

  test("EffectOps.retryOnFailure should fail after max retries") {
    var attempts = 0
    val alwaysFails = IO {
      attempts += 1
      throw new RuntimeException("always fails")
    }

    assertThrows[RuntimeException] {
      alwaysFails.retryOnFailure(2, 1.millisecond).unsafeRunSync()
    }
    attempts should be > 1
  }

  test("EffectOps.recoverWith should use fallback on failure") {
    val failing  = IO.raiseError[Int](new RuntimeException("error"))
    val fallback = IO.pure(42)

    val result = failing.recoverFrom { case _ => fallback }.unsafeRunSync()
    result shouldBe 42
  }

  test("EffectOps.recoverFrom should recover from specific errors") {
    val failing = IO.raiseError[Int](new ArithmeticException("divide by zero"))

    val recovered = failing.recoverFrom {
      case _: ArithmeticException => IO.pure(0)
    }

    recovered.unsafeRunSync() shouldBe 0
  }

  test("EffectOps.recoverFrom should not recover from other errors") {
    val failing = IO.raiseError[Int](new IllegalArgumentException("bad arg"))

    val recovered = failing.recoverFrom {
      case _: ArithmeticException => IO.pure(0)
    }

    assertThrows[IllegalArgumentException] {
      recovered.unsafeRunSync()
    }
  }

  test("EffectOps.guaranteeCleanup should run finalizer on success") {
    var cleaned   = false
    val op        = IO.pure(42)
    val finalizer = IO { cleaned = true }

    val result = op.guaranteeCleanup(finalizer).unsafeRunSync()
    result shouldBe 42
    cleaned shouldBe true
  }

  test("EffectOps.guaranteeCleanup should run finalizer on failure") {
    var cleaned   = false
    val op        = IO.raiseError[Int](new RuntimeException("error"))
    val finalizer = IO { cleaned = true }

    assertThrows[RuntimeException] {
      op.guaranteeCleanup(finalizer).unsafeRunSync()
    }
    cleaned shouldBe true
  }

  test("EffectOps.attempt should convert to Either") {
    val success = IO.pure(42)
    val failure = IO.raiseError[Int](new RuntimeException("error"))

    success.attempt.unsafeRunSync() shouldBe Right(42)
    failure.attempt.unsafeRunSync().isLeft shouldBe true
  }

  test("EffectOps.timed should measure execution time") {
    val op                 = IO.sleep(10.milliseconds) *> IO.pure(42)
    val (result, duration) = com.flowforge.core.algebra.EffectSystem[IO].timed(op).unsafeRunSync()

    result shouldBe 42
    duration.toMillis should be >= 10L
  }

  test("EffectOps.exec should ignore result") {
    val op     = IO.pure(42)
    val result = op.exec.unsafeRunSync()

    result shouldBe ()
  }

  test("EffectOps.logResult should log result") {
    val op     = IO.pure(42)
    val logged = op.logResult("Test result")

    logged.unsafeRunSync() shouldBe 42
  }

  test("EffectOps.logErrors should log errors") {
    val op     = IO.raiseError[Int](new RuntimeException("test error"))
    val logged = op.logErrors("Error occurred")

    assertThrows[RuntimeException] {
      logged.unsafeRunSync()
    }
  }

  // ===============================
  // PARALLEL COLLECTION OPS TESTS
  // ===============================

  test("ParallelCollectionOps.parTraverseEffect should process in parallel") {
    val list   = List(1, 2, 3, 4, 5)
    val result = list.parTraverseEffect(x => IO.pure(x * 2)).unsafeRunSync()

    result shouldBe List(2, 4, 6, 8, 10)
  }

  test("ParallelCollectionOps.traverseEffect should process sequentially") {
    val list   = List(1, 2, 3)
    val result = list.traverseEffect(x => IO.pure(x * 2)).unsafeRunSync()

    result shouldBe List(2, 4, 6)
  }

  test("ParallelCollectionOps.filterEffect should filter with effect") {
    val list   = List(1, 2, 3, 4, 5)
    val result = list.filterEffect(x => IO.pure(x % 2 == 0)).unsafeRunSync()

    result shouldBe List(2, 4)
  }

  test("ParallelEffectCollectionOps.parSequenceEffect should run in parallel") {
    val effects = List(IO.pure(1), IO.pure(2), IO.pure(3))
    val result  = effects.parSequenceEffect.unsafeRunSync()

    result shouldBe List(1, 2, 3)
  }

  test("ParallelEffectCollectionOps.sequenceEffect should run sequentially") {
    val effects = List(IO.pure(1), IO.pure(2), IO.pure(3))
    val result  = effects.sequenceEffect.unsafeRunSync()

    result shouldBe List(1, 2, 3)
  }

  // ===============================
  // RESOURCE OPS TESTS
  // ===============================

  test("ResourceOps.bracket should manage resource lifecycle") {
    var acquired = false
    var released = false

    val acquire = IO { acquired = true; "resource" }
    val use     = (r: String) => IO.pure(r.length)
    val release = (_: String) => IO { released = true }

    val result = acquire.bracket(use)(release).unsafeRunSync()

    result shouldBe 8
    acquired shouldBe true
    released shouldBe true
  }

  test("ResourceOps.bracket should release on failure") {
    var released = false

    val acquire = IO.pure("resource")
    val use     = (_: String) => IO.raiseError[Int](new RuntimeException("error"))
    val release = (_: String) => IO { released = true }

    assertThrows[RuntimeException] {
      acquire.bracket(use)(release).unsafeRunSync()
    }
    released shouldBe true
  }

  test("ResourceOps.using should auto-close AutoCloseable") {
    class TestCloseable extends AutoCloseable {
      var closed                 = false
      override def close(): Unit = closed = true
    }

    var closeable: TestCloseable = null
    val acquire                  = IO { closeable = new TestCloseable; closeable }
    val use                      = (c: TestCloseable) => IO.pure(42)

    val result = acquire.using(use).unsafeRunSync()

    result shouldBe 42
    closeable.closed shouldBe true
  }

  // ===============================
  // CONCURRENCY OPS TESTS
  // ===============================

  test("ConcurrencyOps.startFiber should start background task") {
    val op    = IO.sleep(10.milliseconds) *> IO.pure(42)
    val fiber = op.startFiber.unsafeRunSync()

    fiber.join.unsafeRunSync() shouldBe 42
  }

  test("ConcurrencyOps.raceWith should return first result") {
    val op1 = IO.sleep(100.milliseconds) *> IO.pure(1)
    val op2 = IO.sleep(10.milliseconds) *> IO.pure(2)

    val result = op1.raceWith(op2).unsafeRunSync()

    result shouldBe Right(2)
  }

  test("ConcurrencyOps.parWith should run in parallel") {
    val op1 = IO.sleep(10.milliseconds) *> IO.pure(1)
    val op2 = IO.sleep(10.milliseconds) *> IO.pure(2)

    val result = op1.parWith(op2).unsafeRunSync()

    result shouldBe (1, 2)
  }

  test("ConcurrencyOps.parMapN should combine results") {
    val op1 = IO.pure(1)
    val op2 = IO.pure(2)
    val op3 = IO.pure(3)

    val result = op1
      .parMapN(op2, op3)(
        (
          a,
          b,
          c,
        ) => a + b + c,
      ).unsafeRunSync()

    result shouldBe 6
  }

  // ===============================
  // VALIDATION OPS TESTS
  // ===============================

  test("ValidationOps.liftToEffect should lift valid value") {
    val validated: ValidatedNel[RuntimeException, Int] = Validated.valid(42)
    val result                                         = validated.liftToEffect[IO].unsafeRunSync()

    result shouldBe 42
  }

  test("ValidationOps.liftToEffect should fail for invalid value") {
    val error                                          = new RuntimeException("error")
    val validated: ValidatedNel[RuntimeException, Int] = Validated.invalidNel(error)

    assertThrows[RuntimeException] {
      validated.liftToEffect[IO].unsafeRunSync()
    }
  }

  test("ValidationOps.liftToEffectWith should use custom error handler") {
    val errors                               = NonEmptyList.of("error1", "error2")
    val validated: ValidatedNel[String, Int] = Validated.invalid(errors)

    val result =
      validated.liftToEffectWith[IO](errs => new RuntimeException(s"Errors: ${errs.toList.mkString(", ")}"))

    val thrown = intercept[RuntimeException] {
      result.unsafeRunSync()
    }
    thrown.getMessage should include("error1")
    thrown.getMessage should include("error2")
  }

  // ===============================
  // CONFIG VALIDATION OPS TESTS
  // ===============================

  test("ConfigValidationOps.toFlowForgeError should convert config errors") {
    val errors = NonEmptyList.of(
      ConfigError.MissingRequired("field1"),
      ConfigError.InvalidValue("field2", "bad", "good"),
    )

    val flowForgeError = errors.toFlowForgeError

    flowForgeError shouldBe a[FlowForgeError.CompositeError]
    val composite = flowForgeError.asInstanceOf[FlowForgeError.CompositeError]
    composite.errors.size shouldBe 2
  }

  // ===============================
  // CONVERSION OPS TESTS
  // ===============================

  test("ConversionOps.liftEffect should lift pure value") {
    val result = 42.liftEffect[IO].unsafeRunSync()
    result shouldBe 42
  }

  test("ConversionOps.liftNonNull should lift non-null value") {
    val result = 42.liftNonNull[IO](new NullPointerException("null")).unsafeRunSync()
    result shouldBe 42
  }

  test("ConversionOps.liftNonNull should fail for null value") {
    val nullValue: String = null

    assertThrows[NullPointerException] {
      nullValue.liftNonNull[IO](new NullPointerException("was null")).unsafeRunSync()
    }
  }

  test("FutureOps.liftToEffect should convert Future") {
    val future = Future.successful(42)
    val result = future.liftToEffect[IO].unsafeRunSync()

    result shouldBe 42
  }

  test("FutureOps.liftToEffect should handle failed Future") {
    val future = Future.failed[Int](new RuntimeException("error"))

    assertThrows[RuntimeException] {
      future.liftToEffect[IO].unsafeRunSync()
    }
  }

  test("EitherOps.liftToEffect should lift Right") {
    val either: Either[RuntimeException, Int] = Right(42)
    val result                                = either.liftToEffect[IO].unsafeRunSync()

    result shouldBe 42
  }

  test("EitherOps.liftToEffect should fail for Left") {
    val either: Either[RuntimeException, Int] = Left(new RuntimeException("error"))

    assertThrows[RuntimeException] {
      either.liftToEffect[IO].unsafeRunSync()
    }
  }

  // ===============================
  // UTILITY OPS TESTS
  // ===============================

  test("UtilityOps.whenM should execute when condition is true") {
    val condition = IO.pure(true)
    val op        = IO.pure(42)

    val result = op.whenM(condition).unsafeRunSync()

    result shouldBe Some(42)
  }

  test("UtilityOps.whenM should not execute when condition is false") {
    val condition = IO.pure(false)
    val op        = IO.pure(42)

    val result = op.whenM(condition).unsafeRunSync()

    result shouldBe None
  }

  test("UtilityOps.when should execute when condition is true") {
    val result = IO.pure(42).when(true).unsafeRunSync()
    result shouldBe Some(42)
  }

  test("UtilityOps.when should not execute when condition is false") {
    val result = IO.pure(42).when(false).unsafeRunSync()
    result shouldBe None
  }

  test("UtilityOps.unless should not execute when condition is true") {
    val result = IO.pure(42).unless(true).unsafeRunSync()
    result shouldBe None
  }

  test("UtilityOps.unless should execute when condition is false") {
    val result = IO.pure(42).unless(false).unsafeRunSync()
    result shouldBe Some(42)
  }

  test("UtilityOps.tap should run side effect without changing result") {
    var sideEffect = 0
    val result = IO
      .pure(42)
      .tap(x => IO { sideEffect = x * 2 })
      .unsafeRunSync()

    result shouldBe 42
    sideEffect shouldBe 84
  }

  test("UtilityOps.mapWhen should transform when predicate matches") {
    val result = IO
      .pure(5)
      .mapWhen(_ > 3)(_ * 2)
      .unsafeRunSync()

    result shouldBe 10
  }

  test("UtilityOps.mapWhen should not transform when predicate doesn't match") {
    val result = IO
      .pure(2)
      .mapWhen(_ > 3)(_ * 2)
      .unsafeRunSync()

    result shouldBe 2
  }

  test("UtilityOps.mapError should transform errors") {
    val op     = IO.raiseError[Int](new RuntimeException("original"))
    val mapped = op.mapError(e => new IllegalStateException(s"Transformed: ${e.getMessage}"))

    val thrown = intercept[IllegalStateException] {
      mapped.unsafeRunSync()
    }
    thrown.getMessage should include("Transformed")
    thrown.getMessage should include("original")
  }

  // ===============================
  // INTEGRATION TESTS
  // ===============================

  test("Combining multiple syntax extensions should work") {
    val result = IO
      .pure(5)
      .map(_ * 2)
      .timeoutAfter(1.second)
      .retryOnFailure(3, 1.millisecond)
      .attempt
      .unsafeRunSync()

    result shouldBe Right(10)
  }

  test("Complex pipeline with error handling should work") {
    var attempts = 0
    val pipeline = IO {
      attempts += 1
      if (attempts < 2) throw new RuntimeException("transient")
      else 42
    }
      .retryOnFailure(3, 1.millisecond)
      .map(_ * 2)
      .guaranteeCleanup(IO.unit)

    val result = pipeline.unsafeRunSync()
    result shouldBe 84
    attempts shouldBe 2
  }

  test("Parallel processing with error recovery should work") {
    val list = List(1, 2, 3, 4, 5)

    val result = list.parTraverseEffect { x =>
      if (x == 3) IO.raiseError[Int](new RuntimeException("error"))
      else IO.pure(x * 2)
    }.recoverFrom { case _ => IO.pure(List(0)) }
      .unsafeRunSync()

    result shouldBe List(0) // Fallback on error
  }

  test("Resource management with nested operations should work") {
    var acquired = false
    var released = false

    val result = IO { acquired = true; "resource" }.bracket { resource =>
      IO.pure(resource.length)
        .map(_ * 2)
        .timeoutAfter(1.second)
    } { _ =>
      IO { released = true }
    }
      .unsafeRunSync()

    result shouldBe 16
    acquired shouldBe true
    released shouldBe true
  }

  test("Concurrent operations with timeouts should work") {
    val op1 = IO.sleep(50.milliseconds) *> IO.pure(1)
    val op2 = IO.sleep(10.milliseconds) *> IO.pure(2)

    val result = op1
      .raceWith(op2)
      .timeoutAfter(100.milliseconds)
      .unsafeRunSync()

    result shouldBe Right(2)
  }

  test("Validation lifting with error transformation should work") {
    val validated: ValidatedNel[String, Int] = Validated.invalidNel("error")

    val result = validated
      .liftToEffectWith[IO](errors => new RuntimeException(errors.toList.mkString(", ")))
      .recoverFrom { case _ => IO.pure(0) }
      .unsafeRunSync()

    result shouldBe 0
  }

  test("Effect chaining with multiple transformations should work") {
    val result = 10
      .liftEffect[IO]
      .map(_ * 2)
      .flatMap(x => IO.pure(x + 5))
      .tap(x => IO.unit)
      .mapWhen(_ > 20)(_ * 2)
      .unsafeRunSync()

    result shouldBe 50 // ((10 * 2) + 5) * 2 = 50
  }
}
