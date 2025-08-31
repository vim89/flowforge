/**
 * FlowForge Core Module - Effect System Instances
 *
 * File: modules/core/src/main/scala/com/flowforge/core/instances/EffectInstances.scala Package:
 * com.flowforge.core.instances
 *
 * This file provides concrete implementations of the EffectSystem type class for popular effect
 * libraries in the Scala ecosystem. Currently supports:
 *   - Cats-Effect IO
 *   - ZIO Task
 *
 * These instances enable FlowForge to work seamlessly with either effect system, providing the user
 * with choice while maintaining a unified API.
 *
 * Design Patterns Applied:
 *   - Type Class Instance Pattern: Concrete implementations of abstract capabilities
 *   - Adapter Pattern: Adapting different effect libraries to unified interface
 *   - Bridge Pattern: Bridging FlowForge abstractions to concrete implementations
 *   - Strategy Pattern: Different effect execution strategies per library
 *
 * Scala Features Showcased:
 *   - Implicit Type Class Instances: Automatic capability resolution
 *   - Higher-Kinded Types: Generic programming over effect types
 *   - Interoperability: cats-interop-zio for seamless integration
 *   - Effect Polymorphism: Write once, run with any supported effect system
 *   - Stack Safety: tailRecM implementations for large data processing
 *   - Resource Safety: Bracket patterns for guaranteed cleanup
 *
 * Innovation Highlights:
 *   - Zero-cost abstractions over effect libraries
 *   - Optimal performance through native library features
 *   - Comprehensive concurrency support (Fibers, racing, parallelism)
 *   - Stack-safe recursion for big data processing
 *   - Resource management with automatic cleanup
 *   - Timeout and cancellation support
 *
 * Usage Examples:
 * ```scala
 * import com.flowforge.core.instances.EffectInstances._
 *
 * // Automatically works with Cats-Effect IO
 * def processWithCats[A](data: List[A]): IO[List[ProcessedData]] =
 *   EffectSystem[IO].parTraverse(data)(processItem)
 *
 * // Automatically works with ZIO Task
 * def processWithZio[A](data: List[A]): Task[List[ProcessedData]] =
 *   EffectSystem[Task].parTraverse(data)(processItem)
 *
 * // Same code works with both effect systems!
 * def genericProcess[F[_]: EffectSystem, A](data: List[A]): F[List[ProcessedData]] =
 *   EffectSystem[F].parTraverse(data)(processItem)
 * ```
 *
 * @author
 *   FlowForge Team
 * @version 1.0.0
 * @since 2024
 */
package com.flowforge.core.instances

import cats.effect.{ IO, Outcome }
import cats.syntax.all._
import com.flowforge.core.algebra.EffectSystem
// import zio.{ Task, ZIO }  // Commented out - ZIO dependencies are "provided"

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ ExecutionContext, Future }

/**
 * Effect system instances for popular Scala effect libraries.
 *
 * This object contains implicit instances that enable the EffectSystem type class to work with
 * concrete effect types like IO and Task.
 *
 * Import this object to automatically get instances in scope:
 * ```scala
 * import com.flowforge.core.instances.EffectInstances._
 * ```
 */
object EffectInstances {

  // ===============================
  // CATS-EFFECT IO IMPLEMENTATION
  // ===============================

