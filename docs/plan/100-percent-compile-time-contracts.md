Below is a surgical, end-to-end plan with concrete diffs, file-by-file guidance, and CI gates. It keeps your current compile-time contract story intact (pipelines fail to build on schema drift), while removing the HList/LabelledGeneric machinery.

⸻

What I found (where shapeless is used today)

These files currently import or depend on shapeless:
•	project/Dependencies.scala
•	project/ContractsCodegen.scala (mentions in comments/imports)
•	modules/core/src/main/scala/com/flowforge/core/types/PipelineBuilder2.scala
•	modules/core/src/main/scala/com/flowforge/core/types/TypedIO.scala
•	modules/core/src/main/scala/com/flowforge/core/types/SchemaWitness.scala
•	modules/core/src/main/scala/com/flowforge/core/types/TypedSchema.scala
•	modules/core/src/main/scala/com/flowforge/core/types/DataTypes.scala (HList-typed TypedSource/TypedSink)
•	modules/core/src/main/scala/com/flowforge/core/types/SchemaEvidence.scala
•	modules/contracts/src/main/scala/com/flowforge/framework/TypedContracts.scala
•	modules/contracts/src/main/scala/com/flowforge/contracts/CompileTimeContracts.scala
•	modules/contracts/src/test/.../SchemaWitnessCompileFailSpec.scala (uses illTyped, HNil, etc.)

The hot spots are the HList/LabelledGeneric-based representations and the Align/Intersection tricks for “unordered exact” and subset/superset checks.

⸻

Why Magnolia (Scala 2) is a good fit
•	Magnolia 1 (scala2 branch) exposes field labels and structure via CaseClass/Param - we can read param names & types during derivation and materialize a tiny shape typeclass. Then, use a small macro to abort compilation with your rich error when shapes violate a policy.  ￼
•	For Scala 2 projects, use:
libraryDependencies += "com.softwaremill.magnolia1_2" %% "magnolia" % "1.1.10" (artifact family for Scala 2; the README shows the exact coordinates).  ￼
•	Later, on Scala 3, you can drop this backend and plug in Mirrors + inline with the same facade. (The repo’s main README shows the Scala 3 coordinates and modern API; we’re future-proofing the facade now.)  ￼

For context on what we’re replacing: shapeless LabelledGeneric/HList and friends, including Align and Intersection.  ￼

⸻

Clean-sweep migration plan (safe + reversible)

This is a single coherent migration but intentionally staged so you never lose compile-time guarantees. No runtime regressions, and the DX remains “compile-fail on drift.”

0) Build setup

Add Magnolia + macros; keep shapeless temporarily (until step 6).
•	project/Dependencies.scala
•	Remove later: "com.chuusai" %% "shapeless" % x.y.z
•	Add now:

val magnolia2 = "1.1.10" // Scala 2 line
// in libraryDeps:
"com.softwaremill.magnolia1_2" %% "magnolia" % magnolia2,
"org.scala-lang" % "scala-reflect" % scalaVersion.value % Provided

Magnolia (scala2 branch) uses macros; scala-reflect is the standard way to report compile-time errors via c.abort.  ￼ ￼

	•	Keep your current test stack (ScalaTest has assertDoesNotCompile/assertTypeError we can use after we drop illTyped).  ￼

⸻

1) Introduce a derivation facade (Scala-3 ready)

Create a tiny module (or package) that hides the backend:

// modules/core/src/main/scala/com/flowforge/core/contracts/derive/Shape.scala
package com.flowforge.core.contracts.derive

// Value-level description of a record's fields (no runtime cost after inlining)
final case class Field(name: String, tpe: String, hasDefault: Boolean, isOptional: Boolean)
trait Shape[A] { def fields: List[Field] }

