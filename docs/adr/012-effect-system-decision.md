```markdown
# ADR 012 — Effect System Decision (IO/ZIO with Pure Spark)

- Status: Accepted
- Date: 2025-09-04

## Context
EffectSystem[F[_]] provides unified abstractions; docs argue for pure Spark transforms and effectful IO boundaries.

## Decision
- Keep `EffectSystem[F]` for IO/orchestration and boundaries; provide IO/ZIO instances.
- Enforce purity for Spark transformations (return datasets/values directly, no F[_]).

## Consequences
- Pros: Clear effect boundaries; testable and portable; leverages IO/ZIO where needed.
- Cons: Requires discipline and code reviews to avoid leaking F[_] into pure paths.

## Verification
- Signature audit in engines-spark to keep transforms pure; IO-only for reads/writes/Delta/CDC/table ops.

## References
- Source: `docs/design/EffectSystemResearch.md`, `AGENTS.md`
- Evidence: `docs/plans/templates/EVIDENCE.md`
- Plan: `docs/plans/templates/PLAN.md`

## End Goal (Big Picture)
- Effect polymorphism with strict purity rules, enabling engine-agnostic business logic and safe IO orchestration.

## Milestones
- M1: Leaf-module dependency slimming (see evidence/plan).
- M2: Add lint/tests for purity.

## Open Questions
- Enforcement strategy for single effect per module.
```
