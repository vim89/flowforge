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
| S3       | `DataSource.s3`    | `DataSink.s3`    | Spark, Flink, batch or streaming configs |
| GCS      | `DataSource.gcs`   | `DataSink.gcs`   | Cloud auth & path style |
| JDBC     | `DataSource.jdbc`  | `DataSink.jdbc`  | Use for small side tables or sinks |
| Kafka    | engine‑specific    | engine‑specific  | Use Spark SS/Flink streaming; examples include a JSONL facade |

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

## Advanced: streaming

- Spark Structured Streaming + Kafka: use Spark SS readStream/writeStream in the Spark runner; keep `DataAlgebra[F]` the interface.
- Flink + Kafka: use DataStream API in Flink runner; same `DataAlgebra[F]` surface.

The same swap‑by‑trait principle applies.

