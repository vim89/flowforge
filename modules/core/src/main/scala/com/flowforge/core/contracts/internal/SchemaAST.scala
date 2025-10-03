package com.flowforge.core.contracts.internal

/**
 * Normalized structural shape for compile-time comparison.
 *
 * Clean, minimal ADT focused on
 * the essentials needed for deep schema comparison.
 */
sealed trait TypeShape

object TypeShape {
  final case class PrimitiveShape(name: String) extends TypeShape
  final case class SequenceShape(elem: TypeShape) extends TypeShape
  final case class MapShape(key: PrimitiveShape, value: TypeShape) extends TypeShape
  // Represents nested optionality (e.g., List[Option[A]]). Field-level optionality remains on FieldShape.
  final case class OptionalShape(inner: TypeShape) extends TypeShape
  final case class FieldShape(
    name: String,
    shape: TypeShape,
    hasDefault: Boolean,
    isOptional: Boolean
  ) extends TypeShape
  final case class StructShape(fields: List[FieldShape]) extends TypeShape

  def pretty(shape: TypeShape): String = shape match {
    case PrimitiveShape(name) => name
    case SequenceShape(elem) => s"List[${pretty(elem)}]"
    case MapShape(key, value) => s"Map[${pretty(key)}, ${pretty(value)}]"
    case OptionalShape(inner) => s"Option[${pretty(inner)}]"
    case FieldShape(name, tpe, hasDefault, isOptional) =>
      val opt = if (isOptional) " (optional)" else ""
      val dflt = if (hasDefault) " (default)" else ""
      s"$name: ${pretty(tpe)}$opt$dflt"
    case StructShape(fields) =>
      fields.map(f => f.name + ":" + pretty(f.shape)).mkString("{", ",", "}")
  }
}

// Legacy compatibility - keep SchemaAST as alias until migration complete

@deprecated("Use TypeShape instead", "0.2.0")
object SchemaAST {
  // Legacy type aliases for backward compatibility
  type Record = TypeShape.StructShape
  type Field = TypeShape.FieldShape
  type Primitive = TypeShape.PrimitiveShape
  type OptionT = TypeShape.OptionalShape
  type ArrayT = TypeShape.SequenceShape
  type MapT = TypeShape.MapShape

  // Factory methods for backward compatibility
  def Record(name: String, fields: List[TypeShape.FieldShape]): TypeShape.StructShape =
    TypeShape.StructShape(fields)
  def Field(name: String, tpe: TypeShape, hasDefault: Boolean, isOptional: Boolean): TypeShape.FieldShape =
    TypeShape.FieldShape(name, tpe, hasDefault, isOptional)
  def Primitive(tag: String): TypeShape.PrimitiveShape =
    TypeShape.PrimitiveShape(tag)
  def OptionT(value: TypeShape): TypeShape.OptionalShape =
    TypeShape.OptionalShape(value)
  def ArrayT(elem: TypeShape): TypeShape.SequenceShape =
    TypeShape.SequenceShape(elem)
  def MapT(key: TypeShape.PrimitiveShape, value: TypeShape): TypeShape.MapShape =
    TypeShape.MapShape(key, value)

  object PrimitiveTags {
    val Str     = "String"
    val Int     = "Int"
    val Long    = "Long"
    val Double  = "Double"
    val Float   = "Float"
    val Boolean = "Boolean"
    val Instant = "Instant"
    val Decimal = "BigDecimal"
    val Unknown = "Unknown"
  }

  def pretty(shape: TypeShape, indent: Int = 0): String = TypeShape.pretty(shape)
}
