package com.flowforge.core.exec

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.flowforge.core.PipelineBuilder
import com.flowforge.core.algebra.EffectSystem
import com.flowforge.core.contracts.SchemaPolicy
import com.flowforge.core.instances.EffectInstances
import com.flowforge.core.lineage.{ LineageError, OpenLineageEmitter }
import com.flowforge.core.observability.Tracer
import com.flowforge.core.testing.How
import com.flowforge.core.types._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable.ListBuffer

/**
 * Covers the three things the builder claimed but did not do: honour a tracer attached before the first
 * stage, scope a span to one stage, and emit lineage when an emitter is configured.
 */
object StageComposerSpec {
  final case class UserContract(id: Long, name: String)
}

class StageComposerSpec extends AnyFunSuite with Matchers {

  import StageComposerSpec.UserContract

  implicit val es: EffectSystem[IO] = EffectInstances.catsEffectSystemInstance

  private val source = TypedSource[UserContract](DataSource.local("/tmp/in", DataFormat.JSON))
  private val sink   = TypedSink[UserContract](DataSink.local("/tmp/out", DataFormat.JSON))
  private val reader = (_: DataSource) => IO.pure(UserContract(1L, "Alice"))
  private val writer = (_: UserContract, _: DataSink) => IO.unit

  /** Records span entry and exit so a span's extent can be checked, not just that it was opened. */
  private final class RecordingTracer extends Tracer[IO] {
    val events: ListBuffer[String] = ListBuffer.empty

    def inSpan[A](name: String)(fa: IO[A]): IO[A] =
      IO(events += s"enter:$name") *> fa.guarantee(IO(events += s"exit:$name").void)

    def annotate(key: String, value: String): IO[Unit] = IO.unit
  }

  private final class RecordingEmitter(failEveryEmit: Boolean = false) extends OpenLineageEmitter[IO] {
    val events: ListBuffer[String] = ListBuffer.empty
    val runIds: ListBuffer[String] = ListBuffer.empty

    private def record(kind: String, jobName: String, runId: String): IO[Either[LineageError, Unit]] =
      IO {
        events += s"$kind:$jobName"
        runIds += runId
        if (failEveryEmit) Left(LineageError("emitter is down")) else Right(())
      }

    def emitJobStart(ns: String, jobName: String, runId: String): IO[Either[LineageError, Unit]] =
      record("START", jobName, runId)

    def emitJobComplete(ns: String, jobName: String, runId: String): IO[Either[LineageError, Unit]] =
      record("COMPLETE", jobName, runId)

    def emitJobFail(
      ns: String,
      jobName: String,
      runId: String,
      error: String,
    ): IO[Either[LineageError, Unit]] =
      record("FAIL", jobName, runId)
  }

  private def pipelineWith(
    name: String,
    tracer: Option[Tracer[IO]] = None,
    lineage: Option[OpenLineageEmitter[IO]] = None,
    tracerFirst: Boolean = true,
  ) = {
    val empty = PipelineBuilder[BuilderState.Empty, IO, Unit, Unit](name)
    val head  = if (tracerFirst) tracer.fold(empty)(empty.withTracer) else empty
    val withLineage = lineage.fold(head)(head.withLineageEmitter)
    val sourced = withLineage
      .addTypedSource[UserContract, UserContract, SchemaPolicy.Exact](source, reader)
      .noTransform
      .addTypedSink[UserContract, SchemaPolicy.Exact](sink, writer)
    val finished = if (tracerFirst) sourced else tracer.fold(sourced)(sourced.withTracer)
    finished.build()
  }

  test("tracer attached before the first stage is still used at build time", How) {
    val tracer = new RecordingTracer
    pipelineWith("tracer-first", tracer = Some(tracer), tracerFirst = true)
      .execute(())
      .unsafeRunSync()

    // Before the fix the stage methods rebuilt the case class without the tracer field, so this was empty.
    tracer.events should not be empty
  }

  test("tracer attached after the stages behaves the same as one attached before", How) {
    val first = new RecordingTracer
    val last  = new RecordingTracer
    pipelineWith("t-first", tracer = Some(first), tracerFirst = true).execute(()).unsafeRunSync()
    pipelineWith("t-last", tracer = Some(last), tracerFirst = false).execute(()).unsafeRunSync()

    first.events.toList shouldBe last.events.toList
  }

