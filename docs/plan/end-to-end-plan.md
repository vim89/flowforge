# FlowForge 1.0 Ship Plan - precise, end-to-end, FP-first (Scala 2.13, Spark 3.5/4.0 ready)

> This is a hands-on checklist that maps tasks to **concrete files/paths** in your repo (`/flowforge/flowforge`). It favors **compile-time contracts**, **pure transforms**, **effect safety**, **small clear APIs**, and **fast inner loops**. No new libraries; only version bumps if truly necessary.

---

## 0) Ground rules (the guardrails)
- **Keep Spark transforms pure.** Only external I/O and orchestration use `F[_]`.
- **Minimize effect abstraction.** Prefer Cats-Effect typeclasses at edges; Spark code stays pure.
- **One builder to rule them all.** Canonical `PipelineBuilder` with phantom-state gating.
- **Every patch compiles.** Green gate: `sbt test` + compile-fail tests + docs links.
- **Prove claims with tests and examples.** “Won’t compile if contract drifts” must be demonstrated.

Repo facts you’ll touch most:
modules/core/src/main/scala/com/flowforge/core/...
modules/contracts/src/main/scala/com/flowforge/contracts/...
modules/compile-fail-tests/src/test/scala/com/flowforge/compilefail/CompileTimeContractFailSpec.scala
modules/engines-spark/src/main/scala/com/flowforge/engines/spark/...
modules/quality-deequ/src/main/scala/com/flowforge/quality/deequ/...
modules/lineage/src/main/scala/com/flowforge/lineage/OpenLineageEmitter.scala
flowforge.g8/src/main/g8/...
ops/marquez/docker-compose.yml
.github/workflows/{ci.yml,release.yml}


---

## 1) Canonize the public DSL (collapse to a single compile-time-safe builder)

**Files to edit**
- `modules/core/src/main/scala/com/flowforge/core/PipelineBuilder.scala`
- `modules/engines-spark/src/main/scala/com/flowforge/engines/spark/SparkPipelineBuilder.scala` (deprecate type-erased builder here; keep Spark behind the algebra)
- `modules/core/src/main/scala/com/flowforge/core/types/BuilderState.scala` (phantom states)
- `modules/core/src/main/scala/com/flowforge/core/types/DataTypes.scala` (source/sink/format ADTs)

**What to do**
- Keep **only** `PipelineBuilder[F, In, Out]` as public entrypoint. Its **phantom states** gate `.build` until `HasSource with HasContract with HasTransform with HasSink` is satisfied.
- Move Spark specifics behind `DataAlgebra`/`Engine` interfaces. Public API is stable and engine-agnostic.
- Mark anything not meant for users as `internal` package (e.g., `com.flowforge.core.internal`).

**Why**
- One mental model → lower learning time; public surface becomes teachable and stable.

---

## 2) Effect system: trim to essentials

**Files**
- `modules/core/src/main/scala/com/flowforge/core/algebra/EffectSystem.scala`
- `modules/core/src/main/scala/com/flowforge/core/instances/EffectInstances.scala`

**Edits**
- Reduce to the **ops you actually need** (edges only):  
  `pure`, `map`, `flatMap`, `raiseError`, `handleErrorWith`, `bracket`, `fromFuture`, `sleep`, `realTime`, `parallelTraverse`, and a thin `Resource` bridge.
- For any module that already requires Cats-Effect, accept `Async[F]` directly instead of re-abstracting.

**Litmus**
- Pure Spark transforms have **no** `F` in signatures. External I/O and orchestration do.

---

## 3) Contracts that fail at compile time (end-to-end)

**Files**
- `modules/core/src/main/scala/com/flowforge/core/contracts/derive/Shape.scala` (Magnolia)
- `modules/core/src/main/scala/com/flowforge/core/contracts/SchemaConforms.scala`
- `modules/contracts/src/test/scala/com/flowforge/contracts/SchemaConformsCompileSpec.scala`
- `modules/compile-fail-tests/src/test/scala/com/flowforge/compilefail/CompileTimeContractFailSpec.scala`

