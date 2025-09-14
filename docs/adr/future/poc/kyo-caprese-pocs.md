Here are **4 lean, high-impact POC topics—2 for Kyo, 2 for Caprese**—that plug directly into FlowForge (Scala + Spark + Cloud) without reinventing engine features. Each POC includes the value prop, what to build, how to demo/measure, and the “why” with sources.

---

## KYO – POC #1: Cancellation-safe Cloud Upload Sink (S3 Multipart / GCS Resumable)

**Real value-add:** Large checkpoint/artifact/log uploads should be resumable and *safe to cancel* (so partial parts don’t leak, jobs stop cleanly). Use Kyo’s **effect sets** and **cats-effect interop** so cancellations and timeouts propagate correctly to the underlying Java SDK clients. ([GitHub][1])

**What to build**

* A FlowForge `CloudUploadSink` with two backends:

    * **S3 multipart**: start, upload parts in chunks, **abort on cancel/failure**; support bucket lifecycle cleanups. ([AWS Documentation][2])
    * **GCS resumable**: create session, stream chunks, **resume on retry**, finalize or cancel. ([Google Cloud][3])
* Stage signatures (IO userland, Kyo inside):

  ```scala
  // Cats-Effect edge; inside we run Kyo <(Async & Abort[Throwable] & Clock)>
  def upload(path: Path, bytes: Stream[F, Byte], dest: CloudUri): F[UploadResult]
  ```

  Internally: `Clock` for deadlines, `Abort` for fail-fast, `Async` for I/O; bridge via **kyo-cats** so CE `Fiber.cancel` interrupts Kyo fibers as documented. ([GitHub][1])

**How to demo**

* Trigger a big upload, cancel mid-way; verify **S3 AbortMultipartUpload** invoked and no orphaned parts billed; verify GCS **resumable session** resumes after retry. ([AWS Documentation][4], [Google Cloud][3])

**Measure**

* No leaked MPU parts after cancel; resumable completion rate; wall-clock saved on retries.

---

## KYO – POC #2: Built-in OTel Metrics per Stage via `kyo-stats-otel`

**Real value-add:** First-class, zero-boilerplate **OpenTelemetry** counters/histograms for *every* stage (records in/out, latency, retries). Kyo already ships a **Stats** effect with an OTel exporter—use it rather than adding a new metrics stack. ([GitHub][1])

**What to build**

* Tiny wrapper that auto-initializes a `Stat` scope for each pipeline stage and emits:

    * `records_in`, `records_out` (counters)
    * `stage_latency_ms` (histogram)
    * `retries`, `failures` (counters)
* Keep engine features pure (Spark/Flink stay native); we just **observe**. Spark’s EOS still relies on checkpoint + idempotent sink (documented). ([Apache Spark][5])

**How to demo**

* Run a small Spark job with two stages; view OTel exporter output (Prom/Grafana/DataDog via OTel pipeline).

**Measure**

* Metrics available with no user wiring; <10 LoC to instrument a new stage.

---

## CAPRESE – POC #1: “Safe UDF” Slots (Compile-time Non-capturing Closures)

**Real value-add:** Prevent classic Spark failures where UDFs capture **non-serializable** resources (e.g., JDBC clients, SDKs) causing `Task not serializable` at runtime. Caprese **rejects** such captures at *compile time*. ([Scala Documentation][6], [Nicola Ferraro][7], [Medium][8])

**What to build**

* Add an optional API next to normal transforms:

  ```scala
  import language.experimental.captureChecking
  type PureFn[-A,+B] = A -> B   // non-capturing
  def pureTransform[A,B](name: String)(f: PureFn[A,B]): Pipeline[F]
  ```
* If a user tries to close over a JDBC client / Secrets / Logger, compilation fails (no more “works locally, explodes on cluster” surprises). ([Scala Documentation][6])

**How to demo**

* Two UDFs: one pure (OK), one capturing a non-serializable pool (compile error). Compare with typical Spark guidance around serialization pitfalls. ([Nicola Ferraro][7], [Medium][8])

**Measure**

* Number of serialization incidents prevented; time saved vs. debugging executor crashes.

---

## CAPRESE – POC #2: Scoped Cloud Credentials (No-escape Capabilities)

**Real value-add:** Enforce “**credentials never leak**” by construction. Wrap short-lived AWS/GCP credentials in a **capability** and prove they *cannot escape* a scope or be stored in closures—aligns with security best practices (don’t hardcode keys; prefer roles/Workload Identity; no long-lived secrets). ([Scala Documentation][6], [AWS Documentation][9], [Google Cloud][10])

**What to build**

