package com.flowforge.core.algebra

import cats.data.{ NonEmptyList, ValidatedNel }
// Avoid name clash with the type-class-style DataContract defined in this package
import com.flowforge.core.types.PipelineTypes.{ DataContract => PDataContract, QualityCheck => PQualityCheck }
import com.flowforge.core.types.RefinedTypes.FieldName
import com.flowforge.core.types._
import eu.timepit.refined.types.string.NonEmptyString

import java.time.Instant
import scala.concurrent.duration.FiniteDuration

/**
 * 🚀 **FlowForge Data Algebra - Effect System Research Applied**
 *
 * CRITICAL ARCHITECTURAL CHANGE: This implementation applies the Effect System research findings.
 *
 * **Effect Usage Separation (Non-negotiable):**
 *   1. **Pure Data Transformations**: Return Dataset[A] directly, no F[_] wrapper 2. **External IO
 *      Operations**: Use F[_] with proper resource management 3. **Pipeline Orchestration**: Use F[_] for
 *      composing heterogeneous systems 4. **Configuration/Metadata**: Use F[_] for schema registry, config
 *      loading, audit logging
 *
 * **Key Design Principles:**
 *   - **Effect Polymorphism**: F[_] only where external effects are needed
 *   - **Type Safety**: All operations are statically typed
 *   - **Composability**: Operations compose via Kleisli arrows for external operations
 *   - **Resource Safety**: Automatic resource management via Resource[F, _] for IO
 *   - **Error Handling**: ValidatedNel for multi-error scenarios
 *   - **Spark Compliance**: Pure Spark operations are direct Dataset operations
 *
 * @author
 *   FlowForge Core Team
 * @since 0.1.0
 */
trait DataAlgebra[F[_]] extends CDCOperations[F] with TableOperations[F] {

  // Import companion object types
  import DataAlgebra._

  // ===============================
  // EXTERNAL IO OPERATIONS (F[_] Required)
  // ===============================

  /**
   * Read data from external source with resource management. Uses F[_] because it involves external IO (JDBC,
   * file system, network).
   */
  def read[A: DataDecoder](source: DataSource): F[Dataset[A]]

  /**
   * Read data with schema validation from external source. Uses F[_] for IO and ValidatedNel for multi-error
   * validation.
   */
  def readWithSchema[A: DataDecoder](
    source: DataSource,
    expectedSchema: DataSchema,
  ): F[ValidatedNel[FlowForgeError, Dataset[A]]]

  /**
   * Stream data for large external datasets. Uses F[_] because streaming involves external resource
   * management.
   */
  def stream[A: DataDecoder](source: DataSource): F[DataStream[F, A]]

  /**
   * Write data to external sink with resource management. Uses F[_] because it involves external IO and
   * resource cleanup.
   */
  def write[A: DataEncoder](
    dataset: Dataset[A],
    sink: DataSink,
    options: WriteOptions = WriteOptions.default,
  ): F[WriteResult]

  /**
   * Write data with validation to external sink. Uses F[_] for IO operations and validation.
   */
  def writeWithValidation[A: DataEncoder](
    dataset: Dataset[A],
    sink: DataSink,
    contract: PDataContract[A],
    options: WriteOptions = WriteOptions.default,
  ): F[ValidatedNel[FlowForgeError, WriteResult]]

  // ===============================
  // PURE DATA TRANSFORMATIONS (No F[_] - Direct Operations)
  // ===============================

  /**
   * Filter data based on predicate. PURE OPERATION: No F[_] wrapper - direct Dataset transformation.
   */
  def filter[A](dataset: Dataset[A], predicate: A => Boolean): Dataset[A]

  /**
   * Map over dataset with pure function. PURE OPERATION: No F[_] wrapper - direct Dataset transformation.
   */
  def map[A, B: DataEncoder](dataset: Dataset[A], f: A => B): Dataset[B]

  /**
   * FlatMap over dataset for pure nested operations. PURE OPERATION: No F[_] wrapper - direct Dataset
   * transformation.
   */
  def flatMap[A, B: DataEncoder](dataset: Dataset[A], f: A => Dataset[B]): Dataset[B]

  /**
   * Group by key with pure aggregation. PURE OPERATION: No F[_] wrapper - direct Dataset transformation.
   */
  def groupBy[A, K, V: DataEncoder](
    dataset: Dataset[A],
    keyExtractor: A => K,
    aggregator: List[A] => V,
  ): Dataset[(K, V)]

  /**
   * Join two datasets with pure combination function. PURE OPERATION: No F[_] wrapper - direct Dataset
   * transformation.
   */
  def join[A, B, K, C: DataEncoder](
    left: Dataset[A],
    right: Dataset[B],
    leftKey: A => K,
    rightKey: B => K,
    combiner: (A, B) => C,
  ): Dataset[C]

