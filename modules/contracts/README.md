# FlowForge Contracts Module (Logical/DQ Contracts)

Purpose
- Provide domain‑level validation abstractions (DataContract, ValidationRule, RuleSeverity) and builders for dataset‑level checks using ValidatedNel.

How this relates to typed/compile‑time contracts
- Typed compile‑time gates (`TypedSource/TypedSink`, `SchemaEq/SchemaConforms`) live in `modules/core/types` and are enforced at pipeline construction time.
- Physical schema validation (Delta/Hive/Parquet/JDBC) runs in CI via `modules/validation-cli`; sbt plugin wrappers are optional and should delegate to the CLI.

Use this module for
- Expressing business rules (e.g., not‑null, ranges, patterns), aggregating errors, and surfacing DQ results.
- Complementing the type‑level and CI gates with domain semantics.

References
- Contracts Operating Model: `docs/adr/010-contracts-authoring-operating-model.md`
- CI‑first compile/build gates: `docs/adr/011-contracts-compile-build-gates.md`
- Typed pipeline example: `docs/adr/019-typed-contract-pipelines-example.md`