object Shape {
// Magnolia 1 (Scala 2) backend; the object name & API remain the same for Scala 3 later.
import language.experimental.macros
import magnolia1._
type Typeclass[T] = Shape[T]

def join[T](ctx: CaseClass[Typeclass, T]): Shape[T] =
new Shape[T] {
def fields: List[Field] =
ctx.parameters.toList.map { p =>
Field(
name        = p.label,
tpe         = p.typeInfo.full,
hasDefault  = p.default.isDefined,
isOptional  = p.typeInfo.full.startsWith("scala.Option[")
)
}
}

def split[T](ctx: SealedTrait[Typeclass, T]): Shape[T] =
new Shape[T] {
def fields: List[Field] =
// not expected for pipeline records; flatten subtypes if you need sum-types later
ctx.subtypes.head.typeclass.fields
}

implicit def gen[T]: Shape[T] = macro Magnolia.gen[T]
}

	•	This gives us labels and enough metadata (default/optional) to implement Exact, ExactUnordered, Backward, Forward, Full policies.

⸻

2) Replace HList schema kinds with record types (public API)

A. TypedSource/TypedSink stop using HList
•	Before (HList-encoded schema):

final case class TypedSink[R <: HList](underlying: DataSink)
final case class TypedSource[R <: HList](underlying: DataSource)

	•	After (record type R is the case class; the schema is carried by Shape[R]):

final case class TypedSink[R](underlying: DataSink)(implicit val shape: Shape[R])
final case class TypedSource[R](underlying: DataSource)(implicit val shape: Shape[R])

Update TypedIO.scala constructors similarly (drop HList bounds).

B. CompileTimeContracts and TypedContracts
•	Replace LabelledGeneric.Aux[A, R] guards with Shape[A] evidence.

final case class TypedContract[A](dc: DataContract[A])(implicit val shape: Shape[A])


⸻

3) Re-implement policy evidence without shapeless

You currently have:
•	SchemaWitness using LabelledGeneric + Intersection for subset checks.
•	SchemaEvidence using Align for “unordered exact”.

We will introduce SchemaConforms[Out, Contract, P] as a macro materializer that uses two Shape instances and aborts on policy violations with your big ASCII error.

// modules/core/src/main/scala/com/flowforge/core/contracts/SchemaConforms.scala
package com.flowforge.core.contracts

import com.flowforge.core.contracts.derive.Shape
import scala.annotation.implicitNotFound

sealed trait SchemaPolicy
object SchemaPolicy {
sealed trait Exact extends SchemaPolicy
sealed trait ExactUnordered extends SchemaPolicy
sealed trait Backward extends SchemaPolicy
sealed trait Forward extends SchemaPolicy
sealed trait Full extends SchemaPolicy
}

@implicitNotFound("""
╔══════════════════════════════════════════════════════════════════════╗
║ 🚨 FlowForge Contract Drift (policy: ${P})                           ║
║ Out: ${Out}  vs  Contract: ${Contract}                               ║
║ Missing: ${Missing}  Extra: ${Extra}  Mismatched: ${Mismatched}      ║
║ See docs/how-it-fails.md#${P}                                        ║
╚══════════════════════════════════════════════════════════════════════╝
""")
trait SchemaConforms[Out, Contract, P <: SchemaPolicy]

object SchemaConforms {
import language.experimental.macros
implicit def materialize[Out, Contract, P <: SchemaPolicy](implicit
so: Shape[Out], sc: Shape[Contract]
): SchemaConforms[Out, Contract, P] =
macro SchemaConformsMacros.materializeImpl[Out, Contract, P]
}

And the macro (sketch):

// modules/core/src/main/scala/com/flowforge/core/contracts/internal/SchemaConformsMacros.scala
package com.flowforge.core.contracts.internal

import scala.reflect.macros.blackbox
import com.flowforge.core.contracts._
import com.flowforge.core.contracts.derive._

