package com.flowforge.core.observability

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
   * Returns a Prometheus-backed collector if metrics are available; otherwise a no-op collector.
   */
  lazy val prometheusOrNoop: MetricsCollector =
    try
      new MetricsCollector {
        def incRead(engine: String, format: String): Unit =
          try PrometheusMetrics.Data.readTotal.labels(engine, format).inc()
          catch { case _: Throwable => () }

        def incWrite(engine: String, format: String): Unit =
          try PrometheusMetrics.Data.writeTotal.labels(engine, format).inc()
          catch { case _: Throwable => () }

        def observeLatency(
          op: String,
          engine: String,
          millis: Double,
        ): Unit =
          try PrometheusMetrics.Data.opLatencyMs.labels(op, engine).observe(millis)
          catch { case _: Throwable => () }
      }
    catch {
      case _: Throwable => noop
    }
}
