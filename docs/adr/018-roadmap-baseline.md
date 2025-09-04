```markdown
# ADR 018 — Roadmap Baseline (MVR → MVP → v1)

- Status: Accepted
- Date: 2025-09-04

## Context
Roadmap proposes staged delivery from a Minimum Viable Run to MVP to v1.0.0.

## Decision
- Adopt staged roadmap: MVR (Spark local SCD1/SCD2 + demo), MVP (+config, S3, Deequ), v1 (+connectors, schema evolution, coverage targets).

## Consequences
- Pros: Clear milestones and expectations; avoids overreach.
- Cons: Requires disciplined scoping.

## Verification
- Alignment status tracked; milestone acceptance criteria defined.

## References
- Source: `docs/design/RoadmapProposal.md`
- Evidence: `docs/plans/templates/EVIDENCE.md`
- Plan: `docs/plans/templates/PLAN.md`

## End Goal (Big Picture)
- Clear path MVR → MVP → v1, mapped to ADR/Evidence-backed work items with acceptance criteria.

## Milestones
- MVR: Spark local demo (SCD1/SCD2, example, minimal infra).
- MVP: Config MVP, S3, Deequ adapter, ITs.
- v1: API freeze, connectors, schema evolution, coverage targets.

## Open Questions
- Team capacity/timelines; acceptance criteria per milestone.
```
