# ADR-021: FlowForge Audit logging - Effect-agnostic, portable, tamper‑evident

Status: Accepted

Date: 2025-09-17

Authors: FlowForge Core Team

Supersedes/Relates: ADR‑020 (30‑point checklist), Runbook §Audit Trail

## Context

FlowForge needs a first‑class, provider‑agnostic audit system that:
- Records critical lifecycle and data operations for pipelines without rewriting pipelines.
- Is safe under effects (uses our Effect System and FlowforgeResource for resource safety and retries).
- Lets users “bring their own” durable store (files/object, JDBC DB, Kafka, or custom) while we define a stable event model and SPI.
- Enables decision‑making: resumability, offsets/watermarks, partition checkpoints, SLA gating, and incident analysis.
- Is tamper‑evident via hash chaining and periodic digests.

## Decision

Adopt a core audit event model and a minimal SPI with pluggable sinks. Provide reference sinks for File/Object (JSONL + digest) and JDBC, with an optional Kafka adapter. Wire audit hooks at PipelineExecution and DataAlgebra IO boundaries. Default mode is non‑blocking with local spill on failures; a strict blocking mode is available for regulated flows.

## Objectives

- Portable: no cloud lock‑in; no heavyweight provider SDKs in core.
- Durable & tamper‑evident: append‑only event stream, per‑chain hash continuity, periodic digests.
- Reportable: L1/L2 can query the audit store directly for dashboards and incident analysis.
- Orchestratable: single source of truth for offsets, checkpoints, watermarks, runs, and resumability.
- Non‑invasive: audit at orchestration and IO edges; no pipeline DSL rewrites required.

## Core model (stable)

### AuditEvent (immutable, append‑only)
- `id: UUID` - event ID (unique). Used for idempotency/dedup.
- `chainId: UUID` - logical sequence/session (e.g., pipeline run or job session). Used for hash chain continuity.
- `occurredAt: Instant` - UTC timestamp when the event occurred (not when persisted).
- `actor: String` - service/user/role identity (e.g., `ff-pipeline-runner`, `ops-user@corp`).
- `engine: String` - `spark`, `flink`, `none`, etc.
- `source: String` - module/component emitting (`PipelineExecution`, `DataAlgebra`, `DQ`, `CDC`).
- `correlationId: String` - pipeline/job/run correlation (e.g., runId, jobId).
- `subject: Json` - structured description of the thing acted on (dataset, sink, contract, topic/partition, table/partition, window, etc.).
- `action: String` - event type. Canonical set (non‑exhaustive, extensible):
  - Pipeline: `pipeline_started`, `pipeline_completed`, `pipeline_failed`, `batch_open`, `batch_close`.
  - IO: `read_started`, `read_completed`, `write_started`, `write_completed`.
  - DQ/Contracts: `quality_checked`, `contract_validated`.
  - CDC: `offset_committed`, `checkpoint_saved`.
- `outcome: String` - `success` | `failure` | `skipped`.
- `severity: String` - `Info` | `Warning` | `Error`.
- `details: Json` - event‑specific payload:
  - Common keys (conventions): `records_read`, `records_written`, `duration_ms`, `dq_score`, `violations` (array with `{constraint, count, severity}`), `topic`, `partition`, `offset`, `window_start`, `window_end`, `idempotency_key`, `watermark_at`, `metrics` (freeform map), `error` (on failures).
- `prevHash: HexString(64)` - hash of previous event in the same `chainId` (or genesis constant for the first event).
- `hash:  HexString(64)` - hash of this event’s canonical encoding (including `prevHash`).

Notes:
- Hash algorithm: SHA‑256 over canonical JSON encoding (UTF‑8). Canonicalization: sorted object keys; no insignificant whitespace; fixed number/string representations.
- Redaction: `details` and `subject` are filtered by a configured policy before hashing/persisting (mask/drop specific keys like `email`, `ssn`, `token`).

### Auxiliary, normalized facts (optional but recommended for JDBC/reporting)
- Run: `run_id, pipeline, engine, started_at, completed_at, status, metrics_json`.
- Stage: `stage_id, run_id, kind(read/write/transform/quality), input, output, stats_json`.
- Violations: `run_id/stage_id, constraint, count, severity`.
- Offsets: `run_id, source_id, topic, partition, offset, offset_at, watermark_at`.
- Checkpoints: `run_id, entity(table/dataset), window_start, window_end, partition_spec_json, checkpoint_at`.
- Digests: `interval_start, interval_end, chain_id, head_hash, file_hashes_json, signature, created_at`.

