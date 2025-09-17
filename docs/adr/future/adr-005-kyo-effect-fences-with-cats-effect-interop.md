# ADR-005: Experimental – Kyo Effect Fences with Cats-Effect Interop

- **Status**: Proposed (Opt-In)
- **Date**: 2025-09-10
- **Owner**: Experimental
- **Related**: ADR-003, ADR-002

## Context
Teams sometimes want **effect-set precision** without changing FlowForge’s `F[_]` surface. **Kyo** tracks multiple effects via **intersection types** and ships **kyo-cats** to interoperate with Cats-Effect (cancellation propagation, etc.). :contentReference[oaicite:39]{index=39}

## Decision
Introduce module `experimental-kyo`:
- Provide a thin bridge `KyoInterop.toIO[A](A < FX): IO[A]` via **kyo-cats**. :contentReference[oaicite:40]{index=40}
- Two overloads in `PipelineBuilder` (IO variant):
    - `kyoPureStage(f: A => (B < Sync))` for **pre-contract** areas (no async/abort).
    - `kyoStage(f: A => (B < (Sync & Async & Abort[String])))` for general stages.

## Low-Level Design
- Keep FlowForge’s public API unchanged; Kyo usage is **local** to stage bodies.
- Ensure cancellation propagation between IO and Kyo per kyo-cats guarantees. :contentReference[oaicite:41]{index=41}

## Consequences
- Advanced users get effect-set clarity; beginners can ignore it.
- Zero reinvention of engines; we only constrain *where* effects are allowed by type.

## Risks
- Library maturity: keep experimental and optional; fall back to plain `IO` always possible.

## References
- Kyo README (Cats integration & cancellation propagation). :contentReference[oaicite:42]{index=42}
