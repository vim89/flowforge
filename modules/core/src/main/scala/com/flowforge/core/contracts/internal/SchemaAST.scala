package com.flowforge.core.contracts.internal

/**
 * Normalized, engine-agnostic schema shape for deep structural comparison at compile time.
 */
sealed trait SchemaAST extends Product with Serializable

object SchemaAST {
  final case class Record(name: String, fields: List[Field]) extends SchemaAST
  final case class Field(
    name: String,
    tpe: SchemaAST,
    hasDefault: Boolean,
    isOptional: Boolean)
      extends SchemaAST

  final case class Primitive(tag: String)                 extends SchemaAST
  final case class OptionT(value: SchemaAST)              extends SchemaAST
  final case class ArrayT(elem: SchemaAST)                extends SchemaAST
  final case class MapT(key: SchemaAST, value: SchemaAST) extends SchemaAST

  object PrimitiveTags {
    val Str     = "string"
    val Int     = "int"
    val Long    = "long"
    val Double  = "double"
    val Float   = "float"
    val Boolean = "boolean"
    val Instant = "timestamp"
    val Decimal = "decimal"
    val Unknown = "unknown"
  }

  def pretty(ast: SchemaAST, indent: Int = 0): String = {
    val pad = "  " * indent
    ast match {
      case Primitive(tag) => s"$pad$tag"
      case OptionT(v)     => s"$pad? ${pretty(v, indent)}"
      case ArrayT(e)      => s"$pad[ ${pretty(e, indent)} ]"
      case MapT(k, v)     => s"$pad{ ${pretty(k, indent)} -> ${pretty(v, indent)} }"
      case Field(n, t, d, o) =>
        s"$pad$n: ${pretty(t, indent)}${if (o) " (opt)" else ""}${if (d) " (def)" else ""}"
      case Record(n, fs) =>
        val inner = fs.map(f => pretty(f, indent + 1)).mkString("\n")
        s"$pad$n {\n$inner\n$pad}"
    }
  }
}
