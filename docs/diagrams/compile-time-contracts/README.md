# Compile‑Time Data Contracts - Diagrams

This folder contains flowcharts and UML diagrams explaining how FlowForge’s compile‑time data contracts work. It covers the common flow used across both implementations and details for:

- Scala 2.13 using Magnolia-based derivation
- Scala 3 using Mirrors + inline + macros (quotes reflection)

Quick links:

- Common flowchart: `docs/diagrams/compile-time-contracts/flowchart.md`
- UML (Scala 2 • Magnolia): `docs/diagrams/compile-time-contracts/scala2-magnolia-uml.md`
- UML (Scala 3 • Mirrors/macros): `docs/diagrams/compile-time-contracts/scala3-mirror-uml.md`
- Field vs Element Optionality: `docs/diagrams/compile-time-contracts/optionality.md`

Related reading:

- `docs/why-compile-time.md`
- `docs/how-it-fails.md` (error anatomy, sample compile‑time diffs)
- `docs/plan/compile-time-4.md` (derivation facade and policies)

Implementation note (Scala 2.13):

- FlowForge now normalizes both Out and Contract types into a deep `TypeShape` ADT (replacing legacy `SchemaAST`) and compares them under the selected `SchemaPolicy`. This validates nested records, options (field vs element), arrays, and maps recursively with path-aware diffs.
