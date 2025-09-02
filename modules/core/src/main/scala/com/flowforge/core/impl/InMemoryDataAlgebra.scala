package com.flowforge.core.impl

import cats.data.{ NonEmptyList, Validated, ValidatedNel }
import cats.implicits._
import com.flowforge.core.algebra._
import com.flowforge.core.algebra.DataAlgebra._
import com.flowforge.core.types.PipelineTypes.{ DataContract, QualityCheck }
import com.flowforge.core.types.RefinedTypes.FieldName
import com.flowforge.core.types._

import java.nio.file.{ Files, Paths }
import java.time.Instant

final class InMemoryDataAlgebra[F[_]](implicit F: EffectSystem[F]) extends DataAlgebra[F] {

  // ---------- External IO ----------
  override def read[A: DataDecoder](source: DataSource): F[Dataset[A]] = source match {
    case LocalDataSource(path, format, _, schemaOpt, _) =>
      F.blocking {
        val bytes = Files.readAllBytes(Paths.get(path))
        val records: List[A] = format match {
          case DataFormat.JSONL =>
            new String(bytes, "UTF-8").linesIterator.toList
              .filter(_.trim.nonEmpty)
              .flatMap { line =>
                DataDecoder[A].decode(EncodedData(line.getBytes("UTF-8"), format), format).toOption
              }
          case DataFormat.JSON =>
            DataDecoder[A].decode(EncodedData(bytes, format), format).toOption.toList
          case DataFormat.CSV =>
            val lines     = new String(bytes, "UTF-8").linesIterator.toList
            val dataLines = if (lines.nonEmpty) lines.tail else lines
            dataLines.flatMap { line =>
              DataDecoder[A].decode(EncodedData(line.getBytes("UTF-8"), format), format).toOption
            }
          case other => throw new UnsupportedOperationException(s"Unsupported format: $other")
        }
        val schema = schemaOpt.getOrElse(DataSchema.builder.build)
        SimpleDataset(
          records,
          schema,
          DatasetMetadata(records.size.toLong, schema, 1, Instant.now(), Some(source))
        )
      }
    case _ =>
      F.raiseError(
        new UnsupportedOperationException("Unsupported DataSource for in-memory algebra")
      )
  }

  override def readWithSchema[A: DataDecoder](
    source: DataSource,
    expectedSchema: DataSchema
  ): F[ValidatedNel[FlowForgeError, Dataset[A]]] =
    F.map(read[A](source)) { ds =>
      if (ds.schema.fieldNames.toSet == expectedSchema.fieldNames.toSet) Validated.valid(ds)
      else Validated.invalidNel(SchemaIncompatible(expectedSchema, ds.schema))
    }

  override def stream[A: DataDecoder](source: DataSource): F[DataStream[F, A]] =
    F.raiseError(new NotImplementedError("Streaming not supported in InMemoryDataAlgebra"))

  override def write[A: DataEncoder](
    dataset: Dataset[A],
    sink: DataSink,
    options: WriteOptions
  ): F[WriteResult] = sink match {
    case LocalDataSink(path, format, _, _, _) =>
      F.blocking {
        val p = Paths.get(path)
        val bytes: Array[Byte] = format match {
          case DataFormat.JSONL =>
            dataset.data
              .map(a =>
                DataEncoder[A]
                  .encode(a, format)
                  .fold(e => throw new RuntimeException(e.message), _.data)
              )
              .map(b => new String(b, "UTF-8"))
              .mkString("\n")
              .getBytes("UTF-8")
          case DataFormat.CSV =>
            val header = dataset.schema.fieldNames match {
              case Nil => None; case xs => Some(xs.mkString(","))
            }
            val body = dataset.data
              .map(a =>
                DataEncoder[A]
                  .encode(a, format)
                  .fold(e => throw new RuntimeException(e.message), _.data)
              )
              .map(b => new String(b, "UTF-8"))
            (header.toList ++ body).mkString("\n").getBytes("UTF-8")
          case other => throw new UnsupportedOperationException(s"Unsupported format: $other")
        }
        Option(p.getParent).foreach(parent => Files.createDirectories(parent))
        Files.write(
          p,
          bytes,
          java.nio.file.StandardOpenOption.CREATE,
          java.nio.file.StandardOpenOption.TRUNCATE_EXISTING,
          java.nio.file.StandardOpenOption.WRITE
        )
        WriteResult(dataset.data.size.toLong, 1, bytes.length.toLong, success = true)
      }
    case _ =>
      F.raiseError(new UnsupportedOperationException("Unsupported sink for in-memory algebra"))
  }

  override def writeWithValidation[A: DataEncoder](
    dataset: Dataset[A],
    sink: DataSink,
    contract: DataContract[A],
    options: WriteOptions
  ): F[ValidatedNel[FlowForgeError, WriteResult]] =
    write(dataset, sink, options).map(Validated.valid)

