package com.flowforge.infrastructure.partitions

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class PartitionsEdgeCoverageSpec extends AnyFunSuite with Matchers {
  test("daily around DST boundary and offset") {
    val start = java.time.Instant.parse("2021-03-13T23:00:00Z").toEpochMilli
    val end   = java.time.Instant.parse("2021-03-15T02:00:00Z").toEpochMilli
    val days  = AffectedPartitions.daily(start, end, java.time.ZoneOffset.ofHours(-5))
    days.nonEmpty shouldBe true
  }

  test("monthly end-of-year to next-year inclusive") {
    val start = java.time.Instant.parse("2023-12-01T00:00:00Z").toEpochMilli
    val end   = java.time.Instant.parse("2024-02-01T00:00:00Z").toEpochMilli
    val parts = AffectedPartitions.monthly(start, end, java.time.ZoneOffset.UTC)
    parts should contain inOrder ("2023-12", "2024-01")
  }
}
