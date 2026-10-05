// scalafix:off DisableSyntax.throw
package com.flowforge.core.algebra

import java.util.concurrent.atomic.{ AtomicBoolean, AtomicInteger }
import scala.concurrent.duration._

/**
 * The laws an `EffectSystem[F]` instance must satisfy, written once for every instance.
 *
 * Each law is a description of a check, not a run of it: a law is an `F[Boolean]` that a caller runs with
 * whatever runtime its `F` needs. That is what lets the Cats Effect and the ZIO adapter be held to one
 * list instead of two hand-written ones, which is the only way a reader can tell that the two adapters
 * agree. Per-adapter suites say what each adapter does; this list says what both of them owe.
 *
 * The laws deliberately avoid asserting on elapsed time. A law that passes only on an unloaded machine
 * reports the machine rather than the adapter.
 */
object EffectSystemLaws {

  /** One named check, as an effect that yields true when the law holds. */
  final case class Law[F[_]](name: String, holds: F[Boolean])

  /** Every law, for any instance. */
  def all[F[_]](implicit F: EffectSystem[F]): List[Law[F]] =
    monad ++ errors ++ suspension ++ resources ++ concurrency

  private val boom: Throwable = new RuntimeException("law failure")

  private val StackDepth = 10000

  /** Compare two effects by running both. Laws are equalities, and this is the only way to check one. */
  private def same[F[_], A](fa: F[A], fb: F[A])(implicit F: EffectSystem[F]): F[Boolean] =
    F.flatMap(fa)(a => F.map(fb)(b => a == b))

  /**
   * Wait until a flag is set, polling.
   *
   * Polling rather than a promise because `EffectSystem` has no promise, and polling is enough: the point
   * of the wait is that it can only finish if something else is running, which is the property under test.
   */
  private def awaitFlag[F[_]](flag: AtomicBoolean)(implicit F: EffectSystem[F]): F[Unit] =
    F.void(F.repeatUntil(F.flatMap(F.sleep(5.millis))(_ => F.delay(flag.get())))(identity))

  private def monad[F[_]](implicit F: EffectSystem[F]): List[Law[F]] = {
    val f: Int => F[Int] = x => F.pure(x + 1)
    val g: Int => F[Int] = x => F.pure(x * 3)
    List(
      Law("left identity: flatMap on pure is the function itself", same(F.flatMap(F.pure(7))(f), f(7))),
      Law("right identity: flatMap with pure changes nothing", same(F.flatMap(F.pure(7))(F.pure), F.pure(7))),
      Law(
        "associativity: nesting of flatMap does not matter",
        same(
          F.flatMap(F.flatMap(F.pure(7))(f))(g),
          F.flatMap(F.pure(7))(x => F.flatMap(f(x))(g)),
        ),
      ),
      Law("map with identity changes nothing", same(F.map(F.pure(7))(identity), F.pure(7))),
      Law(
        s"tailRecM is stack safe to $StackDepth steps",
        F.map(F.tailRecM(0) { i =>
          F.pure(if (i < StackDepth) Left(i + 1) else Right(i))
        })(_ == StackDepth),
      ),
      Law(
        "traverse keeps the order of the input",
        F.map(F.traverse(List(1, 2, 3))(i => F.pure(i * 2)))(_ == List(2, 4, 6)),
      ),
      Law(
        "sequence keeps the order of the input",
        F.map(F.sequence(List(F.pure(1), F.pure(2), F.pure(3))))(_ == List(1, 2, 3)),
      ),
    )
  }

  private def errors[F[_]](implicit F: EffectSystem[F]): List[Law[F]] =
    List(
      Law(
        "handleErrorWith recovers a raised error",
        F.map(F.handleErrorWith(F.raiseError[Int](boom))(_ => F.pure(1)))(_ == 1),
      ),
      Law(
        "handleErrorWith leaves a success alone",
        F.map(F.handleErrorWith(F.pure(1))(_ => F.pure(2)))(_ == 1),
      ),
      Law("attempt reports a raised error as Left", F.map(F.attempt(F.raiseError[Int](boom)))(_ == Left(boom))),
      Law("attempt reports a success as Right", F.map(F.attempt(F.pure(1)))(_ == Right(1))),
      Law(
        "a raised error skips the rest of the chain",
        {
          val reached = new AtomicInteger(0)
          val chain   = F.flatMap(F.raiseError[Int](boom))(_ => F.delay(reached.incrementAndGet()))
          F.map(F.attempt(chain))(result => result == Left(boom) && reached.get() == 0)
        },
      ),
      Law(
        "delay turns a thrown exception into a raised error",
        F.map(F.attempt(F.delay[Int](throw boom)))(_ == Left(boom)),
      ),
      Law("fromEither of a Left raises it", F.map(F.attempt(F.fromEither[Int](Left(boom))))(_ == Left(boom))),
      Law("fromEither of a Right succeeds", F.map(F.fromEither[Int](Right(1)))(_ == 1)),
      Law(
        "retryWithBackoff runs the effect again until it succeeds",
        {
          val attempts = new AtomicInteger(0)
          val flaky = F.flatMap(F.delay(attempts.incrementAndGet())) { attempt =>
            if (attempt < 3) F.raiseError[Int](boom) else F.pure(attempt)
          }
          F.map(F.retryWithBackoff(flaky, maxRetries = 5, initialDelay = 1.milli))(_ == 3)
        },
      ),
    )

