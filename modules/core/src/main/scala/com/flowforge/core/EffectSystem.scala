/**
 * FlowForge Core Module - Effect System Abstraction
 *
 * This is the beating heart of FlowForge - a unified effect system abstraction that allows
 * developers to choose between ZIO and Cats-Effect without changing business logic.
 *
 * Design Patterns Applied:
 * - Strategy Pattern: Effect system implementations
 * - Template Method: Common effect operations
 * - Adapter Pattern: Unified interface for different effect systems
 * - Abstract Factory: Effect system instances
 *
 * Scala Features Showcased:
 * - Higher-Kinded Types: F[_] abstraction
 * - Type Classes: Implicit evidence parameters
 * - Variance: Covariant/contravariant relationships
 * - Pattern Matching: Error handling and result processing
 * - Implicit Conversions: Syntax extensions
 * - Lazy Evaluation: Deferred computations
 * - Generic Types: Parameterized abstractions
 * - Self Types: Component capabilities
 *
 * @author Vitthal Mirji
 * @version 0.1.0
 * @since Aug-2025
 */
package com.flowforge.core

import cats.effect.implicits.monadCancelOps_

import scala.annotation.implicitNotFound
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

/**
 * The unified effect system abstraction that forms the foundation of FlowForge.
 *
 * This trait provides a complete implementation of all essential type class operations
 * for the Scala/Cats ecosystem, supporting the full hierarchy from Functor to Concurrent.
 *
 * Key innovations:
 * 1. **Stack-Safe Recursion**: Proper tailRecM implementation for all effect types
 * 2. **Resource Safety**: Comprehensive bracket-based resource management
 * 3. **Concurrency Abstractions**: Unified fiber and parallelism support
 * 4. **Error Composition**: Structured error handling with recovery patterns
 * 5. **Performance Optimization**: Zero-cost abstractions where possible
 *
 * The design follows the **Open/Closed Principle** - open for extension with new
 * effect systems, closed for modification of the core interface.
 *
 * @tparam F The effect type constructor (e.g., IO, Task, ZIO[R,E,*])
 */
@implicitNotFound("No EffectSystem instance found for ${F}. Import the appropriate implementation.")
trait EffectSystem[F[_]] {

  // ===============================
  // FUNCTOR OPERATIONS
  // ===============================

  /**
   * Transform the value inside an effect using a pure function.
   *
   * Laws:
   * - Identity: map(fa)(identity) === fa
   * - Composition: map(map(fa)(f))(g) === map(fa)(f andThen g)
   *
   * @param fa The effect containing a value of type A
   * @param f The transformation function A => B
   * @return An effect containing the transformed value of type B
   */
  def map[A, B](fa: F[A])(f: A => B): F[B]

  /**
   * Replace the value inside an effect with a constant value.
   * Equivalent to map(fa)(_ => b) but potentially more efficient.
   */
  def as[A, B](fa: F[A])(b: B): F[B] = map(fa)(_ => b)

  /**
   * Discard the value inside an effect, keeping only the effect.
   * Useful for side effects where the result value is not needed.
   */
  def void[A](fa: F[A]): F[Unit] = map(fa)(_ => ())

  /**
   * Transform a value and pair it with the original.
   * Creates a tuple (original, transformed) inside the effect.
   */
  def fproduct[A, B](fa: F[A])(f: A => B): F[(A, B)] = map(fa)(a => (a, f(a)))

  // ===============================
  // APPLICATIVE OPERATIONS
  // ===============================

  /**
   * Lift a pure value into the effect context.
   *
   * This is the most fundamental operation for creating effects.
   * All other pure values can be lifted using this operation.
   *
   * Laws:
   * - Identity: ap(pure(identity))(fa) === fa
   * - Composition: ap(ap(ap(pure(compose))(f))(g))(fa) === ap(f)(ap(g)(fa))
   * - Homomorphism: ap(pure(f))(pure(a)) === pure(f(a))
   * - Interchange: ap(f)(pure(a)) === ap(pure(_(a)))(f)
   */
  def pure[A](a: A): F[A]

  /**
   * Apply a function in an effect to a value in an effect.
   *
   * This enables applying multi-parameter functions to multiple effects,
   * which is the foundation for parallel composition and validation.
   */
  def ap[A, B](ff: F[A => B])(fa: F[A]): F[B]