## SPI & Algebra (BYO sink)

### `AuditSink[F]`
- `writeEncoded(batch: List[EncodedAuditEvent]): F[Unit]`
- `rollInterval(key: IntervalKey): F[Unit]` (optional; for file/object rotations)
- `readRange(q: VerifyQuery): Stream[F, EncodedAuditEvent]` (optional; used by verifiers)

### `AuditAlgebra[F]`
- `append(e: AuditEvent): F[Unit]`
- `appendBatch(es: List[AuditEvent]): F[Unit]`
- `checkpoint(): F[Unit]`
- `verify(q: VerifyQuery): F[AuditVerification]`

### Interpreters
- File/Object sink (default): JSONL per time partition + hourly digest JSON; path layout compatible with `file://`, `s3a://`, `gs://`, `abfs://`.
- JDBC sink (optional): append‑only `audit_event` + helper tables and views; uniqueness on `id` for idempotency.
- Kafka sink (optional): events topic + digest topic; typically paired with a compacting store downstream.
- BYO: implement `AuditSink[F]` for Postgres/Oracle/ClickHouse/BigQuery/Iceberg/Delta, etc.

## Storage schema (Portable SQL)

### `audit_event`
```sql
CREATE TABLE audit_event (
  id UUID PRIMARY KEY,
  chain_id UUID NOT NULL,
  occurred_at TIMESTAMPTZ NOT NULL,
  pipeline TEXT NOT NULL,
  engine TEXT NOT NULL,
  correlation_id TEXT,
  actor TEXT,
  action TEXT NOT NULL,
  outcome TEXT NOT NULL,
  severity TEXT NOT NULL,
  subject_json JSONB NOT NULL,
  details_json JSONB NOT NULL,
  prev_hash CHAR(64) NOT NULL,
  hash CHAR(64) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX ON audit_event (pipeline, occurred_at);
CREATE INDEX ON audit_event (chain_id, occurred_at);
CREATE UNIQUE INDEX ON audit_event (hash);
```

### `audit_offset`
```sql
CREATE TABLE audit_offset (
  pipeline TEXT NOT NULL,
  source_id TEXT NOT NULL,
  topic TEXT NOT NULL,
  partition INT NOT NULL,
  last_offset BIGINT NOT NULL,
  offset_at TIMESTAMPTZ NOT NULL,
  run_id UUID,
  PRIMARY KEY (pipeline, source_id, topic, partition)
);
```

### `audit_checkpoint`
```sql
CREATE TABLE audit_checkpoint (
  pipeline TEXT NOT NULL,
  entity TEXT NOT NULL,       -- table or dataset
  window_start TIMESTAMPTZ,
  window_end TIMESTAMPTZ,
  partition_spec JSONB,
  checkpoint_at TIMESTAMPTZ NOT NULL,
  run_id UUID,
  PRIMARY KEY (pipeline, entity, window_start, window_end)
);
```

### `audit_run`
```sql
CREATE TABLE audit_run (
  run_id UUID PRIMARY KEY,
  pipeline TEXT NOT NULL,
  engine TEXT NOT NULL,
  started_at TIMESTAMPTZ NOT NULL,
  completed_at TIMESTAMPTZ,
  status TEXT NOT NULL,        -- running/succeeded/failed/canceled
  metrics_json JSONB
);
CREATE INDEX ON audit_run (pipeline, started_at);
```

### `audit_violation`
```sql
CREATE TABLE audit_violation (
  run_id UUID NOT NULL,
  stage_id TEXT,
  constraint TEXT NOT NULL,
  count BIGINT NOT NULL,
  severity TEXT NOT NULL,
  PRIMARY KEY (run_id, constraint)
);
```

### `audit_digest`
```sql
CREATE TABLE audit_digest (
  interval_start TIMESTAMPTZ NOT NULL,
  interval_end TIMESTAMPTZ NOT NULL,
  chain_id UUID NOT NULL,
  head_hash CHAR(64) NOT NULL,
  file_hashes JSONB,
  previous_digest_hash CHAR(64),
  signature TEXT,
  created_at TIMESTAMPTZ NOT NULL,
  PRIMARY KEY (interval_start, chain_id)
);
```

## Tamper‑evidence

- Each event encodes to canonical JSON; `hash = sha256(prevHash || payload)`; `prevHash` points to the prior event within the same `chainId`.
- Periodic digests (e.g., hourly) capture chain head hash plus file/object hashes; optional HMAC signature.
- Verification job walks chains to test continuity and compare digest expectations.

