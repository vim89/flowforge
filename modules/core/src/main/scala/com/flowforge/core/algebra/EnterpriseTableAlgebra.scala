/**
 * FlowForge Core Module - Enterprise Table Operations Algebra
 *
 * File: modules/core/src/main/scala/com/flowforge/core/algebra/EnterpriseTableAlgebra.scala
 * Package: com.flowforge.core.algebra
 *
 * Enterprise-grade table management operations enhanced with FlowForge's functional principles.
 * Integrates reference-utilities Table.scala patterns with effect polymorphism and type safety.
 *
 * Design Patterns Applied:
 *   - Tagless Final Pattern: Effect abstraction for table operations
 *   - Resource Pattern: Safe table connection and cleanup management
 *   - Strategy Pattern: Different table operation strategies per engine
 *   - Command Pattern: Table operations as composable, reusable commands
 *   - Observer Pattern: Table state change notifications
 *
 * Scala Features Showcased:
 *   - Higher-Kinded Types: F[_] abstraction over effect containers
 *   - Resource Management: Bracket patterns for connection safety
 *   - Type Classes: Polymorphic operations across table types
 *   - Refined Types: Compile-time validation of table names and paths
 *   - ValidatedNel: Accumulative error handling for table operations
 *   - Kleisli Arrows: Composable table transformations
 *
 * Reference Integration:
 *   - Based on reference-utilities Table.scala analysis
 *   - Enhanced repair and refresh operations with effect safety
 *   - Partition management with functional composition
 *   - GCS blob operations with resource management
 *   - Enterprise table optimization strategies
 *
 * @author FlowForge Team
 * @version 1.0.0
 * @since 2024
 */
package com.flowforge.core.algebra

import cats.data.{NonEmptyList, ValidatedNel}
import cats.effect.{Resource, MonadCancel}
import cats.implicits._
import com.flowforge.core.types.RefinedTypes.{FieldName, TableName}
import com.flowforge.core.types.{ErrorCategory, ErrorSeverity, FlowForgeError}

import java.time.Instant
import scala.concurrent.duration.FiniteDuration

/**
 * Enterprise table operations algebra with effect polymorphism.
 * Enhanced version of reference-utilities Table.scala patterns.
 */
trait EnterpriseTableAlgebra[F[_]] {

  // ===============================
  // TABLE MANAGEMENT OPERATIONS
  // ===============================

  /**
   * Repair and refresh table metadata.
   * Enhanced version of reference Table.repairRefreshTable with effect safety.
   *
   * @param tableName Table to repair and refresh
   * @return Operation result with detailed metrics
   */
  def repairRefreshTable(tableName: TableName): F[TableOperationResult]

  /**
   * Get table physical location on distributed file system.
   * Enhanced version of reference Table.getTableLocation with validation.
   *
   * @param tableName Table name to locate
   * @return Validated table location or errors
   */
  def getTableLocation(tableName: TableName): F[ValidatedNel[TableError, TableLocation]]

  /**
   * Delete table data from distributed file system.
   * Enhanced version of reference Table.deleteDfsLocation with safety checks.
   *
   * @param location DFS location to delete
   * @return Deletion result with safety confirmations
   */
  def deleteDfsLocation(location: TableLocation): F[DeletionResult]

  /**
   * Optimize table storage and performance.
   * Advanced optimization strategies for different table types.
   *
   * @param tableName Table to optimize
   * @param strategy Optimization strategy to apply
   * @return Optimization result with performance metrics
   */
  def optimizeTable(
    tableName: TableName,
    strategy: OptimizationStrategy
  ): F[OptimizationResult]

  /**
   * Vacuum table to remove old versions and compact storage.
   * Resource-safe vacuum operations with configurable retention.
   *
   * @param tableName Table to vacuum
   * @param retentionPolicy Retention policy for old versions
   * @return Vacuum result with space reclaimed metrics
   */
  def vacuumTable(
    tableName: TableName,
    retentionPolicy: RetentionPolicy
  ): F[VacuumResult]

  // ===============================
  // PARTITION MANAGEMENT OPERATIONS
  // ===============================

  /**
   * Get affected partitions based on timestamp criteria.
   * Enhanced version of reference Table.getAffectedPartitions with type safety.
   *
   * @param tableName Source table name
   * @param sinceTimestamp Timestamp threshold for affected partitions
   * @param regionFilter Optional region filter for geo-partitioned tables
   * @return List of affected partitions with metadata
   */
  def getAffectedPartitions(
    tableName: TableName,
    sinceTimestamp: Instant,
    regionFilter: Option[String] = None
  ): F[ValidatedNel[PartitionError, List[PartitionInfo]]]

