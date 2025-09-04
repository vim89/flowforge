# Data Contracts — Overview (Current Model)

This page clarifies FlowForge’s contract model so there’s no confusion between typed compile‑time gates, CI checks, and runtime code.

## What “Won’t Compile if Schema Doesn’t Match” Means
- In code (types ↔ contract types):
  - Pipelines use typed endpoints: `TypedSource[R]` / `TypedSink[R]`.
  - `PipelineBuilder2` requires evidence that your case class type matches the contract schema representation `R`.
  - If they don’t match, implicit resolution (e.g., `SchemaEq[A, R]`) fails and compilation stops with a clear message.

- In CI (physical storage ↔ contracts):
  - Non‑technical authors submit contracts via GitHub Actions Forms.
  - CI materializes typed artifacts (and/or publishes typed SDK JARs) and runs `validation-cli` to compare physical schemas (Delta/Hive/Parquet/JDBC) with the contract.
  - Mismatch → PR fails with actionable diffs. This is the authoritative “compile‑time in CI” gate for physical sources/targets.

## What Lives Where
- Type‑level enforcement (compile time in code):
  - `modules/core/types/SchemaEvidence.scala` — `SchemaEq`, `SchemaConforms`, policies.
  - `modules/core/types/PipelineBuilder2.scala` — typed builder enforcing endpoints/gates.
  - Generated/SDK contracts provide the typed endpoints (`TypedSource/TypedSink`) and witnesses.

- CI validation (physical schema checks):
  - `modules/validation-cli` — canonicalizes Spark/Delta/Hive/Parquet schemas and diffs vs contract JSON/Avro.
  - GitHub Actions Forms + workflow: contract submission → materialize typed artifacts → run validation.
- No sbt AutoPlugin is maintained. Local checks invoke the same CLI directly (e.g., via `sbt ffValidate`).

### Optional `.avsc` Generation
- Contracts CI can generate Avro `.avsc` alongside Scala case classes (toggle via Forms flag `generateAvsc` or repo variable `FF_GENERATE_AVSC`).
- When disabled, Scala typed gates still work; CI can validate physical schemas using Spark JSON canonicalization instead of Avro.

- Business/DQ contracts (logical rules):
  - `modules/contracts` — DataContract, validation rules (ValidatedNel), builder patterns for dataset‑level checks.
  - These complement type/physical gates with domain rules.

## Developer Reality
- If your pipeline output type doesn’t match the typed sink’s contract schema, `addTypedSink` won’t compile.
- If a physical source/target drifts, CI fails your PR (not your local compiler) unless you run the optional local task (which delegates to the CLI).

## References
- ADR‑010 (Contracts Authoring & Operating Model): `docs/adr/010-contracts-authoring-operating-model.md`
- ADR‑011 (CI‑first compile/build gates): `docs/adr/011-contracts-compile-build-gates.md`
- ADR‑019 (Typed Contract Pipelines Example): `docs/adr/019-typed-contract-pipelines-example.md`
- Archive design docs (superseded) include historical rationale and API sketches.
