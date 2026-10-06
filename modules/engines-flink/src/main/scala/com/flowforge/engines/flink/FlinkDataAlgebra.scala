package com.flowforge.engines.flink

import cats.data.{ NonEmptyList, Validated, ValidatedNel }
import com.flowforge.core.algebra.DataAlgebra._
import com.flowforge.core.algebra._
import com.flowforge.core.types.PipelineTypes.{ DataContract => PDataContract, QualityCheck }
import com.flowforge.core.types.RefinedTypes.FieldName
import com.flowforge.core.types._

import java.time.Instant

/**
 * `DataAlgebra` backed by Flink batch jobs.
 *
 * A dataset this engine produces is a [[FlinkDataset]]: a plan for the records rather than the records, so
 * nothing is held on the driver beyond a bounded sample. `read`, `write`, `filter`, `map`, `flatMap`,
 * `union`, `validate` and `runQualityChecks` run in Flink and see every record.
 *
 * The rest of the algebra still forwards to `InMemoryDataAlgebra`, which reads `dataset.data`. On a
 * Flink-backed dataset that is the sample, so `groupBy`, `join`, `sortBy`, `take`, `drop`, `profile`, the CDC
 * operations and the table operations answer for at most [[FlinkDataset.sampleSize]] records. They are listed
 * in `docs/plan/v1.0-readiness.md` rather than hidden here. A dataset from some other engine is held by the
 * driver in full, so forwarding it is exact.
 *
 * The one thing this engine does not do is stream. Every source it reads is a bounded file and every job runs
 * in batch mode, for the reason given on `FlinkStreamOps.batchEnv`.
 */
final class FlinkDataAlgebra[F[_]](implicit F: EffectSystem[F]) extends DataAlgebra[F] {

  private val delegate = new com.flowforge.core.impl.InMemoryDataAlgebra[F]()

  override val capabilities: Set[Capability] =
    Set(Capability.Read, Capability.Write, Capability.QualityChecks)

  // ---------- External IO ----------

  /**
   * The plan for a local file, or None for a format this engine cannot read.
   *
   * Reading the CSV header is an action, so this is one too, and the caller runs it inside `F`. Parquet and
   * Delta are absent because reading them needs a connector this module does not depend on; saying so beats
   * reading them wrong.
   */
  private def planFor(local: LocalDataSource): Option[FlinkPlan] = local.format match {
    case DataFormat.CSV =>
      val header = {
        val lines = scala.io.Source.fromFile(local.location, "UTF-8")
        try
          lines
            .getLines().find(_.trim.nonEmpty).fold(List.empty[String])(
              _.trim.split(",", -1).toList.map(_.trim),
            )
        finally lines.close()
      }
      Some(FlinkStreamOps.readCsv(local.location, header))
    case DataFormat.JSON | DataFormat.JSONL => Some(FlinkStreamOps.readJsonLines(local.location))
    case _                                  => None
  }

  override def read[A: DataDecoder](source: DataSource): F[Dataset[A]] = {
    val result: F[Dataset[A]] = source match {
      case local: LocalDataSource =>
        F.flatMap(F.blocking(planFor(local))) {
          case None =>
            F.raiseError[Dataset[A]](
              new UnsupportedOperationException(s"Format ${local.format} not supported"),
            )
          case Some(plan) =>
            F.blocking(FlinkDataset.fromPlan[A](plan, Some(source)))
        }

      case other =>
        F.raiseError[Dataset[A]](
          new UnsupportedOperationException(
            s"DataSource type ${other.getClass.getSimpleName} not supported",
          ),
        )
    }

    // The Spark engine reports a read failure as the framework's error type. Reporting it the same way here
    // means a caller that handles one engine's read failure handles both.
    F.handleErrorWith(result) { error =>
      F.raiseError(
        DataProcessingError.ProcessingFailure(
          stepName = "flink-read",
          reason = error.getMessage,
          message = s"Failed to read from ${source.getClass.getSimpleName}",
          cause = Some(error),
        ),
      )
    }
  }

  override def readWithSchema[A: DataDecoder](
    source: DataSource,
    expectedSchema: DataSchema,
  ): F[ValidatedNel[FlowForgeError, Dataset[A]]] =
    F.map(read[A](source))(dataset => Validated.validNel(dataset))

  /**
   * One batch read, handed back as a stream of a single chunk. Nothing here is a Flink streaming job: see the
   * class comment.
   */
  override def stream[A: DataDecoder](source: DataSource): F[DataStream[F, A]] =
    F.pure(new DataStream[F, A] {
      def chunks: F[List[Dataset[A]]] = F.map(read[A](source))(dataset => List(dataset))
    })

