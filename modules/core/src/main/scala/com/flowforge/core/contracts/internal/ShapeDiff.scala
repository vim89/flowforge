package com.flowforge.core.contracts.internal

import com.flowforge.core.contracts.SchemaPolicy
import com.flowforge.core.contracts.internal.TypeShape._

/**
 * Policy-aware comparison of two normalized shapes.
 *
 * This is all of contract checking that does not need a compiler: given two [[TypeShape]]s and a policy name
 * it says what drifted and renders the message. Each Scala version's macro only has to turn a type into a
 * `TypeShape` and hand the pair to [[ShapeDiff.report]], so the rules live in one place and can be tested
 * without compiling anything.
 */
object ShapeDiff {

  final case class Missing(path: String, field: FieldShape)
  final case class Extra(path: String, name: String)
  final case class Mismatch(
    path: String,
    expected: String,
    found: String)

  /** What a policy still considers drift, after its own relaxations are applied. */
  final case class Drift(
    missing: List[Missing],
    extra: List[Extra],
    mismatched: List[Mismatch]) {
    def isEmpty: Boolean = missing.isEmpty && extra.isEmpty && mismatched.isEmpty
  }

  /**
   * How a policy compares two shapes, flattened to booleans so the comparison needs no type-level queries.
   */
  final case class Flags(
    caseInsensitive: Boolean = false,
    orderedByName: Boolean = false,
    byPosition: Boolean = false,
    backward: Boolean = false,
    forward: Boolean = false,
    full: Boolean = false)

  object Flags {

    /**
     * The one mapping from policy to comparison behaviour.
     *
     * Total over the policy ADT on purpose. A policy nobody mapped is a compile error here rather than a
     * silent fallback to some default at the call site.
     */
    def of(policy: SchemaPolicy): Flags = policy match {
      case SchemaPolicy.Exact            => Flags()
      case SchemaPolicy.ExactUnordered   => Flags()
      case SchemaPolicy.ExactUnorderedCI => Flags(caseInsensitive = true)
      case SchemaPolicy.ExactOrdered     => Flags(orderedByName = true)
      case SchemaPolicy.ExactOrderedCI   => Flags(caseInsensitive = true, orderedByName = true)
      case SchemaPolicy.ExactByPosition  => Flags(byPosition = true)
      case SchemaPolicy.Backward         => Flags(backward = true)
      case SchemaPolicy.Forward          => Flags(forward = true)
      case SchemaPolicy.Full             => Flags(full = true)
    }

    /**
     * What to compare under when the policy type is not one of the known policies.
     *
     * Reached only for an abstract `P <: SchemaPolicy`, which is generic code that has not fixed its policy
     * yet. Strict matching is the safe answer: it can report drift a looser policy would have accepted, but
     * it never passes a producer the requested policy would have rejected.
     */
    val strictest: Flags = Flags()
  }

  /**
   * The compile error for the drift between `out` and `contract` under `flags`, or None when they conform.
   *
   * `flags` is passed in rather than looked up from `policyName`, which is only ever printed. Resolving a
   * policy from its rendered type name was fragile: a policy written as `SchemaPolicy.Backward.type` renders
   * differently from the same policy written as `SchemaPolicy.Backward`, and a lookup that missed used to
   * compare under the default rules instead of the requested ones.
   */
  def report(
    policyName: String,
    outName: String,
    contractName: String,
    flags: Flags,
    out: TypeShape,
    contract: TypeShape,
  ): Option[String] = {
    val drift = diff(flags, out, contract)
    if (drift.isEmpty) None else Some(render(policyName, outName, contractName, drift))
  }

  /** Compare two shapes and apply the policy's relaxations. */
  def diff(
    flags: Flags,
    out: TypeShape,
    contract: TypeShape,
  ): Drift = {
    val (missing, extra, mismatched) = compare(flags, "", out, contract)
    relax(flags, missing, extra, mismatched)
  }

  private def render(
    policyName: String,
    outName: String,
    contractName: String,
    drift: Drift,
  ): String = {
    def renderField(f: FieldShape): String = {
      val opt  = if (f.isOptional) " (optional)" else ""
      val dflt = if (f.hasDefault) " (default)" else ""
      s"${TypeShape.pretty(f.shape)}$opt$dflt"
    }

    val fmtMissing = drift.missing.map(m => s"${m.path}: ${renderField(m.field)}").mkString(", ")
    val fmtExtra   = drift.extra.map(_.path).mkString(", ")
    val fmtMismatches =
      drift.mismatched.map(m => s"${m.path} expected ${m.expected}, found ${m.found}").mkString("; ")

    s"""Compile-time contract drift (policy: $policyName).
       |Out: $outName vs Contract: $contractName
       |Missing attributes: $fmtMissing
       |Extra attributes: $fmtExtra
       |Mismatch attributes: $fmtMismatches
       |""".stripMargin
  }

  private def compare(
    flags: Flags,
    path: String,
    out: TypeShape,
    contract: TypeShape,
  ): (List[Missing], List[Extra], List[Mismatch]) =
    if (flags.byPosition) compareByPos(flags, path, out, contract)
    else if (flags.orderedByName) compareOrdered(flags, path, out, contract)
    else compareByName(flags, path, out, contract)

