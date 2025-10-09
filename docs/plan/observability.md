# PLAN - Observability Hardening (Spark IO + DQ)

## 1) Scope (Minimal Viable Change)
- Instrument engine IO boundaries and DQ runs with structured logs and metrics; keep APIs unchanged.
- Targets: `read`, `write`, `performDelta`/CDC, and `runQualityChecks` in Spark engine; adapter timing in `quality-deequ`.

## 2) Deliverables
- Logs: operation, engine, format, location (sanitized), counts, duration, outcome.
- Metrics (Prometheus):
  - Counters: `data.read.total`, `data.write.total`, `data.cdc.total`, `dq.run.total`.
  - Histograms: `op_latency_ms{op,engine}`, `dq_latency_ms`.
  - Gauges (optional): `inflight_ops` by op.
- OTEL (optional): start/end spans for IO ops via infra hooks; no-op safe.

## 3) Tasks
1. Wrap Spark IO ops with `EffectSystem.timed` and log+metric emission:
   - `modules/engines-spark/SparkDataAlgebra.scala`: `read`, `write`, `performDelta`, `runQualityChecks` (around success and failures).
   - Standardize metric labels: `{ op, engine, format, outcome }`.
2. Use `StructuredLogger` for logs; keep payload PI I-safe (paths masked where necessary).
3. Add basic metrics in `quality-deequ/DeequAdapter` (duration, violations count).
4. Wire optional OTEL spans via `InfrastructureLayer.DistributedTracing` (no behavior change if disabled).

## 4) Tests
- Unit: smoke tests that wrappers execute and preserve results (assertions on behavior only).
- IT (guarded): run Delta MERGE IT and verify metrics increment via lightweight hook (best-effort, non-flaky).

## 5) Risks & Mitigations
- Overhead: keep logging at info with compact messages; use histograms with reasonable buckets; avoid per-row logs.
- PII: mask sensitive segments in locations; include only aggregate counts in logs.

## 6) Validation & Acceptance
- Logs appear for IO ops with duration and outcome.
- Metrics counters/histograms register for successful and failing runs.
- No API changes; `sbt fullCheck` green.

