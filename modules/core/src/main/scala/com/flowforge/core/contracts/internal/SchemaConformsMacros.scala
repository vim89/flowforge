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

    def allowMissingForBackward(x: (String, String, Boolean, Boolean)) = x._3 || x._4

    // Helpers for name normalization
    def normName(ci: Boolean)(s: String) = if (ci) s.toLowerCase else s

    // Compute default diffs (name-sensitive, unordered)
    val outMapByName      = outFields.map(f => f._1 -> f).toMap
    val contractMapByName = contractFields.map(f => f._1 -> f).toMap
    val missing0 = contractFields.collect { case (n, t, hasDef, isOpt) if !outMapByName.contains(n) => (n, t, hasDef, isOpt) }
    val extra0   = outFields.collect { case (n, t, _, _) if !contractMapByName.contains(n)           => (n, t) }
    val mismatchedNameTyped = contractFields.collect {
      case (n, t, _, _) if outMapByName.get(n).exists(_._2 != t) => (n, t, outMapByName(n)._2)
    }

    // Policy-specific evaluation
    val (missing, extra, mismatched) = policyType match {
      case p if p =:= weakTypeOf[SchemaPolicy.Exact] || p =:= weakTypeOf[SchemaPolicy.ExactUnordered] =>
        (missing0, extra0, mismatchedNameTyped)

      case p if p =:= weakTypeOf[SchemaPolicy.ExactUnorderedCI] =>
        val outMapCI      = outFields.map { case (n, t, d, o) => normName(true)(n) -> (n, t, d, o) }.toMap
        val contractMapCI = contractFields.map { case (n, t, d, o) => normName(true)(n) -> (n, t, d, o) }.toMap
        val missing = contractFields.collect { case (n, t, d, o) if !outMapCI.contains(normName(true)(n)) => (n, t, d, o) }
        val extra   = outFields.collect { case (n, t, _, _) if !contractMapCI.contains(normName(true)(n)) => (n, t) }
        val mis = contractFields.collect {
          case (n, t, _, _) =>
            outMapCI.get(normName(true)(n)) match {
              case Some((_, outT, _, _)) if outT != t => Some((n, t, outT))
              case _                                  => None
            }
        }.flatten
        (missing, extra, mis)

      case p if p =:= weakTypeOf[SchemaPolicy.ExactOrdered] || p =:= weakTypeOf[SchemaPolicy.ExactOrderedCI] =>
        val ci = p =:= weakTypeOf[SchemaPolicy.ExactOrderedCI]
        val namesOut      = outFields.map(_._1).map(normName(ci))
        val namesContract = contractFields.map(_._1).map(normName(ci))
        val orderOk       = namesOut == namesContract
        val typeMis = outFields.zipAll(contractFields, ("<none>", "<none>", false, false), ("<none>", "<none>", false, false)).collect {
          case ((no, to, _, _), (nc, tc, _, _)) if normName(ci)(no) == normName(ci)(nc) && to != tc => (nc, tc, to)
        }
        val missing = if (namesOut.size < namesContract.size) contractFields.drop(namesOut.size) else Nil
        val extra   = if (namesOut.size > namesContract.size) outFields.drop(namesContract.size).map { case (n, t, _, _) => (n, t) } else Nil
        val mis     = if (!orderOk) (List(("__order__", namesContract.mkString("[", ",", "]"), namesOut.mkString("[", ",", "]"))) ++ typeMis) else typeMis
        (missing, extra, mis)

      case p if p =:= weakTypeOf[SchemaPolicy.ExactByPosition] =>
        val lenOk    = outFields.length == contractFields.length
        val typeMisP = outFields.zipAll(contractFields, ("<none>", "<none>", false, false), ("<none>", "<none>", false, false)).zipWithIndex.collect {
          case (((_, to, _, _), (_, tc, _, _)), idx) if to != tc => (s"@pos$idx", tc, to)
        }
        val missing = if (!lenOk && contractFields.length > outFields.length) contractFields.drop(outFields.length) else Nil
        val extra   = if (!lenOk && outFields.length > contractFields.length) outFields.drop(contractFields.length).map { case (n, t, _, _) => (n, t) } else Nil
        (missing, extra, typeMisP)

      case p if p =:= weakTypeOf[SchemaPolicy.Backward] =>
        (missing0.filterNot(allowMissingForBackward), Nil, mismatchedNameTyped)

      case p if p =:= weakTypeOf[SchemaPolicy.Forward] =>
        (Nil, extra0, mismatchedNameTyped)

      case p if p =:= weakTypeOf[SchemaPolicy.Full] =>
        (Nil, Nil, Nil)

      case _ =>
        (missing0, extra0, mismatchedNameTyped)
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
