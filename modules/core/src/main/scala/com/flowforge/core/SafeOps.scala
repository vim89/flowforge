package com.flowforge.core

import cats.data.{ EitherT, Kleisli, NonEmptyList, OptionT, ValidatedNel }
import cats.{ Applicative, ApplicativeError, Functor, Monad, MonadError }
import cats.syntax.all._
import com.flowforge.types._
import scala.util.{ Failure, Success, Try }
import scala.concurrent.duration._

/**
 * Enhanced safe operations with complete monadic support
 *
 * Because nobody likes runtime explosions. This module gives you bulletproof operations that handle
 * errors gracefully and compose beautifully with the rest of your functional code.
 */
object SafeOperations {

  // ===== SAFE TRANSFORMATION OPERATIONS =====

  /**
   * Safe map operation that catches exceptions
   */
  def safeMap[F[_], A, B](fa: F[A])(f: A => B)(implicit
    F: MonadError[F, Throwable]
  ): F[B] =
    fa.flatMap(a => F.attempt(F.pure(f(a))).rethrow)

  /**
   * Safe flatMap that handles nested failures
   */
  def safeFlatMap[F[_], A, B](fa: F[A])(f: A => F[B])(implicit
    F: MonadError[F, Throwable]
  ): F[B] =
    fa.flatMap(a => F.handleErrorWith(f(a))(F.raiseError))

  /**
   * Safe parsing with automatic error conversion
   */
  def safeParse[F[_], A, B](value: A)(parser: A => B)(implicit
    F: ApplicativeError[F, FlowForgeError]
  ): F[B] =
    Try(parser(value)) match {
      case Success(result) => F.pure(result)
      case Failure(ex) =>
        F.raiseError(
          ConfigError("parsing", "parse_error", ex)
        )
    }

  /**
   * Safe resource operations with automatic cleanup
   */
  def safeResource[F[_], R, A](
    acquire: F[R]
  )(
    use: R => F[A]
  )(
    release: R => F[Unit]
  )(implicit F: MonadError[F, Throwable]): F[A] =
    acquire.flatMap { resource =>
      F.handleErrorWith(use(resource)) { error =>
        release(resource).flatMap(_ => F.raiseError(error))
      }.flatTap(_ => release(resource))
    }

  // ===== ENHANCED EITHER OPERATIONS =====

  /**
   * Enhanced EitherT operations for nested error handling
   */
  implicit class SafeEitherOps[F[_], E, A](eT: EitherT[F, E, A]) {

    /**
     * Safely transforms the right value
     */
    def safeMap[B](f: A => B)(implicit F: MonadError[F, Throwable]): EitherT[F, E, B] =
      eT.subflatMap(a => Try(f(a)).toEither.leftMap(_.asInstanceOf[E]))

    /**
     * Safely flatMaps with error recovery
     */
    def safeFlatMap[B](f: A => EitherT[F, E, B])(implicit
      F: MonadError[F, Throwable]
    ): EitherT[F, E, B] =
      eT.flatMap(a =>
        EitherT(F.handleErrorWith(f(a).value)(err => F.pure(err.asInstanceOf[E].asLeft[B])))
      )

    /**
     * Recovers from specific error types
     */
    def recoverWith(pf: PartialFunction[E, A]): EitherT[F, E, A] =
      eT.leftFlatMap(e =>
        if (pf.isDefinedAt(e)) EitherT.rightT[F, E](pf(e))
        else EitherT.leftT[F, A](e)
      )

    /**
     * Timeout support for long-running operations
     */
    def withTimeout(duration: FiniteDuration)(implicit
      F: cats.effect.Temporal[F]
    ): EitherT[F, E, A] =
      EitherT(
        F.timeout(eT.value, duration).handleErrorWith(err => F.pure(err.asInstanceOf[E].asLeft[A]))
      )
  }

  // ===== ENHANCED OPTION OPERATIONS =====

  /**
   * Enhanced OptionT operations
   */
  implicit class SafeOptionOps[F[_], A](oT: OptionT[F, A]) {

    /**
     * Safe getOrElse with effect support
     */
    def safeGetOrElse(default: => F[A])(implicit F: Monad[F]): F[A] =
      oT.getOrElseF(default)

    /**
     * Converts None to a specific error
     */
    def toEitherT[E](error: => E)(implicit F: Functor[F]): EitherT[F, E, A] =
      EitherT(oT.toRight(error).value)

    /**
     * Safe filter with fallback
     */
    def safeFilter(predicate: A => Boolean, fallback: => F[A])(implicit
      F: Monad[F]
    ): F[A] =
      oT.filter(predicate).getOrElseF(fallback)
  }

  // ===== VALIDATION OPERATIONS =====

  /**
   * Enhanced Validated operations
   */
  implicit class SafeValidatedOps[E, A](validated: ValidatedNel[E, A]) {

    /**
     * Safely transforms valid values
     */
    def safeMap[B](f: A => B): ValidatedNel[E, B] =
      validated.map(f) // Validated is already safe

    /**
     * Combines multiple validations safely
     */
    def combineWith[B, C](other: ValidatedNel[E, B])(
      f: (A, B) => C
    ): ValidatedNel[E, C] =
      (validated, other).mapN(f)

    /**
     * Converts to EitherT for monadic composition
     */
    def toEitherT[F[_]: Applicative]: EitherT[F, NonEmptyList[E], A] =
      EitherT.fromEither[F](validated.toEither)

    /**
     * Accumulates errors across multiple validations
     */
    def zipWithValidation[B, C](other: ValidatedNel[E, B])(
      f: (A, B) => ValidatedNel[E, C]
    ): ValidatedNel[E, C] =
      (validated, other).tupled.andThen(f.tupled)
  }