  /**
   * EffectSystem instance for Cats-Effect IO.
   *
   * This implementation leverages Cats-Effect's comprehensive effect system to provide all
   * EffectSystem capabilities with optimal performance.
   *
   * Features:
   *   - Full integration with Cats ecosystem (Parallel, Resource, etc.)
   *   - Stack-safe tailRecM implementation
   *   - Native fiber support with cancellation
   *   - Efficient parallel operations
   *   - Resource-safe bracket operations
   *   - Timeout and timing operations
   */
  implicit val catsEffectSystemInstance: EffectSystem[IO] = new EffectSystem[IO] {

    // ===============================
    // MONAD ERROR OPERATIONS
    // ===============================

    // Functor implementation
    override def map[A, B](fa: IO[A])(f: A => B): IO[B] = fa.map(f)

    // Applicative implementation
    def pure[A](a: A): IO[A] = IO.pure(a)

    override def ap[A, B](ff: IO[A => B])(fa: IO[A]): IO[B] = ff.ap(fa)

    // Monad implementation with stack-safe tailRecM (CRITICAL for Cats compatibility)
    def flatMap[A, B](fa: IO[A])(f: A => IO[B]): IO[B] = fa.flatMap(f)

    /**
     * Stack-safe tail recursion implementation. This is essential for processing large datasets
     * without stack overflow.
     */
    // def tailRecM[A, B](a: A)(f: A => IO[Either[A, B]]): IO[B] = IO.tailRecM(a)(f)
    // Cats provides a default implementation, but we can optimize if needed

    // MonadError implementation
    def raiseError[A](error: Throwable): IO[A] = IO.raiseError(error)

    override def handleErrorWith[A](fa: IO[A])(f: Throwable => IO[A]): IO[A] =
      fa.handleErrorWith(f)

    // ===============================
    // SYNCHRONOUS OPERATIONS
    // ===============================

    def delay[A](thunk: => A): IO[A] = IO.delay(thunk)

    def suspend[A](fa: => IO[A]): IO[A] = IO.defer(fa)

    override def blocking[A](thunk: => A): IO[A] = IO.blocking(thunk)

    // ===============================
    // ASYNCHRONOUS OPERATIONS
    // ===============================

    def async[A](k: (Either[Throwable, A] => Unit) => Unit): IO[A] =
      IO.async[A] { callback =>
        IO.delay {
          k(callback)
          None // No finalizer needed for basic async
        }
      }

    override def fromFuture[A](future: => Future[A])(implicit ec: ExecutionContext): IO[A] =
      IO.fromFuture(IO.delay(future))

    // ===============================
    // CONCURRENCY OPERATIONS
    // ===============================

    /**
     * Cats-Effect fiber implementation. Wraps cats.effect.Fiber with our unified interface.
     */
    case class CatsEffectFiber[A](fiber: cats.effect.Fiber[IO, Throwable, A]) extends Fiber[IO, A] {
      def cancel: IO[Unit] = fiber.cancel

      def join: IO[A] = fiber.joinWithNever
    }

    def start[A](fa: IO[A]): IO[Fiber[IO, A]] =
      fa.start.map(fiber => CatsEffectFiber(fiber))

    def race[A, B](fa: IO[A], fb: IO[B]): IO[Either[A, B]] =
      IO.race(fa, fb)

    def racePair[A, B](fa: IO[A], fb: IO[B]): IO[Either[(A, Fiber[IO, B]), (Fiber[IO, A], B)]] =
      IO.racePair(fa, fb).flatMap {
        case Left((oa, fiberB)) =>
          oa match {
            case Outcome.Succeeded(ioa) => ioa.map(a => Left((a, CatsEffectFiber(fiberB))))
            case Outcome.Errored(e)     => IO.raiseError(e)
            case Outcome.Canceled() => IO.raiseError(new RuntimeException("Left fiber canceled"))
          }
        case Right((fiberA, ob)) =>
          ob match {
            case Outcome.Succeeded(iob) => iob.map(b => Right((CatsEffectFiber(fiberA), b)))
            case Outcome.Errored(e)     => IO.raiseError(e)
            case Outcome.Canceled() => IO.raiseError(new RuntimeException("Right fiber canceled"))
          }
      }

    // ===============================
    // PARALLEL OPERATIONS
    // ===============================

    def parProduct[A, B](fa: IO[A], fb: IO[B]): IO[(A, B)] =
      (fa, fb).parTupled

    def parTraverse[A, B](list: List[A])(f: A => IO[B]): IO[List[B]] =
      list.parTraverse(f)

    // ===============================
    // RESOURCE MANAGEMENT
    // ===============================

    def bracket[A, B](acquire: IO[A])(use: A => IO[B])(release: A => IO[Unit]): IO[B] =
      acquire.bracket(use)(release)

    def bracketCase[A, B](
      acquire: IO[A]
    )(use: A => IO[B])(release: (A, ExitCase[Throwable]) => IO[Unit]): IO[B] =
      acquire.bracketCase(use) { (a, outcome) =>
        val exitCase = outcome match {
          case Outcome.Succeeded(_) => ExitCase.Completed
          case Outcome.Errored(e)   => ExitCase.Error(e)
          case Outcome.Canceled()   => ExitCase.Canceled
        }
        release(a, exitCase)
      }

    // ===============================
    // TIMING OPERATIONS
    // ===============================

    def sleep(duration: FiniteDuration): IO[Unit] = IO.sleep(duration)

    def timeout[A](fa: IO[A], duration: FiniteDuration): IO[A] = fa.timeout(duration)

    // ===============================
    // OPTIMIZED IMPLEMENTATIONS
    // ===============================

    // Use Cats' optimized implementations when available
    override def traverse[A, B](list: List[A])(f: A => IO[B]): IO[List[B]] =
      list.traverse(f)

    override def sequence[A](list: List[IO[A]]): IO[List[A]] =
      list.sequence

    override def void[A](fa: IO[A]): IO[Unit] = fa.void

    def tailRecM[A, B](a: A)(f: A => IO[Either[A, B]]): IO[B] = {
      def loop(current: A): IO[B] =
        f(current).flatMap {
          case Left(nextA) => loop(nextA) // Continue recursion
          case Right(b)    => IO.pure(b)  // Terminate recursion
        }
      loop(a)
    }
  }

