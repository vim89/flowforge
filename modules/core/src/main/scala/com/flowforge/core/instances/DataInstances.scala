package com.flowforge.core.instances

import cats.{ Applicative, Functor, Monad, Show }
import cats.data.{ Kleisli, NonEmptyList, Validated, ValidatedNel }
import cats.effect.{ Resource, Sync }
import cats.implicits._
import com.flowforge.core.algebra.{ DataAlgebra, EffectSystem }
import com.flowforge.core.types._
import com.flowforge.core.syntax.ValidationSyntax._
import java.time.Instant
import java.util.UUID

/**
 * 🚀 **FlowForge Data Instances - Type Class Implementations**
 *
 * This module provides type class instances for all FlowForge data types, ensuring they integrate
 * seamlessly with the Cats ecosystem and provide consistent behavior across the entire framework.
 *
 * **Key Features:**
 *   - **Complete Cats Integration**: All major type classes implemented
 *   - **Effect System Instances**: Custom instances for EffectSystem types
 *   - **Data Type Instances**: Encoders, decoders, and validators
 *   - **Pipeline Instances**: Type class instances for pipeline components
 *   - **Validation Instances**: Rich validation type class instances
 *   - **Show Instances**: Human-readable string representations
 *
 * **Provided Instances:**
 *   - Functor, Applicative, Monad for pipeline types
 *   - Show instances for debugging and logging
 *   - DataEncoder/DataDecoder for serialization
 *   - Validation instances for data quality
 *
 * @author
 *   FlowForge Core Team
 * @since 0.1.0
 */

object DataInstances {

  // ===============================
  // CORE DATA TYPE INSTANCES
  // ===============================

  /**
   * Show instances for common FlowForge types (for debugging/logging)
   */
  implicit val environmentShow: Show[Environment] = Show.show {
    case Environment.Development => "Development"
    case Environment.Testing     => "Testing"
    case Environment.Staging     => "Staging"
    case Environment.Production  => "Production"
  }

  implicit val dataFormatShow: Show[DataFormat] = Show.show {
    case DataFormat.Parquet => "Parquet"
    case DataFormat.Avro    => "Avro"
    case DataFormat.CSV     => "CSV"
    case DataFormat.JSON    => "JSON"
    case DataFormat.JSONL   => "JSONL"
    case DataFormat.ORC     => "ORC"
    case DataFormat.Delta   => "Delta"
  }

  implicit val retryPolicyShow: Show[RetryPolicy] = Show.show { policy =>
    s"RetryPolicy(maxRetries=${policy.maxRetries}, backoffMultiplier=${policy.backoffMultiplier})"
  }

  implicit val flowForgeErrorShow: Show[FlowForgeError] = Show.show { error =>
    s"${error.getClass.getSimpleName}: ${error.message}"
  }

  // ===============================
  // DATA ALGEBRA INSTANCES
  // ===============================

  /**
   * Functor instance for Dataset
   */
  implicit val datasetFunctor: Functor[DataAlgebra.Dataset] = new Functor[DataAlgebra.Dataset] {
    def map[A, B](fa: DataAlgebra.Dataset[A])(f: A => B): DataAlgebra.Dataset[B] =
      fa.copy(
        id = s"${fa.id}_mapped",
        data = fa.data.map(f)
      )
  }

  /**
   * Show instance for Dataset
   */
  implicit def datasetShow[A: Show]: Show[DataAlgebra.Dataset[A]] = Show.show { dataset =>
    s"Dataset(id=${dataset.id}, size=${dataset.size}, schema=${dataset.schema.name})"
  }

  /**
   * DataEncoder instances for common types
   */
  implicit val stringDataEncoder: DataAlgebra.DataEncoder[String] =
    new DataAlgebra.DataEncoder[String] {
      def encode(value: String): DataAlgebra.EncodedData =
        DataAlgebra.EncodedData(value.getBytes("UTF-8"))
      def schema: DataAlgebra.DataSchema =
        DataAlgebra.DataSchema("string", Map("type" -> "string"), List("non-null"))
    }

  implicit val intDataEncoder: DataAlgebra.DataEncoder[Int] = new DataAlgebra.DataEncoder[Int] {
    def encode(value: Int): DataAlgebra.EncodedData =
      DataAlgebra.EncodedData(value.toString.getBytes("UTF-8"))
    def schema: DataAlgebra.DataSchema =
      DataAlgebra.DataSchema("int", Map("type" -> "integer"), List("non-null"))
  }