**Tasks**
1. **Finish Magnolia derivation** (it’s already present) for `Shape[T]` to reflect field names/types/defaults.
2. In `SchemaConforms`, ensure policies: `Exact`, `Backward`, `Forward` map to field-level rules.
3. **Turn the 3 negative tests ON**. In `CompileTimeContractFailSpec.scala`, replace “Uncomment to test compile failure” with **actual compile-fail assertions** using typechecker tools or sbt scripted tests:
   - **Missing sink** → builder `.build` has no implicit `Complete`.
   - **Schema mismatch** (missing field / wrong type) under `Exact`.
   - **Illegal evolution** `User -> UserWithAge` under `Exact` (should fail).
4. Keep two **positive** tests:
   - Valid `Exact` compiles and runs.
   - `Backward` accepts superset output.

**Result**
- Your core USP is enforced in CI as a first-class gate.

---

## 4) Data quality & enforcement (compose, don’t re-invent)

**Files**
- `modules/quality-deequ/src/main/scala/com/flowforge/quality/deequ/DeequAdapter.scala`
- `modules/engines-spark/src/main/scala/com/flowforge/engines/spark/SparkAdapters.scala`
- Add docs: `docs/quality/README.md`

**Tasks**
- **Default runtime checks to real engines**:
  - Keep a thin adapter to **Deequ (Spark 3.5 build)**; use it for profiling and rules where useful.
  - For sinks, show **Delta constraints** (`NOT NULL`, `CHECK`) as *enforcement*.  
- Provide a tiny example that maps a contract rule → Deequ check **and** a Delta constraint at the table:
  - Contract says `email nonEmpty` → Deequ `isComplete("email")` + Delta `CHECK (length(email) > 0)`.

**DX**
- `ff check` runs compile-fail tests, then runs a small Spark job (local[*]) that logs DQ summary.

---

## 5) Lineage: “on by default”

**Files**
- `modules/lineage/src/main/scala/com/flowforge/lineage/OpenLineageEmitter.scala`
- `ops/marquez/docker-compose.yml`
- Hook inside `PipelineBuilder.build` to emit start/finish/failure

**Tasks**
- Wire `OpenLineageEmitter` to pipeline lifecycle:
  - On stage start/finish/failure, emit job/run/dataset events.
- Provide `make lineage-up` script → `docker compose -f ops/marquez/docker-compose.yml up -d`.
- Docs: “Run a pipeline and open Marquez UI at http://localhost:3000”.

**Outcome**
- First run shows lineage without extra user code. That’s memorable.

---

## 6) Connectors breadth (just enough for credibility)

**Tasks**
- Make sure **gcs** connector with the same `FileSystemConnector[F]` shape:
  - `read(path)`, `write(path, bytes)`, `list(prefix)`, `exists(path)`
- Keep **retry/backoff** at edge using `EffectSystem[F].retry` or `Resource` + simple retry helpers.
- Add smoke tests that run offline (mock clients) and one opt-in integration test per cloud (skipped by default).

---

## 7) Engines: prove "same business logic, different runner"

**Files**
- `modules/engines-flink/src/main/scala/...` (empty → fill)
- `modules/engines-spark/src/main/scala/...`

**Tasks**
- Implement a **minimal Flink runner demo** that:
  - Reuses the same domain transforms (pure functions).
  - Reads a small stream (e.g., `SocketTextStream`) and writes to a file sink.
- Provide a shared algebra test that both engines pass (compile-time composition) to prove "engine-agnostic API".

---

## 8) Developer experience (fast inner loop)

**Files**
- `flowforge.g8/src/main/g8/...`
- `dev.sh`, `scripts/ff` (sbt aliases)