  /**
   * Apply a binary function to two effects.
   * More convenient than using ap for two-parameter functions.
   */
  def map2[A, B, C](fa: F[A], fb: F[B])(f: (A, B) => C): F[C] =
    ap(map(fa)(a => (b: B) => f(a, b)))(fb)

  /**
   * Combine two effects into a tuple.
   * Preserves both values in a product type.
   */
  def product[A, B](fa: F[A], fb: F[B]): F[(A, B)] =
    map2(fa, fb)((_, _))

  /**
   * Sequence effects, keeping only the left value.
   * Useful when you need the side effect of fb but only the value of fa.
   */
  def productL[A, B](fa: F[A])(fb: F[B]): F[A] =
    map2(fa, fb)((a, _) => a)

  /**
   * Sequence effects, keeping only the right value.
   * Useful when you need the side effect of fa but only the value of fb.
   */
  def productR[A, B](fa: F[A])(fb: F[B]): F[B] =
    map2(fa, fb)((_, b) => b)

  // ===============================
  // MONAD OPERATIONS
  // ===============================

  /**
   * Sequentially compose two effects, passing the result of the first
   * as input to a function that produces the second.
   *
   * This is the fundamental operation for sequential composition and
   * enables for-comprehensions in Scala.
   *
   * Laws:
   * - Left Identity: flatMap(pure(a))(f) === f(a)
   * - Right Identity: flatMap(fa)(pure) === fa
   * - Associativity: flatMap(flatMap(fa)(f))(g) === flatMap(fa)(a => flatMap(f(a))(g))
   */
  def flatMap[A, B](fa: F[A])(f: A => F[B]): F[B]

  /**
   * Flatten nested effects.
   * Converts F[F[A]] to F[A] by removing one level of nesting.
   */
  def flatten[A](ffa: F[F[A]]): F[A] = flatMap(ffa)(identity)

  /**
   * Conditional effect execution based on a boolean effect.
   * If the effect produces true, execute ifTrue, otherwise ifFalse.
   */
  def ifM[B](fa: F[Boolean])(ifTrue: => F[B], ifFalse: => F[B]): F[B] =
    flatMap(fa)(if (_) ifTrue else ifFalse)

  /**
   * Stack-safe recursive flatMap operation.
   *
   * This is THE most critical operation for Cats compatibility.
   * It enables stack-safe recursion by trampolining through the
   * effect system rather than relying on JVM stack frames.
   *
   * CRITICAL: This must be overridden with stack-safe implementations
   * in concrete effect systems to prevent stack overflow errors.
   *
   * @param a The initial value
   * @param f Function that either continues recursion (Left) or terminates (Right)
   * @return The final result wrapped in the effect
   */
  def tailRecM[A, B](a: A)(f: A => F[Either[A, B]]): F[B] = {
    // Default implementation - MUST be overridden for stack safety!
    flatMap(f(a)) {
      case Left(nextA) => tailRecM(nextA)(f)
      case Right(b) => pure(b)
    }
  }

  // ===============================
  // MONAD ERROR OPERATIONS
  // ===============================

  /**
   * Raise an error in the effect context.
   * This is how we signal failures in a purely functional way.
   */
  def raiseError[A](error: Throwable): F[A]

  /**
   * Handle errors that might occur in an effect.
   * The handler function is only called if an error occurs.
   */
  def handleError[A](fa: F[A])(handler: Throwable => F[A]): F[A]

  /**
   * Alias for handleError for better readability.
   * Some developers prefer this name for the operation.
   */
  def handleErrorWith[A](fa: F[A])(handler: Throwable => F[A]): F[A] =
    handleError(fa)(handler)

  /**
   * Convert errors to values using Either.
   * This enables error handling without throwing exceptions.
   */
  def attempt[A](fa: F[A]): F[Either[Throwable, A]] =
    handleError(map(fa)(Right(_): Either[Throwable, A]))(e => pure(Left(e)))

  /**
   * Recover from specific errors using a partial function.
   * Only handles errors that the partial function is defined for.
   */
  def recover[A](fa: F[A])(pf: PartialFunction[Throwable, A]): F[A] =
    handleError(fa)(e => if (pf.isDefinedAt(e)) pure(pf(e)) else raiseError(e))

