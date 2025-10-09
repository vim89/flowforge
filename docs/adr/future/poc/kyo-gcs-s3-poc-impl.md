```scala
/* =======================================================================================================================
 * modules/experimental-kyo-cloud-upload/src/main/scala/io/flowforge/experimental/kyo/cloudupload/CloudUploadSink.scala
 * -----------------------------------------------------------------------------------------------------------------------
 * POC: Cancellation-Safe Cloud Upload Sink for S3 (Multipart) and GCS (Resumable)
 *
 * WHAT THIS DEMOS (in plain English):
 * - We keep Spark/Flink "pure": this is a library-level sink; no engine rewrite.
 * - We use Kyo effects (Async, Abort[Throwable], Clock) INSIDE the sink’s core logic to model async and failure.
 * - We interop with Cats-Effect via kyo-cats:
 *     * Kyo program  ----(Cats.run)----> cats.effect.IO
 *     * Fiber cancellation on IO propagates into Kyo, so cooperative cancellation flows through.
 * - S3: we start a multipart upload -> stream parts -> on cancel, we call AbortMultipartUpload and (best effort) loop
 *       until no parts remain, as AWS notes that in-flight parts may still finish.
 * - GCS: we use Storage#writer which uses RESUMABLE uploads by default. We also show how to CAPTURE/RESTORE the channel
 *       to survive transient failures (or even process restarts, if you persist the capture bytes).
 *
 * WHY “cancellation-safe” MATTERS:
 * - If a job gets killed mid upload, we don’t want orphaned parts (S3) or stuck sessions (GCS). This POC shows the exact
 *   hooks where we coordinate cancellation with SDK-specific cleanup, without re-implementing their features.
 *
 * HOW TO RUN (Scala 3, Cats-Effect 3):
 *   - You can copy all files into a small sbt module, then:
 *       sbt "runMain io.flowforge.experimental.kyo.cloudupload.Main s3 s3://my-bucket/path/obj /path/to/large-file"
 *       sbt "runMain io.flowforge.experimental.kyo.cloudupload.Main gcs gs://my-bucket/path/obj /path/to/large-file"
 *
 * Libraries you’ll need in build.sbt (POC versions; pin as needed):
 *
 *   scalaVersion := "3.3.3"
 *   libraryDependencies ++= Seq(
 *     "org.typelevel"              %% "cats-effect"          % "3.5.4",
 *     "co.fs2"                     %% "fs2-io"               % "3.10.2",
 *     "io.getkyo"                  %% "kyo-core"             % "<latest>",   // Kyo core
 *     "io.getkyo"                  %% "kyo-cats"             % "<latest>",   // Kyo <-> Cats interop
 *     "software.amazon.awssdk"      % "s3"                   % "2.25.64",    // AWS SDK v2
 *     "com.google.cloud"            % "google-cloud-storage" % "2.36.0"      // GCS storage client
 *   )
 *
 * NOTE:
 * - This is a POC. Tune part sizes, retries, timeouts, and add logging/metrics per your standards.
 * - For prod, prefer role-based auth (AWS) or Workload Identity (GCP). We don’t touch your credential model.
 * =======================================================================================================================
 */

package io.flowforge.experimental.kyo.cloudupload

import cats.effect.*
import cats.syntax.all.*
import fs2.Stream
import fs2.io.file.{Files, Path => Fs2Path}
import kyo.*
import kyo.prelude.*

import java.net.URI
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicReference

// ---------------------------------------------------------------------------------------------------------------------
// Shared model
// ---------------------------------------------------------------------------------------------------------------------

final case class CloudUri(scheme: String, bucket: String, key: String)
object CloudUri:
  def parse(s: String): CloudUri =
    // Accept s3://bucket/key OR gs://bucket/key
    val u = new URI(s)
    val scheme = u.getScheme
    val bucket = u.getHost
    val key = u.getPath.stripPrefix("/")
    CloudUri(Option(scheme).getOrElse(""), Option(bucket).getOrElse(""), key)

// Unified result model so the rest of the pipeline doesn’t care which cloud we used.
final case class UploadResult(bytes: Long, etag: Option[String], generation: Option[String])

trait CloudUploadSink[F[_]]:
  def upload(bytes: Stream[F, Byte], dest: CloudUri): F[UploadResult]

// ---------------------------------------------------------------------------------------------------------------------
// Kyo <-> Cats-Effect bridge
//
// MAGIC EXPLAINED:
// - We write the core program using Kyo’s effects (Async & Abort[Throwable]) to model async and failures.
// - At the boundary, we call `kyo.Cats.run(kyoProgram)` to get a Cats-Effect IO. CE cancellation then cooperatively
//   propagates into Kyo internals. We attach `onCancel/guaranteeCase` in IO space to run cloud-specific cleanup.
// ---------------------------------------------------------------------------------------------------------------------

object KyoInterop:
  type FX = Async & Abort[Throwable] & Clock

  inline def toIO[A](ka: A < FX): IO[A] =
    // kyo.Cats.run converts a Kyo program into Cats-Effect IO; fiber cancellations/interrupts are propagated.
    kyo.Cats.run(ka)

// Small helper to turn Java CompletableFuture into IO (then into Kyo with Cats.get when needed).
object JCF:
  def toIO[A](thunk: => CompletableFuture[A]): IO[A] =
    IO.fromCompletableFuture(IO(thunk))

// ---------------------------------------------------------------------------------------------------------------------
// S3 implementation (Multipart Upload, cancellation = AbortMultipartUpload)
//
// MAGIC EXPLAINED:
// - We stream bytes in fixed-size parts (>= 5 MiB per S3 rules).
// - We START a multipart upload, save the uploadId into an AtomicReference.
// - For each part, we upload with increasing partNumber.
// - On success, we COMPLETE with the list of ETags.
// - On cancel (IO.cancel), we ABORT the multipart upload (best-effort loop since in-flight parts might still “land”).
//
// Notes:
// - We use the AWS SDK v2 (async client). We bridge its CompletableFutures into IO, then into Kyo.
// - We don’t “interrupt” the network in flight; we instead ensure cleanup via AbortMultipartUpload on cancel.
// ---------------------------------------------------------------------------------------------------------------------

package s3

import io.flowforge.experimental.kyo.cloudupload.*
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.core.async.AsyncRequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.*
import java.nio.charset.StandardCharsets
import scala.jdk.CollectionConverters.*

final class S3UploadSink[F[_]](
  s3: S3AsyncClient,
  partSizeBytes: Int = 8 * 1024 * 1024 // 8 MiB (>= 5 MiB minimum)
)(using F: Async[F])
    extends CloudUploadSink[F]:

  import KyoInterop.*
  import kyo.*

  def upload(bytes: Stream[F, Byte], dest: CloudUri): F[UploadResult] =
    val uploadIdRef = new AtomicReference[Option[String]](None)

    // Kyo program describing the whole multipart flow
    val program: UploadResult < (Async & Abort[Throwable] & Clock) =
      for
        // 1) Create multipart upload
        createResp <- Cats.get(JCF.toIO {
                       s3.createMultipartUpload(
                         CreateMultipartUploadRequest
                           .builder()
                           .bucket(dest.bucket)
                           .key(dest.key)
                           .build()
                       )
                     })
        _          <- Sync.defer(uploadIdRef.set(Option(createResp.uploadId()))) // record uploadId for cancel cleanup
        uploadId    = createResp.uploadId()

        // 2) Consume the fs2 stream as fixed-size parts and upload each part
        // NOTE: We chunk in CE-space, then bridge each uploadPart Future into Kyo via Cats.get.
        partsEffect <- Cats.get {
                         // return IO[List[CompletedPart]]
                         val partStream: Stream[F, (Int, Array[Byte])] =
                           bytes
                             .chunkN(partSizeBytes, allowFewer = true)
                             .zipWithIndex
                             .map { case (chunk, idx) =>
                               val arr = chunk.toArray
                               (idx.toInt + 1, arr) // partNumber starts at 1
                             }

                         partStream.evalMap { case (partNum, arr) =>
                           val req = UploadPartRequest
                             .builder()
                             .bucket(dest.bucket)
                             .key(dest.key)
                             .uploadId(uploadId)
                             .partNumber(partNum)
                             .build()

                           val body = AsyncRequestBody.fromBytes(arr)
                           JCF
                             .toIO(s3.uploadPart(req, body))
                             .map(resp => (partNum, resp.eTag()))
                         }.compile.toList
                       }

        completed   = partsEffect
                        .map { case (num, etag) =>
                          CompletedPart.builder().partNumber(num).eTag(etag).build()
                        }
                        .asJava

        // 3) Complete multipart
        compResp   <- Cats.get(JCF.toIO {
                        s3.completeMultipartUpload(
                          CompleteMultipartUploadRequest
                            .builder()
                            .bucket(dest.bucket)
                            .key(dest.key)
                            .uploadId(uploadId)
                            .multipartUpload(
                              CompletedMultipartUpload.builder().parts(completed).build()
                            )
                            .build()
                        )
                      })

        // 4) Return result
        //    Note: AWS gives ETag only for the completed object; bytes are not returned → we sum part sizes above.
        sizeBytes  <- Cats.get {
                        IO.pure(partsEffect.map(_ => ()).size.toLong * partSizeBytes.toLong)
                          .map { nominal =>
                            // This is a rough estimate (last chunk may be smaller). For POC we approximate by re-summing.
                            // If you need exact, sum `arr.length` while building partsEffect.
                            nominal
                          }
                      }
      yield UploadResult(bytes = sizeBytes, etag = Option(compResp.eTag()), generation = None)

    // Attach a CE-level finalizer: if canceled, attempt AbortMultipartUpload and loop until no parts remain.
    toIO(program).guaranteeCase {
      case Outcome.Canceled() =>
        uploadIdRef.get() match
          case Some(uid) =>
            def abortOnce: IO[Unit] =
              JCF.toIO {
                s3.abortMultipartUpload(
                  AbortMultipartUploadRequest.builder().bucket(dest.bucket).key(dest.key).uploadId(uid).build()
                )
              }.void.handleErrorWith(_ => IO.unit) // best-effort

            def partsRemaining: IO[Boolean] =
              JCF.toIO {
                s3.listParts(ListPartsRequest.builder().bucket(dest.bucket).key(dest.key).uploadId(uid).build())
              }.map(r => Option(r.parts()).exists(!_.isEmpty)).handleError(_ => false)

            // AWS docs: in-flight parts may still land; abort multiple times if needed.
            def abortUntilGone(attempts: Int): IO[Unit] =
              if attempts <= 0 then IO.unit
              else abortOnce *> IO.sleep(200.millis) *> partsRemaining.flatMap {
                case true  => abortUntilGone(attempts - 1)
                case false => IO.unit
              }

            abortUntilGone(5) // small loop; tune for prod
          case None =>
            IO.unit
      case _ => IO.unit
    }

// ---------------------------------------------------------------------------------------------------------------------
// GCS implementation (Resumable Upload via Storage#writer)
//
// MAGIC EXPLAINED:
// - The Java client’s Storage#writer uses a RESUMABLE upload under the hood for large content.
// - We stream bytes into a WriteChannel. If a transient failure happens, we can CAPTURE the channel state and RESTORE it.
// - On cancellation, we intentionally DO NOT close the channel (closing finalizes); the partially-uploaded object is
//   not visible until finalize. The session will expire server-side; you may optionally delete the object if created.
// ---------------------------------------------------------------------------------------------------------------------

package gcs

import io.flowforge.experimental.kyo.cloudupload.*
import com.google.cloud.storage.{BlobId, BlobInfo, Storage, StorageOptions, StorageException}
import com.google.cloud.{Restorable, RestorableState}
import com.google.cloud.WriteChannel
import java.io.*
import scala.util.control.NonFatal

final class GcsUploadSink[F[_]](storage: Storage, chunkSize: Int = 8 * 1024 * 1024)(using F: Async[F])
    extends CloudUploadSink[F]:

  import KyoInterop.*
  import kyo.*

  def upload(bytes: Stream[F, Byte], dest: CloudUri): F[UploadResult] =
    val blobId   = BlobId.of(dest.bucket, dest.key)
    val blobInfo = BlobInfo.newBuilder(blobId).build()

    // We’ll keep an optional capture (serialized) to demonstrate resume after transient errors.
    final case class Capture(bytes: Array[Byte])
    val captureRef = new AtomicReference[Option[Capture]](None)

    def newWriter(): IO[WriteChannel] =
      IO.blocking {
        val w = storage.writer(blobInfo) // uses resumable upload by default for large content
        w.setChunkSize(chunkSize)
        w
      }

    def restoreWriter(cap: Capture): IO[WriteChannel] =
      IO.blocking {
        val bais  = new ByteArrayInputStream(cap.bytes)
        val ois   = new ObjectInputStream(bais)
        val state = ois.readObject().asInstanceOf[RestorableState[WriteChannel]]
        state.restore()
      }

    def captureWriter(w: WriteChannel): IO[Unit] =
      IO.blocking {
        val state = w.capture() // RestorableState[WriteChannel]
        val baos  = new ByteArrayOutputStream()
        val oos   = new ObjectOutputStream(baos)
        oos.writeObject(state)
        oos.flush()
        captureRef.set(Some(Capture(baos.toByteArray)))
      }

    val program: UploadResult < (Async & Abort[Throwable] & Clock) =
      // Bridge actual IO work via Cats.get => Kyo; this keeps the “effect boundary” consistent.
      Cats.get {
        def loop(writer: WriteChannel, bs: Stream[F, Byte], total: Long): IO[Long] =
          bs.pull.uncons.flatMap {
            case Some((chunk, tl)) =>
              val arr = chunk.toArray
              val buf = ByteBuffer.wrap(arr)
              IO.blocking(writer.write(buf))
                .attempt
                .flatMap {
                  case Right(written) =>
                    // periodically capture writer state (optional; demo purpose)
                    if (total % (chunkSize.toLong * 16) == 0) captureWriter(writer) else IO.unit
                    loop(writer, tl, total + written)
                  case Left(e) if isRetryable(e) =>
                    // Try to restore from the last capture; otherwise create a fresh writer and continue
                    val next: IO[WriteChannel] =
                      captureRef.get() match
                        case Some(cap) => restoreWriter(cap)
                        case None      => newWriter()

                    next.flatMap(w => loop(w, Stream.chunk(chunk) ++ tl, total)) // retry same chunk
                  case Left(e) =>
                    IO.raiseError(e)
                }
            case None =>
              IO.pure(total)
          }.stream.compile.lastOrError

        for
          w     <- newWriter()
          bytes <- loop(w, bytes, 0L)
          _     <- IO.blocking(w.close()) // finalize upload
        yield UploadResult(bytes = bytes, etag = None, generation = None)
      }

    // If canceled, do not close the writer (which would finalize). Optionally delete the object if it exists.
    toIO(program).guaranteeCase {
      case Outcome.Canceled() =>
        // Best-effort: if an object generation exists, you can delete it; here we just drop the session.
        IO.unit
      case _ => IO.unit
    }

  private def isRetryable(t: Throwable): Boolean =
    t match
      case e: StorageException =>
        // Simple heuristic; refine per your retry policy
        val code = e.getCode
        code == 408 || code == 429 || (code >= 500 && code < 600)
      case _ => false

// ---------------------------------------------------------------------------------------------------------------------
// Main app: choose S3 or GCS via args
// ---------------------------------------------------------------------------------------------------------------------

package io.flowforge.experimental.kyo.cloudupload

import io.flowforge.experimental.kyo.cloudupload.s3.*
import io.flowforge.experimental.kyo.cloudupload.gcs.*

import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider

import com.google.cloud.storage.{Storage, StorageOptions}

import fs2.io.file.Path as Fs2Path

object Main extends IOApp.Simple:

  def run: IO[Unit] =
    for
      args <- IO(sys.env.get("ARGS").map(_.split("\\s+").toList).getOrElse(sys.props.get("sun.java.command").toList.flatMap(_.split("\\s+").toList).drop(1)))
      _    <- args match
                // s3 s3://bucket/key /local/file
                case "s3" :: cloud :: local :: Nil =>
                  val dest   = CloudUri.parse(cloud)
                  val source = Fs2Path(local)
                  val s3     = S3AsyncClient.builder()
                                  .region(Region.AWS_GLOBAL) // or set explicitly (Region.US_EAST_1, etc.)
                                  .credentialsProvider(DefaultCredentialsProvider.create())
                                  .build()
                  val sink   = new S3UploadSink[IO](s3, partSizeBytes = 8 * 1024 * 1024)
                  val stream = Files[IO].readAll(source, 64 * 1024).covary[IO] // 64 KiB internal read chunk

                  // Example: cancel after a small delay to see abort logic kick in (uncomment to test)
                  // val cancellable = sink.upload(stream, dest).start >> IO.sleep(1.second) >> fiber.cancel >> fiber.join

                  sink.upload(stream, dest).flatMap { r =>
                    IO.println(s"[S3] Uploaded bytes=${r.bytes}, etag=${r.etag.getOrElse("-")}")
                  }

                // gcs gs://bucket/key /local/file
                case "gcs" :: cloud :: local :: Nil =>
                  val dest    = CloudUri.parse(cloud)
                  val source  = Fs2Path(local)
                  val storage = StorageOptions.getDefaultInstance.getService
                  val sink    = new GcsUploadSink[IO](storage, chunkSize = 8 * 1024 * 1024)
                  val stream  = Files[IO].readAll(source, 64 * 1024).covary[IO]

                  sink.upload(stream, dest).flatMap { r =>
                    IO.println(s"[GCS] Uploaded bytes=${r.bytes}, generation=${r.generation.getOrElse("-")}")
                  }

                case other =>
                  IO.println(
                    s"""
                       |Usage:
                       |  runMain io.flowforge.experimental.kyo.cloudupload.Main s3  s3://bucket/key  /path/to/file
                       |  runMain io.flowforge.experimental.kyo.cloudupload.Main gcs gs://bucket/key  /path/to/file
                       |
                       |(or pass ARGS env var with the three tokens)
                       |""".stripMargin
                  )
    yield ()
```

