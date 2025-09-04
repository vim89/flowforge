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
- Source: `docs/design/CONTRACTS_COMPILE_AND_BUILD_GATES.md`
- Evidence: `docs/plans/templates/EVIDENCE.md`
- Plan: `docs/plans/templates/PLAN.md`

## End Goal (Big Picture)
- Fail-fast pipeline lifecycle: compile-time typed gates + build-time physical validation across Delta/Hive/Parquet/JDBC.

## Milestones
- M1: Minimal sbt tasks for schema diff.
- M2: Integrate tasks in template example flows.

## Open Questions
- Where to host codegen and compatibility checks (this repo vs separate plugin repos)?
```