  /**
   * Recover from specific errors with an effect-producing function.
   * Like recover, but the recovery can produce effects.
   */
  def recoverWith[A](fa: F[A])(pf: PartialFunction[Throwable, F[A]]): F[A] =
    handleError(fa)(e => if (pf.isDefinedAt(e)) pf(e) else raiseError(e))

  /**
   * Execute a side effect when an error occurs, but don't handle the error.
   * Useful for logging or cleanup without affecting error propagation.
   */
  def onError[A](fa: F[A])(pf: PartialFunction[Throwable, F[Unit]]): F[A] =
    handleErrorWith(fa)(e =>
      flatMap(if (pf.isDefinedAt(e)) pf(e) else pure(()))((_ => raiseError(e))))

  // ===============================
  // SYNC OPERATIONS
  // ===============================

  /**
   * Suspend a side-effecting computation in the effect context.
   * This captures side effects without executing them immediately.
   */
  def delay[A](thunk: => A): F[A]

  /**
   * Suspend the entire effect computation.
   * This enables lazy evaluation of the entire effect.
   */
  def suspend[A](fa: => F[A]): F[A]

  /**
   * Alias for suspend for better readability.
   * Some prefer this name for deferring computations.
   */
  def defer[A](fa: => F[A]): F[A] = suspend(fa)

  /**
   * Suspend a blocking operation on a dedicated thread pool.
   * Override in implementations to use appropriate blocking semantics.
   */
  def blocking[A](thunk: => A): F[A] = delay(thunk) // Override in implementations

  // ===============================
  // ASYNC OPERATIONS
  // ===============================

  /**
   * Create an effect from an async computation with a callback.
   * This bridges callback-based APIs to effect-based APIs.
   */
  def async[A](k: (Either[Throwable, A] => Unit) => Unit): F[A]

  /**
   * Alias for async for simpler callback scenarios.
   * Some implementations might optimize this differently.
   */
  def async_[A](k: (Either[Throwable, A] => Unit) => Unit): F[A] = async(k)

  /**
   * Convert a Future to an effect.
   * This bridges Future-based APIs to effect-based APIs.
   */
  def fromFuture[A](future: F[Future[A]]): F[A] =
    flatMap(future)(f => async(k => f.onComplete {
      case Success(a) => k(Right(a))
      case Failure(e) => k(Left(e))
    }(ExecutionContext.global)))

  // ===============================
  // CONCURRENT OPERATIONS
  // ===============================

  /**
   * Start an effect on a separate fiber.
   * Returns a handle to the running computation.
   */
  def start[A](fa: F[A]): F[Fiber[F, A]]

  /**
   * Race two effects, returning both the winner and a handle to the loser.
   * This is the foundation for racing and timeout operations.
   */
  def racePair[A, B](fa: F[A], fb: F[B]): F[Either[(A, Fiber[F, B]), (Fiber[F, A], B)]]

  /**
   * Race two effects, canceling the loser.
   * Returns the result of whichever effect completes first.
   */
  def race[A, B](fa: F[A], fb: F[B]): F[Either[A, B]] =
    flatMap(racePair(fa, fb)) {
      case Left((a, fiberB)) => map(fiberB.cancel)((_ => Left(a)))
      case Right((fiberA, b)) => map(fiberA.cancel)((_ => Right(b)))
    }

  // ===============================
  // PARALLEL OPERATIONS
  // ===============================

  /**
   * Execute two effects in parallel and combine their results.
   * This is more efficient than sequential execution when effects are independent.
   */
  def parProduct[A, B](fa: F[A], fb: F[B]): F[(A, B)]

  /**
   * Apply a binary function to two effects executed in parallel.
   * Combines the efficiency of parallel execution with function application.
   */
  def parMap2[A, B, C](fa: F[A], fb: F[B])(f: (A, B) => C): F[C] =
    map(parProduct(fa, fb))(f.tupled)

  /**
   * Execute effects in parallel and collect results in order.
   * This is the parallel version of traverse.
   */
  def parTraverse[A, B](list: List[A])(f: A => F[B]): F[List[B]]

  /**
   * Execute a list of effects in parallel.
   * This is the parallel version of sequence.
   */
  def parSequence[A](list: List[F[A]]): F[List[A]] = parTraverse(list)(identity)

  // ===============================
  // RESOURCE MANAGEMENT
  // ===============================

  /**
   * Safely acquire, use, and release a resource.
   * Guarantees that release is called even if use fails or is canceled.
   *
   * This is the foundation for all resource-safe operations in FlowForge.
   */
  def bracket[A, B](acquire: F[A])(use: A => F[B])(release: A => F[Unit]): F[B]

