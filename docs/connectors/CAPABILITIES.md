# Connector Capabilities

This page summarizes supported features per connector module. A row describes code that is in the repository, not a plan.

Legend: ✅ supported • 🟡 partial/experimental • ⛔ not supported

| Connector | Read | Write | Streaming | Partition Pruning | Merge/Upsert | Notes |
|-----------|------|-------|-----------|-------------------|--------------|-------|
| Local FS  | ✅   | ✅    | ⛔        | 🟡                | ⛔           | CSV/JSON/Parquet/Delta (Delta via Spark), in `connectors` |
| GCS       | ✅   | ✅    | ⛔        | 🟡                | ⛔           | `connectors-gcs`, via the google-cloud-storage client rather than the Hadoop `gs://` connector; requires GCP creds |
| S3        | ⛔   | ⛔    | ⛔        | ⛔                | ⛔           | `DataSource.S3Source` and `DataSink.S3Sink` exist as types, but `SparkDataAlgebra` matches only local and JDBC endpoints and raises `UnsupportedOperationException` for the rest. The `S3Connector` in `connectors` is commented out |
| JDBC      | 🟡   | 🟡    | ⛔        | ⛔                | ⛔           | `connectors-jdbc` has tests only, no main sources. Read and write go through Spark's JDBC source; upsert is not implemented |
| Kafka     | ⛔   | ⛔    | ⛔        | ⛔                | ⛔           | No connector. `examples` carries a file-backed facade for runnable examples only |
| BigQuery  | ⛔   | ⛔    | ⛔        | ⛔                | ⛔           | No connector and no spark-bigquery dependency. `DataSource.BigQuerySource` exists as a type but nothing reads it |

Read and write here mean the connector's own API. They do not mean a `DataSource` or `DataSink` of that kind works inside a pipeline: `SparkDataAlgebra.read` matches `LocalDataSource` and `DataSource.JdbcSource` only, and `SparkDataAlgebra.write` matches `LocalDataSink` and `JdbcSink` only. GCS and S3 endpoints reach the unsupported branch. Use the connector directly for those.

Streaming is ⛔ across the board: `DataAlgebra.stream` performs one batch read and returns it as a single chunk, and no engine uses Spark Structured Streaming or the Flink DataStream API. Partition pruning is whatever Spark's reader does with the path you give it; no connector adds pruning of its own.

See module READMEs for usage examples and configuration options.

