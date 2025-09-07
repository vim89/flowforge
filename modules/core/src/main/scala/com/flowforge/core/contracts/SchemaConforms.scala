package com.flowforge.core.contracts

import scala.annotation.implicitNotFound
import com.flowforge.core.contracts.derive.Shape

@implicitNotFound("""
FlowForge: Contract drift (policy: ${P})
Out: ${Out} vs Contract: ${Contract}
Missing: ... | Extra: ... | Mismatched: ...
""")
trait SchemaConforms[Out, Contract, P <: SchemaPolicy]

object SchemaConforms {
  import scala.language.experimental.macros
  implicit def materialize[Out, Contract, P <: SchemaPolicy](
    implicit so: Shape[Out],
    sc: Shape[Contract],
  ): SchemaConforms[Out, Contract, P] =
    macro internal.SchemaConformsMacros.materializeImpl[Out, Contract, P]
}
