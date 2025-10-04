// scalafix:off DisableSyntax.throw
package com.flowforge.core.contracts.derive

import magnolia1._

import scala.language.experimental.macros

/**
 * Shape derivation using Magnolia for compile-time field metadata extraction. Fixed implementation following
 * End-to-End-Compile-Issues.md
 */
final case class Field(
  name: String,
  tpe: String,
  hasDefault: Boolean,
  isOptional: Boolean)
trait Shape[T] { def fields: List[Field] }

object Shape {
  type Typeclass[T] = Shape[T]

  def join[T](caseClass: CaseClass[Typeclass, T]): Shape[T] =
    new Shape[T] {
      val fields: List[Field] =
        caseClass.parameters.toList.map { p =>
          val full = p.typeName.full // Magnolia 1 (Scala 2) exposes typeName
          Field(
            name = p.label,
            tpe = full,
            hasDefault = p.default.isDefined,
            isOptional = full.startsWith("scala.Option["),
          )
        }
    }

  // Keep v1.0 scope to products only; abort on sums for now
  def split[T](ctx: SealedTrait[Typeclass, T]): Typeclass[T] =
    throw new IllegalArgumentException(
      s"Shape supports case classes only for now (got sum type ${ctx.typeName.full})",
    )

  implicit def gen[T]: Shape[T] = macro Magnolia.gen[T]

  // Primitive instances
  implicit val stringShape: Shape[String]   = new Shape[String] { val fields = List.empty }
  implicit val intShape: Shape[Int]         = new Shape[Int] { val fields = List.empty }
  implicit val longShape: Shape[Long]       = new Shape[Long] { val fields = List.empty }
  implicit val booleanShape: Shape[Boolean] = new Shape[Boolean] { val fields = List.empty }
  implicit val doubleShape: Shape[Double]   = new Shape[Double] { val fields = List.empty }

  // Collection instances
  implicit def listShape[A]: Shape[List[A]]     = new Shape[List[A]] { val fields = List.empty }
  implicit def mapShape[K, V]: Shape[Map[K, V]] = new Shape[Map[K, V]] { val fields = List.empty }
  implicit def optionShape[A]: Shape[Option[A]] = new Shape[Option[A]] { val fields = List.empty }
}
