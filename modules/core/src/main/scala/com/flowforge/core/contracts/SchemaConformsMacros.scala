package com.flowforge.core.contracts

import scala.reflect.macros.blackbox

object SchemaConformsMacros {
  def materializeImpl[Out: c.WeakTypeTag, Contract: c.WeakTypeTag, P <: SchemaPolicy: c.WeakTypeTag](
    c: blackbox.Context,
  ): c.Tree = {
    import c.universe._

    // Extract field information directly from type symbols instead of trying to evaluate Shape instances
    def extractFieldsFromType(tpe: Type): List[(String, String, Boolean, Boolean)] = {
      if (tpe.typeSymbol.isClass && tpe.typeSymbol.asClass.isCaseClass) {
        val caseClassSym = tpe.typeSymbol.asClass
        
        // Get primary constructor instead of apply method
        val primaryConstructor = caseClassSym.primaryConstructor.asMethod
        
        primaryConstructor.paramLists.headOption.getOrElse(Nil).map { param =>
          val paramName = param.name.toString
          val paramType = param.info.substituteTypes(tpe.typeConstructor.typeParams, tpe.typeArgs)
          val paramTypeString = paramType.toString
          val hasDefault = param.asTerm.isParamWithDefault
          val isOptional = paramType.typeConstructor =:= typeOf[Option[_]].typeConstructor
          
          (paramName, paramTypeString, hasDefault, isOptional)
        }
      } else {
        c.abort(c.enclosingPosition, s"Type $tpe is not a case class")
      }
    }

    val outType = weakTypeOf[Out]
    val contractType = weakTypeOf[Contract]
    
    val outFields = extractFieldsFromType(outType)
    val contractFields = extractFieldsFromType(contractType)
    
    val outMap = outFields.map(f => f._1 -> f).toMap
    val contractMap = contractFields.map(f => f._1 -> f).toMap

    val missing0 = contractFields.collect { case (n, t, hasDef, isOpt) if !outMap.contains(n) => (n, t, hasDef, isOpt) }
    val extra0 = outFields.collect { case (n, t, _, _) if !contractMap.contains(n) => (n, t) }
    val mismatched = contractFields.collect {
      case (n, t, _, _) if outMap.get(n).exists(_._2 != t) => (n, t, outMap(n)._2)
    }

    def allowMissingForBackward(x: (String, String, Boolean, Boolean)) =
      x._3 || x._4 // default or Option[_]

    val policyType = weakTypeOf[P]
    val (missing, extra) = policyType match {
      case p if p =:= weakTypeOf[SchemaPolicy.Exact]          => (missing0, extra0)
      case p if p =:= weakTypeOf[SchemaPolicy.ExactUnordered] => (missing0, extra0)
      case p if p =:= weakTypeOf[SchemaPolicy.Backward] =>
        // Backward: Pipeline can have extra fields, but cannot miss required contract fields
        (missing0.filterNot(allowMissingForBackward), Nil) // Allow extra fields
      case p if p =:= weakTypeOf[SchemaPolicy.Forward] => 
        // Forward: Pipeline can have fewer fields, but all fields it has must match contract
        (Nil, Nil) // Allow missing fields from contract, don't care about extra
      case _ => (Nil, Nil) // Full compatibility - allow both
    }

    if (missing.nonEmpty || extra.nonEmpty || mismatched.nonEmpty) {
      val fmtMissing = missing.map { case (n, t, _, _) => s"$n:$t" }.mkString(", ")
      val fmtExtra = extra.map { case (n, t) => s"$n:$t" }.mkString(", ")
      val fmtMismatched = mismatched.map { case (n, exp, got) => s"$n expected $exp, found $got" }.mkString("; ")
      val msg =
        s"""FlowForge: Contract drift (policy: ${policyType}).
           |Out: ${outType} vs Contract: ${contractType}
           |Missing: $fmtMissing
           |Extra: $fmtExtra
           |Mismatched: $fmtMismatched
           |See docs/how-it-fails.md#${policyType.typeSymbol.name}""".stripMargin
      c.abort(c.enclosingPosition, msg)
    }

    q"new _root_.com.flowforge.core.contracts.SchemaConforms[${weakTypeOf[Out]}, ${weakTypeOf[Contract]}, ${weakTypeOf[P]}]{}"
  }
}
