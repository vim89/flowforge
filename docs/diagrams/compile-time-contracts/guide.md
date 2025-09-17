# Compile-Time Contracts Diagrams — Guide

How to read these diagrams (junior-friendly)

- Start with the flowchart (flowchart.svg). Follow arrows left→right, top→bottom.
- When you see “Evidence”, read it as: the compiler must prove something. If it can’t, your code won’t compile.
- “SchemaConforms” reads: Out conforms to Contract under Policy P.
- Policies encode your evolution rules (Exact, Backward, Forward, order/position variants, Full).
- Derivation facade = selector for implementation (Scala 2 Magnolia or Scala 3 Mirrors). Public APIs don’t change.

Glossary

- Shape: compile-time description of a record’s fields (names, types, optional/default). We compute it to build SchemaAST.
- SchemaAST: normalized, deep tree (Record/Field/Primitive/Option/Array/Map) used for recursive comparison.
- Evidence: a typeclass instance the compiler must find; here, SchemaConforms.
- SchemaConforms[Out, Contract, P]: “Out conforms to Contract under Policy P”. If not, macro aborts with a diff.
- Mirror (Scala 3): compile-time descriptor for case class shape. Lets us walk fields in macros.
- Reflection (Scala 2 macro): compile-time type inspection; no runtime cost.

Design patterns and principles

- Strategy/Bridge: Derivation facade chooses backend (Magnolia vs Mirrors) transparently.
- Typeclass pattern: SchemaConforms is a typeclass — no runtime registry; the compiler builds it for you.
- ADTs: SchemaPolicy and SchemaAST are sealed trait hierarchies — explicit and safe.
- Typestate/Phantom Builder: PipelineBuilder enforces stage ordering at compile time (build only when complete).
- Functional error model: compile-time failures; optional runtime data quality uses ValidatedNel.
- SOLID highlights:
  - Single Responsibility: macro validates structure only; engines do IO/compute.
  - Open/Closed: new policies/backends can be added without modifying callers.
  - Dependency Inversion: callers depend on abstractions (SchemaConforms), not concrete derivation.

Pointers

- Policy examples: docs/contracts/policy-cookbook.md
- Failing tests (copy/paste snippets): modules/compile-fail-tests/src/test/scala/com/flowforge/compilefail
- Rendering tips (Mermaid): docs/diagrams/compile-time-contracts/RENDERING.md

