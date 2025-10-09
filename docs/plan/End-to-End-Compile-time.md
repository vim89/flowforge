# FlowForge - 100% Compile-Time Contracts: End-to-End Implementation Plan
_This is a precise, do-once, keep-forever plan. Follow the steps in order; each has clear deliverables and acceptance checks._

---

## 0) Preconditions (single source of truth)
- **Scala**: 2.13.16
- **Build hygiene**: `scalafmt`, `scalafix` (no-unused-imports), fatal warnings in CI.
- **Repo switches**:
  - Single builder: keep `PipelineBuilder` (typed), mark any prior builder `LegacyPipelineBuilder` and deprecate.
  - Zero engine imports in `modules/core/**`.
  - Tests may depend on extra libs (e.g., ScalaTest, MUnit (2 & 3): `compileErrors` to assert the compiler rejects a snippet. Alt (no test macros): a small sbt scripted/harness project that compiles a sample and expects failure.) even if prod doesn’t.

**Acceptance**
- `sbt "scalafmtCheckAll; scalafixAll --check; Test/compile"` passes locally.

---

## 1) Contracts Core (types + derivation + policy)
### 1.1 Create the compile-time contracts API
**Files**
- `modules/core/src/main/scala/com/flowforge/core/contracts/SchemaPolicy.scala`
- `modules/core/src/main/scala/com/flowforge/core/contracts/SchemaConforms.scala`
- `modules/core/src/main/scala/com/flowforge/core/contracts/internal/SchemaConformsMacros.scala`

**SchemaPolicy.scala (sealed ADT)**
```scala
package com.flowforge.core.contracts
sealed trait SchemaPolicy
object SchemaPolicy {
  sealed trait Exact          extends SchemaPolicy     // ordered equality (name,type)
  sealed trait ExactUnordered extends SchemaPolicy     // set equality (name,type)
  sealed trait Backward       extends SchemaPolicy     // contract ⊆ out; missing allowed iff default/Option
  sealed trait Forward        extends SchemaPolicy     // out ⊆ contract
  sealed trait Full           extends SchemaPolicy     // allow all (escape hatch)
}

**SchemaConforms.scala (typeclass + materializer)**

```scala
package com.flowforge.core.contracts
import scala.annotation.implicitNotFound
import com.flowforge.core.contracts.derive.Shape

@implicitNotFound("""
FlowForge: Contract drift (policy: ${P})
Out: ${Out} vs Contract: ${Contract}
Missing: ... | Extra: ... | Mismatched: ...
""")
trait SchemaConforms[Out, Contract, P <: SchemaPolicy]

object SchemaConforms {
  import scala.language.experimental.macros
  implicit def materialize[Out, Contract, P <: SchemaPolicy](
    implicit so: Shape[Out], sc: Shape[Contract]
  ): SchemaConforms[Out, Contract, P] =
    macro internal.SchemaConformsMacros.materializeImpl[Out, Contract, P]
}
```

**SchemaConformsMacros.scala (algorithm)**

* Evaluate *fields* at compile-time from `Shape[Out]` and `Shape[Contract]` → `List[(name, fqnType, hasDefault, isOptional)]`.
* Compute `(name,type)` pairs; check policy:

    * Exact: **list equality**
    * ExactUnordered: **set equality**
    * Backward: `contractPairs.forall(outSet.contains)`; allow “missing” only if `hasDefault || isOptional`
    * Forward: `outPairs.forall(contractSet.contains)`
    * Full: always ok
* If ok ⇒ emit instance; else `abort` with concise diff (Missing/Extra/Mismatch).

**Acceptance**

* Unit tests prove all four policies over simple, nested, optional, and collection fields.

### 1.2 Choose derivation for record “shape”

You have two viable lanes-pick one and stick to it:

**Lane A (recommended on Scala 2): Magnolia-based shape**

* Add `modules/core/src/main/scala/com/flowforge/core/contracts/derive/Shape.scala`
* Derive `Shape[A]` using Magnolia 1.x (Scala 2 line). Use `Param.label` + `Param.typeName.full` for field metadata.

**Lane B: Keep your custom reflection macro**

* Keep your existing macro that extracts fields from case classes; ensure it returns the same information (`(name, fqnType, hasDefault, isOptional)`).
* The rest of the pipeline is identical.

**Acceptance**

* `Shape` (from Magnolia or custom) returns identical metadata for a golden set of case classes (assert in tests).

---

## 2) Enforce contracts at the **edges**

### 2.1 Typed endpoints

**Files**

* `modules/core/src/main/scala/com/flowforge/core/types/TypedSource.scala`
* `modules/core/src/main/scala/com/flowforge/core/types/TypedSink.scala`

**Signatures**

```scala
final case class TypedSource[C](underlying: DataSource)(implicit val sc: Shape[C])
final case class TypedSink[R](underlying: DataSink)(implicit val sr: Shape[R])
```

### 2.2 PipelineBuilder evidence flow

**File**

* `modules/core/src/main/scala/com/flowforge/core/PipelineBuilder.scala`

**Enforce at add-points**

```scala
import com.flowforge.core.contracts.{SchemaConforms, SchemaPolicy}
import com.flowforge.core.contracts.derive.Shape

