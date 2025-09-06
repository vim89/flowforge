package com.flowforge.core.types

object SchemaEvidence {
  // Re-export the contracts API here for legacy call-sites.
  type SchemaPolicy = com.flowforge.core.contracts.SchemaPolicy
  type SchemaConforms[A, R, P <: SchemaPolicy] = com.flowforge.core.contracts.SchemaConforms[A, R, P]

  // Re-export the term (object) so existing references to SchemaPolicy.Exact etc. continue to work.
  val SchemaPolicy: com.flowforge.core.contracts.SchemaPolicy.type = com.flowforge.core.contracts.SchemaPolicy
}
