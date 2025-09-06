package com.flowforge.core.contracts

import com.flowforge.core.contracts.derive.Shape
import scala.annotation.implicitNotFound

@implicitNotFound("""
╔══════════════════════════════════════════════════════════════════════╗
║ 🚨 FlowForge Contract Drift (policy: ${P})                           ║
║ Out: ${Out}  vs  Contract: ${Contract}                               ║
║ Missing: ${Missing}  Extra: ${Extra}  Mismatched: ${Mismatched}      ║
║ docs/how-it-fails.md#${P}                                            ║
╚══════════════════════════════════════════════════════════════════════╝
""")
trait SchemaConforms[Out, Contract, P <: SchemaPolicy]

object SchemaConforms {
  import language.experimental.macros
  implicit def materialize[Out, Contract, P <: SchemaPolicy](
    implicit
    so: Shape[Out],
    sc: Shape[Contract],
  ): SchemaConforms[Out, Contract, P] =
    macro internal.SchemaConformsMacros.materializeImpl[Out, Contract, P]
}