// SOURCE: produced C must conform to declared contract R under policy P
def addTypedSource[C, R, P <: SchemaPolicy](
  source: TypedSource[R],
  reader: DataSource => F[C]
)(implicit ev: SchemaConforms[C, R, P]) : PipelineBuilder[F, Unit, C]

// SINK: current Out must conform to declared contract R under policy P
def addTypedSink[R, P <: SchemaPolicy](
  sink: TypedSink[R],
  writer: (Out, DataSink) => F[Unit]
)(implicit ev: SchemaConforms[Out, R, P]) : PipelineBuilder[F, In, Unit]
```

**Acceptance**

* Calling either method without valid evidence doesn’t compile; with valid evidence compiles.

---

## 3) Engine boundaries (no leakage)

### 3.1 Core stays engine-agnostic

* No `org.apache.spark.*` in `modules/core/**`, `modules/connectors/**`, `modules/quality/**`.

### 3.2 Typed handles + type classes

**File**

* `modules/connectors/src/main/scala/com/flowforge/connectors/capabilities/SinkCaps.scala`

**Pattern**

```scala
sealed trait SupportsMerge
sealed trait SupportsBatch

final case class Sink[R, Caps, Handle](handle: Handle)

trait CanWrite[A, R, Caps, Handle] {
  def write(s: Sink[R, Caps, Handle], ds: A): Unit
}

object Writes {
  def writeMerge[A, R, H](s: Sink[R, SupportsMerge, H], ds: A)
                         (implicit W: CanWrite[A, R, SupportsMerge, H]): Unit = W.write(s, ds)
  def writeBatch[A, R, H](s: Sink[R, SupportsBatch, H], ds: A)
                         (implicit W: CanWrite[A, R, SupportsBatch, H]): Unit = W.write(s, ds)
}
```

### 3.3 Engine adapters live in engine modules

* `modules/engines-spark/**` provides:

    * `type DS[A] = org.apache.spark.sql.Dataset[A]`
    * `final case class SparkHandle[R, Caps](...)`
    * `implicit` writers: `CanWrite[DS[R], R, SupportsMerge, SparkHandle[R, Caps]]`, etc.

**Acceptance**

* Core compiles without Spark on classpath; engine modules add the instances.

---

## 4) Data Quality (runtime) without coupling

### 4.1 Engine-agnostic API

**File**

* `modules/quality/src/main/scala/com/flowforge/quality/AssetCheck.scala`

```scala
sealed trait Severity; object Severity { case object Info; case object Warn; case object Error }
final case class CheckResult(passed: Boolean, details: String)
final case class AssetCheck[A](name: String, eval: A => CheckResult, severity: Severity, owner: String, hint: String)
```

### 4.2 Engine adapters

* `quality-spark` module: helpers like `nonEmpty: AssetCheck[Dataset[_]]`, `nullRateBelow(...)`, `freshnessWithin(...)`.

**Acceptance**

* A pipeline can attach checks at source or pre-sink and run them in engine modules; core remains pure.

---

## 5) Tests that **prove** the promise

### 5.1 Positive compilation tests

* Simple/nested/Option/collection contracts compile under `Exact`, `Backward`, `Forward`, `ExactUnordered`.

### 5.2 Negative (compile-fail) tests

* **ScalaTest:** `shouldNot typeCheck`, `assertTypeError`, or `assertDoesNotCompile` for negative compile checks. ([scalatest.org][1])
* **MUnit (2 & 3):** `compileErrors` to assert the compiler rejects a snippet. ([Scala Documentation][2], [GitHub][3], [scalameta.org][4])
* **Alt (no test macros):** a small **sbt scripted**/harness project that compiles a sample and expects failure. ([scala-sbt.org][5], [stackoverflow.com][6])

* Add at least 6 negatives:

    * Missing required field (Exact, Backward)
    * Extra field (Exact/ExactUnordered)
    * Type mismatch
    * Forward with missing in contract
    * Nested mismatch

**Acceptance**

* `test` job turns **red** when contracts drift; a PR can’t merge.

---

