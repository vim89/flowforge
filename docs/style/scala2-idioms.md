# Scala 2.13 Idioms in FlowForge

This document summarizes the core coding conventions used across FlowForge (Scala 2.13 line). These are guardrails, not dogma, and are aligned with ADRs and CONTRIBUTING.md.

- Effects at edges only: pure transforms modelled as functions; external I/O wrapped in `EffectSystem[F]`.
- For-comprehensions over long `F` chains for readability.
- No `println` in core modules - use `CoreLogger[F]` (or infrastructure `StructuredLogger[F]`). CLIs/examples may use minimal `IO.println`.
- No `var`, `return`, `throw`, `null` in main sources. Exceptions only at integration edges.
- Eliminate ad-hoc casts. If required for reflective integration, isolate in a private utility and document why. CI gates forbid `asInstanceOf`/`Any` outside whitelisted files.
- Prefer `ValidatedNel` for multi-rule validation; `Either` for fail-fast.
- Spark: keep Dataset/DataFrame transforms pure; put session, I/O, and timing in `F[_]`. Centralize write options and logging.
- Resource safety: use `cats.effect.Resource` (or equivalent) for acquisition/release.

See also:
- docs/plan/refactor-idiomatic-scala2.md
- docs/adr/002-spark-purity-and-io-boundaries.md