  /**
   * Drop specific table partitions safely.
   * Enhanced version with validation and safety checks.
   *
   * @param tableName Table name
   * @param partitions Partitions to drop
   * @return Drop operation results
   */
  def dropPartitions(
    tableName: TableName,
    partitions: NonEmptyList[PartitionSpec]
  ): F[List[PartitionDropResult]]

  /**
   * Add new partitions to table.
   * Type-safe partition addition with validation.
   *
   * @param tableName Table name
   * @param partitions Partitions to add
   * @return Addition operation results
   */
  def addPartitions(
    tableName: TableName,
    partitions: NonEmptyList[PartitionSpec]
  ): F[List[PartitionAddResult]]

  /**
   * Analyze partition statistics for optimization.
   *
   * @param tableName Table to analyze
   * @return Partition analysis with optimization recommendations
   */
  def analyzePartitions(tableName: TableName): F[PartitionAnalysis]

  // ===============================
  // CLOUD STORAGE INTEGRATION
  // ===============================

  /**
   * Get affected blobs from cloud storage.
   * Enhanced version of reference Table.getAffectedBlobs with resource safety.
   *
   * @param bucketName Cloud storage bucket
   * @param beforeTimestamp Upper bound for blob timestamps
   * @param afterTimestamp Lower bound for blob timestamps
   * @return List of affected blobs with metadata
   */
  def getAffectedBlobs(
    bucketName: String,
    beforeTimestamp: Option[Instant] = None,
    afterTimestamp: Option[Instant] = None
  ): F[ValidatedNel[BlobError, List[BlobInfo]]]

  /**
   * Process blobs concurrently with resource management.
   * Enhanced version with effect-safe concurrent processing.
   *
   * @param blobs Blobs to process
   * @param processor Processing function
   * @param concurrency Maximum concurrent operations
   * @tparam A Processing result type
   * @return Processing results
   */
  def processBlobsConcurrently[A](
    blobs: List[BlobInfo],
    processor: BlobInfo => F[A],
    concurrency: Int = 10
  ): F[List[A]]

  /**
   * Sync table metadata with cloud storage state.
   * Ensures table metadata accurately reflects storage reality.
   *
   * @param tableName Table to sync
   * @return Sync operation result
   */
  def syncTableWithStorage(tableName: TableName): F[SyncResult]

  // ===============================
  // SCHEMA EVOLUTION OPERATIONS
  // ===============================

  /**
   * Evolve table schema with backward compatibility validation.
   *
   * @param tableName Table to evolve
   * @param newSchema New schema definition
   * @param evolutionStrategy Evolution strategy (additive, breaking, etc.)
   * @return Schema evolution result
   */
  def evolveSchema(
    tableName: TableName,
    newSchema: TableSchema,
    evolutionStrategy: SchemaEvolutionStrategy
  ): F[SchemaEvolutionResult]

  /**
   * Validate schema compatibility between versions.
   *
   * @param currentSchema Current table schema
   * @param proposedSchema Proposed new schema
   * @return Compatibility analysis
   */
  def validateSchemaCompatibility(
    currentSchema: TableSchema,
    proposedSchema: TableSchema
  ): F[SchemaCompatibilityResult]

  /**
   * Generate schema migration script.
   *
   * @param fromSchema Source schema
   * @param toSchema Target schema
   * @return Migration script and instructions
   */
  def generateSchemaMigration(
    fromSchema: TableSchema,
    toSchema: TableSchema
  ): F[SchemaMigrationScript]
}

// ===============================
// TABLE OPERATION RESULT TYPES
// ===============================

/**
 * Result of table operation with comprehensive metrics.
 */
case class TableOperationResult(
  tableName: TableName,
  operation: String,
  success: Boolean,
  duration: FiniteDuration,
  recordsAffected: Long,
  partitionsAffected: Int,
  storageUsed: Long,
  errors: List[TableError] = List.empty,
  warnings: List[String] = List.empty,
  metadata: Map[String, String] = Map.empty
)

/**
 * Table location information.
 */
case class TableLocation(
  path: String,
  fileSystem: FileSystemType,
  partitioned: Boolean,
  partitionColumns: List[FieldName] = List.empty
)

/**
 * Partition information with metadata.
 */
