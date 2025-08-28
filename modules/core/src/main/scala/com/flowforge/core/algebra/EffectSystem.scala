/**
 * FlowForge Core Module - Effect System Abstraction
 *
 * File: modules/core/src/main/scala/com/flowforge/core/algebra/EffectSystem.scala Package:
 * com.flowforge.core.algebra
 *
 * This file defines the universal effect system abstraction that enables FlowForge to work with
 * different effect libraries (ZIO, Cats-Effect, etc.) through a unified interface. This is the
 * heart of FlowForge's effect polymorphism.
 *
 * Design Patterns Applied:
 *   - Tagless Final Pattern: Effect abstraction without concrete effect types
 *   - Type Class Pattern: Capability-based programming with implicit resolution
 *   - Bridge Pattern: Unified interface bridging different effect implementations
 *   - Strategy Pattern: Different effect execution strategies via type class instances
 *
 * Scala Features Showcased:
 *   - Higher-Kinded Types: F[_] abstraction over effect containers
 *   - Type Classes: Implicit-based capability injection
 *   - Phantom Types: Compile-time effect capability tracking
 *   - Self Types: Compositional type constraints
 *   - Implicit Conversions: Seamless syntax extensions
 *   - Generic Programming: Polymorphic operations over effects
 *   - Variance: Covariant effect containers
 *
 * Innovation Highlights:
 *   - Stack-safe recursion with tailRecM for large data processing
 *   - Unified concurrency model abstracting Fibers/Tasks
 *   - Resource management with bracket patterns
 *   - Parallel operations with type-safe composition
 *   - Timeout and cancellation support
 *   - Seamless integration with for-comprehensions
 *
 * Usage Examples:
 * ```scala
 * def processData[F[_]: EffectSystem](data: List[String]): F[List[ProcessedData]] =
 *   for {
 *     // Parallel processing of data chunks
 *     processed <- EffectSystem[F].parTraverse(data)(processItem)
 *     // Resource-safe file writing
 *     _ <- EffectSystem[F].bracket(openFile)(writeData(processed))(closeFile)
 *     // Error recovery with fallback
 *     result <- EffectSystem[F].handleError(processed)(fallbackProcessing)
 *   } yield result
 *
 * // Works with any F[_] that has an EffectSystem instance
 * val zioResult: Task[List[ProcessedData]] = processData(data)
 * val catsResult: IO[List[ProcessedData]]  = processData(data)
 * ```
 *
 * @author
 *   FlowForge Team
 * @version 1.0.0
 * @since 2024
 */
package com.flowforge.core.algebra

import cats.MonadError

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
 * Universal effect system abstraction.
 *
 * This type class provides a unified interface for working with different effect systems in the
 * Scala ecosystem. It combines capabilities from:
 *   - Cats: Functor, Applicative, Monad, MonadError
 *   - Cats-Effect: Sync, Async, Concurrent, Resource management
 *   - Custom: FlowForge-specific operations like parallel processing
 *
 * The abstraction is designed to be:
 *   - Complete: Covers all essential effect operations
 *   - Performant: Optimized implementations for each effect system
 *   - Safe: Stack-safe recursion and resource management
 *   - Composable: Enables functional composition patterns
 *
 * @tparam F
 *   The effect type constructor (e.g., IO, Task, etc.)
 */
trait EffectSystem[F[_]] extends MonadError[F, Throwable] {

  // ===============================
  // CORE MONADIC OPERATIONS
  // ===============================

  /**
   * Stack-safe monadic tail recursion.
   *
   * This is CRITICAL for Cats compatibility and large data processing. Must be implemented
   * efficiently to prevent stack overflow.
   *
   * @param a
   *   Initial value
   * @param f
   *   Function that either continues (Left) or terminates (Right)
   * @return
   *   The final result when Right is produced
   */
  // def tailRecM[A, B](a: A)(f: A => F[Either[A, B]]): F[B]

  // ===============================
  // SYNCHRONOUS OPERATIONS
  // ===============================

