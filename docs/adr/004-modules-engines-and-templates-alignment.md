```markdown
# ADR 004 — Modules, Engines, and Templates Alignment

- Status: Proposed
- Date: 2025-09-04

## Context
Build declares modules (connectors-gcs, engines-flink, quality, quality-deequ, templates) where some module paths are empty. The g8 template exists at `templates/data-pipeline.g8` but `modules/templates` is empty.

## Decision
- Do not alter `build.sbt` structure.
- Mirror `templates/data-pipeline.g8` under `modules/templates` to avoid an empty module path (files only; no build logic changes).
- Leave engines-flink/quality/quality-deequ as stubs until staffed; document status in ALIGNMENT_STATUS.md.

## Consequences
- Pros: Clearer repo hygiene; fewer onboarding surprises.
- Cons: Slight duplication for templates folder until further consolidation.

## Verification
- Files appear under `modules/templates/**`; build graph unchanged; `sbt compile` unaffected.

## References
- Evidence: `docs/plans/templates/EVIDENCE.md` (§2)
- Plan: `docs/plans/templates/PLAN.md` (§4.1, §4.3)

## End Goal (Big Picture)
- A coherent module layout where declared modules have discoverable, meaningful content; templates are easy to find and use.

## Milestones
- M1: Mirror g8 under modules (no build changes).
- M2: Consider consolidation later once CI/UX confirms stability.

## Open Questions
- Do we want to move g8 fully under the module in a future breaking change window?
```
