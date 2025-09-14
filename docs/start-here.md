# FlowForge — Start Here

This is the quickest way to experience FlowForge’s unique value: contracts‑first pipelines that fail at compile time on schema drift, with batteries‑included Spark execution, Delta + DQ, lineage by default, and optional Flink.

What you’ll do in 5 minutes:
- Generate a project from the template
- See a compile‑time contract failure and fix it
- Run an example Spark pipeline locally
- (Optional) Send lineage to Marquez

## 1. Quickstart

1) Template

```
sbt new flowforge.g8 --name="ff-demo" --organization="com.acme"
cd ff-demo
```

2) Compile — you’ll get a red compile‑time contract error. Open the hinted file, fix field/type, re‑compile: green.

3) Run example

```
sbt run
```

## 2. Lineage (Marquez)

Start Marquez (see the official getting started):
- OpenLineage: https://openlineage.io/getting-started
- Marquez: https://marquezproject.ai/

Set env and run:

```
export OPENLINEAGE_URL=http://localhost:5000/api/v1/lineage
export OPENLINEAGE_NAMESPACE=flowforge
sbt examples/run
```

You should see FlowForge emitting START/COMPLETE/FAIL events. A screenshot is included in docs/diagrams (placeholder) showing a simple run in Marquez.

## 3. Data Quality

Deequ is optional. If present on the classpath, FlowForge enhances native checks automatically. See `docs/quality/README.md` and `docs/operating/using-deequ.md`.

## 4. Delta Ops (non‑SLA)

Use the maintenance CLI to vacuum and compact tables off the data path:

```
sbt maintenance-cli/run -- vacuum --path /tmp/delta/table --retention 168
sbt maintenance-cli/run -- compact --path /tmp/delta/table --targetFiles 4
```

## 5. Next Steps

- JDBC ingestion: Use `DataSource.JdbcSource` and `DataSink.jdbcSink`.
- CDC/SCD: See SparkDeltaSCD1IT/SparkDeltaSCD2IT (opt‑in with `-DwithSparkIT=true`).
- Affected partitions: `AffectedPartitions` utilities for daily/monthly windowing.
- Flink minimal: CSV read/write parity example.

Happy building!