  // ---------- Pure data transformations ----------
  override def filter[A](dataset: Dataset[A], predicate: A => Boolean): Dataset[A] =
    SimpleDataset(dataset.data.filter(predicate), dataset.schema, dataset.metadata)

  override def map[A, B: DataEncoder](dataset: Dataset[A], f: A => B): Dataset[B] = {
    val out = dataset.data.map(f)
    val sch = DataEncoder[B].schema(DataFormat.JSON)
    SimpleDataset(out, sch, dataset.metadata.copy(recordCount = out.size.toLong))
  }

  override def flatMap[A, B: DataEncoder](dataset: Dataset[A], f: A => Dataset[B]): Dataset[B] = {
    val out = dataset.data.flatMap(a => f(a).data)
    val sch = if (out.nonEmpty) DataEncoder[B].schema(DataFormat.JSON) else dataset.schema
    SimpleDataset(out, sch, dataset.metadata.copy(recordCount = out.size.toLong))
  }

  override def groupBy[A, K, V: DataEncoder](
    dataset: Dataset[A],
    keyExtractor: A => K,
    aggregator: List[A] => V
  ): Dataset[(K, V)] = {
    val grouped = dataset.data.groupBy(keyExtractor).view.mapValues(aggregator).toList
    val sch =
      DataSchema.builder.addField("key", DataType.String).addField("value", DataType.String).build
    SimpleDataset(grouped, sch, dataset.metadata.copy(recordCount = grouped.size.toLong))
  }

  override def join[A, B, K, C: DataEncoder](
    left: Dataset[A],
    right: Dataset[B],
    leftKey: A => K,
    rightKey: B => K,
    combiner: (A, B) => C
  ): Dataset[C] = {
    val rIndex = right.data.groupBy(rightKey)
    val out = left.data.flatMap { la =>
      rIndex.getOrElse(leftKey(la), Nil).map(rb => combiner(la, rb))
    }
    val sch = DataEncoder[C].schema(DataFormat.JSON)
    SimpleDataset(out, sch, left.metadata.copy(recordCount = out.size.toLong))
  }

  override def union[A](left: Dataset[A], right: Dataset[A]): Dataset[A] =
    SimpleDataset(
      left.data ++ right.data,
      left.schema,
      left.metadata.copy(recordCount = (left.data.size + right.data.size).toLong)
    )

  override def sortBy[A, K: Ordering](dataset: Dataset[A], keyExtractor: A => K): Dataset[A] =
    SimpleDataset(dataset.data.sortBy(keyExtractor), dataset.schema, dataset.metadata)

  override def take[A](dataset: Dataset[A], n: Int): Dataset[A] =
    SimpleDataset(
      dataset.data.take(n),
      dataset.schema,
      dataset.metadata.copy(recordCount = math.min(dataset.data.size, n).toLong)
    )

  override def drop[A](dataset: Dataset[A], n: Int): Dataset[A] =
    SimpleDataset(
      dataset.data.drop(n),
      dataset.schema,
      dataset.metadata.copy(recordCount = math.max(0, dataset.data.size - n).toLong)
    )

  // ---------- Effectful transforms ----------
  override def transformWithEffect[A, B: DataEncoder](
    dataset: Dataset[A],
    transformation: A => F[B]
  ): F[Dataset[B]] =
    F.map(F.traverse(dataset.data)(transformation)) { out =>
      val sch = DataEncoder[B].schema(DataFormat.JSON)
      SimpleDataset(out, sch, dataset.metadata.copy(recordCount = out.size.toLong))
    }

  override def transformPipeline[A, B: DataEncoder](
    dataset: Dataset[A],
    transformations: NonEmptyList[A => F[B]]
  ): F[Dataset[B]] = {
    val composed = transformations.reduceLeft { (f, g) => a =>
      F.flatMap(f(a))(b => F.map(g(a))(_ => b))
    }
    transformWithEffect(dataset, composed)
  }

  // ---------- Metadata & lineage ----------
  override def extractSchema[A](dataset: Dataset[A]): F[DataSchema] = F.pure(dataset.schema)

  override def evolveSchema[A, B: DataEncoder](
    dataset: Dataset[A],
    migration: SchemaMigration[A, B]
  ): F[Dataset[B]] =
    F.raiseError(new NotImplementedError("Schema migration not implemented for in-memory algebra"))

  override def compareSchemas(
    schema1: DataSchema,
    schema2: DataSchema
  ): F[SchemaCompatibilityReport] =
    F.pure(
      SchemaCompatibilityReport(schema1.fieldNames.toSet == schema2.fieldNames.toSet, Nil, Nil)
    )

  override def recordLineage[A](
    dataset: Dataset[A],
    operation: String,
    context: LineageContext
  ): F[LineageRecord] =
    F.pure(
      LineageRecord(
        java.util.UUID.randomUUID().toString,
        dataset.metadata.source.getOrElse(LocalDataSource("unknown", DataFormat.JSON)),
        None,
        operation,
        context,
        List(dataset.schema),
        Some(dataset.schema)
      )
    )

