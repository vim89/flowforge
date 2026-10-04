// scalafix:off DisableSyntax.isInstanceOf
package com.flowforge.core.impl

import cats.data.Kleisli
import com.flowforge.core.algebra.{ DataAlgebra, DataEncoder, EffectSystem }
import com.flowforge.core.exec.{ ExecutableStage, StageComposer }
import com.flowforge.core.types._
import com.flowforge.framework.{ Pipeline, PipelineMetadata }

/**
 * Production-ready In-Memory pipeline builder that integrates typed builders with fs2.Stream-based
 * InMemoryDataAlgebra.
 *
 * This builder provides:
 *   - Memory-safe processing with fs2.Stream integration
 *   - Type-safe pipeline construction for testing and development
 *   - Lightweight alternative to distributed engines
 *   - Full compatibility with production DataAlgebra interface
 */
class InMemoryPipelineBuilder[F[_]: EffectSystem] private (
  private val dataAlgebra: InMemoryDataAlgebra[F]) {

  /**
   * Create a type-safe pipeline builder with in-memory backend
   */
  def typed(name: String): InMemoryTypedBuilder[F, Unit, Unit] =
    new InMemoryTypedBuilder[F, Unit, Unit](
      name = name,
      dataAlgebra = dataAlgebra,
      stages = List.empty,
    )(EffectSystem[F])

  /**
   * Create a streaming pipeline builder for large datasets.
   *
   * `A` is the element type the pipeline is fed. The builder carries it so that each operation added has to
   * accept what the one before it produced.
   */
  def streaming[A](name: String): InMemoryStreamBuilder[F, A, A] =
    new InMemoryStreamBuilder[F, A, A](
      name = name,
      dataAlgebra = dataAlgebra,
      stages = List.empty,
    )(EffectSystem[F])
}

/**
 * Type-safe in-memory pipeline builder with fs2.Stream integration
 */