  /**
   * Lift a side-effecting computation into the effect.
   *
   * @param thunk
   *   The side-effecting computation
   * @return
   *   Effect wrapping the computation
   */
  def delay[A](thunk: => A): F[A]

  /**
   * Suspend the creation of an effect.
   *
   * @param fa
   *   The suspended effect
   * @return
   *   Suspended effect creation
   */
  def suspend[A](fa: => F[A]): F[A]

  /**
   * Mark computation as blocking (thread pool shifting).
   *
   * @param thunk
   *   Potentially blocking computation
   * @return
   *   Effect on blocking thread pool
   */
  def blocking[A](thunk: => A): F[A] = delay(thunk)

  // ===============================
  // ASYNCHRONOUS OPERATIONS
  // ===============================

  /**
   * Create an effect from an async callback.
   *
   * @param k
   *   Callback-based async operation
   * @return
   *   Effect representing the async operation
   */
  def async[A](k: (Either[Throwable, A] => Unit) => Unit): F[A]

  /**
   * Convert a Future to an effect.
   *
   * @param future
   *   The Future to convert
   * @param ec
   *   ExecutionContext for Future operations
   * @return
   *   Effect representation of the Future
   */
  def fromFuture[A](future: => Future[A])(implicit ec: ExecutionContext): F[A] =
    async[A] { callback =>
      future.onComplete {
        case Success(value) => callback(Right(value))
        case Failure(error) => callback(Left(error))
      }
    }

  /**
   * Convert a Try to an effect.
   *
   * @param t
   *   The Try to convert
   * @return
   *   Effect representation of the Try
   */
  def fromTry[A](t: Try[A]): F[A] = t match {
    case Success(value) => pure(value)
    case Failure(error) => raiseError(error)
  }

  /**
   * Convert an Either to an effect.
   *
   * @param either
   *   The Either to convert
   * @return
   *   Effect representation of the Either
   */
  override def fromEither[A](either: Either[Throwable, A]): F[A] = either match {
    case Right(value) => pure(value)
    case Left(error)  => raiseError(error)
  }

  // ===============================
  // CONCURRENCY OPERATIONS
  // ===============================

  /**
   * Fiber abstraction for concurrent operations.
   *
   * Provides a unified interface for managing concurrent computations across different effect
   * systems.
   */
  trait Fiber[FIBER[_], A] {

    /** Cancel the fiber */
    def cancel: FIBER[Unit]

    /** Wait for fiber completion */
    def join: FIBER[A]
  }

  /**
   * Start a computation in a separate fiber.
   *
   * @param fa
   *   The computation to run concurrently
   * @return
   *   Fiber representing the concurrent computation
   */
  def start[A](fa: F[A]): F[Fiber[F, A]]

  /**
   * Race two effects, returning the first to complete.
   *
   * @param fa
   *   First effect
   * @param fb
   *   Second effect
   * @return
   *   Either the result of fa (Left) or fb (Right)
   */
  def race[A, B](fa: F[A], fb: F[B]): F[Either[A, B]]

  /**
   * Race two effects, returning both the winner and loser fiber.
   *
   * @param fa
   *   First effect
   * @param fb
   *   Second effect
   * @return
   *   Either (winner result, loser fiber) for fa or fb
   */
  def racePair[A, B](fa: F[A], fb: F[B]): F[Either[(A, Fiber[F, B]), (Fiber[F, A], B)]]

  // ===============================
  // PARALLEL OPERATIONS
  // ===============================

  /**
   * Combine two effects in parallel.
   *
   * @param fa
   *   First effect
   * @param fb
   *   Second effect
   * @return
   *   Tuple of both results computed in parallel
   */
  def parProduct[A, B](fa: F[A], fb: F[B]): F[(A, B)]

  /**
   * Apply a function to each element in parallel.
   *
   * This is optimized for parallel processing of large datasets.
   *
   * @param list
   *   Input list
   * @param f
   *   Function to apply to each element
   * @return
   *   List of results computed in parallel
   */
  def parTraverse[A, B](list: List[A])(f: A => F[B]): F[List[B]]

