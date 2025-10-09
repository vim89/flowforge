# EVIDENCE - Infrastructure Layer

## 1) Problem & Constraints
- **Goal**: Validate infra layer services exist and are wired (config, logging, metrics, tracing, resource safety, testing harness).
- **Non-goals**: Full OTEL/micrometer in this pass.
- **Hard constraints**: Effectful boundaries only; no build.sbt structural edits.

## 2) Codebase Recon
- **Modules involved**: `infrastructure` (logging, tracing, metrics, config, safety), engines-spark.
- **Key files**:
  - `modules/infrastructure/src/main/scala/com/flowforge/**`
  - `modules/engines-spark/SparkDataAlgebra.scala` (metrics hooks)
- **Findings**:
  - Traits present; partial/default impls; wiring not end-to-end.

## 2.1) Detailed Findings (TODO hotspots)
- MetricsCollector.scala: Multiple TODOs for real Prometheus integration (counters, gauges, timers, tags).
- DistributedTracing.scala: TODOs for OTEL span lifecycle and tagging/baggage propagation.
- ConfigurationManagement.scala: TODO for config watching and proper decoding; current decoder is a placeholder.
- SparkDataAlgebra.scala: Middleware for logging/metrics is best-effort; tracing not present; logging not uniformly applied to IO.

## 3) Prior Art & Sources
- ADR-013; archived INFRASTRUCTURE_LAYER.md.

## 4) Options & Trade-offs
| Option | Pros | Cons | Why |
|---|---|---|---|
| Implement minimal, wire engines/connectors | Tangible value | Some duplication initially | Accepted
| Leave as traits only | None now | Claims remain unmet | Rejected

**Decision sketch**: Provide minimal working impls + usage wiring in Spark IO paths.

## 5) Edge Cases & Invariants
- Avoid logging PII; ensure effect-safe finalizers.

## 6) Success Criteria
- Structured logging and metrics increment in IO ops; config load examples; compile/test green.

## 7) Recommendations (Production-grade)
- Logging: Use a thin SLF4J/log4cats wrapper; add MDC helpers for pipeline/context IDs; avoid logging PII.
- Metrics: Integrate Prometheus client with labeled counters/histograms for IO ops (read/write/merge), errors, and latencies; avoid failing business flow on metrics errors.
- Tracing: Add minimal OTEL span wrappers around IO boundaries with operation names and key tags (source/sink formats, counts); make it optional.
- Config: Provide a simple type-safe loader with validation and environment overrides; add a watch stub with FS2/ZIO Streams for future hot-reload.
- Wiring: Add lightweight middleware in SparkDataAlgebra IO ops to log begin/end and record metrics; include timing helpers in EffectSystem.timed.

## 8) Next Steps (Concrete)
- Implement Prometheus-backed `MetricsCollector` with labeled counters: `data.read.total`, `data.write.total`, `data.cdc.merge.total`, and histograms `data.op.latency_ms`.
- Add `StructuredLogger` usage in Spark read/write/CDC; include context map with `engine`, `format`, `path`/`table`.
- Introduce minimal OTEL tracing in `DistributedTracing` (create/end span; set error on failure) and a no-op default.
- Enhance `ConfigurationManagement` with a basic Typesafe Config loader and a validation step; watch stub via FS2.
