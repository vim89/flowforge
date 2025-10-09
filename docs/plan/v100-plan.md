# Here’s the blunt, file-level snapshot first, then the “what to do to get to v1.0.0” with *stepwise* fixes that align with our plans.

---

# What’s in the repo **right now** (post-refresh)

* **Footprint scanned:** 13,047 files · 232 `.scala` · 268 Markdown · 24 workflows.
* **Plans read:** 29 files under `docs/plan/*` including `UMBRELLA.md`, `v10-5.md`, `v10-6.md`, `v10-plan.md`, `End-to-End-Compile-time.md`, `compile-build-gates.md`, `effect-per-module-and-purity.md`, `engines-spark.md`, `quality-deequ.md`, `100-percent-compile-time-contracts.md`.
* **Good news:** plans are concrete and map to code paths. Many ADRs and evidence docs line up with the “contracts-first, F-polymorphic core, Deequ/Delta/lineage” story.

## P0 code issues still present (hard blockers for v1.0.0)

These contradict the “all fixed” claims and also diverge from our own plans.

1. **Duplicate method defs & unfinished emitter**

    * `modules/lineage/.../OpenLineageEmitter.scala`
      Contains **three copies** each of `emitJobStart/emitJobComplete/emitJobFail`. Needs dedupe and a single `F` instance strategy. (I also saw a comment-style truncation earlier; the duplicates alone are a compile smell.)

2. **Placeholders in main sources**

    * `modules/engines-spark/.../SparkAdapters.scala` has literal placeholder lines (e.g., `SparkTypes { ... }`) and ellipses in writer logic.
    * `modules/core/.../contracts/SchemaConforms.scala` includes a placeholder message with `Missing: ... | Extra: ...` (string is fine if it’s just formatting, but audit it).
    * `modules/quality-deequ/.../ContractToDeltaExample.scala` includes `...` in SQL examples (and claims a **UNIQUE** constraint in comments-see below).

3. **Contract SDK generator truncated**

    * `modules/contracts-extractor-cli/.../ContractSdkGenerator.scala` stops mid-definition (`version: String, ...`) and a partial method (`extractNamespace(avroJ...`). This is a showstopper if the CLI is part of v1.0.

4. **Effect leaks in main sources (minor, but against our plan)**

    * `modules/quality-deequ/src/main/scala/.../ContractToDeltaExample.scala` imports `cats.effect.IO` in **main** (it’s an example, but it lives in main). Our plan says examples should be isolated; core/leaf modules stay **F-polymorphic**.

5. **Spark/Delta/Deequ pinning**

    * `project/Dependencies.scala` pins **Spark = 3.5.0**; our plan and reality suggest moving to **3.5.6** (current 3.5 LTS maintenance) for stability. ([Apache Spark][1], [endoflife.date][2])
    * Deequ is already on `2.0.12-spark-3.5` (good). ([Maven Repository][3])

6. **Delta constraint claims vs. actual capability**

    * Comments/examples imply **UNIQUE**; Delta supports **NOT NULL** and **CHECK** (no enforced UNIQUE). If you want uniqueness, do it in **Deequ** (logical) or **merge-dedupe** (physical), and document that clearly. ([Delta Lake][4], [Databricks Documentation][5], [Delta Lake][6])

> These P0s are small in number but high in blast radius. They’re also already acknowledged in our **v10 plan docs**-so the fix is mostly to **finish what our own plan prescribes**.

---

# Is it v1.0.0-ready?

**Not yet.** But you have a precise path to green that matches `docs/plan/*`. Below is the **reconciled, step-by-step cut plan**-each step points to the files and the plan it satisfies.

## Step-by-step to a credible v1.0.0 (mapped to our plans)

### 1) Clean and freeze the minimal v1.0 surface (our **v10-plan**, **UMBRELLA**)

* **Scope IN:** `core`, `contracts`, `builder`, `quality-deequ`, `lineage`, `engines-spark`, `examples` (single golden path), `flowforge.g8`, docs, validation/compile-fail tests, `validation-cli`.
* **Scope OUT / experimental:** `engines-flink`, `examples-spark` (merge into `examples`), `experimental/*`, complex CDC beyond SCD1. Mark them **post-1.0**.