case class PartitionInfo(
  partitionSpec: PartitionSpec,
  location: String,
  lastModified: Instant,
  sizeBytes: Long,
  recordCount: Option[Long] = None
)

/**
 * Partition specification.
 */
case class PartitionSpec(
  columns: Map[FieldName, String]
) {
  def toPartitionPath: String =
    columns.map { case (name, value) => s"${name.value}=$value" }.mkString("/")
}

/**
 * Blob information for cloud storage.
 */
case class BlobInfo(
  name: String,
  bucket: String,
  lastModified: Instant,
  sizeBytes: Long,
  contentType: String,
  metadata: Map[String, String] = Map.empty
)

/**
 * Optimization strategies for table operations.
 */
sealed trait OptimizationStrategy
object OptimizationStrategy {
  case object Compact extends OptimizationStrategy
  case object ZOrder extends OptimizationStrategy
  case object Vacuum extends OptimizationStrategy
  case object RepartitionBySize extends OptimizationStrategy
  case object RepartitionByColumn extends OptimizationStrategy
  case class Custom(name: String, parameters: Map[String, String]) extends OptimizationStrategy
}

/**
 * Retention policies for table maintenance.
 */
sealed trait RetentionPolicy
object RetentionPolicy {
  case class TimeBasedRetention(duration: FiniteDuration) extends RetentionPolicy
  case class VersionBasedRetention(versionsToKeep: Int) extends RetentionPolicy
  case class SizeBasedRetention(maxSizeBytes: Long) extends RetentionPolicy
  case class CompositeRetention(policies: NonEmptyList[RetentionPolicy]) extends RetentionPolicy
}

/**
 * Schema evolution strategies.
 */
sealed trait SchemaEvolutionStrategy
object SchemaEvolutionStrategy {
  case object Additive extends SchemaEvolutionStrategy      // Add new columns only
  case object Compatible extends SchemaEvolutionStrategy    // Backward compatible changes
  case object Breaking extends SchemaEvolutionStrategy      // Breaking changes allowed
  case object Strict extends SchemaEvolutionStrategy        // No changes allowed
}

/**
 * File system types supported.
 */
sealed trait FileSystemType
object FileSystemType {
  case object HDFS extends FileSystemType
  case object S3 extends FileSystemType
  case object GCS extends FileSystemType
  case object AzureBlob extends FileSystemType
  case object Local extends FileSystemType
}

// ===============================
// OPERATION RESULT TYPES
// ===============================

case class OptimizationResult(
  tableName: TableName,
  strategy: OptimizationStrategy,
  beforeMetrics: TableMetrics,
  afterMetrics: TableMetrics,
  duration: FiniteDuration,
  spaceReclaimed: Long,
  performanceImprovement: Double
)

case class VacuumResult(
  tableName: TableName,
  retentionPolicy: RetentionPolicy,
  filesDeleted: Long,
  spaceReclaimed: Long,
  duration: FiniteDuration
)

case class DeletionResult(
  location: TableLocation,
  filesDeleted: Long,
  spaceReclaimed: Long,
  duration: FiniteDuration,
  confirmed: Boolean
)

case class SyncResult(
  tableName: TableName,
  metadataUpdated: Boolean,
  partitionsAdded: Int,
  partitionsRemoved: Int,
  inconsistenciesFixed: Int
)

case class SchemaEvolutionResult(
  tableName: TableName,
  fromVersion: SchemaVersion,
  toVersion: SchemaVersion,
  strategy: SchemaEvolutionStrategy,
  migrationApplied: Boolean,
  backupCreated: Boolean
)

case class SchemaCompatibilityResult(
  compatible: Boolean,
  breakingChanges: List[SchemaChange],
  warnings: List[String],
  recommendations: List[String]
)

case class SchemaMigrationScript(
  statements: List[String],
  rollbackStatements: List[String],
  estimatedDuration: FiniteDuration,
  riskLevel: RiskLevel
)

case class PartitionAnalysis(
  tableName: TableName,
  totalPartitions: Int,
  activePartitions: Int,
  emptyPartitions: Int,
  averagePartitionSize: Long,
  recommendations: List[PartitionRecommendation]
)

case class TableMetrics(
  recordCount: Long,
  sizeBytes: Long,
  fileCount: Int,
  averageFileSize: Long,
  compressionRatio: Double,
  lastModified: Instant
)

case class TableSchema(
  fields: List[SchemaField],
  partitionColumns: List[FieldName],
  version: SchemaVersion
)