  test("each span covers one stage instead of every stage before it", How) {
    val tracer = new RecordingTracer
    pipelineWith("span-scope", tracer = Some(tracer)).execute(()).unsafeRunSync()

    // Spans must close in the order they open. The old fold wrapped the accumulated prefix rather than
    // the stage, which nested every span inside the next and made stage durations cumulative.
    tracer.events.toList shouldBe List(
      "enter:contract-source-0",
      "exit:contract-source-0",
      "enter:contract-transform-1",
      "exit:contract-transform-1",
      "enter:contract-sink-2",
      "exit:contract-sink-2",
    )
  }

  test("a configured lineage emitter receives pipeline and stage events", How) {
    val emitter = new RecordingEmitter
    pipelineWith("lineage-wired", lineage = Some(emitter)).execute(()).unsafeRunSync()

    // Before the fix the emitter was stored, reported as "configured" in metadata, and never called.
    emitter.events.toList shouldBe List(
      "START:lineage-wired",
      "START:contract-source-0",
      "COMPLETE:contract-source-0",
      "START:contract-transform-1",
      "COMPLETE:contract-transform-1",
      "START:contract-sink-2",
      "COMPLETE:contract-sink-2",
      "COMPLETE:lineage-wired",
    )
  }

  test("every event of one execution shares a single run id", How) {
    val emitter = new RecordingEmitter
    pipelineWith("one-run-id", lineage = Some(emitter)).execute(()).unsafeRunSync()

    emitter.runIds.distinct.size shouldBe 1
  }

  test("a stage failure emits FAIL and re-raises the original error", How) {
    val emitter = new RecordingEmitter
    val boom    = new RuntimeException("source exploded")
    val failing = PipelineBuilder[BuilderState.Empty, IO, Unit, Unit]("lineage-fail")
      .withLineageEmitter(emitter)
      .addTypedSource[UserContract, UserContract, SchemaPolicy.Exact](
        source,
        (_: DataSource) => IO.raiseError[UserContract](boom),
      )
      .noTransform
      .addTypedSink[UserContract, SchemaPolicy.Exact](sink, writer)
      .build()

    val thrown = intercept[RuntimeException](failing.execute(()).unsafeRunSync())
    thrown.getMessage shouldBe "source exploded"

    emitter.events.toList should contain("FAIL:contract-source-0")
    emitter.events.toList should contain("FAIL:lineage-fail")
    emitter.events.toList should not contain "COMPLETE:lineage-fail"
  }

  test("an emitter that fails does not fail the pipeline it describes", How) {
    val emitter = new RecordingEmitter(failEveryEmit = true)
    pipelineWith("emitter-down", lineage = Some(emitter)).execute(()).unsafeRunSync() shouldBe (())

    emitter.events.toList should not be empty
  }

  test("an emitter that throws does not fail the pipeline it describes", How) {
    val throwing = new OpenLineageEmitter[IO] {
      def emitJobStart(ns: String, j: String, r: String): IO[Either[LineageError, Unit]] =
        IO.raiseError(new RuntimeException("emitter threw"))
      def emitJobComplete(ns: String, j: String, r: String): IO[Either[LineageError, Unit]] =
        IO.raiseError(new RuntimeException("emitter threw"))
      def emitJobFail(ns: String, j: String, r: String, e: String): IO[Either[LineageError, Unit]] =
        IO.raiseError(new RuntimeException("emitter threw"))
    }

    pipelineWith("emitter-throws", lineage = Some(throwing)).execute(()).unsafeRunSync() shouldBe (())
  }

  test("stages run in order and produce the sink result", How) {
    val seen = ListBuffer.empty[String]
    val built = PipelineBuilder[BuilderState.Empty, IO, Unit, Unit]("ordering")
      .addTypedSource[UserContract, UserContract, SchemaPolicy.Exact](
        source,
        (_: DataSource) => IO(seen += "read") *> IO.pure(UserContract(7L, "Bob")),
      )
      .addTransform((u: UserContract) => IO(seen += "transform") *> IO.pure(u.copy(id = u.id + 1)))
      .addTypedSink[UserContract, SchemaPolicy.Exact](
        sink,
        (u: UserContract, _: DataSink) => IO(seen += s"write:${u.id}").void,
      )
      .build()

    built.execute(()).unsafeRunSync()
    seen.toList shouldBe List("read", "transform", "write:8")
  }
}
