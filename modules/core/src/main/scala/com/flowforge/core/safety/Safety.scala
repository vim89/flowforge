package com.flowforge.core.safety

import cats.data.Validated
import cats.data.ValidatedNel
import cats.syntax.all._
import com.flowforge.core.algebra.EffectSystem
import com.flowforge.core.logging.CoreLogger
import com.flowforge.core.types.FlowForgeError

import scala.annotation.tailrec
import scala.util.Try

/**
 * Universal safety helpers for pure and effectful code paths.
 *
 *   - Pure helpers avoid try/catch and return typed error channels.
 *   - Effectful helpers use EffectSystem[F] so callers stay engine-agnostic.
 */
object Safety {

  type Result[+A]          = Either[FlowForgeError, A]
  type ValidatedResult[+A] = ValidatedNel[FlowForgeError, A]

  // ==================
  // Pure helpers
  // ==================
  def safely[A](thunk: => A)(implicit em: ErrorMapper): Result[A] =
    Either.catchNonFatal(thunk).leftMap(em(_))

  def safelyV[A](thunk: => A)(implicit em: ErrorMapper): ValidatedResult[A] =
    Validated.fromEither(safely(thunk)).toValidatedNel

  def fromTry[A](t: Try[A])(implicit em: ErrorMapper): Result[A] =
    t.toEither.leftMap(em(_))

  def fromOption[A](oa: Option[A], ifEmpty: => FlowForgeError): Result[A] =
    oa.toRight(ifEmpty)

  def sequenceV[A](xs: List[Result[A]]): ValidatedResult[List[A]] =
    xs.traverse(_.toValidatedNel)

  // ==================
  // Effectful helpers
  // ==================
  def in[F[_]](implicit F: EffectSystem[F]): In[F] = new In[F] {
    def attempt[A](fa: F[A])(implicit em: ErrorMapper): F[Result[A]] =
      F.handleErrorWith(F.map(fa)(Right(_): Result[A]))(t => F.pure(Left(em(t))))

    def attemptV[A](fa: F[A])(implicit em: ErrorMapper): F[ValidatedResult[A]] =
      F.map(attempt(fa))(_.toValidatedNel)

    def orFail[A](fr: F[Result[A]]): F[A] =
      F.flatMap(fr)(_.fold(F.raiseError, F.pure))

    def guarantee[A](fa: F[A])(finalizer: F[Unit]): F[A] =
      F.guarantee(fa)(finalizer)

    def bracket[A, B](acquire: F[A])(use: A => F[B])(release: A => F[Unit]): F[B] =
      F.bracket(acquire)(use)(release)

    def logLeft[A](fr: F[Result[A]])(implicit L: CoreLogger[F]): F[Result[A]] =
      F.flatMap(fr) {
        case l @ Left(err) => F.flatMap(L.error(err.message))(_ => F.pure(l))
        case r @ Right(_)  => F.pure(r)
      }
  }

  trait In[F[_]] {
    def attempt[A](fa: F[A])(implicit em: ErrorMapper): F[Result[A]]
    def attemptV[A](fa: F[A])(implicit em: ErrorMapper): F[ValidatedResult[A]]
    def orFail[A](fr: F[Result[A]]): F[A]
    def guarantee[A](fa: F[A])(finalizer: F[Unit]): F[A]
    def bracket[A, B](acquire: F[A])(use: A => F[B])(release: A => F[Unit]): F[B]
    def logLeft[A](fr: F[Result[A]])(implicit L: CoreLogger[F]): F[Result[A]]
  }

  // ==================
  // Syntax
  // ==================
  implicit final class ResultOps[A](private val r: Result[A]) extends AnyVal {
    def mapError(f: FlowForgeError => FlowForgeError): Result[A] = r.leftMap(f)
    @tailrec
    def toValidatedNel: ValidatedResult[A] = r.toValidatedNel
  }

  implicit final class ValidatedResultOps[A](private val v: ValidatedResult[A]) extends AnyVal {
    def toEither: Result[A] = v.toEither.leftMap(errs => FlowForgeError.CompositeError(errs))
  }
}
// scalafix:off noScalaUtilTry
