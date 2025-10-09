```markdown
# ADR 012 - Effect System Decision (IO/ZIO with Pure Spark)

- Status: Accepted
- Date: 2025-09-04

## Context
EffectSystem[F[_]] provides unified abstractions; docs argue for pure Spark transforms and effectful IO boundaries.

## Decision
- Keep `EffectSystem[F]` for IO/orchestration and boundaries; provide IO/ZIO instances.
- Enforce purity for Spark transformations (return datasets/values directly, no F[_]).

## Consequences
- Pros: Clear effect boundaries; testable and portable; leverages IO/ZIO where needed.
- Cons: Requires discipline and code reviews to avoid leaking F[_] into pure paths.

## Verification
- Signature audit in engines-spark to keep transforms pure; IO-only for reads/writes/Delta/CDC/table ops.

## References
- Source: `docs/archive/design/EffectSystemResearch.md`, `CONTRIBUTING.md`
- Evidence: `docs/evidence/effect-per-module-and-purity.md`
- Plan: `docs/plan/effect-per-module-and-purity.md`

## Appendix: Source Notes (archive/design/EffectSystemResearch.md)

- Source: `docs/archive/design/EffectSystemResearch.md` @ 877826bbf05636a3db581e425901fc490cac224d on 2025-09-04T17:18:50+05:30
- Preserved verbatim artifacts: none (no sensitive tables/code blocks requiring verbatim inclusion)

Summary (faithful, no points removed)
- Premise: Assess if an effect system (F[_]) is needed and where. Core finding: Pure Spark transformations should remain pure; external IO and orchestration require effects.
- Benefits of F[_]: typed error handling, async/concurrency, resource safety, composability, testability via interpreters (IO/ZIO/Id).
- Costs of F[_]: boilerplate, learning curve, dual-layer debugging (Spark + effect runtime), little gain if wrapped around pure Spark ops.
- Verdict: For real-world pipelines (multi-source ingestion, DQ, profiling, CDC, audit, schema evolution, SLAs), an effect system is necessary for orchestration. Spark remains the execution engine, not the orchestration runtime.
- Implementation guidance:
  - Keep F[_] in algebras for non-Spark IO/orchestration; don’t wrap pure Spark transforms.
  - Use typed errors (EitherT/ZIO); compose with Kleisli; provide a local Id interpreter for tests.
  - Provide IO and ZIO instances; keep effect-polymorphic APIs via `EffectSystem[F]`.

Production-grade concerns (consolidated 35+)
- Correctness & Safety: contracts as types; schema compatibility; typed error channels; idempotency/backfill; explicit delivery semantics (exactly-once/at-least-once); DLQ/record-level retry.
- Scalability & Performance: affected partitions; partition-aware joins; shuffle minimization; CDC/incremental updates; encoding/serialization (Parquet/Avro/Arrow); stream-batch unification; ordering guarantees.
- Reliability & Resilience: audit around every stage; lineage/provenance as first-class; freshness/latency SLAs; distributed hazards (retries, timeouts, clock skew); fault isolation; transactional sink writes/outbox.
- Governance & Maintainability: profiling/anomaly detection; DQ pre/post; versioned transformations; reprocessability; contracts as public APIs; observability hooks; catalog integration; DDIA-derived consistency/serialization/transactions.

DDIA additions (selection)
- Idempotency and replay; explicit semantics (at-least/exactly-once); partition-aware shuffles.
- Consistency models; schema compatibility rules (forward/backward/full).
- Stream as first-class; batch = replay of stream.
- Distributed pitfalls (partitions, retries, timeouts, clock skew).
- Lineage/provenance as data; compression/encoding decisions impact evolution.
- Distributed transactions/outbox for multi-sink writes; data freshness SLAs; backfills; fine-grained retries/DLQ; concurrency/ordering; contracts as APIs.

Non-negotiable rules (enforcement)
- Pure Spark ops must return `Dataset[...]` directly (no F[_]).
- External IO must use F[_] with Resource/bracket patterns.
- Orchestration, config/metadata, audit, schema registry interactions are effectful.
- Use `ValidatedNel` for multi-error validation (never fail-fast exceptions) when aggregating violations.

Example composition pattern
- Tagless Final pipeline with F[_]: `read → validate → audit → profile → transform → CDC → audit → write`, with typed errors and effectful boundaries.

## End Goal (Big Picture)
- Effect polymorphism with strict purity rules, enabling engine-agnostic business logic and safe IO orchestration.

## Milestones
- M1: Leaf-module dependency slimming (see evidence/plan).
- M2: Add lint/tests for purity.

## Open Questions
- Enforcement strategy for single effect per module.
```
