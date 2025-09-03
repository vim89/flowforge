package com.flowforge.logging

import cats.effect.Sync
import cats.syntax.all._
import com.typesafe.scalalogging.Logger
import org.slf4j.{ LoggerFactory, MDC }

import scala.collection.mutable
import scala.jdk.CollectionConverters.MapHasAsScala

/**
 * Structured logging framework for FlowForge Infrastructure Layer. Provides type-safe, contextual
 * logging with automatic structured data handling.
 */
trait StructuredLogger[F[_]] {

  /**
   * Log informational message with optional context.
   */
  def info(message: String, context: Map[String, String] = Map.empty): F[Unit]

  /**
   * Log warning message with optional context.
   */
  def warn(message: String, context: Map[String, String] = Map.empty): F[Unit]

  /**
   * Log error message with throwable and context.
   */
  def error(message: String, error: Throwable, context: Map[String, String] = Map.empty): F[Unit]

  /**
   * Log error message with context (no throwable).
   */
  def error(message: String, context: Map[String, String]): F[Unit]

  /**
   * Log debug message with optional context.
   */
  def debug(message: String, context: Map[String, String] = Map.empty): F[Unit]

  /**
   * Execute operation with additional logging context. Context is automatically added to all log
   * messages within the operation.
   */
  def withContext[A](context: Map[String, String])(operation: F[A]): F[A]

  /**
   * Log pipeline operation with automatic timing and context.
   */
  def logOperation[A](operationName: String, context: Map[String, String] = Map.empty)(
    operation: F[A]
  ): F[A]

  /**
   * Log data processing metrics (record counts, processing time, etc.).
   */
  def logDataMetrics(
    operation: String,
    recordsProcessed: Long,
    processingTimeMs: Long,
    context: Map[String, String] = Map.empty
  ): F[Unit]
}

/**
 * Logging levels for structured logging.
 */
sealed trait LogLevel extends Product with Serializable

object LogLevel {
  case object Debug extends LogLevel
  case object Info  extends LogLevel
  case object Warn  extends LogLevel
  case object Error extends LogLevel
}

/**
 * Structured log entry with metadata.
 */
case class LogEntry(
  level: LogLevel,
  message: String,
  context: Map[String, String],
  timestamp: Long,
  thread: String,
  error: Option[Throwable] = None
)

object StructuredLogger {

  /**
   * Create StructuredLogger instance.
   */
  def apply[F[_]: StructuredLogger]: StructuredLogger[F] = implicitly[StructuredLogger[F]]

  /**
   * Create logger for specific class/component.
   */
  def forClass[F[_]: Sync](clazz: Class[_]): StructuredLogger[F] =
    new Slf4jStructuredLogger[F](Logger(clazz.getName))

  /**
   * Create logger with specific name.
   */
  def forName[F[_]: Sync](name: String): StructuredLogger[F] =
    new Slf4jStructuredLogger[F](Logger(name))

  /**
   * Default implementation using SLF4J with structured MDC.
   */
  implicit def forSyncEffect[F[_]: Sync]: StructuredLogger[F] =
    new Slf4jStructuredLogger[F](Logger("FlowForge"))

  /**
   * SLF4J-based implementation with MDC support.
   */
  private class Slf4jStructuredLogger[F[_]: Sync](logger: Logger) extends StructuredLogger[F] {

    override def info(message: String, context: Map[String, String] = Map.empty): F[Unit] =
      Sync[F].delay {
        withMDC(context) {
          logger.info(message)
        }
      }

    override def warn(message: String, context: Map[String, String] = Map.empty): F[Unit] =
      Sync[F].delay {
        withMDC(context) {
          logger.warn(message)
        }
      }

    override def error(
      message: String,
      error: Throwable,
      context: Map[String, String] = Map.empty
    ): F[Unit] =
      Sync[F].delay {
        withMDC(context) {
          logger.error(message, error)
        }
      }

    override def error(message: String, context: Map[String, String]): F[Unit] =
      Sync[F].delay {
        withMDC(context) {
          logger.error(message)
        }
      }

    override def debug(message: String, context: Map[String, String] = Map.empty): F[Unit] =
      Sync[F].delay {
        withMDC(context) {
          logger.debug(message)
        }
      }

    override def logOperation[A](operationName: String, context: Map[String, String] = Map.empty)(
      operation: F[A]
    ): F[A] = {
      val startTime        = System.currentTimeMillis()
      val operationContext = context + ("operation" -> operationName)

      for {
        _      <- info(s"Starting operation: $operationName", operationContext)
        result <- operation.attempt
        endTime      = System.currentTimeMillis()
        duration     = endTime - startTime
        finalContext = operationContext + ("duration_ms" -> duration.toString)
        _ <- result match {
          case Right(_)    => info(s"Completed operation: $operationName", finalContext)
          case Left(error) => this.error(s"Failed operation: $operationName", error, finalContext)
        }
        finalResult <- Sync[F].fromEither(result)
      } yield finalResult
    }

