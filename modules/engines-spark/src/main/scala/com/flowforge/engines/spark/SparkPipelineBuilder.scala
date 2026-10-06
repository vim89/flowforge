package com.flowforge.engines.spark

import cats.data.Kleisli
import cats.effect.Resource
import cats.implicits._
import com.flowforge.core.algebra.DataAlgebra.WriteOptions
import com.flowforge.core.algebra.{ DataAlgebra, DataDecoder, EffectSystem }
import com.flowforge.core.exec.{ StageChain, StageComposer, StageKind }
import com.flowforge.core.types._
import com.flowforge.framework.{ Pipeline, PipelineMetadata }
import org.apache.spark.sql.SparkSession

/**
 * Production-ready Spark-specific pipeline builder that integrates typed builders with Spark DataAlgebra
 * implementation.
 *
 * @deprecated
 *   Since 0.9.0. Use com.flowforge.core.PipelineBuilder with Spark engine instead. This class will be removed
 *   in 1.0.0. The canonical DSL is now engine-agnostic.
 *
 * MIGRATION: Replace SparkPipelineBuilder with PipelineBuilder[F] and use SparkDataAlgebra as the underlying
 * engine implementation.
 *
 * This builder provides:
 *   - Direct integration with SparkDataAlgebra for distributed processing
 *   - Type-safe pipeline construction with compile-time validation
 *   - Resource-safe SparkSession management
 *   - Production-ready error handling and recovery
 */
@deprecated("Use com.flowforge.core.PipelineBuilder instead. Will be removed in 1.0.0.", "0.9.0")
class SparkPipelineBuilder[F[_]: EffectSystem] private (
  private val sparkSession: SparkSession,
  private val dataAlgebra: DataAlgebra[F]) {

  private[spark] def session: org.apache.spark.sql.SparkSession = sparkSession

  /**
   * Create a type-safe pipeline builder with Spark backend
   */
  def typed(name: String): SparkTypedBuilder[F, Unit, Unit] =
    new SparkTypedBuilder[F, Unit, Unit](
      name = name,
      sparkSession = sparkSession,
      dataAlgebra = dataAlgebra,
      stages = StageChain.empty[F, Unit],
    )

  /**
   * Create a runtime pipeline builder with Spark backend
   */
  def runtime(name: String): SparkRuntimeBuilder[F, Unit, Unit] =
    new SparkRuntimeBuilder[F, Unit, Unit](
      name = name,
      sparkSession = sparkSession,
      dataAlgebra = dataAlgebra,
      stages = StageChain.empty[F, Unit],
    )
}

/**
 * Type-safe Spark pipeline builder with compile-time guarantees
 */
class SparkTypedBuilder[F[_]: EffectSystem, In, Out] private[spark] (
  private[spark] val name: String,
  private[spark] val sparkSession: SparkSession,
  private[spark] val dataAlgebra: DataAlgebra[F],
  private[spark] val stages: StageChain[F, In, Out],
  private[spark] val description: String = "",
  private[spark] val config: Option[PipelineConfig] = None) {

  def withDescription(desc: String): SparkTypedBuilder[F, In, Out] =
    new SparkTypedBuilder(name, sparkSession, dataAlgebra, stages, desc, config)

  def withConfig(cfg: PipelineConfig): SparkTypedBuilder[F, In, Out] =
    new SparkTypedBuilder(name, sparkSession, dataAlgebra, stages, description, Some(cfg))

  /** Append a stage. Its arrow has to read what the builder currently produces. */
  private[spark] def advance[Out2](
    stageName: String,
    kind: StageKind,
    arrow: Kleisli[F, Out, Out2],
  ): SparkTypedBuilder[F, In, Out2] =
    new SparkTypedBuilder[F, In, Out2](
      name,
      sparkSession,
      dataAlgebra,
      stages.andThen(stageName, kind, arrow),
      description,
      config,
    )

  /**
   * Add a Spark-optimized data source.
   *
   * A source takes no input, so the `Out =:= Unit` evidence restricts it to a builder that has not produced a
   * value yet. At `typed(name)` the builder's `Out` is already `Unit`, so no caller names the evidence.
   */
  def addSource[C](
    source: DataSource,
    decoder: com.flowforge.core.algebra.DataDecoder[C],
  )(implicit atStart: Out =:= Unit,
  ): SparkTypedBuilder[F, In, DataAlgebra.Dataset[C]] = {
    val stage = SparkStage.Source[F, C](
      name = s"spark-source-${stages.size}",
      description = s"Read from ${source.format} using Spark",
      source = source,
      execute = Kleisli(_ => dataAlgebra.read(source)(decoder)),
    )
    advance[DataAlgebra.Dataset[C]](stage.name, StageKind.Source, stage.execute.local[Out](atStart))
  }

  /**
   * Add a transformation that leverages Spark distributed processing
   */
  def addTransform[C](transform: Out => F[C]): SparkTypedBuilder[F, In, C] = {
    val stage = SparkStage.Transform[F, Out, C](
      name = s"spark-transform-${stages.size}",
      description = "Spark distributed transformation",
      execute = Kleisli(transform),
    )
    advance[C](stage.name, StageKind.Transform, stage.execute)
  }

  /**
   * Build the final pipeline with Spark optimizations
   */
  def build(): Pipeline[F, In, Out] = {
    // Composition belongs to StageComposer, which is the one place that answers how a chain of stages runs.
    // This builder used to do its own fold and its own two casts, so the erasure existed in two places and
    // only one of them was tested.
    val typedPipeline = StageComposer.compose[F, In, Out](
      pipelineName = name,
      stages = stages,
    )

    val metadata = PipelineMetadata(
      name = name,
      stages = stages.names,
      transformations = stages.count(StageKind.Transform),
      qualityChecks = stages.count(StageKind.Quality),
      tags = Map(
        "engine"        -> "spark",
        "type_safe"     -> "true",
        "spark_session" -> sparkSession.sparkContext.applicationId,
      ),
    )

    Pipeline(typedPipeline, metadata)
  }
}