  implicit val longDataEncoder: DataAlgebra.DataEncoder[Long] = new DataAlgebra.DataEncoder[Long] {
    def encode(value: Long): DataAlgebra.EncodedData =
      DataAlgebra.EncodedData(value.toString.getBytes("UTF-8"))
    def schema: DataAlgebra.DataSchema =
      DataAlgebra.DataSchema("long", Map("type" -> "long"), List("non-null"))
  }

  implicit val doubleDataEncoder: DataAlgebra.DataEncoder[Double] =
    new DataAlgebra.DataEncoder[Double] {
      def encode(value: Double): DataAlgebra.EncodedData =
        DataAlgebra.EncodedData(value.toString.getBytes("UTF-8"))
      def schema: DataAlgebra.DataSchema =
        DataAlgebra.DataSchema("double", Map("type" -> "double"), List("non-null"))
    }

  implicit val booleanDataEncoder: DataAlgebra.DataEncoder[Boolean] =
    new DataAlgebra.DataEncoder[Boolean] {
      def encode(value: Boolean): DataAlgebra.EncodedData =
        DataAlgebra.EncodedData(value.toString.getBytes("UTF-8"))
      def schema: DataAlgebra.DataSchema =
        DataAlgebra.DataSchema("boolean", Map("type" -> "boolean"), List("non-null"))
    }

  implicit val instantDataEncoder: DataAlgebra.DataEncoder[Instant] =
    new DataAlgebra.DataEncoder[Instant] {
      def encode(value: Instant): DataAlgebra.EncodedData =
        DataAlgebra.EncodedData(value.toString.getBytes("UTF-8"))
      def schema: DataAlgebra.DataSchema =
        DataAlgebra.DataSchema("timestamp", Map("type" -> "timestamp"), List("non-null"))
    }

  /**
   * Generic encoder for case classes (simplified - would use reflection/macros in real
   * implementation)
   */
  implicit def caseClassEncoder[A <: Product]: DataAlgebra.DataEncoder[A] =
    new DataAlgebra.DataEncoder[A] {
      def encode(value: A): DataAlgebra.EncodedData =
        DataAlgebra.EncodedData(value.toString.getBytes("UTF-8"))
      def schema: DataAlgebra.DataSchema =
        DataAlgebra.DataSchema(
          name = value.getClass.getSimpleName.toLowerCase,
          fields = Map("type" -> "record"),
          constraints = List("non-null")
        )
    }

  /**
   * DataDecoder instances for common types
   */
  implicit val stringDataDecoder: DataAlgebra.DataDecoder[String] =
    new DataAlgebra.DataDecoder[String] {
      def decode(data: DataAlgebra.EncodedData): Either[FlowForgeError, String] =
        Right(new String(data.bytes, "UTF-8"))
      def expectedSchema: DataAlgebra.DataSchema =
        DataAlgebra.DataSchema("string", Map("type" -> "string"), List("non-null"))
    }

  implicit val intDataDecoder: DataAlgebra.DataDecoder[Int] = new DataAlgebra.DataDecoder[Int] {
    def decode(data: DataAlgebra.EncodedData): Either[FlowForgeError, Int] = {
      val str = new String(data.bytes, "UTF-8")
      scala.util
        .Try(str.toInt)
        .toEither
        .left
        .map(e =>
          FlowForgeError
            .DataProcessingError(s"Cannot decode '$str' as Int: ${e.getMessage}", Some(e))
        )
    }
    def expectedSchema: DataAlgebra.DataSchema =
      DataAlgebra.DataSchema("int", Map("type" -> "integer"), List("non-null"))
  }

  implicit val longDataDecoder: DataAlgebra.DataDecoder[Long] = new DataAlgebra.DataDecoder[Long] {
    def decode(data: DataAlgebra.EncodedData): Either[FlowForgeError, Long] = {
      val str = new String(data.bytes, "UTF-8")
      scala.util
        .Try(str.toLong)
        .toEither
        .left
        .map(e =>
          FlowForgeError
            .DataProcessingError(s"Cannot decode '$str' as Long: ${e.getMessage}", Some(e))
        )
    }
    def expectedSchema: DataAlgebra.DataSchema =
      DataAlgebra.DataSchema("long", Map("type" -> "long"), List("non-null"))
  }

