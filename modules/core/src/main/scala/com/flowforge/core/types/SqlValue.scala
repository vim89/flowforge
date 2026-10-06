package com.flowforge.core.types

import java.time.Instant

/**
 * A value bound to a placeholder in a SQL statement.
 *
 * Parameters used to be `List[Any]`, which says nothing about what a driver can actually bind. The set of
 * things a JDBC `PreparedStatement` accepts is small and closed, so the type can say so: a string, a whole
 * number, a fractional number, a boolean, an instant, or SQL `NULL`. A caller with something else has to
 * decide how it maps to a column before the call rather than after it, which is the decision `Any` let
 * everyone skip.
 *
 * `Null` is a case rather than an absent element because a bound `NULL` still occupies a placeholder;
 * dropping it from the list would shift every parameter after it.
 */
sealed abstract class SqlValue extends Product with Serializable

object SqlValue {

  /** A character value: `VARCHAR`, `TEXT`, `CHAR`. */
  final case class Text(value: String) extends SqlValue

  /** A whole number: `INT`, `BIGINT`. `Long` so the widest integral column fits. */
  final case class Num(value: Long) extends SqlValue

  /** A fractional number: `DOUBLE`, `FLOAT`, `NUMERIC`. */
  final case class Decimal(value: Double) extends SqlValue

  /** A boolean value: `BOOLEAN`, or the single-bit column a driver maps it to. */
  final case class Flag(value: Boolean) extends SqlValue

  /** A point in time: `TIMESTAMP`, `TIMESTAMPTZ`. */
  final case class Timestamp(value: Instant) extends SqlValue

  /** SQL `NULL`, whatever the column's type. */
  case object Null extends SqlValue
}
