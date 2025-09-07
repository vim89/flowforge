# FlowForge — Final Plan for **100% Compile-Time Contracts**
> Targets **Scala 2.13.16** now, stays **Scala-3-ready** later.  
> **Decisions**:  
> • Canonical builder = `PipelineBuilder` (typed). Legacy runtime builder becomes `internal.LegacyPipelineBuilder` (deprecated).  
> • **Magnolia-based** generic derivation on Scala 2.13 (artifact family: `com.softwaremill.magnolia1_2`), hidden behind a small **derivation facade** so we can swap to Scala-3 Mirrors later without touching user code.  
> • No Shapeless, no Frameless. Pure Spark kernels; effects only at IO/orchestration.  
> • Typed endpoints **everywhere** (sources & sinks) + schema policies; illegal pipelines are **not representable** and **won’t compile**.

---

## Phase 0 — Refactor & Freeze (Day 0–1)

- [ ] **Rename / deprecate**
    - Move `modules/core/types/PipelineBuilder2.scala` → `modules/core/PipelineBuilder.scala` (canonical).
    - Move old `modules/core/PipelineBuilder.scala` → `modules/core/internal/LegacyPipelineBuilder.scala`.
    - Annotate with:
      ```scala
      @deprecated("Use typed PipelineBuilder; this is runtime-only and will be removed post-1.0", "0.9.0")
      ```
- [ ] **Public surface**
    - Introduce `docs/public-api.md` (what’s public vs `...internal`).
    - Add a scalafix rule blocking imports of `...internal` from examples/templates/libs.
- [ ] **Version policy**
    - `versionScheme := Some("early-semver")` + `sbt-version-policy` (MiMa) to guard 1.0.

---

## Phase 1 — Type-Level Contracts (Week 1)

- [ ] **Policy algebra**
    - `SchemaPolicy = Exact | ExactUnordered | Backward | Forward | Full` with precise allowed changes.
- [ ] **Derivation facade (Scala 2 now, Scala 3 later)**
    - Define a tiny SPI in `core/contracts/derive/`:
      ```scala
      package com.flowforge.core.contracts.derive
  
      trait ShapeOf[A] { type Labels <: Tuple; type Types <: Tuple }
      trait Conforms[Out, Contract, P] // evidence marker
  
      trait DerivationBackend {
        type Aux[A, L <: Tuple, T <: Tuple]
        def shapeOf[A]: ShapeOf[A]
        def conforms[Out, Contract, P]: Option[Conforms[Out, Contract, P]]
      }
      object Derivation extends DerivationBackend // wired by module (Magnolia2 today, Mirrors3 tomorrow)
      ```
    - **Scala 2.13 module** uses **Magnolia** to materialize `ShapeOf[A]` + implement `conforms`.
    - Later **Scala 3 module** replaces backend with Mirrors+inline; facade remains unchanged.
- [ ] **Helpful compiler errors**
    - Annotate evidence with `@implicitNotFound` showing:
        - used policy,
        - missing/extra fields,
        - type mismatches,
        - short “Fix” hint (add field / change policy / adjust type):
      ```scala
      @implicitNotFound("Schema does not conform under ${P}.\nMissing: ${Missing}\nExtra: ${Extra}\nMismatched: ${Mismatched}\nSee: docs/how-it-fails.md#${P}")
      trait SchemaConforms[Out, Contract, P]
      ```
- [ ] **Attach evidence at endpoints**
    - Sources:
      ```scala
      def addTypedSource[R, C, P <: SchemaPolicy](
        src: TypedSource[R], contract: Contract[C], policy: P
      )(implicit ev: SchemaConforms[R, C, P]): Builder[HasSource with HasContract, F, R, C, P]
      ```
    - Sinks:
      ```scala
      def addTypedSink[R, C, P <: SchemaPolicy](
        snk: TypedSink[R], contract: Contract[C], policy: P
      )(implicit ev: SchemaConforms[R, C, P]): Builder[HasSink, F, R, C, P]
      ```
- [ ] **Phantom-state gating**
    - Stages: `HasSource`, `HasContract`, `HasTransform`, `HasSink`.
    - `.build` requires `S <:< (HasSource with HasContract with HasTransform with HasSink)`.

---

## Phase 2 — Typed Endpoints, Codegen, Transforms (Week 2)

- [ ] **Typed endpoints only (public)**
    - Public constructors return **only** `TypedSource[R]` / `TypedSink[R]`.
    - Provide `TypedIO.*` helpers: `gcsParquetSource[R]`, `deltaSink[R]`, `kafkaJsonSource[R]`, etc.
- [ ] **External contract → codegen**
    - sbt task generates `case class Contract_vN(...)` from Avro/JSON Schema into `src_managed/`.
    - Users pass `Contract_vN` as the `C` type; compile-time policy decides if it conforms to record `R`.
