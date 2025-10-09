# PLAN - Partitions & Table Ops (Affected Partitions First)

## 1) Scope (Minimal Viable Change)
- Implement `getAffectedPartitions(table, startTime, endTime)` and consume results in table ops to reduce work and shuffles.
- Support Delta and filesystem-backed Parquet tables; provide optional Cloud SDK and regex/date interpreters; fall back gracefully when scheme unknown.

## 2) Deliverables
- `SparkDataAlgebra.getAffectedPartitions` with pluggable interpreter pipeline (discovery order configurable):
  1) DeltaInterpreter (default for provider=delta): `_delta_log` JSON/checkpoint parsing; extract `add.partitionValues`, `modificationTime` ∈ [start, end].
  2) FsInterpreter (default for parquet/hive): Hadoop `FileSystem` listing; parse `col=value` paths; filter by `FileStatus.getModificationTime`.
  3) CloudSdkInterpreter (optional): Use connector (e.g., GCS/S3) to list objects by prefix with pagination and rate-limited parallelism; filter by `lastModified`.
  4) RegexInterpreter (optional): Enumerate candidate partitions based on `dateColumn` + `regex`/window; verify existence lightly.
  5) Fallback: Spark `inputFiles` listing + FS stat to determine mtimes.
- Table ops (`repairRefreshTable`, `vacuumTable`, `analyzeTable`) accept/derive partitions and operate on those only.
- Config knobs (per table/engine): discovery order, `dateColumn`, `dateRegex`, max depth, parallelism, rate limits.

## 3) Tasks
1. Detect provider, location, and partition columns via Spark catalog; expose overrides.
2. Implement DeltaInterpreter: read `_delta_log` JSON/checkpoint; filter adds by modification time; dedupe `partitionValues` to PartitionSpec.
3. Implement FsInterpreter: list under base path (bounded depth); stat mtimes; parse `col=value` segments into PartitionSpec (catalog order).
4. Implement CloudSdkInterpreter (behind feature flag): reuse connectors (e.g., GCS) to list/paginate; use `EffectSystem.parTraverse` with bounded parallelism to collect keys and lastModified.
5. Implement RegexInterpreter: enumerate dates in window; construct candidate prefixes; verify existence quickly; map to PartitionSpec for configured `dateColumn`.
6. Add fallback using `df.inputFiles` + FS stat.
7. Update table ops to prefer provided `partitions` else derive via `getAffectedPartitions`.
8. Add observability: structured logs (interpreter, window, counts, duration) and Prometheus metrics (runs, latency, count).

## 4) Tests
- Unit:
  - Path parsing to PartitionSpec (multi-column, nested).
  - Deduplication and inclusive window logic.
  - Interpreter selection based on provider/config.
- IT (guarded):
  - Delta: create two commits across different partitions; assert only touched partitions returned.
  - Parquet: synthetic `col=value` tree on local FS; mutate mtimes; assert.
  - CloudSdk (optional): mock connector to emulate pages and lastModified; assert interpreter pipeline behavior.

## 5) Risks & Mitigations
- Catalog variance: When partition columns unknown, default to full-table operation (logged) or require explicit config.
- Time accuracy: Document reliance on commit/mtime; prefer Delta logs; expose `dateColumn` override.
- Object store throttling: Bound parallelism; page results; backoff on 429/5xx; allow interpreter order to skip SDK.
- False positives from optimize/compaction: Accept as safe superset; document.

## 6) Validation & Acceptance
- `getAffectedPartitions` returns non-empty list for updated windows.
- Table ops act on those partitions only when present.
- CI green; no API breakage.


## Appendix:

How we can Adapt It In FlowForge

- Discovery order and interpreters:
    - DeltaInterpreter: read _delta_log JSON/checkpoints to extract add.partitionValues where modificationTime ∈ [start, end]. Cheapest/most precise.
    - Catalog+FSInterpreter: use spark.catalog.getTable for location and partition columns; walk Hadoop FS (no SDK) to collect files and mtimes; parse col=value segments
      to PartitionSpec.
    - CloudSdkInterpreter (optional): when connectors present and explicitly enabled, use GcsFileSystemConnector (and S3 later) to list objects by prefix with
      pagination, concurrency via EffectSystem.parTraverse, and filter by lastModified; parse keys → PartitionSpec. Guard with rate-limits and batch sizes.
    - RegexInterpreter (optional convenience): for strict date-named partitions (dt=YYYY-MM-DD), enumerate only date range candidates and check existence (fast
      O(windowDays)).
    - Audit/IndexInterpreter (future): if pipelines maintain a per-partition write index/audit, query it to avoid listing.
    - ViewInterpreter (optional): given a view, expand to underlying tables and union affected partitions across them.
    - ViewInterpreter (optional): given a view, expand to underlying tables and union affected partitions across them.
-
API fit:
- Keep TableOperations.getAffectedPartitions(table, start, end): F[List[PartitionSpec]].
- Add opt-in flags via config (e.g., ff.tableOps.discovery = [delta, fs, cloudsdk, regex], rate limits, maxDepth).
- Add a convenience getAffectedPartitionDates facade that returns List[(LocalDate, Instant)] when a single date partition is in play.
-
Performance and safety:
- Use metadata-only paths; never scan data.
- Bound parallelism with EffectSystem (e.g., parTraverseN) and backoff on SDK rate errors.
- Prefer directory-level listing; avoid per-file HEADs.
- Always log the chosen interpreter (delta|fs|cloudsdk|regex) + counts + duration.
-
Partition parsing:
- Normalize by base-path stripping and splitting on / to match col=value in catalog order; ignore non-partition path segments.
- For regex/date extractor, gate it with explicit dateColumn + regex config.
-
Testing:
- Unit: path parsing; dedupe; window inclusion semantics; interpreter selection by provider.
- IT (guarded):
- Delta: two commits across different partitions in a window.
- Parquet: synthetic `col=value` layout on local FS with mtimes toggled.
- GCS (optional): mock connector (like existing GCS tests) to emulate listing pages and lastModified.

Concrete Additions To Our Plan

- Expand docs/plan/partitions-and-table-ops.md to include:
    - CloudSdkInterpreter (behind feature flag) using existing connectors.
    - RegexInterpreter option for date-based single-column partitions.
    - Optional ViewInterpreter that expands views to base tables then unions partitions.
    - Config knobs: discovery order, concurrency limits, max depth, date regex/column.
- Implement helpers in Spark engine:
    - partitionColumnsOf(TableName), tableLocationOf(TableName), parsePartitionSpecFromPath(base, path, cols).
    - Interpreter selection function by provider + config.
- Observability:
    - Log interpreter, window, partitionsFound, and duration; emit histogram/counter metrics.

If you’d like, I can update the plan doc now to reflect these extra interpreters and their guard rails, or move straight to a skeleton implementation of
getAffectedPartitions with the interpreter switch and stubs.
