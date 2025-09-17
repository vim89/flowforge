package com.flowforge.example

/**
 * Logging & Metrics Quickstart
 *
 * Demonstrates SLF4J/Logback logging and a Prometheus HTTP server exposing basic process metrics.
 */
object LoggingAndMetrics {
  def main(args: Array[String]): Unit = {
    // Logging
    val log = org.slf4j.LoggerFactory.getLogger("app")
    log.info("Application starting…")

    // Prometheus: default JVM metrics and a custom counter
    io.prometheus.client.hotspot.DefaultExports.initialize()
    val requests = io.prometheus.client.Counter
      .build("demo_requests_total", "Total demo requests processed")
      .register()

    // Start HTTP metrics server on port 9095
    val server = new io.prometheus.client.exporter.HTTPServer(9095)
    log.info("Prometheus metrics available at http://localhost:9095/metrics")

    // Simulate some “work” and metric increments
    (1 to 5).foreach { i =>
      log.info("Processing item " + i)
      requests.inc()
      Thread.sleep(200)
    }

    log.info("Done. Press Ctrl+C to exit.")
    // Keep process alive to allow scraping
    Thread.sleep(5 * 60 * 1000)
    server.close()
  }
}

