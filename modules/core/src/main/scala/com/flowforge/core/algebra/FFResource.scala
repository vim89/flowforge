package com.flowforge.core.algebra

/**
 * Minimal, effect-system-agnostic resource abstraction.
 *
 * Avoids a hard dependency on any effect-system in core public APIs while preserving
 * the common "acquire/use/release" pattern.
 */
trait FFResource[F[_], R] {
  def use[B](f: R => F[B]): F[B]
}

object FFResource {
  def make[F[_], R](acquire: F[R])(release: R => F[Unit])(implicit F: EffectSystem[F]): FFResource[F, R] =
    new FFResource[F, R] {
      def use[B](f: R => F[B]): F[B] = F.bracket(acquire)(f)(release)
    }

  def pure[F[_], R](value: R)(implicit F: EffectSystem[F]): FFResource[F, R] =
    new FFResource[F, R] {
      def use[B](f: R => F[B]): F[B] = f(value)
    }
}

