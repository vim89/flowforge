# FlowForge

![Build](https://img.shields.io/github/actions/workflow/status/vim89/flowforge/ci.yml?branch=main&label=build)
[![Coverage](https://img.shields.io/codecov/c/github/vim89/flowforge?label=coverage)](https://app.codecov.io/gh/vim89/flowforge)
[![Scaladoc](https://img.shields.io/badge/api-Scaladoc-informational)](https://vim89.github.io/flowforge/api/)
[![Changelog](https://img.shields.io/badge/changelog-Release%20Please-blue)](CHANGELOG.md)
![Release](https://img.shields.io/github/v/release/vim89/flowforge?include_prereleases&label=release)
![License](https://img.shields.io/github/license/vim89/flowforge)
![Scala](https://img.shields.io/badge/Scala-2.13-red?logo=scala)
![sbt](https://img.shields.io/badge/sbt-1.9%2B-blue)
![JDK](https://img.shields.io/badge/JDK-17%2B-orange)
[![Docs](https://img.shields.io/badge/docs-start--here-blue)](docs/start-here.md)

Build data pipelines that fail at compile time when contracts drift, keep business logic pure, and manage IO safely. FlowForge is a Scala (2.13) framework that adds strong typing and predictable effects to Spark‑based data engineering.

Highlights
- Contracts first: schemas + evolution policies enforced at compile time via `SchemaConforms`.
- Pure transforms: pipeline logic is ordinary, testable Scala; IO is explicit via `F[_]`.
- Effect/resource safety: all connectors and engines use `Resource[F, _]` and an `EffectSystem`.
- Built‑in quality: multi‑rule validation with aggregated errors (no fail‑fast surprises).
- Optional lineage: emit OpenLineage events; see runs in Marquez with one docker compose.

Quick links: docs/getting-started-quick.md, docs/getting-started.md, docs/start-here.md

## Why FlowForge

Most frameworks surface schema issues at runtime. FlowForge catches them during `sbt compile` using typed contracts and policies. Pure transformations keep tests fast and deterministic, while effect‑safe connectors make IO boundaries deliberate and maintainable.

When to use it
- You want end‑to‑end type safety for batch/streaming pipelines on the JVM.
- You like PySpark/Scala flexibility but need guardrails around schema evolution.
- You deploy with Airflow/Prefect and need a reliable pipeline core to schedule.

How it compares (high level)
- dbt: excellent SQL lineage/testing; FlowForge targets typed Scala pipelines beyond SQL, with compile‑time contracts and effect safety.
- Airflow/Prefect: orchestration; FlowForge sits underneath as the pipeline runtime.
- PySpark/Scala UDFs: powerful but runtime‑typed; FlowForge adds compile‑time shape checks and typed connectors.

## Architecture at a glance

- core: algebras, builders, `EffectSystem`, compile‑time contract evidence.
- contracts + contracts‑sdk: author contracts and generate types from Avro.
- engines‑spark (and engines‑flink): pure transforms that return `Dataset[...]` for Spark.
- connectors‑*: S3, GCS, Kafka, JDBC, BigQuery (all effect‑safe).
- quality + quality‑deequ: rules with optional Deequ mapping.
- CLIs: validation‑cli, contracts‑extractor‑cli.
- examples: minimal runnable demos under `modules/examples`.

## 5‑minute quick start

Prereqs: Java 17+, sbt 1.9+, Docker (optional for Marquez).

1) Build the repo
```bash
sbt fmtCheck && sbt compile
```

2) Run a pipeline demo
```bash
sbt "examples/test:runMain com.flowforge.examples.spark.UsersPipeline"
```

3) See lineage (optional)
```bash
docker compose -f ops/marquez/docker-compose.yml up -d
# open http://localhost:3000 and watch runs appear
```

## Contracts 101 (compile‑time)

- Put Avro schemas under `modules/contracts-inputs/avro/...`.
- FlowForge generates a small “contracts‑sdk” at build time (see `modules/contracts-sdk`).
- The compiler requires evidence that your output type conforms to the declared contract and policy.

Sketch
```scala
import com.flowforge.core.contracts._
final case class User(id: Long, email: String, age: Int)

object UserContract {
  import SchemaPolicy._
  implicit val conforms: SchemaConforms[User, User, Exact] = implicitly
}
```
If `User` and the Avro contract diverge (extra/missing/mismatched fields), `sbt compile` fails with a precise message.

## For Python/Java engineers

- Think of contracts like Pydantic/Avro models—except enforced before the job runs.
- Transforms are just functions; you can test them without a cluster.
- Connectors expose clear, parameterized sources/sinks—no hidden globals.

## Modules overview

- modules/core: EffectSystem, builders, contract evidence, syntax helpers.
- modules/contracts: contract types and DSL; modules/contracts‑sdk: generated types.
- modules/contracts‑inputs: place Avro and metadata here (codegen source of truth).
- modules/engines‑spark: Spark transforms returning `Dataset[...]`.
- modules/connectors‑*: S3, GCS, Kafka, JDBC, BigQuery.
- modules/quality | modules/quality‑deequ: validation rules and Deequ adapter.
- modules/validation‑cli | modules/contracts‑extractor‑cli | modules/maintenance‑cli.
- modules/examples: runnable demos (`com.flowforge.examples.*`).

## Development workflow

- Build: `sbt compile`
- Tests: `sbt test` (non‑parallel)
- Format/lint: `sbt fmt` / `sbt fmtCheck`, `sbt fix` / `sbt fixCheck`
- Focused runs: `sbt core/test`, `sbt engines-spark/compile`
- Contributor guide: `CONTRIBUTING.md`

## Documentation map

- Start here: `docs/start-here.md`, quick starts in `docs/getting-started*.md`.
- Decisions (ADRs): `docs/adr` (index: `docs/adr/INDEX.md`).
- Failure anatomy: `docs/how-it-fails.md` (explains compiler messages from contract drift).
- Public API: `docs/public-api.md`.

## FAQ

- Does it require Scala knowledge?
  - You can start by treating contracts like schemas and using the examples. For deeper work, a small amount of Scala goes a long way; the code is intentionally idiomatic and test‑friendly.
- Can I use different effect systems?
  - Yes. The core uses an `EffectSystem` abstraction; Cats‑Effect is provided and ZIO interop is available.
- Where do contracts live?
  - Under `modules/contracts-inputs/avro` (and `metadata` if used). The `contracts-sdk` module is generated from there.

## License

Apache 2.0 — see `LICENSE`.