  implicit val doubleDataDecoder: DataAlgebra.DataDecoder[Double] =
    new DataAlgebra.DataDecoder[Double] {
      def decode(data: DataAlgebra.EncodedData): Either[FlowForgeError, Double] = {
        val str = new String(data.bytes, "UTF-8")
        scala.util
          .Try(str.toDouble)
          .toEither
          .left
          .map(e =>
            FlowForgeError
              .DataProcessingError(s"Cannot decode '$str' as Double: ${e.getMessage}", Some(e))
          )
      }
      def expectedSchema: DataAlgebra.DataSchema =
        DataAlgebra.DataSchema("double", Map("type" -> "double"), List("non-null"))
    }

  implicit val booleanDataDecoder: DataAlgebra.DataDecoder[Boolean] =
    new DataAlgebra.DataDecoder[Boolean] {
      def decode(data: DataAlgebra.EncodedData): Either[FlowForgeError, Boolean] = {
        val str = new String(data.bytes, "UTF-8")
        str.toLowerCase match {
          case "true" | "1" | "yes" => Right(true)
          case "false" | "0" | "no" => Right(false)
          case _ =>
            Left(FlowForgeError.DataProcessingError(s"Cannot decode '$str' as Boolean", None))
        }
      }
      def expectedSchema: DataAlgebra.DataSchema =
        DataAlgebra.DataSchema("boolean", Map("type" -> "boolean"), List("non-null"))
    }

  implicit val instantDataDecoder: DataAlgebra.DataDecoder[Instant] =
    new DataAlgebra.DataDecoder[Instant] {
      def decode(data: DataAlgebra.EncodedData): Either[FlowForgeError, Instant] = {
        val str = new String(data.bytes, "UTF-8")
        scala.util
          .Try(Instant.parse(str))
          .toEither
          .left
          .map(e =>
            FlowForgeError
              .DataProcessingError(s"Cannot decode '$str' as Instant: ${e.getMessage}", Some(e))
          )
      }
      def expectedSchema: DataAlgebra.DataSchema =
        DataAlgebra.DataSchema("timestamp", Map("type" -> "timestamp"), List("non-null"))
    }

  // ===============================
  // VALIDATION TYPE CLASS INSTANCES
  // ===============================

  /**
   * Functor instance for ValidationResult
   */
  implicit val validationResultFunctor: Functor[ValidationResult] = new Functor[ValidationResult] {
    def map[A, B](fa: ValidationResult[A])(f: A => B): ValidationResult[B] = fa.map(f)
  }

  /**
   * Applicative instance for ValidationResult (for validation accumulation)
   */
  implicit val validationResultApplicative: Applicative[ValidationResult] =
    new Applicative[ValidationResult] {
      def pure[A](x: A): ValidationResult[A] = x.validNel

      def ap[A, B](ff: ValidationResult[A => B])(fa: ValidationResult[A]): ValidationResult[B] =
        (ff, fa).mapN((f, a) => f(a))
    }

  /**
   * Show instance for validation results
   */
  implicit def validationResultShow[A: Show]: Show[ValidationResult[A]] = Show.show {
    case Validated.Valid(a)        => s"Valid(${a.show})"
    case Validated.Invalid(errors) => s"Invalid(${errors.toList.map(_.show).mkString(", ")})"
  }

  // ===============================
  // PIPELINE TYPE CLASS INSTANCES
  // ===============================

  /**
   * Functor instance for PipelineComponent (Kleisli)
   */
  implicit def pipelineComponentFunctor[F[_]: Functor, A]: Functor[Kleisli[F, A, *]] =
    new Functor[Kleisli[F, A, *]] {
      def map[B, C](fa: Kleisli[F, A, B])(f: B => C): Kleisli[F, A, C] = fa.map(f)
    }

  /**
   * Applicative instance for PipelineComponent
   */
  implicit def pipelineComponentApplicative[F[_]: Applicative, A]: Applicative[Kleisli[F, A, *]] =
    new Applicative[Kleisli[F, A, *]] {
      def pure[B](x: B): Kleisli[F, A, B] = Kleisli.pure(x)

      def ap[B, C](ff: Kleisli[F, A, B => C])(fa: Kleisli[F, A, B]): Kleisli[F, A, C] =
        ff.ap(fa)
    }

