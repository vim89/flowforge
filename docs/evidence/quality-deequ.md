# EVIDENCE - Quality and Deequ Adapter MVP (Status: Implemented)

## 1) Problem & Constraints
- **Goal**: Validate a minimal Deequ adapter to back our DQ claims end-to-end.
- **Non-goals**: Full rule matrix; only one or two representative rules.
- **Hard constraints**: Spark ops pure; effectful IO for checks; no build.sbt structure changes.

## 2) Codebase Recon (Updated 2025-09-05)
- **Modules involved**: `quality-deequ` (adapter implemented), `engines-spark` (auto‑invokes Deequ via reflection when present), `core/types` (QualityConstraint ADTs).
- **Key files**:
  - `project/Dependencies.scala` (Deequ version present)
  - `modules/engines-spark/SparkDataAlgebra.scala` (execution context)
  - `modules/quality-deequ` (no sources)
- **APIs / Types impacted**: Rule ADTs mapping → Deequ checks.
- **Effect boundaries**: Running checks is IO; results aggregation pure.

## 2.1) Detailed Findings
- `project/Dependencies.scala` includes a Deequ version compatible with Spark 3.5 (`2.0.11-spark-3.5`).
- `modules/quality-deequ` contains `DeequAdapter.scala` mapping NotNull and Unique to Deequ checks; returns QualityResult.
- Engines-Spark `runQualityChecks` attempts to use DeequAdapter via reflection when dataset is Spark‑backed, else falls back to functional checks over in‑memory data.
- Unit test added: `DeequAdapterSpec.scala` spins local Spark and verifies pass/fail scenarios.

## 3) Prior Art & Sources
- ADR-005 - Quality and Deequ Adapter MVP.
- QA_PLAN.md (archived) - testing strategy.

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

## 6) Success Criteria (Status: Met)
- Unit test passes running checks locally (guarded by Spark env).
- No structural build changes required.

## 7) Recommendations (Production-grade)
- Implement a thin adapter that translates a minimal, representative set of rules: `not_null(field)`, `unique(field)`, `non_negative(field)`, and a simple `range(field, min, max)`.
- Use Deequ’s `Check` and `VerificationSuite` to compute results; map failures back to our `QualityViolation` ADTs with clear, structured messages and field names.
- Keep the adapter surface decoupled from Spark (accept an abstraction or Spark DF via a small interpreter), so future engines can plug different DQ engines.
- Add metrics around DQ execution (duration, failures) using the infra `MetricsCollector`.

## 8) Next Steps (Concrete)
- Extend mappings to Range/Pattern and YAML‑to‑constraints.
- Add metrics around DQ execution.

## 9) Related Plans
- Quality MVP Extension (Range + Pattern): docs/plan/quality-mvp-extension.md
- Observability Hardening (DQ timing/metrics): docs/plan/observability.md
