```markdown
# ADR 002 — Spark Purity and Effectful IO Boundaries

- Status: Accepted
- Date: 2025-09-04

## Context
We enforce that Spark transformations remain pure (return `Dataset[A]`-like values), while all external IO (read/write, Delta/CDC, table ops, CLIs) is encoded in `F[_]` via `EffectSystem`. Current SparkDataAlgebra follows this pattern.

## Decision
- Maintain pure signatures for transformations (`map/flatMap/filter/join/groupBy` etc.).
- Keep read/write/Delta/table ops in `F[_]` using `EffectSystem[F]`.

## Consequences
- Pros: Clear effect boundaries; performance and composability in pure Spark paths; testability.
- Cons: Requires careful API review; some operations may need refactoring to move side effects to boundaries.

## Verification
- Review `modules/engines-spark/SparkDataAlgebra.scala` to ensure purity/effects separation.
- Add tests that validate signatures remain pure for transforms and effectful for IO.

## References
- Evidence: `docs/plans/templates/EVIDENCE.md` (§2, §6)
- Plan: `docs/plans/templates/PLAN.md`
- House Rules: `AGENTS.md` (Effect System Implementation Rules)

## End Goal (Big Picture)
- All Spark transformations remain pure across engines; IO boundaries are explicit and uniformly instrumented.
- Pipeline orchestration composes pure stages with effectful IO cleanly.

## Milestones
- M1: Signature audit complete; transforms pure in engines-spark (mostly achieved).
- M2: Add purity tests/linters; prevent regressions.
- M3: Instrument IO boundaries with infra (ADR-013).

## Open Questions
- Do we formalize “pure transform” contracts in type signatures for all engines, including Flink/local?
```
