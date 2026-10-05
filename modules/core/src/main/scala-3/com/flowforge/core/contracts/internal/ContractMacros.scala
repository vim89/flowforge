package com.flowforge.core.contracts.internal

import com.flowforge.core.contracts.{ SchemaConforms, SchemaPolicy }

import scala.quoted.*

/**
 * Scala 3 half of compile-time contract validation.
 *
 * This only turns the two types into [[TypeShape]]s and hands them to [[ShapeDiff]], which owns the policy
 * rules and the error text. The Scala 2 macro in `src/main/scala-2` does the same, so the two versions cannot
 * drift in what they accept.
 */
object ContractMacros {

  def conformsImpl[Out: Type, Contract: Type, P <: SchemaPolicy: Type](using
    q: Quotes,
  ): Expr[SchemaConforms[Out, Contract, P]] = {
    import q.reflect.*

    ShapeDiff
      .report(
        policyName = TypeRepr.of[P].show,
        outName = TypeRepr.of[Out].show,
        contractName = TypeRepr.of[Contract].show,
        out = TypeShapes.of(TypeRepr.of[Out]),
        contract = TypeShapes.of(TypeRepr.of[Contract]),
      )
      .foreach(message => report.errorAndAbort(message))

    '{ new SchemaConforms[Out, Contract, P] {} }
  }
}
