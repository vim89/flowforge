package com.flowforge.infrastructure.partitions

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class AffectedPartitionsSpec extends AnyFunSuite with Matchers {
  test("daily partitions compute inclusive-exclusive window") {
    val start = 1704067200000L // 2024-01-01T00:00:00Z
    val end   = 1704239999000L // 2024-01-02T23:59:59Z
    val parts = AffectedPartitions.daily(start, end + 1)
    parts should contain inOrderOnly ("2024-01-01", "2024-01-02")
  }
  test("monthly partitions compute months range") {
    val start = 1704067200000L // 2024-01-01
    val end   = 1709251200000L // 2024-03-31
    val parts = AffectedPartitions.monthly(start, end + 1)
    parts should contain inOrderOnly ("2024-01", "2024-02", "2024-03")
  }
}