object SchemaConformsMacros {
def materializeImpl[Out: c.WeakTypeTag, Contract: c.WeakTypeTag, P <: SchemaPolicy: c.WeakTypeTag]
(c: blackbox.Context)(so: c.Expr[Shape[Out]], sc: c.Expr[Shape[Contract]]): c.Tree = {

    import c.universe._

    // Extract field lists (pure values baked at compile-time)
    val outFields  = extractFields(c)(so)
    val conFields  = extractFields(c)(sc)

    val (missing, extra, mismatched) = diffForPolicy[P](c)(outFields, conFields)

    if (missing.nonEmpty || extra.nonEmpty || mismatched.nonEmpty) {
      val msg =
        s"""
           |FlowForge: Contract drift (policy: ${weakTypeOf[P]})
           |Out: ${weakTypeOf[Out]} vs Contract: ${weakTypeOf[Contract]}
           |Missing: ${missing.mkString(", ")}
           |Extra: ${extra.mkString(", ")}
           |Mismatched: ${mismatched.mkString(", ")}
           |See docs/how-it-fails.md#${weakTypeOf[P].typeSymbol.name}
         """.stripMargin

      c.abort(c.enclosingPosition, msg) // hard compile error, Scala 2 macros
    }

    q"new SchemaConforms[${weakTypeOf[Out]}, ${weakTypeOf[Contract]}, ${weakTypeOf[P]}]{}"
}

private def extractFields(c: blackbox.Context)(shape: c.Expr[Shape[_]]): List[(String, String, Boolean, Boolean)] = {
import c.universe._
// Evaluate the `fields` on the `Shape` value at macro expand time (pure & stable)
shape.tree match {
case _ =>
// Best-effort: call fields via c.eval / c.Expr if inlined; otherwise punt to stringy types
// (In practice, Magnolia inlines enough for simple records.)
c.eval(c.Expr[List[derive.Field]](q"$shape.fields")).map(f => (f.name, f.tpe, f.hasDefault, f.isOptional))
}
}

private def diffForPolicy[P](c: blackbox.Context)(
out: List[(String, String, Boolean, Boolean)],
con: List[(String, String, Boolean, Boolean)]
): (List[String], List[String], List[String]) = {
import c.universe._
val outMap = out.map(f => f._1 -> f).toMap
val conMap = con.map(f => f._1 -> f).toMap

    val missing = con.collect {
      case (n, t, hasDef, isOpt) if !outMap.contains(n) =>
        // Backward/Full can allow missing if (has default or optional)
        s"$n:$t"
    }

    val extra = out.collect { case (n, t, _, _) if !conMap.contains(n) => s"$n:$t" }

    val mismatched = con.collect {
      case (n, t, _, _) if outMap.get(n).exists(_._2 != t) => s"$n expected $t, found ${outMap(n)._2}"
    }

    // You’d switch on P here (by type) to filter missing/extra the way your policies require.
    (missing, extra, mismatched)
}
}

	•	The important part: c.abort gives you hard compile errors, just like your current shapeless evidence, with custom messages.  ￼
	•	This completely removes the need for Align/Intersection gymnastics. Labels & optional/default semantics are available from Magnolia’s CaseClass/Param.  ￼

Result: Same UX as today (bad pipelines won’t compile), but the schema representation is plain record types + Magnolia-derived Shape.

⸻

4) Wire the builder to the new evidence
   •	In PipelineBuilder2.scala (soon to be your canonical PipelineBuilder), change the attach points:

import com.flowforge.core.contracts.{ SchemaConforms, SchemaPolicy }
// …
def addTypedSource[R, C, P <: SchemaPolicy](src: TypedSource[R], contract: DataContract[C], policy: P)(
implicit ev: SchemaConforms[R, C, P]
): PipelineBuilder2[F, In, R] = ???
// …
def addTypedSink[R, C, P <: SchemaPolicy](snk: TypedSink[R], contract: DataContract[C], policy: P)(
implicit ev: SchemaConforms[R, C, P]
): PipelineBuilder2[F, In, Out] = ???

	•	Keep your phantom-state gating unchanged. It composes nicely with the new evidence.

⸻

5) Port the tests (drop shapeless’ illTyped)
   •	Replace shapeless.test.illTyped usage in SchemaWitnessCompileFailSpec with ScalaTest negative checks:

import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpec

class SchemaConformsCompileSpec extends AnyWordSpec with Matchers {
"contracts" should {
"fail to compile when field is missing" in {
assertTypeError(
"""
import com.flowforge.core.contracts._
// define Out, Contract with one missing field under Exact
implicitly[SchemaConforms[Out, Contract, SchemaPolicy.Exact]]
"""
)
}
}
}

ScalaTest exposes assertTypeError / assertDoesNotCompile, so you can keep compile-fail scenarios without shapeless.  ￼

	•	Keep your scripted projects (good/bad) to enforce repo-wide compile fail/pass in CI (unchanged).

