package com.flowforge.core.algebra

import cats.data.{ Kleisli, NonEmptyList, ValidatedNel }
import cats.{ Functor, Monad }
import com.flowforge.core.QualityCheck
import com.flowforge.core.algebra.DataAlgebra.{
  AnomalyDetector,
  AnomalyReport,
  CacheStrategy,
  CleaningRule,
  DataOperation,
  DataProfile,
  DataStream,
  Dataset,
  DatasetMetadata,
  LineageContext,
  LineageQuery,
  LineageRecord,
  Partitioner,
  QualityCheckResult,
  QualityResult,
  SchemaCompatibilityReport,
  SchemaMigration,
  SchemaValidator,
  WriteOptions,
  WriteResult
}
import com.flowforge.core.types._

import java.time.Instant

/**
 * 🚀 **FlowForge Data Algebra - Universal Data Operations**
 *
 * This module defines the complete algebra for data operations in FlowForge. It provides a
 * functional, composable, and effect-safe interface for all data processing operations, integrating
 * seamlessly with the existing Kleisli-based pipeline architecture.
 *
 * **Key Design Principles:**
 *   - **Effect Polymorphism**: Works with any effect system F[_]
 *   - **Type Safety**: All operations are statically typed
 *   - **Composability**: Operations compose via Kleisli arrows
 *   - **Resource Safety**: Automatic resource management via Resource[F, _]
 *   - **Error Handling**: Comprehensive error types with recovery
 *   - **Integration**: Seamlessly works with existing FlowForge components
 *
 * **Supported Operations:**
 *   - Data reading from multiple sources (JDBC, Files, APIs)
 *   - Schema evolution and migration
 *   - Data transformations with quality checks
 *   - Data validation and quality assessment
 *   - Data writing to multiple sinks
 *   - Metadata operations and lineage tracking
 *
 * @author
 *   FlowForge Core Team
 * @since 0.1.0
 */

/**
 * Core algebra defining all data operations in FlowForge. Integrates with the existing
 * EffectSystem[F[_]] architecture.
 */
trait DataAlgebra[F[_]] {

  // ===============================
  // CORE DATA SOURCE OPERATIONS
  // ===============================

  /**
   * Read data from a source with automatic resource management
   */
  def read[A: DataDecoder](source: DataSource): F[Dataset[A]]

  /**
   * Read data with schema validation
   */
  def readWithSchema[A: DataDecoder: SchemaValidator](
    source: DataSource,
    expectedSchema: DataSchema
  ): F[Either[FlowForgeError, Dataset[A]]]

  /**
   * Stream data for large datasets
   */
  def stream[A: DataDecoder](source: DataSource): F[DataStream[F, A]]

  /**
   * Batch read with configurable size
   */
  def readBatch[A: DataDecoder](
    source: DataSource,
    batchSize: Int
  ): F[List[Dataset[A]]]

  // ===============================
  // DATA TRANSFORMATION OPERATIONS
  // ===============================

  /**
   * Apply a transformation to a dataset
   */
  def transform[A, B: DataEncoder](
    dataset: Dataset[A],
    transformation: A => F[B]
  ): F[Dataset[B]]

  /**
   * Apply multiple transformations in sequence
   */
  def transformPipeline[A, B: DataEncoder](
    dataset: Dataset[A],
    transformations: NonEmptyList[A => F[B]]
  ): F[Dataset[B]]

  /**
   * Filter data based on predicate
   */
  def filter[A](dataset: Dataset[A], predicate: A => Boolean): F[Dataset[A]]

  /**
   * Map over dataset with effect support
   */
  def mapWithEffect[A, B: DataEncoder](
    dataset: Dataset[A],
    f: A => F[B]
  ): F[Dataset[B]]

  /**
   * FlatMap over dataset for nested operations
   */
  def flatMapWithEffect[A, B: DataEncoder](
    dataset: Dataset[A],
    f: A => F[Dataset[B]]
  ): F[Dataset[B]]

  /**
   * Group by key with aggregation
   */
  def groupBy[A, K, V: DataEncoder](
    dataset: Dataset[A],
    keyExtractor: A => K,
    aggregator: List[A] => V
  ): F[Dataset[(K, V)]]

  /**
   * Join two datasets
   */
  def join[A, B, K, C: DataEncoder](
    left: Dataset[A],
    right: Dataset[B],
    leftKey: A => K,
    rightKey: B => K,
    combiner: (A, B) => C
  ): F[Dataset[C]]

  // ===============================
  // DATA QUALITY OPERATIONS
  // ===============================

  /**
   * Validate dataset against data contract
   */
  def validate[A](dataset: Dataset[A], contract: DataContract[A]): F[QualityResult[Dataset[A]]]

