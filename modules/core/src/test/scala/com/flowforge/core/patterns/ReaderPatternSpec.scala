// A test is an edge, so it runs the effect here rather than handing it to an IOApp.
// scalafix:off DisableSyntax.noUnsafeRunSync
package com.flowforge.core.patterns

import cats.data.ReaderT
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.flowforge.core.algebra.EffectSystem
import com.flowforge.core.impl.InMemoryDataAlgebra
import com.flowforge.core.instances.{ DefaultCodecs, EffectInstances }
import com.flowforge.core.patterns.ReaderPattern._
import com.flowforge.core.testing.How
import com.flowforge.core.types.{ DataFormat, Environment, LocalDataSource }
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.nio.file.Files
import java.util.concurrent.atomic.{ AtomicInteger, AtomicReference }
import scala.concurrent.duration._

class ReaderPatternSpec extends AnyFunSuite with Matchers {

  implicit val es: EffectSystem[IO] = EffectInstances.catsEffectSystemInstance
  implicit val stringDecoder: com.flowforge.core.algebra.DataDecoder[String] =
    DefaultCodecs.stringDecoder

  /** Records what a service was asked to do, so a test can assert on it instead of on a mock framework. */
  private final class Recorder {
    private val entries = new AtomicReference[List[String]](Nil)

    def record(entry: String): IO[Unit] = IO(entries.updateAndGet(entry :: _)).void
    def recorded: List[String]          = entries.get().reverse
  }

  private def recordingLogger(recorder: Recorder): Logger[IO] = new Logger[IO] {
    def debug(message: String): IO[Unit] = recorder.record(s"debug:$message")
    def info(message: String): IO[Unit]  = recorder.record(s"info:$message")
    def warn(message: String): IO[Unit]  = recorder.record(s"warn:$message")
    def error(message: String, throwable: Option[Throwable]): IO[Unit] =
      recorder.record(s"error:$message")
    def withContext(context: Map[String, String]): Logger[IO] = this
  }

  private def recordingMetrics(recorder: Recorder): MetricsCollector[IO] =
    new MetricsCollector[IO] {
      def counter(
        name: String,
        value: Long,
        tags: Map[String, String],
      ): IO[Unit] = recorder.record(s"counter:$name=$value:${tags.getOrElse("environment", "?")}")
      def gauge(
        name: String,
        value: Double,
        tags: Map[String, String],
      ): IO[Unit] = recorder.record(s"gauge:$name=$value:${tags.getOrElse("environment", "?")}")
      def histogram(
        name: String,
        value: Double,
        tags: Map[String, String],
      ): IO[Unit] = recorder.record(s"histogram:$name=$value")
      def timer[A](name: String, tags: Map[String, String])(operation: IO[A]): IO[A] = operation
    }

  private def recordingAudit(recorder: Recorder): AuditService[IO] = new AuditService[IO] {
    def recordAccess(
      resource: String,
      action: String,
      user: String,
    ): IO[Unit] = recorder.record(s"access:$resource:$action")
    def recordDataChange(
      table: String,
      operation: String,
      recordCount: Long,
    ): IO[Unit] = recorder.record(s"change:$table:$operation:$recordCount")
    def recordPipelineExecution(pipelineId: String, status: String): IO[Unit] =
      recorder.record(s"execution:$pipelineId:$status")
    def queryAuditLog(query: AuditQuery): IO[List[AuditRecord]] = IO.pure(Nil)
  }

  /**
   * A context with the real in-memory algebra and services that record what they were asked to do.
   *
   * Everything a test does not assert on comes from [[ReaderPattern.testContext]], so adding a dependency to
   * the container does not mean touching every test here.
   */
  private def context(
    recorder: Recorder,
    settings: Map[String, String] = Map.empty,
  ): AppContext[IO] = {
    val base = testContext[IO]
    base.copy(core =
      base.core.copy(
        config = base.core.config.copy(settings = base.core.config.settings ++ settings),
        dataAlgebra = new InMemoryDataAlgebra[IO],
        logger = recordingLogger(recorder),
        metrics = recordingMetrics(recorder),
        auditService = recordingAudit(recorder),
      ),
    )
  }

  test("a component resolved against a context runs as an ordinary pipeline", How) {
    val recorder = new Recorder

    val built = ReaderPattern
      .pipeline[IO, Int, Int](
        "doubler",
        ReaderPattern.component[IO, Int, Int]("double", (_, n) => IO.pure(n * 2)),
        stages = List("double"),
      )
      .run(context(recorder))
      .unsafeRunSync()

    built.name shouldBe "doubler"
    built.stages shouldBe List("double")
    built.metadata.tags.get("di") shouldBe Some("reader")
    built.execute(21).unsafeRunSync() shouldBe 42
  }

