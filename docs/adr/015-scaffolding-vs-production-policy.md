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
- Source: `docs/archive/design/SCAFFOLDING_VS_PRODUCTION_AUDIT.md`
- Plan: `docs/plan/UMBRELLA.md`

## End Goal (Big Picture)
- Clarity between scaffold vs production; enforce via banners, CI checks, and ADR/Evidence discipline.

## Milestones
- M1: Banners + archive (completed).
- M2: CI check for TODO/??? in production modules.

## Open Questions
- Thresholds and exceptions for experimental modules.

## Appendix: Source Notes (archive/design/SCAFFOLDING_VS_PRODUCTION_AUDIT.md)

- Source: `docs/archive/design/SCAFFOLDING_VS_PRODUCTION_AUDIT.md` @ 877826bbf05636a3db581e425901fc490cac224d on 2025-09-04T17:18:50+05:30
- Summary: Audits where placeholder implementations were presented as production; introduces policy for banners, evidence linking, and CI checks to prevent overstatement.

## Appendix: Source Notes (archive/design/Rules_Notes.md)

- Source: `docs/archive/design/Rules_Notes.md` @ 877826bbf05636a3db581e425901fc490cac224d on 2025-09-04T17:18:50+05:30
- Summary: Consolidated house rules and guidelines overlapping with AGENTS.md; captured here to retain policy context and historical rationale.
```
