/**
 * FlowForge Core Module - Change Data Capture Algebra
 *
 * File: modules/core/src/main/scala/com/flowforge/core/algebra/CDCAlgebra.scala Package:
 * com.flowforge.core.algebra
 *
 * Type-safe, effect-polymorphic Change Data Capture operations. Inspired by reference-utilities ETL.scala but
 * enhanced with:
 *   - Compile-time type safety via phantom types and refined types
 *   - Effect system abstraction for resource safety
 *   - Functional composition patterns with Kleisli arrows
 *   - ValidatedNel for comprehensive error accumulation
 *   - Data contract integration for schema validation
 *
 * Design Patterns Applied:
 *   - Tagless Final Pattern: Effect abstraction without concrete effect types
 *   - Type Class Pattern: Capability-based programming with implicit resolution
 *   - ADT Pattern: Comprehensive modeling of CDC operations and results
 *   - Phantom Types: Compile-time tracking of data lineage and transformations
 *
 * Scala Features Showcased:
 *   - Higher-Kinded Types: F[_] abstraction over effect containers
 *   - Refined Types: Compile-time validation of field names and constraints
 *   - Type Classes: DataContract[A] for automatic schema validation
 *   - ValidatedNel: Accumulative error handling for data quality
 *   - NonEmptyList: Safe handling of primary key lists
 *
 * @author
 *   FlowForge Team
 * @version 1.0.0
 * @since 2024
 */
package com.flowforge.core.algebra

import cats.data.{ NonEmptyList, ValidatedNel }
import cats.implicits._
import com.flowforge.core.algebra.DataAlgebra.Dataset
import com.flowforge.core.algebra.EffectSystem
// Use function-style DataContract alias explicitly to avoid clash with type class
import com.flowforge.core.types.PipelineTypes.{ DataContract => PDataContract }
import com.flowforge.core.types.RefinedTypes.FieldName
import com.flowforge.core.types.{ DataSink, DataSource, FlowForgeError }
import eu.timepit.refined.types.string.NonEmptyString

import java.time.Instant
import scala.concurrent.duration.FiniteDuration

/**
 * Change Data Capture operations abstraction.
 *
 * Provides type-safe, effect-polymorphic operations for detecting and processing data changes between source
 * and target datasets. Enhanced version of reference-utilities ETL patterns with functional programming
 * principles.
 */
// ===============================
// CORE CDC TYPES (moved outside trait for companion object access)
// ===============================

/**
 * Change operation types for CDC processing
 */
sealed trait ChangeOperation
object ChangeOperation {
  case object Insert   extends ChangeOperation
  case object Update   extends ChangeOperation
  case object Delete   extends ChangeOperation
  case object NoChange extends ChangeOperation
}

/**
 * CDC processing result with comprehensive metadata
 */
case class CDCResult[A](
  processedRecords: Long,
  insertCount: Long,
  updateCount: Long,
  deleteCount: Long,
  noChangeCount: Long,
  processingTime: FiniteDuration,
  qualityMetrics: CDCQualityMetrics,
  lineage: DataLineage,
  errors: List[FlowForgeError] = List.empty)

/**
 * Quality metrics for CDC operations
 */
case class CDCQualityMetrics(
  duplicateKeyCount: Long,
  nullPrimaryKeyCount: Long,
  schemaViolationCount: Long,
  dataQualityScore: Double, // 0.0 to 1.0
)

/**
 * Data lineage information for audit and tracking
 */
case class DataLineage(
  sourceInfo: DataSource,
  targetInfo: DataSink,
  transformationHash: String,
  processingTimestamp: Instant,
  pipelineId: NonEmptyString)

/**
 * CDC configuration with validation
 */
case class CDCConfig(
  primaryKeys: NonEmptyList[FieldName],
  hashColumns: Option[NonEmptyList[FieldName]] = None,
  softDeleteColumn: Option[FieldName] = None,
  timestampColumn: Option[FieldName] = None,
  batchSize: Int = 10000,
  enableQualityChecks: Boolean = true)

trait CDCAlgebra[F[_]] {

  // ===============================
  // CDC OPERATIONS
  // ===============================

  /**
   * Perform CDC between source and target datasets.
   *
   * Enhanced version of reference ETL.performDelta with:
   *   - Type safety via DataContract
   *   - Effect polymorphism
   *   - Comprehensive validation
   *   - Quality metrics collection
   *
   * @param source
   *   Source dataset
   * @param target
   *   Target dataset
   * @param config
   *   CDC configuration
   * @tparam A
   *   Dataset record type with DataContract
   * @return
   *   CDC result with comprehensive metrics
   */
  def performDelta[A](
    source: Dataset[A],
    target: Dataset[A],
    config: CDCConfig,
  )(implicit contract: PDataContract[A],
  ): F[CDCResult[A]]