## 6) Developer Experience (DX) that sells it

### 6.1 One-minute fail-then-fix quickstart

* Giter8 template: `flowforge.g8`
* Post-generate actions:

    * Project includes a **typed sink** with `SchemaPolicy.Exact` and a matching contract.
    * A comment in `Contract.scala` instructs changing a type (e.g., `Long` → `String`) to force RED.
* Commands:

    * `sbt compile` → **RED** with drift diff
    * Revert / set `Backward` policy / align types → `sbt compile` → **GREEN**

### 6.2 “How it fails” docs

* Table of policy behaviors with examples + **verbatim** error diffs.
* A condensed FAQ: nested types, Options, lists/maps, ADTs scope.

**Acceptance**

* A brand-new user can reproduce red→green in <60 seconds.

---

## 7) CI/CD that enforces your claims

* Jobs:

    * `lint`: `scalafmtCheckAll; scalafixAll --check`
    * `unit`: `Test/compile; test`
    * `contracts-negative`: compile-fail specs (must fail appropriately)
    * `template-smoke`: generate archetype → compile → run minimal job
    * `engines-spark` (optional matrix): run integration smoke against Spark only in a separate job
* Treat warnings as errors for core; allow engine ITs to be opt-in (long-running).

**Acceptance**

* Any PR that breaks contracts or re-introduces engine leakage fails CI.

---

## 8) Lineage (OpenLineage/Marquez) first-class by default

### 8.1 Minimal emitter

* `modules/lineage/src/main/scala/.../OpenLineageEmitter.scala`

    * Send job/run/dataset events on each stage start/end.
    * Destination: HTTP → Marquez.

### 8.2 Demo docker-compose

* `docker/lineage/docker-compose.yaml`: boots Marquez + Postgres, exposes UI.
* Add example project task: `sbt run` → view job/run graph in Marquez.

**Acceptance**

* Running the quickstart emits visible lineage to local Marquez.

---

## 9) Scala-3 migration posture (document now, implement later)

* Keep **public API** free of Scala-2-only features.
* Document two tracks:

    * Short-term: keep Scala-2 macro; publish Scala-2 artifact.
    * Mid-term: add **Scala-3** macro derivation (or Magnolia-3) and cross-build.
* Add a `MIGRATION.md` with guidelines (no top-level `object` synthetic members relied upon, avoid symbol hacks, etc.).

**Acceptance**

* Clear plan exists; no public API change needed to switch derivation backend later.

---

## 10) Cleanup & deprecation

* Mark any shapeless/HList files `@deprecated` and block new imports via scalafix rule.
* After all call-sites are clean: remove legacy files, then drop shapeless from runtime (keep in test if you use `illTyped`).

**Acceptance**

* `git grep -nE '\bHList\b|\bshapeless\b'` returns **only** test files (or zero).

---

## 11) Release criteria (promote 0.9.x → 1.0.0)

* ✅ Policies enforced at edges (source/sink) with compile-time failures and legible diffs.
* ✅ Positive + negative (compile-fail) tests cover nesting/Options/collections.
* ✅ Engine-agnostic core; Spark adapters provided; lineage demo works.
* ✅ One-minute fail-then-fix quickstart + docs.
* ✅ CI blocks regressions; deprecation fence in place; no shapeless in prod.

---

## 12) Work plan (swimlanes & order)

1. **Contracts core** (policy semantics + macro) → tests (pos/neg).
2. **Typed endpoints & builder** evidence wiring.
3. **Engine boundary** hardening (type classes; spark adapter).
4. **Quickstart** + **docs** (“how it fails”).
5. **CI** (negative tests + template smoke).
6. **Lineage** minimal emitter + compose demo.
7. **Cleanup** (legacy deprecations) → **release 0.9.x**.
8. **Polish** (docs/site/benchmarks) → **1.0.0**.

*Stay boring; be strict. The compiler is your product manager.*

```

**Sources**
- Magnolia (Scala-2 line): `CaseClass`, `Param.typeName`, product derivation. :contentReference[oaicite:0]{index=0}  
- dbt model contracts (build-time schema gates; mental model for your fail-then-fix demo). :contentReference[oaicite:1]{index=1}  
- Dagster asset checks (runtime quality; complementary to compile-time contracts). :contentReference[oaicite:2]{index=2}  
- OpenLineage + Marquez quickstarts (minimal lineage emitter + local demo). :contentReference[oaicite:3]{index=3}  
- Frameless (typeful Spark; contrasts with your engine-agnostic, edge-contract approach). :contentReference[oaicite:4]{index=4}
::contentReference[oaicite:5]{index=5}
```

