```markdown
# ADR 005 - Quality and Deequ Adapter MVP

- Status: Proposed
- Date: 2025-09-04

## Context
Quality module is declared; Deequ integration is planned but currently missing. We need a minimal adapter to validate an end-to-end DQ claim and enable incremental growth.

## Decision
- Implement a minimal `quality-deequ` adapter that maps a single representative rule (e.g., not_null/unique) to Deequ, scoped to local Spark for tests.

## Consequences
- Pros: Demonstrable DQ capability; unlocks examples and CI samples.
- Cons: Adds Spark runtime to tests; careful scoping required.

## Verification
- Add a focused unit test that runs a Deequ check on a small local Dataset.

## References
- Evidence: `docs/evidence/quality-deequ.md` (§3)
- Plan: `docs/plan/quality-deequ.md` (§4.3)

## End Goal (Big Picture)
- Data quality framework with a minimal Deequ adapter to validate end-to-end claims, expandable over time.

## Milestones
- M1: not_null + unique checks implemented and tested locally.
- M2: Expand rule coverage based on need.

## Open Questions
- Which rule set is core vs optional modules?
```
