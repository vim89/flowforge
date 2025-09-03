/**
 * FlowForge Core Module - Effect System Syntax Extensions
 *
 * File: modules/core/src/main/scala/com/flowforge/core/syntax/effect.scala Package: com.flowforge.core.syntax
 *
 * This file provides convenient syntax extensions for effect operations in FlowForge. It enables fluent,
 * readable APIs for common effect patterns like error handling, resource management, timing, and parallel
 * processing.
 *
 * Design Patterns Applied:
 *   - Extension Methods Pattern: Adding methods to existing types via implicits
 *   - Fluent Interface Pattern: Chainable method calls for readable code
 *   - Decorator Pattern: Adding functionality without modifying original classes
 *   - Pimp My Library Pattern: Extending third-party libraries with custom operations
 *
 * Scala Features Showcased:
 *   - Implicit Classes: Zero-cost syntax extensions via value classes
 *   - Value Classes: Performance optimization for wrapper types
 *   - Type Classes: Polymorphic operations via implicit parameters
 *   - Higher-Kinded Types: Generic programming over effect types
 *   - Method Chaining: Fluent APIs with immutable data structures
 *   - Type Inference: Seamless integration with Scala's type system
 *   - Implicit Conversions: Automatic syntax availability
 *
 * Innovation Highlights:
 *   - Zero-runtime-cost syntax extensions using value classes
 *   - Functional programming patterns with imperative-style syntax
 *   - Cross-effect-system compatibility (works with IO, Task, etc.)
 *   - Performance-optimized implementations using native library features
 *   - Type-safe operations with compile-time guarantees
 *   - Integration with for-comprehensions and monadic composition
 *
 * Usage Examples:
 * ```scala
 * import com.flowforge.core.syntax.effect._
 *
 * // Fluent error handling
 * val result = dataProcessing
 *   .timeoutAfter(30.seconds)
 *   .retryOnFailure(3, 1.second)
 *   .recoverWith(fallbackProcessing)
 *   .guaranteeCleanup(closeResources)
 *
 * // Parallel operations
 * val processed = data.parTraverseEffect(processItem)
 *
 * // Resource management
 * val pipeline = for {
 *   conn   <- acquireConnection.bracket(use, release)
 *   result <- processWithConnection(conn)
 * } yield result
 *
 * // Timing and measurement
 * val (result, duration) = computation.timed
 * ```
 *
 * @author
 *   FlowForge Team
 * @version 1.0.0
 * @since 2024
 */
package com.flowforge.core.syntax

import cats.data.{ NonEmptyList, ValidatedNel }
import cats.syntax.all._
import com.flowforge.core.algebra.EffectSystem
import com.flowforge.core.types.{ ConfigError, FlowForgeError }

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

/**
 * Syntax extensions for effect operations.
 *
 * This object provides implicit classes that add convenient methods to effect types. Import this to get
 * fluent syntax for common operations.
 *
 * Usage:
 * ```scala
 * import com.flowforge.core.syntax.effect._
 * ```
 */
object effect {

  // ===============================
  // CORE EFFECT OPERATIONS
  // ===============================