  /**
   * Write every record, one per line, through a Flink sink.
   *
   * Only a local JSON sink is supported. The records are re-encoded with the caller's encoder rather than
   * passed through as the plan's own JSON, so what lands on disk is what the encoder says a record looks
   * like. CSV is not supported: a dataflow sink cannot order a header line ahead of the rows it writes, and
   * writing the rows without the header would produce a file this engine could not read back.
   *
   * The record count is the one the dataset already established, so reporting it starts no extra job.
   */
  override def write[A: DataEncoder](
    dataset: Dataset[A],
    sink: DataSink,
    options: WriteOptions = WriteOptions.default,
  ): F[WriteResult] = (dataset, sink) match {
    case (fd: FlinkDataset[A], local: LocalDataSink)
        if local.format == DataFormat.JSON || local.format == DataFormat.JSONL =>
      F.blocking {
        val encoded = FlinkStreamOps.encodeRows[A](fd.plan, local.format)(fd.decoder, DataEncoder[A])
        FlinkStreamOps.writeLines(encoded, local.location)
        val bytes = java.nio.file.Files.size(java.nio.file.Paths.get(local.location))
        WriteResult(
          recordsWritten = fd.metadata.recordCount,
          partitionsWritten = 1,
          bytesWritten = bytes,
          success = true,
        )
      }

    case (_: FlinkDataset[A], local: LocalDataSink) =>
      F.raiseError[WriteResult](
        new UnsupportedOperationException(s"Format ${local.format} not supported by the Flink sink"),
      )

    case _ =>
      // Not a dataset this engine produced, so the driver holds it in full and the in-memory writer is exact.
      delegate.write(dataset, sink, options)
  }

  override def writeWithValidation[A: DataEncoder](
    dataset: Dataset[A],
    sink: DataSink,
    contract: PDataContract[A],
    options: WriteOptions = WriteOptions.default,
  ): F[ValidatedNel[FlowForgeError, WriteResult]] =
    delegate.writeWithValidation(dataset, sink, contract, options)

  // ---------- Pure transformations ----------

  // These three append an operator to the plan and rebuild the dataset around it. Appending is a calculation;
  // the job that follows is what `FlinkDataset.fromPlan` runs to resample and count. Reading `dataset.data`
  // instead, as the forwarding version did, narrowed the sample and left the records alone, so a filter
  // followed by a write wrote every record.
  override def filter[A: DataDecoder](dataset: Dataset[A], predicate: A => Boolean): Dataset[A] =
    dataset match {
      case fd: FlinkDataset[A] => FlinkDataset.withPlan[A](fd, FlinkStreamOps.filter[A](fd.plan, predicate))
      case other               => delegate.filter(other, predicate)
    }

  override def map[A: DataDecoder, B: DataEncoder: DataDecoder](dataset: Dataset[A], f: A => B): Dataset[B] =
    dataset match {
      case fd: FlinkDataset[A] =>
        FlinkDataset.fromPlan[B](FlinkStreamOps.mapRows[A, B](fd.plan, a => List(f(a))), fd.metadata.source)
      case other => delegate.map(other, f)
    }

  override def flatMap[A: DataDecoder, B: DataEncoder: DataDecoder](
    dataset: Dataset[A],
    f: A => Dataset[B],
  ): Dataset[B] =
    dataset match {
      case fd: FlinkDataset[A] =>
        FlinkDataset.fromPlan[B](FlinkStreamOps.mapRows[A, B](fd.plan, a => f(a).data), fd.metadata.source)
      case other => delegate.flatMap(other, f)
    }

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

  // `union` carries no `DataDecoder[A]`, so the one the left dataset was read with is what rebuilds the
  // result. This is why a `FlinkDataset` keeps its decoder.
  override def union[A](left: Dataset[A], right: Dataset[A]): Dataset[A] = (left, right) match {
    case (lf: FlinkDataset[A], rf: FlinkDataset[A]) =>
      FlinkDataset.fromPlan[A](FlinkStreamOps.union(lf.plan, rf.plan), lf.metadata.source)(lf.decoder)
    case _ => delegate.union(left, right)
  }

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

  /**
   * Apply the contract to every record.
   *
   * The result is built by `DataAlgebra.contractResult`, the same function the other engines call, so what
   * `passed`, `score` and `recordsAffected` mean here is what they mean there.
   *
   * Two jobs run, for the two halves of the answer. The count of violating records is exact, because that is
   * what decides `passed` and `score`. The list of distinct messages is capped, because a message built from
   * a record's own values is otherwise as numerous as the dataset. Capping messages cannot turn a failing
   * contract into a passing one.
   */
  override def validate[A: DataDecoder](
    dataset: Dataset[A],
    contract: PDataContract[A],
  ): F[QualityResult[Dataset[A]]] = {

    /** The messages for the violations the contract reports on one record, empty when it accepts it. */
    val messagesOf: A => List[String] = a => contract(a).fold(_.toList.map(_.message), _ => Nil)

    dataset match {
      case fd: FlinkDataset[A] =>
        F.blocking {
          val violating =
            FlinkStreamOps.count(FlinkStreamOps.filter[A](fd.plan, a => messagesOf(a).nonEmpty))
          val messageCounts = FlinkStreamOps.countKeys[A](fd.plan, messagesOf)
          DataAlgebra.contractResult(dataset, fd.metadata.recordCount, violating, messageCounts)
        }

      case other => delegate.validate(other, contract)
    }
  }

