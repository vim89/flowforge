# Audit logging — Implementation plan (Sprint A & B)

Audience: Developers (including juniors) implementing the audit system.

Prereqs
- Build works: `sbt compileAll`.
- Familiar with core modules: `modules/core`, engines, examples.
- Comfortable with basic JSON, JDBC, and file IO in Scala.

## Milestones overview

1) Sprint A (Core delivery)
- Module `audit-core` (types, hashing, redaction, SPI) [D1–D3]
- Module `audit-file` (JSONL writer + digest + verifier) [D3–D5]
- Hooks at orchestration/IO edges [D5–D7]
- Example + docs [D7]

2) Sprint B (Integrations & tooling)
- Module `audit-jdbc` (schema + sink) [D1–D3]
- Module `audit-cli` (verify, quick queries) [D3–D5]
- Optional `audit-kafka` [D5–D6]
- Docs: reporting patterns & orchestrator recipes [D7]

Time boxes are indicative (workdays per sprint week).

## Module: audit-core

Create `modules/audit-core` with the following packages:
- `com.flowforge.audit.core`

Files and responsibilities
- `model/AuditEvent.scala`
  - Case class exactly per ADR. Include Circe encoders/decoders with canonical JSON encoding helpers.
  - `Subject` and `Details` are `io.circe.Json`.
- `model/EncodedAuditEvent.scala`
  - Holds canonical bytes/string, `hash`, `prevHash`, and parsed `AuditEvent`.
- `hash/Hashing.scala`
  - `sha256Hex(bytes: Array[Byte]): String`.
  - `canonicalize(e: AuditEvent): Array[Byte]` — sorted keys, fixed number formats, UTC ISO‑8601.
- `redaction/Redactor.scala`
  - Load policy keys (comma‑separated). Modes: `mask`, `drop`, `hash` (salted) — start with `mask`.
  - Apply to `subject` and `details` recursively by key name.
- `algebra/AuditSink.scala`
  - Trait per ADR; `writeEncoded`, `rollInterval`, `readRange`.
- `algebra/AuditAlgebra.scala`
  - Trait + default interpreter that: redacts → canonicalizes → links `prevHash` (per chain cache) → hashes → delegates to sink (batched).
  - `retryWithBackoff` for sink writes; parameters via config.
- `verify/Verifier.scala`
  - Chain walker on `EncodedAuditEvent` stream to validate continuity; returns `AuditVerification(status, issues)`.
- `config/Config.scala`
  - Case class for `enabled`, `sink`, `file.path`, `digest.interval`, `redact`, `mode`.

Testing guidance
- Unit tests for hashing determinism, redaction behavior, and chain continuity.
- Golden JSON canonicalization tests (fixtures).

## Module: audit-file

Create `modules/audit-file` with package `com.flowforge.audit.file`.

Writer
- Buffered async writer writing JSONL files under layout:
  - `${base}/pipeline=${pipeline}/chain=${chainId}/dt=YYYY-MM-DD/hour=HH/*.jsonl`
- Rotation policy by hour; `rollInterval` closes current writer and opens the next.
- Digest writer runs at interval end, emits `digest-YYYYMMDDHH.json` with:
  - `interval_start`, `interval_end`, `chain_id`, `head_hash`, `file_hashes` (path→sha256), `previous_digest_hash`, optional `signature`.

Reader (for verification)
- Stream JSONL files in an interval; decode `EncodedAuditEvent` in order and feed to `Verifier`.

Failure & backpressure
- If write fails and mode is `nonblocking`, spill to `${base}/_degraded/pipeline=.../chain=.../*.jsonl` and log a metric. If `blocking`, bubble error.

## Wiring hooks

Targets
- `modules/core/src/main/scala/com/flowforge/core/FlowForgePipeline.scala`
- `modules/core/src/main/scala/com/flowforge/core/algebra/DataAlgebra.scala` (calls sites in engines/sinks)

