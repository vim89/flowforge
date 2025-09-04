# PLAN — Engines: Spark Hardening

## 1) Scope (Minimal Viable Change)
- **Goal**: Remove in-memory fallbacks where possible, consolidate Delta path, optimize write path, and wire observability at IO boundaries.
- **Out of scope**: Flink/local engines.

## 2) Files to Touch (exact)
1. `modules/engines-spark/SparkDataAlgebra.scala` — prefer distributed ops; optimize writes; add logs/metrics.
2. `modules/engines-spark/DeltaSupport.scala` — delete or move to archive; update documentation.

## 3) Patch Sketch (pseudo-diffs)
```scala
// SparkDataAlgebra.scala (sketch)
// Replace SimpleDataset fallbacks with ProductionSparkDataset ops where encoders exist
F.delay(logger.info("read", Map("format" -> src.format.toString, "loc" -> src.path)))
val (result, dur) <- EffectSystem[F].timed(readOp)
metrics.observeLatency("read", dur)
```

## 4) Risk mitigation
- Keep fallbacks for non-Spark contexts guarded; validate encoders available.
- Add try/catch around metrics/logging to avoid failures.

## 5) Validation
- Unit tests for modified paths; manual smoke test on local Spark.

## 6) Acceptance criteria
- Transforms avoid in-memory SimpleDataset under Spark.
- Delta reflection stub removed; canonical path documented.
- Write path avoids JSON round-trip; observability present on IO ops.

