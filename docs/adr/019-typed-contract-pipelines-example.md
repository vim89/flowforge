```markdown
# ADR 019 — Typed Contract Pipelines Example

- Status: Accepted
- Date: 2025-09-04

## Context
Typed contract pipeline examples illustrate compile-time enforcement using SDK-generated types and PipelineBuilder2.

## Decision
- Maintain a canonical typed example that fails to compile on schema drift and demonstrates runtime DQ checks.

## Consequences
- Pros: Clear DX; smoke test for typed path; accelerates onboarding.
- Cons: Needs upkeep as APIs evolve.

## Verification
- Example and test live under examples/core tests; CI builds ensure behavior.

## References
- Source: `docs/examples/TYPED_CONTRACT_PIPELINES.md`
- Evidence: `docs/plans/templates/EVIDENCE.md`
- Plan: `docs/plans/templates/PLAN.md`

## End Goal (Big Picture)
- A canonical typed example that acts as a regression guard and onboarding aid.

## Milestones
- M1: Example + test added (core-only).
- M2: Template wiring uses typed path by default.

## Open Questions
- How to provide negative “compile-time” tests in an automated way for Scala 2.13.
```