- [ ] **Typed transforms**
    - Kernels stay pure (`A => B` / `Dataset[A] => Dataset[B]`); if type changes `R ⇒ R2`, the next sink must satisfy `SchemaConforms[R2, C, P]`.

---

## Phase 3 — Capability-Typed Connectors (Week 2–3)

- [ ] **Small, local phantom types**
    - Encode sink capabilities (no runtime cost):
      ```scala
      sealed trait SupportsMerge
      sealed trait SupportsBatch
      final case class Sink[R, Caps](...)
      def writeMerge[R](s: Sink[R, SupportsMerge], r: Dataset[R]) = ...
      ```
    - Now `writeMerge` *only compiles* for sinks that actually support MERGE.
- [ ] **Newtypes for domain IDs/keys**
    - Use `@newtype` (or opaque in 3 later) for `UserId`, `TenantId`, `Partition` to avoid accidental mixups.

---

## Phase 4 — DX: Quickstart + Docs That Fail (Week 3)

- [ ] **Quickstart that fails then passes**
    - `flowforge-quickstart.g8`:
        1) Initial compile **fails** (one field drift).
        2) Fix field → compile succeeds.
        3) Run local job on tiny CSV/Parquet.
- [ ] **Living docs**
    - `docs/how-it-fails.md`: screenshots for each policy + one-line fixes.
    - `docs/why-compile-time.md`: contrast with engine/CI-time contracts (e.g., dbt); your edge is Scala **compile** time.

---

## Phase 5 — Tests that Prove It (Week 3–4)

- [ ] **Negative compilation tests (unit)**
    - ScalaTest/MUnit:
        - Missing sink ⇒ **does not compile**
        - `R` ≠ `C` under `Exact` ⇒ **does not compile**
        - Illegal evolution under `Backward`/`Forward` ⇒ **does not compile**
        - Positive cases assert compile.
- [ ] **Contract laws with Discipline + ScalaCheck**
    - Property checks:
        - `Exact` == set equality;
        - `Backward` allows only added optional fields (with defaults);
        - `Forward` allows dropping optional fields;
        - `Full` superset of all.
- [ ] **Scripted build tests (integration)**
    - `good/` projects compile & run; `bad/` projects must **fail** compilation with golden messages.
- [ ] **CI gates**
    - `scalafmtCheckAll`, `scalafixAll --check`, **negative compile tests**, property tests, coverage report.
    - Spark ITs remain opt-in (`-DwithSparkIT=true`) to keep the loop fast.

---

## Phase 6 — Quality & Lineage (Week 4–5)

- [ ] **Asset checks (first-class)**
    - Tiny API:
      ```scala
      final case class AssetCheck(name: String, eval: Dataset[?] => CheckResult, severity: Severity, owner: String, hint: String)
      ```
    - Default provider: **Deequ** (in-process).
- [ ] **OpenLineage emission**
    - Emit run/job/dataset events on read/write and include **contract version** as a custom facet.
    - Add a short “Hello Marquez” doc with a screenshot.

---

## Phase 7 — Repo-Wide Lockdown (Week 5)

- [ ] **Remove escape hatches**
    - No public untyped source/sink constructors; legacy builder documented only for migration.
    - Scalafix rule: builder usage must specify a `SchemaPolicy`.
- [ ] **Logging & effects**
    - Replace stray `println`; keep effects in the orchestration shell; Spark kernels pure.

---

## Phase 8 — Ergonomics & Error Clarity (Week 5–6)

- [ ] **Error polish**
    - Pretty-print diffs: `Missing: name:String`, `Extra: zip:Option[String]`, `Mismatched: amount expected Long, found Int`.
    - Include short fix hints and doc links *in* the `@implicitNotFound` message.
- [ ] **Dev helper (optional)**
    - `sbt :why-not-compiling` task that renders the inferred `R` vs `C` shape for faster iteration.

---

## Phase 9 — Ship Criteria (Week 6)

- [ ] **Definition of Done**
    - All public ways to attach sources/sinks require `SchemaConforms`/`SchemaEq`.
    - Legacy builder is deprecated, hidden from docs/templates.
    - Quickstart demonstrates **fail-then-fix**.
    - CI includes **negative compilation** + property + scripted tests.
    - Public API documented; `...internal` fenced.
- [ ] **Release sequence**
    - Tag **0.9.0** → gather feedback with 1–2 design partners → stabilize → tag **1.0.0** with migration notes.

---

## Minimal Code Sketches (for clarity)

**1) Evidence usage at endpoints**
```scala
def addTypedSink[R, C, P <: SchemaPolicy](
  snk: TypedSink[R], contract: Contract[C], policy: P
)(implicit ev: SchemaConforms[R, C, P]): Builder[HasSink, F, R, C, P]
