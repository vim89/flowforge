# PLAN — Infrastructure Layer

## 1) Scope (Minimal Viable Change)
- **Goal**: Provide minimal working implementations for logging, metrics, and config; wire Spark IO paths.
- **Out of scope**: Full tracing/lineage system.

## 2) Files to Touch (exact)
1. `modules/infrastructure/src/main/scala/.../StructuredLogger.scala` — ensure effectful logging with MDC helpers.
2. `modules/infrastructure/src/main/scala/.../MetricsCollector.scala` — counters/timers used by engines.
3. `modules/engines-spark/SparkDataAlgebra.scala` — use logger/metrics around reads/writes/CDC.

## 3) Patch Sketch (pseudo-diffs)
```scala
// SparkDataAlgebra.scala (sketch)
F.delay(logger.info("write", Map("format" -> sink.format.toString)))
```

## 4) Risk mitigation
- Keep null-safe MDC; swallow metrics errors.

## 5) Validation
- Unit tests around logger/metrics helpers; manual run in local job path.

## 6) Acceptance criteria
- Visible logs/metrics on IO ops; tests compile and pass.