### 2) Finish lineage emitter and wire lifecycle (**v10-plan**, **End-to-End-Compile-time**)

* **Fix** `modules/lineage/.../OpenLineageEmitter.scala`:

    * Dedupe the three sets of methods into **one** implementation.
    * Require `Sync[F]`/`Async[F]` via a context bound (`trait OpenLineageEmitter[F[_]: Async]`).
    * Provide **two transports**:

        * `NoopEmitter[F]` (default)
        * `HttpEmitter[F]` (Marquez/OpenLineage endpoint)
* **Wire events** in `PipelineBuilder` (or our single canonical builder):

    * `emitJobStart` at pipeline start, `emitJobComplete` on success, `emitJobFail` on errors, and **per-stage** events with input/output dataset hints.
* **Docs + ops**: add a **Marquez** docker-compose and a one-pager (`docs/operating/lineage.md`) showing the events in the UI. ([OpenLineage][7], [marquezproject.ai][8])

### 3) Align DQ mapping & physical constraints (**quality-deequ plan**, **engines-spark plan**)

* **Deequ adapter**: map our contract rule ADTs → Deequ checks (at least `not null`, `pattern`, `range`, `uniqueness`). Pin Deequ to `2.0.12-spark-3.5`. ([Maven Repository][3])
* **Delta**: implement **NOT NULL** and **CHECK** creation/update on the target Delta table. **Do not** imply **UNIQUE**-document uniqueness as Deequ logical checks + optional dedupe on write path. ([Delta Lake][4], [Databricks Documentation][5])

### 4) Effect purity per module (**effect-per-module-and-purity plan**)

* Ensure all **core/engine** code paths are **F-polymorphic** (no `IO` imports in `src/main`).
* Move `ContractToDeltaExample.scala` into `examples/` or mark it as example-only submodule so it doesn’t contaminate leaf modules.

### 5) Contracts source-of-truth & codegen (**ADR-021**, **compile-build-gates plan**)

* **Finish** `ContractSdkGenerator.scala` (currently truncated). It should:

    * Parse Avro/contract inputs; generate typed case classes and **Refined** constraints (as per our plan), and drop artifacts in a predictable location.
* **CI flow**: provide the “contracts submit” workflow that materializes/validates schema in PR and fails on drift (our plan already sketches this).

### 6) Golden path **examples** module, not kitchen sink (**v10-6**, **dx-dogfooding**)

* Keep **one** E2E example: CSV → typed contract → Deequ checks → Delta with **NOT NULL/CHECK** → lineage events (noop by default).
* Write an **integration test** that runs the whole path and asserts:

    * DQ summary JSON exists and the checks pass
    * Delta table shows constraints
    * Lineage calls executed (or noop)
* **Delete/merge** `examples-spark` into `examples` to avoid duplication.

### 7) Template (**flowforge.g8**) as the 10-minute win (depends on libs; don’t vendor features)

* Make the template depend on **published** FlowForge modules; keep it **minimal**.
* Add a **scripted/g8Test** in CI: generate → compile → run → see success text.
* Optional scaffolds (`g8Scaffold contract`, `pipeline`) for progressive adoption.

### 8) Versions & compatibility (documented)

* Pin **Spark 3.5.6** as default (stable/LTS line); keep a **Spark 4.0.1** job as **tech preview** (allowed-to-fail). ([Apache Spark][1])
* Document the tested **Delta** and **Deequ** versions and the “no UNIQUE” limitation with the prescribed workaround. ([Delta Lake][4], [Databricks Documentation][5])

### 9) CI that enforces the philosophy (from **compile-build-gates**)

* Jobs in order:

    1. **lint** (`scalafmtCheckAll`, `scalafix --check`)
    2. **+compile** (2.12 + 2.13 if needed)
    3. **unit tests**
    4. **it: golden path**
    5. **g8 smoke**
    6. **docs/mdoc** (snippets import from `examples/`)
    7. **spark-4 preview** (allow fail)
* Gates:

    * **Ban placeholders**: fail CI if `...` or `???` found in `src/main/scala`.
    * **Ban IO** in F-polymorphic modules: grep for `cats.effect.IO` under `src/main/scala` (except `examples`/`cli`).

That’s it. If you execute those nine steps, the tag is legit.

---

# Architectural & design gaps (and specific fixes)

