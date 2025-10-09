# PLAN - Comprehensive DX Roadmap (Dogfooding + Foundations + Feedback)

This is the master plan for delivering a batteries‑included, functional‑first data engineering toolkit with a great developer experience (DX). It merges the earlier dogfooding plan with foundations (observability, partitions, quality, CI contracts) into a single, executable roadmap.

## 1) Vision & Principles
- Simplicity first: fewer features, done extremely well.
- Functional by default: type safety, pure transforms, effectful IO via EffectSystem.
- DX focus: fast path to value; small, clear code; rapid feedback.
- Contracts‑first: compile‑time confidence + CI‑time physical gates.

## 2) KPIs (DX + Quality)
- Learning time: ≤ 30 minutes to first pipeline run.
- Code to value: ≤ 60 LoC for read → validate → transform → write.
- Feedback loop: compile+unit ≤ 5–10s; end‑to‑end sample ≤ 60–90s.
- Docs: one Quickstart page; scenario pages per feature.
- Quality: unit/IT coverage on changed code ≥ 80%; guarded ITs stable.

## 3) Scope & Non‑Goals
- In scope: examples, connectors (local/GCS/HDFS), quality (Deequ MVP), CDC (Delta SCD1/SCD2), affected partitions, observability (logs+metrics), CI contract gates, templates.
- Out of scope (for this phase): S3/Azure connectors (beyond mocks), registry integrations, full streaming infra, advanced compatibility policies.

## 4) Workstreams (with Deliverables)
1) Examples & Quickstart (DX)
   - Minimal typed batch pipeline (Parquet→transform→Parquet). Docs + tests.
   - Fast loop: `~core/testQuick`, fixtures, quick `ffValidate` demo.
   - Acceptance: ≤ 30 min to first success; LoC ≤ 60; tests green.

2) Quality (Deequ MVP → Extension)
   - MVP (done): NotNull, Unique + tests.
   - Extension: Range, Pattern; DQ timing metrics; clear messages.
   - Acceptance: new rules covered by tests; metrics emitted; CI green.

3) CDC (Delta SCD1/SCD2)
   - Guarded IT: counts correct; SCD2 columns support (customizable names).
   - Metrics around merge latency; logs summarize operation.
   - Acceptance: IT stable with `-DwithSparkIT=true`; Prometheus metrics recorded.

4) Connectors (Local, HDFS, GCS)
   - GCS mocked tests; HDFS guarded by `HDFS_URL`.
   - Clear error messages and docs for env‑based guards.
   - Acceptance: unit specs pass; guarded HDFS spec passes when configured.

5) Affected Partitions (Pluggable Interpreters)
   - Delta → FS → CloudSDK → Regex → Fallback discovery order (configurable).
   - Table ops consume derived partitions.
   - Acceptance: returns correct partitions in window; table ops act on them.

6) Observability (Logs + Metrics + Optional Tracing)
   - IO ops (`read/write/performDelta/runQualityChecks`) wrapped with timing, structured logs, counters/histograms.
   - Optional OTEL spans (no‑op safe).
   - Acceptance: logs concise and informative; metrics present; no API changes.

7) CI Contracts Submit (Forms + Validation CLI)
   - GH workflow + composite action; `ffValidate` parity.
   - Acceptance: workflow fails on drift with actionable diffs; docs show how to run locally.

8) Templates (g8)
   - Mirror templates in `modules/templates`; banner and usage; quickstart uses template.
   - Acceptance: builds unaffected; template compiles.

9) Docs (Quickstart + Scenarios)
   - `docs/quickstart.md` (copy‑paste runnable), scenario pages for Quality, CDC, Partitions, CI Contracts.
   - Acceptance: runbook validated on a clean machine.

10) Testing & Coverage
   - Unit + guarded ITs; scoverage target (on changed code) documented and tracked.
   - Acceptance: ≥ 80% on changed files; CI reports coverage.

## 5) Dogfooding Scenarios (Hands‑On Build)
1. Quickstart (Local Parquet → Transform → Parquet)
2. Quality (Deequ NotNull + Unique; then Range/Pattern)
3. CDC (Delta SCD1 + SCD2, guarded)
4. Connectors (GCS mocked; HDFS guarded)
5. Streaming CDC (2 micro‑batches)
6. Partition‑Aware Maintenance (`getAffectedPartitions` + analyze/vacuum)

## 6) Fast Feedback Loop Practices
- sbt aliases: `dev`, `devAll`, `testQuick`, `fullCheck`; watch mode `~core/testQuick`.
- Small local fixtures; no heavy IO in unit tests.
- `sbt ffValidate` delegates to validation‑cli for quick schema diffs.
- Logs: single info line per op; Prometheus metrics via simpleclient.

## 7) Execution Plan (Milestones)
MVR (Weeks 1–2)
- Examples & Quickstart; Quality MVP verified; Connectors mocked; Quickstart docs.
- Initial observability on IO ops; CI Contracts workflow skeleton.

MVP (Weeks 3–6)
- CDC SCD1/SCD2 ITs; Affected Partitions (Delta + FS); Observability expanded; CI Contracts parity.
- Quality extension (Range/Pattern) + metrics; Templates mirrored.

v1 Foundations (Weeks 7–12)
- CloudSDK interpreter (opt‑in); Streaming CDC sample; coverage gates; docs polish; perf passes on examples.

## 8) Acceptance Criteria (Global)
- DX KPIs met (Section 2).
- All scenarios runnable locally (guards applied where needed).
- Observability present for IO ops; metrics visible.
- CI Contracts workflow working with drift diffs; local `ffValidate` parity.
- ≥ 6 issues filed for identified gaps with proposed fixes (or explicit “none” per category).

## 9) Gaps Capture & Triage
- For each scenario, record: missing APIs/connectors, slow loops, confusing errors.
- Open issues with labels: `dx`, `observability`, `docs`, `connectors`, `quality`, `partitions`.
- Prioritize high‑impact small fixes first.

## 10) Risks & Mitigations
- Spark/Delta version drift: pin versions; guard ITs; document JDK matrix.
- Object store listing cost: prefer Delta/FS; bound SDK interpreter with rate limits; opt‑in only.
- Performance regressions: measure on examples; profile hot paths.
- PII in logs: standardize structured logs; mask paths when needed.

## 11) Dependencies
- Spark 3.5.x, Delta 3.x; Prometheus simpleclient for metrics.
- Optional: GCP/AWS creds for real connector tests; otherwise use mocks/guards.

## 12) References
- Evidence: docs/evidence/brutal-truth-2025-09-04.md
- Plans: docs/plan/observability.md, docs/plan/quality-mvp-extension.md, docs/plan/partitions-and-table-ops.md, docs/plan/ci-contracts-submit.md