  /**
   * Monad instance for PipelineComponent
   */
  implicit def pipelineComponentMonad[F[_]: Monad, A]: Monad[Kleisli[F, A, *]] =
    new Monad[Kleisli[F, A, *]] {
      def pure[B](x: B): Kleisli[F, A, B] = Kleisli.pure(x)

      def flatMap[B, C](fa: Kleisli[F, A, B])(f: B => Kleisli[F, A, C]): Kleisli[F, A, C] =
        fa.flatMap(f)

      def tailRecM[B, C](b: B)(f: B => Kleisli[F, A, Either[B, C]]): Kleisli[F, A, C] =
        Kleisli.tailRecM(b)(f)
    }

  // ===============================
  // DATA QUALITY INSTANCES
  // ===============================

  /**
   * Default data contracts for common types
   */
  implicit val stringDataContract: DataContract[String] = { str =>
    str
      .validIf(
        str.nonEmpty,
        FlowForgeError.ValidationError("String cannot be empty", None)
      )
      .map(_ => ())
  }

  implicit val intDataContract: DataContract[Int] = { int =>
    int
      .validIf(
        int >= 0,
        FlowForgeError.ValidationError(s"Integer $int must be non-negative", None)
      )
      .map(_ => ())
  }

  implicit val doubleDataContract: DataContract[Double] = { double =>
    double
      .validIf(
        !double.isNaN && !double.isInfinite,
        FlowForgeError.ValidationError(s"Double $double must be finite", None)
      )
      .map(_ => ())
  }

  /**
   * Generic data contract for case classes
   */
  implicit def caseClassDataContract[A <: Product]: DataContract[A] = { value =>
    // Simplified - in real implementation would validate all fields
    value
      .validIf(
        value != null,
        FlowForgeError.ValidationError("Case class instance cannot be null", None)
      )
      .map(_ => ())
  }

  // ===============================
  // CONFIGURATION INSTANCES
  // ===============================

  /**
   * Show instances for configuration types
   */
  implicit val pipelineConfigShow: Show[PipelineConfig] = Show.show { config =>
    s"PipelineConfig(settings=${config.settings.size} items, retryPolicy=${config.retryPolicy.show})"
  }

  implicit val dataSourceShow: Show[DataSource] = Show.show { source =>
    source match {
      case DataSource.GcsSource(bucket, prefix, format, _) =>
        s"GcsSource(bucket=$bucket, prefix=$prefix, format=${format.show})"
      case DataSource.S3Source(bucket, prefix, format, _) =>
        s"S3Source(bucket=$bucket, prefix=$prefix, format=${format.show})"
      case DataSource.BigQuerySource(project, dataset, table, _) =>
        s"BigQuerySource(project=$project, dataset=$dataset, table=$table)"
      case DataSource.JdbcSource(url, query, _) =>
        s"JdbcSource(url=$url, query=$query)"
    }
  }

  implicit val dataSinkShow: Show[DataSink] = Show.show { sink =>
    sink match {
      case DataSink.GcsSink(bucket, prefix, format, _) =>
        s"GcsSink(bucket=$bucket, prefix=$prefix, format=${format.show})"
      case DataSink.S3Sink(bucket, prefix, format, _) =>
        s"S3Sink(bucket=$bucket, prefix=$prefix, format=${format.show})"
      case DataSink.BigQuerySink(project, dataset, table, _) =>
        s"BigQuerySink(project=$project, dataset=$dataset, table=$table)"
      case DataSink.JdbcSink(url, table, _) =>
        s"JdbcSink(url=$url, table=$table)"
    }
  }

  // ===============================
  // ERROR TYPE INSTANCES
  // ===============================

  /**
   * Show instances for error types
   */
  implicit val configErrorShow: Show[ConfigError] = Show.show {
    case ConfigError.MissingRequired(field) => s"MissingRequired($field)"
    case ConfigError.InvalidFormat(field, value, expected) =>
      s"InvalidFormat($field=$value, expected=$expected)"
    case ConfigError.OutOfRange(field, value, min, max) =>
      s"OutOfRange($field=$value, range=[$min, $max])"
  }

  implicit val validationErrorShow: Show[ValidationError] = Show.show { error =>
    s"ValidationError(${error.message}, field=${error.field})"
  }

  // ===============================
  // UTILITY INSTANCES
  // ===============================

  /**
   * UUID-based ID generators
   */
  implicit val uuidGenerator: () => String = () => UUID.randomUUID().toString

