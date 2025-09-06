package com.flowforge.core.contracts.internal

import scala.reflect.macros.blackbox
import com.flowforge.core.contracts._
import com.flowforge.core.contracts.derive._

object SchemaConformsMacros {
  def materializeImpl[Out: c.WeakTypeTag, Contract: c.WeakTypeTag, P <: SchemaPolicy: c.WeakTypeTag](
    c: blackbox.Context,
  )(
    so: c.Expr[Shape[Out]],
    sc: c.Expr[Shape[Contract]],
  ): c.Tree = {
    import c.universe._

    def evalFields(e: c.Expr[Shape[_]]): List[(String, String, Boolean, Boolean)] =
      c.eval(c.Expr[List[Field]](q"${e.tree}.fields")).map(f => (f.name, f.tpe, f.hasDefault, f.isOptional))

    val out    = evalFields(so); val con = evalFields(sc)
    val outMap = out.map(f => f._1 -> f).toMap
    val conMap = con.map(f => f._1 -> f).toMap

    val missing0 = con.collect { case (n, t, hasDef, isOpt) if !outMap.contains(n) => (n, t, hasDef, isOpt) }
    val extra0   = out.collect { case (n, t, _, _) if !conMap.contains(n) => (n, t) }
    val mismatched = con.collect {
      case (n, t, _, _) if outMap.get(n).exists(_._2 != t) => (n, expected = t, found = outMap(n)._2)
    }

    def allowMissingForBackward(x: (String, String, Boolean, Boolean)) =
      x._3 || x._4 // default or Option[_]

    val (missing, extra) = weakTypeOf[P] match {
      case p if p =:= weakTypeOf[SchemaPolicy.Exact]          => (missing0, extra0)
      case p if p =:= weakTypeOf[SchemaPolicy.ExactUnordered] => (missing0, extra0)
      case p if p =:= weakTypeOf[SchemaPolicy.Backward] =>
        (missing0.filterNot(allowMissingForBackward), extra0)
      case p if p =:= weakTypeOf[SchemaPolicy.Forward] => (missing0, Nil)
      case _                                           => (Nil, Nil) // Full
    }

    if (missing.nonEmpty || extra.nonEmpty || mismatched.nonEmpty) {
      def fmtMissing = missing.map { case (n, t, _, _) => s"$n:$t" }.mkString(", ")
      def fmtExtra   = extra.map { case (n, t) => s"$n:$t" }.mkString(", ")
      def fmtMis     = mismatched.map { case (n, e, f) => s"$n expected $e, found $f" }.mkString("; ")
      val msg =
        s"""FlowForge: Contract drift (policy: ${weakTypeOf[P]}).
           |Out: ${weakTypeOf[Out]} vs Contract: ${weakTypeOf[Contract]}
           |Missing: ${fmtMissing}
           |Extra: ${fmtExtra}
           |Mismatched: ${fmtMis}
           |See docs/how-it-fails.md#${weakTypeOf[P].typeSymbol.name}""".stripMargin
      c.abort(c.enclosingPosition, msg)
    }

    q"new _root_.com.flowforge.core.contracts.SchemaConforms[${weakTypeOf[Out]}, ${weakTypeOf[Contract]}, ${weakTypeOf[P]}]{}"
  }
}
