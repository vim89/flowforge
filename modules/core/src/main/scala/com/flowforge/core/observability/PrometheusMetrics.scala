package com.flowforge.core.observability

import io.prometheus.client.{ Counter, Summary }

object PrometheusMetrics {

  object Data {
    val readTotal: Counter = Counter
      .build()
      .name("flowforge_data_read_total")
      .help("Total datasets read")
      .labelNames("engine", "format")
      .register()

    val writeTotal: Counter = Counter
      .build()
      .name("flowforge_data_write_total")
      .help("Total datasets written")
      .labelNames("engine", "format")
      .register()

    val validateTotal: Counter = Counter
      .build()
      .name("flowforge_data_validate_total")
      .help("Total validations executed")
      .labelNames("engine")
      .register()

    val opLatencyMs: Summary = Summary
      .build()
      .name("flowforge_op_latency_ms")
      .help("Operation latency in milliseconds")
      .labelNames("op", "engine")
      .quantile(0.5, 0.05)
      .quantile(0.9, 0.01)
      .quantile(0.99, 0.001)
      .register()
  }
}
