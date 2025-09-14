# ADR-009: Developer Experience – Thin APIs, Fast Feedback, Clear Boundaries

- **Status**: Accepted
- **Date**: 2025-09-10
- **Owner**: DX & Docs
- **Related**: ADR-001..ADR-008

## Context
DX targets:
1) Low learning time, 2) Minimal code to add (clear modular units), 3) Fast feedback.

## Decision
- Keep **surface area minimal**: sources, transforms (pure/impure), sinks, DQ, lineage, audit.
- Provide **ready recipes**:
    - “CDC → Delta MERGE with DQ and lineage”
    - “Batch partitions with ProgressStore”
    - “Spark Streaming EOS checklist”
- **Fast feedback**:
    - Build times: prefer **semi-auto** derivation in huge ADTs; cache shapes.
    - Run: quick local runners and synthetic sources.

## Low-Level Design
- `g8` templates for common blueprints.
- Pre-wired metrics/logging; clear compile errors for contract violations.
- Tutorials that show **Caprese** pure slots and **Kyo** fences as **optional** advanced paths.

## Consequences
- New devs ship quickly; senior devs can scale within the framework without forking.

## Risks
- Feature creep → enforce ADR-002 principles; any PR adding “engine-like” features must be rejected.

## References
- (DX principles complemented by the engine docs cited across ADR-002/003 for guarantees.)
