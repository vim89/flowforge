# Gaps preventing a credible v1.0.0

> Lineage "by default" is not wired
> You have a good OpenLineageEmitter (modules/lineage/), but I couldn’t find it invoked from PipelineBuilder.build or stage boundaries.
> That means the “run → see lineage immediately” promise isn’t true yet.
Fix: Emit START/COMPLETE/FAIL from inside PipelineBuilder.build for each stage and the pipeline run.
> Include a tiny ops/marquez/docker-compose.yml walkthrough so people can see runs in Marquez without custom glue. 
> (OpenLineage/Marquez remains the safe, standard target.)

## Precise, actionable patch plan (file-mapped)

### 1) Wire lineage into lifecycle (mandatory for 1.0)
- Edit: modules/core/src/main/scala/com/flowforge/core/PipelineBuilder.scala
- On build/run: call OpenLineageEmitter.emitJobStart/Complete/Fail per stage and for the pipeline.
- Add docs: docs/operating/lineage.md with Marquez docker-compose quickstart (include ops/marquez/docker-compose.yml).
- Cite OpenLineage/Databricks if you like; the claim should be “works out of the box.”

### 2) DQ story alignment
- (adapter to Deequ): call VerificationSuite with Check rules and keep native as fallback; pin 2.0.12-spark-3.5. Update build.sbt with a Deequ profile and example.

### 3) End-to-end example module (one, polished)
- modules/examples-spark
- UsersPipeline.scala that: read.parquet → .as[User] (Encoder) → map → quality → delta sink + constraints and prints a small DQ summary.
- Guard an IT that creates a Delta table and shows NOT NULL / CHECK constraints.

### 4) Docs + versioning polish
- docs/public-api.md: label "Proposed 1.0 surface" until RC.
- docs/why/compare.md: neutral comparison with links to Frameless, Deequ, Delta/Iceberg.
- VERSION_MANAGEMENT.md: add RC checklist (API diff, scripted tests green, examples runnable).

### 5) Trim surface
- Remove or finish quality-deequ-runner and any empty templates/ folders. I'd prefer remove by moving existing to somewhere relevant module.
- Keep SparkPipelineBuilder.scala deprecated until 1.0; remove at 1.0.
- Remove `modules/contracts-sample-sdk` & it's traces

### 6) Release bar recommendation
#### Cut 0.9.0-RC1 when:
- Lineage auto-emits and the Marquez demo is documented;
- One end-to-end Spark example runs locally fast, enforces Delta constraints, and composes quality checks;
- Compile-fail tests + scripted tests are green in CI;
- Docs match behavior (native vs Deequ).

#### Ship 1.0.0 only after:
- public API is frozen (rename internal packages),
- examples and template are consistent with docs,
- all smoke tests (Spark/Flink/compile-fail) are stable on CI.

#### Then the promise reads cleanly:
- change the contract → won’t compile;
- fix types → compiles;
- run locally in seconds → see DQ + Delta constraints catch regressions;
- open Marquez → see lineage light up.

## De-scope building custom S3/Azure connectors
Short answer: **yes, you can de-scope building *custom* S3/Azure connectors for v1.0** without kneecapping adoption—*if* you lean on the storage drivers Spark already supports and you document them crisply.

### Why this is safe for v1.0

* **Spark already knows these storages.**

    * **S3** via Hadoop’s **S3A** client (`hadoop-aws` + AWS SDK). Use `s3a://…` URIs and standard Spark conf/creds. ([Apache Hadoop][1])
    * **Azure Data Lake Gen2 / Blob** via Hadoop’s **ABFS/ABFSS** driver (`hadoop-azure`). Use `abfss://…` and set `HADOOP_OPTIONAL_TOOLS=hadoop-azure`. ([Apache Hadoop][2])
    * **GCS** via the **GCS Hadoop connector**; you’ve already got this path, and it covers `gs://…`. ([Google Cloud][3], [GitHub][4])
