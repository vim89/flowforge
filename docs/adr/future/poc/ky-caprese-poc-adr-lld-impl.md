# ADR-010: Kyo POC — Cancellation-Safe Cloud Upload Sink (S3 Multipart / GCS Resumable)

- **Status**: Proposed (Opt-in, Experimental module)
- **Date**: 2025-09-10
- **Owners**: Runtime, Connectors
- **Related**: ADR-002 (Non-Rewrite Pact), ADR-003 (Fiber-safe), ADR-005 (Kyo), ADR-008 (SLA/Metrics)

## Context

Large artifacts (audit snapshots, backfills, model exports) must upload to object stores reliably. Real-world failures (driver kill, executor loss, timeout) often leave **orphaned multipart parts** or **dangling resumable sessions** that incur storage costs or block retries. AWS S3 provides **AbortMultipartUpload** to free part storage; GCS provides **Resumable Uploads** to resume from the last committed byte. We will **use** these native features and prove correct behavior under cancellation/retry with **Kyo** effect sets and **kyo-cats** interop so CE `Fiber.cancel` propagates into Kyo computations. :contentReference[oaicite:0]{index=0} :contentReference[oaicite:1]{index=1} :contentReference[oaicite:2]{index=2}

## Decision

Implement a **FlowForge CloudUploadSink** with two backends:
- **S3**: multipart upload; on cancel/failure, call **AbortMultipartUpload**; on retry, resume only if the SDK reports parts; otherwise restart cleanly. :contentReference[oaicite:3]{index=3}
- **GCS**: **resumable** session; store the session URI (in memory or short-lived control table) and **resume** after transient failure. :contentReference[oaicite:4]{index=4}

Kyo is used **inside the sink** to model `Async & Abort[Throwable]` and to ensure **cancellation propagates** from Cats-Effect `IO` (FlowForge surface) to the SDK calls. :contentReference[oaicite:5]{index=5}

## Scope / Non-Goals

- **Scope**: binary uploads (audit bundles, parquet partitions, logs).  
- **Non-Goals**: we **do not** re-implement SDK transfer managers; we call official AWS/GCS APIs. We do **not** change Spark/Flink semantics.

## Low-Level Design

### sbt

```scala
libraryDependencies ++= Seq(
  "io.getkyo" %% "kyo-core"       % "<latest>",
  "io.getkyo" %% "kyo-cats"       % "<latest>",
  "software.amazon.awssdk" % "s3" % "<latest>",      // AWS SDK v2
  "com.google.cloud" % "google-cloud-storage" % "<latest>"
)

### Types & API

```scala
import kyo.*, kyo.prelude.*
type FX = Async & Abort[Throwable]

final case class CloudUri(scheme: String, bucket: String, key: String)
final case class UploadResult(bytes: Long, etag: Option[String], generation: Option[String])