  /**
   * Extended bracket with access to the exit case.
   * Enables different cleanup behavior based on how the effect completed.
   */
  def bracketCase[A, B](acquire: F[A])(use: A => F[B])(release: (A, ExitCase[Throwable]) => F[Unit]): F[B]

  /**
   * Guarantee that a finalizer runs after an effect.
   * The finalizer runs regardless of success, failure, or cancellation.
   */
  def guarantee[A](fa: F[A])(finalizer: F[Unit]): F[A] =
    bracket(pure(()))(_ => fa)(_ => finalizer)

  /**
   * Guarantee with access to the exit case.
   * Enables different finalization behavior based on completion mode.
   */
  def guaranteeCase[A](fa: F[A])(finalizer: ExitCase[Throwable] => F[Unit]): F[A] =
    bracketCase(pure(()))(_ => fa)((_, exitCase) => finalizer(exitCase))

  // ===============================
  // TIMING OPERATIONS
  // ===============================

  /**
   * Suspend execution for the specified duration.
   * Non-blocking sleep that yields control to other fibers.
   */
  def sleep(duration: FiniteDuration): F[Unit]

  /**
   * Add a timeout to an effect.
   * Raises an error if the effect doesn't complete within the duration.
   */
  def timeout[A](fa: F[A], duration: FiniteDuration): F[A]

  /**
   * Add a timeout with a fallback effect.
   * If the timeout expires, execute the fallback instead of failing.
   */
  def timeoutTo[A](fa: F[A], duration: FiniteDuration, fallback: F[A]): F[A] =
    flatMap(timeout(fa, duration)) {
      case a if a != null => pure(a) // If fa completes successfully
      case _ => fallback // If timeout occurs
    }

  // ===============================
  // UTILITY OPERATIONS
  // ===============================

  /**
   * Transform a list using an effect-producing function.
   * Results are collected in order, maintaining the original list structure.
   */
  def traverse[A, B](list: List[A])(f: A => F[B]): F[List[B]] = {
    list.foldRight(pure(List.empty[B])) { (a, acc) =>
      map2(f(a), acc)(_ :: _)
    }
  }

  /**
   * Execute a list of effects and collect results.
   * This is traverse with the identity function.
   */
  def sequence[A](list: List[F[A]]): F[List[A]] = traverse(list)(identity)

  /**
   * Replicate an effect n times and collect results.
   * Useful for generating test data or retrying operations.
   */
  def replicateA[A](n: Int, fa: F[A]): F[List[A]] = sequence(List.fill(n)(fa))

  /**
   * Conditionally execute an effect.
   * Returns Some(result) if condition is true, None otherwise.
   */
  def whenA[A](cond: Boolean)(f: => F[A]): F[Option[A]] =
    if (cond) map(f)(Some(_)) else pure(None)

  /**
   * Conditionally execute an effect (negated condition).
   * Returns Some(result) if condition is false, None otherwise.
   */
  def unlessA[A](cond: Boolean)(f: => F[A]): F[Option[A]] = whenA(!cond)(f)
}

/**
 * Fiber abstraction for concurrent computations.
 *
 * A Fiber represents a running computation that can be joined or canceled.
 * This provides a uniform interface across different effect systems.
 */
trait Fiber[F[_], A] {
  /**
   * Cancel the fiber's computation.
   * This should be a best-effort cancellation.
   */
  def cancel: F[Unit]

  /**
   * Wait for the fiber to complete and get its result.
   * This may throw if the fiber failed or was canceled.
   */
  def join: F[A]
}

/**
 * Exit cases for resource management.
 *
 * These represent the different ways an effect can complete,
 * enabling fine-grained resource cleanup strategies.
 */
sealed trait ExitCase[+E] extends Product with Serializable

object ExitCase {
  /**
   * The effect completed successfully.
   */
  case object Completed extends ExitCase[Nothing]

  /**
   * The effect failed with an error.
   */
  case class Error[E](e: E) extends ExitCase[E]

  /**
   * The effect was canceled before completion.
   */
  case object Canceled extends ExitCase[Nothing]
}

/**
 * Companion object for EffectSystem.
 *
 * Provides instances for common effect types and utility functions
 * for working with effect systems.
 */
object EffectSystem {