* **Delta Lake works on all three** (S3, ADLS, GCS), so your “contracts + table constraints” story remains portable. ([Delta Lake][5])

### What to do instead of writing connectors now

1. **Make storage scheme a config, not a codepath.**
   Accept `s3a://`, `abfss://`, or `gs://` in the same `source/sink` builders. This keeps your API stable while users “bring their own” Spark/Hadoop driver. ([Apache Hadoop][1])
2. **Ship thin “recipes” in docs** (copy-pasteable):

    * S3: add `hadoop-aws` to classpath, set `spark.hadoop.fs.s3a.*` (access key, secret/role, region, path-style if needed). ([Apache Hadoop][1])
    * Azure: enable `hadoop-azure` via `HADOOP_OPTIONAL_TOOLS`, set `fs.azure.account.auth.type` & SAS/ClientCreds, use `abfss://`. ([Apache Hadoop][2])
    * GCS: add GCS connector jar; set service account JSON or workload identity; use `gs://`. ([Google Cloud][3])
3. **Provide one Spark example per storage in docs** using the *same* FlowForge pipeline, just different URI & conf blocks. Keep it “local\[\*]” fast. ([Google Cloud][3])
4. **Gate “cloud ITs” behind env flags** (`WITH_S3_IT`, `WITH_AZURE_IT`), so CI stays green without secrets. This proves portability without shipping SDK wrappers.
5. **Document Delta constraints on each backend** (the enforcement part of your story): show `ALTER TABLE … SET NOT NULL` / `ADD CONSTRAINT CHECK` on S3/ADLS/GCS. ([Delta Lake][5])
6. **Call out concurrency/consistency notes** where relevant—e.g., Delta’s S3 guidance for multi-writer workloads. ([delta.io][6])

### Recommended stance for v1.0

* **YES de-scope custom S3/Azure modules** for now.
* **YES commit to docs + examples** that show S3A/ABFSS usage with your current builder.
* **Plan S3/Azure as 1.1 plugins** only if users ask for value beyond Spark’s drivers (e.g., per-provider retries, metrics, typed creds, auto-assume-role, managed secrets).

This way you keep v1.0 focused on your signature move—**compile-time contracts + pure transforms + runtime enforcement + lineage**—while still being credibly “multi-cloud” on day one through Spark’s standard connectors. ([Apache Hadoop][1], [Google Cloud][3], [Delta Lake][5])

[1]: https://hadoop.apache.org/docs/r3.4.1/hadoop-aws/tools/hadoop-aws/index.html?utm_source=chatgpt.com "Hadoop-AWS module: Integration with Amazon Web Services"
[2]: https://hadoop.apache.org/docs/stable/hadoop-azure/index.html?utm_source=chatgpt.com "Hadoop Azure Support: ABFS - Azure Data Lake Storage Gen2"
[3]: https://cloud.google.com/dataproc/docs/concepts/connectors/cloud-storage?utm_source=chatgpt.com "Cloud Storage connector | Dataproc Documentation"
[4]: https://github.com/GoogleCloudDataproc/hadoop-connectors?utm_source=chatgpt.com "GoogleCloudDataproc/hadoop-connectors"
[5]: https://delta-docs-incubator.netlify.app/?utm_source=chatgpt.com "Welcome to the Delta Lake documentation | Delta Lake"
[6]: https://delta.io/blog/2022-05-18-multi-cluster-writes-to-delta-lake-storage-in-s3/?utm_source=chatgpt.com "Multi-cluster writes to Delta Lake Storage in S3"
[7]: https://www.businessinsider.com/microsoft-amazon-aws-azure-compare-cloud-giants-2025-7?utm_source=chatgpt.com "Azure vs AWS: The first time we get to truly compare these cloud giants"
[8]: https://www.channelinsider.com/infrastructure/cloud-and-hybrid/aws-vs-azure-vs-google-cloud/?utm_source=chatgpt.com "AWS vs. Azure vs. Google Cloud"