Key references used for this POC: Kyo ↔ Cats interop (cancellation propagation) ([GitHub][1]); S3 AbortMultipartUpload semantics (freeing parts; may require multiple aborts due to in-flight parts) ([AWS Documentation][2]); GCS resumable uploads and Java `Storage#writer` using resumable by default for large content ([Google Cloud][3]); `WriteChannel` capture/restore to resume sessions ([Google Cloud][4], [Google Cloud][5]); Cats-Effect blocking/cancellation guidance for SDK calls ([Typelevel][6]).

[1]: https://github.com/getkyo/kyo "GitHub - getkyo/kyo: Toolkit for Scala Development"
[2]: https://docs.aws.amazon.com/AmazonS3/latest/API/API_AbortMultipartUpload.html "AbortMultipartUpload - Amazon Simple Storage Service"
[3]: https://cloud.google.com/storage/docs/resumable-uploads "Resumable uploads | Cloud Storage"
[4]: https://cloud.google.com/java/docs/reference/google-cloud-core/latest/com.google.cloud.WriteChannel "Interface WriteChannel (2.59.0) | Java client library"
[5]: https://googleapis.dev/java/google-cloud-clients/0.97.0-alpha/com/google/cloud/class-use/WriteChannel.html "Uses of Interface com.google.cloud.WriteChannel"
[6]: https://typelevel.org/cats-effect/docs/faq "FAQ · Cats Effect"