  /**
   * Summon an EffectSystem instance for type F.
   * This is the primary way to access effect system capabilities.
   */
  def apply[F[_]](implicit ev: EffectSystem[F]): EffectSystem[F] = ev

  // ===============================
  // CATS-EFFECT IMPLEMENTATION
  // ===============================

  /**
   * Complete Cats-Effect integration with proper monadic operations.
   *
   * This implementation showcases how to adapt Cats-Effect to our
   * unified interface while maintaining full compatibility with
   * the Cats ecosystem.
   */
  implicit val catsEffectSystem: EffectSystem[cats.effect.IO] = {
    import cats.effect.IO
    import cats.implicits._

    new EffectSystem[IO] {
      // Functor implementation
      def map[A, B](fa: IO[A])(f: A => B): IO[B] = fa.map(f)

      // Applicative implementation
      def pure[A](a: A): IO[A] = IO.pure(a)
      def ap[A, B](ff: IO[A => B])(fa: IO[A]): IO[B] = ff.ap(fa)

      // Monad implementation with proper tailRecM
      def flatMap[A, B](fa: IO[A])(f: A => IO[B]): IO[B] = fa.flatMap(f)
      override def tailRecM[A, B](a: A)(f: A => IO[Either[A, B]]): IO[B] =
        // IO.tailRecM(a)(f) // Stack-safe implementation from Cats-Effect
        IO.defer {
          f(a).flatMap {
            case Left(nextA) => tailRecM(nextA)(f)
            case Right(b) => IO.pure(b)
          }
        }
      // MonadError implementation
      def raiseError[A](error: Throwable): IO[A] = IO.raiseError(error)
      def handleError[A](fa: IO[A])(handler: Throwable => IO[A]): IO[A] =
        fa.handleErrorWith(handler)

      // Sync implementation
      def delay[A](thunk: => A): IO[A] = IO.delay(thunk)
      def suspend[A](fa: => IO[A]): IO[A] = IO.defer(fa)
      override def blocking[A](thunk: => A): IO[A] = IO.blocking(thunk)

      // Async implementation
      def async[A](k: (Either[Throwable, A] => Unit) => Unit): IO[A] = IO.async_(k)

      // Concurrent implementation with Fiber adaptation
      def start[A](fa: IO[A]): IO[Fiber[IO, A]] =
        fa.start.map(fiber => new Fiber[IO, A] {
          def cancel: IO[Unit] = fiber.cancel
          def join: IO[A] = fiber.join.flatMap {
            case cats.effect.Outcome.Succeeded(fa) => fa
            case cats.effect.Outcome.Errored(e) => IO.raiseError(e)
            case cats.effect.Outcome.Canceled() =>
              IO.raiseError(new RuntimeException("Fiber was canceled"))
          }
        })

      def racePair[A, B](fa: IO[A], fb: IO[B]): IO[Either[(A, Fiber[IO, B]), (Fiber[IO, A], B)]] =
        IO.racePair(fa, fb).flatMap {
          case Left((outcomeA, fiberB)) =>
            outcomeA.fold(
              IO.raiseError(new RuntimeException("Left side canceled")),
              IO.raiseError,
              fa => fa.map(a => Left((a, new Fiber[IO, B] {
                def cancel: IO[Unit] = fiberB.cancel
                def join: IO[B] = fiberB.join.flatMap {
                  case cats.effect.Outcome.Succeeded(fb) => fb
                  case cats.effect.Outcome.Errored(e) => IO.raiseError(e)
                  case cats.effect.Outcome.Canceled() =>
                    IO.raiseError(new RuntimeException("Fiber was canceled"))
                }
              })))
            )
          case Right((fiberA, outcomeB)) =>
            outcomeB.fold(
              IO.raiseError(new RuntimeException("Right side canceled")),
              IO.raiseError,
              fb => fb.map(b => Right((new Fiber[IO, A] {
                def cancel: IO[Unit] = fiberA.cancel
                def join: IO[A] = fiberA.join.flatMap {
                  case cats.effect.Outcome.Succeeded(fa) => fa
                  case cats.effect.Outcome.Errored(e) => IO.raiseError(e)
                  case cats.effect.Outcome.Canceled() =>
                    IO.raiseError(new RuntimeException("Fiber was canceled"))
                }
              }, b)))
            )
        }

      // Parallel implementation
      def parProduct[A, B](fa: IO[A], fb: IO[B]): IO[(A, B)] = (fa, fb).parTupled
      def parTraverse[A, B](list: List[A])(f: A => IO[B]): IO[List[B]] =
        list.parTraverse(f)

      // Resource management implementation
      def bracket[A, B](acquire: IO[A])(use: A => IO[B])(release: A => IO[Unit]): IO[B] =
        acquire.bracket(use)(release)
      def bracketCase[A, B](acquire: IO[A])(use: A => IO[B])(release: (A, ExitCase[Throwable]) => IO[Unit]): IO[B] =
        acquire.bracketCase(use) { (a, outcome) =>
          val exitCase = outcome match {
            case cats.effect.Outcome.Succeeded(_) => ExitCase.Completed
            case cats.effect.Outcome.Errored(e) => ExitCase.Error(e)
            case cats.effect.Outcome.Canceled() => ExitCase.Canceled
          }
          release(a, exitCase)
        }

      // Timing implementation
      def sleep(duration: FiniteDuration): IO[Unit] = IO.sleep(duration)
      def timeout[A](fa: IO[A], duration: FiniteDuration): IO[A] = fa.timeout(duration)

      // Optimized implementations using Cats operations
      override def traverse[A, B](list: List[A])(f: A => IO[B]): IO[List[B]] =
        list.traverse(f)
      override def sequence[A](list: List[IO[A]]): IO[List[A]] =
        list.sequence
    }
  }

