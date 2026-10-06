// scalafix:off DisableSyntax.throw
package com.flowforge.core.contracts.derive

import magnolia1._

import scala.language.experimental.macros

/** Scala 2 derivation of [[Shape]], via Magnolia. Mixed into `object Shape`. */
trait ShapeDerivation {
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
}
