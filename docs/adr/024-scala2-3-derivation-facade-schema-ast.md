# ADR: Derivation Facade and Deep SchemaAST for Compile-Time Contracts

Status: Accepted
Date: 2025-09-17

Context

- FlowForge enforces data contracts at Scala compile time via a `SchemaConforms[Out, Contract, P]` evidence.
- Scala 2.13 implementation originally compared shallow field lists and wired the macro directly from `SchemaConforms.materialize`.
- We are targeting Scala 3 with Mirrors + inline + macros; we also want deep, recursive structural validation.

Decision

1) Introduce a derivation facade to abstract the backend:
   - `DerivationBackend` trait provides `schemaConforms`.
   - `Derivation` facade selects the backend.
   - For Scala 2, we keep `SchemaConforms.materialize` as the macro entrypoint (to preserve implicit materialization semantics) and retain the facade for Scala 3 migration.

2) Implement deep structural validation with `SchemaAST`:
   - Added `SchemaAST` ADT (Record, Field, Primitive, OptionT, ArrayT, MapT).
   - Macro builds `SchemaAST` for both Out and Contract via reflection and compares recursively under the selected `SchemaPolicy`.
   - Error messages include path-aware diffs for Missing/Extra/Mismatched fields and elements.

Consequences

- Stronger guarantees: nested records, collections, and options are validated at compile time.
- Future-proofing: Scala 3 backend can be swapped in by changing the facade (Scala 3 sources) without touching call sites.
- Tests updated to assert compile-time failures using `assertTypeError` with explicit evidence summoning where needed.

Before → After

- Before: shallow (name, typeString, default, optional) comparison; direct macro in `SchemaConforms.materialize`.
- After: deep `SchemaAST` comparison; facade introduced; Scala 2 continues to use macro in `SchemaConforms.materialize` for implicit resolution, with facade reserved for Scala 3.

References

- Code: `modules/core/src/main/scala/com/flowforge/core/contracts/internal/SchemaAST.scala`
- Macro: `modules/core/src/main/scala/com/flowforge/core/contracts/internal/SchemaConformsMacros.scala`
- Facade: `modules/core/src/main/scala/com/flowforge/core/contracts/derive/Derivation.scala`
- Diagrams: `docs/diagrams/compile-time-contracts/*`