## Redaction & PII

- Config: `ff.audit.redact = "email,token,ssn"` (comma‑separated keys). Modes: `mask`, `drop`, `hash` (salted).
- Redaction is applied before hashing and persistence.
- Subject/details field conventions specify placements for potentially sensitive values; defaults are safe.

## Retention

- File/Object: time‑partitioned layout with periodic compaction; delete/expire by path policy.
- JDBC: monthly partitions or TTL jobs; indexes preserved for the hot window.
- Digests preserved beyond event TTL if regulatory WORM is required.

## Decision‑making & restartability (Drives the platform)

- Offsets: last committed per topic/partition → start/restart from known positions; with Kafka transactions, pair with read_committed consumers.
- Partition checkpoints: record completed table/window/partition chunks → resumable historical loads; resume from the first incomplete chunk only.
- SCD watermarks: per dimension key set; incremental merges only ingest rows after stored watermarks.
- Run windows: `pipeline_started/completed` carry `[window_start, window_end]`; orchestrator computes next window as `[last_success_end, now)`.
- Idempotency: record write idempotency keys for transactional tables (Delta/Iceberg) to avoid duplicate merges on retries.
- SLA gating: derive alerts from audit (missing digests, rising violation counts, repeated stage failures, long durations). Gate deploys or next windows.
- Safety levers: non‑blocking by default (spill locally and continue); blocking mode forces failure on audit write errors for regulated pipelines.

## Failure behavior

- Non‑blocking default: on sink failure, spill to local JSONL (degraded chain), emit metric; background re‑ingest tool can repair.
- Blocking (opt‑in): fail the pipeline if audit cannot append.
- Retries/backoff: `AuditAlgebra` wraps sink writes with `retryWithBackoff` from the Effect System; bounded retries + circuit breaker are configurable.

## Cloud/platform agnostic choices

- File/Object sink: any FS connector URL; digest JSON is neutral.
- JDBC sink: plain SQL; users provide driver, DDL, and retention policy.
- Kafka optional: for streaming sites that centralize events; still keep a compacted/stateful store for queries.
- No cloud SDKs in core; adapters live in connectors/infrastructure.

## How we will implement (phased)

Sprint A
- `audit-core`: types (`AuditEvent`, `EncodedAuditEvent`, `IntervalKey`, `VerifyQuery`, `AuditVerification`), canonical JSON codec, hash chain, redaction.
- `audit-file`: JSONL writer with time‑based rotation; hourly digest writer; simple verifier.
- Hooks: add at `PipelineExecution` and `DataAlgebra` IO ops (`read/*`, `write/*`, `validate`, `CDC` ops, DQ results). Controlled by `ff.audit.enabled` and sink config.
- Example: local filesystem demo + verifier.

Sprint B
- `audit-jdbc`: JDBC sink + recommended schema + convenience views; idempotent on `id`.
- `audit-cli`: verify chains/digests; quick queries (next window, last offsets, run summaries).
- Optional `audit-kafka`: events topic + digest topic.
- Docs for L1/L2 reporting patterns (materialized views) and orchestrator usage.

## Examples of orchestrator queries

- Next incremental window:
  ```sql
  SELECT max(window_end) FROM audit_checkpoint
  WHERE pipeline = 'sales' AND entity = 'sales_table';
  ```
- Incomplete historical chunks:
  ```sql
  SELECT window_start, window_end, status
  FROM audit_run WHERE pipeline = 'sales_hist' ORDER BY started_at;
  -- Resume from the first window with status <> 'succeeded'
  ```
- Kafka last offsets for a consumer group:
  ```sql
  SELECT topic, partition, last_offset
  FROM audit_offset WHERE pipeline = 'events_etl';
  ```
- Last successful DQ score:
  ```sql
  SELECT run_id, details_json->>'dq_score'
  FROM audit_event
  WHERE pipeline = 'sales' AND action = 'quality_checked'
  ORDER BY occurred_at DESC LIMIT 1;
  ```

## Why “Bring your own audit DB/table” ?

- Lets L1/L2 plug into existing BI/observability stacks with no data movement.
- Teams own governance, roles, and retention.
- FlowForge keeps the event model stable and the sink SPI small; users pick the right backend.

## Configuration (initial set)

