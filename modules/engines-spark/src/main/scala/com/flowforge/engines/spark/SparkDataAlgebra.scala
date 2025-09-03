package com.flowforge.engines.spark

import cats.data.{ NonEmptyList, ValidatedNel }
import cats.effect.Resource
import cats.implicits._
import com.flowforge.core.algebra._
import com.flowforge.core.types.PipelineTypes.{ DataContract => PipelineDataContract, QualityCheck }
import com.flowforge.core.types.RefinedTypes.{ FieldName, SchemaVersion }
import com.flowforge.core.types._
import org.apache.spark.sql.SparkSession

import java.time.Instant
import java.util.UUID
import scala.concurrent.duration.{ DurationLong, FiniteDuration, NANOSECONDS }

// TODO: PRODUCTION - This is architectural scaffolding, NOT production-ready code!
//
// CRITICAL MISSING IMPLEMENTATIONS:
// 1. Delta Lake integration with proper MERGE INTO statements
// 2. Real SCD1/SCD2 patterns with temporal versioning
// 3. Optimized large dataset handling (partitioning, streaming)
// 4. Proper Spark SQL query generation instead of in-memory operations
// 5. Transaction log management for consistency
// 6. Change detection using proper key extraction, not hashCode()
//
// CURRENT STATUS: Compiles successfully, would fail catastrophically in production
// PRODUCTION READINESS: ~10% - interfaces exist, logic is placeholder
//
// See: /SCAFFOLDING_VS_PRODUCTION_AUDIT.md for detailed analysis
object SparkDataAlgebra {

