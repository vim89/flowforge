```markdown
# ADR 011 — Contracts Compile-Time & Build-Time Gates

- Status: Accepted
- Date: 2025-09-04

## Context
Deliver fail-fast behavior: code fails to compile when types diverge from contracts; builds fail when physical storage drifts.

## Decision
- Compile-time gates: typed endpoints (PipelineBuilder2) + SchemaEq/SchemaConforms witnesses.
- Build-time validation: sbt tasks to diff storage schema (Delta/Hive/Parquet/JDBC) vs contracts pre-compile.

## Consequences
- Pros: Errors surface before runtime; safer pipelines.
- Cons: Adds codegen and sbt plugin complexity; local offline mode required.

## Verification
- Example project compiles only when witnesses resolve; CI tasks fail with clear diffs on storage drift.

## References
- Source: `docs/archive/design/CONTRACTS_COMPILE_AND_BUILD_GATES.md`
- Evidence: `docs/evidence/compile-build-gates.md`
- Plan: `docs/plan/compile-build-gates.md`

## End Goal (Big Picture)
- Fail-fast pipeline lifecycle: compile-time typed gates + build-time physical validation across Delta/Hive/Parquet/JDBC.

## Milestones
- M1: Minimal sbt tasks for schema diff.
- M2: Integrate tasks in template example flows.

## Open Questions
- Where to host codegen and compatibility checks (this repo vs separate plugin repos)?

## Appendix: Source Notes (archive/design/CONTRACTS_COMPILE_AND_BUILD_GATES.md)

- Source: `docs/archive/design/CONTRACTS_COMPILE_AND_BUILD_GATES.md` @ 877826bbf05636a3db581e425901fc490cac224d on 2025-09-04T17:18:50+05:30
- Summary: Defines the two-tier gating approach: compile-time typed witnesses (SchemaEq with PipelineBuilder2) and build-time physical schema validation via sbt tasks (Delta/Hive/Parquet/JDBC). Includes developer workflow, error messaging, and migration strategy.
```
