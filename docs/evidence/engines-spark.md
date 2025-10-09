# EVIDENCE - Engines: Spark Hardening

## 1) Problem & Constraints
- **Goal**: Ensure Spark engine uses distributed ops for transforms, consistent Delta MERGE path, efficient writes, and uniform observability.
- **Non-goals**: Flink in this pass.
- **Hard constraints**: Pure transforms; effectful IO; no structural build changes.

## 2) Codebase Recon
- **Key files**: `modules/engines-spark/SparkDataAlgebra.scala`, `modules/engines-spark/ProductionSparkDataset.scala`.
- **Findings**:
  - Real SCD1 (Parquet) and Delta MERGE/SCD2 logic exist; pre-merge counts computed; optional zOrder calls.
  - Some transforms fallback to `SimpleDataset` (in-memory) when not working with `ProductionSparkDataset`.
  - JSON writes go through stringification then re-read for Parquet, adding overhead.
  - Delta reflection stub removed; Spark engine consolidates on direct Delta APIs.
  - Observability: metrics present for CDC latency previously; now logging + latency metrics added for read/write; tracing still absent.

## 3) Options & Trade-offs
| Option | Pros | Cons | Why |
|---|---|---|---|
| Use distributed Dataset ops for transforms | Scales | Requires encoder management | Accepted
| Keep JSON stringify + re-read | Simplicity | Perf overhead & type loss | Rejected
| Rely on direct Delta API | Stable & clear | Adds compile dep | Accepted
| Use reflection path for Delta | Avoid dep | Complex; fragile | Rejected

## 4) Recommendations (Production-grade)
- Prioritize `ProductionSparkDataset` paths for all transforms under Spark and avoid `SimpleDataset` fallback when a Spark session and encoders are available.
- For writes, prefer encoders and direct DataFrame/Dataset write APIs rather than JSON stringify + re-read.
- Consolidate on direct Delta usage (done; reflection stub removed).
- Expand observability: add structured logging and metrics at start/end of read/write/merge ops (counts, durations, formats, locations); use tracing spans as optional hooks. [DONE: read/write logging + latency metrics]

## 5) Next Steps (Concrete)
- Audit transform methods and replace `SimpleDataset` fallbacks with Spark DataFrame/Dataset equivalents when possible. [PARTIAL: take/drop use DataFrame limit/except; union uses unionByName]
- Refactor write path to avoid JSON round-trip; use proper encoders and `write.mode(...).format(...)`.
- Reflection stub removed; docs updated to reflect canonical Delta path.
- Add logger/metrics calls around IO ops (effect-safe); use `EffectSystem.timed` for latency. [DONE]

## 6) Related Plans
- Observability Hardening: docs/plan/observability.md
- Partitions & Table Ops: docs/plan/partitions-and-table-ops.md