* A tiny helper:

  ```scala
  import language.experimental.captureChecking
  def withCreds[A](acquire: => CloudCreds)(use: CloudCreds^ => A): A
  ```

  The compiler disallows returning `CloudCreds^` or any closure capturing it. Pair with engine-native auth (IAM roles / GCP SA w/o user-managed keys). ([AWS Documentation][9], [Google Cloud][11])
* Logically complements **“Non-Rewrite Pact”**: we don’t change Spark/Kafka; we just guarantee secrets management discipline in user glue code.

**How to demo**

* Inside `withCreds`, run a signed S3 PUT or GCS GET; attempt to stash creds into a function/val → **compile error**. Also show that no keys appear in logs or UDFs.

**Measure**

* Zero leaked creds in code review; blocked attempts to capture/store creds; security sign-off faster.

---

### Why these four POCs are “real” and not overkill

* They **solve frequent production problems**: upload cancellation/cleanup (Cloud docs emphasize abort/resume), Spark UDF serialization foot-guns, and secret leakage. ([AWS Documentation][4], [Google Cloud][3], [Nicola Ferraro][7])
* They **leverage** (not replace) pioneers: Spark’s EOS stays the Spark way; Delta `MERGE` stays the CDC path; we only add guardrails and observability. ([Apache Spark][5], [Delta Lake][12])
* They are **tiny surface-area**: one sink, one metrics shim, one `pureTransform`, one `withCreds`.

---

### Extra pointers (for docs you’ll cite in each POC)

* **Kyo** cats-effect interop, cancellation propagation, Stats/OTel exporters: GitHub README. ([GitHub][1])
* **Capture checking** overview and syntax (`->`, `^`, capture sets): Scala 3 experimental docs and papers. ([Scala Documentation][6], [ABG][13])
* **Spark exactly-once** = replayable source + checkpoint/WAL + idempotent sink. ([Apache Spark][5])
* **Delta Lake MERGE** for upserts/CDC. ([Delta Lake][12])
* **Debezium offsets & schema history** for restart-safety (if you later wire CDC demos). ([Debezium][14])
* **Cloud upload semantics** (abort multipart / resumable uploads). ([AWS Documentation][4], [Google Cloud][3])
* **Secrets best practices** (don’t embed keys; prefer roles/impersonation). ([AWS Documentation][9], [Google Cloud][10])

If you want, I can turn any of these into a small, runnable `modules/experimental` sample (4–6 files each) that you can drop into FlowForge to demo end-to-end.

[1]: https://github.com/getkyo/kyo "GitHub - getkyo/kyo: Toolkit for Scala Development"
[2]: https://docs.aws.amazon.com/AmazonS3/latest/userguide/mpuoverview.html?utm_source=chatgpt.com "Uploading and copying objects using multipart upload in ..."
[3]: https://cloud.google.com/storage/docs/resumable-uploads?utm_source=chatgpt.com "Resumable uploads | Cloud Storage"
[4]: https://docs.aws.amazon.com/AmazonS3/latest/API/API_AbortMultipartUpload.html?utm_source=chatgpt.com "AbortMultipartUpload - Amazon Simple Storage Service"
[5]: https://spark.apache.org/docs/3.5.1/structured-streaming-programming-guide.html?utm_source=chatgpt.com "Structured Streaming Programming Guide"
[6]: https://docs.scala-lang.org/scala3/reference/experimental/cc.html?utm_source=chatgpt.com "Capture Checking"
[7]: https://www.nicolaferraro.me/2016/02/22/using-non-serializable-objects-in-apache-spark/?utm_source=chatgpt.com "Using Non-Serializable Objects in Apache Spark"
[8]: https://kasata.medium.com/understanding-the-task-not-serializable-error-in-apache-spark-and-how-to-fix-it-919f44a042a4?utm_source=chatgpt.com "Understanding the “Task Not Serializable” Error in Apache ..."
[9]: https://docs.aws.amazon.com/IAM/latest/UserGuide/securing_access-keys.html?utm_source=chatgpt.com "Secure access keys - AWS Identity and Access Management"
[10]: https://cloud.google.com/iam/docs/best-practices-for-managing-service-account-keys?utm_source=chatgpt.com "Best practices for managing service account keys - IAM"
[11]: https://cloud.google.com/iam/docs/best-practices-service-accounts?utm_source=chatgpt.com "Best practices for using service accounts - IAM"
[12]: https://docs.delta.io/latest/delta-update.html?utm_source=chatgpt.com "Table deletes, updates, and merges - the Delta Lake documentation"
[13]: https://abgru.me/publication/capturing-types/capturing-types.pdf?utm_source=chatgpt.com "Keeping Track of Capabilities - ABG"
[14]: https://debezium.io/documentation/reference/stable/connectors/mysql.html?utm_source=chatgpt.com "Debezium connector for MySQL"
