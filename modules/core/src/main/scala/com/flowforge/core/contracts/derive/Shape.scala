package com.flowforge.core.contracts.derive

import magnolia1._
import scala.language.experimental.macros

final case class Field(
  name: String,
  tpe: String,
  hasDefault: Boolean,
  isOptional: Boolean)
trait Shape[A] { def fields: List[Field] }

object Shape {
  type Typeclass[T] = Shape[T]

  def join[T](ctx: CaseClass[Typeclass, T]): Shape[T] =
    new Shape[T] {
      def fields: List[Field] =
        ctx.parameters.toList.map { p =>
          Field(
            name = p.label,
            tpe = p.typeInfo.full,
            hasDefault = p.default.isDefined,
            isOptional = p.typeInfo.full.startsWith("scala.Option["),
          )
        }
    }

  def split[T](ctx: SealedTrait[Typeclass, T]): Shape[T] =
    new Shape[T] { def fields: List[Field] = ctx.subtypes.head.typeclass.fields }

  implicit def gen[T]: Shape[T] = macro Magnolia.gen[T]
}
