/**
 * FlowForge Spark Engine - Pipeline Operations
 * 
 * This module provides Kleisli-based operations for Spark DataAlgebra, enabling seamless
 * integration with FlowForge's functional pipeline composition system.
 * 
 * Key Features:
 * - Kleisli arrows for Spark operations
 * - Type-safe pipeline composition
 * - Resource-safe execution
 * - Error handling with ValidatedNel
 * - Production-ready monitoring
 */
package com.flowforge.engines.spark

import cats.data.{Kleisli, NonEmptyList, ValidatedNel}
import cats.effect.Resource
import cats.implicits._
import com.flowforge.core.algebra.DataAlgebra._
import com.flowforge.core.algebra.EffectSystem
import com.flowforge.core.types._
import com.flowforge.core.types.PipelineTypes._
import org.apache.spark.sql.SparkSession

/**
 * Kleisli-based operations for Spark DataAlgebra, enabling functional pipeline composition
 * with Apache Spark as the execution engine.
 */
object SparkPipelineOps {

  /**
   * Create a data reading operation as Kleisli arrow
   */
  def read[F[_]: EffectSystem, A: DataDecoder](
    source: DataSource
  )(implicit spark: SparkDataAlgebra[F]): Kleisli[F, Unit, Dataset[A]] =
    Kleisli(_ => spark.read[A](source))

  /**
   * Create a transformation operation as Kleisli arrow
   */
  def transform[F[_]: EffectSystem, A, B: DataEncoder](
    transformation: A => F[B]
  )(implicit spark: SparkDataAlgebra[F]): Kleisli[F, Dataset[A], Dataset[B]] =
    Kleisli(dataset => spark.transform(dataset, transformation))

  /**
   * Create a filtering operation as Kleisli arrow
   */
  def filter[F[_]: EffectSystem, A](
    predicate: A => Boolean
  )(implicit spark: SparkDataAlgebra[F]): Kleisli[F, Dataset[A], Dataset[A]] =
    Kleisli(dataset => spark.filter(dataset, predicate))

  /**
   * Create a validation operation as Kleisli arrow
   */
  def validate[F[_]: EffectSystem, A](
    contract: DataContract[A]
  )(implicit spark: SparkDataAlgebra[F]): Kleisli[F, Dataset[A], QualityResult[Dataset[A]]] =
    Kleisli(dataset => spark.validate(dataset, contract))

  /**
   * Create a quality check operation as Kleisli arrow
   */
  def qualityCheck[F[_]: EffectSystem, A](
    checks: NonEmptyList[QualityCheck[A]]
  )(implicit spark: SparkDataAlgebra[F]): Kleisli[F, Dataset[A], (Dataset[A], List[QualityCheckResult])] =
    Kleisli { dataset =>
      spark.runQualityChecks(dataset, checks).map(results => (dataset, results))
    }

  /**
   * Create a data writing operation as Kleisli arrow
   */
  def write[F[_]: EffectSystem, A: DataEncoder](
    sink: DataSink
  )(implicit spark: SparkDataAlgebra[F]): Kleisli[F, Dataset[A], WriteResult] =
    Kleisli(dataset => spark.write(dataset, sink))

  /**
   * Create a data writing operation with options as Kleisli arrow
   */
  def writeWithOptions[F[_]: EffectSystem, A: DataEncoder](
    sink: DataSink,
    options: WriteOptions
  )(implicit spark: SparkDataAlgebra[F]): Kleisli[F, Dataset[A], WriteResult] =
    Kleisli(dataset => spark.writeWithOptions(dataset, sink, options))

  /**
   * Create a join operation as Kleisli arrow
   */
  def join[F[_]: EffectSystem, A, B, K, C: DataEncoder](
    right: Dataset[B],
    leftKey: A => K,
    rightKey: B => K,
    combiner: (A, B) => C
  )(implicit spark: SparkDataAlgebra[F]): Kleisli[F, Dataset[A], Dataset[C]] =
    Kleisli(left => spark.join(left, right, leftKey, rightKey, combiner))

