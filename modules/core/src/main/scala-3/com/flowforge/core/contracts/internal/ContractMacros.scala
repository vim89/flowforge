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

  def conformsImpl[Out: Type, Contract: Type, P <: SchemaPolicy: Type](
    using
    q: Quotes,
  ): Expr[SchemaConforms[Out, Contract, P]] = {
    import q.reflect.*

    ShapeDiff
      .report(
        policyName = TypeRepr.of[P].show,
        outName = TypeRepr.of[Out].show,
        contractName = TypeRepr.of[Contract].show,
        flags = flagsOf[P],
        out = TypeShapes.of(TypeRepr.of[Out]),
        contract = TypeShapes.of(TypeRepr.of[Contract]),
      )
      .foreach(message => report.errorAndAbort(message))

    '{ new SchemaConforms[Out, Contract, P] {} }
  }

  /**
   * The comparison flags the policy type `P` stands for.
   *
   * Matched by subtyping rather than by the rendered type name, so that a policy named as the trait
   * (`SchemaPolicy.Backward`) and the same policy named as the case object (`SchemaPolicy.Backward.type`)
   * resolve to the same rules. The policy traits are disjoint, so at most one branch can match.
   */
  private def flagsOf[P <: SchemaPolicy: Type](using q: Quotes): ShapeDiff.Flags = {
    import q.reflect.*
    val requested = TypeRepr.of[P]

    val known: List[(TypeRepr, SchemaPolicy)] = List(
      TypeRepr.of[SchemaPolicy.Exact]            -> SchemaPolicy.Exact,
      TypeRepr.of[SchemaPolicy.ExactUnordered]   -> SchemaPolicy.ExactUnordered,
      TypeRepr.of[SchemaPolicy.ExactUnorderedCI] -> SchemaPolicy.ExactUnorderedCI,
      TypeRepr.of[SchemaPolicy.ExactOrdered]     -> SchemaPolicy.ExactOrdered,
      TypeRepr.of[SchemaPolicy.ExactOrderedCI]   -> SchemaPolicy.ExactOrderedCI,
      TypeRepr.of[SchemaPolicy.ExactByPosition]  -> SchemaPolicy.ExactByPosition,
      TypeRepr.of[SchemaPolicy.Backward]         -> SchemaPolicy.Backward,
      TypeRepr.of[SchemaPolicy.Forward]          -> SchemaPolicy.Forward,
      TypeRepr.of[SchemaPolicy.Full]             -> SchemaPolicy.Full,
    )

    known.collectFirst { case (tpe, policy) if requested <:< tpe => ShapeDiff.Flags.of(policy) }
      .getOrElse(ShapeDiff.Flags.strictest)
  }
}