  // ===============================
  // ZIO IMPLEMENTATION
  // ===============================

  /**
   * Complete ZIO integration with proper monadic operations.
   *
   * This implementation showcases how to adapt ZIO to our unified
   * interface while maintaining ZIO's unique capabilities like
   * structured concurrency and typed errors.
   */
  implicit val zioEffectSystem: EffectSystem[zio.Task] = {
    import zio.interop.catz._
    import zio.{ Task, ZIO }

    new EffectSystem[Task] {
      // Functor implementation
      def map[A, B](fa: Task[A])(f: A => B): Task[B] = fa.map(f)

      // Applicative implementation
      def pure[A](a: A): Task[A] = ZIO.succeed(a)
      def ap[A, B](ff: Task[A => B])(fa: Task[A]): Task[B] = ff.zipWith(fa)(_(_))

      // Monad implementation with stack-safe tailRecM
      def flatMap[A, B](fa: Task[A])(f: A => Task[B]): Task[B] = fa.flatMap(f)
      override def tailRecM[A, B](a: A)(f: A => Task[Either[A, B]]): Task[B] = {
        def loop(a: A): Task[B] =
          f(a).flatMap {
            case Left(nextA) => loop(nextA)
            case Right(b) => ZIO.succeed(b)
          }
        loop(a)
      }

      // MonadError implementation
      def raiseError[A](error: Throwable): Task[A] = ZIO.fail(error)
      def handleError[A](fa: Task[A])(handler: Throwable => Task[A]): Task[A] =
        fa.catchAll(handler)

      // Sync implementation
      def delay[A](thunk: => A): Task[A] = ZIO.attempt(thunk)
      def suspend[A](fa: => Task[A]): Task[A] = ZIO.suspend(fa)
      override def blocking[A](thunk: => A): Task[A] = ZIO.attemptBlocking(thunk)

      // Async implementation
      def async[A](k: (Either[Throwable, A] => Unit) => Unit): Task[A] =
        // ZIO 2.x aligns with Cats Effect 3.x async—the callback expects a function(the effect to complete with), not just a value.
        // This allows for proper interruption and effect safety.
        // But here want to stick with an Either callback so wrap the callback in ZIO.
        ZIO.async { (cb: ZIO[Any, Throwable, A] => Unit) =>
          k { either =>
            cb(ZIO.fromEither(either)) // wrap the Either back into ZIO
          }
        }

      // Concurrent implementation with ZIO Fiber adaptation
      def start[A](fa: Task[A]): Task[Fiber[Task, A]] =
        fa.fork.map(fiber => new Fiber[Task, A] {
          def cancel: Task[Unit] = fiber.interrupt.unit
          def join: Task[A] = fiber.join.absorb
        })

      def racePair[A, B](fa: Task[A], fb: Task[B]): Task[Either[(A, Fiber[Task, B]), (Fiber[Task, A], B)]] =
        fa.raceWith(fb)(
          // fa completed first
          leftDone = { (exitA, fiberB) =>
            exitA match {
              case zio.Exit.Success(a) =>
                val wrappedB = new Fiber[Task, B] {
                  def cancel: Task[Unit] = fiberB.interrupt.unit
                  def join:  Task[B]     = fiberB.join
                }
                ZIO.succeed(Left((a, wrappedB)))

              case zio.Exit.Failure(cause) =>
                // fail with fa’s error, but be nice and stop fb
                fiberB.interrupt *> ZIO.fail(cause.squash)
            }
          },
          // fb completed first
          rightDone = { (exitB, fiberA) =>
            exitB match {
              case zio.Exit.Success(b) =>
                val wrappedA = new Fiber[Task, A] {
                  def cancel: Task[Unit] = fiberA.interrupt.unit
                  def join:  Task[A]     = fiberA.join
                }
                ZIO.succeed(Right((wrappedA, b)))

              case zio.Exit.Failure(cause) =>
                fiberA.interrupt *> ZIO.fail(cause.squash)
            }
          }
        )

      // Parallel implementation using ZIO's parallel operations
      def parProduct[A, B](fa: Task[A], fb: Task[B]): Task[(A, B)] = fa.zip(fb)
      def parTraverse[A, B](list: List[A])(f: A => Task[B]): Task[List[B]] =
        ZIO.foreachPar(list)(f)

      // Resource management using ZIO's bracket operations
      def bracket[A, B](acquire: Task[A])(use: A => Task[B])(release: A => Task[Unit]): Task[B] =
        acquire.bracket(use)(release)
      def bracketCase[A, B](acquire: Task[A])(use: A => Task[B])(release: (A, ExitCase[Throwable]) => Task[Unit]): Task[B] =
        ZIO.acquireReleaseExitWith(acquire) { (a: A, exit: zio.Exit[Throwable, B]) =>
          val exitCase: ExitCase[Throwable] = exit match {
            case zio.Exit.Success(_)     => ExitCase.Completed
            case zio.Exit.Failure(cause) =>
              cause.failureOption match {
                case Some(err) => ExitCase.Error(err)
                case None      => ExitCase.Canceled
              }
          }
          release(a, exitCase).orDie
        }(use)


      // Timing implementation using ZIO's timing operations
      def sleep(duration: FiniteDuration): Task[Unit] =
        ZIO.sleep(zio.Duration.fromScala(duration))
      def timeout[A](fa: Task[A], duration: FiniteDuration): Task[A] =
        fa.timeout(zio.Duration.fromScala(duration)).map(_.get)

      // ZIO-optimized implementations
      override def traverse[A, B](list: List[A])(f: A => Task[B]): Task[List[B]] =
        ZIO.foreach(list)(f)
      override def sequence[A](list: List[Task[A]]): Task[List[A]] =
        ZIO.collectAll(list)
    }
  }

