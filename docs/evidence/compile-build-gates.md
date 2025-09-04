# EVIDENCE — Compile-Time & Build-Time Gates

## 1) Problem & Constraints
- **Goal**: Confirm typed compile-time gates and build-time physical validators exist.
- **Non-goals**: Full registry integration now.
- **Hard constraints**: No build.sbt structure changes.

## 2) Codebase Recon
- **Modules involved**: core/types (TypedSource/TypedSink/SchemaEq), templates/g8; no sbt AutoPlugin.
- **Key files**:
  - `modules/core/src/main/scala/com/flowforge/core/types/*`
  - `templates/data-pipeline.g8/**` (no codegen tasks)
- **APIs impacted**: Derivation witnesses; sbt tasks.

## 3) Prior Art & Sources
- ADR-011; archived CONTRACTS_COMPILE_AND_BUILD_GATES.md.

## 4) Options & Trade-offs
| Option | Pros | Cons | Why |
|---|---|---|---|
| Add sbt AutoPlugin (pre-compile checks) | Enforces gates | Build plumbing | Accepted (follow-up)
| Defer build-time checks | Simpler now | Claims remain unmet | Rejected

**Decision sketch**: Implement minimal sbt tasks for Parquet/Delta schema diff; wire into example.

## 2.1) Detailed Findings
- Core typed path supports compile-time schema alignment (SchemaEq, TypedSource/Sink, PipelineBuilder2), but there is no canonical example exercising it.
- No sbt AutoPlugin exists to preflight physical schemas (Delta/Hive/Parquet/JDBC) vs contracts pre-compile.
- CI’s `schema-validate.yml` demonstrates CLI-based validation for a mock Parquet only; not integrated as a build gate.

## 5) Edge Cases & Invariants
- Offline mode; fast schema read (no data scans).

## 6) Success Criteria
- sbt tasks exist and can fail builds on mismatches.

## 7) Recommendations (Production-grade)
- Implement `ContractValidationPlugin` with tasks:
  - `ffVerifySourcePhysical`: fetch and normalize source schema; diff against contract.
  - `ffVerifyTargetPhysical`: normalize target schema; diff against contract.
- Canonical schema normalization:
  - Uniform model across Spark StructType, Delta describe detail, Parquet footer, JDBC metadata.
  - Compare field names/types/nullability; support nested structs via path notation.
- Performance:
  - Use metadata-only reads (schemaOnly) or footers; avoid scanning data.
  - Make tasks opt-in locally via a flag; mandatory in CI.
- Developer DX:
  - Clear diffs with remediation hints; support exact/superset policies.

## 8) Next Steps (Concrete)
- Add `project/ContractValidationPlugin.scala` with both tasks.
- Provide small fixtures (local parquet with known schema) and a smoke test under examples/tests.
- Wire tasks into an example project’s `compile := (compile).dependsOn(...)` and document how to enable in CI.
