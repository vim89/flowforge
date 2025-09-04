```markdown
# ADR 015 — Scaffolding vs Production Policy

- Status: Accepted
- Date: 2025-09-04

## Context
Audit documents identify a pattern of placeholder implementations being treated as production-ready.

## Decision
- Establish a policy: interfaces may land with scaffolding, but must be marked clearly; production endpoints require benchmarks, tests, and observability; claim-heavy docs must link to Evidence.

## Consequences
- Pros: Prevents overstatement; improves trust; focuses work.
- Cons: Slows “demo” velocity; requires continuous curation.

## Verification
- Lint or CI checks for TODO/??? in production modules; documentation banners; ADR references.

## References
- Source: `docs/design/SCAFFOLDING_VS_PRODUCTION_AUDIT.md`
- Evidence: `docs/plans/templates/EVIDENCE.md`
- Plan: `docs/plans/templates/PLAN.md`

## End Goal (Big Picture)
- Clarity between scaffold vs production; enforce via banners, CI checks, and ADR/Evidence discipline.

## Milestones
- M1: Banners + archive (completed).
- M2: CI check for TODO/??? in production modules.

## Open Questions
- Thresholds and exceptions for experimental modules.
```