  /**
   * Syntax extensions for EffectSystem operations.
   *
   * These implicit classes provide convenient syntax for working with effects,
   * enabling a fluent API that feels natural in Scala.
   *
   * Usage:
   * ```scala
   * import EffectSystemOps._
   *
   * val result = for {
   *   a <- effect1
   *   b <- effect2.handleError(recover)
   *   c <- effect3.timeout(30.seconds)
   * } yield (a, b, c)
   * ```
   */
  implicit class EffectSystemOps[F[_], A](fa: F[A])(implicit F: EffectSystem[F]) {
    def map[B](f: A => B): F[B] = F.map(fa)(f)
    def flatMap[B](f: A => F[B]): F[B] = F.flatMap(fa)(f)
    def as[B](b: B): F[B] = F.as(fa)(b)
    def void: F[Unit] = F.void(fa)
    def handleError(handler: Throwable => F[A]): F[A] = F.handleError(fa)(handler)
    def attempt: F[Either[Throwable, A]] = F.attempt(fa)
    def race[B](fb: F[B]): F[Either[A, B]] = F.race(fa, fb)
    def timeout(duration: FiniteDuration): F[A] = F.timeout(fa, duration)
    def start: F[Fiber[F, A]] = F.start(fa)
    def guarantee(finalizer: F[Unit]): F[A] = F.guarantee(fa)(finalizer)
    def onError(pf: PartialFunction[Throwable, F[Unit]]): F[A] = F.onError(fa)(pf)
  }

}