case class SchemaField(
  name: FieldName,
  dataType: String,
  nullable: Boolean,
  description: Option[String] = None
)

case class SchemaVersion(value: Int) extends AnyVal

// ===============================
// ERROR TYPES
// ===============================

sealed trait TableError extends FlowForgeError
case class TableNotFound(tableName: TableName) extends TableError {
  val message = s"Table '${tableName.value}' not found"
  val category = ErrorCategory.System
  val severity = ErrorSeverity.Error
  val context = Map("tableName" -> tableName.value)
  val cause = None
  val timestamp = java.time.Instant.now()
  val errorId = java.util.UUID.randomUUID().toString
  val isRetryable = false
  val recoveryHints = List("Check table name spelling", "Verify table exists", "Check permissions")

  def withContext(additionalContext: Map[String, Any]) = this
  def withCause(underlyingCause: Throwable) = this
}

sealed trait PartitionError extends FlowForgeError
case class PartitionNotFound(partitionSpec: PartitionSpec) extends PartitionError {
  val message = s"Partition '${partitionSpec.toPartitionPath}' not found"
  val category = ErrorCategory.System
  val severity = ErrorSeverity.Error
  val context = Map("partitionSpec" -> partitionSpec.toPartitionPath)
  val cause = None
  val timestamp = java.time.Instant.now()
  val errorId = java.util.UUID.randomUUID().toString
  val isRetryable = false
  val recoveryHints = List("Check partition specification", "Verify partition exists")

  def withContext(additionalContext: Map[String, Any]) = this
  def withCause(underlyingCause: Throwable) = this
}

sealed trait BlobError extends FlowForgeError
case class BlobAccessError(bucketName: String, blobName: String, reason: String) extends BlobError {
  val message = s"Cannot access blob '$blobName' in bucket '$bucketName': $reason"
  val category = ErrorCategory.System
  val severity = ErrorSeverity.Error
  val context = Map("bucketName" -> bucketName, "blobName" -> blobName, "reason" -> reason)
  val cause = None
  val timestamp = java.time.Instant.now()
  val errorId = java.util.UUID.randomUUID().toString
  val isRetryable = true
  val recoveryHints = List("Check cloud permissions", "Verify bucket exists", "Retry operation")

  def withContext(additionalContext: Map[String, Any]) = this
  def withCause(underlyingCause: Throwable) = this
}

// ===============================
// ADVANCED TABLE OPERATIONS
// ===============================

/**
 * Advanced table operations with enterprise features.
 */
trait AdvancedTableOperations[F[_]] extends EnterpriseTableAlgebra[F] {

  /**
   * Create table snapshot for backup and recovery.
   *
   * @param tableName Table to snapshot
   * @param snapshotName Name for the snapshot
   * @return Snapshot creation result
   */
  def createSnapshot(
    tableName: TableName,
    snapshotName: String
  ): F[SnapshotResult]

  /**
   * Restore table from snapshot.
   *
   * @param tableName Table to restore
   * @param snapshotName Snapshot to restore from
   * @return Restore operation result
   */
  def restoreFromSnapshot(
    tableName: TableName,
    snapshotName: String
  ): F[RestoreResult]

  /**
   * Clone table with or without data.
   *
   * @param sourceTable Source table to clone
   * @param targetTable Target table name
   * @param includeData Whether to copy data or just schema
   * @return Clone operation result
   */
  def cloneTable(
    sourceTable: TableName,
    targetTable: TableName,
    includeData: Boolean = true
  ): F[CloneResult]

  /**
   * Analyze table statistics and generate recommendations.
   *
   * @param tableName Table to analyze
   * @return Comprehensive table analysis
   */
  def analyzeTable(tableName: TableName): F[TableAnalysis]

  /**
   * Compare two tables for differences.
   *
   * @param table1 First table
   * @param table2 Second table
   * @param comparisonType Type of comparison to perform
   * @return Table comparison result
   */
  def compareTables(
    table1: TableName,
    table2: TableName,
    comparisonType: TableComparisonType
  ): F[TableComparisonResult]
}

// ===============================
// RESOURCE-SAFE TABLE OPERATIONS
// ===============================

/**
 * Resource-safe table operations using Resource[F, _] pattern.
 * Ensures proper cleanup of table connections and locks.
 */
trait ResourceSafeTableOps[F[_]] {
  implicit def monadCancel: MonadCancel[F, Throwable]