trait CloudUploadSink[F[_]] {
  def upload(path: java.nio.file.Path, bytes: fs2.Stream[F, Byte], dest: CloudUri): F[UploadResult]
}
```

### Kyo interop boundary

```scala
object KyoInterop {
  def toIO[A](ka: A < FX): cats.effect.IO[A] = // via kyo-cats adapter
    ??? // use the kyo-cats conversion; cancellation/interrupts propagate. :contentReference[oaicite:6]{index=6}
}
```

### S3 backend sketch

```scala
final class S3UploadSink[F[_]: cats.effect.Async](s3: S3AsyncClient) extends CloudUploadSink[F] {
  def upload(path: Path, in: Stream[F, Byte], dest: CloudUri): F[UploadResult] = {
    val prog: UploadResult < FX =
      for
        uploadId <- S3Kyo.startMultipart(s3, dest)
        // split to parts, upload with bounded parallelism (see ADR-003)
        parts    <- S3Kyo.uploadParts(s3, uploadId, in, parallel = 4)
        result   <- S3Kyo.complete(s3, uploadId, parts)
      yield result

    KyoInterop.toIO(prog).onCancel {
      // ensure abort on cancellation
      cats.effect.Async[F].delay(S3Kyo.abort(s3, dest)) // AbortMultipartUpload
    }
  }
}
```

### GCS backend sketch

```scala
final class GcsUploadSink[F[_]: cats.effect.Async](gcs: Storage) extends CloudUploadSink[F] {
  def upload(path: Path, in: Stream[F, Byte], dest: CloudUri): F[UploadResult] = {
    val prog: UploadResult < FX =
      for
        session  <- GcsKyo.startResumable(gcs, dest)
        _        <- GcsKyo.pipeChunks(gcs, session, in) // resumes after transient errors
        result   <- GcsKyo.finish(gcs, session)
      yield result

    KyoInterop.toIO(prog)
  }
}
```

### Fiber-safety hooks (ADR-003 alignment)

* **Blocking** SDK calls wrapped inside Kyo `Async` and exposed through CE `IO.blocking` on the boundary.
* **Bounded** part concurrency (e.g., `parallel = 4`) to avoid heap explosions.
* **Cancellation** triggers **AbortMultipartUpload** (S3) or closes session (GCS). ([AWS Documentation][1], [Google Cloud][2])

## Implementation Plan

1. Introduce `modules/experimental-kyo-cloud-upload`.
2. Implement `S3Kyo` and `GcsKyo` helpers (start/upload/complete/abort).
3. Add `CloudUploadSink` wiring + minimal config (timeouts, chunk size).
4. Add **metrics** (bytes, parts, retries, latency) via OTel (reuse OTel guidance). ([OpenTelemetry][3])
5. Add **integration tests** using localstack (S3) and fake-gcs-server.

## Test Strategy

* **Unit**: abort called on `cancel`.
* **Integration**: kill mid-upload ⇒ S3 shows **no orphan parts** after abort; GCS resumes from prior byte range. ([AWS Documentation][1], [Google Cloud][2])
* **Load**: throttle network + forced timeouts.

## Risks & Mitigations

* **Partial uploads still finishing after abort** (AWS note) ⇒ **retry abort** until list-parts empty. ([AWS Documentation][1])
* **Resumable session loss** ⇒ fall back to fresh upload.

## References

* Kyo: **cats-effect interop** and **cancellation propagation** (README). ([GitHub][4])
* AWS S3 **AbortMultipartUpload** & best practices. ([AWS Documentation][1])
* GCS **Resumable uploads** docs. ([Google Cloud][2])
* OpenTelemetry **metrics** (counters/histograms). ([OpenTelemetry][3])


# ADR-011: Kyo POC — Stage-Scoped OpenTelemetry Metrics (Zero-Boilerplate via kyo-stats-otel)

- **Status**: Proposed (Opt-in, Experimental module)
- **Date**: 2025-09-10
- **Owners**: Observability
- **Related**: ADR-003 (Fiber-safe), ADR-005 (Kyo), ADR-008 (SLA/Metrics)

## Context

Teams want **per-stage** metrics (records in/out, latency, retries) with near-zero boilerplate. Kyo ships **`kyo-stats-otel`** which exposes a Stats effect and an OpenTelemetry exporter. We will leverage it instead of inventing metrics plumbing, and still export through standard OTel pipelines (Prometheus, Datadog). :contentReference[oaicite:15]{index=15} :contentReference[oaicite:16]{index=16}

## Decision

Add an **optional** wrapper that initializes a `Stat` scope for every stage and emits:
- `records_in`, `records_out` (counters),  
- `stage_latency_ms` (histogram),  
- `retries`, `failures` (counters).

No engine rewrite: Spark/Flink stay native; we **observe** only.

## Low-Level Design

### sbt

```scala
libraryDependencies ++= Seq(
  "io.getkyo" %% "kyo-core"        % "<latest>",
  "io.getkyo" %% "kyo-cats"        % "<latest>",
  "io.getkyo" %% "kyo-stats-otel"  % "<latest>" // OTel exporter for Kyo Stats
)
````

### Stage decorator

```scala
import kyo.*, kyo.prelude.*
import kyo.stats.otel.*

object StageMetrics:

  def wrap[A,B](name: String)(f: A => (B < (Async & Abort[Throwable]))): A => (B < (Async & Abort[Throwable])) =
    (a: A) =>
      Stats.span(name) {
        for
          _      <- Stats.counter("records_in").add(1)
          start  <- Clock.now
          b      <- f(a).tapError(_ => Stats.counter("failures").add(1))
          end    <- Clock.now
          _      <- Stats.histogram("stage_latency_ms").record(java.time.Duration.between(start, end).toMillis.toDouble)
          _      <- Stats.counter("records_out").add(1)
        yield b
      }
```

At the boundary, use **kyo-cats** to produce `IO` so FlowForge pipelines remain `F[_] = IO`. ([GitHub][4])

## Implementation Plan

1. `modules/experimental-kyo-metrics`: implement `StageMetrics.wrap`.
2. Provide `pipeline.kyoTransformWithMetrics("stage")(f)` sugar.
3. Document OTel exporter config and standard semantic names.