  /**
   * Run specific quality checks
   */
  def runQualityChecks[A](
    dataset: Dataset[A],
    checks: NonEmptyList[QualityCheck[A]]
  ): F[List[QualityCheckResult]]

  /**
   * Profile dataset to understand data characteristics
   */
  def profile[A](dataset: Dataset[A]): F[DataProfile[A]]

  /**
   * Clean dataset based on quality rules
   */
  def clean[A](
    dataset: Dataset[A],
    cleaningRules: List[CleaningRule[A]]
  ): F[Dataset[A]]

  /**
   * Detect anomalies in dataset
   */
  def detectAnomalies[A](
    dataset: Dataset[A],
    detectors: List[AnomalyDetector[A]]
  ): F[AnomalyReport[A]]

  // ===============================
  // SCHEMA OPERATIONS
  // ===============================

  /**
   * Extract schema from dataset
   */
  def extractSchema[A](dataset: Dataset[A]): F[DataSchema]

  /**
   * Evolve schema with migrations
   */
  def evolveSchema[A, B: DataEncoder](
    dataset: Dataset[A],
    migration: SchemaMigration[A, B]
  ): F[Dataset[B]]

  /**
   * Compare schemas for compatibility
   */
  def compareSchemas(
    source: DataSchema,
    target: DataSchema
  ): F[SchemaCompatibilityReport]

  /**
   * Validate schema compliance
   */
  def validateSchema[A](
    dataset: Dataset[A],
    schema: DataSchema
  ): F[ValidatedNel[FlowForgeError, Dataset[A]]]

  // ===============================
  // DATA SINK OPERATIONS
  // ===============================

  /**
   * Write dataset to sink
   */
  def write[A: DataEncoder](dataset: Dataset[A], sink: DataSink): F[WriteResult]

  /**
   * Write with options (partitioning, compression, etc.)
   */
  def writeWithOptions[A: DataEncoder](
    dataset: Dataset[A],
    sink: DataSink,
    options: WriteOptions
  ): F[WriteResult]

  /**
   * Stream write for large datasets
   */
  def writeStream[A: DataEncoder](
    stream: DataStream[F, A],
    sink: DataSink
  ): F[WriteResult]

  /**
   * Batch write with configurable size
   */
  def writeBatch[A: DataEncoder](
    datasets: List[Dataset[A]],
    sink: DataSink
  ): F[List[WriteResult]]

  // ===============================
  // METADATA & LINEAGE OPERATIONS
  // ===============================

  /**
   * Extract metadata from dataset
   */
  def extractMetadata[A](dataset: Dataset[A]): F[DatasetMetadata]

  /**
   * Track data lineage
   */
  def trackLineage[A](
    dataset: Dataset[A],
    operation: DataOperation,
    context: LineageContext
  ): F[LineageRecord]

  /**
   * Query lineage information
   */
  def queryLineage(
    datasetId: String,
    query: LineageQuery
  ): F[List[LineageRecord]]

  // ===============================
  // UTILITY OPERATIONS
  // ===============================

  /**
   * Count records in dataset
   */
  def count[A](dataset: Dataset[A]): F[Long]

  /**
   * Check if dataset is empty
   */
  def isEmpty[A](dataset: Dataset[A]): F[Boolean]

  /**
   * Take first N records
   */
  def take[A](dataset: Dataset[A], n: Int): F[Dataset[A]]

  /**
   * Sample dataset
   */
  def sample[A](dataset: Dataset[A], fraction: Double): F[Dataset[A]]

  /**
   * Cache dataset in memory/disk
   */
  def cache[A](dataset: Dataset[A], strategy: CacheStrategy): F[Dataset[A]]

  /**
   * Partition dataset
   */
  def partition[A](
    dataset: Dataset[A],
    partitioner: Partitioner[A]
  ): F[Map[String, Dataset[A]]]
}

/**
 * Type classes and supporting types for DataAlgebra. These integrate with the existing FlowForge
 * type system.
 */
object DataAlgebra {

  // ===============================
  // TYPE CLASSES (aligned with existing system)
  // ===============================

  /**
   * Type class for data encoding
   */
  trait DataEncoder[A] {
    def encode(value: A): EncodedData
    def schema: DataSchema
  }

  /**
   * Type class for data decoding
   */
  trait DataDecoder[A] {
    def decode(data: EncodedData): Either[FlowForgeError, A]
    def expectedSchema: DataSchema
  }

  /**
   * Type class for schema validation
   */
  trait SchemaValidator[A] {
    def validate(data: A, schema: DataSchema): ValidatedNel[FlowForgeError, A]
  }

  // ===============================
  // SUPPORTING DATA TYPES
  // ===============================

  /**
   * Represents encoded data (binary format)
   */
  case class EncodedData(bytes: Array[Byte]) extends AnyVal

