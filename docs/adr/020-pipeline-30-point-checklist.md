```markdown
# ADR 020 — Pipeline 30‑Point Checklist (Script vs Effectful)

- Status: Accepted
- Date: 2025-09-04

## Context
FlowForge needs a concise, shared rubric to evaluate and design pipelines. Teams often start with ad‑hoc “script style” code; this ADR establishes a 30‑point checklist mapping each concern from a bare approach to an effectful, tagless‑final pattern aligned with our architecture (contracts‑first, pure Spark transforms, effectful IO/orchestration).

## Decision
Adopt the following 30‑point checklist as a normative rubric for reviews, templates, and examples. For each concern, the ‘Effectful’ guidance is the target style; ‘Naked/Bare’ highlights the common baseline we are replacing.

1) Data Contracts
- Naked: Runtime schema assertions (df.schema)
- Effectful: Compile‑time with case classes, refined types, ADTs

2) Source Read
- Naked: spark.read.jdbc/parquet/json directly
- Effectful: Pipeline[F].read* with typed datasets

3) Data Quality Checks
- Naked: df.filter(...) with runtime conditions
- Effectful: Composable algebras validate[A]: Dataset[A] => F[Dataset[A]]

4) Profiling
- Naked: Manual .count() or .describe()
- Effectful: Structured DataProfile(count, schema, stats) algebra

5) Business Transformation
- Naked: Inline .withColumn or .map
- Effectful: Pure functions + effectful transforms (transform[F])

6) CDC (Change Data Capture)
- Naked: Outer joins + manual diffs
- Effectful: Algebra performDelta; extendable to Delta Lake

7) Audit Logging
- Naked: println
- Effectful: audit: F[Unit] → pluggable to Kafka/BQ/JSON logs

8) Multiple Sinks
- Naked: Copy‑paste .write.parquet/etc.
- Effectful: Algebra write/writeBatch/writeWithOptions

9) Error Handling
- Naked: Exceptions → crash
- Effectful: Typed error channels (EitherT, Validated)

10) Restartability
- Naked: Rerun whole pipeline
- Effectful: detectAffectedPartitions + resume

11) Huge Data Partitioning
- Naked: Manual .repartition
- Effectful: Partitioning algebra + restart‑safe checkpoints

12) Schema Evolution
- Naked: Risky (breaks silently)
- Effectful: Versioned contracts, schema registry integration

13) Lineage
- Naked: None unless manually logged
- Effectful: LineageRecord in Dataset metadata

14) Reproducibility
- Naked: Fragile (non‑deterministic sources)
- Effectful: Idempotent reads, audit logs, contract‑pinned schema

15) Consistency Models
- Naked: Ignored
- Effectful: Explicit modeling (eventual, transactional, watermark)

16) Batch vs Stream
- Naked: Different entry points
- Effectful: Unified algebra readStream/readBatch

17) Backpressure
- Naked: Left to Spark defaults
- Effectful: FS2/ZIO‑Streams integration

18) Idempotence
- Naked: Reruns duplicate output
- Effectful: Write algebra ensures overwrite/merge/upsert

19) Exactly‑Once Semantics
- Naked: Not guaranteed
- Effectful: Structured sinks (Delta, Kafka transactional writes)

20) Resource Management
- Naked: Manual SparkSession lifecycle
- Effectful: Managed via Resource[F, SparkSession]

21) Monitoring
- Naked: Spark UI only
- Effectful: Algebra pushes metrics to Prometheus/Grafana

22) Security
- Naked: Credentials inline
- Effectful: Secret managers / KMS via algebra

23) Isolation
- Naked: Shared mutable state
- Effectful: Pure functional algebras (referential transparency)

24) Concurrency
- Naked: par.foreach (unsafe)
- Effectful: Parallel[F].parTraverse with controlled fibers

25) Retry Policies
- Naked: Manual try/catch
- Effectful: retryingOnAllErrors[F] (cats‑retry, ZIO Schedule)

26) Timeouts
- Naked: None (risk of hang)
- Effectful: Temporal[F].timeoutTo

27) Circuit Breaking
- Naked: None
- Effectful: CircuitBreaker[F] integration

28) Testing / Mockability
- Naked: Hard to mock Spark
- Effectful: Algebras as interfaces → replace with in‑mem impl

29) Composability
- Naked: Imperative, glued code
- Effectful: Pipelines are Kleisli arrows (A => F[B])

30) Extensibility
- Naked: Every feature = new if/else
- Effectful: New algebra instance/interpreter; no if/else creep

## Consequences
- Pros: Shared vocabulary and review rubric; promotes portability, testability, and safety; aligns with contracts‑first and effect boundaries.
- Cons: Higher initial learning curve; may require refactors from script style; introduces more interfaces and instances.

## Verification
- Code reviews reference checklist items explicitly.
- Templates and examples demonstrate effectful patterns for the top‑priority concerns.
- CI docs job ensures ADR link presence in READMEs and template instructions.

## References
- ADR‑012 Effect System Decision; ADR‑002 Spark Purity & IO Boundaries; ADR‑011 Contracts Build/Compile Gates; ADR‑013 Infrastructure Layer; ADR‑014 QA Strategy; ADR‑018 Roadmap Baseline.
- Evidence: `docs/evidence/effect-per-module-and-purity.md`, `docs/evidence/compile-build-gates.md`.

## Open Questions
- Which items should be enforced by scalafix/CI vs review checklists first?
- How to phase adoption for existing pipelines without large rewrites?
```