  /**
   * Acquire table lock for exclusive operations.
   *
   * @param tableName Table to lock
   * @param lockType Type of lock to acquire
   * @return Resource-managed table lock
   */
  def acquireTableLock(
    tableName: TableName,
    lockType: TableLockType
  ): Resource[F, TableLock]

  /**
   * Open table connection with automatic cleanup.
   *
   * @param tableName Table to connect to
   * @return Resource-managed table connection
   */
  def openTableConnection(tableName: TableName): Resource[F, TableConnection]

  /**
   * Execute table operation within resource boundary.
   *
   * @param tableName Table for operation
   * @param operation Operation to execute
   * @tparam A Operation result type
   * @return Operation result with guaranteed cleanup
   */
  def withTableResource[A](
    tableName: TableName
  )(operation: TableConnection => F[A]): F[A] = {
    openTableConnection(tableName).use(operation)
  }
}

// ===============================
// SUPPORTING TYPES AND ENUMS
// ===============================

sealed trait TableLockType
object TableLockType {
  case object Shared extends TableLockType
  case object Exclusive extends TableLockType
  case object SchemaLock extends TableLockType
}

sealed trait TableComparisonType
object TableComparisonType {
  case object Schema extends TableComparisonType
  case object Data extends TableComparisonType
  case object Statistics extends TableComparisonType
  case object Complete extends TableComparisonType
}

sealed trait RiskLevel
object RiskLevel {
  case object Low extends RiskLevel
  case object Medium extends RiskLevel
  case object High extends RiskLevel
  case object Critical extends RiskLevel
}

case class SchemaChange(
  changeType: SchemaChangeType,
  fieldName: FieldName,
  description: String,
  impact: SchemaImpact
)

sealed trait SchemaChangeType
object SchemaChangeType {
  case object AddColumn extends SchemaChangeType
  case object DropColumn extends SchemaChangeType
  case object ModifyColumn extends SchemaChangeType
  case object RenameColumn extends SchemaChangeType
}

sealed trait SchemaImpact
object SchemaImpact {
  case object None extends SchemaImpact
  case object Low extends SchemaImpact
  case object Medium extends SchemaImpact
  case object High extends SchemaImpact
  case object Breaking extends SchemaImpact
}

case class PartitionRecommendation(
  recommendationType: RecommendationType,
  description: String,
  estimatedBenefit: String,
  effort: EffortLevel
)

sealed trait RecommendationType
object RecommendationType {
  case object Optimize extends RecommendationType
  case object Repartition extends RecommendationType
  case object Cleanup extends RecommendationType
  case object Archive extends RecommendationType
}

sealed trait EffortLevel
object EffortLevel {
  case object Low extends EffortLevel
  case object Medium extends EffortLevel
  case object High extends EffortLevel
}

// ===============================
// OPERATION RESULT TYPES
// ===============================

case class PartitionDropResult(
  partitionSpec: PartitionSpec,
  success: Boolean,
  filesDeleted: Long,
  spaceReclaimed: Long,
  errors: List[PartitionError] = List.empty
)

case class PartitionAddResult(
  partitionSpec: PartitionSpec,
  success: Boolean,
  location: String,
  errors: List[PartitionError] = List.empty
)

case class SnapshotResult(
  tableName: TableName,
  snapshotName: String,
  snapshotLocation: String,
  snapshotSize: Long,
  duration: FiniteDuration
)

case class RestoreResult(
  tableName: TableName,
  snapshotName: String,
  recordsRestored: Long,
  duration: FiniteDuration
)

case class CloneResult(
  sourceTable: TableName,
  targetTable: TableName,
  schemaCloned: Boolean,
  dataCloned: Boolean,
  recordsCloned: Long,
  duration: FiniteDuration
)

case class TableAnalysis(
  tableName: TableName,
  metrics: TableMetrics,
  recommendations: List[TableRecommendation],
  healthScore: Double,
  optimizationOpportunities: List[OptimizationOpportunity]
)

case class TableComparisonResult(
  table1: TableName,
  table2: TableName,
  comparisonType: TableComparisonType,
  differences: List[TableDifference],
  summary: String
)

case class TableRecommendation(
  category: RecommendationCategory,
  description: String,
  priority: Priority,
  estimatedImpact: String
)

case class OptimizationOpportunity(
  strategy: OptimizationStrategy,
  description: String,
  estimatedBenefit: String,
  complexity: ComplexityLevel
)

case class TableDifference(
  category: DifferenceCategory,
  description: String,
  severity: DifferenceSeverity
)