  private def suspension[F[_]](implicit F: EffectSystem[F]): List[Law[F]] =
    List(
      Law(
        "delay does not run its thunk until the effect is run, and runs it once per run",
        {
          val runs   = new AtomicInteger(0)
          val effect = F.delay(runs.incrementAndGet())
          // The read is itself an effect, so it has to happen inside the law rather than beside it.
          F.flatMap(F.delay(runs.get())) { before =>
            F.flatMap(effect) { first =>
              F.map(effect)(second => before == 0 && first == 1 && second == 2)
            }
          }
        },
      ),
      Law(
        "suspend does not build the effect until it is run",
        {
          val builds = new AtomicInteger(0)
          val effect = F.suspend {
            builds.incrementAndGet()
            F.pure(1)
          }
          F.flatMap(F.delay(builds.get())) { before =>
            F.map(effect)(value => before == 0 && value == 1 && builds.get() == 1)
          }
        },
      ),
      Law(
        "suspend turns an exception thrown while building into a raised error",
        F.map(F.attempt(F.suspend[Int](throw boom)))(_ == Left(boom)),
      ),
    )

  private def resources[F[_]](implicit F: EffectSystem[F]): List[Law[F]] =
    List(
      Law(
        "bracket releases the resource after use",
        {
          val released = new AtomicBoolean(false)
          val run = F.bracket(F.pure("resource"))(_ => F.pure(1))(_ => F.delay(released.set(true)))
          F.map(run)(value => value == 1 && released.get())
        },
      ),
      Law(
        "bracket releases the resource when use fails, and re-raises the original error",
        {
          val released = new AtomicBoolean(false)
          val run =
            F.bracket(F.pure("resource"))(_ => F.raiseError[Int](boom))(_ => F.delay(released.set(true)))
          F.map(F.attempt(run))(result => result == Left(boom) && released.get())
        },
      ),
      Law(
        "bracketCase reports Completed when use succeeds",
        {
          val seen = new AtomicInteger(0)
          val run = F.bracketCase(F.pure("resource"))(_ => F.pure(1)) {
            case (_, F.ExitCase.Completed) => F.void(F.delay(seen.incrementAndGet()))
            case _                         => F.pure(())
          }
          F.map(run)(value => value == 1 && seen.get() == 1)
        },
      ),
      Law(
        "bracketCase reports the error when use fails",
        {
          val seen = new AtomicInteger(0)
          val run = F.bracketCase(F.pure("resource"))(_ => F.raiseError[Int](boom)) {
            case (_, F.ExitCase.Error(error)) if error == boom => F.void(F.delay(seen.incrementAndGet()))
            case _                                            => F.pure(())
          }
          F.map(F.attempt(run))(result => result == Left(boom) && seen.get() == 1)
        },
      ),
      Law(
        "guarantee runs the finalizer whether the effect succeeds or fails",
        {
          val finalized = new AtomicInteger(0)
          val finalizer = F.delay(finalized.incrementAndGet())
          F.flatMap(F.guarantee(F.pure(1))(F.void(finalizer))) { value =>
            F.map(F.attempt(F.guarantee(F.raiseError[Int](boom))(F.void(finalizer)))) { result =>
              value == 1 && result == Left(boom) && finalized.get() == 2
            }
          }
        },
      ),
    )

  private def concurrency[F[_]](implicit F: EffectSystem[F]): List[Law[F]] =
    List(
      Law(
        "a started fiber can be joined for its result",
        F.flatMap(F.start(F.pure(42)))(fiber => F.map(fiber.join)(_ == 42)),
      ),
      Law(
        "parTraverse returns results in the order of the input",
        F.map(F.parTraverse(List(1, 2, 3, 4, 5))(i => F.pure(i * 2)))(_ == List(2, 4, 6, 8, 10)),
      ),
      Law(
        "parProduct runs both effects at the same time",
        {
          val leftStarted  = new AtomicBoolean(false)
          val rightStarted = new AtomicBoolean(false)
          val left  = F.flatMap(F.delay(leftStarted.set(true)))(_ => F.map(awaitFlag(rightStarted))(_ => 1))
          val right = F.flatMap(F.delay(rightStarted.set(true)))(_ => F.map(awaitFlag(leftStarted))(_ => 2))
          // Each side waits for the other to announce itself, so this can only finish if both are running.
          // A sequential parProduct would wait rather than return a wrong answer, which is what the
          // timeout turns into a failing law.
          F.map(F.attempt(F.timeout(F.parProduct(left, right), 10.seconds)))(_ == Right((1, 2)))
        },
      ),
      Law(
        "timeout lets an effect that finishes in time through",
        F.map(F.timeout(F.pure(1), 10.seconds))(_ == 1),
      ),
      Law(
        "timeout fails an effect that takes too long",
        F.map(F.attempt(F.timeout(F.sleep(10.seconds), 50.millis)))(_.isLeft),
      ),
      Law(
        "race returns the side that finishes first",
        F.map(F.race(F.flatMap(F.sleep(10.seconds))(_ => F.pure(1)), F.pure(2)))(_ == Right(2)),
      ),
    )
}
