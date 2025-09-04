# EVIDENCE — Quality and Deequ Adapter MVP

## 1) Problem & Constraints
- **Goal**: Validate a minimal Deequ adapter to back our DQ claims end-to-end.
- **Non-goals**: Full rule matrix; only one or two representative rules.
- **Hard constraints**: Spark ops pure; effectful IO for checks; no build.sbt structure changes.

## 2) Codebase Recon
- **Modules involved**: `quality-deequ` (declared, empty), `engines-spark`, `core/patterns` (ValidatedNel rules).
- **Key files**:
  - `project/Dependencies.scala` (Deequ version present)
  - `modules/engines-spark/SparkDataAlgebra.scala` (execution context)
  - `modules/quality-deequ` (no sources)
- **APIs / Types impacted**: Rule ADTs mapping → Deequ checks.
- **Effect boundaries**: Running checks is IO; results aggregation pure.

## 2.1) Detailed Findings
- `project/Dependencies.scala` includes a Deequ version compatible with Spark 3.5 (`2.0.11-spark-3.5`).
- There is no `modules/quality-deequ` source tree; no glue code to translate our core ValidatedNel rules to Deequ’s Check APIs.
- No tests exercising Deequ checks exist; QA plan mentions E2E and property tests only.
- Engines-Spark provides a natural execution context for Deequ checks; we can create small local DataFrames for unit tests.

## 3) Prior Art & Sources
- ADR-005 — Quality and Deequ Adapter MVP.
- QA_PLAN.md (archived) — testing strategy.

## 4) Options & Trade-offs
| Option | Pros | Cons | Why |
|---|---|---|---|
| Minimal adapter (not_null, unique) | Quick win; proves claim | Limited scope | Accepted
| Full adapter matrix | Comprehensive | Larger effort; delay | Defer
| Skip adapter | No complexity | Unmet claims | Rejected

**Decision sketch**: Implement minimal adapter + unit test with local Spark.

## 5) Edge Cases & Invariants
- Large datasets: test on tiny local fixtures only.
- Nullability and uniqueness errors: map to domain ADTs.

## 6) Success Criteria
- A unit test passes running one Deequ check locally.
- No new build.sbt structural changes.

## 7) Recommendations (Production-grade)
- Implement a thin adapter that translates a minimal, representative set of rules: `not_null(field)`, `unique(field)`, `non_negative(field)`, and a simple `range(field, min, max)`.
- Use Deequ’s `Check` and `VerificationSuite` to compute results; map failures back to our `QualityViolation` ADTs with clear, structured messages and field names.
- Keep the adapter surface decoupled from Spark (accept an abstraction or Spark DF via a small interpreter), so future engines can plug different DQ engines.
- Add metrics around DQ execution (duration, failures) using the infra `MetricsCollector`.

## 8) Next Steps (Concrete)
- Create `modules/quality-deequ/src/main/scala/.../DeequAdapter.scala` with mappings for the 3–4 rules above.
- Add `modules/quality-deequ/src/test/scala/.../DeequAdapterSpec.scala` that spins a local SparkSession and runs checks on tiny DataFrames (guard memory use).
- Ensure the adapter returns a unified `QualityCheckResult` with field, rule, and message; aggregate results into a `QualityResult` compatible with core.