  /**
   * Run a list of effects in parallel.
   *
   * @param list
   *   List of effects to run in parallel
   * @return
   *   List of results computed in parallel
   */
  def parSequence[A](list: List[F[A]]): F[List[A]] = parTraverse(list)(identity)

  // ===============================
  // RESOURCE MANAGEMENT
  // ===============================

  /**
   * Exit case for resource management.
   */
  sealed trait ExitCase[+E]
  object ExitCase {
    case object Completed         extends ExitCase[Nothing]
    case class Error[E](error: E) extends ExitCase[E]
    case object Canceled          extends ExitCase[Nothing]
  }

  /**
   * Bracket pattern for safe resource management.
   *
   * Ensures resources are properly acquired and released even in the presence of errors or
   * cancellation.
   *
   * @param acquire
   *   Resource acquisition
   * @param use
   *   Resource usage
   * @param release
   *   Resource release
   * @return
   *   Result of resource usage with guaranteed cleanup
   */
  def bracket[A, B](acquire: F[A])(use: A => F[B])(release: A => F[Unit]): F[B]

  /**
   * Bracket with exit case information.
   *
   * @param acquire
   *   Resource acquisition
   * @param use
   *   Resource usage
   * @param release
   *   Resource release with exit case
   * @return
   *   Result with guaranteed cleanup
   */
  def bracketCase[A, B](acquire: F[A])(use: A => F[B])(
    release: (A, ExitCase[Throwable]) => F[Unit]
  ): F[B]

  /**
   * Guarantee an action runs after the main effect.
   *
   * @param fa
   *   Main effect
   * @param finalizer
   *   Action to run after fa (success or failure)
   * @return
   *   Result of fa with guaranteed finalizer execution
   */
  def guarantee[A](fa: F[A])(finalizer: F[Unit]): F[A] =
    bracket(pure(()))(_ => fa)(_ => finalizer)

  // ===============================
  // TIMING OPERATIONS
  // ===============================

  /**
   * Sleep for a specified duration.
   *
   * @param duration
   *   How long to sleep
   * @return
   *   Effect that completes after the duration
   */
  def sleep(duration: FiniteDuration): F[Unit]

  /**
   * Timeout an effect after a specified duration.
   *
   * @param fa
   *   Effect to timeout
   * @param duration
   *   Maximum duration to wait
   * @return
   *   Effect that fails if fa takes longer than duration
   */
  def timeout[A](fa: F[A], duration: FiniteDuration): F[A]

  // ===============================
  // UTILITY OPERATIONS
  // ===============================

  /**
   * Traverse with sequential execution.
   *
   * @param list
   *   Input list
   * @param f
   *   Function to apply to each element
   * @return
   *   List of results computed sequentially
   */
  def traverse[A, B](list: List[A])(f: A => F[B]): F[List[B]] =
    list.foldRight(pure(List.empty[B])) { (a, acc) =>
      // Use the typeclass methods directly to avoid relying on extension syntax on F[_]
      flatMap(f(a)) { b =>
        map(acc) { bs =>
          b :: bs
        }
      }
    }

  /**
   * Sequence a list of effects.
   *
   * @param list
   *   List of effects to sequence
   * @return
   *   List of results computed sequentially
   */
  def sequence[A](list: List[F[A]]): F[List[A]] = traverse(list)(identity)

  /**
   * Conditional effect execution.
   *
   * @param condition
   *   Boolean condition
   * @param ifTrue
   *   Effect to run if condition is true
   * @param ifFalse
   *   Effect to run if condition is false
   * @return
   *   Result of the chosen effect
   */
  override def ifM[A](condition: F[Boolean])(ifTrue: => F[A], ifFalse: => F[A]): F[A] =
    flatMap(condition) { cond =>
      if (cond) ifTrue else ifFalse
    }

