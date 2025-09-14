# ADR-002: “Non-Rewrite Pact” with Pioneers (Spark, Flink, Kafka, Debezium, Delta)

- **Status**: Accepted
- **Date**: 2025-09-10
- **Owner**: FlowForge Core
- **Related**: ADR-003, ADR-007, ADR-006

## Context
We must **not** re-implement core features that pioneers already provide (exactly-once, checkpoints, transactions, MERGE, etc.). Our role is guardrails and integration.

## Decision
Adopt a strict **integration-only** policy:
- Use **Spark Structured Streaming** semantics directly (checkpoint + WAL + replayable sources + idempotent sinks ⇒ e2e exactly-once). :contentReference[oaicite:7]{index=7}
- Use **Flink** state+checkpointing and its EOS mechanisms; with Kafka transactions, Flink achieves end-to-end exactly-once via two-phase commit. :contentReference[oaicite:8]{index=8}
- Use **Kafka** idempotent producers + transactions + `read_committed` to realize EOS in read-process-write pipelines. :contentReference[oaicite:9]{index=9}
- Use **Debezium** as the CDC source of truth for **offsets** and **schema history** (Kafka-backed), enabling reliable restarts. :contentReference[oaicite:10]{index=10}
- Use **Delta Lake MERGE** for idempotent upserts and CDC ingestion; do not build bespoke dedupe. :contentReference[oaicite:11]{index=11}

## Low-Level Design
- **Engine Adapters**:
    - SparkAdapter: validate checkpoint configured when user claims EOS; verify sink reports idempotency (or refuse “EOS=true”). :contentReference[oaicite:12]{index=12}
    - FlinkAdapter: ensure checkpoints enabled (interval/timeout) and expose metrics for alignment/backpressure. :contentReference[oaicite:13]{index=13}
    - KafkaAdapter: pass-through producer/consumer settings; if `transactional.id` set, hint `isolation.level=read_committed`. :contentReference[oaicite:14]{index=14}
    - DebeziumSource: do **not** duplicate offsets; health check that offset/history topics exist. :contentReference[oaicite:15]{index=15}
    - DeltaSink: emit native `MERGE INTO` with key predicates; surface schema-evolution flags. :contentReference[oaicite:16]{index=16}

## Consequences
- We stay thin; documentation points to official guarantees the engines provide.
- Fewer moving parts in FlowForge; less to maintain.

## Risks & Mitigations
- Misconfiguration still possible → **fail-fast validation** with links to vendor docs in error messages.

## References
- Spark Structured Streaming guide (EOS). :contentReference[oaicite:17]{index=17}
- Flink checkpointing, EOS with Kafka. :contentReference[oaicite:18]{index=18}
- Kafka transactions/EOS overview. :contentReference[oaicite:19]{index=19}
- Debezium offset & schema history storage. :contentReference[oaicite:20]{index=20}
- Delta Lake MERGE docs. :contentReference[oaicite:21]{index=21}

All sinks/readers expose Resource to guarantee cleanup on cancel/fail.
Typelevel

Consequences

Predictable backpressure, fewer GC storms, fewer deadlocks.

Risks & Mitigations

Slight overhead from queues/resources → acceptable; can tune capacities.

References

Cats Effect IO & blocking guidance.
Typelevel

Cats Effect Resource semantics (non-interruptible acquire/release).
Typelevel

Bounded Queue behavior & API.
