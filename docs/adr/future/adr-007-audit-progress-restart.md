# ADR-007: Audit & Progress for Restartability (Batch & CDC)

- **Status**: Accepted
- **Date**: 2025-09-10
- **Owner**: Runtime & Ops
- **Related**: ADR-002, ADR-003

## Context
We need resilient restarts with minimal rework. Streaming engines provide state recovery; batch needs explicit tracking; CDC has specific offset/history rules.

## Decision
- **Streaming**: rely on engine checkpoints/WAL. **Spark**: replayable sources + idempotent sinks; **Flink**: checkpointed state/positions. :contentReference[oaicite:51]{index=51}
- **CDC**: Treat **Debezium** offset and schema history topics as authoritative for resume. :contentReference[oaicite:52]{index=52}
- **Batch**: create:
    - `pipeline_run(run_id, job, start, end, status, rows, sla_status)`
    - `partition_progress(dataset, partition_key, last_successful_at, run_id)`

## Low-Level Design
- `RunContext` issues events and writes audit rows at start/complete/fail.
- `ProgressStore`:
    - `markProcessed(dataset, partition)` after successful sink commit,
    - `nextPartitions(...)` to plan the next batch set.
- Delta write path uses **MERGE** for idempotent upsert, enabling safe replays. :contentReference[oaicite:53]{index=53}

## Consequences
- Deterministic restarts; no double processing.
- Clear operational view via audit tables.

## Risks
- Audit table availability → store in a highly available metastore (or transactional lake table).

## References
- Spark EOS & checkpointing/WAL. :contentReference[oaicite:54]{index=54}
- Flink checkpointing semantics. :contentReference[oaicite:55]{index=55}
- Debezium state storage (offsets, schema history). :contentReference[oaicite:56]{index=56}
- Delta MERGE usage. :contentReference[oaicite:57]{index=57}
