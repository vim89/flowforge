# Scala 3 PoC Alignment - Compile-Time Contracts (2025-10-02)

Source of truth for the PoC: `/Users/vim/IdeaProjects/compile-time-data-contracts`.

What we checked and matched:
- Policy lattice parity: Exact, ExactUnorderedCI, ExactOrdered, ExactOrderedCI, ExactByPosition, Backward, Forward, Full.
- Structural model: Primitive/Sequence/Map/Struct with Field(hasDefault, isOptional) and path-aware diffs.
- Policy toggles: ordered/by-position, case-insensitive handling, and Backward/Forward/Full post-filters.
- Error text shape: “Compile-time contract drift … Missing/Extra/Mismatch” with path rendering.

Implementation mapping:
- Scala 3 PoC uses inline + quotes (TypeRepr / report.errorAndAbort).
- FlowForge Scala 2.13 macro uses `scala.reflect.macros.blackbox` with equivalent logic in `modules/core/.../ContractMacros.scala` and Magnolia for `Shape`.

Conclusion:
- Behavior is equivalent for products/case classes and the documented collection/map primitives. Future work: a Scala 3 port of macros to reduce duplication and enable a single source for 2.13/3 via cross.

