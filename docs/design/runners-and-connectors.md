# Runners & Connectors

This page shows how FlowForge wires execution “runners” (engines) and I/O connectors without changing your pipeline code. You select an engine by choosing a `DataAlgebra[F]` implementation and plug I/O via `DataSource`/`DataSink` (connectors).

## Mental model

- Pipeline code is engine‑agnostic: it composes typed stages and returns a `Pipeline[F, In, Out]`.
- Runners are `DataAlgebra[F]` implementations (Spark, Flink). You swap them at wiring time.
- Connectors (S3, GCS, JDBC, Kafka) appear as `DataSource`/`DataSink` and live behind the same types for engines to use.

```mermaid
flowchart LR
  subgraph App
    PB[PipelineBuilder]
    PIPE[Pipeline[F,In,Out]]
  end
  subgraph Runner
    DA[DataAlgebra[F]]
    SPK[Spark]
    FLK[Flink]
  end
  subgraph Connectors
    S3[S3]
    GCS[GCS]
    JDBC[JDBC]
    KAF[Kafka]
  end

  PB --> PIPE
  PIPE --> DA
  DA -->|uses| SPK
  DA -. alternative .-> FLK
  DA --> S3
  DA --> GCS
  DA --> JDBC
  DA --> KAF
```

## Swap runners by trait (no rewrites)

```scala
// Spark
val daoSpark: DataAlgebra[IO] = SparkDataAlgebra.createSparkDataAlgebra[IO](spark).algebra
PipelineExecution.execute(pipelineWithTypedStages)(())

// Flink
val daoFlink: DataAlgebra[IO] = new FlinkDataAlgebra[IO]()
PipelineExecution.execute(pipelineWithTypedStages)(())
```

Your pipeline `pipelineWithTypedStages` doesn’t change.

## Connectors overview

| Connector | Read (DataSource) | Write (DataSink) | Notes |
|----------|--------------------|------------------|-------|
| S3       | `DataSource.s3`    | `DataSink.s3`    | Cloud auth & path style |
| GCS      | `DataSource.gcs`   | `DataSink.gcs`   | Cloud auth & path style |
| JDBC     | `DataSource.jdbc`  | `DataSink.jdbc`  | Use for small side tables or sinks |
| Kafka    | not implemented    | not implemented  | Examples use a JSONL facade, not a broker |

Kafka: In examples we ship a minimal JSONL facade (`KafkaFacade`) to simulate topics without heavy deps. Real Kafka wiring belongs to engines/connectors with the same engine‑swap pattern.

## Example wiring (runnable)

- Runner + file mode: `RunnerWiringExample` (Spark/Flink)
- Runner + kafka mode (facade): `RunnerWiringExample --mode kafka`
- Kafka pipeline read → Parquet (Spark/Flink): `KafkaPipelineExample`

See:
- `modules/examples/src/main/scala/com/flowforge/examples/runners/RunnerWiringExample.scala`
- `modules/examples/src/main/scala/com/flowforge/examples/runners/KafkaPipelineExample.scala`
- `modules/examples/src/main/scala/com/flowforge/examples/connectors/KafkaFacade.scala`

## Implementation guidance

DO
- Keep pipeline code engine‑agnostic; swap `DataAlgebra[F]` at wiring time.
- Keep resources effect‑neutral with `FlowforgeResource[F, _]`.
- Encapsulate engine‑specific config in modules, not in pipeline stages.

DON’T
- Entangle pipeline composition with SparkSession/StreamExecutionEnvironment creation.
- Leak engine config into typed contracts.

## Streaming is not implemented

No engine in this repository reads or writes a stream. `DataAlgebra.stream` exists, and on Spark it does one
batch read and returns the result as a single chunk. Nothing calls `readStream` or `writeStream`, and Kafka is
not a source `read` accepts.

A sketch of the shape an implementation would take:

- Spark Structured Streaming with Kafka: `readStream` and `writeStream` inside the Spark runner, with
  `DataAlgebra[F]` still the interface pipeline code sees.
- Flink with Kafka: the DataStream API inside the Flink runner, behind the same surface.

Streaming would be added behind the same `DataAlgebra[F]` surface, so pipeline code would not change. That is
a direction, not something this version does.
