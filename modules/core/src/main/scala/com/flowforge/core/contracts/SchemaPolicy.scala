package com.flowforge.core.contracts

/**
 * Compile‑time schema evolution policy used by [[SchemaConforms]].
 *
 * Policies describe how strictly the output type `Out` must match the declared contract `Contract`:
 *   - [[SchemaPolicy.Exact]]: Names and types must match exactly.
 *   - [[SchemaPolicy.Backward]]: `Out` may add optional fields; consumers of older contracts still read it.
 *   - [[SchemaPolicy.Forward]]: `Out` may omit non‑required fields; newer consumers still read older data.
 *   - [[SchemaPolicy.Full]]: Most permissive; use with care.
 *
 * Case objects are for runtime values; traits are used at the type level for macro resolution.
 */
sealed trait SchemaPolicy

object SchemaPolicy {
  // Sealed traits for type-level usage in macros
  sealed trait Exact            extends SchemaPolicy
  sealed trait ExactUnordered   extends SchemaPolicy
  sealed trait ExactOrdered     extends SchemaPolicy
  sealed trait ExactUnorderedCI extends SchemaPolicy
  sealed trait ExactOrderedCI   extends SchemaPolicy
  sealed trait ExactByPosition  extends SchemaPolicy
  sealed trait Backward         extends SchemaPolicy
  sealed trait Forward          extends SchemaPolicy
  sealed trait Full             extends SchemaPolicy

  // Case objects for runtime usage - implement the traits
  case object Exact            extends SchemaPolicy.Exact
  case object ExactUnordered   extends SchemaPolicy.ExactUnordered
  case object ExactOrdered     extends SchemaPolicy.ExactOrdered
  case object ExactUnorderedCI extends SchemaPolicy.ExactUnorderedCI
  case object ExactOrderedCI   extends SchemaPolicy.ExactOrderedCI
  case object ExactByPosition  extends SchemaPolicy.ExactByPosition
  case object Backward         extends SchemaPolicy.Backward
  case object Forward          extends SchemaPolicy.Forward
  case object Full             extends SchemaPolicy.Full
}
