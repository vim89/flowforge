package com.flowforge.infrastructure

import cats.effect.Sync
import cats.syntax.all._

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
    def startSpan[A](operationName: String)(operation: F[A]): F[A] =
      for {
        _ <- Sync[F].delay {
          // TODO: Start OpenTelemetry span
          ()
        }
        result <- operation
        _ <- Sync[F].delay {
          // TODO: End OpenTelemetry span
          ()
        }
      } yield result

    def startSpan[A](operationName: String, tags: Map[String, String])(operation: F[A]): F[A] =
      startSpan(operationName)(operation)

    def addTag(key: String, value: String): F[Unit] =
      Sync[F].delay {
        // TODO: Add tag to current span
        ()
      }

    def addBaggage(key: String, value: String): F[Unit] =
      Sync[F].delay {
        // TODO: Add baggage to current span
        ()
      }

    def setError(error: Throwable): F[Unit] =
      Sync[F].delay {
        // TODO: Set error on current span
        ()
      }

    def setError(message: String): F[Unit] =
      Sync[F].delay {
        // TODO: Set error message on current span
        ()
      }
  }
}
