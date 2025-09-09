package com.flowforge.engines.flink

import cats.data.{ NonEmptyList, ValidatedNel }
import cats.effect.Sync
import com.flowforge.core.algebra.DataAlgebra._
import com.flowforge.core.algebra._
import com.flowforge.core.types.PipelineTypes.{ DataContract => PDataContract, QualityCheck }
import com.flowforge.core.types.RefinedTypes.FieldName
import com.flowforge.core.types._

import java.time.Instant

/**
 * Minimal Flink-backed DataAlgebra implementation. Delegates to InMemoryDataAlgebra while exposing engine
 * capabilities so that pipelines behave consistently across Spark and Flink.
 */
final class FlinkDataAlgebra[F[_]: Sync](implicit F: EffectSystem[F]) extends DataAlgebra[F] {

  private val delegate = new com.flowforge.core.impl.InMemoryDataAlgebra[F]()

  override val capabilities: Set[Capability] =
    Set(Capability.Read, Capability.Write, Capability.QualityChecks)

  // ---------- External IO ----------
  override def read[A: DataDecoder](source: DataSource): F[Dataset[A]] =
    delegate.read(source)

  override def readWithSchema[A: DataDecoder](
    source: DataSource,
    expectedSchema: DataSchema,
  ): F[ValidatedNel[FlowForgeError, Dataset[A]]] =
    delegate.readWithSchema(source, expectedSchema)

  override def stream[A: DataDecoder](source: DataSource): F[DataStream[F, A]] =
    delegate.stream(source)

  override def write[A: DataEncoder](
    dataset: Dataset[A],
    sink: DataSink,
    options: WriteOptions = WriteOptions.default,
  ): F[WriteResult] =
    delegate.write(dataset, sink, options)

  override def writeWithValidation[A: DataEncoder](
    dataset: Dataset[A],
    sink: DataSink,
    contract: PDataContract[A],
    options: WriteOptions = WriteOptions.default,
  ): F[ValidatedNel[FlowForgeError, WriteResult]] =
    delegate.writeWithValidation(dataset, sink, contract, options)

  // ---------- Pure transformations ----------
  override def filter[A](dataset: Dataset[A], predicate: A => Boolean): Dataset[A] =
    delegate.filter(dataset, predicate)

  override def map[A, B: DataEncoder](dataset: Dataset[A], f: A => B): Dataset[B] =
    delegate.map(dataset, f)

  override def flatMap[A, B: DataEncoder](dataset: Dataset[A], f: A => Dataset[B]): Dataset[B] =
    delegate.flatMap(dataset, f)

  override def groupBy[A, K, V: DataEncoder](
    dataset: Dataset[A],
    keyExtractor: A => K,
    aggregator: List[A] => V,
  ): Dataset[(K, V)] =
    delegate.groupBy(dataset, keyExtractor, aggregator)

  override def join[A, B, K, C: DataEncoder](
    left: Dataset[A],
    right: Dataset[B],
    leftKey: A => K,
    rightKey: B => K,
    combiner: (A, B) => C,
  ): Dataset[C] =
    delegate.join(left, right, leftKey, rightKey, combiner)

  override def union[A](left: Dataset[A], right: Dataset[A]): Dataset[A] =
    delegate.union(left, right)

  override def sortBy[A, K: Ordering](dataset: Dataset[A], keyExtractor: A => K): Dataset[A] =
    delegate.sortBy(dataset, keyExtractor)

  override def take[A](dataset: Dataset[A], n: Int): Dataset[A] =
    delegate.take(dataset, n)

  override def drop[A](dataset: Dataset[A], n: Int): Dataset[A] =
    delegate.drop(dataset, n)

  override def transformWithEffect[A, B: DataEncoder](
    dataset: Dataset[A],
    f: A => F[B],
  ): F[Dataset[B]] =
    delegate.transformWithEffect(dataset, f)

  override def transformPipeline[A, B: DataEncoder](
    dataset: Dataset[A],
    transformations: NonEmptyList[A => F[B]],
  ): F[Dataset[B]] =
    delegate.transformPipeline(dataset, transformations)