  override def queryLineage(query: LineageQuery): F[List[LineageRecord]] = F.pure(Nil)

  // ---------- Quality ----------
  override def validate[A](
    dataset: Dataset[A],
    contract: DataContract[A]
  ): F[QualityResult[Dataset[A]]] =
    F.pure(QualityResult(dataset, passed = true, violations = Nil, score = 1.0))

  override def runQualityChecks[A](
    dataset: Dataset[A],
    checks: NonEmptyList[QualityCheck[A]]
  ): F[List[QualityCheckResult]] = F.pure(
    checks.toList.zipWithIndex.map { case (_, idx) =>
      QualityCheckResult(s"check_$idx", passed = true, message = "ok", score = 1.0)
    }
  )

  override def profile[A](dataset: Dataset[A]): F[DataProfile[A]] = F.pure(
    DataProfile(
      recordCount = dataset.data.size.toLong,
      nullCount = 0L,
      distinctCount = dataset.data.distinct.size.toLong,
      schema = dataset.schema,
      statistics = Map.empty
    )
  )

  // ---------- Utilities ----------
  def count[A](dataset: Dataset[A]): Long      = dataset.data.size.toLong
  def isEmpty[A](dataset: Dataset[A]): Boolean = dataset.data.isEmpty
  def cache[A](dataset: Dataset[A], strategy: CacheStrategy): F[Dataset[A]] = F.pure(dataset)
  def partition[A](dataset: Dataset[A], partitioner: Partitioner[A]): List[Dataset[A]] =
    dataset.data.groupBy(partitioner.partition).toList.map { case (_, chunk) =>
      SimpleDataset(chunk, dataset.schema, dataset.metadata.copy(recordCount = chunk.size.toLong))
    }

  // ---------- CDC/Table ops (not yet implemented in in-memory) ----------
  def performDelta[A: DataContract](
    source: Dataset[A],
    target: Dataset[A],
    config: CDCOperations.CDCConfig
  ): F[CDCOperations.CDCResult[A]] =
    F.raiseError(new NotImplementedError("CDC not implemented in InMemoryDataAlgebra"))

  def computeCDCOperations[A](
    source: Dataset[A],
    target: Dataset[A],
    keyColumns: NonEmptyList[FieldName]
  ): F[CDCOperations.CDCOperationSet[A]] =
    F.raiseError(new NotImplementedError("CDC not implemented in InMemoryDataAlgebra"))

  def applyCDCOperations[A](
    operations: CDCOperations.CDCOperationSet[A],
    target: DataSink
  ): F[CDCOperations.CDCResult[A]] =
    F.raiseError(new NotImplementedError("CDC not implemented in InMemoryDataAlgebra"))

  /**
   * Repair and refresh table metadata. Uses F[_] because it involves external metadata system
   * operations.
   */
  override def repairRefreshTable(
    table: TableOperations.TableName
  ): F[TableOperations.TableOperationResult] =
    F.raiseError(new NotImplementedError("Table ops not implemented"))

  /**
   * Get table location with validation. Uses F[_] because it involves external metadata lookup.
   */
  override def getTableLocation(
    table: TableOperations.TableName
  ): F[ValidatedNel[FlowForgeError, String]] =
    F.raiseError(new NotImplementedError("Table ops not implemented"))

  /**
   * Get affected partitions for time range. Uses F[_] because it involves external metadata
   * queries.
   */
  override def getAffectedPartitions(
    table: TableOperations.TableName,
    startTime: Instant,
    endTime: Instant
  ): F[List[TableOperations.PartitionSpec]] =
    F.raiseError(new NotImplementedError("Table ops not implemented"))

  /**
   * Safe deletion of table location with external filesystem operations. Uses F[_] because it
   * involves external filesystem operations.
   */
  override def deleteDfsLocation(
    location: String,
    dryRun: Boolean
  ): F[TableOperations.TableOperationResult] =
    F.raiseError(new NotImplementedError("Table ops not implemented"))

  /**
   * Analyze table and compute statistics. Uses F[_] because it involves external metadata system
   * updates.
   */
  override def analyzeTable(
    table: TableOperations.TableName,
    partitions: Option[NonEmptyList[TableOperations.PartitionSpec]]
  ): F[TableOperations.TableOperationResult] =
    F.raiseError(new NotImplementedError("Table ops not implemented"))

  /**
   * Vacuum table to optimize storage. Uses F[_] because it involves external storage system
   * operations.
   */
  override def vacuumTable(
    table: TableOperations.TableName,
    retentionHours: Int,
    dryRun: Boolean
  ): F[TableOperations.TableOperationResult] =
    F.raiseError(new NotImplementedError("Table ops not implemented"))
}
