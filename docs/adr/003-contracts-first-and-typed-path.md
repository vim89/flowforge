```markdown
# ADR 003 — Contracts-First and Typed Compile-Time Path

- Status: Accepted
- Date: 2025-09-04

## Context
The project mandates contracts-first pipelines with compile-time gates when possible and runtime validation otherwise. The typed path (TypedSource/TypedSink/TypedSchema/PipelineBuilder2) enforces compile-time guarantees; other surfaces use `ValidatedNel` at runtime.

## Decision
- Promote the typed path as the recommended API for compile-time safety.
- Keep runtime validation via `ValidatedNel` where compile-time is not feasible (CLIs, dynamic schemas).

## Consequences
- Pros: Strong guarantees for typed pipelines; pragmatic coverage elsewhere.
- Cons: Duplicate surfaces (typed/untyped); requires clear docs and examples.

## Verification
- Provide an example and unit test demonstrating compile failure on schema drift in the typed path.

## References
- Evidence: `docs/plans/templates/EVIDENCE.md` (§3)
- Plan: `docs/plans/templates/PLAN.md` (§4.2)
- Design: `docs/examples/TYPED_CONTRACT_PIPELINES.md`, contracts/*, core/types/*

## End Goal (Big Picture)
- Contracts-first flow where typed compile-time gates are the default; runtime validations complement where dynamic.

## Milestones
- M1: Canonical typed example + test (see Plan/Evidence typed-example).
- M2: Templates default to typed path; legacy untyped flagged.
- M3: Build-time physical checks integrated (ADR-011).

## Open Questions
- Scope of typed path for streaming and dynamic schemas.
```
