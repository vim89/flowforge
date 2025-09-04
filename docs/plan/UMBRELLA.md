# FlowForge Umbrella Tracker — Plans, Evidence, ADRs (2025-09-04)

This tracker ties ADRs → Evidence (current reality) → Plans (actions) with owners, status, and acceptance criteria.

Governance
- Truth cadence: refresh Evidence weekly (Fri). Link PRs to ADR/Evidence/Plan.
- DoR (Definition of Ready): Evidence exists and is up to date; big-picture goal in ADR is clear.
- DoD (Definition of Done): Acceptance criteria met; Evidence updated; ADR/INDEX updated if needed.

Milestones
- MVR: Spark local SCD1/SCD2 demo, minimal infra hooks, typed example.
- MVP: Config MVP, S3 connector, Deequ adapter, sbt schema-check, E2E ITs.
- v1: API freeze, connectors, schema evolution checks, coverage target, runbooks.

Legend
- Status: planned | in_progress | blocked | complete
- Owner: TBD unless assigned.

---

1) Modules/Templates Alignment
- ADR: docs/adr/004-modules-engines-and-templates-alignment.md
- Evidence: docs/evidence/templates-alignment.md
- Plan: docs/plan/templates-alignment.md
- Owner: TBD
- Status: planned
- Next Step: Mirror `templates/data-pipeline.g8` under `modules/templates`; add README banner.
- Dependencies: none
- Acceptance: Files present; no build.sbt changes; `sbt compile` green.

2) Quality — Deequ Adapter MVP
- ADR: docs/adr/005-quality-and-deequ-adapter.md
- Evidence: docs/evidence/quality-deequ.md
- Plan: docs/plan/quality-deequ.md
- Owner: TBD
- Status: completed
- Next Step: Implement not_null + unique adapters, add local Spark unit test.
- Dependencies: engines-spark present
- Acceptance: Unit test passes; no build.sbt structure changes.

3) Contracts Operating Model (Publisher CI + Consumer Demo)
- ADR: docs/adr/010-contracts-authoring-operating-model.md
- Evidence: docs/evidence/contracts-operating-model.md
- Plan: docs/plan/contracts-operating-model.md
- Owner: TBD
- Status: planned
- Next Step: Author `contracts-publisher-ci.md` and add compile-only consumer demo.
- Dependencies: none
- Acceptance: CI doc clear + consumer demo compiles.

4) Compile-Time & Build-Time Gates (CI-first)
- ADR: docs/adr/011-contracts-compile-build-gates.md
- Evidence: docs/evidence/compile-build-gates.md
- Plan: docs/plan/compile-build-gates.md
- Owner: TBD
- Status: completed
- Next Step: Add GitHub Actions workflow (Forms) to accept contracts; materialize typed artifacts; run validation-cli schema diff; optional sbt task delegates to CLI.
- Dependencies: local parquet/delta fixtures
- Acceptance: CI workflow fails PRs on mismatches with clear diffs; optional local task delegates to CLI. (Met, pending org variables/metadata provided by user.)

5) Effect System & Purity (Leaf Module Hygiene)
- ADR: docs/adr/012-effect-system-decision.md
- Evidence: docs/evidence/effect-per-module-and-purity.md
- Plan: docs/plan/effect-per-module-and-purity.md
- Owner: TBD
- Status: planned
- Next Step: Phase out direct IO/ZIO deps in leaf modules; rely on `EffectSystem[F]` only.
- Dependencies: Dep map in project/Dependencies.scala
- Acceptance: Grep shows no direct IO/ZIO imports in leaf modules; compile green.

6) Infrastructure Layer (Minimal Wiring)
- ADR: docs/adr/013-infrastructure-layer.md, docs/adr/007-observability-and-infra-layer.md
- Evidence: docs/evidence/infrastructure-layer.md
- Plan: docs/plan/infrastructure-layer.md
- Owner: TBD
- Status: planned
- Next Step: Use StructuredLogger and MetricsCollector in Spark IO paths; add minimal tests.
- Dependencies: engines-spark
- Acceptance: Logs/metrics visible on IO operations; tests pass.

7) QA Strategy (Add E2E ITs; Track Perf/Sec)
- ADR: docs/adr/014-qa-strategy.md
- Evidence: docs/evidence/qa-strategy.md
- Plan: docs/plan/qa-strategy.md
- Owner: TBD
- Status: planned
- Next Step: Add Spark CDC E2E integration test guarded by `-DwithSparkIT=true`.
- Dependencies: engines-spark; local env
- Acceptance: E2E test passes locally; CI stable with guard.

8) Typed Contract Pipelines Example
- ADR: docs/adr/019-typed-contract-pipelines-example.md
- Evidence: docs/evidence/typed-example.md
- Plan: docs/plan/typed-example.md
- Owner: TBD
- Status: planned
- Next Step: Add minimal typed example (core-only) and a focused test.
- Dependencies: core types
- Acceptance: Example compiles; test validates typed gate behavior.

9) Governance Loop (Evidence Cadence, PR Checklist)
- ADR: docs/adr/016-ground-reality-governance.md
- Owner: TBD
- Status: planned
- Next Step: Add PR template check for ADR/Evidence/Plan links; schedule weekly Evidence refresh.
- Acceptance: Process live; Evidence updated weekly; PRs include links.

10) Engines — Spark Hardening
- ADR: docs/adr/002-spark-purity-and-io-boundaries.md
- Evidence: docs/evidence/engines-spark.md
- Plan: docs/plan/engines-spark.md
- Owner: TBD
- Status: in_progress
- Next Step: Replace in-memory fallbacks with distributed ops; optimize write path; remove Delta reflection stub (done); add logging/metrics around IO; provide SparkDatasetOps helpers (done partial).
- Dependencies: engines-spark
- Acceptance: Minimized SimpleDataset fallbacks in Spark paths; canonical Delta path (done); observability at IO boundaries (partial). Deequ auto‑integration when present (done).

---

Notes
- Assign owners and ETAs per item; update status as work progresses.
- Keep this tracker small and current; details live in the linked Evidence/Plan documents.
