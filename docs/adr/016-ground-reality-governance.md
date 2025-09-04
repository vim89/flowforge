```markdown
# ADR 016 — Ground Reality & Alignment Governance

- Status: Accepted
- Date: 2025-09-04

## Context
Ground reality reports and alignment status docs reconcile claims vs implementation and guide roadmap.

## Decision
- Maintain an authoritative EVIDENCE.md; update weekly alignment; ensure reference docs include reality banners and pointers.

## Consequences
- Pros: Single source of truth; continuous honesty; better planning.
- Cons: Ongoing maintenance.

## Verification
- Evidence and alignment docs updated on cadence; PR checklist includes “link to Evidence”.

## References
- Source: `docs/design/GROUND_REALITY_REPORT.md`, `docs/design/GROUND_REALITY_REPORT_FULL.md`, `docs/design/ALIGNMENT_STATUS.md`, `docs/design/PRODUCTION_REALITY_UPDATE.md`
- Evidence: `docs/plans/templates/EVIDENCE.md`
- Plan: `docs/plans/templates/PLAN.md`

## End Goal (Big Picture)
- A living governance loop tying truth (Evidence), decisions (ADRs), and actions (Plans) with disciplined updates.

## Milestones
- M1: Weekly Evidence refresh cadence.
- M2: PR checklist requires ADR/Evidence links.

## Open Questions
- Who owns the weekly refresh; how to enforce in CI.
```
