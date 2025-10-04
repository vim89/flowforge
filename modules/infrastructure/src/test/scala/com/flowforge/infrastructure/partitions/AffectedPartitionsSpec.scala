// scalafix:off DisableSyntax.var DisableSyntax.throw DisableSyntax.null DisableSyntax.noUnsafeRunSync
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

  test("leap day coverage and non-UTC offsets") {
    val feb28 = java.time.Instant.parse("2020-02-28T00:00:00Z").toEpochMilli
    val mar01 = java.time.Instant.parse("2020-03-01T00:00:00Z").toEpochMilli
    val daysUtc = AffectedPartitions.daily(feb28, mar01, java.time.ZoneOffset.UTC)
    daysUtc should contain allOf ("2020-02-28", "2020-02-29")
    val offset = java.time.ZoneOffset.ofHours(-5)
    val daysOff = AffectedPartitions.daily(feb28, mar01, offset)
    daysOff.nonEmpty shouldBe true
  }

  test("months across year boundary with offset") {
    val dec = java.time.Instant.parse("2021-12-15T00:00:00Z").toEpochMilli
    val feb = java.time.Instant.parse("2022-02-02T00:00:00Z").toEpochMilli
    val months = AffectedPartitions.monthly(dec, feb, java.time.ZoneOffset.ofHours(2))
    months should contain inOrder ("2021-12", "2022-01", "2022-02")
  }
}
