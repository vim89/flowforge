// A test is an edge, so it runs the effect here rather than handing it to an IOApp.
// scalafix:off DisableSyntax.noUnsafeRunSync
package com.flowforge.core.impl

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.flowforge.core.algebra.EffectSystem
import com.flowforge.core.instances.{ DefaultCodecs, EffectInstances }
import com.flowforge.core.testing.How
import com.flowforge.core.types.{ DataFormat, LocalDataSink, LocalDataSource }
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.nio.file.Files

class InMemoryPipelineBuilderSpec extends AnyFunSuite with Matchers {

  implicit val es: EffectSystem[IO] = EffectInstances.catsEffectSystemInstance

  test("adding a stage keeps the description set before it", How) {
    val built = InMemoryPipelineBuilder
      .create[IO]
      .typed("described")
      .withDescription("keeps me")
      .addStreamTransform[Int](_ => IO.pure(1))
      .build()

    // Every stage method used to rebuild the builder with three of the five fields, dropping this.
    built.metadata.tags.get("description") shouldBe Some("keeps me")
  }

  test("a typed build runs the stages it was given", How) {
    val built = InMemoryPipelineBuilder
      .create[IO]
      .typed("adder")
      .addStreamTransform[Int](_ => IO.pure(1))
      .addStreamTransform[Int](n => IO.pure(n + 41))
      .build()

    // This used to raise on run, because one Out meant both the record type and the value type and the
    // stage types could not be lined up.
    built.execute(()).unsafeRunSync() shouldBe 42
  }

  test("a typed build reads a source, transforms it and writes a sink", How) {
    val in  = Files.createTempFile("ff-in", ".jsonl")
    val out = Files.createTempFile("ff-out", ".jsonl")
    val _   = Files.write(in, "a\nb\nc\n".getBytes("UTF-8"))

    val built = InMemoryPipelineBuilder
      .create[IO]
      .typed("round-trip")
      .addStreamingSource(
        LocalDataSource(in.toString, DataFormat.JSONL),
        DefaultCodecs.stringDecoder,
      )
      // The record type comes from the receiver, so this call never names it.
      .addBatchTransform(ds => SimpleDataset(ds.data.map(_.toUpperCase), ds.schema, ds.metadata))
      .addStreamingSink(
        LocalDataSink(out.toString, DataFormat.JSONL),
        DefaultCodecs.stringEncoder,
      )
      .build()

    built.execute(()).unsafeRunSync() shouldBe (())
    val written = Files.readAllLines(out).toArray.toList.map(_.toString.trim).filter(_.nonEmpty)
    written shouldBe List("A", "B", "C")
  }

  test("a record-at-a-time operation is rejected when the pipeline is not carrying a dataset", How) {
    // Out used to mean the record type for these operations and the value type for addStreamTransform, so
    // this compiled against an Int and then matched a stage expecting a Dataset[Int].
    assertDoesNotCompile(
      """InMemoryPipelineBuilder
           .create[IO]
           .typed("mismatched")
           .addStreamTransform[Int](_ => IO.pure(1))
           .addBatchTransform[Int](ds => ds)""",
    )
  }

  test("a source is rejected when the pipeline is already carrying a value", How) {
    // A source takes no input, so appending one after a transform used to compile and then hand the
    // transform's Int to an arrow expecting Unit.
    assertDoesNotCompile(
      """InMemoryPipelineBuilder
           .create[IO]
           .typed("source-after-transform")
           .addStreamTransform[Int](_ => IO.pure(1))
           .addStreamingSource(
             LocalDataSource("in.jsonl", DataFormat.JSONL),
             DefaultCodecs.stringDecoder,
           )""",
    )
  }

  test("a streaming build applies the operations it was given", How) {
    val built = InMemoryPipelineBuilder
      .create[IO]
      .streaming[Int]("doubler")
      .addStreamingOperation[Int]("double", _.map(_ * 2))
      .addStreamingOperation[Int]("increment", _.map(_ + 1))
      .buildStreaming()

    // It used to return the input stream unchanged, ignoring every registered operation.
    val out = built
      .execute(fs2.Stream[IO, Int](1, 2, 3))
      .flatMap(_.compile.toList)
      .unsafeRunSync()

    out shouldBe List(3, 5, 7)
  }

  test("a streaming operation that does not read the previous element type is rejected", How) {
    // The builder used to carry no element type, so this compiled and then failed at the first element
    // with a cast error from inside fs2 rather than at the call that caused it.
    assertDoesNotCompile(
      """InMemoryPipelineBuilder
           .create[IO]
           .streaming[Int]("mismatched")
           .addStreamingOperation[String]("render", _.map(_.toString))
           .addStreamingOperation[Int]("length", (s: fs2.Stream[IO, Int]) => s)""",
    )
  }
}