  /**
   * Union two datasets. PURE OPERATION: No F[_] wrapper - direct Dataset transformation.
   */
  def union[A](left: Dataset[A], right: Dataset[A]): Dataset[A]

  /**
   * Sort dataset by key. PURE OPERATION: No F[_] wrapper - direct Dataset transformation.
   */
  def sortBy[A, K: Ordering](dataset: Dataset[A], keyExtractor: A => K): Dataset[A]

  /**
   * Take first N elements. PURE OPERATION: No F[_] wrapper - direct Dataset transformation.
   */
  def take[A](dataset: Dataset[A], n: Int): Dataset[A]

  /**
   * Drop first N elements. PURE OPERATION: No F[_] wrapper - direct Dataset transformation.
   */
  def drop[A](dataset: Dataset[A], n: Int): Dataset[A]

  // ===============================
  // EFFECTFUL TRANSFORMATIONS (F[_] Required)
  // ===============================

  /**
   * Transform with effectful function (e.g., external API calls). Uses F[_] because transformation involves
   * external effects.
   */
  def transformWithEffect[A, B: DataEncoder](
    dataset: Dataset[A],
    transformation: A => F[B],
  ): F[Dataset[B]]

  /**
   * Apply multiple effectful transformations in sequence. Uses F[_] because transformations involve external
   * effects.
   */
  def transformPipeline[A, B: DataEncoder](
    dataset: Dataset[A],
    transformations: NonEmptyList[A => F[B]],
  ): F[Dataset[B]]

  // ===============================
  // METADATA & CONFIGURATION OPERATIONS (F[_] Required)
  // ===============================

  /**
   * Extract schema from dataset with metadata service calls. Uses F[_] because it may involve external schema
   * registry.
   */
  def extractSchema[A](dataset: Dataset[A]): F[DataSchema]

  /**
   * Evolve schema with migrations from external registry. Uses F[_] because it involves external schema
   * service.
   */
  def evolveSchema[A, B: DataEncoder](
    dataset: Dataset[A],
    migration: SchemaMigration[A, B],
  ): F[Dataset[B]]

  /**
   * Compare schemas using external compatibility service. Uses F[_] because it may involve external schema
   * service.
   */
  def compareSchemas(
    schema1: DataSchema,
    schema2: DataSchema,
  ): F[SchemaCompatibilityReport]

  // ===============================
  // AUDIT & LINEAGE OPERATIONS (F[_] Required)
  // ===============================

  /**
   * Record lineage information to external tracking system. Uses F[_] because it involves external
   * audit/lineage service.
   */
  def recordLineage[A](
    dataset: Dataset[A],
    operation: String,
    context: LineageContext,
  ): F[LineageRecord]

  /**
   * Query lineage from external tracking system. Uses F[_] because it involves external lineage service.
   */
  def queryLineage(query: LineageQuery): F[List[LineageRecord]]

  // ===============================
  // DATA QUALITY OPERATIONS (F[_] for External Validation Services)
  // ===============================

  /**
   * Validate dataset against external data contract service. Uses F[_] because it may involve external
   * validation service.
   */
  def validate[A](dataset: Dataset[A], contract: PDataContract[A]): F[QualityResult[Dataset[A]]]

  /**
   * Run quality checks that may involve external services. Uses F[_] because checks may involve external
   * quality services.
   */
  def runQualityChecks[A](
    dataset: Dataset[A],
    checks: NonEmptyList[PQualityCheck[A]],
  ): F[List[QualityCheckResult]]

  /**
   * Profile dataset with external profiling service. Uses F[_] because profiling may involve external
   * analytics service.
   */
  def profile[A](dataset: Dataset[A]): F[DataProfile[A]]

  // ===============================
  // UTILITY OPERATIONS (Pure where possible)
  // ===============================

  /**
   * Count records in dataset. PURE OPERATION: No F[_] wrapper - direct Dataset operation.
   */
  def count[A](dataset: Dataset[A]): Long

  /**
   * Check if dataset is empty. PURE OPERATION: No F[_] wrapper - direct Dataset operation.
   */
  def isEmpty[A](dataset: Dataset[A]): Boolean

  /**
   * Cache dataset for reuse. Uses F[_] because caching involves resource management.
   */
  def cache[A](dataset: Dataset[A], strategy: CacheStrategy): F[Dataset[A]]

  /**
   * Partition dataset for parallel processing. PURE OPERATION: No F[_] wrapper - direct Dataset
   * transformation.
   */
  def partition[A](dataset: Dataset[A], partitioner: Partitioner[A]): List[Dataset[A]]
}

// ===============================
// DATA TYPES AND SUPPORTING STRUCTURES
// ===============================

