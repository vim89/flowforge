package com.flowforge.infrastructure

import cats.effect.Sync

/**
 * Distributed tracing for request tracking.
 */
trait DistributedTracing[F[_]] {
  def startSpan[A](operationName: String)(operation: F[A]): F[A]
  def startSpan[A](operationName: String, tags: Map[String, String])(operation: F[A]): F[A]
  def addTag(key: String, value: String): F[Unit]
  def addBaggage(key: String, value: String): F[Unit]
  def setError(error: Throwable): F[Unit]
  def setError(message: String): F[Unit]
}

object DistributedTracing {
  def noOpTracing[F[_]: Sync]: DistributedTracing[F] = new NoOpDistributedTracing[F]

  def openTelemetryTracing[F[_]: Sync]: DistributedTracing[F] = new OpenTelemetryTracing[F]

  private class NoOpDistributedTracing[F[_]: Sync] extends DistributedTracing[F] {
    def startSpan[A](operationName: String)(operation: F[A]): F[A] = operation
    def startSpan[A](operationName: String, tags: Map[String, String])(operation: F[A]): F[A] =
      operation
    def addTag(key: String, value: String): F[Unit]     = Sync[F].unit
    def addBaggage(key: String, value: String): F[Unit] = Sync[F].unit
    def setError(error: Throwable): F[Unit]             = Sync[F].unit
    def setError(message: String): F[Unit]              = Sync[F].unit
  }

  private class OpenTelemetryTracing[F[_]: Sync] extends DistributedTracing[F] {
    import io.opentelemetry.api.GlobalOpenTelemetry
    import io.opentelemetry.api.trace.{ Span, SpanKind, StatusCode }

    private val tracer = GlobalOpenTelemetry.getTracer("com.flowforge.tracing")

    def startSpan[A](operationName: String)(operation: F[A]): F[A] =
      startSpan(operationName, Map.empty)(operation)

    def startSpan[A](operationName: String, tags: Map[String, String])(operation: F[A]): F[A] =
      Sync[F].bracket(
        Sync[F].delay {
          val spanBuilder = tracer.spanBuilder(operationName).setSpanKind(SpanKind.INTERNAL)
          val span        = spanBuilder.startSpan()
          tags.foreach { case (k, v) => span.setAttribute(k, v) }
          val scope = span.makeCurrent()
          (span, scope)
        },
      ) { case _ => operation } {
        case (span, scope) =>
          Sync[F].delay {
            try span.end()
            finally scope.close()
          }
      }

    def addTag(key: String, value: String): F[Unit] =
      Sync[F].delay(Span.current().setAttribute(key, value))

    def addBaggage(key: String, value: String): F[Unit] =
      Sync[F].delay {
        import io.opentelemetry.api.baggage.{ Baggage, BaggageEntryMetadata }
        val baggage = Baggage.current().toBuilder.put(key, value, BaggageEntryMetadata.empty()).build()
        baggage.makeCurrent().close() // push baggage into Context for downstream usage
      }

    def setError(error: Throwable): F[Unit] =
      Sync[F].delay {
        val span = Span.current()
        span.recordException(error)
        span.setStatus(StatusCode.ERROR, Option(error.getMessage).getOrElse("error"))
      }

    def setError(message: String): F[Unit] =
      Sync[F].delay {
        val span = Span.current()
        span.setStatus(StatusCode.ERROR, message)
      }
  }
}
