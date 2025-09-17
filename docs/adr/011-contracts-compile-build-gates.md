```markdown
# ADR 011 — Contracts Compile-Time & Build-Time Gates

- Status: Accepted
- Date: 2025-09-04

## Context
Deliver fail-fast behavior: code fails to compile when types diverge from contracts; builds fail when physical storage drifts.

## Decision
- Compile-time gates: typed endpoints (PipelineBuilder2) + SchemaEq/SchemaConforms witnesses remain the primary type-level enforcement in code.
- Build-time/CI validation: Contracts are submitted by non-technical stakeholders via GitHub Actions Forms. CI materializes contracts (typed artifacts/codegen where needed) and runs schema validation (CLI) against physical sources before merge. This is the authoritative gate for physical schemas.
- No sbt AutoPlugin: Local checks, when needed, invoke the same CLI directly (e.g., via an sbt input task). We avoid a separate plugin to remove drift and reduce maintenance.

## Consequences
- Pros: Errors surface before runtime (in CI); non-engineers can author contracts; clear audit trail via PRs; no duplicate logic in a plugin.
- Cons: CI complexity increases; codegen/typed artifact lifecycle needs stewardship; local runs simply delegate to the CLI.

## Verification
- Code compiles only when typed witnesses resolve.
- CI workflow triggered by contract submission PR:
  - Validates contract shape -> generates typed artifacts -> runs `validation-cli` schema diff on physical sources.
  - Fails PR on mismatches with actionable diffs.

## References
- Source: `docs/archive/design/CONTRACTS_COMPILE_AND_BUILD_GATES.md`
- Evidence: `docs/evidence/compile-build-gates.md`
- Plan: `docs/plan/compile-build-gates.md`
- Supersedes local plugin/codegen suggestions in prior designs; see ADR‑021 for CI templating and `.avsc`/case class generation.

## End Goal (Big Picture)
- Fail-fast pipeline lifecycle: compile-time typed gates + build-time physical validation across Delta/Hive/Parquet/JDBC.

## Milestones
- M1: CI workflow with GitHub Actions Forms for contract submission + CLI schema diff (Parquet/Delta/Hive/JDBC).
- M2: Codegen/materialization of typed artifacts from submitted contracts wired into CI (branch artifacts or PR files).
- M3: Local developer task delegates directly to CLI; no sbt plugin maintained.

## Open Questions
- Where to host codegen and compatibility checks (this repo vs separate plugin repos)?

## Appendix: Source Notes (archive/design/CONTRACTS_COMPILE_AND_BUILD_GATES.md)

- Source: `docs/archive/design/CONTRACTS_COMPILE_AND_BUILD_GATES.md` @ 877826bbf05636a3db581e425901fc490cac224d on 2025-09-04T17:18:50+05:30
- Summary: Defines the two-tier gating approach: compile-time typed witnesses (SchemaEq with PipelineBuilder2) and build-time physical schema validation via sbt tasks (Delta/Hive/Parquet/JDBC). Includes developer workflow, error messaging, and migration strategy.
```