**Tasks**
- Make the g8 template turnkey:
  - **Contract.scala**: instruct the user to tweak a type and watch `sbt compile` fail with a **clear error**.
  - **Pipeline.scala**: 30 lines showing `source → transform → quality → sink`.
  - Tiny **fixtures** (CSV/Parquet, KBs) for instant runs.
- SBT aliases (either in template `build.sbt` or root):
  - `ff dev` → local run with fixtures in <3s.
  - `ff check` → compile-fail tests + contract diff check.
  - `ff runSpark` → Spark local[*] run with DQ and a Delta sink.

**Docs snippet for users**

sbt new flowforge/quickstart.g8
cd my-pipeline
sbt test           # includes compile-fail tests
sbt "ff dev"       # local run fast
sbt "ff runSpark"  # Spark local\[\*], Deequ checks + Delta constraints

---

## 9) CI & release

**Files**
- `.github/workflows/ci.yml`
- `.github/workflows/release.yml`

**Tasks**
- CI stages:
  1) `sbt scalafmtCheckAll scalafixAll`
  2) `sbt test` (includes **compile-fail**)
  3) Quick Spark run (local mode) on tiny fixture as smoke test
- Release:
  - Tag `v0.9.0-rc1` → publish artifacts + site docs.
  - Keep **SemVer policy** in `docs/versioning.md` and mark experimental packages.

---

## 10) Docs that teach (no oversell, lots of proof)

**Files**
- `README.md` (trim hype → show demos)
- `docs/why/compare.md` (Frameless / Scio / Soda / GE / Delta / Iceberg / OpenLineage)
- `docs/operating/runbook.md` (SLOs, dashboards, retries, DLQs, rollbacks)

**Contents**
- “Why contracts at compile-time?” with a **live edit** gif from the template.
- “Why not re-implement DQ?” because engines + proven libs enforce at runtime; we **compose** them.
- “Why lineage by default?” because visibility prevents silent failure.

---

## 11) Example: tiny but real pipeline (idiomatic FP)

**Where**: `flowforge.g8/src/main/g8/src/main/scala/{Contract.scala, Pipeline.scala}`

**Contract.scala (Magnolia-derived shape, compile-time drift demo)**
```scala
package $organization$.$name;format="packaged"$

import com.flowforge.core.contracts.derive.Shape

object ContractShapes {
  final case class User(id: Long, name: String, email: String)
  implicit val userShape: Shape[User] = Shape.gen[User] // Magnolia derivation
}

**Pipeline.scala (pure transforms, effects at edges)**

```scala
package $organization$.$name;format="packaged"$

import cats.effect.{IO, IOApp}
import com.flowforge.core.PipelineBuilder
import com.flowforge.core.contracts.SchemaPolicy
import com.flowforge.core.instances.EffectInstances._
import com.flowforge.core.types.TypedIO._
import ContractShapes._

