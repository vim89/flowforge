# ADR-001: Compile-Time Contracts via Magnolia Derivation

- **Status**: Accepted
- **Date**: 2025-09-10
- **Owner**: FlowForge Core
- **Related**: ADR-002, ADR-004, ADR-007

## Context
FlowForge enforces **compile-time** schema/contract conformance to eliminate many runtime surprises. We want a derivation mechanism that is:
- ergonomic (minimal boilerplate),
- fast enough for developer feedback loops,
- compatible with Scala 3 evolution (future-facing).

## Decision
Adopt **typeclass-based compile-time contracts** with **automatic derivation** using **Magnolia** for product/sum types; keep our `Shape[T]` (or equivalent) as the contract “view” for types. Magnolia is a generic macro approach widely used in the Scala ecosystem for typeclass derivation. :contentReference[oaicite:0]{index=0}

## Low-Level Design
- `core/contracts/derive/Shape.scala`
    - Provide `given derived[T]: Shape[T]` using Magnolia to synthesize field-level descriptors (names, types, optionality).
    - Product handling (`join`) maps case-class fields; sum handling (`split`) maps sealed traits/enums. :contentReference[oaicite:1]{index=1}
- `core/contracts/SchemaConforms.scala`
    - Implement compile-time evidence `SchemaConforms[Contract, Reader, Policy]` using type-level ops on `Shape`.
    - Policies: `Exact` (same fields), `Backward` (contract ≥ reader), `Forward` (contract ⊆ reader + defaults), `Full` (union with type-compatibility).
- `Field-level annotations`
    - Optional metadata (min/max, email, enum) captured in `Shape` to power runtime DQ hints (see ADR-006).

## Implementation Notes
- Default to **auto-derivation**; allow **semi-auto** or **manual** instances to break ties or improve compile times.
- Ensure derivation lives in `Shape`’s companion so “given” search finds it without extra imports. (Scala 3 typeclass derivation is a first-class feature. ) :contentReference[oaicite:2]{index=2}
- Warn/document edge-cases where Magnolia may struggle (very deep recursion, heavy inlining). :contentReference[oaicite:3]{index=3}

## Alternatives Considered
- **Scala 3 `derives`** on our typeclass: viable but we need finer control across Scala 2.13 bridges (if any) and complex ADTs.
- **ZIO Schema derivation**: attractive, but would couple us to a larger stack. :contentReference[oaicite:4]{index=4}

## Consequences
- Compile-time schema checks become **default**; example usage remains terse.
- Teams can override/hand-write `Shape` for odd cases (opaque types).

## Risks & Mitigations
- **Compile-time blowups** on pathological ADTs → document semi-auto pattern; cache derived instances.
- **Migration to Scala 3 only features** → maintain compatibility shims where necessary.

## Test Plan
- Golden tests on `Shape` rendering for diverse ADTs.
- Negative compilation tests for policy violations.
- Benchmarks to monitor derivation time.

## References
- Magnolia docs/readme. :contentReference[oaicite:5]{index=5}
- Scala 3 typeclass derivation reference. :contentReference[oaicite:6]{index=6}