  /**
   * Repeat an effect until a condition is met.
   *
   * @param fa
   *   Effect to repeat
   * @param condition
   *   When to stop repeating
   * @return
   *   Result of the final effect execution
   */
  def repeatUntil[A](fa: F[A])(condition: A => Boolean): F[A] =
    flatMap(fa) { a =>
      if (condition(a)) pure(a)
      else repeatUntil(fa)(condition)
    }

  /**
   * Retry an effect with exponential backoff.
   *
   * @param fa
   *   Effect to retry
   * @param maxRetries
   *   Maximum number of retries
   * @param initialDelay
   *   Initial delay between retries
   * @param backoffFactor
   *   Multiplier for delay on each retry
   * @return
   *   Result of eventual success or final failure
   */
  def retryWithBackoff[A](
    fa: F[A],
    maxRetries: Int,
    initialDelay: FiniteDuration,
    backoffFactor: Double = 2.0
  ): F[A] = {
    def go(attempt: Int, delay: FiniteDuration): F[A] =
      handleErrorWith(fa) { error =>
        if (attempt >= maxRetries) raiseError(error)
        else
          flatMap(sleep(delay)) { _ =>
            go(
              attempt + 1,
              FiniteDuration((delay.toNanos * backoffFactor).toLong, delay.unit)
            )
          }
      }
    go(0, initialDelay)
  }

  // ===============================
  // CONVENIENCE OPERATIONS
  // ===============================

  /**
   * Void an effect (ignore its result).
   *
   * @param fa
   *   Effect to void
   * @return
   *   Unit effect
   */
  override def void[A](fa: F[A]): F[Unit] = map(fa)(_ => ())

  /**
   * Execute an effect for its side effects only.
   *
   * @param fa
   *   Effect to execute
   * @return
   *   Unit effect
   */
  def exec[A](fa: F[A]): F[Unit] = void(fa)

  /**
   * Measure the execution time of an effect.
   *
   * @param fa
   *   Effect to measure
   * @return
   *   Tuple of (result, execution duration)
   */
  def timed[A](fa: F[A]): F[(A, FiniteDuration)] =
    flatMap(delay(System.nanoTime())) { start =>
      flatMap(fa) { result =>
        map(delay(System.nanoTime())) { end =>
          (result, FiniteDuration(end - start, scala.concurrent.duration.NANOSECONDS))
        }
      }
    }
}

/**
 * Companion object with type class summoner and utility functions.
 */
object EffectSystem {

  /**
   * Type class summoner for EffectSystem.
   *
   * Enables syntax like: EffectSystem[IO].pure(42)
   */
  def apply[F[_]](implicit ev: EffectSystem[F]): EffectSystem[F] = ev

  /**
   * Syntax extensions for EffectSystem operations.
   */
  implicit class EffectSystemOps[F[_], A](private val fa: F[A]) extends AnyVal {

    def timeout(duration: FiniteDuration)(implicit F: EffectSystem[F]): F[A] =
      F.timeout(fa, duration)

    def guarantee(finalizer: F[Unit])(implicit F: EffectSystem[F]): F[A] =
      F.guarantee(fa)(finalizer)

    def timed(implicit F: EffectSystem[F]): F[(A, FiniteDuration)] =
      F.timed(fa)

    def retryWithBackoff(
      maxRetries: Int,
      initialDelay: FiniteDuration,
      backoffFactor: Double = 2.0
    )(implicit F: EffectSystem[F]): F[A] =
      F.retryWithBackoff(fa, maxRetries, initialDelay, backoffFactor)

    def start(implicit F: EffectSystem[F]): F[F.Fiber[F, A]] =
      F.start(fa)

    def race[B](fb: F[B])(implicit F: EffectSystem[F]): F[Either[A, B]] =
      F.race(fa, fb)
  }

  /**
   * Parallel syntax extensions.
   */
  implicit class ParallelOps[F[_]](private val F: EffectSystem[F]) extends AnyVal {

    def parTraverse[A, B](list: List[A])(f: A => F[B]): F[List[B]] =
      F.parTraverse(list)(f)

    def parSequence[A](list: List[F[A]]): F[List[A]] =
      F.parSequence(list)
  }
}
