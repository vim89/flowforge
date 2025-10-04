package com.flowforge.core.algebra

import com.flowforge.core.types._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.time.Instant
import scala.concurrent.duration._

class MetricsCollectorCoverageSpec extends AnyFunSuite with Matchers {
  test("MetricsCollector instance, collectTimed and aggregate") {
    val mc = MetricsCollector.instance[String](
      data => ProcessingMetrics(recordCount = 1, byteSize = data.length, processingTimeMs = 0),
      List("len"),
    )
    val m  = mc.collect("abc")
    m.byteSize shouldBe 3
    val mt = mc.collectTimed("abc", 42.millis)
    mt.customMetrics.get("processing_duration_ms").exists(_ >= 42.0) shouldBe true
    val agg = mc.aggregate(List("a", "bb", "ccc"))
    agg.recordCount shouldBe 3
    agg.byteSize shouldBe (1 + 2 + 3)

    // filterMetrics narrows metric names
    val filtered = mc.filterMetrics(Set("len"))
    filtered.metricNames shouldBe List("len")
    filtered.isEnabled shouldBe true
  }

  test("MetricsCollector.noop yields empty metrics and aggregates to empty") {
    val noop = MetricsCollector.noop[Int]
    val m    = noop.collect(1)
    m shouldBe ProcessingMetrics.empty
    val comb = ProcessingMetrics.combine(Nil)
    comb shouldBe ProcessingMetrics.empty
    val basic = ProcessingMetrics.basic(10, Instant.now)
    basic.recordCount shouldBe 1
  }
}