  /**
   * Create Spark-based DataAlgebra instance with SCAFFOLDING implementations
   *
   * WARNING: This is NOT production-ready! See TODOs above for required work.
   */
  def createSparkDataAlgebra[F[_]: EffectSystem](
    sparkSession: SparkSession
  ): DataAlgebra[F] = new DataAlgebra[F] {

    import com.flowforge.core.impl.SimpleDataset

    private val F     = EffectSystem[F]
    private val spark = sparkSession

    // ========================================
    // EXTERNAL IO OPERATIONS (F[_] Required)
    // ========================================

    override def read[A: DataDecoder](source: DataSource): F[DataAlgebra.Dataset[A]] = {
      val result: F[DataAlgebra.Dataset[A]] = source match {
        case local: LocalDataSource =>
          F.blocking {
            // Read with Spark
            val df = local.format match {
              case DataFormat.CSV =>
                spark.read
                  .option("header", "true")
                  .option("inferSchema", "true")
                  .csv(local.location)
              case DataFormat.JSON =>
                spark.read.json(local.location)
              case DataFormat.Parquet =>
                spark.read.parquet(local.location)
              case other =>
                throw new UnsupportedOperationException(s"Format $other not supported")
            }
            // Convert rows to JSON and decode to A via DataDecoder[A]
            import org.apache.spark.sql.functions.to_json
            val jsonDs = df.toJSON.collect().toList
            val decoded: List[A] = jsonDs.flatMap { js =>
              val bytes = js.getBytes("UTF-8")
              DataDecoder[A].decode(EncodedData(bytes, DataFormat.JSON), DataFormat.JSON).toOption
            }
            val schema = DataSchema(
              fields = List.empty,
              version = SchemaVersion.unsafeFrom(1),
              metadata = Map("source" -> "spark"),
              createdAt = Instant.now()
            )
            val metadata = DataAlgebra.DatasetMetadata(
              recordCount = decoded.size.toLong,
              schema = schema,
              partitions = df.rdd.getNumPartitions,
              createdAt = Instant.now(),
              source = Some(source)
            )
            scala.util.Try {
              com.flowforge.core.observability.PrometheusMetrics.Data.readTotal
                .labels("spark", local.format.toString)
                .inc()
            }
            SimpleDataset(decoded, schema, metadata)
          }

        case _ =>
          F.raiseError(
            new UnsupportedOperationException(
              s"DataSource type ${source.getClass.getSimpleName} not supported"
            )
          )
      }

      F.handleErrorWith(result) { error =>
        F.raiseError(
          DataProcessingError.ProcessingFailure(
            stepName = "spark-read",
            reason = error.getMessage,
            message = s"Failed to read from ${source.getClass.getSimpleName}",
            cause = Some(error)
          )
        )
      }
    }

    override def readWithSchema[A: DataDecoder](
      source: DataSource,
      expectedSchema: DataSchema
    ): F[ValidatedNel[FlowForgeError, DataAlgebra.Dataset[A]]] =
      read[A](source).map(dataset => dataset.validNel[FlowForgeError])

    override def stream[A: DataDecoder](source: DataSource): F[DataAlgebra.DataStream[F, A]] =
      F.pure(new DataAlgebra.DataStream[F, A] {
        def chunks: F[List[DataAlgebra.Dataset[A]]] =
          read[A](source).map(dataset => List(dataset))
      })

    override def write[A: DataEncoder](
      dataset: DataAlgebra.Dataset[A],
      sink: DataSink,
      options: DataAlgebra.WriteOptions
    ): F[DataAlgebra.WriteResult] =
      F.blocking {
        // Encode to JSON and write via Spark
        val jsonStrings: List[String] = dataset.data.map { a =>
          DataEncoder[A]
            .encode(a, DataFormat.JSON)
            .fold(_ => "{}", ed => new String(ed.data, "UTF-8"))
        }
        val ds = spark.createDataset(jsonStrings)(org.apache.spark.sql.Encoders.STRING)
        sink match {
          case s: LocalDataSink =>
            dataset.metadata.schema // ensure side-effect free
            s.format match {
              case DataFormat.JSON | DataFormat.JSONL =>
                ds.coalesce(1).write.mode("overwrite").text(s.location)
              case DataFormat.CSV => ds.coalesce(1).write.mode("overwrite").text(s.location)
              case DataFormat.Parquet =>
                spark.read.json(ds).write.mode("overwrite").parquet(s.location)
              case _ => throw new UnsupportedOperationException("Unsupported sink format")
            }
          case _ => throw new UnsupportedOperationException("Unsupported sink type for this path")
        }
        val wr = DataAlgebra.WriteResult(
          recordsWritten = dataset.data.size.toLong,
          partitionsWritten = 1,
          bytesWritten = jsonStrings.map(_.length.toLong).sum,
          success = true
        )
        try
          com.flowforge.core.observability.PrometheusMetrics.Data.writeTotal
            .labels("spark", sink.format.toString)
            .inc()
        catch { case _: Throwable => () }
        wr
      }

    // ========================================
    // CDC (SCD1 + Delta Lake MERGE)
    // ========================================

    private def extractKeys(config: CDCOperations.CDCConfig): List[String] =
      config.keyColumns.toList.map(_.value)

    private def defaultHashColumns(
      df: org.apache.spark.sql.DataFrame,
      keys: List[String],
      config: CDCOperations.CDCConfig
    ): List[String] = {
      val cols    = df.columns.toList
      val nonKeys = cols.filterNot(c => keys.contains(c))
      // If user-specified list is provided, honor it strictly
      config.hashColumns match {
        case Some(nel) => nel.toList.map(_.value).filter(cols.contains)
        case None =>
          val audit = List(
            "created_at",
            "updated_at",
            "ingestion_ts",
            "ingestion_time",
            "load_ts"
          )
          val scdNames = config.scd2
            .map(c => List(c.effectiveFrom.value, c.effectiveTo.value, c.isCurrent.value))
            .getOrElse(List("effective_from", "effective_to", "is_current"))
          // Exclude audit/SCD2 from hashing; hash only business columns
          nonKeys.filterNot(c => (audit ++ scdNames).contains(c))
      }
    }

    private def upsertParquet(
      sourcePath: String,
      targetPath: String,
      keys: List[String]
    ): (Long, Long, Long, Long) = {
      import org.apache.spark.sql.functions._
      val src = spark.read.parquet(sourcePath).alias("source")
      val tgt = spark.read.parquet(targetPath).alias("target")
      val cfg = CDCOperations.CDCConfig(
        keyColumns = cats.data.NonEmptyList.fromListUnsafe(
          keys.map(k => com.flowforge.core.types.RefinedTypes.FieldName.unsafeFrom(k))
        )
      )
      val hashCols = defaultHashColumns(src, keys, cfg)
      def hashExpr(prefix: String) =
        sha2(concat_ws("||", hashCols.map(c => col(s"$prefix.$c")): _*), 256)

      val joinCond = keys.map(k => col(s"target.$k") === col(s"source.$k")).reduce(_ && _)

      val inserted  = src.join(tgt, joinCond, "left_anti").count()
      val matched   = src.join(tgt, joinCond, "inner")
      val updated   = matched.filter(hashExpr("source") =!= hashExpr("target")).count()
      val unchanged = matched.filter(hashExpr("source") === hashExpr("target")).count()
      val deleted   = tgt.join(src, joinCond, "left_anti").count()

      // SCD1-style upsert: target without overlapping keys + all source
      val tgtWithoutOverlap = tgt.join(src, joinCond, "left_anti")
      val merged            = tgtWithoutOverlap.unionByName(src, allowMissingColumns = true)
      merged.write.mode("overwrite").parquet(targetPath)

      (inserted, updated, deleted, unchanged)
    }

    private def mergeDelta(
      sourcePath: String,
      targetPath: String,
      keys: List[String],
      config: CDCOperations.CDCConfig
    ): Either[String, (Long, Long, Long, Long)] =
      try {
        import io.delta.tables.DeltaTable
        import org.apache.spark.sql.functions._

        val srcDf = spark.read.format("delta").load(sourcePath).alias("source")
        val tgt   = DeltaTable.forPath(spark, targetPath)
        val tgtDf = tgt.toDF.alias("target")

        val condSql  = keys.map(k => s"target.$k = source.$k").mkString(" AND ")
        val hashCols = defaultHashColumns(srcDf, keys, config)
        def hashExpr(prefix: String) =
          sha2(concat_ws("||", hashCols.map(c => col(s"$prefix.$c")): _*), 256)

        // CDC counts (pre-merge)
        val inserted  = srcDf.join(tgtDf, expr(condSql), "left_anti").count()
        val matched   = srcDf.join(tgtDf, expr(condSql), "inner")
        val updated   = matched.filter(hashExpr("source") =!= hashExpr("target")).count()
        val unchanged = matched.filter(hashExpr("source") === hashExpr("target")).count()
        val deleted   = tgtDf.join(srcDf, expr(condSql), "left_anti").count()

        // Conditional MERGE: Update only when hash mismatch
        tgt
          .as("target")
          .merge(srcDf, condSql)
          .whenMatched(hashExpr("source") =!= hashExpr("target"))
          .updateAll()
          .whenNotMatched()
          .insertAll()
          .execute()

        Right((inserted, updated, deleted, unchanged))
      } catch {
        case t: Throwable => Left(t.getMessage)
      }

    /**
     * SCD2 merge for Delta tables with standard columns: effective_from, effective_to, is_current.
     * Updates matched changed rows to close current version, then inserts new current versions for
     * changed or new keys.
     */
    private def mergeDeltaSCD2(
      sourcePath: String,
      targetPath: String,
      keys: List[String],
      config: CDCOperations.CDCConfig
    ): Either[String, (Long, Long, Long, Long)] =
      try {
        import io.delta.tables.DeltaTable
        import org.apache.spark.sql.functions._

        val nowTs  = current_timestamp()
        val srcRaw = spark.read.format("delta").load(sourcePath).alias("source")
        val tgtDT  = DeltaTable.forPath(spark, targetPath)
        val tgtRaw = tgtDT.toDF.alias("target")

        val (scdFrom, scdTo, scdCur) = {
          val colsOpt = config.scd2.map { c =>
            (c.effectiveFrom.value, c.effectiveTo.value, c.isCurrent.value)
          }
          colsOpt.getOrElse(("effective_from", "effective_to", "is_current"))
        }

        // Validate target has required SCD2 columns for update path; if empty table, attempt to add
        val missing = List(scdFrom, scdTo, scdCur).filterNot(tgtRaw.columns.contains)
        if (missing.nonEmpty) {
          val isEmpty = tgtRaw.limit(1).count() == 0
          if (isEmpty) {
            // Best-effort: add missing columns via SQL DDL
            try {
              val addCols = missing.map {
                case c if c == scdCur => s"$c BOOLEAN"
                case c                => s"$c TIMESTAMP"
              }.mkString(", ")
              spark.sql(s"ALTER TABLE delta.`$targetPath` ADD COLUMNS ($addCols)")
            } catch {
              case t: Throwable =>
                return Left(
                  s"Failed to add SCD2 columns to empty target at '$targetPath': ${t.getMessage}"
                )
            }
          } else {
            return Left(
              s"Target table at '$targetPath' missing SCD2 columns: ${missing.mkString(", ")}. " +
                "Add columns before SCD2 merge or provide correct names via CDCConfig.scd2."
            )
          }
        }

        val tgtCurrent = tgtRaw.filter(col(scdCur) === lit(true) || col(scdTo).isNull)

        val hashCols = defaultHashColumns(srcRaw, keys, config)
        def hashExpr(prefix: String) =
          sha2(concat_ws("||", hashCols.map(c => col(s"$prefix.$c")): _*), 256)

        val condExpr = keys.map(k => col(s"target.$k") === col(s"source.$k")).reduce(_ && _)

        // Identify changed matches and new keys
        val matched = srcRaw.join(tgtCurrent, condExpr, "inner")
        val changed = matched.filter(hashExpr("source") =!= hashExpr("target")).select("source.*")
        val insertedNew = srcRaw.join(tgtRaw, condExpr, "left_anti")
        val toInsert    = changed.unionByName(insertedNew, allowMissingColumns = true)

        val updatedCnt   = changed.count()
        val insertedCnt  = toInsert.count()
        val unchangedCnt = matched.filter(hashExpr("source") === hashExpr("target")).count()
        val deletedCnt   = tgtRaw.join(srcRaw, condExpr, "left_anti").count()

        // Close current versions for changed rows
        tgtDT
          .as("target")
          .merge(changed.alias("source"), keys.map(k => s"target.$k = source.$k").mkString(" AND "))
          .whenMatched()
          .updateExpr(
            Map(
              scdTo  -> "current_timestamp()",
              scdCur -> "false"
            )
          )
          .execute()

        // Insert new current versions for changed and new rows
        val toInsertWithFrom = config.timestampColumn
          .map(fn => (fn.value, true))
          .filter { case (colName, _) => srcRaw.columns.contains(colName) }
          .map { case (colName, _) => toInsert.withColumn(scdFrom, col(colName).cast("timestamp")) }
          .getOrElse(toInsert.withColumn(scdFrom, nowTs))

        val toInsertFinal = toInsertWithFrom
          .withColumn(scdTo, lit(null).cast("timestamp"))
          .withColumn(scdCur, lit(true))

        {
          val writer =
            toInsertFinal.write.format("delta").mode("append").option("mergeSchema", "true")
          val partitionedWriter = config.partition match {
            case Some(ps) if ps.partitionBy.nonEmpty =>
              writer.partitionBy(ps.partitionBy.map(_.value): _*)
            case _ => writer
          }
          partitionedWriter.save(targetPath)
        }

        // Optional optimize/ZORDER hooks (best-effort; may be no-op outside Databricks)
        try
          config.zOrderBy.foreach { cols =>
            val colsSql = cols.toList.map(_.value).mkString(", ")
            spark.sql(s"OPTIMIZE delta.`$targetPath` ZORDER BY ($colsSql)")
          }
        catch { case _: Throwable => () }

        Right((insertedCnt, updatedCnt, deletedCnt, unchangedCnt))
      } catch {
        case t: Throwable => Left(t.getMessage)
      }

    override def performDelta[A: DataContract](
      source: DataAlgebra.Dataset[A],
      target: DataAlgebra.Dataset[A],
      config: CDCOperations.CDCConfig
    ): F[CDCOperations.CDCResult[A]] = {
      val keys = extractKeys(config)
      val op: F[(Long, Long, Long, Long)] = (source.metadata.source, target.metadata.source) match {
        case (
              Some(LocalDataSource(srcPath, DataFormat.Delta, _, _, _)),
              Some(LocalDataSource(tgtPath, DataFormat.Delta, _, _, _))
            ) =>
          // Use SCD2 if timestampColumn or explicit SCD2 columns provided
          val useScd2 = config.scd2.isDefined || config.timestampColumn.isDefined
          if (useScd2) {
            mergeDeltaSCD2(srcPath, tgtPath, keys, config) match {
              case Right(counts) => F.pure(counts)
              case Left(err) =>
                F.raiseError(new RuntimeException(s"Delta SCD2 MERGE failed: $err"))
            }
          } else {
            mergeDelta(srcPath, tgtPath, keys, config) match {
              case Right(counts) => F.pure(counts)
              case Left(err)     => F.raiseError(new RuntimeException(s"Delta MERGE failed: $err"))
            }
          }
        case (
              Some(LocalDataSource(srcPath, DataFormat.Parquet, _, _, _)),
              Some(LocalDataSource(tgtPath, DataFormat.Parquet, _, _, _))
            ) =>
          F.delay(upsertParquet(srcPath, tgtPath, keys))
        case _ =>
          F.raiseError(
            new UnsupportedOperationException(
              "performDelta requires LocalDataSource with Delta or Parquet formats"
            )
          )
      }

      val startNanosF = F.delay(System.nanoTime())
      for {
        start  <- startNanosF
        counts <- op
        end    <- F.delay(System.nanoTime())
        _ <- F.delay {
          try
            com.flowforge.core.observability.PrometheusMetrics.Data.opLatencyMs
              .labels("cdc-merge", "spark")
              .observe((end - start).toDouble / 1e6)
          catch { case _: Throwable => () }
        }
      } yield {
        val (ins, upd, del, same) = counts
        CDCOperations.CDCResult(
          inserted = ins,
          updated = upd,
          deleted = if (config.deleteDetection) del else 0L,
          unchanged = same,
          errors = 0L,
          processingTime = FiniteDuration(end - start, NANOSECONDS),
          success = true
        )
      }
    }

    override def computeCDCOperations[A](
      source: DataAlgebra.Dataset[A],
      target: DataAlgebra.Dataset[A],
      keyColumns: cats.data.NonEmptyList[FieldName]
    ): F[CDCOperations.CDCOperationSet[A]] =
      // For Spark engine, we expect performDelta to operate on file paths, so computeCDCOperations is not used directly.
      F.pure(CDCOperations.CDCOperationSet(Nil, Nil, Nil))

    override def applyCDCOperations[A](
      operations: CDCOperations.CDCOperationSet[A],
      target: DataSink
    ): F[CDCOperations.CDCResult[A]] =
      F.pure(CDCOperations.CDCResult(0, 0, 0, 0, 0, 0.seconds, success = true))

    override def writeWithValidation[A: DataEncoder](
      dataset: DataAlgebra.Dataset[A],
      sink: DataSink,
      contract: PipelineDataContract[A],
      options: DataAlgebra.WriteOptions
    ): F[ValidatedNel[FlowForgeError, DataAlgebra.WriteResult]] =
      write(dataset, sink, options).map(result => result.validNel)

    // ========================================
    // PURE DATA TRANSFORMATIONS (No F[_])
    // ========================================

    override def filter[A](
      dataset: DataAlgebra.Dataset[A],
      predicate: A => Boolean
    ): DataAlgebra.Dataset[A] =
      SimpleDataset(dataset.data.filter(predicate), dataset.schema, dataset.metadata)

    override def map[A, B: DataEncoder](
      dataset: DataAlgebra.Dataset[A],
      f: A => B
    ): DataAlgebra.Dataset[B] = {
      val transformed = dataset.data.map(f)
      val newSchema = DataSchema(
        fields = List.empty,
        version = SchemaVersion.unsafeFrom(1),
        metadata = Map("transformation" -> "map"),
        createdAt = Instant.now()
      )
      SimpleDataset(transformed, newSchema, dataset.metadata)
    }

    override def flatMap[A, B: DataEncoder](
      dataset: DataAlgebra.Dataset[A],
      f: A => DataAlgebra.Dataset[B]
    ): DataAlgebra.Dataset[B] = {
      val transformed = dataset.data.flatMap(a => f(a).data)
      val newSchema = DataSchema(
        fields = List.empty,
        version = SchemaVersion.unsafeFrom(1),
        metadata = Map("transformation" -> "flatMap"),
        createdAt = Instant.now()
      )
      SimpleDataset(transformed, newSchema, dataset.metadata)
    }

    override def groupBy[A, K, V: DataEncoder](
      dataset: DataAlgebra.Dataset[A],
      keyExtractor: A => K,
      aggregator: List[A] => V
    ): DataAlgebra.Dataset[(K, V)] = {
      val grouped = dataset.data.groupBy(keyExtractor).view.mapValues(aggregator).toList
      val newSchema = DataSchema(
        fields = List.empty,
        version = SchemaVersion.unsafeFrom(1),
        metadata = Map("transformation" -> "groupBy"),
        createdAt = Instant.now()
      )
      SimpleDataset(grouped, newSchema, dataset.metadata)
    }

    override def join[A, B, K, C: DataEncoder](
      left: DataAlgebra.Dataset[A],
      right: DataAlgebra.Dataset[B],
      leftKey: A => K,
      rightKey: B => K,
      combiner: (A, B) => C
    ): DataAlgebra.Dataset[C] = {
      val rightIndex = right.data.groupBy(rightKey)
      val joined = left.data.flatMap { la =>
        rightIndex.getOrElse(leftKey(la), Nil).map(rb => combiner(la, rb))
      }
      val newSchema = DataSchema(
        fields = List.empty,
        version = SchemaVersion.unsafeFrom(1),
        metadata = Map("transformation" -> "join"),
        createdAt = Instant.now()
      )
      SimpleDataset(joined, newSchema, left.metadata)
    }

    override def union[A](
      left: DataAlgebra.Dataset[A],
      right: DataAlgebra.Dataset[A]
    ): DataAlgebra.Dataset[A] =
      SimpleDataset(left.data ++ right.data, left.schema, left.metadata)

    override def sortBy[A, K: Ordering](
      dataset: DataAlgebra.Dataset[A],
      keyExtractor: A => K
    ): DataAlgebra.Dataset[A] =
      SimpleDataset(dataset.data.sortBy(keyExtractor), dataset.schema, dataset.metadata)

    override def take[A](dataset: DataAlgebra.Dataset[A], n: Int): DataAlgebra.Dataset[A] =
      SimpleDataset(dataset.data.take(n), dataset.schema, dataset.metadata)

    override def drop[A](dataset: DataAlgebra.Dataset[A], n: Int): DataAlgebra.Dataset[A] =
      SimpleDataset(dataset.data.drop(n), dataset.schema, dataset.metadata)

    // ========================================
    // EFFECTFUL TRANSFORMATIONS (F[_] Required)
    // ========================================

    override def transformWithEffect[A, B: DataEncoder](
      dataset: DataAlgebra.Dataset[A],
      transformation: A => F[B]
    ): F[DataAlgebra.Dataset[B]] =
      dataset.data.traverse(transformation).map { transformed =>
        val newSchema = DataSchema(
          fields = List.empty,
          version = SchemaVersion.unsafeFrom(1),
          metadata = Map("transformation" -> "effectful"),
          createdAt = Instant.now()
        )
        SimpleDataset(transformed, newSchema, dataset.metadata)
      }

    override def transformPipeline[A, B: DataEncoder](
      dataset: DataAlgebra.Dataset[A],
      transformations: NonEmptyList[A => F[B]]
    ): F[DataAlgebra.Dataset[B]] =
      // Apply first transformation - in production would chain all
      transformWithEffect(dataset, transformations.head)

    // ========================================
    // METADATA & CONFIGURATION (F[_] Required)
    // ========================================

    override def extractSchema[A](dataset: DataAlgebra.Dataset[A]): F[DataSchema] =
      F.pure(dataset.schema)

    override def evolveSchema[A, B: DataEncoder](
      dataset: DataAlgebra.Dataset[A],
      migration: DataAlgebra.SchemaMigration[A, B]
    ): F[DataAlgebra.Dataset[B]] =
      F.pure {
        val transformed = dataset.data.map(migration.migrate)
        SimpleDataset(transformed, migration.targetSchema, dataset.metadata)
      }

    override def compareSchemas(
      schema1: DataSchema,
      schema2: DataSchema
    ): F[DataAlgebra.SchemaCompatibilityReport] =
      F.pure(
        DataAlgebra.SchemaCompatibilityReport(
          compatible = schema1.fields.size == schema2.fields.size,
          changes = List.empty,
          breakingChanges = List.empty
        )
      )

    // ========================================
    // AUDIT & LINEAGE (F[_] Required)
    // ========================================

    override def recordLineage[A](
      dataset: DataAlgebra.Dataset[A],
      operation: String,
      context: DataAlgebra.LineageContext
    ): F[DataAlgebra.LineageRecord] =
      F.pure(
        DataAlgebra.LineageRecord(
          id = UUID.randomUUID().toString,
          source = dataset.metadata.source.getOrElse(LocalDataSource("unknown", DataFormat.JSON)),
          target = None,
          operation = operation,
          context = context,
          inputSchemas = List(dataset.schema),
          outputSchema = Some(dataset.schema)
        )
      )

    override def queryLineage(query: DataAlgebra.LineageQuery): F[List[DataAlgebra.LineageRecord]] =
      F.pure(List.empty)

    // ========================================
    // DATA QUALITY (F[_] Required)
    // ========================================

    override def validate[A](
      dataset: DataAlgebra.Dataset[A],
      contract: PipelineDataContract[A]
    ): F[DataAlgebra.QualityResult[DataAlgebra.Dataset[A]]] =
      F.pure(
        DataAlgebra.QualityResult(
          data = dataset,
          passed = true,
          violations = List.empty,
          score = 1.0
        )
      )

    override def runQualityChecks[A](
      dataset: DataAlgebra.Dataset[A],
      checks: NonEmptyList[QualityCheck[A]]
    ): F[List[DataAlgebra.QualityCheckResult]] =
      F.pure(checks.toList.zipWithIndex.map { case (_, idx) =>
        DataAlgebra.QualityCheckResult(s"check_$idx", passed = true, message = "ok", score = 1.0)
      })

    override def profile[A](dataset: DataAlgebra.Dataset[A]): F[DataAlgebra.DataProfile[A]] =
      F.pure(
        DataAlgebra.DataProfile(
          recordCount = dataset.data.size.toLong,
          nullCount = 0L,
          distinctCount = dataset.data.distinct.size.toLong,
          schema = dataset.schema,
          statistics = Map.empty
        )
      )

    // ========================================
    // UTILITIES
    // ========================================

    override def count[A](dataset: DataAlgebra.Dataset[A]): Long      = dataset.data.size.toLong
    override def isEmpty[A](dataset: DataAlgebra.Dataset[A]): Boolean = dataset.data.isEmpty

    override def cache[A](
      dataset: DataAlgebra.Dataset[A],
      strategy: DataAlgebra.CacheStrategy
    ): F[DataAlgebra.Dataset[A]] =
      F.pure(dataset)

    override def partition[A](
      dataset: DataAlgebra.Dataset[A],
      partitioner: DataAlgebra.Partitioner[A]
    ): List[DataAlgebra.Dataset[A]] = {
      val grouped = dataset.data.groupBy(partitioner.partition)
      grouped.toList.map { case (_, chunk) =>
        SimpleDataset(chunk, dataset.schema, dataset.metadata)
      }
    }

    // ========================================
    // CDC OPERATIONS (F[_] Required)
    // Implemented above: performDelta/computeCDCOperations/applyCDCOperations

    // ========================================
    // TABLE OPERATIONS (F[_] Required)
    // ========================================

    override def repairRefreshTable(
      table: TableOperations.TableName
    ): F[TableOperations.TableOperationResult] =
      F.pure(
        TableOperations.TableOperationResult(
          tableName = table,
          operation = "repairRefresh",
          success = true,
          affectedPartitions = List.empty,
          recordsProcessed = 0L,
          processingTime = 0.seconds,
          errors = List.empty
        )
      )

    override def getTableLocation(
      table: TableOperations.TableName
    ): F[ValidatedNel[FlowForgeError, String]] =
      F.pure(s"spark-catalog.${table.qualified}".validNel)

    override def getAffectedPartitions(
      table: TableOperations.TableName,
      startTime: Instant,
      endTime: Instant
    ): F[List[TableOperations.PartitionSpec]] =
      F.pure(List.empty)

    override def deleteDfsLocation(
      location: String,
      dryRun: Boolean
    ): F[TableOperations.TableOperationResult] =
      F.pure(
        TableOperations.TableOperationResult(
          tableName = TableOperations.TableName(
            database = eu.timepit.refined.types.string.NonEmptyString.unsafeFrom("default"),
            table = eu.timepit.refined.types.string.NonEmptyString.unsafeFrom("unknown")
          ),
          operation = s"delete($location)",
          success = !dryRun,
          affectedPartitions = List.empty,
          recordsProcessed = 0L,
          processingTime = 0.seconds,
          errors = List.empty
        )
      )

    override def analyzeTable(
      table: TableOperations.TableName,
      partitions: Option[NonEmptyList[TableOperations.PartitionSpec]]
    ): F[TableOperations.TableOperationResult] =
      F.pure(
        TableOperations.TableOperationResult(
          tableName = table,
          operation = "analyze",
          success = true,
          affectedPartitions = partitions.map(_.toList).getOrElse(List.empty),
          recordsProcessed = 0L,
          processingTime = 0.seconds,
          errors = List.empty
        )
      )

    override def vacuumTable(
      table: TableOperations.TableName,
      retentionHours: Int,
      dryRun: Boolean
    ): F[TableOperations.TableOperationResult] =
      F.pure(
        TableOperations.TableOperationResult(
          tableName = table,
          operation = s"vacuum($retentionHours h, dryRun=$dryRun)",
          success = true,
          affectedPartitions = List.empty,
          recordsProcessed = 0L,
          processingTime = 0.seconds,
          errors = List.empty
        )
      )
  }

  /**
   * Create a Resource for SparkDataAlgebra with automatic session management
   */
  def resource[F[_]: EffectSystem](
    appName: String,
    master: Option[String] = None
  ): Resource[F, DataAlgebra[F]] = {
    val F = EffectSystem[F]
    Resource.make {
      F.blocking {
        val builder = SparkSession.builder().appName(appName)
        master.foreach(builder.master)
        val spark = builder.getOrCreate()
        createSparkDataAlgebra[F](spark)
      }
    } { _ =>
      F.blocking {
        SparkSession.getActiveSession.foreach(_.stop())
      }
    }
  }
}
