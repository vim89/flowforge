package com.flowforge.core.types

import shapeless.{ HList, LabelledGeneric }

/**
 * Compile-time schema tooling. Uses shapeless LabelledGeneric to expose a case class's labelled
 * field representation as an HList (field name -> type). This enables compile-time checks that a
 * pipeline's output type matches a sink's expected schema.
 */
object TypedSchema {
  type Repr[A] = HList

  /** Evidence that type `A` has labelled-generic representation `R` (an HList of fields).
    * If this implicit cannot be resolved, the types do not match and compilation fails.
    */
  type Matches[A, R <: HList] = LabelledGeneric.Aux[A, R]

  /** Helper summon; primarily for examples/tests. */
  def matches[A, R <: HList](implicit ev: Matches[A, R]): Matches[A, R] = ev
}

