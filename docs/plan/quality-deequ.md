# PLAN — Quality and Deequ Adapter MVP

## 1) Scope (Minimal Viable Change)
- **Goal**: Implement minimal Deequ adapter supporting `not_null` and `unique`, with a local Spark unit test.
- **Out of scope**: Complete rule matrix; registry integration.

## 2) Files to Touch (exact)
1. `modules/quality-deequ/src/main/scala/.../DeequAdapter.scala` — map core rule ADTs → Deequ checks.
2. `modules/quality-deequ/src/test/scala/.../DeequAdapterSpec.scala` — local Spark test for two rules.

## 3) Patch Sketch (pseudo-diffs)
```scala
// DeequAdapter.scala (sketch)
trait DeequAdapter[F[_]] {
  def checkNotNull[A](field: String, ds: Dataset[A]): F[CheckResult]
  def checkUnique[A](field: String, ds: Dataset[A]): F[CheckResult]
}
```

## 4) Risk mitigation
- Guard Spark session in tests; small fixtures only.

## 5) Validation
- `sbt test` runs `DeequAdapterSpec` locally.

## 6) Acceptance criteria
- Two checks pass; no build.sbt structural changes.
