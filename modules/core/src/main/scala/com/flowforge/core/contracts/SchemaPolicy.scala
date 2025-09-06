package com.flowforge.core.contracts
sealed trait SchemaPolicy
object SchemaPolicy {
  sealed trait Exact          extends SchemaPolicy
  sealed trait ExactUnordered extends SchemaPolicy
  sealed trait Backward       extends SchemaPolicy
  sealed trait Forward        extends SchemaPolicy
  sealed trait Full           extends SchemaPolicy
}
