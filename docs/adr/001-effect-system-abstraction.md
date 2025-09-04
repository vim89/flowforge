```markdown
# ADR 001 — Unified Effect System Abstraction (EffectSystem[F[_]])

- Status: Accepted
- Date: 2025-09-04

## Context
We require a unified abstraction over effect systems to support IO boundaries, resource safety, and concurrency while keeping Spark transformations pure. The codebase defines `EffectSystem[F[_]]` with concrete instances for Cats-Effect IO and ZIO Task.

## Decision
- Keep `EffectSystem[F[_]]` as the single abstraction for effects across modules.
- Provide and maintain first-class instances for IO and ZIO in `modules/core/instances/EffectInstances.scala`.
- Encourage leaf modules to depend only on `EffectSystem[F]` constraints, not on specific IO/ZIO imports.

## Consequences
- Pros: Effect polymorphism; easy switching between IO/ZIO; consistent resource-safety and concurrency model.
- Cons: Two ecosystems in deps; risk of mixing in leaf modules; requires discipline and linting/documentation.
- Compatibility: No API break; aligns with current code and docs.

## Verification
- `sbt compile` in core and leaf modules uses `EffectSystem[F]` in signatures.
- Unit tests compile and run with IO; ZIO instances compile.

## References
- Evidence: `docs/plans/templates/EVIDENCE.md` (§2, §3)
- Plan: `docs/plans/templates/PLAN.md` (§4.4)
- House Rules: `AGENTS.md` (§Effect System Implementation Rules)

## End Goal (Big Picture)
- Unified effect abstraction across the platform enabling pure Spark transforms and effectful IO/orchestration, with the flexibility to select Cats-Effect or ZIO per module policy.
- Consistent resource safety, concurrency, retries, and timing primitives across engines/connectors.

## Milestones
- M1: IO/ZIO instances audited with law/property tests (present).
- M2: Leaf modules depend on `EffectSystem[F]` only (no direct IO/ZIO)—see Evidence and Plan.
- M3: Observability and resource-safety wired at IO boundaries (see ADR-007, ADR-013).

## Open Questions
- Do we enforce “one effect per module” via build-time checks or rely on review discipline?
- Do we offer interop helpers (zio-interop-cats) or keep boundary-specific bridges only?
```
