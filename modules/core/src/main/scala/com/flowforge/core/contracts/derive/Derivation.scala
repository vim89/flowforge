package com.flowforge.core.contracts.derive

import com.flowforge.core.contracts.{ SchemaConforms, SchemaPolicy }

/**
 * Derivation facade to abstract over Scala 2 (Magnolia + macros) vs Scala 3 (Mirrors + inline + macros).
 */
trait DerivationBackend {
  implicit def schemaConforms[Out, Contract, P <: SchemaPolicy](
    implicit so: Shape[Out],
    sc: Shape[Contract],
  ): SchemaConforms[Out, Contract, P]
}

/**
 * Default backend for Scala 2.13 using Magnolia + blackbox macros.
 */
object Scala2MagnoliaBackend {
  import scala.language.experimental.macros
  implicit def schemaConforms[Out, Contract, P <: SchemaPolicy](
    implicit so: Shape[Out],
    sc: Shape[Contract],
  ): SchemaConforms[Out, Contract, P] =
    macro com.flowforge.core.contracts.internal.SchemaConformsMacros.materializeImpl[Out, Contract, P]
}

/**
 * Public facade. Swap the right backend per Scala version.
 */
object Derivation {
  // For Scala 2 build, delegate to Magnolia backend. Scala 3 build can redirect to Mirrors backend.
  val backend: DerivationBackend = new DerivationBackend {
    implicit def schemaConforms[Out, Contract, P <: SchemaPolicy](
      implicit so: Shape[Out],
      sc: Shape[Contract],
    ): SchemaConforms[Out, Contract, P] = Scala2MagnoliaBackend.schemaConforms[Out, Contract, P]
  }
}