  /**
   * Core effect syntax extensions. Provides fundamental operations like timeout, retry, and recovery.
   */
  implicit class EffectOps[F[_], A](private val fa: F[A]) extends AnyVal {

    /**
     * Add a timeout to an effect.
     *
     * @param duration
     *   Maximum time to wait
     * @param F
     *   Effect system instance
     * @return
     *   Effect that fails if timeout is exceeded
     */
    def timeoutAfter(duration: FiniteDuration)(implicit F: EffectSystem[F]): F[A] =
      F.timeout(fa, duration)

    /**
     * Retry an effect on failure with exponential backoff.
     *
     * @param maxRetries
     *   Maximum number of retry attempts
     * @param initialDelay
     *   Initial delay between retries
     * @param backoffFactor
     *   Multiplier for delay on each retry
     * @param F
     *   Effect system instance
     * @return
     *   Effect that retries on failure
     */
    def retryOnFailure(
      maxRetries: Int,
      initialDelay: FiniteDuration,
      backoffFactor: Double = 2.0,
    )(implicit F: EffectSystem[F],
    ): F[A] =
      F.retryWithBackoff(fa, maxRetries, initialDelay, backoffFactor)

    /**
     * Recover from errors with a fallback effect.
     *
     * @param fallback
     *   Effect to run if fa fails
     * @param F
     *   Effect system instance
     * @return
     *   Effect that uses fallback on failure
     */
    def recoverWith(fallback: => F[A])(implicit F: EffectSystem[F]): F[A] =
      F.handleErrorWith(fa)(_ => fallback)

    /**
     * Recover from specific error types.
     *
     * @param pf
     *   Partial function to handle specific errors
     * @param F
     *   Effect system instance
     * @return
     *   Effect with selective error recovery
     */
    def recoverFrom(pf: PartialFunction[Throwable, F[A]])(implicit F: EffectSystem[F]): F[A] =
      F.handleErrorWith(fa) { error =>
        pf.applyOrElse(error, (e: Throwable) => F.raiseError[A](e))
      }

    /**
     * Guarantee an action runs after the effect (success or failure).
     *
     * @param finalizer
     *   Action to run after fa completes
     * @param F
     *   Effect system instance
     * @return
     *   Effect with guaranteed cleanup
     */
    def guaranteeCleanup(finalizer: F[Unit])(implicit F: EffectSystem[F]): F[A] =
      F.guarantee(fa)(finalizer)

    /**
     * Convert effect result to Either for explicit error handling.
     *
     * @param F
     *   Effect system instance
     * @return
     *   Effect that always succeeds with Either result
     */
    def attempt(implicit F: EffectSystem[F]): F[Either[Throwable, A]] =
      F.attempt(fa)

    /**
     * Measure the execution time of an effect.
     *
     * @param F
     *   Effect system instance
     * @return
     *   Tuple of (result, execution duration)
     */
    def timed(implicit F: EffectSystem[F]): F[(A, FiniteDuration)] =
      F.timed(fa)

    /**
     * Execute effect for side effects only, ignoring result.
     *
     * @param F
     *   Effect system instance
     * @return
     *   Unit effect
     */
    def exec(implicit F: EffectSystem[F]): F[Unit] =
      F.void(fa)

    /**
     * Log the result of an effect for debugging.
     *
     * @param message
     *   Message to log with result
     * @param F
     *   Effect system instance
     * @return
     *   Original effect with logging side effect
     */
    def logResult(message: String = "Result")(implicit F: EffectSystem[F]): F[A] =
      fa.map { result =>
        println(s"$message: $result") // In practice, use proper logging
        result
      }

    /**
     * Log errors from an effect for debugging.
     *
     * @param message
     *   Message to log with error
     * @param F
     *   Effect system instance
     * @return
     *   Original effect with error logging
     */
    def logErrors(message: String = "Error")(implicit F: EffectSystem[F]): F[A] =
      F.handleErrorWith(fa) { error =>
        println(s"$message: ${error.getMessage}") // In practice, use proper logging
        F.raiseError(error)
      }
  }

  // ===============================
  // PARALLEL OPERATIONS
  // ===============================

  /**
   * Parallel operations syntax for collections.
   */
  implicit class ParallelCollectionOps[A](private val list: List[A]) extends AnyVal {

    /**
     * Apply an effectful function to each element in parallel.
     *
     * @param f
     *   Function to apply to each element
     * @param F
     *   Effect system instance
     * @return
     *   List of results computed in parallel
     */
    def parTraverseEffect[F[_], B](f: A => F[B])(implicit F: EffectSystem[F]): F[List[B]] =
      F.parTraverse(list)(f)

    /**
     * Apply an effectful function to each element sequentially.
     *
     * @param f
     *   Function to apply to each element
     * @param F
     *   Effect system instance
     * @return
     *   List of results computed sequentially
     */
    def traverseEffect[F[_], B](f: A => F[B])(implicit F: EffectSystem[F]): F[List[B]] =
      F.traverse(list)(f)

    /**
     * Filter elements using an effectful predicate.
     *
     * @param predicate
     *   Effectful predicate function
     * @param F
     *   Effect system instance
     * @return
     *   Filtered list
     */
    def filterEffect[F[_]](predicate: A => F[Boolean])(implicit F: EffectSystem[F]): F[List[A]] =
      F.traverse(list) { a =>
        predicate(a).map(if (_) Some(a) else None)
      }.map(_.flatten)
  }

  /**
   * Parallel operations syntax for effect collections.
   */
  implicit class ParallelEffectCollectionOps[F[_], A](private val effects: List[F[A]]) extends AnyVal {

    /**
     * Run all effects in parallel.
     *
     * @param F
     *   Effect system instance
     * @return
     *   List of results computed in parallel
     */
    def parSequenceEffect(implicit F: EffectSystem[F]): F[List[A]] =
      F.parSequence(effects)

    /**
     * Run all effects sequentially.
     *
     * @param F
     *   Effect system instance
     * @return
     *   List of results computed sequentially
     */
    def sequenceEffect(implicit F: EffectSystem[F]): F[List[A]] =
      F.sequence(effects)
  }

  // ===============================
  // RESOURCE MANAGEMENT
  // ===============================

