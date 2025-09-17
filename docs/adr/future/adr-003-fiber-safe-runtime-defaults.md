# ADR-003: Fiber-Safe Runtime Defaults (Blocking, Bounded Concurrency, Cancellation Safety)

- **Status**: Accepted
- **Date**: 2025-09-10
- **Owner**: Runtime & Connectors
- **Related**: ADR-002, ADR-007

## Context
Most production incidents stem from hidden blocking, unbounded concurrency, or sloppy finalization. We need **minimal** guardrails that materially increase stability.

## Decision
Adopt **three** universal defaults:
1) **Route blocking to blocking pool**: wrap all JDBC/cloud SDK/file IO in `IO.blocking` (or equivalent) to avoid starving the compute pool. :contentReference[oaicite:22]{index=22}
2) **Bound concurrency by default**: inter-stage channels use **bounded** `cats.effect.std.Queue` (or `parTraverseN`) to enforce backpressure. :contentReference[oaicite:23]{index=23}
3) **Cancellation-safe resources**: use `Resource`/`bracket` so acquire/release are non-interruptible and always run on cancel. :contentReference[oaicite:24]{index=24}

## Low-Level Design
- `Blocking[F]` helper: `def blocking[A](thunk: => A): F[A] = IO.blocking(thunk).to[F]`. :contentReference[oaicite:25]{index=25}
- `StageChannel[F, A]`: `Queue.bounded(capacity)` with metrics (size, dropped, wait time). :contentReference[oaicite:26]{index=26}
- `AsyncPools`: document separate pools only if needed; default to CE runtime.
- `ManagedConnector`:
  ```scala
  trait ManagedConnector[F[_], C] { def resource: Resource[F, C] }
  ```

All sinks/readers expose Resource to guarantee cleanup on cancel/fail.
Typelevel

## Consequences
- Predictable backpressure, fewer GC storms, fewer deadlocks.

## Risks & Mitigations
- Slight overhead from queues/resources → acceptable; can tune capacities.

## References
- Cats Effect `IO` & blocking guidance.
- Typelevel
- Cats Effect `Resource` semantics (non-interruptible acquire/release).
- Typelevel
- Bounded `Queue` behavior & API. 