- `ff.audit.enabled = true|false` (default: false)
- `ff.audit.sink = file|jdbc|kafka|custom` (default: file)
- `ff.audit.file.path = ./target/audit/` (default)
- `ff.audit.digest.interval = 1h` (default)
- `ff.audit.redact = email,token,ssn` (default: empty)
- `ff.audit.mode = nonblocking|blocking` (default: nonblocking)

## Consequences

- Slight performance overhead on IO edges (encoding + buffered writes). Measurable but bounded.
- Clear, queryable history enabling restartability and audits. Simplifies incident analysis and compliance.
- Users can evolve storage independently; our surface remains stable.

## Alternatives considered

- Centralized proprietary audit store: rejected for lock‑in and operational burden.
- Inline logs only: rejected; unstructured, hard to query, no tamper evidence, weak for orchestration.

## Unresolved / future work

- Pluggable signature providers for digests (HSM/KMS).
- Multi‑tenant isolation guidelines (per‑tenant schema vs RLS) - documented in runbook when tenancy lands.
- Schema evolution for `details` conventions: publish JSON Schemas per `action` for stricter validation (optional).


## Notes (archived)

Below is a future‑ready, cloud/platform‑agnostic audit design tailored to FlowForge that:

- Stays effect‑agnostic (EffectSystem + FlowforgeResource).
- Is BYO sink, including “bring your own audit DB/table.”
- Supports L1/L2 reporting directly from the audit store.
- Drives decision‑making and restartability (Kafka offsets, partition checkpoints, SCD1/2 watermarks, resumable batches).
- Aligns with ADR‑020 (30‑point checklist) and the rest of our ADRs.

Objectives

- Portable: no cloud/provider lock‑in; no heavy libraries by default.
- Durable and tamper‑evident: append‑only, hash‑chain + periodic digests.
- Reportable: L1/L2 can query the audit store for dashboards & incident analysis.
- Orchestratable: a single source of truth for pipeline state, watermarks, offsets, and resumability.
- Non-invasive: wire audit at PipelineExecution and DataAlgebra edges without pipeline rewrites.

Core model (stable, effect‑agnostic)

- AuditEvent (immutable, append‑only)
    - id (UUID), chainId (UUID) for a logical sequence (run, job, session)
    - occurredAt (Instant, UTC)
    - actor (service/user/role), engine (spark|flink|…), source (module)
    - correlationId (pipeline/job/run), subject (dataset/sink/contract)
    - action (pipeline_started/completed/failed, read_started/completed, write_started/completed, quality_checked, offset_committed, checkpoint_saved, batch_open/close)
    - outcome (success/failure), severity (Info/Warning/Error)
    - details (Map[String, AnyJson]) - event‑specific payload (records, partitions, offsets, constraints, metrics)
    - prevHash, hash (tamper‑evidence)
- Auxiliary, normalized facts (all optional; used by JDBC/reporting):
    - Run: run_id, pipeline, engine, started_at, completed_at, status, metrics JSON
    - Stage: stage_id, run_id, kind (read/write/transform/quality), input/output dataset, stats
    - Violations: run_id/stage_id, constraint, count, severity
    - Offsets: run_id, source_id, topic, partition, offset, watermark_at
    - Checkpoints: run_id, entity (table/partition/window), checkpoint_at, window_start/window_end, hash pointer
    - Digests: interval_start/end, chain_id, headHash, fileHashes/signature (file/object sink), verification metadata

SPI & algebra (BYO sink)

- AuditSink[F] - low‑level sink interface (bring your own DB/table)
    - writeEncoded(batch: List[EncodedAuditEvent]): F[Unit]
    - rollInterval(key: IntervalKey): F[Unit]  // optional
    - readRange(q: VerifyQuery): Stream[F, EncodedAuditEvent] // optional (for verifiers)
- AuditAlgebra[F] - main entry points (our API)
    - append(e: AuditEvent): F[Unit], appendBatch(es): F[Unit]
    - checkpoint(): F[Unit]
    - verify(q: VerifyQuery): F[AuditVerification]