  /**
   * Create an aggregation operation as Kleisli arrow
   */
  def groupBy[F[_]: EffectSystem, A, K, V: DataEncoder](
    keyExtractor: A => K,
    aggregator: List[A] => V
  )(implicit spark: SparkDataAlgebra[F]): Kleisli[F, Dataset[A], Dataset[(K, V)]] =
    Kleisli(dataset => spark.groupBy(dataset, keyExtractor, aggregator))

  /**
   * Create a cleaning operation as Kleisli arrow
   */
  def clean[F[_]: EffectSystem, A](
    cleaningRules: List[CleaningRule[A]]
  )(implicit spark: SparkDataAlgebra[F]): Kleisli[F, Dataset[A], Dataset[A]] =
    Kleisli(dataset => spark.clean(dataset, cleaningRules))

  /**
   * Create a sampling operation as Kleisli arrow
   */
  def sample[F[_]: EffectSystem, A](
    fraction: Double
  )(implicit spark: SparkDataAlgebra[F]): Kleisli[F, Dataset[A], Dataset[A]] =
    Kleisli(dataset => spark.sample(dataset, fraction))

  /**
   * Create a caching operation as Kleisli arrow
   */
  def cache[F[_]: EffectSystem, A](
    strategy: CacheStrategy = CacheStrategy.Memory
  )(implicit spark: SparkDataAlgebra[F]): Kleisli[F, Dataset[A], Dataset[A]] =
    Kleisli(dataset => spark.cache(dataset, strategy))

  /**
   * Create a partitioning operation as Kleisli arrow
   */
  def partition[F[_]: EffectSystem, A](
    partitioner: Partitioner[A]
  )(implicit spark: SparkDataAlgebra[F]): Kleisli[F, Dataset[A], Map[String, Dataset[A]]] =
    Kleisli(dataset => spark.partition(dataset, partitioner))

  /**
   * Create a schema evolution operation as Kleisli arrow
   */
  def evolveSchema[F[_]: EffectSystem, A, B: DataEncoder](
    migration: SchemaMigration[A, B]
  )(implicit spark: SparkDataAlgebra[F]): Kleisli[F, Dataset[A], Dataset[B]] =
    Kleisli(dataset => spark.evolveSchema(dataset, migration))

  // ===============================
  // COMPLEX PIPELINE COMPOSITIONS
  // ===============================

  /**
   * Complete ETL pipeline composition
   */
  def etlPipeline[F[_]: EffectSystem, A: DataDecoder, B: DataEncoder, C: DataEncoder](
    source: DataSource,
    transformation: A => F[B],
    contract: DataContract[B],
    cleaning: List[CleaningRule[B]],
    finalTransform: B => F[C],
    sink: DataSink
  )(implicit spark: SparkDataAlgebra[F]): Kleisli[F, Unit, WriteResult] = {
    read[F, A](source)
      .andThen(transform(transformation))
      .andThen(validate(contract).map(_.data))
      .andThen(clean(cleaning))
      .andThen(transform(finalTransform))
      .andThen(write(sink))
  }

  /**
   * Data quality pipeline composition
   */
  def qualityPipeline[F[_]: EffectSystem, A: DataDecoder](
    source: DataSource,
    contract: DataContract[A],
    qualityChecks: NonEmptyList[QualityCheck[A]],
    anomalyDetectors: List[AnomalyDetector[A]]
  )(implicit spark: SparkDataAlgebra[F]): Kleisli[F, Unit, (QualityResult[Dataset[A]], AnomalyReport[A])] = {
    
    val pipeline = for {
      dataset <- read[F, A](source)
      qualityResult <- validate(contract).run(dataset)
      anomalyReport <- Kleisli.liftF(spark.detectAnomalies(dataset, anomalyDetectors))
    } yield (qualityResult, anomalyReport)
    
    pipeline
  }