  /**
   * Represents a dataset with type information
   */
  case class Dataset[A](
    id: String,
    data: List[A],
    schema: DataSchema,
    metadata: DatasetMetadata,
    lineage: Option[LineageRecord] = None
  ) {
    def map[B: DataEncoder](f: A => B): Dataset[B] =
      Dataset[B](
        id = s"${id}_mapped",
        data = data.map(f),
        schema = implicitly[DataEncoder[B]].schema,
        metadata = metadata,
        lineage = lineage
      )

    def filter(predicate: A => Boolean): Dataset[A] =
      copy(data = data.filter(predicate))

    def size: Int         = data.size
    def isEmpty: Boolean  = data.isEmpty
    def nonEmpty: Boolean = data.nonEmpty
  }

  /**
   * Streaming dataset abstraction
   */
  trait DataStream[F[_], A] {
    def chunks: F[List[Dataset[A]]]
    def process[B: DataEncoder](f: A => F[B]): F[DataStream[F, B]]
    def filter(predicate: A => Boolean): F[DataStream[F, A]]
  }

  // ===============================
  // OPERATION RESULTS
  // ===============================

  case class WriteResult(
    recordsWritten: Long,
    bytesWritten: Long,
    partitions: List[String],
    duration: Long,
    success: Boolean,
    errors: List[FlowForgeError]
  )

  case class DataProfile[A](
    recordCount: Long,
    nullCounts: Map[String, Long],
    uniqueCounts: Map[String, Long],
    dataTypes: Map[String, String],
    statistics: Map[String, Double],
    schema: DataSchema
  )

  case class AnomalyReport[A](
    datasetId: String,
    anomalies: List[Anomaly[A]],
    totalRecords: Long,
    anomalyRate: Double,
    detectionTime: Instant
  )

  case class Anomaly[A](
    record: A,
    detectorType: String,
    severity: AnomalySeverity,
    score: Double,
    description: String
  )

  sealed trait AnomalySeverity
  object AnomalySeverity {
    case object Low      extends AnomalySeverity
    case object Medium   extends AnomalySeverity
    case object High     extends AnomalySeverity
    case object Critical extends AnomalySeverity
  }

  // ===============================
  // CONFIGURATION TYPES
  // ===============================

  case class WriteOptions(
    mode: WriteMode,
    partitionColumns: List[String] = List.empty,
    sortColumns: List[String] = List.empty,
    compression: CompressionType = CompressionType.None,
    format: DataFormat = DataFormat.Parquet
  )

  sealed trait WriteMode
  object WriteMode {
    case object Append    extends WriteMode
    case object Overwrite extends WriteMode
    case object Upsert    extends WriteMode
  }

  sealed trait CompressionType
  object CompressionType {
    case object None   extends CompressionType
    case object Gzip   extends CompressionType
    case object Snappy extends CompressionType
    case object LZ4    extends CompressionType
  }

  sealed trait CacheStrategy
  object CacheStrategy {
    case object Memory        extends CacheStrategy
    case object Disk          extends CacheStrategy
    case object MemoryAndDisk extends CacheStrategy
    case object None          extends CacheStrategy
  }

  // ===============================
  // LINEAGE & METADATA
  // ===============================

  case class LineageRecord(
    id: String,
    datasetId: String,
    operation: DataOperation,
    inputs: List[String],
    outputs: List[String],
    timestamp: Instant,
    context: LineageContext
  )

  case class LineageContext(
    pipelineId: String,
    executionId: String,
    userId: String,
    environment: String
  )

  sealed trait DataOperation
  object DataOperation {
    case object Read      extends DataOperation
    case object Transform extends DataOperation
    case object Filter    extends DataOperation
    case object Join      extends DataOperation
    case object Aggregate extends DataOperation
    case object Write     extends DataOperation
    case object Validate  extends DataOperation
  }

  case class DatasetMetadata(
    name: String,
    description: Option[String],
    tags: Set[String],
    owner: String,
    createdAt: Instant,
    updatedAt: Instant,
    version: String,
    size: Long,
    format: DataFormat
  )

  case class QualityResult[A](
    data: A,
    score: Double,
    checks: List[QualityCheckResult],
    passed: Boolean,
    metadata: Map[String, Any]
  )

  case class QualityCheckResult(
    checkType: String,
    passed: Boolean,
    score: Double,
    message: String,
    details: Map[String, Any]
  )

  case class LineageQuery(
    fromTime: Option[Instant],
    toTime: Option[Instant],
    operations: List[DataOperation],
    maxDepth: Int = 10
  )

  case class SchemaCompatibilityReport(
    compatible: Boolean,
    issues: List[String],
    recommendations: List[String]
  )

  case class SchemaMigration[A, B](
    name: String,
    migrate: A => B,
    rollback: B => A
  )

  case class CleaningRule[A](
    name: String,
    apply: A => A,
    condition: A => Boolean
  )