  // ===============================
  // ZIO TASK IMPLEMENTATION - COMMENTED OUT
  // ===============================
  // Note: ZIO implementation is commented out because ZIO dependencies are marked as "provided"
  // and not available at runtime by default. Uncomment when ZIO is explicitly added as dependency.

  /*
  /**
   * EffectSystem instance for ZIO Task.
   *
   * This implementation leverages ZIO's powerful effect system to provide all EffectSystem
   * capabilities with ZIO's unique features like structured concurrency and typed errors.
   *
   * Features:
   *   - Integration with ZIO's structured concurrency model
   *   - Efficient fiber management with interruption
   *   - Stack-safe tailRecM implementation
   *   - Resource-safe bracket operations with ZIO.bracket
   *   - High-performance parallel operations
   *   - Timeout and scheduling operations
   */
  implicit val zioEffectSystemInstance: EffectSystem[Task] = new EffectSystem[Task] {

    // ===============================
    // MONAD ERROR OPERATIONS
    // ===============================

    // Functor implementation
    override def map[A, B](fa: Task[A])(f: A => B): Task[B] = fa.map(f)

    // Applicative implementation
    def pure[A](a: A): Task[A] = ZIO.succeed(a)

    override def ap[A, B](ff: Task[A => B])(fa: Task[A]): Task[B] = ff.zipWith(fa)(_(_))

    // Monad implementation with stack-safe tailRecM
    def flatMap[A, B](fa: Task[A])(f: A => Task[B]): Task[B] = fa.flatMap(f)

    /**
     * Stack-safe tail recursion for ZIO. ZIO provides built-in stack safety, so we can implement
     * this efficiently.
     */
    def tailRecM[A, B](a: A)(f: A => Task[Either[A, B]]): Task[B] = {
      def loop(a: A): Task[B] =
        f(a).flatMap {
          case Left(nextA) => loop(nextA)
          case Right(b)    => ZIO.succeed(b)
        }
      loop(a)
    }

    // MonadError implementation
    def raiseError[A](error: Throwable): Task[A] = ZIO.fail(error)

    override def handleErrorWith[A](fa: Task[A])(f: Throwable => Task[A]): Task[A] =
      fa.catchAll(f)

    // ===============================
    // SYNCHRONOUS OPERATIONS
    // ===============================

    def delay[A](thunk: => A): Task[A] = ZIO.attempt(thunk)

    def suspend[A](fa: => Task[A]): Task[A] = ZIO.suspend(fa)

    override def blocking[A](thunk: => A): Task[A] = ZIO.attemptBlocking(thunk)

    // ===============================
    // ASYNCHRONOUS OPERATIONS
    // ===============================

    def async[A](k: (Either[Throwable, A] => Unit) => Unit): Task[A] =
      ZIO.async[Any, Throwable, A] { callback =>
        k { either =>
          callback(ZIO.fromEither(either))
        }
      }

    override def fromFuture[A](future: => Future[A])(implicit ec: ExecutionContext): Task[A] =
      ZIO.fromFuture(_ => future)

    // ===============================
    // CONCURRENCY OPERATIONS
    // ===============================

    /**
     * ZIO fiber implementation. Wraps zio.Fiber with our unified interface.
     */
    case class ZIOFiber[A](fiber: zio.Fiber[Throwable, A]) extends Fiber[Task, A] {
      def cancel: Task[Unit] = fiber.interrupt.unit

      def join: Task[A] = fiber.join
    }

    def start[A](fa: Task[A]): Task[Fiber[Task, A]] =
      fa.fork.map(fiber => ZIOFiber(fiber))

    def race[A, B](fa: Task[A], fb: Task[B]): Task[Either[A, B]] =
      fa.raceEither(fb)

    def racePair[A, B](
      fa: Task[A],
      fb: Task[B]
    ): Task[Either[(A, Fiber[Task, B]), (Fiber[Task, A], B)]] =
      fa.raceWith(fb)(
        { case (exit, fiber) =>
          exit.foldZIO(
            ZIO.fail(_),
            a => ZIO.succeed(Left((a, ZIOFiber(fiber))))
          )
        },
        { case (exit, fiber) =>
          exit.foldZIO(
            ZIO.fail(_),
            b => ZIO.succeed(Right((ZIOFiber(fiber), b)))
          )
        }
      )

    // ===============================
    // PARALLEL OPERATIONS
    // ===============================

    def parProduct[A, B](fa: Task[A], fb: Task[B]): Task[(A, B)] =
      fa.zip(fb)

    def parTraverse[A, B](list: List[A])(f: A => Task[B]): Task[List[B]] =
      ZIO.foreachPar(list)(f)

    // ===============================
    // RESOURCE MANAGEMENT
    // ===============================

    def bracket[A, B](acquire: Task[A])(use: A => Task[B])(release: A => Task[Unit]): Task[B] =
      ZIO.acquireReleaseWith(acquire) { a =>
        // release returns Task[Unit]; convert to a non-failing finalizer URIO by swallowing errors
        release(a).foldCauseZIO(_ => ZIO.unit, _ => ZIO.unit)
      }(use)

    def bracketCase[A, B](
      acquire: Task[A]
    )(use: A => Task[B])(release: (A, ExitCase[Throwable]) => Task[Unit]): Task[B] =
      ZIO.acquireReleaseExitWith(acquire) { (a: A, exit: zio.Exit[Throwable, B]) =>
        val exitCase = exit match {
          case zio.Exit.Success(_) => ExitCase.Completed
          case zio.Exit.Failure(cause) =>
            cause.failureOrCause match {
              case Left(error) => ExitCase.Error(error)
              case Right(_)    => ExitCase.Canceled
            }
        }
        // Log errors during release but don't fail the operation
        release(a, exitCase).foldCauseZIO(
          cause => ZIO.logWarning(s"Error during resource release: $cause").as(()),
          _ => ZIO.unit
        )
      }(use)

    // ===============================
    // TIMING OPERATIONS
    // ===============================

    def sleep(duration: FiniteDuration): Task[Unit] =
      ZIO.sleep(zio.Duration.fromScala(duration))

    def timeout[A](fa: Task[A], duration: FiniteDuration): Task[A] =
      fa.timeoutFail(
        new java.util.concurrent.TimeoutException(
          s"Operation timed out after $duration"
        )
      )(zio.Duration.fromScala(duration))

    // ===============================
    // OPTIMIZED IMPLEMENTATIONS
    // ===============================

    // Use ZIO's optimized implementations when available
    override def traverse[A, B](list: List[A])(f: A => Task[B]): Task[List[B]] =
      ZIO.foreach(list)(f)

    override def sequence[A](list: List[Task[A]]): Task[List[A]] =
      ZIO.collectAll(list)

    override def parSequence[A](list: List[Task[A]]): Task[List[A]] =
      ZIO.collectAllPar(list)

    override def void[A](fa: Task[A]): Task[Unit] = fa.unit

    // ZIO-specific optimizations
    override def retryWithBackoff[A](
      fa: Task[A],
      maxRetries: Int,
      initialDelay: FiniteDuration,
      backoffFactor: Double = 2.0
    ): Task[A] = {
      import zio.{ Duration, Schedule }

      val schedule = Schedule.exponential(Duration.fromScala(initialDelay)) &&
        Schedule.recurs(maxRetries)

      fa.retry(schedule)
    }

  }
  */