/**
 * Stages that only make sense once the builder is carrying a dataset.
 *
 * A quality check validates records and a sink writes records, so both need the pipeline to be holding a
 * `Dataset`. Keeping them here rather than on the class is what lets them say so: the implicit class only
 * resolves for a builder whose `Out` is a dataset. On the class they were declared over the builder's `Out`
 * while their stage arrows were over `Dataset[Out]`, and the composer's cast hid the mismatch.
 */
object SparkTypedBuilder {

  implicit class DatasetOps[F[_]: EffectSystem, In, E](
    builder: SparkTypedBuilder[F, In, DataAlgebra.Dataset[E]]) {

    /**
     * Add a data quality check using Spark's distributed validation.
     *
     * The decoder is what lets the engine apply the contract to every record. See `DataAlgebra.validate`.
     */
    def addQualityCheck(
      contract: com.flowforge.core.types.PipelineTypes.DataContract[E],
    )(implicit decoder: DataDecoder[E],
    ): SparkTypedBuilder[F, In, DataAlgebra.Dataset[E]] = {
      val _ = decoder
      val stage = SparkStage.Quality[F, E](
        name = s"spark-quality-${builder.stages.size}",
        description = "Spark distributed quality validation",
        contract = contract,
        execute = Kleisli(data => builder.dataAlgebra.validate(data, contract).map(_.data)),
      )
      builder.advance[DataAlgebra.Dataset[E]](stage.name, StageKind.Quality, stage.execute)
    }

    /**
     * Add a sink that uses Spark's distributed writing capabilities
     */
    def addSink(
      sink: DataSink,
      encoder: com.flowforge.core.algebra.DataEncoder[E],
      options: WriteOptions = WriteOptions.default,
    ): SparkTypedBuilder[F, In, Unit] = {
      val stage = SparkStage.Sink[F, E](
        name = s"spark-sink-${builder.stages.size}",
        description = s"Write to ${sink.format} using Spark",
        sink = sink,
        execute = Kleisli(data => builder.dataAlgebra.write(data, sink, options)(encoder).void),
      )
      builder.advance[Unit](stage.name, StageKind.Sink, stage.execute)
    }
  }
}

/**
 * Runtime pipeline builder for dynamic pipeline construction.
 *
 * "Runtime" names where the stage operations come from, not whether the stage types line up. The chain still
 * carries the type between each pair of stages, so `buildRuntime` returns the pipeline's own `In` and `Out`
 * rather than the `Any` the old cast produced.
 */
