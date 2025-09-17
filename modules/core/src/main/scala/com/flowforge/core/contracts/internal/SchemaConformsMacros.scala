package com.flowforge.core.contracts.internal

import com.flowforge.core.contracts.SchemaPolicy
import com.flowforge.core.contracts.derive.Shape
import com.flowforge.core.contracts.internal.SchemaAST._

import scala.reflect.macros.blackbox

object SchemaConformsMacros {
  import scala.annotation.unused
  def materializeImpl[Out: c.WeakTypeTag, Contract: c.WeakTypeTag, P <: SchemaPolicy: c.WeakTypeTag](
    c: blackbox.Context,
  )(
    @unused so: c.Expr[Shape[Out]],
    @unused sc: c.Expr[Shape[Contract]],
  ): c.Tree = {
    import c.universe._

    // AST builder from c.Type
    object Ast {
      private val tOption = typeOf[Option[_]].typeConstructor
      private val tList   = typeOf[List[_]].typeConstructor
      private val tMap    = typeOf[Map[_, _]].typeConstructor

      private def isCaseClass(t: c.Type): Boolean = t.typeSymbol.isClass && t.typeSymbol.asClass.isCaseClass

      private def primTag(t: c.Type): String = {
        val n = t.typeSymbol.fullName
        if (n == "scala.Predef.String" || n == "java.lang.String" || n == "scala.String") PrimitiveTags.Str
        else if (n == "scala.Int") PrimitiveTags.Int
        else if (n == "scala.Long") PrimitiveTags.Long
        else if (n == "scala.Double") PrimitiveTags.Double
        else if (n == "scala.Float") PrimitiveTags.Float
        else if (n == "scala.Boolean") PrimitiveTags.Boolean
        else if (n == "java.time.Instant") PrimitiveTags.Instant
        else PrimitiveTags.Unknown
      }

      private def ctorParams(tpe: c.Type): List[Symbol] = {
        val ctor = tpe.decl(termNames.CONSTRUCTOR)
        if (ctor == NoSymbol) Nil
        else ctor.asMethod.paramLists.headOption.getOrElse(Nil)
      }

      def of(tpe0: c.Type): SchemaAST = {
        val tpe = tpe0.dealias
        val tc  = if (tpe.typeArgs.nonEmpty) tpe.typeConstructor else tpe

        if (tpe.typeArgs.nonEmpty && tc =:= tOption) {
          OptionT(of(tpe.typeArgs.head))
        } else if (tpe.typeArgs.nonEmpty && tc =:= tList) {
          ArrayT(of(tpe.typeArgs.head))
        } else if (tpe.typeArgs.size == 2 && tc =:= tMap) {
          MapT(of(tpe.typeArgs.head), of(tpe.typeArgs(1)))
        } else if (isCaseClass(tpe)) {
          val name   = tpe.typeSymbol.name.decodedName.toString
          val params = ctorParams(tpe)
          val fs = params.map { p =>
            val term       = p.asTerm
            val pt         = term.typeSignatureIn(tpe).dealias
            val hasDefault = term.isParamWithDefault
            val isOpt      = pt.typeArgs.nonEmpty && pt.typeConstructor =:= tOption
            Field(term.name.decodedName.toString, of(pt), hasDefault, isOpt)
          }
          Record(name, fs)
        } else {
          Primitive(primTag(tpe))
        }
      }
    }

    final case class Diff(
      missing: List[(String, SchemaAST)],
      extra: List[(String, SchemaAST)],
      mismatched: List[(String, SchemaAST, SchemaAST)])

    object Compare {
      private def normName(ci: Boolean)(s: String) = if (ci) s.toLowerCase else s

      private def fieldMap(rec: Record, ci: Boolean): Map[String, Field] =
        rec.fields.map(f => normName(ci)(f.name) -> f).toMap

      private def showType(ast: SchemaAST): String = ast match {
        case Primitive(tag)    => tag
        case OptionT(v)        => s"Option[${showType(v)}]"
        case ArrayT(e)         => s"List[${showType(e)}]"
        case MapT(k, v)        => s"Map[${showType(k)}, ${showType(v)}]"
        case Field(n, t, _, _) => s"$n: ${showType(t)}"
        case Record(n, fs)     => s"$n{${fs.map(f => s"${f.name}:${showType(f.tpe)}").mkString(",")}}"
      }

      private def compareRecord(
        out: Record,
        con: Record,
        policy: c.Type,
        path: String,
      ): Diff = {
        import c.universe._
        val ci = policy =:= weakTypeOf[SchemaPolicy.ExactUnorderedCI] || policy =:= weakTypeOf[
          SchemaPolicy.ExactOrderedCI,
        ]
        val outM = fieldMap(out, ci)
        val conM = fieldMap(con, ci)

        val missing = con.fields.collect {
          case f if !outM.contains(normName(ci)(f.name)) => (s"$path${f.name}", f.tpe)
        }
        val extra = out.fields.collect {
          case f if !conM.contains(normName(ci)(f.name)) => (s"$path${f.name}", f.tpe)
        }

        val commonNames = con.fields.map(_.name).filter(n => outM.contains(normName(ci)(n)))
        val mismatches = commonNames.flatMap { n =>
          val cF = conM(normName(ci)(n))
          val oF = outM(normName(ci)(n))
          val p  = s"$path${cF.name}."
          compareType(oF.tpe, cF.tpe, policy, p) match {
            case Diff(Nil, Nil, Nil) => Nil
            case Diff(mi, ex, mm) =>
              val selfTypeMismatch =
                (showType(oF.tpe) != showType(cF.tpe)) && (mi.isEmpty && ex.isEmpty && mm.isEmpty)
              if (selfTypeMismatch) List((s"$path${cF.name}", cF.tpe, oF.tpe)) else mm
          }
        }

        Diff(missing, extra, mismatches)
      }

      def compareType(
        out: SchemaAST,
        con: SchemaAST,
        policy: c.Type,
        path: String,
      ): Diff = {
        import c.universe._
        (out, con) match {
          case (OptionT(o), OptionT(c)) => compareType(o, c, policy, path)
          case (ArrayT(oe), ArrayT(ce)) => compareType(oe, ce, policy, path)
          case (MapT(ok, ov), MapT(ck, cv)) =>
            val d1 = compareType(ok, ck, policy, s"${path}@key.")
            val d2 = compareType(ov, cv, policy, s"${path}@value.")
            Diff(d1.missing ++ d2.missing, d1.extra ++ d2.extra, d1.mismatched ++ d2.mismatched)
          case (ro: Record, rc: Record) =>
            val d = compareRecord(ro, rc, policy, path)
            policy match {
              case p if p =:= weakTypeOf[SchemaPolicy.Backward] =>
                // Allow missing only if contract field is optional or has default
                val filteredMissing = d.missing.filterNot {
                  case (n, _) =>
                    val fname = n.stripPrefix(path)
                    rc.fields.exists(f => f.name == fname && (f.hasDefault || f.isOptional))
                }
                d.copy(missing = filteredMissing, extra = Nil) // extra ignored in Backward
              case p if p =:= weakTypeOf[SchemaPolicy.Forward] =>
                d.copy(missing = Nil) // missing ignored in Forward
              case p if p =:= weakTypeOf[SchemaPolicy.Full] => Diff(Nil, Nil, Nil)
              case p if p =:= weakTypeOf[SchemaPolicy.ExactByPosition] =>
                val lenOk = ro.fields.length == rc.fields.length
                val posMis = ro.fields
                  .zipAll(
                    rc.fields,
                    Field("<none>", Primitive("<none>"), false, false),
                    Field("<none>", Primitive("<none>"), false, false),
                  ).zipWithIndex.collect {
                    case ((of, cf), idx) if showType(of.tpe) != showType(cf.tpe) =>
                      (s"${path}@pos$idx", cf.tpe, of.tpe)
                  }
                val missing =
                  if (!lenOk && rc.fields.length > ro.fields.length)
                    rc.fields.drop(ro.fields.length).map(f => (s"$path${f.name}", f.tpe))
                  else Nil
                val extra =
                  if (!lenOk && ro.fields.length > rc.fields.length)
                    ro.fields.drop(rc.fields.length).map(f => (s"$path${f.name}", f.tpe))
                  else Nil
                Diff(missing, extra, posMis)
              case p
                  if p =:= weakTypeOf[SchemaPolicy.ExactOrdered] || p =:= weakTypeOf[
                    SchemaPolicy.ExactOrderedCI,
                  ] =>
                val namesOut = ro.fields.map(_.name)
                val namesCon = rc.fields.map(_.name)
                val orderMismatch =
                  if (namesOut != namesCon)
                    List((s"${path}__order__", Record("out", ro.fields), Record("con", rc.fields)))
                  else Nil
                d.copy(mismatched = orderMismatch ++ d.mismatched)
              case _ => d
            }
          case (Primitive(po), Primitive(pc)) if po == pc => Diff(Nil, Nil, Nil)
          case (o, c) => Diff(Nil, Nil, List((path.stripSuffix("."), c, o)))
        }
      }
    }

    val outAst = Ast.of(weakTypeOf[Out])
    val conAst = Ast.of(weakTypeOf[Contract])

    val diff = Compare.compareType(outAst, conAst, weakTypeOf[P], path = "")

    if (diff.missing.nonEmpty || diff.extra.nonEmpty || diff.mismatched.nonEmpty) {
      val fmtMissing =
        if (diff.missing.isEmpty) "<none>"
        else diff.missing.map { case (n, t) => s"$n:${SchemaAST.pretty(t)}" }.mkString(", ")
      val fmtExtra =
        if (diff.extra.isEmpty) "<none>"
        else diff.extra.map { case (n, t) => s"$n:${SchemaAST.pretty(t)}" }.mkString(", ")
      val fmtMismatched =
        if (diff.mismatched.isEmpty) "<none>"
        else
          diff.mismatched.map {
            case (n, exp, got) => s"$n expected ${SchemaAST.pretty(exp)} found ${SchemaAST.pretty(got)}"
          }.mkString(", ")

      val msg =
        s"""FlowForge: Contract drift (policy: %s).
           |Out: %s vs Contract: %s
           |Missing: %s
           |Extra: %s
           |Mismatched: %s
           |See docs/how-it-fails.md#%s""".stripMargin.format(
          weakTypeOf[P].toString,
          weakTypeOf[Out].toString,
          weakTypeOf[Contract].toString,
          fmtMissing,
          fmtExtra,
          fmtMismatched,
          weakTypeOf[P].typeSymbol.name.toString,
        )
      c.abort(c.enclosingPosition, msg)
    }

    q"new _root_.com.flowforge.core.contracts.SchemaConforms[${weakTypeOf[Out]}, ${weakTypeOf[Contract]}, ${weakTypeOf[P]}]{}"
  }
}