  /**
   * Timestamp generators
   */
  implicit val instantGenerator: () => Instant = () => Instant.now()

  /**
   * Default resource configurations
   */
  implicit val defaultResourceConfig: ResourceConfig = ResourceConfig(
    timeout = scala.concurrent.duration.Duration.fromNanos(30000000000L), // 30 seconds
    retryPolicy = RetryPolicy.default,
    properties = Map.empty
  )

  // ===============================
  // EFFECT SYSTEM INTEGRATION
  // ===============================

  /**
   * Helper to create DataAlgebra instances for different effect systems
   */
  def createMockDataAlgebra[F[_]: EffectSystem]: DataAlgebra[F] = new DataAlgebra[F] {
    import DataAlgebra._

    val F = implicitly[EffectSystem[F]]

    def read[A: DataDecoder](source: DataSource): F[Dataset[A]] =
      F.delay(Dataset.empty[A])

    def readWithSchema[A: DataDecoder: SchemaValidator](
      source: DataSource,
      expectedSchema: DataSchema
    ): F[Either[FlowForgeError, Dataset[A]]] =
      F.delay(Right(Dataset.empty[A]))

    // Simplified implementations for other methods
    def stream[A: DataDecoder](source: DataSource): F[DataStream[F, A]] =
      F.raiseError(new NotImplementedError("Streaming not implemented in mock"))

    def readBatch[A: DataDecoder](source: DataSource, batchSize: Int): F[List[Dataset[A]]] =
      F.delay(List(Dataset.empty[A]))

    def transform[A, B: DataEncoder](
      dataset: Dataset[A],
      transformation: A => F[B]
    ): F[Dataset[B]] =
      F.delay(Dataset.empty[B])

    def transformPipeline[A, B: DataEncoder](
      dataset: Dataset[A],
      transformations: NonEmptyList[A => F[B]]
    ): F[Dataset[B]] =
      F.delay(Dataset.empty[B])

    def filter[A](dataset: Dataset[A], predicate: A => Boolean): F[Dataset[A]] =
      F.delay(dataset.filter(predicate))

    def mapWithEffect[A, B: DataEncoder](dataset: Dataset[A], f: A => F[B]): F[Dataset[B]] =
      dataset.data
        .traverse(f)
        .map(transformedData => Dataset.fromList(transformedData, s"${dataset.id}_mapped"))

    // Additional methods with simplified implementations
    def flatMapWithEffect[A, B: DataEncoder](
      dataset: Dataset[A],
      f: A => F[Dataset[B]]
    ): F[Dataset[B]] =
      F.delay(Dataset.empty[B])

    def groupBy[A, K, V: DataEncoder](
      dataset: Dataset[A],
      keyExtractor: A => K,
      aggregator: List[A] => V
    ): F[Dataset[(K, V)]] =
      F.delay(Dataset.empty[(K, V)])

    def join[A, B, K, C: DataEncoder](
      left: Dataset[A],
      right: Dataset[B],
      leftKey: A => K,
      rightKey: B => K,
      combiner: (A, B) => C
    ): F[Dataset[C]] =
      F.delay(Dataset.empty[C])

    def validate[A](dataset: Dataset[A], contract: DataContract[A]): F[QualityResult[Dataset[A]]] =
      F.delay(
        QualityResult(
          data = dataset,
          score = 1.0,
          checks = List.empty,
          passed = true,
          metadata = Map.empty
        )
      )

    def runQualityChecks[A](
      dataset: Dataset[A],
      checks: NonEmptyList[QualityCheck[A]]
    ): F[List[QualityCheckResult]] =
      F.delay(List.empty)

    def profile[A](dataset: Dataset[A]): F[DataProfile[A]] =
      F.delay(
        DataProfile(
          recordCount = dataset.size.toLong,
          nullCounts = Map.empty,
          uniqueCounts = Map.empty,
          dataTypes = Map.empty,
          statistics = Map.empty,
          schema = dataset.schema
        )
      )

    def clean[A](dataset: Dataset[A], cleaningRules: List[CleaningRule[A]]): F[Dataset[A]] =
      F.delay(dataset)

    def detectAnomalies[A](
      dataset: Dataset[A],
      detectors: List[AnomalyDetector[A]]
    ): F[AnomalyReport[A]] =
      F.delay(
        AnomalyReport(
          datasetId = dataset.id,
          anomalies = List.empty,
          totalRecords = dataset.size.toLong,
          anomalyRate = 0.0,
          detectionTime = Instant.now()
        )
      )

    def extractSchema[A](dataset: Dataset[A]): F[DataSchema] =
      F.delay(dataset.schema)

    def evolveSchema[A, B: DataEncoder](
      dataset: Dataset[A],
      migration: SchemaMigration[A, B]
    ): F[Dataset[B]] =
      F.delay(Dataset.empty[B])

    def compareSchemas(source: DataSchema, target: DataSchema): F[SchemaCompatibilityReport] =
      F.delay(
        SchemaCompatibilityReport(
          compatible = true,
          issues = List.empty,
          recommendations = List.empty
        )
      )

    def validateSchema[A](
      dataset: Dataset[A],
      schema: DataSchema
    ): F[ValidatedNel[FlowForgeError, Dataset[A]]] =
      F.delay(dataset.validNel)

    def write[A: DataEncoder](dataset: Dataset[A], sink: DataSink): F[WriteResult] =
      F.delay(
        WriteResult(
          recordsWritten = dataset.size.toLong,
          bytesWritten = dataset.size * 100L,
          partitions = List("partition-1"),
          duration = 1000L,
          success = true,
          errors = List.empty
        )
      )

    def writeWithOptions[A: DataEncoder](
      dataset: Dataset[A],
      sink: DataSink,
      options: WriteOptions
    ): F[WriteResult] =
      write(dataset, sink)

    def writeStream[A: DataEncoder](stream: DataStream[F, A], sink: DataSink): F[WriteResult] =
      F.raiseError(new NotImplementedError("Stream writing not implemented in mock"))

    def writeBatch[A: DataEncoder](
      datasets: List[Dataset[A]],
      sink: DataSink
    ): F[List[WriteResult]] =
      datasets.traverse(write(_, sink))

    def extractMetadata[A](dataset: Dataset[A]): F[DatasetMetadata] =
      F.delay(dataset.metadata)

    def trackLineage[A](
      dataset: Dataset[A],
      operation: DataOperation,
      context: LineageContext
    ): F[LineageRecord] =
      F.delay(
        LineageRecord(
          id = UUID.randomUUID().toString,
          datasetId = dataset.id,
          operation = operation,
          inputs = List.empty,
          outputs = List.empty,
          timestamp = Instant.now(),
          context = context
        )
      )

    def queryLineage(datasetId: String, query: LineageQuery): F[List[LineageRecord]] =
      F.delay(List.empty)

    def count[A](dataset: Dataset[A]): F[Long] =
      F.delay(dataset.size.toLong)

    def isEmpty[A](dataset: Dataset[A]): F[Boolean] =
      F.delay(dataset.isEmpty)

    def take[A](dataset: Dataset[A], n: Int): F[Dataset[A]] =
      F.delay(dataset.copy(data = dataset.data.take(n)))

    def sample[A](dataset: Dataset[A], fraction: Double): F[Dataset[A]] =
      F.delay(dataset.copy(data = dataset.data.take((dataset.size * fraction).toInt)))

    def cache[A](dataset: Dataset[A], strategy: CacheStrategy): F[Dataset[A]] =
      F.delay(dataset)

    def partition[A](dataset: Dataset[A], partitioner: Partitioner[A]): F[Map[String, Dataset[A]]] =
      F.delay(Map("default" -> dataset))
  }

  // ===============================
  // IMPLICIT SUMMONERS
  // ===============================

  /**
   * Convenient summoner methods for type class instances
   */
  def dataEncoder[A](implicit encoder: DataAlgebra.DataEncoder[A]): DataAlgebra.DataEncoder[A] =
    encoder
  def dataDecoder[A](implicit decoder: DataAlgebra.DataDecoder[A]): DataAlgebra.DataDecoder[A] =
    decoder
  def dataContract[A](implicit contract: DataContract[A]): DataContract[A] = contract
  def showInstance[A](implicit show: Show[A]): Show[A]                     = show

  // ===============================
  // TYPE ALIASES FOR CONVENIENCE
  // ===============================

  type DataContract[A]     = A => ValidationResult[Unit]
  type ValidationResult[A] = ValidatedNel[FlowForgeError, A]
  type QualityCheck[A]     = A => ValidationResult[A]
  type ResourceConfig      = com.flowforge.core.patterns.ReaderPattern.ResourceConfig
}