  /**
   * Apply every check to every record.
   *
   * Two jobs again, and the split is the same one for the same reason. The first counts how many records each
   * check rejected, keyed by the check's index: that key set is as large as `checks`, so it fits and the set
   * of failed checks is exact. The second collects messages, capped, so a dataset of violations cannot fill
   * the driver. A check can therefore be reported as failed with no message to show.
   */
  override def runQualityChecks[A: DataDecoder](
    dataset: Dataset[A],
    checks: NonEmptyList[QualityCheck[A]],
  ): F[List[QualityCheckResult]] = {
    val messagesPerCheck: List[A => List[String]] =
      checks.toList.map(chk => (a: A) => chk(a).fold(_.toList.map(_.message), _ => Nil))

    /** The indexes of the checks that rejected one record. */
    val failedIndexesOf: A => List[String] = a =>
      messagesPerCheck.zipWithIndex.collect { case (check, idx) if check(a).nonEmpty => idx.toString }

    /** One key per violation, carrying both the check it came from and its message. */
    val indexedMessagesOf: A => List[String] = a =>
      messagesPerCheck.zipWithIndex.flatMap {
        case (check, idx) => check(a).map(message => CheckKey.of(idx, message))
      }

    dataset match {
      case fd: FlinkDataset[A] =>
        F.blocking {
          val failed =
            FlinkStreamOps.countKeys[A](fd.plan, failedIndexesOf, messagesPerCheck.size).map(_._1).toSet
          val messagesByIndex = FlinkStreamOps
            .countKeys[A](fd.plan, indexedMessagesOf)
            .map(_._1)
            .groupBy(CheckKey.indexOf)

          messagesPerCheck.indices.toList.map { idx =>
            if (!failed.contains(idx.toString))
              QualityCheckResult(s"check_$idx", passed = true, message = "ok", score = 1.0)
            else {
              val reported = messagesByIndex.getOrElse(idx.toString, Nil).map(CheckKey.messageOf)
              val message = reported.take(3) match {
                case Nil   => "violations found, messages not collected"
                case shown => shown.mkString("; ")
              }
              QualityCheckResult(s"check_$idx", passed = false, message = message, score = 0.0)
            }
          }
        }

      case other => delegate.runQualityChecks(other, checks)
    }
  }

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

  // A `FlinkDataset` established its record count when it was built, so this is the whole dataset's count and
  // not the sample's, and asking for it starts no job. The frame is not re-counted here because `count` is
  // declared outside `F`: running a job from it would make a method the interface calls pure an action.
  //
  // `size` is not used, because it is an `Int` and this returns a `Long`, so a dataset of more than
  // `Int.MaxValue` records would come back negative.
  override def count[A](dataset: Dataset[A]): Long = dataset match {
    case fd: FlinkDataset[A] => fd.metadata.recordCount
    case other               => other.size.toLong
  }
  override def isEmpty[A](dataset: Dataset[A]): Boolean = dataset.isEmpty
  override def cache[A](dataset: Dataset[A], strategy: CacheStrategy): F[Dataset[A]] =
    delegate.cache(dataset, strategy)
  override def partition[A](dataset: Dataset[A], partitioner: Partitioner[A]): List[Dataset[A]] =
    delegate.partition(dataset, partitioner)
}

/**
 * One key carrying both the check a violation came from and the violation's message.
 *
 * `runQualityChecks` needs per-check counts and per-message counts, and a single counting job can only count
 * one kind of key, so the two facts are joined into one key and taken apart again on the driver. The
 * separator is a control character because a violation message is human-readable text, so one cannot appear
 * inside a message and split a key in the wrong place.
 *
 * This is a top-level object rather than a field of the algebra on purpose: the function that builds these
 * keys travels to a Flink task, and a field would make it carry the whole algebra along, which is not
 * serializable.
 */
private[flink] object CheckKey {

  private val separator: Char = 1.toChar

  def of(checkIndex: Int, message: String): String = s"$checkIndex$separator$message"

  def indexOf(key: String): String = key.takeWhile(_ != separator)

  def messageOf(key: String): String = key.dropWhile(_ != separator).drop(1)
}
