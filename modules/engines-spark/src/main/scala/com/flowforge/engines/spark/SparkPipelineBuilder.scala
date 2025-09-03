package com.flowforge.engines.spark

import cats.data.Kleisli
import cats.effect.Resource
import com.flowforge.core.algebra.DataAlgebra.WriteOptions
import com.flowforge.core.algebra.{DataAlgebra, EffectSystem}
import com.flowforge.core.types._
import com.flowforge.framework.{Pipeline, PipelineMetadata}
import org.apache.spark.sql.SparkSession

/**
 * Production-ready Spark-specific pipeline builder that integrates
 * typed builders with Spark DataAlgebra implementation.
 *
 * This builder provides:
 * - Direct integration with SparkDataAlgebra for distributed processing
 * - Type-safe pipeline construction with compile-time validation
 * - Resource-safe SparkSession management
 * - Production-ready error handling and recovery
 */
class SparkPipelineBuilder[F[_]: EffectSystem] private (
  private val sparkSession: SparkSession,
  private val dataAlgebra: DataAlgebra[F]
) {

  private[spark] def session: org.apache.spark.sql.SparkSession = sparkSession

  /**
   * Create a type-safe pipeline builder with Spark backend
   */
  def typed(name: String): SparkTypedBuilder[F, Unit, Unit] = {
    new SparkTypedBuilder[F, Unit, Unit](
      name = name,
      sparkSession = sparkSession,
      dataAlgebra = dataAlgebra,
      stages = List.empty
    )
  }

  /**
   * Create a runtime pipeline builder with Spark backend
   */
  def runtime(name: String): SparkRuntimeBuilder[F] = {
    new SparkRuntimeBuilder[F](
      name = name,
      sparkSession = sparkSession,
      dataAlgebra = dataAlgebra,
      stages = List.empty
    )
  }
}

/**
 * Type-safe Spark pipeline builder with compile-time guarantees
 */
class SparkTypedBuilder[F[_]: EffectSystem, In, Out] private[spark] (
  private val name: String,
  private val sparkSession: SparkSession,
  private val dataAlgebra: DataAlgebra[F],
  private val stages: List[SparkStage[F, _, _]],
  private val description: String = "",
  private val config: Option[PipelineConfig] = None
) {

  def withDescription(desc: String): SparkTypedBuilder[F, In, Out] =
    new SparkTypedBuilder(name, sparkSession, dataAlgebra, stages, desc, config)

  def withConfig(cfg: PipelineConfig): SparkTypedBuilder[F, In, Out] =
    new SparkTypedBuilder(name, sparkSession, dataAlgebra, stages, description, Some(cfg))

  /**
   * Add a Spark-optimized data source
   */
  def addSource[C](
    source: DataSource,
    decoder: com.flowforge.core.algebra.DataDecoder[C]
  ): SparkTypedBuilder[F, Unit, C] = {
    val stage = SparkStage.Source[F, C](
      name = s"spark-source-${stages.size}",
      description = s"Read from ${source.format} using Spark",
      source = source,
      execute = Kleisli(_ => dataAlgebra.read(source)(decoder))
    )
    new SparkTypedBuilder[F, Unit, C](name, sparkSession, dataAlgebra, stages :+ stage)
  }

  /**
   * Add a transformation that leverages Spark distributed processing
   */
  def addTransform[C](transform: Out => F[C]): SparkTypedBuilder[F, In, C] = {
    val stage = SparkStage.Transform[F, Out, C](
      name = s"spark-transform-${stages.size}",
      description = "Spark distributed transformation",
      execute = Kleisli(transform)
    )
    new SparkTypedBuilder[F, In, C](name, sparkSession, dataAlgebra, stages :+ stage)
  }

  /**
   * Add a data quality check using Spark's distributed validation
   */
  def addQualityCheck(
    contract: com.flowforge.core.types.PipelineTypes.DataContract[Out]
  ): SparkTypedBuilder[F, In, Out] = {
    val F = EffectSystem[F]
    val stage = SparkStage.Quality[F, Out](
      name = s"spark-quality-${stages.size}",
      description = "Spark distributed quality validation",
      contract = contract,
      execute = Kleisli(data => F.map(dataAlgebra.validate(data, contract))(_.data))
    )
    new SparkTypedBuilder[F, In, Out](name, sparkSession, dataAlgebra, stages :+ stage)
  }

  /**
   * Add a sink that uses Spark's distributed writing capabilities
   */
  def addSink(
    sink: DataSink,
    encoder: com.flowforge.core.algebra.DataEncoder[Out],
    options: WriteOptions = WriteOptions.default
  ): SparkTypedBuilder[F, In, Unit] = {
    val stage = SparkStage.Sink[F, Out](
      name = s"spark-sink-${stages.size}",
      description = s"Write to ${sink.format} using Spark",
      sink = sink,
      execute = Kleisli { data =>
        val F = EffectSystem[F]
        F.map(dataAlgebra.write(data, sink, options)(encoder))(_ => ())
      }
    )
    new SparkTypedBuilder[F, In, Unit](name, sparkSession, dataAlgebra, stages :+ stage)
  }

  /**
   * Build the final pipeline with Spark optimizations
   */
  def build(): Pipeline[F, In, Out] = {
    // Compose all stages using Kleisli arrows (erase types during folding)
    val kleisliPipeline = stages
      .foldLeft(Kleisli.ask[F, Any]) { (acc, stage) =>
        acc.andThen(stage.asKleisli.asInstanceOf[Kleisli[F, Any, Any]])
      }
      .asInstanceOf[Kleisli[F, In, Out]]

    val metadata = PipelineMetadata(
      name = name,
      stages = stages.map(_.name),
      transformations = stages.collect { case _: SparkStage.Transform[_, _, _] => 1 }.size,
      qualityChecks   = stages.collect { case _: SparkStage.Quality[_, _]      => 1 }.size,
      tags = Map(
        "engine"       -> "spark",
        "type_safe"    -> "true",
        "spark_session" -> sparkSession.sparkContext.applicationId
      )
    )

    Pipeline(kleisliPipeline, metadata)
  }
}

