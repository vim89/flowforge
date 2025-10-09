# FlowForge: Plan to Achieve **100% Compile-Time Contract** Enforcement

> Goal: **Pipelines cannot be built** (won’t compile) whenever the declared data contract and the program’s types **diverge**-for sources *and* sinks-across all public APIs, examples, and templates.

---

## Phase 0 - Guardrails & Scope (Day 0)

1) **Freeze the public surface**
    - Mark `modules/core/types/PipelineBuilder2.scala` as the **only** public builder API; rename to `PipelineBuilder`.
    - Move legacy/runtime builders to `.../internal/` and `@deprecated` + `@nowarn`. Document migration.

2) **SemVer line-in-the-sand**
    - Create `docs/public-api.md`: list public packages, guarantees, and what constitutes a breaking change.
    - Add CI check: no imports from `...internal` in examples/templates.

---

## Phase 1 - Type-Level Contract Model (Week 1)

3) **Contract policy ADT**
    - `SchemaPolicy = Exact | ExactUnordered | Backward | Forward | Full`.
    - Explain algebra (what each policy allows) in Scaladoc + `docs/contracts/policies.md`.

4) **Typeclass evidence with helpful compiler errors**
    - Define `trait SchemaConforms[Out, Contract, P <: SchemaPolicy]` and `trait SchemaEq[Out, Contract]`.
    - Annotate both with `@implicitNotFound` to show **human-readable** errors on drift (e.g., which fields differ).
    - Add tiny pretty-printer for type-level field sets to include in the error message.

5) **Generic derivation**
    - **Scala 2.13 path**: Derive field shapes using **shapeless** `LabelledGeneric`/`HList` + `Keys`.
    - **Scala 3 path (future)**: Derive using `Mirror.ProductOf` and `MirroredElemLabels/Types`. Keep both behind a stable facade so the API won’t change when you move to Scala 3.

6) **Typed endpoints**
    - `final case class TypedSource[R](...)`, `TypedSink[R](...)` where `R` is the **record type** (case class).
    - Provide helpers `TypedIO.gcsParquetSource[R]`, `TypedIO.deltaSink[R]`, etc., so users don’t accidentally fall back to untyped endpoints.

7) **Phantom-state builder**
    - Stages: `HasSource`, `HasContract`, `HasTransform`, `HasSink`.
    - API requires `SchemaConforms`/`SchemaEq` **at the time the source/sink is attached**:
      ```scala
      def addTypedSource[R, C, P](src: TypedSource[R], c: Contract[C], p: P)
        (implicit ev: SchemaConforms[R, C, P]): Builder[HasSource with HasContract, ...]
      def addTypedSink[R, C, P](snk: TypedSink[R], c: Contract[C], p: P)
        (implicit ev: SchemaConforms[R, C, P]): Builder[HasSink, ...]
      ```
    - `.build` requires `S <:< (HasSource with HasContract with HasTransform with HasSink)`.

---

## Phase 2 - End-to-End Coverage (Week 2)

8) **Every public “add source/sink” requires evidence**
    - Audit all builder overloads. Delete or **deprecate** any variant that doesn’t request `SchemaConforms`/`SchemaEq`.

9) **Transformations preserve/produce types**
    - Ensure `transform` methods are pure and typed (`A => B`). If a transform changes the record type, the **following** sink must match the new `B` via evidence.

10) **Connectors return typed endpoints**
- Wrap all connectors (GCS/S3/Delta/Kafka) so the *only* public constructors return `TypedSource[R]`/`TypedSink[R]`.
- Where runtime schemas are discovered (e.g., reading a table), **require** a compile-time `R` supplied by the user; runtime checking is still allowed, but the compile gate is the contract evidence.

11) **Codegen for external contracts**
- sbt task: generate `case class Contract_vN()` from Avro/JSON Schema **at build time** into `target/scala-2.13/src_managed/`.
- Users then import `Contract_vN` as the `C` type on the builder; this turns external schema into **compile-time** types.

