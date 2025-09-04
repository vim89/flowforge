```markdown
# ADR 014 — QA Strategy and Testing

- Status: Accepted
- Date: 2025-09-04

## Context
A pyramid testing strategy is documented with property tests, law tests, integration tests, and planned perf/security.

## Decision
- Maintain property-based and law tests for effect system and core types; add integration tests for engines/connectors; plan perf/security suites.

## Consequences
- Pros: High confidence in FP laws; end-to-end validation; guardrails for regressions.
- Cons: Increased CI time; selective gating required.

## Verification
- CI jobs reflect categories; coverage and property iteration thresholds enforced.

## References
- Source: `docs/design/QA_PLAN.md`
- Evidence: `docs/plans/templates/EVIDENCE.md`
- Plan: `docs/plans/templates/PLAN.md`

## End Goal (Big Picture)
- A layered QA approach with property/law tests, integration E2E, and planned perf/security coverage.

## Milestones
- M1: Add 1–2 E2E tests (Spark CDC).
- M2: Define perf/security suites and triggers.

## Open Questions
- Nightly/per-PR balance for heavy tests.
```