  /**
   * Resource management syntax extensions.
   */
  implicit class ResourceOps[F[_], R](private val acquire: F[R]) extends AnyVal {

    /**
     * Use bracket pattern for resource management.
     *
     * @param use
     *   Function to use the resource
     * @param release
     *   Function to release the resource
     * @param F
     *   Effect system instance
     * @return
     *   Result of resource usage with guaranteed cleanup
     */
    def bracket[A](use: R => F[A])(release: R => F[Unit])(implicit F: EffectSystem[F]): F[A] =
      F.bracket(acquire)(use)(release)

    /**
     * Use bracket with exit case information.
     *
     * @param use
     *   Function to use the resource
     * @param release
     *   Function to release the resource with exit case
     * @param F
     *   Effect system instance
     * @return
     *   Result with guaranteed cleanup
     */
    // def bracketCase[A](use: R => F[A])(release: (R, F.ExitCase[Throwable]) => F[Unit])(implicit F: EffectSystem[F]): F[A] =
    // F.bracketCase(acquire)(use)(release)
    /**
     * Automatically release resource after use.
     *
     * @param use
     *   Function to use the resource
     * @param F
     *   Effect system instance
     * @return
     *   Result with automatic cleanup (if resource has close method)
     */
    def using[A](
      use: R => F[A],
    )(implicit
      F: EffectSystem[F],
      closeable: R <:< AutoCloseable,
    ): F[A] =
      F.bracket(acquire)(use)(resource => F.delay(closeable(resource).close()))
  }

  // ===============================
  // CONCURRENCY OPERATIONS
  // ===============================

  /**
   * Concurrency syntax extensions.
   */
  implicit class ConcurrencyOps[F[_], A](private val fa: F[A]) extends AnyVal {

    /**
     * Start effect in a separate fiber.
     *
     * @param F
     *   Effect system instance
     * @return
     *   Fiber representing the concurrent computation
     */
    def startFiber(implicit F: EffectSystem[F]): F[F.Fiber[F, A]] =
      F.start(fa)

    /**
     * Race this effect against another.
     *
     * @param other
     *   Effect to race against
     * @param F
     *   Effect system instance
     * @return
     *   Either the result of fa (Left) or other (Right)
     */
    def raceWith[B](other: F[B])(implicit F: EffectSystem[F]): F[Either[A, B]] =
      F.race(fa, other)

    /**
     * Run this effect in parallel with another, combining results.
     *
     * @param other
     *   Effect to run in parallel
     * @param F
     *   Effect system instance
     * @return
     *   Tuple of both results
     */
    def parWith[B](other: F[B])(implicit F: EffectSystem[F]): F[(A, B)] =
      F.parProduct(fa, other)

    /**
     * Run this effect in parallel with others, combining with a function.
     *
     * @param fb
     *   Second effect
     * @param fc
     *   Third effect
     * @param f
     *   Function to combine results
     * @param F
     *   Effect system instance
     * @return
     *   Combined result
     */
    def parMapN[B, C, D](
      fb: F[B],
      fc: F[C],
    )(
      f: (A, B, C) => D,
    )(implicit F: EffectSystem[F],
    ): F[D] = {
      val combined = for {
        ab <- F.parProduct(fa, fb)
        c  <- fc
      } yield f(ab._1, ab._2, c)
      combined
    }
  }

  // ===============================
  // VALIDATION & ERROR HANDLING
  // ===============================

  /**
   * Validation syntax extensions for working with ValidatedNel.
   */
  implicit class ValidationOps[E, A](private val validated: ValidatedNel[E, A]) extends AnyVal {

    /**
     * Convert ValidatedNel to an effect.
     *
     * @param F
     *   Effect system instance
     * @return
     *   Effect that succeeds with valid value or fails with errors
     */
    def liftToEffect[F[_]](implicit F: EffectSystem[F], ev: E <:< Throwable): F[A] =
      validated.fold(
        errors => F.raiseError(errors.head), // Use first error for effect
        success => F.pure(success),
      )

    /**
     * Convert ValidatedNel to effect with custom error handling.
     *
     * @param handleErrors
     *   Function to convert errors to throwable
     * @param F
     *   Effect system instance
     * @return
     *   Effect with converted errors
     */
    def liftToEffectWith[F[_]](
      handleErrors: NonEmptyList[E] => Throwable,
    )(implicit
      F: EffectSystem[F],
    ): F[A] =
      validated.fold(
        errors => F.raiseError(handleErrors(errors)),
        success => F.pure(success),
      )
  }

  /**
   * Config validation syntax extensions.
   */
  implicit class ConfigValidationOps(private val errors: NonEmptyList[ConfigError]) extends AnyVal {

    /**
     * Convert config errors to a FlowForge error.
     *
     * @return
     *   FlowForge error representing config validation failure
     */
    def toFlowForgeError: FlowForgeError =
      FlowForgeError.CompositeError(
        errors = errors.map(err => FlowForgeError.fromThrowable(new IllegalArgumentException(err.message))),
        message = s"Configuration validation failed with ${errors.size} errors",
      )
  }

