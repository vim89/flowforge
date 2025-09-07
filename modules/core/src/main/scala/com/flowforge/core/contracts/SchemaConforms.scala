package com.flowforge.core.contracts

import scala.annotation.implicitNotFound

@implicitNotFound("""
╔══════════════════════════════════════════════════════════════════════╗
║ 🚨 FlowForge Contract Drift (policy: ${P})                           ║
║ Out: ${Out}  vs  Contract: ${Contract}                               ║
║ Schema mismatch detected - compilation failed.                       ║
║ See docs/how-it-fails.md for policy-specific error details.          ║
╚══════════════════════════════════════════════════════════════════════╝
""")
trait SchemaConforms[Out, Contract, P <: SchemaPolicy]

object SchemaConforms {
  import language.experimental.macros
  implicit def materialize[Out, Contract, P <: SchemaPolicy]: SchemaConforms[Out, Contract, P] =
    macro SchemaConformsMacros.materializeImpl[Out, Contract, P]
}