## Test Strategy

* Unit test counters/histograms increments per element.
* E2E: run small job, scrape OTel export; verify names and values.

## Risks & Mitigations

* OTel perf overhead negligible for our volumes; still allow a global **off switch**. ([OpenTelemetry][5])

## References

* **kyo-stats-otel** artifact (Maven/Index). ([Scaladex][6])
* OpenTelemetry **metrics data model** & perf notes. ([OpenTelemetry][3])


# ADR-012: Caprese POC — Safe UDF Slots (Compile-time Non-Capturing Closures)

- **Status**: Proposed (Opt-in, Experimental module)
- **Date**: 2025-09-10
- **Owners**: Core API
- **Related**: ADR-001 (Contracts), ADR-002 (Non-Rewrite Pact), ADR-003 (Fiber-safe), ADR-004 (Caprese)

## Context

A frequent Spark failure: a UDF captures **non-serializable** resources (DB clients, SDKs, loggers) and blows up at runtime (“Task not serializable”). We want a **compile-time** way to ensure UDFs/glue functions used in certain slots are **pure** (no capability capture). Scala 3 **Capture Checking** provides non-capturing functions `A -> B` and forbids leaking capabilities. :contentReference[oaicite:21]{index=21}

Spark community posts and guides repeatedly cite UDF **serialization pitfalls**; we will stop them at compile time for the UDF slots we mark as pure. :contentReference[oaicite:22]{index=22}

## Decision

Add **pure UDF** APIs next to the normal transforms:

```scala
import language.experimental.captureChecking
type PureFn[-A,+B] = A -> B

trait PureDsl {
  def pureTransform[A,B](name: String)(f: PureFn[A,B]): PipelineBuilder[F]
}
````

* If a developer closes over a JDBC client or secret in `f`, the code **fails to compile** (Caprese).
* For IO-using transforms, the existing `A => F[B]` remains available.

## Low-Level Design

### sbt / Scala

* Module `experimental-caprese` compiled with Scala 3.x; files that use CC include:

  ```scala
  import language.experimental.captureChecking
  ```

### Enforcement points

* `pureTransform` used for **schema defaults, simple enrichments, validation mappers**.
* In doc examples: show compile-error for capturing non-serializable objects; show accepted “pure” function. (See Rinaudo’s hands-on article for non-capturing function patterns.) ([Nicolas Rinaudo][7])

### Example

```scala
val normalize: PureFn[User, User] =
  u => u.copy(email = u.email.trim.toLowerCase) // OK

// ❌ Will not compile in pure slot:
val client = new java.sql.DriverManager(...) // example pseudo
val bad: PureFn[User, User] = u => { client.prepareStatement("..."); u }
```

## Implementation Plan

1. Introduce `PureDsl` and `pureTransform` wiring to the existing `PipelineBuilder`.
2. Add **docs**: “When to use pure UDF slots vs normal transforms.”
3. Provide **migration tip**: if code doesn’t compile, put IO inside a normal transform or scope a capability with ADR-013.

## Test Strategy

* Compile-time **negative** tests: capturing resource in `pureTransform` fails.
* Positive: pure mappers compile and run.

## Risks & Mitigations

* Capture Checking is **experimental**; keep opt-in module and examples only; if disabled, the code falls back to conventional review.

## References

* Scala 3 **Capture Checking** docs. ([Scala Documentation][8])
* Hands-on capture checking (non-capturing functions). ([Nicolas Rinaudo][7])
* Spark UDF serialization pitfalls (motivation). ([Stack Overflow][9])

# ADR-013: Caprese POC — Scoped Cloud Credentials (No-Escape Capabilities)

- **Status**: Proposed (Opt-in, Experimental module)
- **Date**: 2025-09-10
- **Owners**: Security, Connectors
- **Related**: ADR-002 (Non-Rewrite Pact), ADR-004 (Caprese), ADR-010 (Cloud Upload Sink)

## Context

Security best practices recommend **short-lived** credentials and **workload identity** (no embedded keys). AWS IAM roles issue temporary credentials; GCP **Workload Identity Federation** exchanges external identities for **short-lived** tokens. We want to **guarantee** these credentials **cannot escape** the scope where they are valid—at **compile time**. :contentReference[oaicite:27]{index=27} :contentReference[oaicite:28]{index=28} :contentReference[oaicite:29]{index=29} :contentReference[oaicite:30]{index=30} :contentReference[oaicite:31]{index=31}

## Decision

Offer capability-scoped helpers so creds **never leak**:

```scala
import language.experimental.captureChecking

