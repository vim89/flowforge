package com.flowforge.infrastructure.partitions

import com.flowforge.core.types.RefinedTypes.FieldName

import java.time.{Instant, ZoneOffset}

/** Compute impacted partitions given a time window and a partitioning scheme. */
trait AffectedPartitions {
  def daily(fromInclusiveMs: Long, toExclusiveMs: Long, tz: ZoneOffset = ZoneOffset.UTC): List[String]
  def monthly(fromInclusiveMs: Long, toExclusiveMs: Long, tz: ZoneOffset = ZoneOffset.UTC): List[String]
  def byColumns(keys: Map[FieldName, String]): List[(FieldName, String)]
}

object AffectedPartitions extends AffectedPartitions {
  private def days(from: Instant, to: Instant, tz: ZoneOffset): List[String] = {
    val start = from.atOffset(tz).toLocalDate
    val end   = to.atOffset(tz).toLocalDate
    Iterator.iterate(start)(_.plusDays(1)).takeWhile(!_.isAfter(end)).map(_.toString).toList
  }
  private def months(from: Instant, to: Instant, tz: ZoneOffset): List[String] = {
    val s = from.atOffset(tz).toLocalDate.withDayOfMonth(1)
    val e = to.atOffset(tz).toLocalDate.withDayOfMonth(1)
    Iterator.iterate(s)(_.plusMonths(1)).takeWhile(!_.isAfter(e)).map(d => f"${d.getYear}%04d-${d.getMonthValue}%02d").toList
  }
  def daily(fromInclusiveMs: Long, toExclusiveMs: Long, tz: ZoneOffset): List[String] =
    days(Instant.ofEpochMilli(fromInclusiveMs), Instant.ofEpochMilli(toExclusiveMs - 1), tz)
  def monthly(fromInclusiveMs: Long, toExclusiveMs: Long, tz: ZoneOffset): List[String] =
    months(Instant.ofEpochMilli(fromInclusiveMs), Instant.ofEpochMilli(toExclusiveMs - 1), tz)
  def byColumns(keys: Map[FieldName, String]): List[(FieldName, String)] = keys.toList
}

