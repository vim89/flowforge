# PLAN - Typed Contract Pipelines Example

## 1) Scope (Minimal Viable Change)
- **Goal**: Add a minimal typed example + test using core-only typed path.
- **Out of scope**: Engine runtime.

## 2) Files to Touch (exact)
1. `modules/examples/src/main/scala/.../TypedContractsDemo.scala`
2. `modules/core/src/test/scala/.../TypedContractsCompileSpec.scala` - negative compile test (schema drift) if feasible, else runtime assertion on witness resolution.

## 3) Patch Sketch (pseudo-diffs)
```scala
// TypedContractsDemo.scala (sketch)
val p = PipelineBuilder2.empty[F].addTypedSource(...).addTransform(...).addTypedSink(...)
```

## 4) Risk mitigation
- Keep independent of engines; tiny code footprint.

## 5) Validation
- `sbt test` runs demo test; example compiles.

## 6) Acceptance criteria
- Example present; test validates typed path behavior.