  override def extractSchema[A](dataset: Dataset[A]): F[DataSchema] =
    delegate.extractSchema(dataset)

  override def evolveSchema[A, B: DataEncoder](
    dataset: Dataset[A],
    migration: SchemaMigration[A, B],
  ): F[Dataset[B]] =
    delegate.evolveSchema(dataset, migration)

  override def compareSchemas(
    left: DataSchema,
    right: DataSchema,
  ): F[SchemaCompatibilityReport] =
    delegate.compareSchemas(left, right)

  override def recordLineage[A](
    dataset: Dataset[A],
    operation: String,
    context: LineageContext,
  ): F[LineageRecord] =
    delegate.recordLineage(dataset, operation, context)

  override def queryLineage(query: LineageQuery): F[List[LineageRecord]] =
    delegate.queryLineage(query)

  override def validate[A](
    dataset: Dataset[A],
    contract: PDataContract[A],
  ): F[QualityResult[Dataset[A]]] =
    delegate.validate(dataset, contract)

  override def runQualityChecks[A](
    dataset: Dataset[A],
    checks: NonEmptyList[QualityCheck[A]],
  ): F[List[QualityCheckResult]] =
    delegate.runQualityChecks(dataset, checks)

  override def profile[A](dataset: Dataset[A]): F[DataProfile[A]] =
    delegate.profile(dataset)

  // ---------- CDC operations ----------
  override def performDelta[A: DataContract](
    source: Dataset[A],
    target: Dataset[A],
    config: CDCOperations.CDCConfig,
  ): F[CDCOperations.CDCResult[A]] =
    delegate.performDelta(source, target, config)

  override def computeCDCOperations[A](
    source: Dataset[A],
    target: Dataset[A],
    keyColumns: NonEmptyList[FieldName],
  ): F[CDCOperations.CDCOperationSet[A]] =
    delegate.computeCDCOperations(source, target, keyColumns)

  override def applyCDCOperations[A](
    operations: CDCOperations.CDCOperationSet[A],
    target: DataSink,
  ): F[CDCOperations.CDCResult[A]] =
    delegate.applyCDCOperations(operations, target)

  // ---------- Table operations ----------
  override def repairRefreshTable(table: TableOperations.TableName): F[TableOperations.TableOperationResult] =
    delegate.repairRefreshTable(table)

  override def getTableLocation(table: TableOperations.TableName): F[ValidatedNel[FlowForgeError, String]] =
    delegate.getTableLocation(table)

  override def getAffectedPartitions(
    table: TableOperations.TableName,
    startTime: Instant,
    endTime: Instant,
  ): F[List[TableOperations.PartitionSpec]] =
    delegate.getAffectedPartitions(table, startTime, endTime)

  override def deleteDfsLocation(
    location: String,
    dryRun: Boolean = true,
  ): F[TableOperations.TableOperationResult] =
    delegate.deleteDfsLocation(location, dryRun)

  override def analyzeTable(
    table: TableOperations.TableName,
    partitions: Option[NonEmptyList[TableOperations.PartitionSpec]] = None,
  ): F[TableOperations.TableOperationResult] =
    delegate.analyzeTable(table, partitions)

  override def vacuumTable(
    table: TableOperations.TableName,
    retentionHours: Int = 168,
    dryRun: Boolean = true,
  ): F[TableOperations.TableOperationResult] =
    delegate.vacuumTable(table, retentionHours, dryRun)

  // ---------- Utilities ----------
  override def count[A](dataset: Dataset[A]): Long      = delegate.count(dataset)
  override def isEmpty[A](dataset: Dataset[A]): Boolean = delegate.isEmpty(dataset)
  override def cache[A](dataset: Dataset[A], strategy: CacheStrategy): F[Dataset[A]] =
    delegate.cache(dataset, strategy)
  override def partition[A](dataset: Dataset[A], partitioner: Partitioner[A]): List[Dataset[A]] =
    delegate.partition(dataset, partitioner)
}