object DataAlgebra {

  /**
   * Generic dataset abstraction.
   */
  trait Dataset[A] {
    def data: List[A]
    def schema: DataSchema
    def metadata: DatasetMetadata

    // Back-compat convenience
    def size: Int        = data.size
    def isEmpty: Boolean = data.isEmpty
  }

  /**
   * Data stream for large datasets.
   */
  trait DataStream[F[_], A] {
    def chunks: F[List[Dataset[A]]]
  }

  /**
   * Dataset metadata.
   */
  case class DatasetMetadata(
    recordCount: Long,
    schema: DataSchema,
    partitions: Int,
    createdAt: Instant,
    source: Option[DataSource] = None)

  /**
   * Write options for external sinks.
   */
  case class WriteOptions(
    mode: WriteMode = WriteMode.Append,
    format: DataFormat = DataFormat.Parquet,
    partitionBy: List[FieldName] = List.empty,
    compression: Option[String] = None)

  object WriteOptions {
    val default: WriteOptions = WriteOptions()
  }

  /**
   * Write modes for external sinks.
   */
  sealed trait WriteMode
  object WriteMode {
    case object Append        extends WriteMode
    case object Overwrite     extends WriteMode
    case object ErrorIfExists extends WriteMode
    case object Ignore        extends WriteMode
  }

  /**
   * Write result from external operations.
   */
  case class WriteResult(
    recordsWritten: Long,
    partitionsWritten: Int,
    bytesWritten: Long,
    success: Boolean,
    errors: List[FlowForgeError] = List.empty)

  /**
   * Quality result wrapper.
   */
  case class QualityResult[A](
    data: A,
    passed: Boolean,
    violations: List[QualityViolation],
    score: Double)

  /**
   * Quality check result.
   */
  case class QualityCheckResult(
    checkName: String,
    passed: Boolean,
    message: String,
    score: Double)

  /**
   * Data profile for understanding dataset characteristics.
   */
  case class DataProfile[A](
    recordCount: Long,
    nullCount: Long,
    distinctCount: Long,
    schema: DataSchema,
    statistics: Map[String, Any])

  /**
   * Schema migration for evolution.
   */
  trait SchemaMigration[A, B] {
    def migrate(data: A): B
    def sourceSchema: DataSchema
    def targetSchema: DataSchema
  }

  /**
   * Schema compatibility report.
   */
  case class SchemaCompatibilityReport(
    compatible: Boolean,
    changes: List[SchemaChange],
    breakingChanges: List[SchemaChange])

  sealed trait SchemaChange
  object SchemaChange {
    case class FieldAdded(name: String)   extends SchemaChange
    case class FieldRemoved(name: String) extends SchemaChange
    case class FieldTypeChanged(
      name: String,
      oldType: String,
      newType: String)
        extends SchemaChange
  }

  /**
   * Lineage context for tracking data flow.
   */
  case class LineageContext(
    pipelineName: String,
    jobId: String,
    timestamp: Instant,
    user: String,
    tags: Map[String, String] = Map.empty)

  /**
   * Lineage record for audit trail.
   */
  case class LineageRecord(
    id: String,
    source: DataSource,
    target: Option[DataSink],
    operation: String,
    context: LineageContext,
    inputSchemas: List[DataSchema],
    outputSchema: Option[DataSchema])

  /**
   * Lineage query for searching audit trail.
   */
  case class LineageQuery(
    sources: List[DataSource] = List.empty,
    targets: List[DataSink] = List.empty,
    operations: List[String] = List.empty,
    timeRange: Option[(Instant, Instant)] = None)

  /**
   * Cache strategy for dataset optimization.
   */
  sealed trait CacheStrategy
  object CacheStrategy {
    case object Memory        extends CacheStrategy
    case object Disk          extends CacheStrategy
    case object MemoryAndDisk extends CacheStrategy
  }

  /**
   * Partitioner for parallel processing.
   */
  trait Partitioner[A] {
    def partition(data: A): Int
    def numPartitions: Int
  }

  /**
   * Quality violation for validation results.
   */
  case class QualityViolation(
    rule: String,
    message: String,
    severity: ViolationSeverity,
    recordsAffected: Long)

  sealed trait ViolationSeverity
  object ViolationSeverity {
    case object Critical extends ViolationSeverity
    case object Warning  extends ViolationSeverity
    case object Info     extends ViolationSeverity
  }
}

// ===============================
// CDC OPERATIONS MIXIN (External Effects)
// ===============================

/**
 * Change Data Capture operations mixin for DataAlgebra. Uses F[_] because CDC involves external systems and
 * state management.
 */
trait CDCOperations[F[_]] {
  self: DataAlgebra[F] =>