object Main extends IOApp.Simple {
  def run: IO[Unit] = {
    val p =
      PipelineBuilder[IO]("users-pipeline")
        .addTypedSource[User, User, SchemaPolicy.Exact](
          gcsParquetSource[User]("gs://demo-bucket/raw/users/*.parquet"),
          // decoder is effectful edge; downstream is pure
          _ => IO.pure(User(1, "Ada Lovelace", "ada@math.org"))
        )
        .addTransform[User](u => IO.pure(u.copy(name = u.name.toUpperCase))) // can be pure as well
        .quality(nonNull("email") and unique("id"))
        .addTypedSink[User, SchemaPolicy.Exact](
          deltaSink[User]("dbfs:/tables/users_clean"),
          (_, dq) => IO.println(s"DQ summary: $dq")
        )
        .build

    p.run // effectful at the boundary
  }
}
```

**What juniors learn immediately**

* Change `id: Long` → `id: String` in `Contract.scala` and watch **compile** break with a specific message.
* Add `age: Int` → `Backward` policy compiles; `Exact` does not.
* DQ and sink constraints live **outside** the core builder and are **composed** at edges.

---

## 12) File-by-file “do this now” checklist

* [ ] **Enable negative tests**
  `modules/compile-fail-tests/.../CompileTimeContractFailSpec.scala` → replace commented examples with real compile-fail assertions (scripted tests or typelevel plugin).
* [ ] **Stabilize DSL**
  Consolidate on `modules/core/.../PipelineBuilder.scala`; move Spark glue behind algebra. Deprecate `engines-spark/SparkPipelineBuilder.scala` public exposure (keep internal).
* [ ] **Trim EffectSystem**
  Keep only essential ops (see §2). Prefer `Async[F]` at edges.
* [ ] **DQ default**
  In `quality-deequ/DeequAdapter.scala`, call real Deequ checks for Spark runs; keep Spark-native fallbacks only when Deequ isn’t available.
* [ ] **Delta constraints example**
  Add one sink example with `NOT NULL` and `CHECK` mapped from contract.
* [ ] **Lineage lifecycle hooks**
  Call `OpenLineageEmitter` at stage boundaries in `PipelineBuilder.build`.
* [ ] **Flink demo**
  Fill `modules/engines-flink/src/main/scala/...` with a tiny runner and a demo job reusing the same pure transforms.
* [ ] **g8 polish**
  Ensure the template compiles, fails on contract drift, and runs a tiny local job in <3s.
* [ ] **CI gates**
  `.github/workflows/ci.yml`: add a step to run the compile-fail suite; add a Spark local smoke test.
* [ ] **Docs**
  `docs/why/compare.md` with neutral comparisons; `docs/operating/runbook.md` for production ops.

---

## 13) Fast feedback loop (what the user types)

```
# 1) Scaffold (teaches compile-time drift immediately)
sbt new flowforge/quickstart.g8
cd my-pipeline
sbt compile                # success
# edit Contract.scala: change id: Long -> id: String
sbt compile                # FAILS with contract drift error (as designed)

# 2) Run locally fast
sbt "ff dev"               # runs on tiny fixtures in <3s

# 3) Spark run + DQ + constraints
sbt "ff runSpark"          # Spark local[*], shows DQ summary and writes Delta sink with constraints

# 4) Lineage
docker compose -f ops/marquez/docker-compose.yml up -d
# run again; open Marquez UI to see job/run/datasets
```

---

## 14) What makes FlowForge special (the crisp core)

* **Compile-time contracts** that literally won’t let you build an invalid pipeline.
* **Pure transforms + effect-safe edges**, so tests are fast and logic is portable across engines.
* **Composable runtime enforcement** (Delta/Iceberg constraints, Deequ) instead of re-inventing DQ.
* **Lineage by default** so you can see what happened, not guess.
* **A frictionless g8 template** that proves all of the above in minutes.

Ship the above and you can fairly claim a real niche: *contract-first, effect-safe pipelines with first-class CI gates and instant lineage*.

**References & notes**

- Spark 3.5 supports Scala 2.12/2.13; Spark 4.0 is Scala 2.13-only (plan your cross-build & upgrade path). :contentReference[oaicite:0]{index=0}  
- Deequ has Spark-3.5 builds (2.0.11+) you can target for profiling & checks. :contentReference[oaicite:1]{index=1}  
- Delta Lake constraints (`NOT NULL`, `CHECK`) provide table-level enforcement; DLT expectations add pipeline-level DQ. :contentReference[oaicite:2]{index=2}  
- Iceberg supports robust schema evolution you can map to your `Exact/Backward/Forward` policies. :contentReference[oaicite:3]{index=3}  
- Comparative landscape for docs: **Frameless** (typed Spark), **Scio** (Beam), **OpenLineage/Marquez** (lineage), **Soda** & **Great Expectations** (DQ). Use them as points of integration, not targets to replace. :contentReference[oaicite:4]{index=4}
