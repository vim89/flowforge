package com.flowforge.infrastructure

import cats.effect.Sync
import cats.syntax.all._
import io.prometheus.client.{ Counter, Histogram }

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
  def recordGauge(
    name: String,
    value: Double,
    tags: Map[String, String],
  ): F[Unit]
  def recordDuration(name: String, duration: FiniteDuration): F[Unit]
}

object MetricsCollector {
  def noOpCollector[F[_]: Sync]: MetricsCollector[F] = new NoOpMetricsCollector[F]

  def prometheusCollector[F[_]: Sync]: MetricsCollector[F] = new PrometheusMetricsCollector[F]

  private class NoOpMetricsCollector[F[_]: Sync] extends MetricsCollector[F] {
    def incrementCounter(name: String): F[Unit]                                        = Sync[F].unit
    def incrementCounter(name: String, tags: Map[String, String]): F[Unit]             = Sync[F].unit
    def recordTimer[A](name: String)(operation: F[A]): F[A]                            = operation
    def recordTimer[A](name: String, tags: Map[String, String])(operation: F[A]): F[A] = operation
    def recordGauge(name: String, value: Double): F[Unit]                              = Sync[F].unit
    def recordGauge(
      name: String,
      value: Double,
      tags: Map[String, String],
    ): F[Unit] = Sync[F].unit
    def recordDuration(name: String, duration: FiniteDuration): F[Unit] = Sync[F].unit
  }

  private class PrometheusMetricsCollector[F[_]: Sync] extends MetricsCollector[F] {
    // Simple global registries by metric name. In production, prefer labeled metrics with a fixed label set.
    private def counter(name: String): Counter =
      Counter.build().name(s"ff_${name}_total").help(s"$name total").register()

    private def labeledCounter(name: String, tags: Map[String, String]): Counter.Child = {
      val labelNames  = tags.keys.toArray
      val labelValues = tags.values.toArray
      Counter
        .build()
        .name(s"ff_${name}_total")
        .labelNames(labelNames: _*)
        .help(s"$name total")
        .register()
        .labels(labelValues: _*)
    }

    private def histogram(name: String): Histogram =
      Histogram
        .build()
        .name(s"ff_${name}_seconds")
        .help(s"$name duration seconds")
        .buckets(0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1.0, 2.5, 5.0)
        .register()

    private def labeledHistogram(name: String, tags: Map[String, String]): Histogram.Child = {
      val labelNames  = tags.keys.toArray
      val labelValues = tags.values.toArray
      Histogram
        .build()
        .name(s"ff_${name}_seconds")
        .labelNames(labelNames: _*)
        .help(s"$name duration seconds")
        .buckets(0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1.0, 2.5, 5.0)
        .register()
        .labels(labelValues: _*)
    }

    def incrementCounter(name: String): F[Unit] = Sync[F].delay(counter(name).inc())

    def incrementCounter(name: String, tags: Map[String, String]): F[Unit] =
      Sync[F].delay(labeledCounter(name, tags).inc())

    def recordTimer[A](name: String)(operation: F[A]): F[A] =
      Sync[F].delay(histogram(name).startTimer()).flatMap { timer =>
        Sync[F].guarantee(operation, Sync[F].delay(timer.observeDuration()).void)
      }

    def recordTimer[A](name: String, tags: Map[String, String])(operation: F[A]): F[A] =
      Sync[F].delay(labeledHistogram(name, tags).startTimer()).flatMap { timer =>
        Sync[F].guarantee(operation, Sync[F].delay(timer.observeDuration()).void)
      }

    // Gauges are not provided by simpleclient core directly; use counters/histograms or integrate a Gauge if needed.
    def recordGauge(name: String, value: Double): F[Unit] = Sync[F].unit

    def recordGauge(
      name: String,
      value: Double,
      tags: Map[String, String],
    ): F[Unit] = Sync[F].unit

    def recordDuration(name: String, duration: FiniteDuration): F[Unit] =
      Sync[F].delay(histogram(name).observe(duration.toNanos.toDouble / 1e9))
  }
}