  private def compareByName(
    flags: Flags,
    path: String,
    out: TypeShape,
    contract: TypeShape,
  ): (List[Missing], List[Extra], List[Mismatch]) =
    (out, contract) match {
      case (PrimitiveShape(outName), PrimitiveShape(contractName)) =>
        if (outName == contractName) (Nil, Nil, Nil)
        else (Nil, Nil, List(Mismatch(path, contractName, outName)))

      case (SequenceShape(outElem), SequenceShape(contractElem)) =>
        compare(flags, s"$path[]", outElem, contractElem)

      case (OptionalShape(outInner), OptionalShape(contractInner)) =>
        compare(flags, s"$path?", outInner, contractInner)

      case (OptionalShape(_), other) =>
        (Nil, Nil, List(Mismatch(path, TypeShape.pretty(other), s"optional ${TypeShape.pretty(other)}")))

      case (other, OptionalShape(_)) =>
        (Nil, Nil, List(Mismatch(path, s"optional ${TypeShape.pretty(other)}", TypeShape.pretty(other))))

      case (MapShape(outKey, outVal), MapShape(contractKey, contractVal)) =>
        val keyMismatch =
          if (
            (flags.caseInsensitive && outKey.name.equalsIgnoreCase(contractKey.name)) ||
            (!flags.caseInsensitive && outKey.name == contractKey.name)
          ) Nil
          else List(Mismatch(s"$path<key>", contractKey.name, outKey.name))
        val (missing, extra, mismatches) = compare(flags, s"$path<value>", outVal, contractVal)
        (missing, extra, keyMismatch ++ mismatches)

      case (StructShape(outFields), StructShape(contractFields)) =>
        compareStructs(flags, path, outFields, contractFields)

      case (other, otherContract) =>
        (Nil, Nil, List(Mismatch(path, TypeShape.pretty(otherContract), TypeShape.pretty(other))))
    }

  private def compareOrdered(
    flags: Flags,
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
            if (flags.caseInsensitive) outField.name.equalsIgnoreCase(contractField.name)
            else outField.name == contractField.name
          if (nameOk) None
          else Some(Mismatch(s"$path@$i(name)", contractField.name, outField.name))
        }

        val nestedDiffs = (0 until min).flatMap { i =>
          val (outField, contractField) = (outFields(i), contractFields(i))
          val (missing, extra, mismatches) =
            compare(flags, pathOf(path, contractField.name), outField.shape, contractField.shape)
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

      case _ => compareByName(flags, path, out, contract)
    }

  private def compareByPos(
    flags: Flags,
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
          val mismatches = outFields.indices.flatMap { i =>
            val (outField, contractField) = (outFields(i), contractFields(i))
            val (missing, extra, nestedMismatches) =
              compare(flags, s"$path@$i", outField.shape, contractField.shape)
            // For by-position comparison, missing/extra become mismatches
            missing.map(m => Mismatch(s"$path@$i", TypeShape.pretty(m.field.shape), "missing")) ++
              extra.map(e => Mismatch(s"$path@$i", "expected", e.name)) ++
              nestedMismatches
          }
          (Nil, Nil, mismatches.toList)
        }

      case _ => compareByName(flags, path, out, contract)
    }

  private def compareStructs(
    flags: Flags,
    path: String,
    outFields: List[FieldShape],
    contractFields: List[FieldShape],
  ): (List[Missing], List[Extra], List[Mismatch]) = {
    val norm: String => String = s => if (flags.caseInsensitive) s.toLowerCase else s
    val outMap                 = outFields.map(f => norm(f.name) -> f).toMap
    val contractMap            = contractFields.map(f => norm(f.name) -> f).toMap

    val missing = contractFields.collect {
      case f if !outMap.contains(norm(f.name)) => Missing(pathOf(path, f.name), f)
    }
    val extra = outFields.collect {
      case f if !contractMap.contains(norm(f.name)) => Extra(pathOf(path, f.name), f.name)
    }

    val nestedDiffs = contractFields.flatMap { contractField =>
      outMap.get(norm(contractField.name)).toList.flatMap { outField =>
        val (m, e, x) =
          compare(flags, pathOf(path, contractField.name), outField.shape, contractField.shape)
        m.map(Left(_)) ++ e.map(found => Right(Left(found))) ++ x.map(found => Right(Right(found)))
      }
    }

    val allMissing    = missing ++ nestedDiffs.collect { case Left(m) => m }
    val allExtra      = extra ++ nestedDiffs.collect { case Right(Left(e)) => e }
    val allMismatches = nestedDiffs.collect { case Right(Right(m)) => m }

    (allMissing, allExtra, allMismatches)
  }

  private def pathOf(base: String, segment: String): String =
    if (base.isEmpty) segment else s"$base.$segment"

  /** Drop the differences the policy tolerates. */
  private def relax(
    flags: Flags,
    missing: List[Missing],
    extra: List[Extra],
    mismatches: List[Mismatch],
  ): Drift = {
    val keptMissing =
      if (flags.forward || flags.full) Nil // Forward allows missing contract fields
      else if (flags.backward) missing.filterNot(m => m.field.hasDefault || m.field.isOptional)
      else missing

    val keptExtra =
      if (flags.backward || flags.full) Nil // Backward allows extra producer fields
      else extra

    val keptMismatches = if (flags.full) Nil else mismatches

    Drift(keptMissing, keptExtra, keptMismatches)
  }
}
