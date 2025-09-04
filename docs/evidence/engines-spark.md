# EVIDENCE — Engines: Spark Hardening

## 1) Problem & Constraints
- **Goal**: Ensure Spark engine uses distributed ops for transforms, consistent Delta MERGE path, efficient writes, and uniform observability.
- **Non-goals**: Flink in this pass.
- **Hard constraints**: Pure transforms; effectful IO; no structural build changes.

## 2) Codebase Recon
- **Key files**: `modules/engines-spark/SparkDataAlgebra.scala`, `modules/engines-spark/DeltaSupport.scala`, `modules/engines-spark/ProductionSparkDataset.scala`.
- **Findings**:
  - Real SCD1 (Parquet) and Delta MERGE/SCD2 logic exist; pre-merge counts computed; optional zOrder calls.
  - Some transforms fallback to `SimpleDataset` (in-memory) when not working with `ProductionSparkDataset`.
  - JSON writes go through stringification then re-read for Parquet, adding overhead.
  - `DeltaSupport.scala` reflection path is a stub returning Left; not used by main algebra which uses direct Delta APIs.
  - Observability (metrics) present for CDC latency only; logging not uniformly applied; tracing absent.

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
- Remove or archive `DeltaSupport.scala` reflection stub; consolidate on direct Delta usage (already used in core algebra).
- Expand observability: add structured logging and metrics at start/end of read/write/merge ops (counts, durations, formats, locations); use tracing spans as optional hooks.

## 5) Next Steps (Concrete)
- Audit transform methods and replace `SimpleDataset` fallbacks with Spark DataFrame/Dataset equivalents when possible.
- Refactor write path to avoid JSON round-trip; use proper encoders and `write.mode(...).format(...)`.
- Delete or archive `DeltaSupport.scala`; update docs to reflect canonical Delta path.
- Add logger/metrics calls around IO ops (effect-safe); use `EffectSystem.timed` for latency.