Emit events at:
- Pipeline lifecycle: `pipeline_started` (with `run_id`, `window_start`), `pipeline_completed`/`pipeline_failed` (with `window_end`, status, metrics).
- IO ops: `read_started`/`read_completed`, `write_started`/`write_completed` with sizes, formats, partitioning.
- DQ/Contracts: `quality_checked`, `contract_validated` with violation summary and `dq_score`.
- CDC ops: `offset_committed` (topic/partition/offset/watermark), `checkpoint_saved` (entity, partition_spec, window).
- Batching: `batch_open`/`batch_close` for historical chunking.

Details conventions (first cut)
- `subject` examples
  - Read: `{ "dataset": {"path": "...", "format": "parquet"} }`
  - Write: `{ "sink": {"table": "db.tbl", "mode": "append"} }`
  - CDC: `{ "kafka": {"topic": "t", "partition": 1} }`
  - Checkpoint: `{ "entity": "db.tbl", "partition_spec": {...} }`
- `details` examples
  - IO: `{ "records_read": 123, "duration_ms": 456 }`
  - DQ: `{ "dq_score": 0.98, "violations": [{"constraint": "notNull(id)", "count": 7, "severity": "Warning"}] }`
  - CDC: `{ "offset": 12345, "watermark_at": "2025-09-17T00:00:00Z" }`
  - Batch: `{ "window_start": "...", "window_end": "..." }`

Config keys
- `ff.audit.enabled` (bool)
- `ff.audit.sink` (`file`|`jdbc`|`kafka`|`custom`)
- `ff.audit.file.path` (path/URI)
- `ff.audit.digest.interval` (e.g., `1h`)
- `ff.audit.redact` (CSV keys)
- `ff.audit.mode` (`nonblocking`|`blocking`)

## Module: audit-jdbc (Sprint B)

Schema
- Implement the SQL exactly as in ADR. Provide DDL helpers for Postgres/H2.

Sink
- Upsert = insert only; rely on `id` uniqueness to de‑dupe (at‑least‑once writers).
- Batched inserts with prepared statements.

Views (docs only to start)
- `mv_pipeline_daily_run_summary`
- `mv_top_violations_7d`

## Module: audit-cli (Sprint B)

Commands
- `verify --base <path> --chain <uuid> --from <ts> --to <ts>`
- `next-window --pipeline <name> --entity <table>`
- `last-offsets --pipeline <name>`
- `run-summary --pipeline <name> --days 7`

## Optional: audit-kafka (Sprint B)

- Producer for events topic; periodic digest topic. Document consumer isolation/read_committed pairing.

## Orchestrator recipes (Decision‑Making)

- Resume historical loads:
  1) Query `audit_checkpoint` for completed `[window_start, window_end]` for `pipeline=X`.
  2) Resume from the first missing or failed window; emit `batch_open` and later `batch_close`.
- Streaming restart:
  1) Read `audit_offset` last offsets per topic/partition.
  2) Seek consumer to `last_offset+1`; start processing.
- Incremental dimension merge:
  1) Read SCD watermark for `table+keyset`.
  2) Filter source rows to `> watermark`; update watermark after success.
- SLA gating:
  - If last 3 runs have `quality_checked.dq_score < threshold` or `pipeline_failed` repeated, block next window and alert.

## Dev notes & checklists

Coding
- Keep audit code side‑effect boundaries clear; prefer small, pure helpers.
- Never log PII in plaintext; rely on Redactor.
- Prefer `F[_]` only at IO boundaries; pure transformations remain pure.

Testing
- Add unit tests per module. For file sink, write/rotate/verify happy path + failure spill.

Docs
- Update Runbook with “Audit Trail” link; add examples SQL snippets.

## Acceptance criteria (Sprint A)
- `audit-core` and `audit-file` compile and are covered by unit tests for hashing/redaction/rotation.
- Hooks emit events for pipeline lifecycle and IO ops in the examples module; JSONL and digest files appear under `./target/audit`.
- Verifier passes for a sample chain.

## Acceptance criteria (Sprint B)
- `audit-jdbc` persists events; simple queries return expected results.
- `audit-cli verify` detects tampering (hash mismatch) and prints continuity report.
- Documentation includes orchestrator recipes and reporting patterns.
