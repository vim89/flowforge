// scalafix:off DisableSyntax.throw
package com.flowforge.core.observability

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

/**
 * Pins which failures a metric update is allowed to hide.
 *
 * These call sites used to catch `Throwable`, which hid an interrupt and a dying JVM as readily as a missing
 * Prometheus client. The split matters because the collector is called once per read and write: anything it
 * swallows is swallowed on the hot path of every pipeline.
 */
class MetricsCollectorSpec extends AnyFunSuite with Matchers {

  test("a client that rejects the call is hidden from the caller") {
    // What a label count mismatch looks like from the call site.
    noException should be thrownBy
      MetricsCollector.bestEffort(throw new IllegalArgumentException("Incorrect number of labels"))
  }

  test("prometheus classes being absent is hidden from the caller") {
    noException should be thrownBy
      MetricsCollector.bestEffort(throw new NoClassDefFoundError("io/prometheus/client/Counter"))
  }

  test("a failed registration is hidden from the caller") {
    // PrometheusMetrics registers its collectors during its own initialization, so a duplicate registration
    // in the default registry reaches the call site as this error rather than as the IllegalArgumentException
    // the registry threw.
    noException should be thrownBy
      MetricsCollector.bestEffort(
        throw new ExceptionInInitializerError(new IllegalArgumentException("Collector already registered")),
      )
  }

  test("an interrupt reaches the caller") {
    // The caller is the one waiting on the cancellation, so it has to see this.
    an[InterruptedException] should be thrownBy
      MetricsCollector.bestEffort(throw new InterruptedException())
  }

  test("a dying JVM reaches the caller") {
    an[OutOfMemoryError] should be thrownBy
      MetricsCollector.bestEffort(throw new OutOfMemoryError("Java heap space"))
  }

  test("the prometheus backed collector records without throwing") {
    val collector = MetricsCollector.prometheusOrNoop

    noException should be thrownBy {
      collector.incRead("test", "json")
      collector.incWrite("test", "json")
      collector.observeLatency("read", "test", 1.0)
    }
  }
}