// Supporting enums
sealed trait RecommendationCategory
object RecommendationCategory {
  case object Performance extends RecommendationCategory
  case object Storage extends RecommendationCategory
  case object Maintenance extends RecommendationCategory
  case object Security extends RecommendationCategory
}

sealed trait Priority
object Priority {
  case object Low extends Priority
  case object Medium extends Priority
  case object High extends Priority
  case object Critical extends Priority
}

sealed trait ComplexityLevel
object ComplexityLevel {
  case object Simple extends ComplexityLevel
  case object Moderate extends ComplexityLevel
  case object Complex extends ComplexityLevel
  case object Expert extends ComplexityLevel
}

sealed trait DifferenceCategory
object DifferenceCategory {
  case object Schema extends DifferenceCategory
  case object Data extends DifferenceCategory
  case object Statistics extends DifferenceCategory
  case object Metadata extends DifferenceCategory
}

sealed trait DifferenceSeverity
object DifferenceSeverity {
  case object Info extends DifferenceSeverity
  case object Warning extends DifferenceSeverity
  case object Error extends DifferenceSeverity
  case object Critical extends DifferenceSeverity
}

// ===============================
// ABSTRACT RESOURCE TYPES
// ===============================

trait TableLock {
  def lockType: TableLockType
  def tableName: TableName
  def acquiredAt: Instant
  def isActive: Boolean
}

trait TableConnection {
  def tableName: TableName
  def connectionType: String
  def isActive: Boolean
  def lastUsed: Instant
}

// ===============================
// COMPANION OBJECT WITH UTILITIES
// ===============================

object EnterpriseTableAlgebra {

  /**
   * Type class summoner
   */
  def apply[F[_]](implicit ev: EnterpriseTableAlgebra[F]): EnterpriseTableAlgebra[F] = ev

  /**
   * Create table operation builder with type safety.
   */
  def operation[F[_]: EffectSystem](tableName: TableName): TableOperationBuilder[F] =
    TableOperationBuilder[F](tableName)

  /**
   * Utility methods for table operations
   */
  def inferTableType(location: TableLocation): TableType = location.path match {
    case path if path.contains("delta") => TableType.Delta
    case path if path.contains("iceberg") => TableType.Iceberg
    case path if path.contains("hudi") => TableType.Hudi
    case _ => TableType.Parquet
  }
}

/**
 * Builder for complex table operations with type safety.
 */
case class TableOperationBuilder[F[_]: EffectSystem](
  tableName: TableName,
  operations: List[TableOperation[F]] = List.empty
) {

  def repair: TableOperationBuilder[F] =
    copy(operations = operations :+ TableOperation.Repair)

  def optimize(strategy: OptimizationStrategy): TableOperationBuilder[F] =
    copy(operations = operations :+ TableOperation.Optimize(strategy))

  def vacuum(policy: RetentionPolicy): TableOperationBuilder[F] =
    copy(operations = operations :+ TableOperation.Vacuum(policy))

  def execute(implicit algebra: EnterpriseTableAlgebra[F]): F[List[TableOperationResult]] = {
    operations.traverse {
      case TableOperation.Repair =>
        algebra.repairRefreshTable(tableName)
      case TableOperation.Optimize(strategy) =>
        algebra.optimizeTable(tableName, strategy).map(opt =>
          TableOperationResult(
            tableName = tableName,
            operation = "optimize",
            success = true,
            duration = opt.duration,
            recordsAffected = 0,
            partitionsAffected = 0,
            storageUsed = opt.beforeMetrics.sizeBytes - opt.afterMetrics.sizeBytes
          )
        )
      case TableOperation.Vacuum(policy) =>
        algebra.vacuumTable(tableName, policy).map(vac =>
          TableOperationResult(
            tableName = tableName,
            operation = "vacuum",
            success = true,
            duration = vac.duration,
            recordsAffected = 0,
            partitionsAffected = 0,
            storageUsed = vac.spaceReclaimed
          )
        )
    }
  }
}

sealed trait TableOperation[+F[_]]
object TableOperation {
  case object Repair extends TableOperation[Nothing]
  case class Optimize(strategy: OptimizationStrategy) extends TableOperation[Nothing]
  case class Vacuum(policy: RetentionPolicy) extends TableOperation[Nothing]
}

sealed trait TableType
object TableType {
  case object Delta extends TableType
  case object Iceberg extends TableType
  case object Hudi extends TableType
  case object Parquet extends TableType
  case object Avro extends TableType
  case object ORC extends TableType
}
