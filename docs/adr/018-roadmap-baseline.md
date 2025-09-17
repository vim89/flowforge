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
- Source: `docs/archive/design/RoadmapProposal.md`
- Plan: `docs/plan/UMBRELLA.md`

## End Goal (Big Picture)
- Clear path MVR → MVP → v1, mapped to ADR/Evidence-backed work items with acceptance criteria.

## Milestones
- MVR: Spark local demo (SCD1/SCD2, example, minimal infra).
- MVP: Config MVP, S3, Deequ adapter, ITs.
- v1: API freeze, connectors, schema evolution, coverage targets.

## Open Questions
- Team capacity/timelines; acceptance criteria per milestone.

## Appendix: Source Notes (archive/design/RoadmapProposal.md)

- Source: `docs/archive/design/RoadmapProposal.md` @ 877826bbf05636a3db581e425901fc490cac224d on 2025-09-04T17:18:50+05:30
- Summary: Details staged delivery (MVR → MVP → v1), acceptance criteria, and reality corrections for overstated completeness; aligns milestones with implementation focus areas and QA gates.

## Appendix: Source Notes (archive/design/IMPLEMENTATION_TODO.md)

- Source: `docs/archive/design/IMPLEMENTATION_TODO.md` @ 877826bbf05636a3db581e425901fc490cac224d on 2025-09-04T17:18:50+05:30
- Summary: Enumerates detailed implementation tasks across modules; serves as backlog feeding roadmap milestones and planning.
```
