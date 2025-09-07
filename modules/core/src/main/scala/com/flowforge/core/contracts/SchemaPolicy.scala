package com.flowforge.core.contracts

sealed trait SchemaPolicy

object SchemaPolicy {
  // Sealed traits for type-level usage in macros
  sealed trait Exact          extends SchemaPolicy
  sealed trait ExactUnordered extends SchemaPolicy
  sealed trait Backward       extends SchemaPolicy
  sealed trait Forward        extends SchemaPolicy
  sealed trait Full           extends SchemaPolicy

  // Case objects for runtime usage - implement the traits
  case object Exact          extends SchemaPolicy.Exact
  case object ExactUnordered extends SchemaPolicy.ExactUnordered
  case object Backward       extends SchemaPolicy.Backward
  case object Forward        extends SchemaPolicy.Forward
  case object Full           extends SchemaPolicy.Full
}