  // ===============================
  // ADDITIONAL INSTANCES
  // ===============================

  /**
   * You can add more effect system instances here as needed. For example: Monix Task,
   * cats.effect.IOApp, etc.
   */

  /**
   * Syntax extensions that become available when instances are in scope.
   */
  implicit class EffectSystemSyntax[F[_], A](private val fa: F[A]) extends AnyVal {

    /**
     * Convert any effect to a different effect type (when both have EffectSystem instances). Note:
     * This is a conceptual method. Real effect transformation requires runtime bridging.
     */
    def liftTo[G[_]](implicit F: EffectSystem[F], G: EffectSystem[G]): G[A] =
      // Placeholder for effect transformation - would need runtime interop
      // For production use, consider using cats-interop-zio or similar
      G.raiseError(
        new UnsupportedOperationException(
          "Effect transformation not implemented. Use specific interop libraries (e.g., zio-interop-cats)"
        )
      )
  }

  /**
   * Utilities for working with multiple effect systems.
   */
  object EffectUtils {

    /*
    /**
     * Run the same computation with different effect systems for comparison.
     * Note: Commented out due to ZIO being "provided" dependency
     */
    def raceEffectSystems[A](
      ioComputation: IO[A],
      zioComputation: Task[A]
    ): IO[Either[A, A]] =
      // This is a conceptual example - in practice you'd need more sophisticated
      // machinery to actually race different effect systems
      IO.race(
        ioComputation,
        IO.fromFuture(IO.delay(zio.Unsafe.unsafe { implicit unsafe =>
          zio.Runtime.default.unsafe.runToFuture(zioComputation)
        }))
      )
    */
  }
}
