# Connector Capabilities

This page summarizes supported features per connector module.

Legend: ✅ supported • 🟡 partial/experimental • ⛔ not supported

| Connector | Read | Write | Streaming | Partition Pruning | Merge/Upsert | Notes |
|-----------|------|-------|-----------|-------------------|--------------|-------|
| Local FS  | ✅   | ✅    | 🟡        | 🟡                | ⛔           | CSV/JSON/Parquet/Delta (Delta via Spark) |
| S3        | ✅   | ✅    | 🟡        | 🟡                | ⛔           | Requires AWS creds; uses Hadoop FS |
| GCS       | ✅   | ✅    | 🟡        | 🟡                | ⛔           | Requires GCP creds; uses Hadoop FS |
| JDBC      | ✅   | ✅    | ⛔        | ⛔                | 🟡           | Basic upsert via key hash planned |
| Kafka     | ✅   | ✅    | ✅        | ⛔                | ⛔           | Typed serdes to be documented |
| BigQuery  | ✅   | ✅    | ⛔        | 🟡                | 🟡           | Via spark-bigquery connector |

See module READMEs for usage examples and configuration options.