class SparkRuntimeBuilder[F[_]: EffectSystem, In, Out] private[spark] (
  private val name: String,
  private val sparkSession: SparkSession,
  private val dataAlgebra: DataAlgebra[F],
  private val stages: StageChain[F, In, Out]) {

  def addDynamicStage[B](
    stageName: String,
    operation: Out => F[B],
  ): SparkRuntimeBuilder[F, In, B] = {
    val stage = SparkStage.Dynamic[F, Out, B](
      name = stageName,
      description = "Dynamic Spark operation",
      execute = Kleisli(operation),
    )
    new SparkRuntimeBuilder[F, In, B](
      name,
      sparkSession,
      dataAlgebra,
      stages.andThen(stageName, StageKind.Transform, stage.execute),
    )
  }

  def buildRuntime(): Pipeline[F, In, Out] = {
    val kleisliPipeline = StageComposer.compose[F, In, Out](
      pipelineName = name,
      stages = stages,
    )

    val metadata = PipelineMetadata(
      name = name,
      stages = stages.names,
      transformations = stages.count(StageKind.Transform),
      qualityChecks = stages.count(StageKind.Quality),
      tags = Map(
        "engine"    -> "spark",
        "type_safe" -> "false",
        "runtime"   -> "true",
      ),
    )

    Pipeline(kleisliPipeline, metadata)
  }
}

/**
 * Spark-specific pipeline stages
 */
sealed trait SparkStage[F[_], A, B] {
  def name: String
  def description: String
  def asKleisli: Kleisli[F, A, B]
}

object SparkStage {

  case class Source[F[_], A](
    name: String,
    description: String,
    source: DataSource,
    execute: Kleisli[F, Unit, DataAlgebra.Dataset[A]])
      extends SparkStage[F, Unit, DataAlgebra.Dataset[A]] {
    def asKleisli: Kleisli[F, Unit, DataAlgebra.Dataset[A]] = execute
  }

  case class Transform[F[_], A, B](
    name: String,
    description: String,
    execute: Kleisli[F, A, B])
      extends SparkStage[F, A, B] {
    def asKleisli: Kleisli[F, A, B] = execute
  }

  case class Quality[F[_], A](
    name: String,
    description: String,
    contract: com.flowforge.core.types.PipelineTypes.DataContract[A],
    execute: Kleisli[F, DataAlgebra.Dataset[A], DataAlgebra.Dataset[A]])
      extends SparkStage[F, DataAlgebra.Dataset[A], DataAlgebra.Dataset[A]] {
    def asKleisli: Kleisli[F, DataAlgebra.Dataset[A], DataAlgebra.Dataset[A]] = execute
  }

  case class Sink[F[_], A](
    name: String,
    description: String,
    sink: DataSink,
    execute: Kleisli[F, DataAlgebra.Dataset[A], Unit])
      extends SparkStage[F, DataAlgebra.Dataset[A], Unit] {
    def asKleisli: Kleisli[F, DataAlgebra.Dataset[A], Unit] = execute
  }

  case class Dynamic[F[_], A, B](
    name: String,
    description: String,
    execute: Kleisli[F, A, B])
      extends SparkStage[F, A, B] {
    def asKleisli: Kleisli[F, A, B] = execute
  }
}

@deprecated("Use com.flowforge.core.PipelineBuilder instead. Will be removed in 1.0.0.", "0.9.0")
object SparkPipelineBuilder {

  /**
   * Create a new Spark pipeline builder with resource-safe session management
   *
   * @deprecated
   *   Use PipelineBuilder with SparkDataAlgebra instead
   */
  @deprecated("Use com.flowforge.core.PipelineBuilder with SparkDataAlgebra instead.", "0.9.0")
  def create[F[_]: EffectSystem](
    sparkSession: SparkSession,
  ): SparkPipelineBuilder[F] = {
    val dataAlgebra = SparkDataAlgebra.createSparkDataAlgebra[F](sparkSession).algebra
    new SparkPipelineBuilder[F](sparkSession, dataAlgebra)
  }

  /**
   * Create with managed Spark session (automatically closed)
   */
  def withManagedSession[F[_]: EffectSystem](
    appName: String,
    config: Map[String, String] = Map.empty,
  ): Resource[F, SparkPipelineBuilder[F]] = {
    val F = EffectSystem[F]

    Resource.make {
      F.blocking {
        val builder = SparkSession
          .builder()
          .appName(appName)
        // Do NOT hardcode master. Respect spark-submit/cluster configs.
        // Optional override from provided config or SPARK_MASTER env (dev convenience only)
        config.get("spark.master").orElse(sys.env.get("SPARK_MASTER")).foreach(builder.master)

        config.foreach {
          case (key, value) =>
            builder.config(key, value)
        }

        val session = builder.getOrCreate()
        SparkPipelineBuilder.create[F](session)
      }
    } { builder =>
      F.blocking {
        // Properly close Spark session
        builder.session.stop()
      }
    }
  }
}
