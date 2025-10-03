package com.flowforge.core.contracts

/**
 * Schema comparison policies for compile-time contract validation.
 *
 * Clean enum-based design. Each policy defines how two schemas
 * should be compared for conformance at compile time.
 *
 * Policy behavior:
 *   - [[SchemaPolicy.Exact]]: Perfect match (unordered, case-insensitive)
 *   - [[SchemaPolicy.ExactUnorderedCI]]: Perfect match, case-insensitive names, unordered
 *   - [[SchemaPolicy.ExactOrdered]]: Perfect match with enforced field order
 *   - [[SchemaPolicy.ExactOrderedCI]]: Perfect match, case-insensitive + ordered
 *   - [[SchemaPolicy.ExactByPosition]]: Match by position only, names ignored
 *   - [[SchemaPolicy.Backward]]: Producer can have extras; missing allowed if optional/default
 *   - [[SchemaPolicy.Forward]]: Producer subset of contract; missing contract fields allowed
 *   - [[SchemaPolicy.Full]]: Escape hatch: accepts everything (development/testing)
 */
sealed trait SchemaPolicy

object SchemaPolicy {
  // Sealed traits for type-level usage in macros
  sealed trait Exact            extends SchemaPolicy
  sealed trait ExactUnorderedCI extends SchemaPolicy
  sealed trait ExactOrdered     extends SchemaPolicy
  sealed trait ExactOrderedCI   extends SchemaPolicy
  sealed trait ExactByPosition  extends SchemaPolicy
  sealed trait Backward         extends SchemaPolicy
  sealed trait Forward          extends SchemaPolicy
  sealed trait Full             extends SchemaPolicy

  // Case objects for runtime usage - implement the traits
  case object Exact            extends SchemaPolicy.Exact
  case object ExactUnorderedCI extends SchemaPolicy.ExactUnorderedCI
  case object ExactOrdered     extends SchemaPolicy.ExactOrdered
  case object ExactOrderedCI   extends SchemaPolicy.ExactOrderedCI
  case object ExactByPosition  extends SchemaPolicy.ExactByPosition
  case object Backward         extends SchemaPolicy.Backward
  case object Forward          extends SchemaPolicy.Forward
  case object Full             extends SchemaPolicy.Full
}
