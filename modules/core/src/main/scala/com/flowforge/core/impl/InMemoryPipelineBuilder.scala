// scalafix:off DisableSyntax.isInstanceOf
package com.flowforge.core.impl

import cats.data.Kleisli
import com.flowforge.core.algebra.{ DataAlgebra, DataDecoder, DataEncoder, EffectSystem }
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
 * Type-safe in-memory pipeline builder with fs2.Stream integration.
 *
 * `In` and `Out` are the values the built pipeline reads and produces, not the record types inside them. A
 * source produces `Dataset[C]`, so the builder after one reports `Out = Dataset[C]`. The operations that work
 * a record at a time live in [[InMemoryTypedBuilder.DatasetOps]], which only applies when `Out` is a dataset,
 * so there is no longer one type parameter trying to mean both things at once.
 */
class InMemoryTypedBuilder[F[_], In, Out] private[impl] (
  private[impl] val name: String,
  private[impl] val dataAlgebra: InMemoryDataAlgebra[F],
  private[impl] val stages: List[InMemoryStage[F, _, _]],
  private[impl] val description: String = "",
  private[impl] val config: Option[PipelineConfig] = None,
)(implicit
  private[impl] val ef: EffectSystem[F]) {

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
  private[impl] def advance[In2, Out2](
    stage: InMemoryStage[F, _, _],
  ): InMemoryTypedBuilder[F, In2, Out2] =
    new InMemoryTypedBuilder[F, In2, Out2](name, dataAlgebra, stages :+ stage, description, config)(ef)

  /**
   * Add a streaming data source with fs2.Stream processing.
   *
   * A source reads from outside the pipeline, so it takes no input. The `Out =:= Unit` evidence restricts it
   * to a builder that has not produced a value yet. Without it, adding a source after a transform compiled
   * and then failed at run time, because the source's arrow would be handed the transform's output.
   */
  def addStreamingSource[C](
    source: DataSource,
    decoder: com.flowforge.core.algebra.DataDecoder[C],
  )(implicit atStart: Out =:= Unit,
  ): InMemoryTypedBuilder[F, Unit, DataAlgebra.Dataset[C]] = {
    val _ = atStart
    val stage = InMemoryStage.StreamingSource[F, C](
      name = s"stream-source-${stages.size}",
      description = s"Stream from ${source.format} with fs2",
      source = source,
      execute = Kleisli(_ => dataAlgebra.read(source)(decoder)),
    )
    advance[Unit, DataAlgebra.Dataset[C]](stage)
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
   * Build the final pipeline with memory-safe processing
   */
  def build(): Pipeline[F, In, Out] = {
    // The stages run back to back, each reading what the one before produced, so they compose directly.
    // This used to return an arrow that raised on run, because one Out was being asked to mean both the
    // record type and the value type, and the stage types therefore did not line up.
    val kleisliPipeline = StageComposer.compose[F, In, Out](
      pipelineName = name,
      stages = stages.map(st => ExecutableStage[F](st.name, st.asKleisli)),
    )(ef)

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

object InMemoryTypedBuilder {

  /**
   * The operations that work a record at a time.
   *
   * These only make sense once the pipeline is carrying a dataset, so they live here rather than on the
   * class: the receiver type is what supplies the record type `E`, which means a caller never has to name it
   * and a builder that is not carrying a dataset cannot reach them at all.
   */
  implicit class DatasetOps[F[_], In, E](
    private val builder: InMemoryTypedBuilder[F, In, DataAlgebra.Dataset[E]]) {

    /**
     * Add batch processing transformation (for compatibility)
     */
    def addBatchTransform[C](
      transform: DataAlgebra.Dataset[E] => DataAlgebra.Dataset[C],
    ): InMemoryTypedBuilder[F, In, DataAlgebra.Dataset[C]] = {
      val ef = builder.ef
      val stage = InMemoryStage.BatchTransform[F, DataAlgebra.Dataset[E], DataAlgebra.Dataset[C]](
        name = s"batch-transform-${builder.stages.size}",
        description = "Batch transformation",
        execute = Kleisli(data => ef.pure(transform(data))),
      )
      builder.advance[In, DataAlgebra.Dataset[C]](stage)
    }

    /**
     * Add data quality validation.
     *
     * The decoder is what lets the engine apply the contract to every record. See `DataAlgebra.validate`.
     */
    def addQualityCheck(
      contract: com.flowforge.core.types.PipelineTypes.DataContract[E],
    )(implicit decoder: DataDecoder[E],
    ): InMemoryTypedBuilder[F, In, DataAlgebra.Dataset[E]] = {
      val ef      = builder.ef
      val algebra = builder.dataAlgebra
      val stage = InMemoryStage.Quality[F, E](
        name = s"quality-${builder.stages.size}",
        description = "Data quality validation",
        contract = contract,
        execute =
          Kleisli(data => ef.flatMap(algebra.validate(data, contract))(result => ef.pure(result.data))),
      )
      builder.advance[In, DataAlgebra.Dataset[E]](stage)
    }

    /**
     * Add streaming sink with fs2.Stream writing
     */
    def addStreamingSink(
      sink: DataSink,
      encoder: DataEncoder[E],
      options: DataAlgebra.WriteOptions = DataAlgebra.WriteOptions.default,
    ): InMemoryTypedBuilder[F, In, Unit] = {
      val ef      = builder.ef
      val algebra = builder.dataAlgebra
      val stage = InMemoryStage.StreamingSink[F, E](
        name = s"stream-sink-${builder.stages.size}",
        description = s"Stream to ${sink.format} with fs2",
        sink = sink,
        execute = Kleisli(data => ef.flatMap(algebra.write(data, sink, options)(encoder))(_ => ef.pure(()))),
      )
      builder.advance[In, Unit](stage)
    }
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
      execute = Kleisli { (stream: fs2.Stream[F, Out]) =>
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