  /**
   * Compute hash for change detection.
   *
   * @param record
   *   Record to hash
   * @param columns
   *   Columns to include in hash
   * @return
   *   Hash string for change detection
   */
  def computeRecordHash[A](
    record: A,
    columns: NonEmptyList[FieldName],
  ): F[String]

  /**
   * Identify changes between source and target records.
   *
   * @param sourceRecords
   *   Source dataset records
   * @param targetRecords
   *   Target dataset records
   * @param config
   *   CDC configuration
   * @return
   *   List of changes with operation types
   */
  def identifyChanges[A](
    sourceRecords: List[A],
    targetRecords: List[A],
    config: CDCConfig,
  )(implicit contract: PDataContract[A],
  ): F[List[(A, ChangeOperation)]]

  /**
   * Apply CDC changes to target dataset.
   *
   * @param changes
   *   List of changes to apply
   * @param target
   *   Target dataset
   * @return
   *   Updated target dataset
   */
  def applyChanges[A](
    changes: List[(A, ChangeOperation)],
    target: Dataset[A],
  )(implicit contract: PDataContract[A],
  ): F[Dataset[A]]

  /**
   * Validate CDC configuration against dataset schema.
   *
   * @param config
   *   CDC configuration
   * @param schema
   *   Dataset schema
   * @return
   *   Validation result
   */
  def validateCDCConfig[A](
    config: CDCConfig,
    schema: PDataContract[A],
  ): ValidatedNel[FlowForgeError, CDCConfig]

  // ===============================
  // ADVANCED CDC OPERATIONS
  // ===============================

  /**
   * Perform incremental CDC with watermark tracking.
   *
   * @param source
   *   Source dataset
   * @param target
   *   Target dataset
   * @param watermark
   *   Last processing timestamp
   * @param config
   *   CDC configuration
   * @return
   *   CDC result with updated watermark
   */
  def performIncrementalDelta[A](
    source: Dataset[A],
    target: Dataset[A],
    watermark: Option[Instant],
    config: CDCConfig,
  )(implicit contract: PDataContract[A],
  ): F[(CDCResult[A], Instant)]

  /**
   * Merge CDC results from multiple sources.
   *
   * @param results
   *   List of CDC results to merge
   * @return
   *   Merged CDC result
   */
  def mergeCDCResults[A](
    results: NonEmptyList[CDCResult[A]],
  ): F[CDCResult[A]]

  /**
   * Generate CDC summary report.
   *
   * @param result
   *   CDC processing result
   * @return
   *   Human-readable summary report
   */
  def generateCDCReport[A](
    result: CDCResult[A],
  ): F[String]
}

/**
 * Companion object with utility functions and instances
 */
object CDCAlgebra {

  /**
   * Type class summoner
   */
  def apply[F[_]](implicit ev: CDCAlgebra[F]): CDCAlgebra[F] = ev

  /**
   * Default CDC configuration builder
   */
  def defaultConfig(primaryKeys: NonEmptyList[FieldName]): CDCConfig =
    CDCConfig(
      primaryKeys = primaryKeys,
      hashColumns = None,
      softDeleteColumn = None,
      timestampColumn = None,
      batchSize = 10000,
      enableQualityChecks = true,
    )

  /**
   * Syntax extensions for CDC operations
   */
  implicit class CDCOps[F[_], A](private val source: Dataset[A]) {

    def deltaWith(
      target: Dataset[A],
      config: CDCConfig,
    )(implicit
      cdc: CDCAlgebra[F],
      contract: PDataContract[A],
    ): F[CDCResult[A]] =
      cdc.performDelta(source, target, config)

    def incrementalDeltaWith(
      target: Dataset[A],
      watermark: Option[Instant],
      config: CDCConfig,
    )(implicit
      cdc: CDCAlgebra[F],
      contract: PDataContract[A],
    ): F[(CDCResult[A], Instant)] =
      cdc.performIncrementalDelta(source, target, watermark, config)
  }

  /**
   * Enhanced syntax for dataset operations
   */
  implicit class DatasetOps[A](private val dataset: Dataset[A]) {

    def validateWith[F[_]](
      contract: PDataContract[A],
    )(implicit F: EffectSystem[F],
    ): F[ValidatedNel[FlowForgeError, Dataset[A]]] = {
      import cats.syntax.traverse._
      val validations = dataset.data.traverse(contract)
      F.pure(validations.map(_ => dataset))
    }
  }
}
