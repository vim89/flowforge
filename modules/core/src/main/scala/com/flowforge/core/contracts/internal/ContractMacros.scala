package com.flowforge.core.contracts.internal

import com.flowforge.core.contracts.SchemaPolicy
import com.flowforge.core.contracts.internal.TypeShape._

import scala.reflect.macros.blackbox

/**
 * Macro implementation for compile-time contract validation.
 *
 * Follows FlowForge's idiomatic Scala standards:
 *   - Pure functional design with immutable data structures
 *   - Clean separation of concerns
 *   - Type-safe comparison strategies
 *   - Actionable error messages
 */
object ContractMacros {

  def conformsImpl[Out: c.WeakTypeTag, Contract: c.WeakTypeTag, P <: SchemaPolicy: c.WeakTypeTag](
    c: blackbox.Context,
  ): c.Tree = {
    import c.universe._

    // Type inspection utilities
    object TypeInspector {
      def isCaseClass(t: Type): Boolean = {
        val sym = t.typeSymbol
        sym.isClass && sym.asClass.isCaseClass
      }

      def appliedArgs(t: Type): List[Type] = t match {
        case TypeRef(_, _, args) => args
        case _                   => Nil
      }

      def optionArg(t: Type): Option[Type] =
        if (t <:< typeOf[Option[_]]) appliedArgs(t).headOption
        else None

      def seqArg(t: Type): Option[Type] = {
        val isSeqLike = t <:< typeOf[List[_]] || t <:< typeOf[Seq[_]] ||
          t <:< typeOf[Vector[_]] || t <:< typeOf[Array[_]] ||
          t <:< typeOf[Set[_]]
        if (isSeqLike) appliedArgs(t).headOption
        else None
      }

      def mapArgs(t: Type): Option[(Type, Type)] =
        if (t <:< typeOf[Map[_, _]]) {
          appliedArgs(t) match {
            case k :: v :: Nil => Some((k, v))
            case _             => None
          }
        } else None

      def isAtomicKey(t: Type): Boolean =
        t =:= typeOf[String] || t =:= typeOf[Int] || t =:= typeOf[Long] ||
          t =:= typeOf[Short] || t =:= typeOf[Byte] || t =:= typeOf[Boolean]
    }

    // TypeShape builder - pure functional approach
    object ShapeBuilder {
      def buildTypeShape(tpe: Type, inField: Boolean = false): TypeShape = {
        import TypeInspector._

        optionArg(tpe).map { inner =>
          // Field-level Option is captured on FieldShape.isOptional; avoid double-wrapping there.
          // Outside of field context (e.g., List[Option[A]]), preserve optionality as OptionalShape.
          if (inField) buildTypeShape(inner, inField = false)
          else TypeShape.OptionalShape(buildTypeShape(inner, inField = false))
        }.getOrElse {
          seqArg(tpe).map(elem => SequenceShape(buildTypeShape(elem))).getOrElse {
            mapArgs(tpe).map {
              case (k, v) =>
                if (!isAtomicKey(k)) {
                  c.abort(
                    c.enclosingPosition,
                    s"Unsupported Map key type: ${k.toString}. Allowed: String, Int, Long, Short, Byte, Boolean",
                  )
                }
                MapShape(PrimitiveShape(k.toString), buildTypeShape(v))
            }.getOrElse {
              if (isCaseClass(tpe)) {
                buildStructShape(tpe)
              } else {
                PrimitiveShape(tpe.toString)
              }
            }
          }
        }
      }

      private def buildStructShape(tpe: Type): StructShape = {
        val sym    = tpe.typeSymbol
        val ctor   = sym.asClass.primaryConstructor
        val params = ctor.asMethod.paramLists.flatten

        val fields = params.map { param =>
          val name       = param.name.toString
          val paramType  = tpe.member(param.name).asMethod.returnType
          val hasDefault = param.asTerm.isParamWithDefault
          val (underlyingType, isOptional) =
            TypeInspector.optionArg(paramType).fold((paramType, false))(t => (t, true))
          // For field-level shape, pass inField = true so Option is carried via isOptional flag
          FieldShape(name, buildTypeShape(underlyingType, inField = true), hasDefault, isOptional)
        }

        StructShape(fields)
      }
    }

    // Diff types for comparison results
    final case class Missing(path: String, field: FieldShape)
    final case class Extra(path: String, name: String)
    final case class Mismatch(
      path: String,
      expected: String,
      found: String)

    // Policy-based comparison strategies
    object PolicyComparator {
      val policyType = weakTypeOf[P]

      // Policy flags - use trait types for macro type checks
      val caseInsensitive = policyType <:< weakTypeOf[SchemaPolicy.ExactUnorderedCI] ||
        policyType <:< weakTypeOf[SchemaPolicy.ExactOrderedCI]
      val orderedByName = policyType <:< weakTypeOf[SchemaPolicy.ExactOrdered] ||
        policyType <:< weakTypeOf[SchemaPolicy.ExactOrderedCI]
      val byPosition = policyType <:< weakTypeOf[SchemaPolicy.ExactByPosition]
      val isBackward = policyType <:< weakTypeOf[SchemaPolicy.Backward]
      val isForward  = policyType <:< weakTypeOf[SchemaPolicy.Forward]
      val isFull     = policyType <:< weakTypeOf[SchemaPolicy.Full]

      val norm: String => String = s => if (caseInsensitive) s.toLowerCase else s

      def compare(
        path: String,
        out: TypeShape,
        contract: TypeShape,
      ): (List[Missing], List[Extra], List[Mismatch]) =
        if (byPosition) compareByPos(path, out, contract)
        else if (orderedByName) compareOrdered(path, out, contract)
        else compareByName(path, out, contract)

      private def compareByName(
        path: String,
        out: TypeShape,
        contract: TypeShape,
      ): (List[Missing], List[Extra], List[Mismatch]) =
        (out, contract) match {
          case (PrimitiveShape(outName), PrimitiveShape(contractName)) =>
            if (outName == contractName) (Nil, Nil, Nil)
            else (Nil, Nil, List(Mismatch(path, contractName, outName)))

          case (SequenceShape(outElem), SequenceShape(contractElem)) =>
            compare(s"$path[]", outElem, contractElem)

          case (OptionalShape(outInner), OptionalShape(contractInner)) =>
            compare(s"$path?", outInner, contractInner)

          case (OptionalShape(_), other) =>
            (Nil, Nil, List(Mismatch(path, TypeShape.pretty(other), s"optional ${TypeShape.pretty(other)}")))

          case (other, OptionalShape(_)) =>
            (Nil, Nil, List(Mismatch(path, s"optional ${TypeShape.pretty(other)}", TypeShape.pretty(other))))

          case (MapShape(outKey, outVal), MapShape(contractKey, contractVal)) =>
            val keyMismatch =
              if (
                (caseInsensitive && outKey.name.equalsIgnoreCase(contractKey.name)) ||
                (!caseInsensitive && outKey.name == contractKey.name)
              ) Nil
              else List(Mismatch(s"$path<key>", contractKey.name, outKey.name))
            val (missing, extra, mismatches) = compare(s"$path<value>", outVal, contractVal)
            (missing, extra, keyMismatch ++ mismatches)

          case (StructShape(outFields), StructShape(contractFields)) =>
            compareStructs(path, outFields, contractFields)

          case (out, contract) =>
            (Nil, Nil, List(Mismatch(path, TypeShape.pretty(contract), TypeShape.pretty(out))))
        }

      private def compareOrdered(
        path: String,
        out: TypeShape,
        contract: TypeShape,
      ): (List[Missing], List[Extra], List[Mismatch]) =
        (out, contract) match {
          case (StructShape(outFields), StructShape(contractFields)) =>
            val min = math.min(outFields.length, contractFields.length)
            val nameMismatches = (0 until min).flatMap { i =>
              val (outField, contractField) = (outFields(i), contractFields(i))
              val nameOk =
                if (caseInsensitive) outField.name.equalsIgnoreCase(contractField.name)
                else outField.name == contractField.name
              if (nameOk) None
              else Some(Mismatch(s"$path@$i(name)", contractField.name, outField.name))
            }

            val nestedDiffs = (0 until min).flatMap { i =>
              val (outField, contractField) = (outFields(i), contractFields(i))
              val (missing, extra, mismatches) =
                compare(pathOf(path, contractField.name), outField.shape, contractField.shape)
              missing.map(Left(_)) ++ extra.map(m => Right(Left(m))) ++ mismatches.map(m => Right(Right(m)))
            }

            val tailMissing =
              if (contractFields.length > outFields.length)
                contractFields.drop(min).map(f => Missing(pathOf(path, f.name), f))
              else Nil
            val tailExtra =
              if (outFields.length > contractFields.length)
                outFields.drop(min).map(f => Extra(pathOf(path, f.name), f.name))
              else Nil

            val allMissing = nestedDiffs.collect { case Left(m) => m }.toList ++ tailMissing
            val allExtra   = nestedDiffs.collect { case Right(Left(e)) => e }.toList ++ tailExtra
            val allMismatches = nestedDiffs.collect {
              case Right(Right(m)) => m
            }.toList ++ nameMismatches.toList

            (allMissing, allExtra, allMismatches)

          case _ => compareByName(path, out, contract)
        }

      private def compareByPos(
        path: String,
        out: TypeShape,
        contract: TypeShape,
      ): (List[Missing], List[Extra], List[Mismatch]) =
        (out, contract) match {
          case (StructShape(outFields), StructShape(contractFields)) =>
            // For ExactByPosition: different field count is a mismatch
            if (outFields.length != contractFields.length) {
              (
                Nil,
                Nil,
                List(Mismatch(path, s"${contractFields.length} fields", s"${outFields.length} fields")),
              )
            } else {
              // Compare types by position, ignoring field names
              val mismatches = (0 until outFields.length).flatMap { i =>
                val (outField, contractField) = (outFields(i), contractFields(i))
                val (missing, extra, nestedMismatches) =
                  compare(s"$path@$i", outField.shape, contractField.shape)
                // For by-position comparison, missing/extra become mismatches
                missing.map(m => Mismatch(s"$path@$i", TypeShape.pretty(m.field.shape), "missing")) ++
                  extra.map(e => Mismatch(s"$path@$i", "expected", e.name)) ++
                  nestedMismatches
              }
              (Nil, Nil, mismatches.toList)
            }

          case _ => compareByName(path, out, contract)
        }

      private def compareStructs(
        path: String,
        outFields: List[FieldShape],
        contractFields: List[FieldShape],
      ): (List[Missing], List[Extra], List[Mismatch]) = {
        val outMap      = outFields.map(f => norm(f.name) -> f).toMap
        val contractMap = contractFields.map(f => norm(f.name) -> f).toMap

        val missing = contractFields.collect {
          case f if !outMap.contains(norm(f.name)) => Missing(pathOf(path, f.name), f)
        }
        val extra = outFields.collect {
          case f if !contractMap.contains(norm(f.name)) => Extra(pathOf(path, f.name), f.name)
        }

        val nestedDiffs = contractFields.flatMap { contractField =>
          outMap.get(norm(contractField.name)).toList.flatMap { outField =>
            val (m, e, x) = compare(pathOf(path, contractField.name), outField.shape, contractField.shape)
            m.map(Left(_)) ++ e.map(m => Right(Left(m))) ++ x.map(m => Right(Right(m)))
          }
        }

        val allMissing    = missing ++ nestedDiffs.collect { case Left(m) => m }
        val allExtra      = extra ++ nestedDiffs.collect { case Right(Left(e)) => e }
        val allMismatches = nestedDiffs.collect { case Right(Right(m)) => m }

        (allMissing, allExtra, allMismatches)
      }

      private def pathOf(base: String, segment: String): String =
        if (base.isEmpty) segment else s"$base.$segment"

      // Policy-specific filtering
      def applyPolicyFilters(
        missing: List[Missing],
        extra: List[Extra],
        mismatches: List[Mismatch],
      ): (List[Missing], List[Extra], List[Mismatch]) = {
        val filteredMissing =
          if (isForward || isFull) Nil // Forward allows missing contract fields
          else if (isBackward) missing.filterNot(m => m.field.hasDefault || m.field.isOptional)
          else missing

        val filteredExtra =
          if (isBackward || isFull) Nil // Backward allows extra producer fields
          else if (isForward) extra     // Forward rejects extra fields
          else extra

        val filteredMismatches = if (isFull) Nil else mismatches

        (filteredMissing, filteredExtra, filteredMismatches)
      }
    }

    // Main comparison logic
    val outShape      = ShapeBuilder.buildTypeShape(weakTypeOf[Out])
    val contractShape = ShapeBuilder.buildTypeShape(weakTypeOf[Contract])

    val (missing0, extra0, mismatches0) = PolicyComparator.compare("", outShape, contractShape)
    val (missing, extra, mismatches)    = PolicyComparator.applyPolicyFilters(missing0, extra0, mismatches0)

    if (missing.nonEmpty || extra.nonEmpty || mismatches.nonEmpty) {
      def renderField(f: FieldShape): String = {
        val opt  = if (f.isOptional) " (optional)" else ""
        val dflt = if (f.hasDefault) " (default)" else ""
        s"${TypeShape.pretty(f.shape)}$opt$dflt"
      }

      val fmtMissing = missing.map(m => s"${m.path}: ${renderField(m.field)}").mkString(", ")
      val fmtExtra   = extra.map(_.path).mkString(", ")
      val fmtMismatches =
        mismatches.map(m => s"${m.path} expected ${m.expected}, found ${m.found}").mkString("; ")

      val errorMsg = s"""Compile-time contract drift (policy: ${weakTypeOf[P].toString}).
                        |Out: ${weakTypeOf[Out].toString} vs Contract: ${weakTypeOf[Contract].toString}
                        |Missing attributes: $fmtMissing
                        |Extra attributes: $fmtExtra
                        |Mismatch attributes: $fmtMismatches
                        |""".stripMargin

      c.abort(c.enclosingPosition, errorMsg)
    }

    q"new _root_.com.flowforge.core.contracts.SchemaConforms[${weakTypeOf[Out]}, ${weakTypeOf[Contract]}, ${weakTypeOf[P]}] {}"
  }
}
