# What Makes FlowForge a Framework (Not Just Libraries)

This page captures the non‑negotiable behaviors that define FlowForge as a cohesive framework. These are design‑time constraints and runtime invariants that make pieces fit exactly into each other.

## Core Behaviors
- Typed Contracts as Build Gates
  - Pipelines referencing typed endpoints must materialize `SchemaConforms[Out, Contract, Policy]` at compile time.
  - Failure to prove evidence aborts compilation with a readable drift diff (policy lattice applies).
- Phantom‑State Pipeline Builder
  - `build()` is only callable from `BuilderState.Complete` (HasSource ∧ HasTransform* ∧ HasSink [+ ContractEvidence]).
  - Incomplete pipelines are literally unbuildable.
- Effect Boundary + Purity
  - Transforms are pure functions (`A => B`). All I/O happens at edges via `EffectSystem[F]`; side‑effects are bracketed and idempotent.
  - No I/O escapes from pure sections; connectors/engines implement effectful algebras.
- Engine Abstraction
  - A single `DataAlgebra[F]` surface defines read/transform/write/quality. Engines (Spark, Flink) are plug‑ins behind that algebra.
- Quality as Optional Enhancement
  - Native Spark constraints by default; Deequ reflection auto‑enables when on classpath; failures degrade gracefully to native.

## Developer Experience
- One‑Command Feedback Loops
  - `sbt ffDev` runs compile + focused tests; goal: <3s for pure code, <60s for integration smoke.
  - g8 template demonstrates red‑>green contract fix in under 3 minutes.
- Compile‑Fail Tests are First‑Class
  - Repositories ship with `compile-fail-tests` that assert policy violations at type‑check time.

## Operating & Observability
- Idempotent Edge Actions
  - Sinks and out‑of‑band effects (audit/notify) must be idempotent; retries/speculation not allowed to duplicate.
- Structured Errors & Metrics
  - Errors are typed (category/severity/context); metrics shape is standard (per‑stage, per‑pipeline).
- Tracing Hooks
  - Span lifecycle around actions with stage/pipeline tags; no‑op implementation is default, OTEL is pluggable.

## Documentation Contract
- Why‑First
  - Every audience doc starts with “What hurts, why this helps” before any API surface.
- Runtime vs Compile‑Time Boundary
  - Each concept explains what is proven at compile time vs what is guarded at runtime.

