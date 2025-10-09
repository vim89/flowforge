```markdown
# ADR 007 - Observability and Infrastructure Layer

- Status: Accepted
- Date: 2025-09-04

## Context
Infrastructure abstractions (logging, metrics, tracing, config, resource safety) exist with partial implementations. Spark algebra emits Prometheus metrics; broader OTEL/Micrometer are not wired.

## Decision
- Keep SLF4J logging; use Prometheus counters as current baseline.
- Introduce OTEL/Micrometer gradually; wire via `EffectSystem[F]` at IO boundaries.

## Consequences
- Pros: Pragmatic baseline; clear upgrade path.
- Cons: Partial coverage until wiring completed.

## Verification
- Metrics increment at key IO operations; infra code compiles; future wiring PRs reference this ADR.

## References
- Evidence: `docs/evidence/infrastructure-layer.md` (§6)
- Plan: `docs/plan/infrastructure-layer.md`

## End Goal (Big Picture)
- Platform-wide structured logging, metrics, and tracing with minimal overhead and effect-safe hooks.

## Milestones
- M1: Logging + Prometheus counters wired at IO boundaries.
- M2: Optional OTEL/Micrometer bridges.

## Open Questions
- How deep to go on tracing for batch runs vs streaming.
```