* **Emitter lifecycle not part of the builder boundary.**
  *Fix:* put `OpenLineageEmitter[F]` into the `PipelineBuilder` wiring and emit per-stage events.

* **Multiple example modules & “examples in main”.**
  *Fix:* keep a single `examples/` and move all example code there; main source sets stay library-grade.

* **UNIQUE constraint mismatch.**
  *Fix:* document plainly: **Delta only enforces NOT NULL/CHECK**; for uniqueness use Deequ `uniqueness` checks and optionally implement a merge-dedupe write path. ([Delta Lake][4], [Databricks Documentation][5])

* **Version drift vs ecosystem.**
  *Fix:* bump to **Spark 3.5.6**; keep Deequ at `2.0.12-spark-3.5`; add Spark-4 preview job. ([Apache Spark][1])

* **Effect hygiene at the edges.**
  *Fix:* apply your own “effect-per-module” plan; keep IO/ZIO only in app/cli/examples; core stays parametric.

* **Template vs examples split.**
  *Fix:* template stays minimal; examples act as the **living spec** driven by CI and used by `mdoc`.

---

# Is FlowForge unique?

**Your wedge is real-if you ship it end-to-end.**

* **What’s unique (in Scala):** a **contracts-first** approach that fans out into **Deequ DQ**, **Delta constraints**, and **OpenLineage** events from a **single source of truth**. Scala has type-safety projects (e.g., Frameless), but not this *integrated*, contracts-as-product story. ([GitHub][9], [typelevel.org][10])
* **Where others play:**

    * **Dagster** → amazing asset checks and orchestration, mostly Python. ([Dagster Docs][11], [Dagster][12])
    * **Kedro** → great scaffolding and ML pipeline discipline, also Python. ([Kedro][13], [Kedro Documentation][14])
    * **Scio (Beam)** → Scala API for Beam/Dataflow, portability powerhouse, different model. ([Spotify Open Source][15], [Apache Beam][16])
    * None of these deliver **compile-time contract → DQ + physical constraints + lineage** in Scala.

**Conclusion:** You can credibly be the first **Scala “contracts → enforcement ×3”** framework, *provided* the golden path runs out of the box.

---

# How to make it unmistakably first (and keep it simple)

* **Adopt an open contract spec as the human face**
  Import/export **ODCS** or **Data Contract Specification** (YAML) while maintaining **typed contracts** internally. That lets platform teams standardize and still get compile-time Scala goodness. ([Bitol][17], [Data Contract Specification][18])

* **Ship a one-command CI gate**
  `sbt flowforge:verify` runs: compile-time contract checks, generates Deequ checks, syncs Delta constraints (dry-run), and emits a lineage smoke. Fail fast with actionable diffs.

* **Marquez in a box**
  Include a tiny `ops/marquez/docker-compose.yml` and a doc screenshot. The ability to *see* lineage on day one is a trust accelerator. ([OpenLineage][7], [marquezproject.ai][8])

---

# Developer Experience (the “10-minute joy”)

* **Learning time low:** `sbt new`, `sbt run`, see:
  `Contracts compiled ✅`, `Deequ checks ✅`, `Delta constraints ✅`, `OpenLineage START/COMPLETE ✅` (noop by default).
* **Small code to add:** scaffold commands (`g8Scaffold contract`), plus a tiny `PipelineMain.scala`.
* **Fast loop:** `sbt ~testQuick` (unit <10s); `it:test` (E2E <90s); `mdoc` compiles doc snippets pulled from `examples/`.

---

# Competitive calibration (external facts)

* **Delta constraints: NOT NULL & CHECK**; no enforced UNIQUE in Delta-document and handle accordingly. ([Delta Lake][4], [Databricks Documentation][5])
* **Spark baselines:** 3.5.6 is the latest 3.5 maintenance release; 4.0.1 exists but should be “tech preview” until deps catch up. ([Apache Spark][1])
* **Deequ:** current Spark-3.5 builds are available and in active use. ([Maven Repository][3])
* **OpenLineage/Marquez:** reference impl for lineage ingestion & UI. ([OpenLineage][19])

---

# Final, un-polished checklist (copy straight into issues)

**P0 - must pass before tag**