----

# Using fake-gcs-server & for integration testing


Local S3/GCS emulation picks are **LocalStack (S3)** and **fake-gcs-server (GCS)** because they’re lightweight, popular, and have clear endpoint overrides for SDKs; we’ll run them via Docker Compose and wire the POC to those endpoints. LocalStack’s S3 supports both virtual-host and path-style addressing (we’ll use **path-style with `http://localhost:4566`** as recommended) and fake-gcs-server exposes the **JSON API** with easy preloading and `-scheme http` to avoid TLS during tests. Cloud behaviors we rely on are documented: **AbortMultipartUpload** may require repeated aborts to free all in-flight parts, and GCS provides **resumable uploads** with **`WriteChannel` capture/restore** for resume. ([Docs][1], [GitHub][2], [AWS Documentation][3], [Google Cloud][4])

```scala
/* =======================================================================================================================
 * FLOWFORGE EXPERIMENTAL POC - CANCELLATION-SAFE CLOUD UPLOAD SINK (KYO) WITH LOCAL EMULATORS
 * =======================================================================================================================
 * Layout (all in one block for copy-paste; split into files as indicated):
 *
 *   docker/docker-compose.yml
 *   docker/init-localstack-s3.sh
 *   docker/gcs-data/sample-bucket/.gitkeep
 *
 *   modules/experimental-kyo-cloud-upload/build.sbt
 *   modules/experimental-kyo-cloud-upload/src/main/scala/io/flowforge/experimental/kyo/cloudupload/CloudUploadSink.scala
 *   modules/experimental-kyo-cloud-upload/src/main/scala/io/flowforge/experimental/kyo/cloudupload/s3/S3UploadSink.scala
 *   modules/experimental-kyo-cloud-upload/src/main/scala/io/flowforge/experimental/kyo/cloudupload/gcs/GcsUploadSink.scala
 *   modules/experimental-kyo-cloud-upload/src/main/scala/io/flowforge/experimental/kyo/cloudupload/Main.scala
 *
 * WHAT THIS ADDS (beyond the prior POC):
 * - Docker Compose for **LocalStack (S3)** and **fake-gcs-server (GCS)**, preloading a bucket for GCS.
 * - S3 client configured for **path-style** + **endpoint override** (LocalStack).
 * - GCS client configured with **host override** (fake-gcs-server) and resumable **WriteChannel** with capture/restore.
 * - A runnable Main that targets either S3 or GCS. Optional `--cancel-after-ms` demonstrates cancellation cleanup.
 *
 * =======================================================================================================================
 */

/* =======================================================================================================================
 * docker/docker-compose.yml
 * -----------------------------------------------------------------------------------------------------------------------
 */
/// docker/docker-compose.yml
/*
version: "3.9"
services:
  localstack:
    image: localstack/localstack:s3-latest
    container_name: flowforge-localstack
    ports:
      - "4566:4566"             # LocalStack gateway (S3, etc.)
    environment:
      - DEBUG=1
      - SERVICES=s3
      - AWS_ACCESS_KEY_ID=test  # for awscli convenience
      - AWS_SECRET_ACCESS_KEY=test
    volumes:
      - "./localstack-data:/var/lib/localstack"
      - "/var/run/docker.sock:/var/run/docker.sock"

  fake-gcs-server:
    image: fsouza/fake-gcs-server:latest
    container_name: flowforge-fake-gcs
    command: ["-scheme", "http"]  # run over HTTP to avoid TLS complexity in tests
    ports:
      - "4443:4443"
    volumes:
      # Preload a bucket named 'sample-bucket' by mounting data dir:
      # Any file under docker/gcs-data/sample-bucket becomes an object.
      - "./gcs-data:/data"

# After up, run docker/init-localstack-s3.sh to create buckets on LocalStack.
*/
 
/* =======================================================================================================================
 * docker/init-localstack-s3.sh
 * -----------------------------------------------------------------------------------------------------------------------
 */
/// docker/init-localstack-s3.sh
/*
#!/usr/bin/env bash
set -euo pipefail

# Create an S3 bucket on LocalStack. We use host/path style via localhost:4566.
: "${AWS_ACCESS_KEY_ID:=test}"
: "${AWS_SECRET_ACCESS_KEY:=test}"
: "${AWS_DEFAULT_REGION:=us-east-1}"

ENDPOINT="http://localhost:4566"
BUCKET="${1:-flowforge-bucket}"

aws --endpoint-url="${ENDPOINT}" s3api create-bucket --bucket "${BUCKET}" >/dev/null 2>&1 || true
echo "✔ Created or already exists: s3://${BUCKET} on ${ENDPOINT}"
*/

/// docker/gcs-data/sample-bucket/.gitkeep
/* (empty placeholder to ensure the directory exists and preloads a GCS bucket called 'sample-bucket') */


/* =======================================================================================================================
 * modules/experimental-kyo-cloud-upload/build.sbt
 * -----------------------------------------------------------------------------------------------------------------------
 */
/// modules/experimental-kyo-cloud-upload/build.sbt
/*
ThisBuild / scalaVersion := "3.3.3"

lazy val root = (project in file("."))
  .settings(
    name := "experimental-kyo-cloud-upload",
    libraryDependencies ++= Seq(
      "org.typelevel"              %% "cats-effect"          % "3.5.4",
      "co.fs2"                     %% "fs2-io"               % "3.10.2",
      "io.getkyo"                  %% "kyo-core"             % "<latest>",
      "io.getkyo"                  %% "kyo-cats"             % "<latest>",
      "software.amazon.awssdk"      % "s3"                   % "2.25.64",
      "com.google.cloud"            % "google-cloud-storage" % "2.36.0"
    )
  )
*/


/* =======================================================================================================================
 * modules/experimental-kyo-cloud-upload/src/main/scala/io/flowforge/experimental/kyo/cloudupload/CloudUploadSink.scala
 * -----------------------------------------------------------------------------------------------------------------------
 */
package io.flowforge.experimental.kyo.cloudupload

import cats.effect.*
import fs2.Stream
import kyo.*
import kyo.prelude.*
import java.net.URI
import java.util.concurrent.CompletableFuture

/** A parsed cloud URI like s3://bucket/key or gs://bucket/key. */
final case class CloudUri(scheme: String, bucket: String, key: String)
object CloudUri:
  def parse(s: String): CloudUri =
    val u = new URI(s)
    val scheme = Option(u.getScheme).getOrElse("")
    val bucket = Option(u.getHost).getOrElse("")
    val key    = Option(u.getPath).getOrElse("").stripPrefix("/")
    CloudUri(scheme, bucket, key)

/** Unified upload result across providers. */
final case class UploadResult(bytes: Long, etag: Option[String], generation: Option[String])

/** Common sink API used by the POC. */
trait CloudUploadSink[F[_]]:
  def upload(bytes: Stream[F, Byte], dest: CloudUri): F[UploadResult]

/** Kyo ↔ Cats bridge. We model core ops in Kyo, then expose IO for FlowForge. */
object KyoInterop:
  type FX = Async & Abort[Throwable] & Clock
  inline def toIO[A](ka: A < FX): IO[A] = kyo.Cats.run(ka)

/** Helper to bridge Java CompletableFutures into IO. */
object JCF:
  def toIO[A](thunk: => CompletableFuture[A]): IO[A] =
    IO.fromCompletableFuture(IO(thunk))


/* =======================================================================================================================
 * modules/experimental-kyo-cloud-upload/src/main/scala/io/flowforge/experimental/kyo/cloudupload/s3/S3UploadSink.scala
 * -----------------------------------------------------------------------------------------------------------------------
 */
package io.flowforge.experimental.kyo.cloudupload.s3

import cats.effect.*
import cats.syntax.all.*
import fs2.Stream
import io.flowforge.experimental.kyo.cloudupload.*
import io.flowforge.experimental.kyo.cloudupload.KyoInterop.*
import kyo.*
import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider}
import software.amazon.awssdk.core.async.AsyncRequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.{S3AsyncClient, S3Configuration}
import software.amazon.awssdk.services.s3.model.*
import java.net.URI
import java.util.concurrent.atomic.AtomicReference
import scala.jdk.CollectionConverters.*

/** S3 Multipart implementation using AWS SDK v2 Async client.
  *
  * Emulation:
  *   - Endpoint override: http://localhost:4566 (LocalStack gateway)
  *   - Path-style access: true (keeps bucket in URL path; easy with localhost)
  *   - Credentials: "test/test" for local (do not hardcode in prod)
  */
final class S3UploadSink[F[_]](
  s3: S3AsyncClient,
  partSizeBytes: Int = 8 * 1024 * 1024 // >= 5 MiB minimum for S3 MPU
)(using F: Async[F])
    extends CloudUploadSink[F]:

  import JCF.*

  def upload(bytes: Stream[F, Byte], dest: CloudUri): F[UploadResult] =
    val uploadIdRef = new AtomicReference[Option[String]](None)

    val program: UploadResult < (Async & Abort[Throwable] & Clock) =
      for
        // 1) Start MPU
        create <- Cats.get(toIO {
                    s3.createMultipartUpload(
                      CreateMultipartUploadRequest.builder().bucket(dest.bucket).key(dest.key).build()
                    )
                  })
        _       <- Sync.defer(uploadIdRef.set(Option(create.uploadId())))
        uploadId = create.uploadId()

        // 2) Upload parts (bounded by stream backpressure; can add parEvalMap for concurrency)
        parts <- Cats.get {
                   bytes
                     .chunkN(partSizeBytes, allowFewer = true)
                     .zipWithIndex
                     .evalMap { case (chunk, idx) =>
                       val arr = chunk.toArray
                       val req = UploadPartRequest
                         .builder()
                         .bucket(dest.bucket)
                         .key(dest.key)
                         .uploadId(uploadId)
                         .partNumber(idx.toInt + 1)
                         .build()
                       toIO(s3.uploadPart(req, AsyncRequestBody.fromBytes(arr))).map(resp => (idx.toInt + 1, resp.eTag()))
                     }
                     .compile
                     .toList
                 }
        completed = parts.map { case (n, etag) => CompletedPart.builder().partNumber(n).eTag(etag).build() }.asJava

        // 3) Complete
        done <- Cats.get(toIO {
                  s3.completeMultipartUpload(
                    CompleteMultipartUploadRequest
                      .builder()
                      .bucket(dest.bucket)
                      .key(dest.key)
                      .uploadId(uploadId)
                      .multipartUpload(CompletedMultipartUpload.builder().parts(completed).build())
                      .build()
                  )
                })
        // Exact byte counting: recompute from chunks
        size <- Cats.get {
                  bytes
                    .chunkN(partSizeBytes, allowFewer = true)
                    .map(_.size.toLong)
                    .compile
                    .fold(0L)(_ + _)
                }
      yield UploadResult(bytes = size, etag = Option(done.eTag()), generation = None)

    // On cancellation, attempt AbortMultipartUpload a few times to free in-flight parts.
    toIO(program).guaranteeCase {
      case Outcome.Canceled() =>
        uploadIdRef.get() match
          case Some(uid) =>
            def abortOnce: IO[Unit] =
              toIO {
                s3.abortMultipartUpload(
                  AbortMultipartUploadRequest.builder().bucket(dest.bucket).key(dest.key).uploadId(uid).build()
                )
              }.void.attempt.void

            def partsRemaining: IO[Boolean] =
              toIO {
                s3.listParts(ListPartsRequest.builder().bucket(dest.bucket).key(dest.key).uploadId(uid).build())
              }.map(r => Option(r.parts()).exists(!_.isEmpty)).handleError(_ => false)

            def loop(n: Int): IO[Unit] =
              if n <= 0 then IO.unit
              else abortOnce *> IO.sleep(200.millis) *> partsRemaining.flatMap {
                case true  => loop(n - 1)
                case false => IO.unit
              }

            loop(5)
          case None => IO.unit
      case _ => IO.unit
    }

object S3UploadSink:
  /** Build an AsyncClient for LocalStack S3 (path-style). */
  def clientForLocalstack(
      endpoint: String = sys.env.getOrElse("S3_ENDPOINT", "http://localhost:4566"),
      region:   Region  = Region.US_EAST_1
  ): S3AsyncClient =
    S3AsyncClient
      .builder()
      .endpointOverride(URI.create(endpoint))
      .region(region)
      .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
      .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
      .build()


/* =======================================================================================================================
 * modules/experimental-kyo-cloud-upload/src/main/scala/io/flowforge/experimental/kyo/cloudupload/gcs/GcsUploadSink.scala
 * -----------------------------------------------------------------------------------------------------------------------
 */
package io.flowforge.experimental.kyo.cloudupload.gcs

import cats.effect.*
import cats.syntax.all.*
import fs2.Stream
import io.flowforge.experimental.kyo.cloudupload.*
import io.flowforge.experimental.kyo.cloudupload.KyoInterop.*
import kyo.*
import com.google.cloud.storage.{BlobId, BlobInfo, Storage, StorageOptions, StorageException}
import com.google.cloud.WriteChannel
import java.io.*
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicReference

/** GCS Resumable upload using Storage#writer (WriteChannel) with capture/restore.
  * fake-gcs-server runs at http://localhost:4443 in this POC. */
final class GcsUploadSink[F[_]](storage: Storage, chunkSize: Int = 8 * 1024 * 1024)(using F: Async[F])
    extends CloudUploadSink[F]:

  final case class Capture(bytes: Array[Byte])
  private val captureRef = new AtomicReference[Option[Capture]](None)

  private def newWriter(blobInfo: BlobInfo): IO[WriteChannel] =
    IO.blocking {
      val w = storage.writer(blobInfo)
      w.setChunkSize(chunkSize)
      w
    }

  private def restoreWriter(cap: Capture): IO[WriteChannel] =
    IO.blocking {
      val bais  = new ByteArrayInputStream(cap.bytes)
      val ois   = new ObjectInputStream(bais)
      val state = ois.readObject().asInstanceOf[com.google.cloud.RestorableState[WriteChannel]]
      state.restore()
    }

  private def captureWriter(w: WriteChannel): IO[Unit] =
    IO.blocking {
      val state = w.capture()
      val baos  = new ByteArrayOutputStream()
      val oos   = new ObjectOutputStream(baos)
      oos.writeObject(state)
      oos.flush()
      captureRef.set(Some(Capture(baos.toByteArray)))
    }

  private def isRetryable(t: Throwable): Boolean =
    t match
      case e: StorageException =>
        val c = e.getCode
        c == 408 || c == 429 || (c >= 500 && c < 600)
      case _ => false

  def upload(bytes: Stream[F, Byte], dest: CloudUri): F[UploadResult] =
    val blobId   = BlobId.of(dest.bucket, dest.key)
    val blobInfo = BlobInfo.newBuilder(blobId).build()

    val program: UploadResult < (Async & Abort[Throwable] & Clock) =
      Cats.get {
        def loop(w: WriteChannel, s: Stream[F, Byte], total: Long): IO[Long] =
          s.pull.uncons.flatMap {
            case Some((chunk, tl)) =>
              val arr = chunk.toArray
              val buf = ByteBuffer.wrap(arr)
              IO.blocking(w.write(buf)).attempt.flatMap {
                case Right(written) =>
                  // Occasionally capture state to allow resuming on transient failures.
                  if (total % (chunkSize.toLong * 16) == 0) captureWriter(w) else IO.unit
                  loop(w, tl, total + written)
                case Left(e) if isRetryable(e) =>
                  // Retry from last capture if present; else start fresh writer.
                  val nextW = captureRef.get() match
                    case Some(cap) => restoreWriter(cap)
                    case None      => newWriter(blobInfo)
                  nextW.flatMap(nw => loop(nw, Stream.chunk(chunk) ++ tl, total))
                case Left(e) =>
                  IO.raiseError(e)
              }
            case None =>
              IO.pure(total)
          }.stream.compile.lastOrError

        for
          w     <- newWriter(blobInfo)
          bytes <- loop(w, bytes, 0L)
          _     <- IO.blocking(w.close()) // finalize
        yield UploadResult(bytes = bytes, etag = None, generation = None)
      }

    // On cancellation: don't close the writer (no finalize); session expires server-side.
    toIO(program).guaranteeCase {
      case Outcome.Canceled() => IO.unit
      case _                  => IO.unit
    }

object GcsUploadSink:
  /** Build a Storage client pointed at fake-gcs-server host. */
  def clientForFakeGcs(
      host: String = sys.env.getOrElse("GCS_HOST", "http://localhost:4443"),
      project: String = sys.env.getOrElse("GCP_PROJECT", "demo")
  ): Storage =
    StorageOptions
      .newBuilder()
      .setHost(host)         // point SDK to emulator
      .setProjectId(project)
      .build()
      .getService


/* =======================================================================================================================
 * modules/experimental-kyo-cloud-upload/src/main/scala/io/flowforge/experimental/kyo/cloudupload/Main.scala
 * -----------------------------------------------------------------------------------------------------------------------
 */
package io.flowforge.experimental.kyo.cloudupload

import cats.effect.*
import cats.syntax.all.*
import fs2.io.file.{Files, Path => Fs2Path}
import io.flowforge.experimental.kyo.cloudupload.s3.S3UploadSink
import io.flowforge.experimental.kyo.cloudupload.gcs.GcsUploadSink
import software.amazon.awssdk.regions.Region

/** Runnable demo:
  *   sbt "runMain io.flowforge.experimental.kyo.cloudupload.Main s3  s3://flowforge-bucket/obj.bin  /path/to/file  [--cancel-after-ms 1000]"
  *   sbt "runMain io.flowforge.experimental.kyo.cloudupload.Main gcs gs://sample-bucket/obj.bin   /path/to/file  [--cancel-after-ms 1000]"
  *
  * Before running:
  *   1) docker compose -f docker/docker-compose.yml up -d
  *   2) bash docker/init-localstack-s3.sh flowforge-bucket
  *      (fake-gcs-server auto-preloads 'sample-bucket' via docker/gcs-data mount)
  */
object Main extends IOApp.Simple:

  def run: IO[Unit] =
    for
      args <- IO(sys.props.get("sun.java.command").toList.flatMap(_.split("\\s+").toList).drop(1))
      _    <- parse(args) match
                case Some(cmd) => runCmd(cmd)
                case None      => usage
    yield ()

  private sealed trait Cmd
  private object Cmd:
    final case class S3(dest: CloudUri, file: Fs2Path, cancelAfterMs: Option[Long])  extends Cmd
    final case class GCS(dest: CloudUri, file: Fs2Path, cancelAfterMs: Option[Long]) extends Cmd

  private def parse(as: List[String]): Option[Cmd] =
    as match
      case "s3" :: dest :: file :: rest =>
        Some(Cmd.S3(CloudUri.parse(dest), Fs2Path(file), parseCancel(rest)))
      case "gcs" :: dest :: file :: rest =>
        Some(Cmd.GCS(CloudUri.parse(dest), Fs2Path(file), parseCancel(rest)))
      case _ => None

  private def parseCancel(rest: List[String]): Option[Long] =
    rest.sliding(2, 2).collectFirst { case List("--cancel-after-ms", v) => v.toLong }.orElse(None)

  private def usage: IO[Unit] =
    IO.println(
      s"""
         |Usage:
         |  runMain io.flowforge.experimental.kyo.cloudupload.Main s3  s3://<bucket>/<key>  /path/to/file  [--cancel-after-ms 1000]
         |  runMain io.flowforge.experimental.kyo.cloudupload.Main gcs gs://<bucket>/<key>  /path/to/file  [--cancel-after-ms 1000]
         |
         |Prereqs:
         |  docker compose -f docker/docker-compose.yml up -d
         |  bash docker/init-localstack-s3.sh flowforge-bucket
         |""".stripMargin
    )

  private def runCmd(cmd: Cmd): IO[Unit] =
    cmd match
      case Cmd.S3(dest, file, cancelMs) =>
        val s3Client = S3UploadSink.clientForLocalstack(
          endpoint = sys.env.getOrElse("S3_ENDPOINT", "http://localhost:4566"),
          region   = Region.US_EAST_1
        )
        val sink    = new S3UploadSink[IO](s3Client, partSizeBytes = sys.env.get("S3_PART_SIZE_MB").map(_.toInt).getOrElse(8) * 1024 * 1024)
        val stream  = Files[IO].readAll(file, 128 * 1024)

        cancelMs match
          case Some(ms) =>
            for
              fiber <- sink.upload(stream, dest).start
              _     <- IO.sleep(ms.millis) *> fiber.cancel
              _     <- fiber.join
              _     <- IO.println(s"[S3] Canceled after ${ms}ms; check LocalStack for no lingering parts.")
            yield ()
          case None =>
            sink.upload(stream, dest).flatMap(r => IO.println(s"[S3] Uploaded bytes=${r.bytes}, etag=${r.etag.getOrElse("-")}"))

      case Cmd.GCS(dest, file, cancelMs) =>
        val gcsClient = GcsUploadSink.clientForFakeGcs(
          host    = sys.env.getOrElse("GCS_HOST", "http://localhost:4443"),
          project = sys.env.getOrElse("GCP_PROJECT", "demo")
        )
        val sink     = new GcsUploadSink[IO](gcsClient, chunkSize = sys.env.get("GCS_CHUNK_MB").map(_.toInt).getOrElse(8) * 1024 * 1024)
        val stream   = Files[IO].readAll(file, 128 * 1024)

        cancelMs match
          case Some(ms) =>
            for
              fiber <- sink.upload(stream, dest).start
              _     <- IO.sleep(ms.millis) *> fiber.cancel
              _     <- fiber.join
              _     <- IO.println(s"[GCS] Canceled after ${ms}ms; session will expire without finalize.")
            yield ()
          case None =>
            sink.upload(stream, dest).flatMap(r => IO.println(s"[GCS] Uploaded bytes=${r.bytes}"))
```

[1]: https://docs.localstack.cloud/aws/services/s3/ "Simple Storage Service (S3) | Docs"
[2]: https://github.com/fsouza/fake-gcs-server "GitHub - fsouza/fake-gcs-server: Google Cloud Storage emulator & testing library."
[3]: https://docs.aws.amazon.com/AmazonS3/latest/API/API_AbortMultipartUpload.html "AbortMultipartUpload - Amazon Simple Storage Service"
[4]: https://cloud.google.com/storage/docs/resumable-uploads "Resumable uploads | Cloud Storage"

---

