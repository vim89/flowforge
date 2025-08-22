package com.flowforge.core

import cats.{ Applicative, Functor, Monad }

/**
 * Pipeline composition DSL for easy chaining
 */
object PipelineComposition {

  /**
   * Fluent API for pipeline composition
   */
  implicit class PipelineOps[F[_], A](fa: F[A]) {
    def ~>[B](f: A => F[B])(implicit F: Monad[F]): F[B] =
      fa.flatMap(f)

    def |>[B](f: A => B)(implicit F: Functor[F]): F[B] =
      fa.map(f)

    def @@[B](fb: F[B])(implicit F: Applicative[F]): F[(A, B)] =
      (fa, fb).mapN((_, _))
  }

  /**
   * Safe composition with automatic error handling
   */
  def safeCompose[F[_]: cats.effect.MonadThrow, A, B, C](
    f: A => F[B],
    g: B => F[C]
  ): A => F[Either[Throwable, C]] = { a =>
    cats.effect.MonadThrow[F].attempt(f(a).flatMap(g))
  }
}
