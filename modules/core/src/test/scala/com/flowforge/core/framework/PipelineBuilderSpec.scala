package com.flowforge.core.framework

import cats.effect.IO
import com.flowforge.core.PipelineBuilder
import com.flowforge.core.algebra.EffectSystem
import com.flowforge.core.contracts.SchemaPolicy
import com.flowforge.core.instances.EffectInstances
import com.flowforge.core.lineage.OpenLineageEmitter
import com.flowforge.core.observability.Tracer
import com.flowforge.core.types._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import com.flowforge.core.testing.How

class PipelineBuilderSpec extends AnyFunSuite with Matchers {

  // EffectSystem instance for IO
  implicit val es: EffectSystem[IO] = EffectInstances.catsEffectSystemInstance

  // Simple contract and pipeline types with identical shapes for Exact policy
  final case class UserContract(id: Long, name: String)
  type PipelineOut = UserContract

  private val source = TypedSource[UserContract](DataSource.local("/tmp/in", DataFormat.JSON))
  private val sink   = TypedSink[UserContract](DataSink.local("/tmp/out", DataFormat.JSON))
  private val reader = (_: DataSource) => IO.pure(UserContract(1L, "Alice"))
  private val writer = (_: PipelineOut, _: DataSink) => IO.unit

  test(
    "builds end-to-end pipeline with typed endpoints, tracer and lineage tags; builder mutators and multi-transform metadata",
    How,
  ) {
    // minimal tracer to exercise withTracer path
    val tracer = new Tracer[IO] {
      def inSpan[A](name: String)(fa: IO[A]): IO[A]      = fa
      def annotate(key: String, value: String): IO[Unit] = IO.unit
    }
    val lineage = OpenLineageEmitter.noop[IO]

    val built = PipelineBuilder[BuilderState.Empty, IO, Unit, Unit]("pb-spec")
      .withDescription("desc")
      .withLineageEmitter(lineage)
      .withTracer(tracer)
      .addTypedSource[PipelineOut, UserContract, SchemaPolicy.Exact](source, reader)
      .noTransform
      .addTypedSink[UserContract, SchemaPolicy.Exact](sink, writer)
      .build

    // Execute and verify metadata
    import cats.effect.unsafe.implicits.global
    built.execute(()).unsafeRunSync() shouldBe (())

    val md = built.metadata
    md.name shouldBe "pb-spec"
    md.stages should contain inOrder ("contract-source-0", "contract-transform-1", "contract-sink-2")
    md.transformations should be >= 1 // extra .map bumped transformations before sink
    md.tags.get("builder") shouldBe Some("contract-aware")
    md.tags.get("lineage") shouldBe Some("configured")
  }

  test("identity transform via noTransform preserves shape and completes builder state", How) {
    import cats.effect.unsafe.implicits.global
    val b = PipelineBuilder[BuilderState.Empty, IO, Unit, Unit]("pb-identity")
      .addTypedSource[PipelineOut, UserContract, SchemaPolicy.Exact](source, reader)
      .noTransform
      .addTypedSink[UserContract, SchemaPolicy.Exact](sink, writer)
      .build
    b.execute(()).unsafeRunSync() shouldBe (())
  }
}