⸻

6) Remove shapeless (+ refactor leftovers)

Once the above compiles and tests pass:
•	Delete imports: shapeless._, ops.hlist._, labelled._ from:
•	SchemaWitness.scala, SchemaEvidence.scala, TypedSchema.scala, TypedIO.scala, DataTypes.scala, TypedContracts.scala, CompileTimeContracts.scala.
•	Replace any R <: HList type params with R (record type).
•	Remove the shapeless dep in Dependencies.scala.

As a bonus, prune any now-redundant “HList helper” objects.

⸻

7) Error message polish & docs
   •	Just like you have today, bake policy names and a short “fix recipe” into the @implicitNotFound and macro message.
   •	Keep your “docs that fail until you fix the field” tutorial; simply update the import paths.
   •	Add a short “Why Magnolia (Scala 2)” callout referencing:
   •	Magnolia scala2 availability & API,
   •	Field labels & defaults available from CaseClass/Param,
   •	The cross-branch artifact naming (scala2 vs scala3).  ￼

⸻

Patchlets (illustrative)

TypedIO.scala (constructor surface)

- import shapeless.HList
- def localParquetSource[R <: HList](path: String): TypedSource[R] = ...
+ import com.flowforge.core.contracts.derive.Shape
+ def localParquetSource[R : Shape](path: String): TypedSource[R] = ...

DataTypes.scala (type defs)

- final case class TypedSink[R <: HList](underlying: DataSink)
- final case class TypedSource[R <: HList](underlying: DataSource)
+ final case class TypedSink[R](underlying: DataSink)(implicit val shape: Shape[R])
+ final case class TypedSource[R](underlying: DataSource)(implicit val shape: Shape[R])

SchemaEvidence.scala → delete/replace with new SchemaConforms.scala

- import shapeless.ops.hlist.Align
- import shapeless.{ HList, LabelledGeneric }
- trait SchemaConforms[A, R <: HList, P <: SchemaPolicy]
+ import com.flowforge.core.contracts.derive.Shape
+ trait SchemaConforms[Out, Contract, P <: SchemaPolicy]

SchemaWitness.scala → remove (logic lives in macro)

All the policy logic now sits in SchemaConformsMacros. If you like the “witness” naming, re-export a type alias to the new evidence.

⸻

CI gates (keep you honest)
•	Unit tests: Positive/negative compile tests (ScalaTest assertTypeError), property tests of policy laws (unchanged).
•	Scripted: Good projects compile & run; bad projects must fail compilation with a golden error snippet.
•	scalac-profiling (optional): baseline compile time before & after; Magnolia often improves the implicit mess compared to heavy shapeless paths.  ￼

⸻

Risks & mitigations
•	Macro eval of shapes: The extractFields helper uses macro evaluation of Shape[T].fields; keep the Shape derivation pure & total so expansion is deterministic. (Magnolia does that by design.)  ￼
•	Sealed trait records: If you have sum-type “records,” decide whether to forbid, or flatten one chosen subtype. The sketch shows a conservative approach; you can expand later if needed.
•	Default/optional semantics: For Backward/Forward, decide the exact rule (e.g., new fields must be Option[_] or have a default). Magnolia exposes defaults & you can read Option from the type name string we recorded; refine as needed.  ￼
•	Test infra: Replace illTyped (shapeless) with ScalaTest’s native macros to avoid a dependency loop.  ￼

⸻

Why this isn’t over-engineering
•	We’ve removed an entire category of type-level HList code and made schemas ordinary case classes.
•	We used a single derivation facade so Scala 3’s migration later is swapping the backend, not the API. (Scala 3 path via Mirrors is well-trodden.)  ￼
•	We kept the core USP: pipelines do not compile when contracts drift.

⸻

Quick dependency crib
•	Magnolia for Scala 2 (scala2 branch):

libraryDependencies += "com.softwaremill.magnolia1_2" %% "magnolia" % "1.1.10"

(per the official scala2 README)  ￼

	•	Optional: scala-reflect for macros (Provided).  ￼

⸻
