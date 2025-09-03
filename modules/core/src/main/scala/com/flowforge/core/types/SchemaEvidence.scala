package com.flowforge.core.types

import shapeless.{ HList, LabelledGeneric }
import shapeless.ops.hlist.Align
import scala.annotation.implicitNotFound

/**
 * Evidence that a case class A has a labelled-generic representation R. This is used as a human-friendly gate
 * so compile errors explain schema mismatches clearly.
 */
@implicitNotFound(
  "FlowForge: Pipeline type ${A} does not match the required contract schema. Make sure fields and types align exactly with the contract (order and names matter in this phase).",
)
trait SchemaEq[A, R <: HList]

object SchemaEq {
  implicit def fromLabelledGeneric[A, R <: HList](
    implicit
    L: LabelledGeneric.Aux[A, R],
  ): SchemaEq[A, R] = new SchemaEq[A, R] {}
}

/** Policy marker types for schema conformance at compile time. */
sealed trait SchemaPolicy
object SchemaPolicy {
  sealed trait Exact          extends SchemaPolicy
  sealed trait ExactUnordered extends SchemaPolicy // same fields/types, order-insensitive
}

/** Dispatcher for policy-driven conformance at compile time. */
@implicitNotFound(
  "FlowForge: Pipeline type does not conform to the required contract under the selected policy.",
)
trait SchemaConforms[A, R <: HList, P <: SchemaPolicy]
object SchemaConforms {
  import SchemaPolicy._

  implicit def exact[A, R <: HList](implicit eq: SchemaEq[A, R]): SchemaConforms[A, R, Exact] =
    new SchemaConforms[A, R, Exact] {}

  /** Exact match ignoring field order: require subset in both directions. */
  implicit def exactUnordered[A, RA <: HList, R <: HList](
    implicit
    L: LabelledGeneric.Aux[A, RA],
    align: Align[RA, R],
  ): SchemaConforms[A, R, ExactUnordered] = new SchemaConforms[A, R, ExactUnordered] {}
}
