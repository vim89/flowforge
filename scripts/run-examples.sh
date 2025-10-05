#!/usr/bin/env bash
set -euo pipefail

CSV="modules/examples/examples-data/users.csv"

echo "== Spark (file mode) =="
sbt -v "examples/runMain com.flowforge.examples.runners.RunnerWiringExample --engine spark --input ${CSV} --output target/examples/spark-file-out --mode file"

echo "== Flink (file mode) =="
sbt -v "examples/runMain com.flowforge.examples.runners.RunnerWiringExample --engine flink --input ${CSV} --output target/examples/flink-file-out --mode file"

echo "== Spark (kafka mode) =="
sbt -v "examples/runMain com.flowforge.examples.runners.RunnerWiringExample --engine spark --input ${CSV} --output target/examples/spark-kafka-out --mode kafka"

echo "== Flink (kafka mode) =="
sbt -v "examples/runMain com.flowforge.examples.runners.RunnerWiringExample --engine flink --input ${CSV} --output target/examples/flink-kafka-out --mode kafka"

echo "== Kafka Pipeline (Spark) =="
sbt -v "examples/runMain com.flowforge.examples.runners.KafkaPipelineExample --engine spark --topics-dir target/examples/topics --topic users_out --output target/examples/kafka-spark-parquet"

echo "== Kafka Pipeline (Flink) =="
sbt -v "examples/runMain com.flowforge.examples.runners.KafkaPipelineExample --engine flink --topics-dir target/examples/topics --topic users_out --output target/examples/kafka-flink-parquet"

echo "All examples finished."