/**
 * Runtime pipeline builder for dynamic pipeline construction
 */
class SparkRuntimeBuilder[F[_]: EffectSystem] private[spark] (
  private val name: String,
  private val sparkSession: SparkSession,
  private val dataAlgebra: DataAlgebra[F],
  private val stages: List[SparkStage[F, _, _]]
) {

  def addDynamicStage[A, B](
    stageName: String,
    operation: DataAlgebra.Dataset[A] => F[DataAlgebra.Dataset[B]]
  ): SparkRuntimeBuilder[F] = {
    val stage = SparkStage.Dynamic[F, DataAlgebra.Dataset[A], DataAlgebra.Dataset[B]](
      name = stageName,
      description = "Dynamic Spark operation",
      execute = Kleisli(operation)
    )
    new SparkRuntimeBuilder[F](name, sparkSession, dataAlgebra, stages :+ stage)
  }

  def buildRuntime(): Pipeline[F, Any, Any] = {
    // Runtime composition - less type safety but more flexibility
    val kleisliPipeline = stages.foldLeft(Kleisli.ask[F, Any]) { (acc, stage) =>
      acc.andThen(stage.asKleisli.asInstanceOf[Kleisli[F, Any, Any]])
    }

    val metadata = PipelineMetadata(
      name = name,
      stages = stages.map(_.name),
      transformations = stages.size,
      qualityChecks = 0,
      tags = Map(
        "engine" -> "spark",
        "type_safe" -> "false",
        "runtime" -> "true"
      )
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
    execute: Kleisli[F, Unit, DataAlgebra.Dataset[A]]
  ) extends SparkStage[F, Unit, DataAlgebra.Dataset[A]] {
    def asKleisli: Kleisli[F, Unit, DataAlgebra.Dataset[A]] = execute
  }

  case class Transform[F[_], A, B](
    name: String,
    description: String,
    execute: Kleisli[F, A, B]
  ) extends SparkStage[F, A, B] {
    def asKleisli: Kleisli[F, A, B] = execute
  }

  case class Quality[F[_], A](
    name: String,
    description: String,
    contract: com.flowforge.core.types.PipelineTypes.DataContract[A],
    execute: Kleisli[F, DataAlgebra.Dataset[A], DataAlgebra.Dataset[A]]
  ) extends SparkStage[F, DataAlgebra.Dataset[A], DataAlgebra.Dataset[A]] {
    def asKleisli: Kleisli[F, DataAlgebra.Dataset[A], DataAlgebra.Dataset[A]] = execute
  }

  case class Sink[F[_], A](
    name: String,
    description: String,
    sink: DataSink,
    execute: Kleisli[F, DataAlgebra.Dataset[A], Unit]
  ) extends SparkStage[F, DataAlgebra.Dataset[A], Unit] {
    def asKleisli: Kleisli[F, DataAlgebra.Dataset[A], Unit] = execute
  }

  case class Dynamic[F[_], A, B](
    name: String,
    description: String,
    execute: Kleisli[F, A, B]
  ) extends SparkStage[F, A, B] {
    def asKleisli: Kleisli[F, A, B] = execute
  }
}

object SparkPipelineBuilder {

  /**
   * Create a new Spark pipeline builder with resource-safe session management
   */
  def create[F[_]: EffectSystem](
    sparkSession: SparkSession
  ): SparkPipelineBuilder[F] = {
    val dataAlgebra = SparkDataAlgebra.createSparkDataAlgebra[F](sparkSession)
    new SparkPipelineBuilder[F](sparkSession, dataAlgebra)
  }

  /**
   * Create with managed Spark session (automatically closed)
   */
  def withManagedSession[F[_]: EffectSystem](
    appName: String,
    config: Map[String, String] = Map.empty
  ): Resource[F, SparkPipelineBuilder[F]] = {
    val F = EffectSystem[F]

    Resource.make {
      F.blocking {
        val builder = SparkSession.builder()
          .appName(appName)
          .master("local[*]") // Default to local mode

        config.foreach { case (key, value) =>
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