  // ===== RETRY OPERATIONS =====

  /**
   * Safe retry mechanism with exponential backoff
   */
  def safeRetry[F[_], A](
    fa: F[A],
    maxRetries: Int = 3,
    initialDelay: FiniteDuration = 1.second,
    backoffFactor: Double = 2.0
  )(implicit F: cats.effect.Temporal[F]): F[A] = {

    def loop(attempt: Int, delay: FiniteDuration): F[A] =
      F.handleErrorWith(fa) { error =>
        if (attempt >= maxRetries) F.raiseError(error)
        else F.sleep(delay) *> loop(attempt + 1, delay * backoffFactor)
      }

    loop(0, initialDelay)
  }

  /**
   * Conditional retry based on error type
   */
  def retryWhen[F[_], A](
    fa: F[A],
    shouldRetry: Throwable => Boolean,
    maxRetries: Int = 3
  )(implicit F: MonadError[F, Throwable]): F[A] = {

    def loop(attempt: Int): F[A] =
      F.handleErrorWith(fa) { error =>
        if (attempt >= maxRetries || !shouldRetry(error)) {
          F.raiseError(error)
        } else {
          loop(attempt + 1)
        }
      }

    loop(0)
  }

  // ===== CIRCUIT BREAKER PATTERN =====

  /**
   * Simple circuit breaker for fault tolerance
   */
  sealed trait CircuitState
  case object Closed   extends CircuitState
  case object Open     extends CircuitState
  case object HalfOpen extends CircuitState

  class CircuitBreaker[F[_]](
    failureThreshold: Int,
    timeout: FiniteDuration,
    state: cats.effect.Ref[F, (CircuitState, Int, Option[Long])]
  )(implicit F: cats.effect.Temporal[F]) {

    def protect[A](fa: F[A]): F[A] =
      state.get.flatMap {
        case (Open, _, Some(openTime)) =>
          val now = System.currentTimeMillis()
          if (now - openTime > timeout.toMillis) {
            // Transition to half-open
            state.set((HalfOpen, 0, None)) *> attempt(fa)
          } else {
            F.raiseError(new RuntimeException("Circuit breaker is open"))
          }

        case (HalfOpen, _, _) => attempt(fa)
        case (Closed, _, _)   => attempt(fa)
        case _                => F.raiseError(new RuntimeException("Circuit breaker is open"))
      }

    private def attempt[A](fa: F[A]): F[A] =
      F.handleErrorWith(fa) { error =>
        state.modify {
          case (Closed, failures, _) =>
            val newFailures = failures + 1
            if (newFailures >= failureThreshold) {
              val newState = (Open, 0, Some(System.currentTimeMillis()))
              (newState, F.raiseError[A](error))
            } else {
              val newState = (Closed, newFailures, None)
              (newState, F.raiseError[A](error))
            }

          case (HalfOpen, _, _) =>
            val newState = (Open, 0, Some(System.currentTimeMillis()))
            (newState, F.raiseError[A](error))

          case state => (state, F.raiseError[A](error))
        }.flatten
      }.flatTap(_ =>
        // Success - reset failure count
        state.update { case (_, _, openTime) =>
          (Closed, 0, None)
        }
      )
  }

  // ===== RATE LIMITING =====

  /**
   * Simple rate limiter using sliding window
   */
  class RateLimiter[F[_]](
    maxRequests: Int,
    windowSize: FiniteDuration,
    requests: cats.effect.Ref[F, List[Long]]
  )(implicit F: cats.effect.Temporal[F]) {

    def throttle[A](fa: F[A]): F[A] = {
      val now         = System.currentTimeMillis()
      val windowStart = now - windowSize.toMillis

      requests.modify { current =>
        val recentRequests = current.filter(_ > windowStart)
        if (recentRequests.length >= maxRequests) {
          (current, F.raiseError[A](new RuntimeException("Rate limit exceeded")))
        } else {
          val updated = now :: recentRequests
          (updated, fa)
        }
      }.flatten
    }
  }
}

/**
 * Safe operations syntax for nicer composition
 */
object SafeOperationsSyntax {

  implicit class SafeOps[F[_], A](fa: F[A]) {

    def safely[B](f: A => B)(implicit F: MonadError[F, Throwable]): F[B] =
      SafeOperations.safeMap(fa)(f)

    def safelyFlatMap[B](f: A => F[B])(implicit F: MonadError[F, Throwable]): F[B] =
      SafeOperations.safeFlatMap(fa)(f)

    def withRetries(maxRetries: Int = 3)(implicit
      F: cats.effect.Temporal[F]
    ): F[A] =
      SafeOperations.safeRetry(fa, maxRetries)

    def when(condition: Boolean)(implicit F: Applicative[F]): F[Option[A]] =
      if (condition) fa.map(Some(_)) else F.pure(None)

    def unless(condition: Boolean)(implicit F: Applicative[F]): F[Option[A]] =
      when(!condition)
  }
}
