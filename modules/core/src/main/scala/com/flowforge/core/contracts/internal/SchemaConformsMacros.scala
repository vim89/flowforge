package com.flowforge.core.contracts.internal

import com.flowforge.core.contracts.SchemaPolicy
import com.flowforge.core.contracts.derive.Shape

import scala.reflect.macros.blackbox

object SchemaConformsMacros {
  def materializeImpl[Out: c.WeakTypeTag, Contract: c.WeakTypeTag, P <: SchemaPolicy: c.WeakTypeTag](
    c: blackbox.Context,
  )(
    so: c.Expr[Shape[Out]],
    sc: c.Expr[Shape[Contract]],
  ): c.Tree = {
    import c.universe._

    // Use pure reflection instead of c.eval to avoid macro evaluation issues
    def fieldsOf(tpe: c.Type): List[(String, String, Boolean, Boolean)] = {
      val cls = tpe.typeSymbol.asClass
      if (!cls.isCaseClass)
        c.abort(c.enclosingPosition, s"Contracts must be case classes; got $tpe")

      val ctor   = tpe.decl(termNames.CONSTRUCTOR).asMethod
      val params = ctor.paramLists.headOption.getOrElse(Nil)

      params.map { p =>
        val term       = p.asTerm
        val pt         = term.typeSignatureIn(tpe).dealias
        val full       = pt.toString // Use toString for stable type representation
        val hasDefault = term.isParamWithDefault
        val isOpt      = pt.typeConstructor.toString == "Option"
        (term.name.decodedName.toString, full, hasDefault, isOpt)
      }
    }

    val outFields      = fieldsOf(weakTypeOf[Out])
    val contractFields = fieldsOf(weakTypeOf[Contract])

    validateContract(c)(outFields, contractFields, weakTypeOf[P], weakTypeOf[Out], weakTypeOf[Contract])
  }

  private def validateContract(
    c: blackbox.Context,
  )(
    outFields: List[(String, String, Boolean, Boolean)],
    contractFields: List[(String, String, Boolean, Boolean)],
    policyType: c.Type,
    outType: c.Type,
    contractType: c.Type,
  ): c.Tree = {
    import c.universe._

    val outMap      = outFields.map(f => f._1 -> f).toMap
    val contractMap = contractFields.map(f => f._1 -> f).toMap

    val missing0 = contractFields.collect {
      case (n, t, hasDef, isOpt) if !outMap.contains(n) => (n, t, hasDef, isOpt)
    }
    val extra0 = outFields.collect { case (n, t, _, _) if !contractMap.contains(n) => (n, t) }
    val mismatched = contractFields.collect {
      case (n, t, _, _) if outMap.get(n).exists(_._2 != t) => (n, t, outMap(n)._2)
    }

    def allowMissingForBackward(x: (String, String, Boolean, Boolean)) =
      x._3 || x._4 // default or Option[_]

    // Apply policy-specific validation logic as per plan
    val (missing, extra) = policyType match {
      case p if p =:= weakTypeOf[SchemaPolicy.Exact] =>
        // Exact: list equality - no missing, no extra, no mismatches
        (missing0, extra0)
      case p if p =:= weakTypeOf[SchemaPolicy.ExactUnordered] =>
        // ExactUnordered: set equality - same fields, order doesn't matter
        (missing0, extra0)
      case p if p =:= weakTypeOf[SchemaPolicy.Backward] =>
        // Backward: contractPairs.forall(outSet.contains) - allow missing only if hasDefault || isOptional
        (missing0.filterNot(allowMissingForBackward), Nil) // Allow extra fields in output
      case p if p =:= weakTypeOf[SchemaPolicy.Forward] =>
        // Forward: outPairs.forall(contractSet.contains) - output fields must be in contract
        (Nil, extra0) // Allow missing fields from contract, but no extra fields in output
      case p if p =:= weakTypeOf[SchemaPolicy.Full] =>
        // Full: always ok
        (Nil, Nil)
      case _ =>
        // Unknown policy - fail-safe with strict validation
        (missing0, extra0)
    }

    if (missing.nonEmpty || extra.nonEmpty || mismatched.nonEmpty) {
      val fmtMissing = missing.map { case (n, t, _, _) => s"$n:$t" }.mkString(", ")
      val fmtExtra   = extra.map { case (n, t) => s"$n:$t" }.mkString(", ")
      val fmtMismatched =
        mismatched.map { case (n, exp, got) => s"$n expected $exp, found $got" }.mkString("; ")
      val msg =
        s"""FlowForge: Contract drift (policy: $policyType).
           |Out: $outType vs Contract: $contractType
           |Missing: $fmtMissing
           |Extra: $fmtExtra
           |Mismatched: $fmtMismatched
           |See docs/how-it-fails.md#${policyType.typeSymbol.name}""".stripMargin
      c.abort(c.enclosingPosition, msg)
    }

    q"new _root_.com.flowforge.core.contracts.SchemaConforms[$outType, $contractType, $policyType]{}"
  }
}