- Interpreters (select at wiring time)
    - File/Object sink (default): JSONL per time partition + hourly digest JSON; supports any filesystem connector (file://, s3a://, gs://, abfs://)
    - JDBC sink (optional): append‑only table(s) with uniqueness on id; optional views to denormalize details for reporting
    - Kafka sink (optional): events + hourly digest topic; helpful in streaming environments
    - BYO: implement AuditSink[F] against your store (Postgres, Oracle, ClickHouse, BigQuery, Iceberg, Delta, etc.)

Bring‑Your‑Own Audit DB/Table

- Supported and encouraged via AuditSink[F].
- Two recommended schemas:
    - JSON‑blob: audit_event(id PK, occurred_at, chain_id, correlation_id, action, outcome, engine, severity, subject_json, details_json, prev_hash, hash, created_at)
    - Normalized: audit_event core columns, plus event_details (EAV or JSON), plus tables for runs/stages/violations/offsets/checkpoints for fast joins
- Idempotency: unique(id); at‑least‑once writers de‑dupe by id
- Tamper‑evidence: check prevHash→hash continuity via verification job
- Views/Materialized views for L1/L2: e.g., mv_pipeline_daily_run_summary, mv_top_violations_7d

Decision‑making and restartability

- Kafka offsets (batch/stream)
    - For each source: topic, partition, last_committed_offset, offsetAt, run_id, session_id
    - Use cases:
        - “Start from last processed offset” at job startup (exact‑once flows add transactional semantics on write)
        - “Skip partitions that are complete” on restarts
- Partition checkpoints for batch
    - Table + columns: pipeline, run_id, entity (table), window_start, window_end, partition_spec (json), checkpoint_at, count, checksum
    - Use cases:
        - Multi‑chunk historical loads (e.g., 6‑month batches over 4 years): each chunk produces batch_open/batch_close events + saved checkpoint
        - If 3rd chunk fails, orchestrator queries audit: list completed chunks → resume from the first incomplete chunk only
- SCD1/SCD2 watermarks
    - Store dimension watermarks per natural key set:
        - scd1_watermark(table, keyset_hash, last_processed_at)
        - scd2_watermark(table, keyset_hash, last_valid_from)
    - Use cases:
        - Incremental merges that honor slowly changing dimensions (only ingest rows after last watermark)
- Windowing & time travel
    - run_window(start/end) recorded on pipeline_started/completed
    - Orchestrator can plan next window = [last_success_end, now) using audit_run
- Idempotency & exactly‑once write semantics (where available)
    - For Delta/transactional targets: record idempotency keys per batch, avoid duplicate UPSERTs
    - audit_event holds idempotency_key for the stage (optional)
- SLA/health gating
    - Derive alerts from audit: missing digests, large violation counts, repeated failures on a stage
    - Use details_json to store standardized metrics (records_read, records_written, duration_ms, dq_score)

How it fits ADR‑020 (30‑point checklist)

      7. Audit Logging: effectful, pluggable, structured events (we deliver)
     10. Restartability & 11) Partitioning: partition checkpoint & offset storage (we deliver)
     18. Idempotence & 19) Exactly‑Once: hooks + idempotency keys captured in audit (pipeline writers communicate keys)
     21. Monitoring: audit doubles as a structured telemetry feed; Prometheus metrics can be derived
     22. Security: redaction policy in event encoding (mask keys like email, tokens); BYO DB roles for read-only access
- 24/25/26) Concurrency/Retry/Timeout: all writes go through EffectSystem; sink can implement retry/backoff; pipeline remains non‑blocking or blocking per policy

Cloud-/platform‑agnostic choices

- File/Object sink: works with anything our filesystem connector supports; digest JSON is provider‑agnostic
- JDBC sink: plain SQL; users bring their DB driver, own DDL and retention
- Kafka (optional): only if the user depends on it; our examples demonstrate JSONL “facade” for local dev
- No provider‑specific SDKs in core; adapters live in connectors/infrastructure

Suggested schema (portable SQL)
Audit event (core)

- CREATE TABLE audit_event (
  id UUID PRIMARY KEY,
  chain_id UUID NOT NULL,
  occurred_at TIMESTAMPTZ NOT NULL,
  pipeline TEXT NOT NULL,
  engine TEXT NOT NULL,
  correlation_id TEXT,
  actor TEXT,
  action TEXT NOT NULL,
  outcome TEXT NOT NULL,
  severity TEXT NOT NULL,
  subject_json JSONB NOT NULL,
  details_json JSONB NOT NULL,
  prev_hash CHAR(64) NOT NULL,
  hash CHAR(64) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
  );
- CREATE INDEX ON audit_event (pipeline, occurred_at);
- CREATE INDEX ON audit_event (chain_id, occurred_at);
- CREATE UNIQUE INDEX ON audit_event (hash);

Offsets

