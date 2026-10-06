package com.flowforge.core.observability

import scala.util.control.NonFatal

/**
 * Tiny wrapper over Prometheus client to keep call sites clean and make metrics optional. If Prometheus is
 * absent or registration fails, no-ops are used.
 */
trait MetricsCollector {
  def incRead(engine: String, format: String): Unit
  def incWrite(engine: String, format: String): Unit
  def observeLatency(
    op: String,
    engine: String,
    millis: Double,
  ): Unit
}

object MetricsCollector {
  lazy val noop: MetricsCollector = new MetricsCollector {
    def incRead(engine: String, format: String): Unit  = ()
    def incWrite(engine: String, format: String): Unit = ()
    def observeLatency(
      op: String,
      engine: String,
      millis: Double,
    ): Unit = ()
  }

  /**
   * Run a metric update, discarding only the failures that mean the metric could not be recorded.
   *
   * Two kinds of failure are discarded. `NonFatal` covers a client that rejects the call, such as a label
   * count that does not match the collector's declared labels. `LinkageError` covers the Prometheus classes
   * being absent from the classpath, and also covers `PrometheusMetrics` failing its own initialization,
   * which is what a duplicate registration in the default registry looks like from here. Both are surfaced as
   * an `Error` rather than an exception, so neither is `NonFatal`.
   *
   * Everything else propagates. The ones that matter are `InterruptedException`, because swallowing it loses
   * a cancellation request the caller is waiting on, and `VirtualMachineError`, because a metric call is not
   * a useful place to decide that the JVM running out of memory is survivable.
   *
   * Nothing is logged. These calls sit on the per-operation path, so a broken metrics setup would log once
   * per read and write for the life of the process.
   */
  private[observability] def bestEffort(update: => Unit): Unit =
    try update
    catch {
      case NonFatal(_)     => ()
      case _: LinkageError => ()
    }

  /**
   * Returns a Prometheus-backed collector if metrics are available; otherwise a no-op collector.
   *
   * There is no eager availability check: `PrometheusMetrics` builds and registers its collectors when it is
   * first touched, which happens inside the methods below rather than here. So "otherwise a no-op collector"
   * is delivered per call by [[bestEffort]] rather than by returning [[noop]].
   */
  lazy val prometheusOrNoop: MetricsCollector =
    new MetricsCollector {
      def incRead(engine: String, format: String): Unit =
        bestEffort(PrometheusMetrics.Data.readTotal.labels(engine, format).inc())

      def incWrite(engine: String, format: String): Unit =
        bestEffort(PrometheusMetrics.Data.writeTotal.labels(engine, format).inc())

      def observeLatency(
        op: String,
        engine: String,
        millis: Double,
      ): Unit =
        bestEffort(PrometheusMetrics.Data.opLatencyMs.labels(op, engine).observe(millis))
    }
}
