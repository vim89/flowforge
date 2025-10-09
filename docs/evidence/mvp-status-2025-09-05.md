# MVP Status - 2025-09-05 (GCS‑only)

## Achieved
- CI‑first schema gates:
  - Manual workflow (`contracts-submit.yml`) with expectedJsonUrl/sourcePath and dryRun control.
  - PR auto‑validation scaffold detecting changed `contracts/avro/**/*.avsc` and resolving per‑contract metadata (`contracts/metadata/<domain>/<entity>.yaml`) or falling back to `FF_MODE/FF_INPUT` repo vars.
  - Local parity task: `sbt ffValidate` delegates to validation‑cli.
- GCS connector: implemented with mocked tests; S3 explicitly out of scope.
- Deequ adapter MVP: NotNull + Unique mapped; engines‑spark auto‑invokes Deequ when present via reflection; unit test added.
- Spark engine hardening: Delta reflection removed; Spark‑backed transforms (map/flatMap/join/groupBy/union); helper ops (filterByColumn/sortByColumn/limitRows/dropRows).
- Configuration MVP: Infra decoder now delegates to core decoder (no stub), returns validated FlowForgeConfig.
- g8 template: runnable Spark+Delta demo with quiet logging.

## Pending (v1)
- Contracts publisher/SDK pipeline (replace stub materialization with real contracts source).
- Typed/contract‑first g8 template variant that generates project with CI wired.
- Deequ mappings for Range/Pattern and YAML‑to‑constraints.
- Uniform observability around IO/CDC; tracing integration; coverage targets; Flink engine.

## Notes
- Physical schema “compile‑time” gates are CI‑time by design (ADR‑011). Typed compile‑time gates (SchemaEq/SchemaConforms) work in code for contract types.

