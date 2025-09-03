package com.flowforge.logging

import cats.effect.Sync
import cats.syntax.all._
import com.typesafe.scalalogging.Logger

/**
 * Structured logger trait for effect-polymorphic logging.
 */
trait StructuredLogger[F[_]] {
  def info(msg: String): F[Unit]
  def info(msg: String, context: Map[String, String]): F[Unit]
  def warn(msg: String): F[Unit]
  def warn(msg: String, context: Map[String, String]): F[Unit]
  def error(msg: String): F[Unit]
  def error(msg: String, context: Map[String, String]): F[Unit]
  def error(msg: String, throwable: Throwable): F[Unit]
  def withContext(context: Map[String, String]): StructuredLogger[F]
  def logOperation[A](operationName: String)(operation: F[A]): F[A]
}

object StructuredLogger {
  def forName[F[_]: Sync](name: String): StructuredLogger[F] =
    new DefaultStructuredLogger[F](Logger(name))

  private class DefaultStructuredLogger[F[_]: Sync](logger: Logger) extends StructuredLogger[F] {

    def info(msg: String): F[Unit] =
      Sync[F].delay(logger.info(msg))

    def info(msg: String, context: Map[String, String]): F[Unit] =
      Sync[F].delay(logger.info(format(msg, context)))

    def warn(msg: String): F[Unit] =
      Sync[F].delay(logger.warn(msg))

    def warn(msg: String, context: Map[String, String]): F[Unit] =
      Sync[F].delay(logger.warn(format(msg, context)))

    def error(msg: String): F[Unit] =
      Sync[F].delay(logger.error(msg))

    def error(msg: String, context: Map[String, String]): F[Unit] =
      Sync[F].delay(logger.error(format(msg, context)))

    def error(msg: String, throwable: Throwable): F[Unit] =
      Sync[F].delay(logger.error(msg, throwable))

    def withContext(context: Map[String, String]): StructuredLogger[F] =
      new ContextualStructuredLogger[F](this, context)

    def logOperation[A](operationName: String)(operation: F[A]): F[A] =
      for {
        _      <- info(s"Starting operation: $operationName")
        result <- operation
        _      <- info(s"Completed operation: $operationName")
      } yield result

    private def format(msg: String, context: Map[String, String]): String =
      if (context.isEmpty) msg
      else {
        val ctx = context.map { case (k, v) => s"$k=$v" }.mkString(" ")
        s"$msg | $ctx"
      }
  }

  private class ContextualStructuredLogger[F[_]: Sync](
    underlying: StructuredLogger[F],
    contextMap: Map[String, String])
      extends StructuredLogger[F] {

    def info(msg: String): F[Unit] = underlying.info(msg, contextMap)
    def info(msg: String, context: Map[String, String]): F[Unit] =
      underlying.info(msg, contextMap ++ context)
    def warn(msg: String): F[Unit] = underlying.warn(msg, contextMap)
    def warn(msg: String, context: Map[String, String]): F[Unit] =
      underlying.warn(msg, contextMap ++ context)
    def error(msg: String): F[Unit] = underlying.error(msg, contextMap)
    def error(msg: String, context: Map[String, String]): F[Unit] =
      underlying.error(msg, contextMap ++ context)
    def error(msg: String, throwable: Throwable): F[Unit] =
      underlying.error(msg, throwable)
    def withContext(context: Map[String, String]): StructuredLogger[F] =
      new ContextualStructuredLogger[F](underlying, contextMap ++ context)
    def logOperation[A](operationName: String)(operation: F[A]): F[A] =
      underlying.logOperation(operationName)(operation)
  }
}
