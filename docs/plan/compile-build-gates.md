# PLAN — Compile-Time & Build-Time Gates

## 1) Scope (Minimal Viable Change)
- **Goal**: Add minimal sbt tasks to diff physical schemas vs contracts and fail builds on mismatch.
- **Out of scope**: Registry compatibility checks.

## 2) Files to Touch (exact)
1. `project/ContractValidationPlugin.scala` — AutoPlugin with tasks `ffVerifySourcePhysical`, `ffVerifyTargetPhysical`.
2. `modules/examples/src/test/scala/.../PhysicalCheckSpec.scala` — smoke tests for tasks (mock or local parquet files).

## 3) Patch Sketch (pseudo-diffs)
```scala
// project/ContractValidationPlugin.scala (sketch)
object ContractValidationPlugin extends AutoPlugin {
  val ffVerifySourcePhysical = taskKey[Unit]("...")
  // implement parquet/delta schema fetch and diff
}
```

## 4) Risk mitigation
- Fast schema-only reads; offline flag; guard heavy tasks with env vars.

## 5) Validation
- Run tasks locally; ensure failure on intentional mismatches.

## 6) Acceptance criteria
- Tasks exist and fail builds on mismatches; no build graph change.
