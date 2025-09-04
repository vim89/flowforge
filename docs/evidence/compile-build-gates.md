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
| CI-first (GitHub Actions Forms + CLI) | Non-engineer friendly; auditable; decoupled from local sbt | CI complexity; artifact lifecycle | Accepted
| sbt AutoPlugin only | Local developer loop | Diverges from non-engineer workflow; less auditable | Optional
| Defer checks | Simpler now | Claims unmet | Rejected

**Decision sketch**: CI-first approach is authoritative; sbt plugin optional and should delegate to CLI to avoid drift.

## 2.1) Detailed Findings
- Core typed path supports compile-time schema alignment (SchemaEq, TypedSource/Sink, PipelineBuilder2), but there is no canonical example exercising it.
- No sbt AutoPlugin exists to preflight physical schemas (Delta/Hive/Parquet/JDBC) vs contracts pre-compile.
- CI’s `schema-validate.yml` demonstrates CLI-based validation for a mock Parquet only; not integrated as a build gate.

## 5) Edge Cases & Invariants
- Offline mode; fast schema read (no data scans).

## 6) Success Criteria
- CI workflow exists and fails PRs on mismatches (clear diffs).
- Optional sbt tasks can call the same CLI for local smoke tests.

## 7) Recommendations (Production-grade)
- Keep canonical schema normalization in one place (validation-cli / shared lib) used by both CI and any sbt plugin wrappers.
- Canonical schema normalization:
  - Uniform model across Spark StructType, Delta describe detail, Parquet footer, JDBC metadata.
  - Compare field names/types/nullability; support nested structs via path notation.
- Performance:
  - Use metadata-only reads (schemaOnly) or footers; avoid scanning data.
  - Make tasks opt-in locally via a flag; mandatory in CI.
- Developer DX:
  - Clear diffs with remediation hints; support exact/superset policies.

## 8) Next Steps (Concrete)
- Add `.github/workflows/contracts-submit.yml` (Forms + schema diff run via validation-cli).
- Materialize typed artifacts (avsc + metadata) into `contracts/**` in PR or as artifacts.
- Optionally, keep `project/ContractValidationPlugin.scala` delegating to validation-cli for local usage.