    override def logDataMetrics(
      operation: String,
      recordsProcessed: Long,
      processingTimeMs: Long,
      context: Map[String, String] = Map.empty
    ): F[Unit] = {
      val metricsContext = context ++ Map(
        "operation"          -> operation,
        "records_processed"  -> recordsProcessed.toString,
        "processing_time_ms" -> processingTimeMs.toString,
        "records_per_second" -> (if (processingTimeMs > 0)
                                   (recordsProcessed * 1000 / processingTimeMs).toString
                                 else "0")
      )

      info(s"Data processing metrics for $operation", metricsContext)
    }

    /**
     * Execute code block with MDC context, restoring original context afterwards.
     */
    private def withMDC[A](context: Map[String, String])(block: => A): A = {
      val originalMDC = getCurrentMDC
      try {
        context.foreach { case (k, v) => MDC.put(k, v) }
        block
      } finally {
        MDC.clear()
        originalMDC.foreach { case (k, v) => MDC.put(k, v) }
      }
    }

    /**
     * Get current MDC as immutable map.
     */
    private def getCurrentMDC: Map[String, String] =
      Option(MDC.getCopyOfContextMap) match {
        case Some(mdcMap) => mdcMap.asScala.toMap
        case None         => Map.empty
      }

    /**
     * Execute operation with additional logging context. Context is automatically added to all log
     * messages within the operation.
     */
    override def withContext[A](context: Map[String, String])(operation: F[A]): F[A] =
      // Use bracket-style pattern to push MDC before running the effect and restore afterward
      Sync[F].bracket(
        acquire = Sync[F].delay(getCurrentMDC)
      )(
        use = original =>
          Sync[F].delay {
            context.foreach { case (k, v) => MDC.put(k, v) }
          } *> operation
      )(
        release = original =>
          Sync[F].delay {
            MDC.clear()
            original.foreach { case (k, v) => MDC.put(k, v) }
          }
      )
  }
}

/**
 * Metrics collection for observability framework.
 */
trait MetricsCollector[F[_]] {

  /**
   * Increment counter metric.
   */
  def incrementCounter(name: String, tags: Map[String, String] = Map.empty): F[Unit]

  /**
   * Record gauge value.
   */
  def recordGauge(name: String, value: Double, tags: Map[String, String] = Map.empty): F[Unit]

  /**
   * Record histogram value.
   */
  def recordHistogram(name: String, value: Double, tags: Map[String, String] = Map.empty): F[Unit]

  /**
   * Time operation and record duration.
   */
  def recordTimer[A](name: String, tags: Map[String, String] = Map.empty)(operation: F[A]): F[A]

  /**
   * Record pipeline metrics (throughput, latency, error rate).
   */
  def recordPipelineMetrics(
    pipelineName: String,
    recordsProcessed: Long,
    processingTimeMs: Long,
    errors: Long = 0
  ): F[Unit]
}

object MetricsCollector {

  /**
   * No-op metrics collector for development/testing.
   */
  implicit def noOpCollector[F[_]: Sync]: MetricsCollector[F] = new NoOpMetricsCollector[F]

  private class NoOpMetricsCollector[F[_]: Sync] extends MetricsCollector[F] {

    override def incrementCounter(name: String, tags: Map[String, String] = Map.empty): F[Unit] =
      Sync[F].unit

    override def recordGauge(
      name: String,
      value: Double,
      tags: Map[String, String] = Map.empty
    ): F[Unit] =
      Sync[F].unit

    override def recordHistogram(
      name: String,
      value: Double,
      tags: Map[String, String] = Map.empty
    ): F[Unit] =
      Sync[F].unit

    override def recordTimer[A](name: String, tags: Map[String, String] = Map.empty)(
      operation: F[A]
    ): F[A] = {
      val start = System.currentTimeMillis()
      operation.flatTap(_ => Sync[F].delay(System.currentTimeMillis() - start))
    }

    override def recordPipelineMetrics(
      pipelineName: String,
      recordsProcessed: Long,
      processingTimeMs: Long,
      errors: Long = 0
    ): F[Unit] = Sync[F].unit
  }
}

/**
 * Distributed tracing for request tracking across services.
 */
trait DistributedTracing[F[_]] {

  /**
   * Create new span for operation.
   */
  def createSpan[A](operationName: String, tags: Map[String, String] = Map.empty)(
    operation: F[A]
  ): F[A]

  /**
   * Add tag to current span.
   */
  def addSpanTag(key: String, value: String): F[Unit]

  /**
   * Get current trace ID for correlation.
   */
  def getCurrentTraceId: F[Option[String]]

  /**
   * Create child span with automatic parent relationship.
   */
  def childSpan[A](operationName: String)(operation: F[A]): F[A]
}

object DistributedTracing {

  /**
   * No-op distributed tracing for development/testing.
   */
  implicit def noOpTracing[F[_]: Sync]: DistributedTracing[F] = new NoOpDistributedTracing[F]

  private class NoOpDistributedTracing[F[_]: Sync] extends DistributedTracing[F] {

    override def createSpan[A](operationName: String, tags: Map[String, String] = Map.empty)(
      operation: F[A]
    ): F[A] =
      operation

    override def addSpanTag(key: String, value: String): F[Unit] =
      Sync[F].unit

    override def getCurrentTraceId: F[Option[String]] =
      Sync[F].pure(None)

    override def childSpan[A](operationName: String)(operation: F[A]): F[A] =
      operation
  }
}
