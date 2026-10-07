package com.flowforge.core.contracts.internal

import com.flowforge.core.contracts.internal.TypeShape.*

import scala.quoted.*

/**
 * Turning a Scala 3 type into a [[TypeShape]].
 *
 * The two macros that need this, contract checking and `Shape` derivation, ask different questions of the
 * same type, so the reflection lives here and each of them keeps only its own job.
 */
object TypeShapes {

  /** The normalized shape of `tpe`. */
  def of(using q: Quotes)(tpe: q.reflect.TypeRepr): TypeShape = shapeOf(tpe, inField = false)

  /**
   * Primary-constructor parameters of a case class, as (name, type, whether it has a default).
   *
   * Names and types come from the case fields and defaults from the constructor, because only the constructor
   * parameters carry the default flag.
   */
  def params(using q: Quotes)(tpe: q.reflect.TypeRepr): List[(String, q.reflect.TypeRepr, Boolean)] = {
    import q.reflect.*
    val sym = tpe.typeSymbol
    val hasDefault = sym.primaryConstructor.paramSymss.flatten
      .filterNot(_.isTypeParam)
      .map(p => p.name -> p.flags.is(Flags.HasDefault))
      .toMap
    sym.caseFields.map(f => (f.name, tpe.memberType(f), hasDefault.getOrElse(f.name, false)))
  }

  /** Whether `tpe` is a case class, and so has a struct shape rather than being opaque. */
  def isCaseClass(using q: Quotes)(tpe: q.reflect.TypeRepr): Boolean = {
    import q.reflect.*
    val sym = tpe.typeSymbol
    sym.isClassDef && sym.flags.is(Flags.Case)
  }

  /** Whether `tpe` is `Option[_]`. */
  def isOption(using q: Quotes)(tpe: q.reflect.TypeRepr): Boolean =
    tpe.asType match {
      case '[Option[t]] => true
      case _            => false
    }

  private def shapeOf(using q: Quotes)(tpe: q.reflect.TypeRepr, inField: Boolean): TypeShape = {
    import q.reflect.*
    val t = tpe.dealias
    t.asType match {
      case '[Option[a]] =>
        // Field-level Option is captured on FieldShape.isOptional; avoid double-wrapping there.
        // Outside of field context (e.g., List[Option[A]]), preserve optionality as OptionalShape.
        val inner = shapeOf(TypeRepr.of[a], inField = false)
        if (inField) inner else OptionalShape(inner)

      case '[Map[k, v]] =>
        val key = TypeRepr.of[k].dealias
        if (!isAtomicKey(key)) {
          report.errorAndAbort(
            s"Unsupported Map key type: ${key.show}. Allowed: String, Int, Long, Short, Byte, Boolean",
          )
        }
        MapShape(PrimitiveShape(TypeShape.simpleName(key.show)), shapeOf(TypeRepr.of[v], inField = false))

      case '[Seq[a]]   => SequenceShape(shapeOf(TypeRepr.of[a], inField = false))
      case '[Set[a]]   => SequenceShape(shapeOf(TypeRepr.of[a], inField = false))
      case '[Array[a]] => SequenceShape(shapeOf(TypeRepr.of[a], inField = false))

      case _ => structOrLeaf(t)
    }
  }

  private def structOrLeaf(using q: Quotes)(tpe: q.reflect.TypeRepr): TypeShape = {
    import q.reflect.*
    // Tuples are checked first because every TupleN is itself a case class. Reading one as a struct of
    // `_1`, `_2` would make positional junk look like a named schema, so a tuple is rejected rather than
    // reinterpreted.
    if (tpe <:< TypeRepr.of[Tuple]) unsupportedLeaf(tpe)
    else if (isCaseClass(tpe)) StructShape(fieldsOf(tpe))
    else if (isSupportedLeaf(tpe)) PrimitiveShape(TypeShape.simpleName(tpe.show))
    else unsupportedLeaf(tpe)
  }

  private def isSupportedLeaf(using q: Quotes)(tpe: q.reflect.TypeRepr): Boolean = {
    import q.reflect.*
    tpe =:= TypeRepr.of[String] ||
    tpe =:= TypeRepr.of[Int] ||
    tpe =:= TypeRepr.of[Long] ||
    tpe =:= TypeRepr.of[Short] ||
    tpe =:= TypeRepr.of[Byte] ||
    tpe =:= TypeRepr.of[Double] ||
    tpe =:= TypeRepr.of[Float] ||
    tpe =:= TypeRepr.of[Boolean] ||
    tpe =:= TypeRepr.of[BigDecimal] ||
    tpe =:= TypeRepr.of[java.math.BigDecimal] ||
    tpe =:= TypeRepr.of[java.sql.Date] ||
    tpe =:= TypeRepr.of[java.time.LocalDate] ||
    tpe =:= TypeRepr.of[java.sql.Timestamp] ||
    tpe =:= TypeRepr.of[java.time.Instant] ||
    tpe =:= TypeRepr.of[java.time.LocalDateTime]
  }

  private def unsupportedLeaf(using q: Quotes)(tpe: q.reflect.TypeRepr): Nothing = {
    import q.reflect.*
    report.errorAndAbort(
      s"Unsupported structural leaf type in SchemaConforms derivation: ${tpe.show}. " +
        s"Supported leaf types: ${TypeShape.supportedLeafTypes}. " +
        "Supported container shapes: case classes, Option, List/Seq/Vector/Array/Set, and Map[atomic, _].",
    )
  }

  private def fieldsOf(using q: Quotes)(tpe: q.reflect.TypeRepr): List[FieldShape] = {
    import q.reflect.*
    params(tpe).map {
      case (name, fieldType, hasDefault) =>
        val (underlying, isOptional) = fieldType.dealias.asType match {
          case '[Option[a]] => (TypeRepr.of[a], true)
          case _            => (fieldType, false)
        }
        // For field-level shape, pass inField = true so Option is carried via isOptional flag
        FieldShape(name, shapeOf(underlying, inField = true), hasDefault, isOptional)
    }
  }

  private def isAtomicKey(using q: Quotes)(tpe: q.reflect.TypeRepr): Boolean = {
    import q.reflect.*
    tpe =:= TypeRepr.of[String] || tpe =:= TypeRepr.of[Int] || tpe =:= TypeRepr.of[Long] ||
    tpe =:= TypeRepr.of[Short] || tpe =:= TypeRepr.of[Byte] || tpe =:= TypeRepr.of[Boolean]
  }
}