- CREATE TABLE audit_offset (
  pipeline TEXT NOT NULL,
  source_id TEXT NOT NULL,
  topic TEXT NOT NULL,
  partition INT NOT NULL,
  last_offset BIGINT NOT NULL,
  offset_at TIMESTAMPTZ NOT NULL,
  run_id UUID,
  PRIMARY KEY (pipeline, source_id, topic, partition)
  );

Checkpoints (partitions/batches)

- CREATE TABLE audit_checkpoint (
  pipeline TEXT NOT NULL,
  entity TEXT NOT NULL,       -- table or dataset
  window_start TIMESTAMPTZ,
  window_end TIMESTAMPTZ,
  partition_spec JSONB,
  checkpoint_at TIMESTAMPTZ NOT NULL,
  run_id UUID,
  PRIMARY KEY (pipeline, entity, window_start, window_end)
  );

Runs

- CREATE TABLE audit_run (
  run_id UUID PRIMARY KEY,
  pipeline TEXT NOT NULL,
  engine TEXT NOT NULL,
  started_at TIMESTAMPTZ NOT NULL,
  completed_at TIMESTAMPTZ,
  status TEXT NOT NULL,        -- running/succeeded/failed/canceled
  metrics_json JSONB
  );
- CREATE INDEX ON audit_run (pipeline, started_at);

Violations

- CREATE TABLE audit_violation (
  run_id UUID NOT NULL,
  stage_id TEXT,
  constraint TEXT NOT NULL,
  count BIGINT NOT NULL,
  severity TEXT NOT NULL,
  PRIMARY KEY (run_id, constraint)
  );

Digest (optional if using file/object sink)

- CREATE TABLE audit_digest (
  interval_start TIMESTAMPTZ NOT NULL,
  interval_end TIMESTAMPTZ NOT NULL,
  chain_id UUID NOT NULL,
  head_hash CHAR(64) NOT NULL,
  file_hashes JSONB,           -- list of path->hash
  previous_digest_hash CHAR(64),
  signature TEXT,              -- optional HMAC
  created_at TIMESTAMPTZ NOT NULL,
  PRIMARY KEY (interval_start, chain_id)
  );

Retention & PII

- Retention: time partitioning and pruning (monthly tables) or TTL jobs (DB‑specific)
- PII: mask keys in details_json via DetailsMask(policy) before encoding:
    - Hash (salted) or drop entirely; centralized list in config (ff.audit.redact=“email,token,ssn”)

Failure behavior

- Non‑blocking default: on sink failure, spill to local JSONL; append to a “degraded events” chain; re‑ingest later.
- Blocking mode (opt‑in): fail pipeline if audit cannot append (regulated flows).
- Backoff/Retry: AuditAlgebra wraps writes with EffectSystem retryWithBackoff.

How we’ll implement (incrementally)

- Sprint A: audit-core + audit-file; hooks in PipelineExecution/DataAlgebra; example + verifier
- Sprint B: audit-jdbc + audit-cli; optional audit-kafka; docs for report building (L1/L2) and orchestrator queries

Examples of orchestrator queries (decision‑making)

- Next incremental window for pipeline “sales”:
    - SELECT max(window_end) FROM audit_checkpoint WHERE pipeline='sales' AND entity='sales_table';
- Incomplete historical chunks:
    - SELECT window_start, window_end, status FROM audit_run WHERE pipeline='sales_hist' ORDER BY started_at;
    - Resume from the first window with status != 'succeeded'.
- Kafka last offsets for consumer group:
    - SELECT topic, partition, last_offset FROM audit_offset WHERE pipeline='events_etl';
- Last successful quality score:
    - SELECT run_id, details_json->>'dq_score' FROM audit_event WHERE pipeline='sales' AND action='quality_checked' ORDER BY occurred_at DESC LIMIT 1;

Why “bring your own audit DB/table” is the right call

- It lets L1/L2 teams plug into existing BI/observability stacks and build dashboards without moving data.
- Teams can choose governance/retention appropriate to their org.
- FlowForge only defines the event schema and sink SPI; you decide where/how to persist and scale.

Next steps -

- Scaffold modules: audit-core and audit-file (types, hash chain, JSONL writer, digest writer, simple verifier).
- Wire hooks under ff.audit.enabled in PipelineExecution and DataAlgebra edges.
- Provide an example + demo verifier CLI.
- Add docs: “Audit Reporting 101” showing SQL views/materialized views for L1/L2 dashboards-pipeline success rates, DQ failures, throughput, top offsets/checkpoints,
  restart summaries.