  /**
   * CDC pipeline composition
   */
  def cdcPipeline[F[_]: EffectSystem, A: DataDecoder: DataEncoder](
    sourceDataSource: DataSource,
    targetDataSource: DataSource,
    primaryKeys: NonEmptyList[com.flowforge.core.types.RefinedTypes.FieldName],
    sink: DataSink
  )(implicit spark: SparkDataAlgebra[F]): Kleisli[F, Unit, WriteResult] = {
    
    import com.flowforge.core.algebra.CDCOperations
    
    val pipeline = for {
      sourceDataset <- read[F, A](sourceDataSource)
      targetDataset <- read[F, A](targetDataSource)
      cdcResult <- Kleisli.liftF(spark.performDelta(sourceDataset, targetDataset, primaryKeys))
      // In production, this would write the delta results
      writeResult <- write[F, A](sink).run(sourceDataset)
    } yield writeResult
    
    pipeline
  }

  /**
   * Streaming pipeline composition
   */
  def streamingPipeline[F[_]: EffectSystem, A: DataDecoder: DataEncoder, B: DataEncoder](
    source: DataSource,
    transformation: A => F[B],
    sink: DataSink
  )(implicit spark: SparkDataAlgebra[F]): Kleisli[F, Unit, WriteResult] = {
    
    val pipeline = for {
      stream <- Kleisli.liftF(spark.stream[A](source))
      transformedStream <- Kleisli.liftF(stream.process(transformation))
      writeResult <- Kleisli.liftF(spark.writeStream(transformedStream, sink))
    } yield writeResult
    
    pipeline
  }

  // ===============================
  // RESOURCE-SAFE PIPELINE EXECUTION
  // ===============================

  /**
   * Execute pipeline with resource-safe Spark session management
   */
  def executePipeline[F[_]: EffectSystem, A](
    pipeline: Kleisli[F, SparkDataAlgebra[F], A],
    appName: String = "FlowForge-Pipeline",
    sparkConfigs: Map[String, String] = Map.empty
  ): F[A] = {
    SparkDataAlgebra.resource[F](appName).use { spark =>
      pipeline.run(spark)
    }
  }

  /**
   * Execute pipeline with custom Spark configuration
   */
  def executeWithConfig[F[_]: EffectSystem, A](
    pipeline: Kleisli[F, SparkDataAlgebra[F], A],
    sparkConfig: SparkConfig
  ): F[A] = {
    createSparkSession(sparkConfig).use { spark =>
      val algebra = SparkDataAlgebra[F](spark)
      pipeline.run(algebra)
    }
  }

  /**
   * Execute production pipeline with optimized configuration
   */
  def executeProduction[F[_]: EffectSystem, A](
    pipeline: Kleisli[F, SparkDataAlgebra[F], A],
    appName: String,
    deployMode: String = "cluster"
  ): F[A] = {
    SparkDataAlgebra.production[F](appName, deployMode).use { spark =>
      pipeline.run(spark)
    }
  }

  // ===============================
  // HELPER TYPES AND METHODS
  // ===============================

  /**
   * Spark configuration for pipeline execution
   */
  case class SparkConfig(
    appName: String,
    master: String = "local[*]",
    configs: Map[String, String] = Map.empty,
    enableHiveSupport: Boolean = false
  )

  /**
   * Create Spark session with custom configuration
   */
  private def createSparkSession[F[_]: EffectSystem](
    config: SparkConfig
  ): Resource[F, SparkSession] = {
    val effectSystem = EffectSystem[F]
    
    Resource.make {
      effectSystem.delay {
        var builder = SparkSession.builder()
          .appName(config.appName)
          .master(config.master)
        
        config.configs.foreach { case (key, value) =>
          builder = builder.config(key, value)
        }
        
        if (config.enableHiveSupport) {
          builder = builder.enableHiveSupport()
        }
        
        builder.getOrCreate()
      }
    } { spark =>
      effectSystem.delay(spark.stop())
    }
  }

