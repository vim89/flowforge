# PLAN - QA Strategy and Testing

## 1) Scope (Minimal Viable Change)
- **Goal**: Add 1–2 E2E integration tests; track perf/security as follow-ups.
- **Out of scope**: Full perf/security suites.

## 2) Files to Touch (exact)
1. `modules/engines-spark/src/test/scala/.../SparkCDCIntegrationSpec.scala` - small CDC E2E.
2. `docs/design/qa-followups.md` - list perf/security test plan.

## 3) Patch Sketch (pseudo-diffs)
```scala
// SparkCDCIntegrationSpec.scala (sketch)
it("scd2 merge closes/open rows correctly") { ... }
```

## 4) Risk mitigation
- Guard with `-DwithSparkIT=true` flag.

## 5) Validation
- Test passes locally when enabled.

## 6) Acceptance criteria
- At least one E2E test added and green locally.