  case class AnomalyDetector[A](
    name: String,
    detect: A => Double // Returns anomaly score 0.0 to 1.0
  )

  case class Partitioner[A](
    partitionBy: A => String
  )

  // ===============================
  // SMART CONSTRUCTORS & UTILITIES
  // ===============================

  object Dataset {
    def empty[A: DataEncoder]: Dataset[A] = Dataset[A](
      id = java.util.UUID.randomUUID().toString,
      data = List.empty,
      schema = implicitly[DataEncoder[A]].schema,
      metadata = DatasetMetadata(
        name = "empty",
        description = None,
        tags = Set.empty,
        owner = "system",
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
        version = "1.0.0",
        size = 0L,
        format = DataFormat.Parquet
      ),
      lineage = None
    )

    def fromList[A: DataEncoder](data: List[A], name: String = "dataset"): Dataset[A] =
      Dataset[A](
        id = java.util.UUID.randomUUID().toString,
        data = data,
        schema = implicitly[DataEncoder[A]].schema,
        metadata = DatasetMetadata(
          name = name,
          description = None,
          tags = Set.empty,
          owner = "system",
          createdAt = Instant.now(),
          updatedAt = Instant.now(),
          version = "1.0.0",
          size = data.size.toLong,
          format = DataFormat.Parquet
        ),
        lineage = None
      )
  }

  // ===============================
  // KLEISLI INTEGRATION HELPERS
  // ===============================

  /**
   * Helper functions for creating Kleisli-based data operations that integrate with the existing
   * FlowForge pipeline system
   */
  object Operations {

    /**
     * Create a data transformation as Kleisli arrow
     */
    def transform[F[_]: Monad, A, B: DataEncoder](
      f: A => B
    ): Kleisli[F, Dataset[A], Dataset[B]] =
      Kleisli { dataset =>
        Monad[F].pure(dataset.map(f))
      }

    /**
     * Create a filtering operation as Kleisli arrow
     */
    def filter[F[_]: Monad, A](
      predicate: A => Boolean
    ): Kleisli[F, Dataset[A], Dataset[A]] =
      Kleisli { dataset =>
        Monad[F].pure(dataset.filter(predicate))
      }

    /**
     * Create a validation operation as Kleisli arrow
     */
    def validate[F[_]: Monad, A](
      contract: DataContract[A]
    )(implicit algebra: DataAlgebra[F]): Kleisli[F, Dataset[A], QualityResult[Dataset[A]]] =
      Kleisli { dataset =>
        algebra.validate(dataset, contract)
      }

    /**
     * Compose multiple transformations
     */
    def compose[F[_]: Monad, A, B, C](
      first: Kleisli[F, A, B],
      second: Kleisli[F, B, C]
    ): Kleisli[F, A, C] =
      first andThen second
  }

  // ===============================
  // TYPE CLASS INSTANCES
  // ===============================

  implicit def datasetFunctor: Functor[Dataset] = new Functor[Dataset] {
    def map[A, B](fa: Dataset[A])(f: A => B): Dataset[B] =
      fa.map(f)(DataEncoder.anyDataEncoder)
  }

  // Provide basic DataEncoder instances for common types
  object DataEncoder {
    implicit def anyDataEncoder[A]: DataEncoder[A] = new DataEncoder[A] {
      def encode(value: A): EncodedData = EncodedData(value.toString.getBytes)
      def schema: DataSchema            = DataSchema.builder.build
    }

    implicit val stringDataEncoder: DataEncoder[String] = new DataEncoder[String] {
      def encode(value: String): EncodedData = EncodedData(value.getBytes)
      def schema: DataSchema                 = DataSchema.builder.build
    }

    implicit val intDataEncoder: DataEncoder[Int] = new DataEncoder[Int] {
      def encode(value: Int): EncodedData = EncodedData(value.toString.getBytes)
      def schema: DataSchema              = DataSchema.builder.build
    }

    implicit val longDataEncoder: DataEncoder[Long] = new DataEncoder[Long] {
      def encode(value: Long): EncodedData = EncodedData(value.toString.getBytes)
      def schema: DataSchema              = DataSchema.builder.build
    }
  }

  object DataDecoder {
    implicit def anyDataDecoder[A]: DataDecoder[A] = new DataDecoder[A] {
      def decode(data: EncodedData): Either[FlowForgeError, A] =
        Left(
          DataProcessingError.ProcessingFailure(
            "Generic decoder not implemented",
            s"Generic decoder cannot decode to specific type A"
          )
        )
      def expectedSchema: DataSchema = DataSchema.builder.build
    }

    implicit val stringDataDecoder: DataDecoder[String] = new DataDecoder[String] {
      def decode(data: EncodedData): Either[FlowForgeError, String] =
        Right(new String(data.bytes))
      def expectedSchema: DataSchema = DataSchema.builder.build
    }
  }
}