---

## Phase 3 - Developer Experience & Templates (Week 3)

12) **Quickstart that fails on purpose (then passes)**
- New g8 template `flowforge-quickstart.g8`:
    - Step 1: compile fails with an `@implicitNotFound` showing missing/extra field.
    - Step 2: user fixes the field in their case class → recompile passes.
    - Step 3: run a local job on a tiny CSV/Parquet.

13) **Examples rework**
- All examples use `TypedIO.*` endpoints and declare a `Contract[C]` with an explicit `SchemaPolicy`.
- Provide at least one `Backward` example: add an optional field with default and show that compilation **still succeeds** only under `Backward`/`Full`.

14) **Docs & messaging**
- `docs/how-it-fails.md`: screenshots of the **compile error** for each policy.
- `docs/why-compile-time.md`: compare with dbt contracts (engine/CI-time) and Dagster checks (runtime/CI).

---

## Phase 4 - Tests: Make Drift **Unbuildable** in CI (Week 3–4)

15) **Negative compilation tests (unit level)**
- **MUnit**: `compileErrors("bad code")` with golden messages for:
    - Missing sink
    - Source `R` != `Contract C` under `Exact`
    - Illegal evolution under `Backward/Forward`
- **ScalaTest** alternative: `"code" shouldNot compile`/`typeCheck`.

16) **Scripted build tests (integration level)**
- sbt **scripted** projects where `>` `compile` is expected to **fail** for bad samples and **succeed** for good ones.

17) **CI gates**
- New job runs: `scalafmtCheckAll`, `scalafixAll --check`, **negative compile tests**, unit tests, coverage.
- Upload quickstart docs + compile error screenshots as artifacts.

---

## Phase 5 - Total Path Audit (Week 4)

18) **grep for escape hatches**
- Disallow any public constructor that yields an untyped source/sink.
- Lint rule (scalafix custom) to forbid imports from `...internal` in non-internal code.

19) **Examples & docs sweep**
- Ensure **no** README or doc page shows untyped endpoints or legacy builder.
- Replace any `println` with the project logger; keep kernels pure.

---

## Phase 6 - Ergonomics & Error Clarity (Week 5)

20) **High-signal compiler messages**
- Format `@implicitNotFound` to show:
    - policy used,
    - missing fields,
    - extra fields,
    - field(s) with mismatched types.
- Add link in message to `docs/how-it-fails.md` for a 60-second fix guide.

21) **“Why not compiling?” helper**
- Optional macro/given to pretty-print the inferred `R` vs `C` at compile error sites (for Scala 3 future).

---

## Phase 7 - Hardening (Week 5–6)

22) **Contract evolution rules**
- Encode permitted changes per policy (e.g., `Backward`: add optional fields with defaults; `Forward`: allow dropping optional fields).
- Unit tests over witness derivation: prove what’s allowed/forbidden.

23) **Typed sinks w/ format facets**
- For Parquet/Delta/BigQuery/Kafka, include a tiny type-level “format facet” so the same `R` can be checked alongside a sink’s own constraints (e.g., no nested maps for specific sinks if unsupported).

24) **Performance sanity**
- Ensure derivation is cached (summon evidence once per `R`/`C` pair). Macro/time compilation benchmarks optional but good to have.

---

## Phase 8 - Ship Criteria for “100%” (Week 6)

25) **Definition of Done**
- **All** public ways to attach sources/sinks require `SchemaConforms`/`SchemaEq`.
- Legacy builder **not** shown in any docs/templates and marked `@deprecated` in code.
- Quickstart demonstrates a **fail-then-fix** journey.
- CI contains **negative compilation tests** and scripted build checks for bad samples.
- A migration note explains how to move old code to typed endpoints.

26) **Tag 0.9 → collect feedback → 1.0**
- Cut **0.9** with these guarantees; dogfood with 1–2 teams; then freeze for **1.0** when surface is stable.

---
