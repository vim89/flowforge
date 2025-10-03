# FlowForge — Revised Plan to Reach **100% Compile-Time Contracts**
> Implements your refactor: **`PipelineBuilder2` → `PipelineBuilder`** (canonical), legacy **`PipelineBuilder` → `LegacyPipelineBuilder`** (deprecated). Everything below drives the repo to a state where **every public path** enforces contracts at compile time and bad pipelines are literally **unbuildable**.

---

## Phase 0 — Refactor & Freeze (Day 0–1)

- [ ] **Rename & deprecate**
    - Move `modules/core/types/PipelineBuilder2.scala` → `modules/core/PipelineBuilder.scala` (canonical).
    - Move old `modules/core/PipelineBuilder.scala` → `modules/core/internal/LegacyPipelineBuilder.scala`.
    - Annotate `LegacyPipelineBuilder` with `@deprecated(message = "Use PipelineBuilder (typed) instead", since = "0.9.0")`.
    - Replace package imports in codebase: `types.PipelineBuilder2` → `PipelineBuilder`.

- [ ] **Public vs. internal**
    - Create `docs/public-api.md` listing public packages (e.g. `com.flowforge.core`, `...contracts`, `...io.TypedIO`) and mark `...internal` as non-API.
    - Add a scalafix check (deny-list) that forbids importing `...internal` from examples, templates, or public modules.

- [ ] **Versioning stance**
    - Set `versionScheme := Some("early-semver")` (sbt-version-policy).
    - Add a short `DEPRECATION_POLICY.md` (how long legacy stays; migration windows).

---

## Phase 1 — Evidence-Driven Builder (Week 1)

- [ ] **Schema policies (already present, ensure complete)**
    - `SchemaPolicy = Exact | ExactUnordered | Backward | Forward | Full`, each documented with allowed changes.

- [ ] **Typeclass evidence with great errors**
    - Ensure `SchemaEq`/`SchemaConforms[Out, Contract, P]` are annotated with `@implicitNotFound` and produce:
        - policy name,
        - missing/extra fields,
        - mismatched types,
        - fix-hint URL (`docs/how-it-fails.md#${policy}`).

- [ ] **Attach evidence at the right time**
    - **Sources:** `addTypedSource[R, C, P](src, contract, policy)(implicit ev: SchemaConforms[R, C, P])`.
    - **Sinks:**   `addTypedSink  [R, C, P](sink, contract, policy)(implicit ev: SchemaConforms[R, C, P])`.
    - No untyped overloads remain on the public builder.

- [ ] **Phantom-state gating**
    - Stages: `HasSource`, `HasContract`, `HasTransform`, `HasSink`.
    - `.build` requires `S <:< (HasSource with HasContract with HasTransform with HasSink)`.

---

## Phase 2 — Endpoints, Codegen & Transforms (Week 2)

- [ ] **Typed endpoints everywhere**
    - Public constructors yield only `TypedSource[R]` / `TypedSink[R]`.
    - Provide `TypedIO.*` helpers (e.g., `gcsParquetSource[R]`, `deltaSink[R]`) per connector.

- [ ] **External contracts → codegen**
    - sbt task generates `case class Contract_vN` from Avro/JSON Schema into `src_managed/`.
    - Builder then consumes `Contract_vN` as `C` for compile-time checks.

- [ ] **Typed transforms**
    - Keep kernels pure (`A => B`); when transforms change `R`→`R2`, subsequent sinks re-check `SchemaConforms[R2, C, P]`.

---

## Phase 3 — Templates & Docs (Week 3)

- [ ] **Quickstart that fails on purpose**
    - New `flowforge-quickstart.g8`:
        1) Initial compile **fails** (one field drift).
        2) User fixes field → compile passes.
        3) Run local job, tiny CSV/Parquet fixtures.

- [ ] **Examples sweep**
    - All examples use the canonical `PipelineBuilder` and `TypedIO.*`.
    - Include one `Backward` policy example (optional field added) that **still compiles** under `Backward/Full`.

- [ ] **Docs**
    - `docs/how-it-fails.md` — screenshots of compile errors per policy + “1-line fix”.
    - `docs/why-compile-time.md` — contrast with dbt/Dagster (engine/CI-time) and emphasize FlowForge’s compile-time guarantee.

---

## Phase 4 — Tests that Prove It (Week 3–4)

- [ ] **Negative compilation tests (unit)**
    - MUnit/ScalaTest spec:
        - Missing sink ⇒ does **not** compile.
        - Source `R` ≠ `C` under `Exact` ⇒ does **not** compile.
        - Illegal evolution under `Backward/Forward` ⇒ does **not** compile.
    - Also assert positive cases (`assertCompiles` / `assertNoDiff(compileErrors(...))`).

- [ ] **Scripted build tests (integration)**
    - `sbt scripted` projects:
        - `good/` samples compile & run.
        - `bad/` samples must **fail** compilation with golden error messages.

- [ ] **CI gates**
    - Workflow steps: `scalafmtCheckAll`, `scalafixAll --check`, unit tests, **negative compile tests**, coverage. Upload docs artifacts (error screenshots).

---

## Phase 5 — Repo-Wide Lockdown (Week 4)

- [ ] **Remove escape hatches**
    - Deprecate or hide any untyped source/sink constructor from public packages.
    - Add a scalafix rule to forbid builder/sink/source usage without a `SchemaPolicy`.

- [ ] **Migration assist**
    - Provide a scalafix rewrite:
        - `types.PipelineBuilder2` → `PipelineBuilder`
        - `core.PipelineBuilder`  → `internal.LegacyPipelineBuilder`
        - Insert `TypedIO.*` wrappers where trivial.

---

## Phase 6 — Ergonomics (Week 5)

- [ ] **Error message polish**
    - Pretty-print field diffs and include a “Fix:” hint (e.g., “add `zipCode: Option[String]` or use `Backward` policy”).

- [ ] **Docs link in errors**
    - Embed a short URL in `@implicitNotFound` that jumps to the right section in `how-it-fails.md`.

---

## Phase 7 — Hardening & Release Prep (Week 5–6)

- [ ] **Policy law tests**
    - Property tests over witness derivation to prove allowed/forbidden changes per policy.

- [ ] **Binary/API safety**
    - Enable sbt-version-policy (MiMa under the hood).
    - Mark all `...internal` packages clearly; keep public surface stable for 1.0.

- [ ] **Tag & announce**
    - Cut **0.9.0** with all of the above; run with design partners.
    - Tag **1.0.0** after feedback, with a clean migration guide and the Legacy builder on a clear deprecation timer.

---

### Footnotes & Nice-to-haves
- A tiny `:why-not-compiling` dev command that prints the inferred `R` vs `C` (Scala 3 later via Mirrors).
- A “contract-aware OpenLineage” emitter and Deequ-backed `AssetCheck` API can ship separately; they don’t block compile-time guarantees.