  test("the default stage list reports one stage and one transformation", How) {
    val recorder = new Recorder

    val built = ReaderPattern
      .pipeline[IO, Int, Int](
        "doubler",
        ReaderPattern.component[IO, Int, Int]("double", (_, n) => IO.pure(n * 2)),
      )
      .run(context(recorder))
      .unsafeRunSync()

    built.stages shouldBe List("doubler")
    built.metadata.transformations shouldBe built.stages.size
  }

  test("composed components run in order and each one sees the same context", How) {
    val recorder = new Recorder

    val read = ReaderPattern.component[IO, Unit, String](
      "read",
      (ctx, _) => ctx.core.logger.info("read").as(ctx.environment.name),
    )
    val shout = ReaderPattern.component[IO, String, String](
      "shout",
      (ctx, s) => ctx.core.logger.info("shout").as(s.toUpperCase),
    )

    val built = ReaderPattern
      .pipeline[IO, Unit, String](
        "env-name",
        ReaderPattern.composeComponents(read, shout),
        stages = List("read", "shout"),
      )
      .run(context(recorder))
      .unsafeRunSync()

    built.execute(()).unsafeRunSync() shouldBe Environment.Testing.name.toUpperCase
    recorder.recorded shouldBe List("info:read", "info:shout")
    built.metadata.transformations shouldBe 2
  }

  test("a reader-built pipeline reads a source through the injected algebra", How) {
    val recorder = new Recorder
    val in       = Files.createTempFile("ff-reader", ".jsonl")
    val _        = Files.write(in, "alpha\nbeta\n".getBytes("UTF-8"))

    val load: FlowForgeReaderT[IO, List[String]] =
      for {
        _ <- Operations.log[IO](LogLevel.Info, "loading")
        dataset <- Operations.createDataReader[IO, String](
          LocalDataSource(in.toString, DataFormat.JSONL),
        )
        _ <- Operations.recordMetric[IO]("records", dataset.size.toDouble, MetricType.Counter)
      } yield dataset.data

    val records = Operations
      .withAudit[IO, List[String]]("source", "read", load)
      .run(context(recorder))
      .unsafeRunSync()

    records shouldBe List("alpha", "beta")
    recorder.recorded shouldBe List(
      "access:source:read",
      "info:loading",
      "counter:records=2:Testing",
      "access:source:read_completed",
    )
  }

  test("configuration lookup prefers the environment-specific key", How) {
    val recorder = new Recorder
    val ctx = context(
      recorder,
      Map("batch.size" -> "10", "testing.batch.size" -> "99"),
    )

    Operations.getConfig[IO]("batch.size", "0").run(ctx).unsafeRunSync() shouldBe "99"
    Operations.getConfig[IO]("missing.key", "fallback").run(ctx).unsafeRunSync() shouldBe
      "fallback"
  }

  test("a retried operation logs each attempt and gives up after the last one", How) {
    val recorder = new Recorder
    val attempts = new AtomicInteger(0)

    val failing: FlowForgeReaderT[IO, Int] = ReaderT.liftF(
      IO(attempts.incrementAndGet()).flatMap(n => IO.raiseError[Int](new RuntimeException(s"boom $n"))),
    )

    val result = Operations
      .withRetry[IO, Int](maxRetries = 2, failing)
      .run(context(recorder))
      .attempt
      .unsafeRunSync()

    result.isLeft shouldBe true
    attempts.get() shouldBe 3
    recorder.recorded shouldBe List(
      "warn:Operation failed, retrying (attempt 1/2)",
      "warn:Operation failed, retrying (attempt 2/2)",
    )
  }

  test("a retried operation stops as soon as one attempt succeeds", How) {
    val recorder = new Recorder
    val attempts = new AtomicInteger(0)

    val flaky: FlowForgeReaderT[IO, Int] = ReaderT.liftF(
      IO(attempts.incrementAndGet()).flatMap {
        case 1 => IO.raiseError[Int](new RuntimeException("boom"))
        case n => IO.pure(n)
      },
    )

    Operations
      .withRetry[IO, Int](maxRetries = 5, flaky)
      .run(context(recorder))
      .unsafeRunSync() shouldBe 2
    recorder.recorded shouldBe List("warn:Operation failed, retrying (attempt 1/5)")
  }

  test("an operation that outruns its timeout is reported as a timeout", How) {
    val recorder = new Recorder

    val slow: FlowForgeReaderT[IO, Int] = ReaderT.liftF(IO.sleep(2.seconds).as(1))

    val result = Operations
      .withTimeout[IO, Int](50.millis, slow)
      .run(context(recorder))
      .attempt
      .unsafeRunSync()

    result.left.map(_.getClass) shouldBe
      Left(classOf[java.util.concurrent.TimeoutException])
    recorder.recorded shouldBe List("error:Operation timed out after 50 milliseconds")
  }

  test("a new request keeps the dependencies and takes a fresh request id", How) {
    val ctx   = context(new Recorder)
    val child = ctx.newRequest

    child.requestId should not be ctx.requestId
    child.core shouldBe ctx.core
    child.environment shouldBe Environment.Testing
  }
}