## 13) Future ready with Scala 3

We must design FlowForge’s derivation layer so the **public API stays macro-agnostic**, with two swappable backends: **Scala 2: Magnolia** and **Scala 3: inline + Mirrors**. Here’s the lean plan.


### Cross-version, pluggable derivation plan (S2: Magnolia | S3: Inline)

#### 1) Stable, public API (common to 2 & 3)
- Keep the user-facing types unchanged:
  - `trait SchemaConforms[Out, Contract, P <: SchemaPolicy]`
  - `object SchemaConforms { implicit def materialize[...] : SchemaConforms[...] = /* delegated */ }`
- All policy logic (Exact / ExactUnordered / Backward / Forward) lives in **shared** helpers over a common `Field(name: String, fqnType: String, hasDefault: Boolean, isOptional: Boolean)` list.

#### 2) Split implementations by Scala version
- **src/main/scala-2/**: implement `Shape[T]` with **Magnolia 1.x** and a Scala 2 macro materializer.
- **src/main/scala-3/**: implement `Shape[T]` with **inline + Mirrors** and `inline` materialization using `compiletime.error` for custom diagnostics.
- Keep error text & policy semantics identical between backends.

#### 3) sbt wiring
- Enable cross-build (2.13.x & 3.x) and version-specific source dirs:
  ```scala
  ThisBuild / crossScalaVersions := Seq("2.13.16", "3.3.3")
  libraryDependencies ++= Seq(
    "com.softwaremill.magnolia1_2" %% "magnolia" % "1.1.10" % "scala2"
  )
  Compile / unmanagedSourceDirectories ++= {
    val base = (Compile / sourceDirectory).value
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, _)) => Seq(base / "scala-2")
      case Some((3, _)) => Seq(base / "scala-3")
      case _            => Nil
    }
  }
  ```

* Optional: a tiny “contracts-tests” cross-project to assert parity (same drift messages) under both compilers.

#### 4) Backend specifics (sketch)

* **Scala 2 (Magnolia)**

    * `Shape[T]` via `magnolia1.CaseClass`/`Param.typeName.full` (product types) and map to `Field`.
    * `SchemaConforms.materialize` = Scala 2 def-macro; compute diff over `Field`s; `abort` with concise “Missing/Extra/Mismatch”.
* **Scala 3 (inline)**

    * `inline given shape[T]` using Mirrors to enumerate fields; build identical `Field` list.
    * `inline def materialize[...]` computes the diff at compile time; on failure call `scala.compiletime.error` with the same message.

#### 5) Guarantees & tests

* Shared property tests for policy rules over generated case classes (nested, options, collections).
* Compile-fail tests for each policy under both Scala 2 & 3 builds
* Use built-in test macros instead of shapeless:

* **ScalaTest:** `shouldNot typeCheck`, `assertTypeError`, or `assertDoesNotCompile` for negative compile checks. ([scalatest.org][1])
* **MUnit (2 & 3):** `compileErrors` to assert the compiler rejects a snippet. ([Scala Documentation][2], [GitHub][3], [scalameta.org][4])
* **Alt (no test macros):** a small **sbt scripted**/harness project that compiles a sample and expects failure. ([scala-sbt.org][5], [stackoverflow.com][6])

[1]: https://www.scalatest.org/user_guide/using_matchers "Using matchers"
[2]: https://docs.scala-lang.org/toolkit/testing-suite.html "How to write tests? | The Scala Toolkit"
[3]: https://github.com/scalameta/munit "scalameta/munit: Scala testing library with actionable ..."
[4]: https://scalameta.org/munit/blog/2021/01/05/macromix.html "Publish Scala 2 and Scala 3 macros together · MUnit"
[5]: https://www.scala-sbt.org/1.x/docs/Testing-sbt-plugins.html "sbt Reference Manual - Testing sbt plugins"
[6]: https://stackoverflow.com/questions/20114298/how-do-i-write-a-scala-unit-test-that-ensures-compliation-fails "How do I write a scala unit test that ensures compliation fails?"


**Why this works**
- Magnolia gives you robust **product/param** metadata on Scala 2; you already rely on this shape to decide policies. :contentReference[oaicite:0]{index=0}  
- Scala 3’s **inline + Mirrors** + `compiletime.error` replace macros while preserving custom, compile-time diagnostics. :contentReference[oaicite:1]{index=1}  
- sbt **cross-build + split source dirs** is the standard path to ship a single artifact API with per-version implementations. :contentReference[oaicite:2]{index=2}

This keeps contracts API stable; you can “swap engines” (Magnolia vs. Mirrors) without changing user code.
::contentReference[oaicite:3]{index=3}