final case class CloudCreds(
  aws: Option[software.amazon.awssdk.auth.credentials.AwsCredentials],
  gcpToken: Option[String]
)

def withCreds[A](acquire: => CloudCreds)(use: CloudCreds^ => A): A = {
  val c = acquire
  val out = use(c) // any attempt to return c or closures capturing c ⇒ compile error
  out
}
````

* In Spark/Flink jobs, use engine-native auth (instance profiles, workload identity). We **do not** manage tokens; we **scope** their use so they cannot be retained accidentally in globals/closures. ([AWS Documentation][10]) ([Google Cloud][11])

## Low-Level Design

* Provide `withAws(creds^ => ...)` and `withGcp(creds^ => ...)` variants that internally call the official providers (InstanceProfile, WebIdentity, WIF STS).
* Document that **no closures** that capture `creds^` can be returned or stored; the compiler enforces this (Caprese). ([Scala Documentation][8])

### Example

```scala
withCreds(AwsCredsProvider.default()) { c^ =>
  // do a signed S3 PUT using c.aws
  // OK: use within scope only
}

// ❌ compile-time error: escaping capability
val later: () => Unit =
  withCreds(AwsCredsProvider.default()) { c^ => () => println(c.aws) }
```

## Implementation Plan

1. `modules/experimental-caprese-auth`: add `withCreds`, `withAws`, `withGcp`.
2. Add examples for **S3 PUT** and **GCS GET** inside the scope.
3. Add a security doc guiding teams to prefer roles (AWS) and WIF (GCP) so creds are short-lived. ([AWS Documentation][12]) ([Google Cloud][13])

## Test Strategy

* Compile-time negative tests: returning closures that capture `CloudCreds^` fails.
* Integration: run signed requests with scoped creds; verify no creds in logs; ensure tokens are **short-lived** (WIF guidance). ([Google Cloud][11])

## Risks & Mitigations

* Capture Checking is **experimental**; keep opt-in; if disabled, retain normal `Resource`-based scoping plus code review.
* Ensure **pass-through** of engine/provider configs; do not re-implement auth flows.

## References

* AWS IAM roles (temporary credentials). ([AWS Documentation][10])
* AWS IAM best practices (use roles/temporary creds). ([AWS Documentation][12])
* GCP Workload Identity Federation (best practices, configuration). ([Google Cloud][13])
* Short-lived credential rationale. ([Firefly][14])

```
::contentReference[oaicite:42]{index=42}
```

[1]: https://docs.aws.amazon.com/AmazonS3/latest/API/API_AbortMultipartUpload.html "AbortMultipartUpload - Amazon Simple Storage Service"
[2]: https://cloud.google.com/storage/docs/resumable-uploads "Resumable uploads | Cloud Storage"
[3]: https://opentelemetry.io/docs/specs/otel/metrics/data-model/ "Metrics Data Model"
[4]: https://github.com/getkyo/kyo "getkyo/kyo: Toolkit for Scala Development"
[5]: https://opentelemetry.io/blog/2024/java-metric-systems-compared/ "OpenTelemetry Java Metrics Performance Comparison"
[6]: https://index.scala-lang.org/getkyo/kyo/artifacts/kyo-stats-otel/0.14.1 "kyo"
[7]: https://nrinaudo.github.io/articles/capture_checking.html "Hands on Capture Checking - Nicolas Rinaudo"
[8]: https://docs.scala-lang.org/scala3/reference/experimental/cc.html "Capture Checking"
[9]: https://stackoverflow.com/questions/60705536/task-not-serializable-error-while-calling-udf-to-spark-dataframe "scala - Task not serializable error while calling udf to spark ..."
[10]: https://docs.aws.amazon.com/IAM/latest/UserGuide/id_roles.html "IAM roles - AWS Identity and Access Management"
[11]: https://cloud.google.com/iam/docs/workload-identity-federation-with-other-clouds "Configure Workload Identity Federation with AWS or Azure ..."
[12]: https://docs.aws.amazon.com/IAM/latest/UserGuide/best-practices.html "Security best practices in IAM - AWS Identity and Access ..."
[13]: https://cloud.google.com/iam/docs/best-practices-for-using-workload-identity-federation "Best practices for using Workload Identity Federation"
[14]: https://www.firefly.ai/academy/setting-up-workload-identity-federation-between-github-actions-and-google-cloud-platform "Setting Up Workload Identity Federation Between GitHub ..."

---