  import CDCOperations._

  /**
   * Perform delta/incremental processing between source and target. Uses F[_] because it involves external
   * system comparison.
   */
  def performDelta[A: DataContract](
    source: DataAlgebra.Dataset[A],
    target: DataAlgebra.Dataset[A],
    config: CDCConfig,
  ): F[CDCResult[A]]

  /**
   * Compute CDC operations (insert, update, delete) for synchronization. Uses F[_] because it may involve
   * external metadata services.
   */
  def computeCDCOperations[A](
    source: DataAlgebra.Dataset[A],
    target: DataAlgebra.Dataset[A],
    keyColumns: NonEmptyList[FieldName],
  ): F[CDCOperations.CDCOperationSet[A]]

  /**
   * Apply CDC operations to target system. Uses F[_] because it involves external system modification.
   */
  def applyCDCOperations[A](
    operations: CDCOperations.CDCOperationSet[A],
    target: DataSink,
  ): F[CDCResult[A]]
}

object CDCOperations {

  /**
   * CDC configuration for delta processing.
   */
  case class CDCConfig(
    keyColumns: NonEmptyList[FieldName],
    timestampColumn: Option[FieldName] = None,
    deleteDetection: Boolean = true,
    batchSize: Int = 10000,
    scd2: Option[SCD2Columns] = None,
    partition: Option[PartitionStrategy] = None,
    hashColumns: Option[NonEmptyList[FieldName]] = None,
    optimizeAfterMerge: Boolean = false,
    zOrderBy: Option[NonEmptyList[FieldName]] = None)

  /**
   * SCD2 column names to support non-standard conventions.
   */
  case class SCD2Columns(
    effectiveFrom: FieldName,
    effectiveTo: FieldName,
    isCurrent: FieldName)

  /**
   * Optional writer partitioning strategy for sink tables.
   */
  case class PartitionStrategy(
    partitionBy: List[FieldName] = Nil)

  /**
   * CDC result with processing statistics.
   */
  case class CDCResult[A](
    inserted: Long,
    updated: Long,
    deleted: Long,
    unchanged: Long,
    errors: Long,
    processingTime: FiniteDuration,
    success: Boolean)

  /**
   * Set of CDC operations to apply.
   */
  case class CDCOperationSet[A](
    inserts: List[A],
    updates: List[A],
    deletes: List[A])
}

// ===============================
// TABLE OPERATIONS MIXIN (External Effects)
// ===============================

/**
 * Table management operations mixin for DataAlgebra. Uses F[_] because table operations involve external
 * metadata systems.
 */
trait TableOperations[F[_]] {
  self: DataAlgebra[F] =>

  import TableOperations._

  /**
   * Repair and refresh table metadata. Uses F[_] because it involves external metadata system operations.
   */
  def repairRefreshTable(table: TableName): F[TableOperationResult]

  /**
   * Get table location with validation. Uses F[_] because it involves external metadata lookup.
   */
  def getTableLocation(table: TableName): F[ValidatedNel[FlowForgeError, String]]

  /**
   * Get affected partitions for time range. Uses F[_] because it involves external metadata queries.
   */
  def getAffectedPartitions(
    table: TableName,
    startTime: Instant,
    endTime: Instant,
  ): F[List[PartitionSpec]]

  /**
   * Safe deletion of table location with external filesystem operations. Uses F[_] because it involves
   * external filesystem operations.
   */
  def deleteDfsLocation(
    location: String,
    dryRun: Boolean = true,
  ): F[TableOperationResult]

  /**
   * Analyze table and compute statistics. Uses F[_] because it involves external metadata system updates.
   */
  def analyzeTable(
    table: TableName,
    partitions: Option[NonEmptyList[PartitionSpec]] = None,
  ): F[TableOperationResult]

  /**
   * Vacuum table to optimize storage. Uses F[_] because it involves external storage system operations.
   */
  def vacuumTable(
    table: TableName,
    retentionHours: Int = 168,
    dryRun: Boolean = true,
  ): F[TableOperationResult]
}

object TableOperations {

  /**
   * Table name with validation.
   */
  case class TableName(
    database: NonEmptyString,
    table: NonEmptyString) {
    def qualified: String = s"${database.value}.${table.value}"
  }

  /**
   * Partition specification.
   */
  case class PartitionSpec(
    columns: NonEmptyList[FieldName],
    values: NonEmptyList[String])

  /**
   * Table operation result.
   */
  case class TableOperationResult(
    tableName: TableName,
    operation: String,
    success: Boolean,
    affectedPartitions: List[PartitionSpec] = List.empty,
    recordsProcessed: Long = 0,
    processingTime: FiniteDuration,
    errors: List[FlowForgeError] = List.empty)
}