* [ ] **OpenLineageEmitter.scala**: dedupe methods; add `F[_]: Async`; implement `Noop` + `Http` transports; wire `PipelineBuilder` to emit START/COMPLETE/FAIL + per-stage. ([OpenLineage][19])
* [ ] **SparkAdapters.scala**: remove ellipses; complete `SparkTypes`, writers, and factories per plan.
* [ ] **ContractSdkGenerator.scala**: finish the codegen (no `...`), implement `extractNamespace` and write outputs deterministically.
* [ ] **Placeholder ban**: CI check fails on `...`/`???` in `src/main/scala`.
* [ ] **Effect purity**: no `cats.effect.IO` in **main** for core/engines; move example code to `examples/`.
* [ ] **UNIQUE reality**: replace any UNIQUE claims with Deequ `uniqueness` + optional merge-dedupe; document “no UNIQUE in Delta” in `docs/compat.md`. ([Delta Lake][4])
* [ ] **Version pins**: Spark **3.5.6**, Deequ `2.0.12-spark-3.5`; add Spark-4.0.1 **allow-fail** CI job. ([Apache Spark][1])
* [ ] **Golden path E2E**: one `examples/` pipeline that asserts Deequ report, Delta constraints, and lineage (noop default).
* [ ] **flowforge.g8 smoke**: `g8Test` generates → compiles → runs success text.

**P1 - DX & docs**

* [ ] Quickstart with 4 commands + expected outputs/screenshots. See [quick guide](../getting-started-quick.md) and [architecture overview](../diagrams/overview.svg).
* [ ] `mdoc` wired to `examples/` so doc snippets compile in CI.
* [ ] Compatibility matrix (Spark/Delta/Deequ) and limitations (no UNIQUE). ([Delta Lake][4])
* [ ] Comparison page vs Frameless/Dagster/Kedro/Scio-what FlowForge uniquely provides (contracts → enforcement ×3). ([GitHub][9], [Dagster Docs][11], [Kedro][13], [Spotify Open Source][15])

---

## Brutal truth (with love)

The **vision is excellent** and your **plans are solid**-but v1.0.0 can’t ship with duplicate lineage methods, ellipses in Spark adapters, and a truncated codegen CLI. Finish those, narrow to the **one immaculate Spark 3.5 path**, and make the **contract → (DQ + Delta + lineage)** story boringly repeatable. Then the “Scala needs a batteries-included functional data engineering kit” isn’t a slogan-it’s Tuesday at 10am, on a laptop, working.

[1]: https://spark.apache.org/downloads.html "Downloads | Apache Spark"
[2]: https://endoflife.date/apache-spark "Apache Spark"
[3]: https://mvnrepository.com/artifact/com.amazon.deequ/deequ "com.amazon.deequ"
[4]: https://docs.delta.io/latest/delta-constraints.html "Constraints - Delta Lake Documentation"
[5]: https://docs.databricks.com/aws/en/tables/constraints "Constraints on Databricks"
[6]: https://delta.io/blog/2022-11-21-delta-lake-contraints-check/ "Delta Lake Constraints and Checks"
[7]: https://openlineage.io/getting-started/ "Getting Started"
[8]: https://marquezproject.ai/ "Marquez Project | Marquez Project"
[9]: https://github.com/typelevel/frameless "typelevel/frameless: Expressive types for Spark."
[10]: https://typelevel.org/frameless/FeatureOverview.html "TypedDataset: Feature Overview"
[11]: https://docs.dagster.io/guides/test/asset-checks "Testing assets with asset checks"
[12]: https://dagster.io/blog/dagster-asset-checks "Introducing Dagster Asset Checks"
[13]: https://kedro.org/ "Kedro | An open-source framework for data science code"
[14]: https://docs.kedro.org/en/0.19.14/tutorial/create_a_pipeline.html "Create a data processing pipeline"
[15]: https://spotify.github.io/scio/ "Scio"
[16]: https://beam.apache.org/documentation/sdks/scala/ "Apache Beam Scala SDK"
[17]: https://bitol-io.github.io/open-data-contract-standard/v3.0.2/ "Definition: Open Data Contract Standard (ODCS)"
[18]: https://datacontract.com/ "Data Contract Specification | Data contracts bring data providers and ..."
[19]: https://openlineage.io/docs/ "About OpenLineage"
