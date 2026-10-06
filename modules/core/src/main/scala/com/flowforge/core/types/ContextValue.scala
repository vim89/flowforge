package com.flowforge.core.types

/**
 * A value attached to an error for diagnosis.
 *
 * Error context used to be `Map[String, Any]`, which meant anything at all could be put in it: a whole
 * `DataFrame`, a credential, a mutable builder, a closure. Nothing in the type said otherwise, and nothing
 * read it back except one line of string interpolation. `Any` in a public signature also costs the thing the
 * rest of this codebase is built on, which is that a signature tells you what a value can be.
 *
 * Three cases cover what diagnostic context actually holds: a label, a count or size, and a yes/no. A caller
 * that wants to attach something else has to decide how it reads first, which is the decision that was being
 * skipped.
 */
sealed abstract class ContextValue extends Product with Serializable {

  /** How the value appears in an error message or a log line. */
  def render: String
}

object ContextValue {

  /** A string: a field name, an identifier, a state name. */
  final case class Text(value: String) extends ContextValue {
    def render: String = value
  }

  /**
   * A whole number: a row count, a byte size, an attempt number.
   *
   * `Long` rather than `Int` because the counts that end up here are row and byte counts, and widening is
   * free at the call site while narrowing is not.
   */
  final case class Num(value: Long) extends ContextValue {
    def render: String = value.toString
  }

  /** A yes/no: whether a retry is possible, whether a cache was warm. */
  final case class Flag(value: Boolean) extends ContextValue {
    def render: String = value.toString
  }
}