class InMemoryTypedBuilder[F[_], In, Out] private[impl] (
  private val name: String,
  private val dataAlgebra: InMemoryDataAlgebra[F],
  private val stages: List[InMemoryStage[F, _, _]],
  private val description: String = "",
  private val config: Option[PipelineConfig] = None,
)(implicit
  ef: EffectSystem[F]) {

  def withDescription(desc: String): InMemoryTypedBuilder[F, In, Out] =
    new InMemoryTypedBuilder(name, dataAlgebra, stages, desc, config)(ef)

  def withConfig(cfg: PipelineConfig): InMemoryTypedBuilder[F, In, Out] =
    new InMemoryTypedBuilder(name, dataAlgebra, stages, description, Some(cfg))(ef)

  /**
   * Append a stage and retype the builder.
   *
   * Every stage method used to call the constructor with three of the five fields, so `withDescription` and
   * `withConfig` were discarded by the next stage added. Carrying the fields in one place removes that.
   */
  private def advance[In2, Out2](stage: InMemoryStage[F, _, _]): InMemoryTypedBuilder[F, In2, Out2] =
    new InMemoryTypedBuilder[F, In2, Out2](name, dataAlgebra, stages :+ stage, description, config)(ef)

  /**
   * Add a streaming data source with fs2.Stream processing
   */
  def addStreamingSource[C](
    source: DataSource,
    decoder: com.flowforge.core.algebra.DataDecoder[C],
  ): InMemoryTypedBuilder[F, Unit, C] = {
    val stage = InMemoryStage.StreamingSource[F, C](
      name = s"stream-source-${stages.size}",
      description = s"Stream from ${source.format} with fs2",
      source = source,
      execute = Kleisli(_ => dataAlgebra.read(source)(decoder)),
    )
    advance[Unit, C](stage)
  }

  /**
   * Add a memory-safe transformation using fs2.Stream
   */
  def addStreamTransform[C](transform: Out => F[C]): InMemoryTypedBuilder[F, In, C] = {
    val stage = InMemoryStage.Transform[F, Out, C](
      name = s"stream-transform-${stages.size}",
      description = "Memory-safe transformation with fs2",
      execute = Kleisli(transform),
    )
    advance[In, C](stage)
  }

  /**
   * Add batch processing transformation (for compatibility)
   */
  def addBatchTransform[C](
    transform: DataAlgebra.Dataset[Out] => DataAlgebra.Dataset[C],
  ): InMemoryTypedBuilder[F, In, C] = {
    val stage = InMemoryStage.BatchTransform[F, DataAlgebra.Dataset[Out], DataAlgebra.Dataset[C]](
      name = s"batch-transform-${stages.size}",
      description = "Batch transformation",
      execute = Kleisli(data => ef.pure(transform(data))),
    )
    advance[In, C](stage)
  }

  /**
   * Add data quality validation
   */
  def addQualityCheck(
    contract: com.flowforge.core.types.PipelineTypes.DataContract[Out],
  ): InMemoryTypedBuilder[F, In, Out] = {
    val stage = InMemoryStage.Quality[F, Out](
      name = s"quality-${stages.size}",
      description = "Data quality validation",
      contract = contract,
      execute =
        Kleisli(data => ef.flatMap(dataAlgebra.validate(data, contract))(result => ef.pure(result.data))),
    )
    advance[In, Out](stage)
  }

  /**
   * Add streaming sink with fs2.Stream writing
   */
  def addStreamingSink(
    sink: DataSink,
    encoder: DataEncoder[Out],
    options: DataAlgebra.WriteOptions = DataAlgebra.WriteOptions.default,
  ): InMemoryTypedBuilder[F, In, Unit] = {
    val stage = InMemoryStage.StreamingSink[F, Out](
      name = s"stream-sink-${stages.size}",
      description = s"Stream to ${sink.format} with fs2",
      sink = sink,
      execute = Kleisli(data => ef.flatMap(dataAlgebra.write(data, sink, options)(encoder))(_ => ef.pure(()))),
    )
    advance[In, Unit](stage)
  }

  /**
   * Build the final pipeline with memory-safe processing
   */
  def build(): Pipeline[F, In, Out] = {
    // This builder cannot run its stages yet, and it must not pretend to.
    //
    // It used to return an identity arrow that discarded every stage and handed the input back cast to
    // Out. Because the cast is erased it did not even fail: a pipeline that should have produced 42
    // returned (), and the metadata below still listed the stages it had dropped.
    //
    // Composing the stages is not a small fix, because the stage types do not line up with the type
    // parameters. addStreamingSource[C] reports Out = C while its stage produces Dataset[C], and
    // addStreamTransform (Out => F[C]) and addBatchTransform (Dataset[Out] => Dataset[C]) disagree about
    // whether Out is the element type or the value type. One Out cannot satisfy both, so the signatures
    // have to change before the stages can be run. That is an API change, kept out of this fix.
    //
    // Until then, failing on run is the honest behaviour. Returning wrong data silently is the worse of
    // the two, and this builder has no callers to break.
    val kleisliPipeline = Kleisli[F, In, Out] { _ =>
      ef.raiseError[Out](
        new UnsupportedOperationException(
          s"InMemoryTypedBuilder '$name' cannot execute its ${stages.size} stage(s): stage composition is " +
            "not implemented. Use PipelineBuilder for a runnable typed pipeline.",
        ),
      )
    }

    val metadata = PipelineMetadata(
      name = name,
      stages = stages.map(_.name),
      transformations = stages.count(_.isInstanceOf[InMemoryStage.Transform[F, _, _]]),
      qualityChecks = stages.count(_.isInstanceOf[InMemoryStage.Quality[F, _]]),
      tags = Map(
        "engine"      -> "inmemory",
        "streaming"   -> "fs2",
        "memory_safe" -> "true",
        "type_safe"   -> "true",
        "description" -> description,
      ),
    )

    Pipeline(kleisliPipeline, metadata)
  }
}

/**
 * Streaming pipeline builder for large dataset processing.
 *
 * `In` is the element type the pipeline is fed, `Out` the element type it currently produces. Both are needed
 * because the stages are run back to back: without `Out` the next operation could declare any input element
 * type it liked, the mismatch would survive compilation, and the stream would fail at the first element with
 * a cast error inside the fs2 machinery rather than at the call that caused it.
 */
