# EVIDENCE — QA Strategy and Testing

## 1) Problem & Constraints
- **Goal**: Confirm QA layers exist (property/law/integration) and planned perf/security are tracked.
- **Non-goals**: Adding perf/security suites in this patch.

## 2) Codebase Recon
- **Modules involved**: core tests, engines-spark tests.
- **Key files**:
  - `modules/core/src/test/**` (property and law tests exist)
  - `modules/engines-spark/src/test/**` (partial/disabled ITs)
- **Findings**: Good property/law coverage; integration breadth limited; no perf/security.

## 2.1) Detailed Findings
- Core tests: `EffectInstancesLawsSpec`, `PipelinePropertyTests`, and `EffectSystemContractSpec` provide strong law/property baselines.
- Engines-spark: `StreamingCDCSpec` exists; SCD2 IT present but disabled/opt-in; Deequ tests absent.
- No explicit perf/security tests in-tree; maintenance.yml hints at benchmarks but no working module.

## 3) Prior Art & Sources
- ADR-014; archived QA_PLAN.md.

## 4) Options & Trade-offs
| Option | Pros | Cons | Why |
|---|---|---|---|
| Add minimal E2E ITs | Raises confidence | Time to author fixtures | Accepted
| Defer perf/security | Focus now | Risks linger | Accepted (track)

**Decision sketch**: Add 1–2 high-value E2E tests; track perf/security as follow-ups.

## 5) Edge Cases & Invariants
- Guard Spark ITs behind flags to avoid heavy CI cost.

## 6) Success Criteria
- At least one E2E IT for Spark CDC path; CI stable.

## 7) Recommendations (Production-grade)
- Add one Spark CDC E2E IT that covers SCD2 path on a small local Delta table; gate via `-DwithSparkIT=true`.
- After Deequ MVP, add a unit test running not_null/unique on local data.
- Track perf/security tests in a separate doc with scope, triggers, and ownership; keep them out of PR CI by default.

## 8) Next Steps (Concrete)
- Implement `SparkCDCIntegrationSpec` that writes a tiny source/target Delta table and validates close/open semantics and counts.
- Add `DeequAdapterSpec` (after adapter exists) covering two rules.
- Create `docs/design/qa-followups.md` outlining perf/security suite definitions and nightly schedule.