  // ===============================
  // CONVERSION OPERATIONS
  // ===============================

  /**
   * Conversion syntax for different effect types.
   */
  implicit class ConversionOps[A](private val value: A) extends AnyVal {

    /**
     * Lift a pure value into any effect.
     *
     * @param F
     *   Effect system instance
     * @return
     *   Effect containing the value
     */
    def liftEffect[F[_]](implicit F: EffectSystem[F]): F[A] =
      F.pure(value)

    /**
     * Lift a value into an effect with error handling for nulls.
     *
     * @param error
     *   Error to raise if value is null
     * @param F
     *   Effect system instance
     * @return
     *   Effect that fails if value is null
     */
    def liftNonNull[F[_]](error: => Throwable)(implicit F: EffectSystem[F]): F[A] =
      if (value == null) F.raiseError(error) else F.pure(value)
  }

  /**
   * Try conversion syntax.
   */
  implicit class TryOps[A](private val tried: Try[A]) extends AnyVal {

    /**
     * Convert Try to any effect type.
     *
     * @param F
     *   Effect system instance
     * @return
     *   Effect representing the Try result
     */
    def liftToEffect[F[_]](implicit F: EffectSystem[F]): F[A] =
      F.fromTry(tried)
  }

  /**
   * Future conversion syntax.
   */
  implicit class FutureOps[A](private val future: Future[A]) extends AnyVal {

    /**
     * Convert Future to any effect type.
     *
     * @param F
     *   Effect system instance
     * @param ec
     *   Execution context for Future operations
     * @return
     *   Effect representing the Future result
     */
    def liftToEffect[F[_]](implicit F: EffectSystem[F], ec: ExecutionContext): F[A] =
      F.fromFuture(future)
  }

  /**
   * Either conversion syntax.
   */
  implicit class EitherOps[E <: Throwable, A](private val either: Either[E, A]) extends AnyVal {

    /**
     * Convert Either to any effect type.
     *
     * @param F
     *   Effect system instance
     * @return
     *   Effect representing the Either result
     */
    def liftToEffect[F[_]](implicit F: EffectSystem[F]): F[A] =
      F.fromEither(either.left.map(identity))
  }

  // ===============================
  // UTILITY OPERATIONS
  // ===============================

  /**
   * Utility syntax for common patterns.
   */
  implicit class UtilityOps[F[_], A](private val fa: F[A]) extends AnyVal {

    /**
     * Conditionally execute an effect.
     *
     * @param condition
     *   When to execute the effect
     * @param F
     *   Effect system instance
     * @return
     *   Effect result if condition is true, None otherwise
     */
    def whenM(condition: F[Boolean])(implicit F: EffectSystem[F]): F[Option[A]] =
      F.ifM(condition)(fa.map(Some(_)), F.pure(None))

    /**
     * Execute effect only if condition is true.
     *
     * @param condition
     *   Simple boolean condition
     * @param F
     *   Effect system instance
     * @return
     *   Effect result if condition is true, None otherwise
     */
    def when(condition: Boolean)(implicit F: EffectSystem[F]): F[Option[A]] =
      if (condition) fa.map(Some(_)) else F.pure(None)

    /**
     * Execute effect unless condition is true.
     *
     * @param condition
     *   When NOT to execute the effect
     * @param F
     *   Effect system instance
     * @return
     *   Effect result if condition is false, None otherwise
     */
    def unless(condition: Boolean)(implicit F: EffectSystem[F]): F[Option[A]] =
      when(!condition)

    /**
     * Tap into effect result for debugging without changing the result.
     *
     * @param f
     *   Function to apply to the result for side effects
     * @param F
     *   Effect system instance
     * @return
     *   Original effect with side effects
     */
    def tap(f: A => F[Unit])(implicit F: EffectSystem[F]): F[A] =
      fa.flatMap(a => f(a).map(_ => a))

    /**
     * Apply a function if result matches a condition.
     *
     * @param predicate
     *   Condition to check
     * @param f
     *   Function to apply if condition matches
     * @param F
     *   Effect system instance
     * @return
     *   Modified or original result
     */
    def mapWhen(predicate: A => Boolean)(f: A => A)(implicit F: EffectSystem[F]): F[A] =
      fa.map(a => if (predicate(a)) f(a) else a)

    /**
     * Transform errors while preserving success values.
     *
     * @param f
     *   Function to transform errors
     * @param F
     *   Effect system instance
     * @return
     *   Effect with transformed errors
     */
    def mapError(f: Throwable => Throwable)(implicit F: EffectSystem[F]): F[A] =
      F.handleErrorWith(fa)(error => F.raiseError(f(error)))
  }
}