class InMemoryStreamBuilder[F[_], In, Out] private[impl] (
  private val name: String,
  private val dataAlgebra: InMemoryDataAlgebra[F],
  private val stages: List[InMemoryStage[F, _, _]],
)(implicit
  ef: EffectSystem[F]) {

  /** Append an operation. It has to read the element type the pipeline currently produces. */
  def addStreamingOperation[B](
    stageName: String,
    operation: fs2.Stream[F, Out] => fs2.Stream[F, B],
  ): InMemoryStreamBuilder[F, In, B] = {
    val stage = InMemoryStage.Streaming[F, Out, B](
      name = stageName,
      description = "fs2.Stream operation",
      execute = Kleisli { stream: fs2.Stream[F, Out] =>
        ef.pure(operation(stream))
      },
    )
    new InMemoryStreamBuilder[F, In, B](name, dataAlgebra, stages :+ stage)(ef)
  }

  def buildStreaming(): Pipeline[F, fs2.Stream[F, In], fs2.Stream[F, Out]] = {
    // Each stage maps Stream[F, A] to Stream[F, B] and feeds the next, so these compose directly.
    // This used to return the input stream unchanged, ignoring every registered operation.
    val kleisliPipeline = StageComposer.compose[F, fs2.Stream[F, In], fs2.Stream[F, Out]](
      pipelineName = name,
      stages = stages.map(st => ExecutableStage[F](st.name, st.asKleisli)),
    )(ef)

    val metadata = PipelineMetadata(
      name = name,
      stages = stages.map(_.name),
      transformations = stages.size,
      qualityChecks = 0,
      tags = Map(
        "engine"         -> "inmemory",
        "streaming"      -> "fs2",
        "pure_streaming" -> "true",
      ),
    )

    Pipeline(kleisliPipeline, metadata)
  }
}

/**
 * In-memory specific pipeline stages
 */
sealed trait InMemoryStage[F[_], A, B] {
  def name: String
  def description: String
  def asKleisli: Kleisli[F, A, B]
}

object InMemoryStage {

  case class StreamingSource[F[_], A](
    name: String,
    description: String,
    source: DataSource,
    execute: Kleisli[F, Unit, DataAlgebra.Dataset[A]])
      extends InMemoryStage[F, Unit, DataAlgebra.Dataset[A]] {
    def asKleisli: Kleisli[F, Unit, DataAlgebra.Dataset[A]] = execute
  }

  case class Transform[F[_], A, B](
    name: String,
    description: String,
    execute: Kleisli[F, A, B])
      extends InMemoryStage[F, A, B] {
    def asKleisli: Kleisli[F, A, B] = execute
  }

  case class BatchTransform[F[_], A, B](
    name: String,
    description: String,
    execute: Kleisli[F, A, B])
      extends InMemoryStage[F, A, B] {
    def asKleisli: Kleisli[F, A, B] = execute
  }

  case class Quality[F[_], A](
    name: String,
    description: String,
    contract: com.flowforge.core.types.PipelineTypes.DataContract[A],
    execute: Kleisli[F, DataAlgebra.Dataset[A], DataAlgebra.Dataset[A]])
      extends InMemoryStage[F, DataAlgebra.Dataset[A], DataAlgebra.Dataset[A]] {
    def asKleisli: Kleisli[F, DataAlgebra.Dataset[A], DataAlgebra.Dataset[A]] = execute
  }

  case class StreamingSink[F[_], A](
    name: String,
    description: String,
    sink: DataSink,
    execute: Kleisli[F, DataAlgebra.Dataset[A], Unit])
      extends InMemoryStage[F, DataAlgebra.Dataset[A], Unit] {
    def asKleisli: Kleisli[F, DataAlgebra.Dataset[A], Unit] = execute
  }

  case class Streaming[F[_], A, B](
    name: String,
    description: String,
    execute: Kleisli[F, fs2.Stream[F, A], fs2.Stream[F, B]])
      extends InMemoryStage[F, fs2.Stream[F, A], fs2.Stream[F, B]] {
    def asKleisli: Kleisli[F, fs2.Stream[F, A], fs2.Stream[F, B]] = execute
  }
}

object InMemoryPipelineBuilder {

  /**
   * Create a new in-memory pipeline builder
   */
  def create[F[_]: EffectSystem]: InMemoryPipelineBuilder[F] = {
    val dataAlgebra = new InMemoryDataAlgebra[F]()
    new InMemoryPipelineBuilder[F](dataAlgebra)
  }

  /**
   * Create with custom data algebra instance
   */
  def withDataAlgebra[F[_]: EffectSystem](
    dataAlgebra: InMemoryDataAlgebra[F],
  ): InMemoryPipelineBuilder[F] =
    new InMemoryPipelineBuilder[F](dataAlgebra)
}
