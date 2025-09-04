package com.flowforge.core.logging

import com.flowforge.core.algebra.EffectSystem

/**
 * Minimal effect-polymorphic logger contract kept in core to avoid
 * module coupling. Infrastructure can provide rich implementations
 * (e.g., StructuredLogger) and adapters to this trait.
 */
trait CoreLogger[F[_]] {
  def info(msg: String): F[Unit]
  def warn(msg: String): F[Unit]
  def error(msg: String): F[Unit]
}

object CoreLogger {
  /** Default no-op logger to avoid forcing dependencies at call sites. */
  implicit def noOp[F[_]](implicit F: EffectSystem[F]): CoreLogger[F] = new CoreLogger[F] {
    def info(msg: String): F[Unit]  = F.pure(())
    def warn(msg: String): F[Unit]  = F.pure(())
    def error(msg: String): F[Unit] = F.pure(())
  }
}

