package com.flowforge.core.observability

import cats.Applicative

/** Tracing interface (minimal) with a no-op default and an OTEL-ready hook. */
trait Tracer[F[_]] {
  def inSpan[A](name: String)(fa: F[A]): F[A]
  def annotate(key: String, value: String): F[Unit]
}

object Tracer {
  def noop[F[_]](implicit F: Applicative[F]): Tracer[F] = new Tracer[F] {
    def inSpan[A](name: String)(fa: F[A]): F[A]       = fa
    def annotate(key: String, value: String): F[Unit] = F.unit
  }
}

/**
 * Placeholder for OTEL integration. Actual wiring is provided by infrastructure modules. Users can supply an
 * instance via PipelineBuilder.withTracer.
 */
object OTelTracer {
  // Stub constructor kept minimal to avoid hard dependency
  def create[F[_]: Applicative]: Tracer[F] = Tracer.noop
}