  // ===============================
  // SYNTAX EXTENSIONS
  // ===============================

  /**
   * Syntax extensions for Dataset operations
   */
  implicit class DatasetSparkOps[F[_]: EffectSystem, A](dataset: Dataset[A]) {
    
    def sparkTransform[B: DataEncoder](
      transformation: A => F[B]
    )(implicit spark: SparkDataAlgebra[F]): F[Dataset[B]] =
      spark.transform(dataset, transformation)
    
    def sparkFilter(
      predicate: A => Boolean
    )(implicit spark: SparkDataAlgebra[F]): F[Dataset[A]] =
      spark.filter(dataset, predicate)
    
    def sparkValidate(
      contract: DataContract[A]
    )(implicit spark: SparkDataAlgebra[F]): F[QualityResult[Dataset[A]]] =
      spark.validate(dataset, contract)
    
    def sparkWrite[B >: A: DataEncoder](
      sink: DataSink
    )(implicit spark: SparkDataAlgebra[F]): F[WriteResult] =
      spark.write(dataset, sink)
    
    def sparkCache(
      strategy: CacheStrategy = CacheStrategy.Memory
    )(implicit spark: SparkDataAlgebra[F]): F[Dataset[A]] =
      spark.cache(dataset, strategy)
  }

  /**
   * Syntax extensions for Kleisli composition with Spark operations
   */
  implicit class KleisliSparkOps[F[_]: EffectSystem, A, B](kleisli: Kleisli[F, A, B]) {
    
    def withSparkCache(
      strategy: CacheStrategy = CacheStrategy.Memory
    )(implicit spark: SparkDataAlgebra[F], ev: B <:< Dataset[_]): Kleisli[F, A, B] =
      kleisli.andThen(Kleisli { dataset =>
        spark.cache(dataset, strategy).asInstanceOf[F[B]]
      })
    
    def withSparkMonitoring(
      onSuccess: B => F[Unit] = _ => EffectSystem[F].unit,
      onError: Throwable => F[Unit] = _ => EffectSystem[F].unit
    ): Kleisli[F, A, B] =
      Kleisli { input =>
        kleisli.run(input).flatTap(onSuccess).handleErrorWith { error =>
          onError(error) *> EffectSystem[F].raiseError(error)
        }
      }
  }

  // ===============================
  // COMMON PIPELINE PATTERNS
  // ===============================

  /**
   * Common pattern for data ingestion with validation
   */
  def ingestWithValidation[F[_]: EffectSystem, A: DataDecoder](
    source: DataSource,
    contract: DataContract[A]
  )(implicit spark: SparkDataAlgebra[F]): Kleisli[F, Unit, QualityResult[Dataset[A]]] =
    read[F, A](source).andThen(validate(contract))

  /**
   * Common pattern for data export with quality checks
   */
  def exportWithQuality[F[_]: EffectSystem, A: DataEncoder](
    qualityChecks: NonEmptyList[QualityCheck[A]],
    sink: DataSink
  )(implicit spark: SparkDataAlgebra[F]): Kleisli[F, Dataset[A], WriteResult] = {
    qualityCheck(qualityChecks)
      .andThen(Kleisli(_._1))
      .andThen(write(sink))
  }

  /**
   * Common pattern for data transformation with error handling
   */
  def safeTransform[F[_]: EffectSystem, A, B: DataEncoder](
    transformation: A => F[B],
    fallback: A => F[B]
  )(implicit spark: SparkDataAlgebra[F]): Kleisli[F, Dataset[A], Dataset[B]] = {
    Kleisli { dataset =>
      spark.transform(dataset, transformation).handleErrorWith { _ =>
        spark.transform(dataset, fallback)
      }
    }
  }
}