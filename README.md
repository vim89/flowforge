# FlowForge

FlowForge is a type‑safe data engineering framework for building reliable pipelines on Spark (and friends) using compile‑time contracts, pure transformations, and effect‑safe IO. It aims to make schema drift, ad‑hoc quality checks, and fragile runtime configs a thing of the past.

- Contracts first: schemas and policies are typed and enforced at compile time.
- Pure transforms: business logic stays pure; IO lives behind an `F[_]` effect.
- Resource safety: connectors and engines use `Resource[F, _]` patterns.
- Quality by design: multi‑rule validation with aggregated errors (no fail‑fast surprises).
- Observability: optional lineage events with OpenLineage/Marquez.

See the quick starts: `docs/getting-started-quick.md`, `docs/getting-started.md`.

## Why FlowForge

- Schema drift shows up at compile time via `SchemaConforms` evidence, not at 2AM in prod.
- Transformations are regular Scala functions; tests stay fast and deterministic.
- IO and resources (filesystems, Kafka, JDBC, S3, GCS, BigQuery) are managed safely.
- Works with your effect system (Cats‑Effect today; ZIO interop available).

## How It Compares (High Level)

- dbt: great SQL lineage/testing; FlowForge targets typed pipelines in Scala, compile‑time contracts, and effect safety outside SQL.
- Airflow/Prefect: orchestration; FlowForge is the pipeline core you schedule there.
- PySpark/Scala UDFs: powerful but runtime‑typed; FlowForge adds compile‑time shape checks and typed connectors.

Use FlowForge when you want pipeline logic to be type‑checked end‑to‑end with predictable resource handling and clear failure modes.

## Architecture At A Glance

- `core`: algebras, types, builders, `EffectSystem`, compile‑time `SchemaConforms`.
- `contracts` + `contracts-sdk`: author and generate typed contracts.
- `engines-spark` (and `engines`/`engines-flink`): pure transforms that return `Dataset[...]` for Spark.
- `connectors-*`: S3, GCS, JDBC, Kafka, BigQuery, local FS (all `Resource[F, _]`).
- `quality` + `quality-deequ`: rule definitions and optional Deequ integration.
- CLIs: `validation-cli`, `contracts-extractor-cli`.
- Examples: runnable demos and tests to explore patterns quickly.

## Quick Start (5 Minutes)

Prereqs: Java 17+, sbt 1.9+, Scala 2.13 (set by the build).

1) Clone and build
- `sbt fmtCheck && sbt compile`

2) Run an example
- `sbt "examples/test:runMain com.flowforge.examples.spark.UsersPipeline"`
- Optional lineage: `docker compose -f ops/marquez/docker-compose.yml up -d` then open Marquez.

3) Author a contract (sketch)
```scala
import com.flowforge.core.contracts._
final case class User(id: Long, email: String, age: Int)

object UserContract {
  import SchemaPolicy._
  // Compile-time policy: out type must match contract (Exact)
  implicit val conforms: SchemaConforms[User, User, Exact] = implicitly
}
```

Use the contract in a pipeline builder; mismatches fail at compile time with a clear message.

## For Python/Java Engineers (No Scala Background?)

- Start with `docs/getting-started-quick.md` and `modules/examples`.
- Treat contracts like Pydantic/Avro schemas, but enforced at compile time.
- Transformations are just type‑safe functions; tests run on the JVM without cluster spin‑up.

## Key Features

- Compile‑time contracts: `SchemaConforms[Out, Contract, Policy]` via macros; mistakes show up during `sbt compile`.
- Pure builder: compose sources, transforms, quality rules, and sinks without side effects.
- Effect‑safe IO: connectors wrapped in `Resource[F, _]` and `EffectSystem`.
- Validation: `ValidatedNel` rules to aggregate multiple errors.
- Lineage: optional OpenLineage events; Marquez docker compose provided.

## Modules Overview

- `modules/core`: core types (EffectSystem, builders), contracts evidence, syntax helpers.
- `modules/contracts`: contract types and DSL; `modules/contracts-sdk` for codegen.
- `modules/engines-spark`: Spark‑specific transforms returning `Dataset[...]`.
- `modules/connectors-*`: S3, GCS, Kafka, JDBC, BigQuery.
- `modules/quality` | `modules/quality-deequ`: validation rules and Deequ adapter.
- `modules/validation-cli` | `modules/contracts-extractor-cli` | `modules/maintenance-cli`.
- `modules/examples`: runnable demos (see package `com.flowforge.examples`).

## Develop

- Build: `sbt compile` | Tests: `sbt test` | Format: `sbt fmt` | Lint: `sbt fixCheck`.
- Focused runs: `sbt core/test`, `sbt engines-spark/compile`.
- See `CONTRIBUTING.md` for contributor workflow and conventions.

## Documentation

- Start here: `docs/start-here.md`, `docs/getting-started.md`.
- Decisions: ADRs live in `docs/adr/` with an index in `docs/adr/INDEX.md`.
- How failures look: `docs/how-it-fails.md`.

## License

Apache 2.0. See `LICENSE`.
