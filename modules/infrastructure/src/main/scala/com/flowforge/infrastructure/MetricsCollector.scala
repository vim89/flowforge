package com.flowforge.infrastructure

import cats.effect.Sync
import cats.syntax.all._
import scala.concurrent.duration.FiniteDuration

/**
 * Metrics collector for observability.
 */
trait MetricsCollector[F[_]] {
  def incrementCounter(name: String): F[Unit]
  def incrementCounter(name: String, tags: Map[String, String]): F[Unit]
  def recordTimer[A](name: String)(operation: F[A]): F[A]
  def recordTimer[A](name: String, tags: Map[String, String])(operation: F[A]): F[A]
  def recordGauge(name: String, value: Double): F[Unit]
  def recordGauge(name: String, value: Double, tags: Map[String, String]): F[Unit]
  def recordDuration(name: String, duration: FiniteDuration): F[Unit]
}

object MetricsCollector {
  def noOpCollector[F[_]: Sync]: MetricsCollector[F] = new NoOpMetricsCollector[F]

  def prometheusCollector[F[_]: Sync]: MetricsCollector[F] = new PrometheusMetricsCollector[F]

  private class NoOpMetricsCollector[F[_]: Sync] extends MetricsCollector[F] {
    def incrementCounter(name: String): F[Unit]                            = Sync[F].unit
    def incrementCounter(name: String, tags: Map[String, String]): F[Unit] = Sync[F].unit
    def recordTimer[A](name: String)(operation: F[A]): F[A]                = operation
    def recordTimer[A](name: String, tags: Map[String, String])(operation: F[A]): F[A] = operation
    def recordGauge(name: String, value: Double): F[Unit]                            = Sync[F].unit
    def recordGauge(name: String, value: Double, tags: Map[String, String]): F[Unit] = Sync[F].unit
    def recordDuration(name: String, duration: FiniteDuration): F[Unit]              = Sync[F].unit
  }

  private class PrometheusMetricsCollector[F[_]: Sync] extends MetricsCollector[F] {
    def incrementCounter(name: String): F[Unit] =
      Sync[F].delay {
        // TODO: Implement actual Prometheus integration
        ()
      }

    def incrementCounter(name: String, tags: Map[String, String]): F[Unit] =
      Sync[F].delay {
        // TODO: Implement actual Prometheus integration with tags
        ()
      }

    def recordTimer[A](name: String)(operation: F[A]): F[A] =
      for {
        start  <- Sync[F].delay(System.currentTimeMillis())
        result <- operation
        end    <- Sync[F].delay(System.currentTimeMillis())
        _ <- Sync[F].delay {
          // TODO: Record timer metric (end - start)
          ()
        }
      } yield result

    def recordTimer[A](name: String, tags: Map[String, String])(operation: F[A]): F[A] =
      recordTimer(name)(operation)

    def recordGauge(name: String, value: Double): F[Unit] =
      Sync[F].delay {
        // TODO: Implement actual Prometheus gauge
        ()
      }

    def recordGauge(name: String, value: Double, tags: Map[String, String]): F[Unit] =
      recordGauge(name, value)

    def recordDuration(name: String, duration: FiniteDuration): F[Unit] =
      Sync[F].delay {
        // TODO: Record duration metric
        ()
      }
  }
}
