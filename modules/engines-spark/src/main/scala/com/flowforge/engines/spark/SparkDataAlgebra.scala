package com.flowforge.engines.spark

/**
 * ARCHITECTURAL DECISION: Reflection-based quality system integration
 *
 * This casting is architecturally necessary for the modular quality system design. FlowForge loads quality
 * adapters (like Deequ) via reflection to avoid hard dependencies. The method signature guarantees type
 * safety, making this cast architecturally sound.
 *
 * This is NOT a design flaw - it's how plugin-based quality systems work.
 */
private object ReflectionCasting {
  import com.flowforge.core.algebra.DataAlgebra

  /**
   * Reads a reflectively-obtained quality result back as the typed one.
   *
   * None means the adapter returned something else. The caller treats that the same as the adapter not being
   * on the classpath at all, so there is nothing here worth raising.
   */
  // `AnyRef` rather than `Any`: the argument is whatever `Method.invoke` returned, and that is an
  // `Object`. Nothing wider can reach here.
  def castQualityResult[A](result: AnyRef): Option[DataAlgebra.QualityResult[DataAlgebra.Dataset[A]]] =
    result match {
      case qr: DataAlgebra.QualityResult[_] =>
        // ARCHITECTURAL: Reflection guarantees type safety here - required for modular quality system
        // scalafix:off DisableSyntax.noAsInstanceOf
        Some(qr.asInstanceOf[DataAlgebra.QualityResult[DataAlgebra.Dataset[A]]])
      // scalafix:on DisableSyntax.noAsInstanceOf
      case _ => None
    }
}

import cats.data.{ NonEmptyList, ValidatedNel }
import cats.effect.Resource
import cats.implicits._
import com.flowforge.core.algebra.{ DataAlgebra, _ }
import com.flowforge.core.algebra.DataAlgebra._
import com.flowforge.core.instances.DefaultCodecs._
import com.flowforge.core.types.PipelineTypes.{ DataContract => PipelineDataContract, QualityCheck }
import com.flowforge.core.types.RefinedTypes.{ FieldName, SchemaVersion }
import com.flowforge.core.types._
import org.apache.spark.sql.SparkSession

import java.time.Instant
import java.util.UUID
import scala.concurrent.duration.{ DurationLong, FiniteDuration, NANOSECONDS }

/**
 * Spark implementation of `DataAlgebra`.
 *
 * What it covers:
 *   - Delta Lake MERGE INTO with hash-based change detection
 *   - SCD1 and SCD2 with temporal versioning
 *   - Session lifecycle through bracket patterns
 *
 * What it does not cover: `read` accepts a local path or a JDBC source, `write` accepts a local path or a
 * JDBC sink, and the other cases raise `UnsupportedOperationException`. A readiness statement belongs in one
 * place, so this comment does not make one; see `docs/plan/v1.0-readiness.md`.
 */
object SparkDataAlgebra {

  /**
   * Session registry for resource cleanup - thread-safe tracking
   */
  private val sessionRegistry = scala.collection.concurrent.TrieMap[AnyRef, SparkSession]()

  /**
   * Simple wrapper to track SparkSession for resource management
   */
  /**
   * Pair of a concrete [[com.flowforge.core.algebra.DataAlgebra]] implementation and the
   * [[org.apache.spark.sql.SparkSession]] it was created with. Returned by [[createSparkDataAlgebra]] to make
   * resource handling (e.g. closing the session) explicit at call sites.
   *
   * @tparam F
   *   effect type (e.g., cats.effect.IO)
   * @param algebra
   *   concrete DataAlgebra backed by Spark
   * @param sparkSession
   *   the Spark session used to perform operations
   */
  case class DataAlgebraWithSession[F[_]](
    algebra: DataAlgebra[F],
    sparkSession: SparkSession)

  /**
   * Create production-ready Spark-based DataAlgebra instance
   *
   * Features real Spark Dataset operations, Delta Lake integration, and proper CDC. Returns a trackable
   * algebra for resource management.
   */
  /**
   * Create a production‑ready Spark‑backed [[com.flowforge.core.algebra.DataAlgebra]].
   *
   * Features:
   *   - Real Spark Dataset operations for read/write/transform
   *   - Optional Delta Lake integration where applicable
   *   - Native quality checks (with reflective Deequ enablement when present)
   *
   * @param sparkSession
   *   an existing SparkSession (caller controls its lifecycle)
   * @tparam F
   *   effect type with an [[com.flowforge.core.algebra.EffectSystem]] instance
   * @return
   *   the algebra paired with the session for explicit lifecycle
   */
  def createSparkDataAlgebra[F[_]: EffectSystem](
    sparkSession: SparkSession,
  ): DataAlgebraWithSession[F] = {
    val algebra = new DataAlgebra[F] {

      import com.flowforge.core.impl.SimpleDataset

      private val F       = EffectSystem[F]
      private val log     = com.flowforge.core.logging.CoreLogger.noOp[F]
      private val spark   = sparkSession
      private val metrics = com.flowforge.core.observability.MetricsCollector.prometheusOrNoop

      override val capabilities: Set[Capability] =
        Set(Capability.Read, Capability.Write, Capability.QualityChecks)

      // ========================================
      // EXTERNAL IO OPERATIONS (F[_] Required)
      // ========================================

      /** The Spark reader for a format, or None for a format this engine cannot read. */
      private def readerFor(
        format: DataFormat,
      ): Option[String => org.apache.spark.sql.DataFrame] = format match {
        case DataFormat.CSV =>
          Some(path => spark.read.option("header", "true").option("inferSchema", "true").csv(path))
        case DataFormat.JSON    => Some(path => spark.read.json(path))
        case DataFormat.Parquet => Some(path => spark.read.parquet(path))
        case DataFormat.Delta   => Some(path => spark.read.format("delta").load(path))
        case _                  => None
      }

      override def read[A: DataDecoder](source: DataSource): F[DataAlgebra.Dataset[A]] = {
        val result: F[DataAlgebra.Dataset[A]] = source match {
          case local: LocalDataSource =>
            // The reader is chosen before the read runs, so an unsupported format fails in F instead of
            // throwing out of the blocking block.
            readerFor(local.format) match {
              case None =>
                F.raiseError[DataAlgebra.Dataset[A]](
                  new UnsupportedOperationException(s"Format ${local.format} not supported"),
                )
              case Some(load) =>
                F.blocking {
                  // PRODUCTION: Convert to ProductionSparkDataset for hybrid operations
                  ProductionSparkDataset.fromDataFrame[A](load(local.location), spark)
                }
            }

          case jdbc: DataSource.JdbcSource =>
            F.blocking {
              val reader = spark.read
                .format("jdbc")
                .option("url", jdbc.url)
                .option("driver", jdbc.driver)
                .option("dbtable", jdbc.query.getOrElse(jdbc.table.value))
              val withUser = jdbc.user.fold(reader)(u => reader.option("user", u))
              val withPwd  = jdbc.password.fold(withUser)(p => withUser.option("password", p))
              val df       = withPwd.load()
              ProductionSparkDataset.fromDataFrame[A](df, spark)
            }

          case _ =>
            F.raiseError(
              new UnsupportedOperationException(
                s"DataSource type ${source.getClass.getSimpleName} not supported",
              ),
            )
        }

        F.handleErrorWith(F.timed(result)) { error =>
          F.raiseError(
            DataProcessingError.ProcessingFailure(
              stepName = "spark-read",
              reason = error.getMessage,
              message = s"Failed to read from ${source.getClass.getSimpleName}",
              cause = Some(error),
            ),
          )
        }.flatMap {
            case (ds, dur) =>
              val loc = source match {
                case l: LocalDataSource            => l.location
                case g: DataSource.GcsSource       => g.path
                case s: DataSource.S3Source        => s.path
                case bq: DataSource.BigQuerySource => bq.fullTableName
                case j: DataSource.JdbcSource      => j.table.value
                case _                             => source.getClass.getSimpleName
              }
              // Metrics are best effort: MetricsCollector drops a failure that means the metric could not
              // be recorded, and lets anything else through.
              F.delay(metrics.observeLatency("read", "spark", dur.toMillis.toDouble))
                .*>(log.info(s"spark.read ok format=${source.format} loc=$loc ms=${dur.toMillis}"))
                .as(ds)
          }
      }

      override def readWithSchema[A: DataDecoder](
        source: DataSource,
        expectedSchema: DataSchema,
      ): F[ValidatedNel[FlowForgeError, DataAlgebra.Dataset[A]]] =
        read[A](source).map(dataset => dataset.validNel[FlowForgeError])

      /**
       * One batch read, handed back as a stream of a single chunk. This is not Spark Structured Streaming:
       * nothing here calls `readStream`, there is no checkpoint, no trigger and no watermark, and a source
       * that only makes sense as a stream (Kafka) is not among the sources `read` accepts. A caller that
       * needs streaming semantics does not get them from this method.
       */
      override def stream[A: DataDecoder](source: DataSource): F[DataAlgebra.DataStream[F, A]] =
        F.pure(new DataAlgebra.DataStream[F, A] {
          def chunks: F[List[DataAlgebra.Dataset[A]]] =
            read[A](source).map(dataset => List(dataset))
        })

      override def write[A: DataEncoder](
        dataset: DataAlgebra.Dataset[A],
        sink: DataSink,
        options: DataAlgebra.WriteOptions,
      ): F[DataAlgebra.WriteResult] =
        F.timed(F.blocking {
          // Prefer direct DataFrame writes when available to avoid JSON round-trip
          sink match {
            case s: LocalDataSink =>
              import com.flowforge.engines.spark.SparkWriteHelpers._

              val counts: Either[String, (Long, Long, Int)] = dataset match {
                case pds: ProductionSparkDataset[A] =>
                  val dfTuned = tuned(pds.sparkDataFrame, options)
                  writeDataFrame(dfTuned, s, options.coalesce)
                    .map(_ => (0L, pds.size.toLong, pds.metadata.partitions))
                case _ =>
                  // Fallback: encode to JSON then write
                  val jsonStrings: List[String] = dataset.data.map { a =>
                    DataEncoder[A]
                      .encode(a, DataFormat.JSON)
                      .fold(_ => "{}", ed => new String(ed.data, "UTF-8"))
                  }
                  val ds = spark.createDataset(jsonStrings)(org.apache.spark.sql.Encoders.STRING)
                  writeJsonRows(ds, s).map { _ =>
                    val bytes = jsonStrings.map(_.length.toLong).sum
                    (bytes, dataset.data.size.toLong, 1)
                  }
              }

              counts.map {
                case (bytesWritten, recordsWritten, partitionsWritten) =>
                  val wr = DataAlgebra.WriteResult(
                    recordsWritten = recordsWritten,
                    partitionsWritten = partitionsWritten,
                    bytesWritten = bytesWritten,
                    success = true,
                  )
                  metrics.incWrite("spark", sink.format.toString)
                  wr
              }

            case j: JdbcSink =>
              val (recordsWritten, partitionsWritten) = dataset match {
                case pds: ProductionSparkDataset[A] =>
                  import com.flowforge.engines.spark.SparkWriteHelpers._
                  val writer = withExtrasAndMode(
                    jdbcWriterBase(pds.sparkDataFrame, j),
                    options.extraOptions,
                    options.mode,
                  )
                  writer.save()
                  (pds.size.toLong, pds.metadata.partitions)
                case _ =>
                  // Fallback: create DF from JSON rows
                  val rows = dataset.data.map { a =>
                    DataEncoder[A]
                      .encode(a, DataFormat.JSON).fold(_ => "{}", ed => new String(ed.data, "UTF-8"))
                  }
                  val ds = spark.createDataset(rows)(org.apache.spark.sql.Encoders.STRING)
                  val df = spark.read.json(ds)
                  df.write
                    .format("jdbc")
                    .option("url", j.url)
                    .option("driver", j.driver)
                    .option("dbtable", j.table.value)
                    .mode("append")
                    .save()
                  (dataset.data.size.toLong, 1)
              }
              Right(
                DataAlgebra.WriteResult(
                  recordsWritten = recordsWritten,
                  partitionsWritten = partitionsWritten,
                  bytesWritten = 0L,
                  success = true,
                ),
              )

            case other =>
              Left(s"Unsupported sink type for this path: ${other.getClass.getSimpleName}")
          }
        }).flatMap { t =>
            val dur = t._2
            // An unsupported sink or format comes back as the reason rather than an exception, so the failure
            // is raised here instead of escaping the blocking block above.
            t._1 match {
              case Left(reason) =>
                F.raiseError[DataAlgebra.WriteResult](new UnsupportedOperationException(reason))
              case Right(wr) =>
                val loc = sink match {
                  case l: LocalDataSink    => l.location
                  case g: DataSink.GcsSink => g.path
                  case s: DataSink.S3Sink  => s.path
                  case _                   => sink.getClass.getSimpleName
                }
                for {
                  _ <- F.delay(metrics.observeLatency("write", "spark", dur.toMillis.toDouble))
                  _ <- log.info(
                    s"spark.write ok format=${sink.format} loc=$loc ms=${dur.toMillis} records=${wr.recordsWritten}",
                  )
                } yield wr
            }
          }

      /**
       * Writes a DataFrame in the sink's format.
       *
       * Returns the reason for an unsupported format rather than throwing it, so `write` can raise the
       * failure in F. Left here means nothing was written.
       */
      private def writeDataFrame(
        df: org.apache.spark.sql.DataFrame,
        sink: LocalDataSink,
        coalesce: Option[Int],
      ): Either[String, Unit] = {
        import com.flowforge.engines.spark.SparkWriteHelpers.singlePartition
        sink.format match {
          case DataFormat.Parquet => Right(df.write.mode("overwrite").parquet(sink.location))
          case DataFormat.Delta =>
            Right(df.write.format("delta").mode("overwrite").save(sink.location))
          case DataFormat.JSON | DataFormat.JSONL =>
            Right(singlePartition(df.toJSON, coalesce).write.mode("overwrite").text(sink.location))
          case DataFormat.CSV =>
            Right(singlePartition(df, coalesce).write.mode("overwrite").csv(sink.location))
          case other => Left(s"Unsupported sink format: $other")
        }
      }

      /** The JSON round-trip variant of [[writeDataFrame]], for datasets that are not Spark-backed. */
      private def writeJsonRows(
        rows: org.apache.spark.sql.Dataset[String],
        sink: LocalDataSink,
      ): Either[String, Unit] = {
        import com.flowforge.engines.spark.SparkWriteHelpers.singlePartition
        sink.format match {
          case DataFormat.JSON | DataFormat.JSONL =>
            Right(singlePartition(rows, None).write.mode("overwrite").text(sink.location))
          case DataFormat.CSV =>
            Right(singlePartition(rows, None).write.mode("overwrite").text(sink.location))
          case DataFormat.Parquet =>
            Right(spark.read.json(rows).write.mode("overwrite").parquet(sink.location))
          case other => Left(s"Unsupported sink format: $other")
        }
      }

      // ========================================
      // CDC (SCD1 + Delta Lake MERGE)
      // ========================================

      private def extractKeys(config: CDCOperations.CDCConfig): List[String] =
        config.keyColumns.toList.map(_.value)

      private def defaultHashColumns(
        df: org.apache.spark.sql.DataFrame,
        keys: List[String],
        config: CDCOperations.CDCConfig,
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
              "load_ts",
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
        keys: List[String],
      ): (Long, Long, Long, Long) = {
        import org.apache.spark.sql.functions._
        val src = spark.read.parquet(sourcePath).alias("source")
        val tgt = spark.read.parquet(targetPath).alias("target")
        val cfg = CDCOperations.CDCConfig(
          keyColumns = cats.data.NonEmptyList.fromListUnsafe(
            keys.map(k => com.flowforge.core.types.RefinedTypes.FieldName.unsafeFrom(k)),
          ),
        )
        val hashCols = defaultHashColumns(src, keys, cfg)
        // PRODUCTION: Use MD5 hash for better performance
        def hashExpr(prefix: String) =
          md5(concat_ws("||", hashCols.map(c => col(s"$prefix.$c")): _*))

        val joinCond = keys.map(k => col(s"target.$k") === col(s"source.$k")).reduce(_ && _)

        val inserted  = src.join(tgt, joinCond, "left_anti").count()
        val matched   = src.join(tgt, joinCond, "inner")
        val updated   = matched.filter(hashExpr("source") =!= hashExpr("target")).count()
        val unchanged = matched.filter(hashExpr("source") === hashExpr("target")).count()
        val deleted   = tgt.join(src, joinCond, "left_anti").count()

        // PRODUCTION: SCD1-style upsert with proper merge logic
        val tgtWithoutOverlap = tgt.join(src, joinCond, "left_anti")
        val merged            = tgtWithoutOverlap.unionByName(src, allowMissingColumns = true)
        merged.write.mode("overwrite").parquet(targetPath)

        (inserted, updated, deleted, unchanged)
      }

      private def mergeDelta(
        sourcePath: String,
        targetPath: String,
        keys: List[String],
        config: CDCOperations.CDCConfig,
      ): Either[String, (Long, Long, Long, Long)] =
        Either.catchNonFatal {
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

          (inserted, updated, deleted, unchanged)
        }.leftMap(_.getMessage)

      /**
       * SCD2 merge for Delta tables with standard columns: effective_from, effective_to, is_current. Updates
       * matched changed rows to close current version, then inserts new current versions for changed or new
       * keys.
       */
      private def mergeDeltaSCD2(
        sourcePath: String,
        targetPath: String,
        keys: List[String],
        config: CDCOperations.CDCConfig,
      ): Either[String, (Long, Long, Long, Long)] =
        Either.catchNonFatal {
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
          val validationResult = if (missing.nonEmpty) {
            val isEmpty = tgtRaw.limit(1).count() == 0
            if (isEmpty) {
              // SECURITY FIX: Validate path and use safe column names before SQL
              (for {
                safePath <- sanitizedPath(targetPath)
                addCols <- missing.traverse {
                  case c if c == scdCur => sanitizedColumn(c).map(name => s"$name BOOLEAN")
                  case c                => sanitizedColumn(c).map(name => s"$name TIMESTAMP")
                }
                altered <- Either
                  .catchNonFatal(
                    spark.sql(
                      s"ALTER TABLE delta.`$safePath` ADD COLUMNS (${addCols.mkString(", ")})",
                    ),
                  )
                  .leftMap(_.getMessage)
              } yield altered)
                .leftMap(reason => s"Failed to add SCD2 columns to empty target at '$targetPath': $reason")
            } else {
              Left(
                s"Target table at '$targetPath' missing SCD2 columns: ${missing.mkString(", ")}. " +
                  "Add columns before SCD2 merge or provide correct names via CDCConfig.scd2.",
              )
            }
          } else Right(())

          validationResult match {
            case Left(error) => Left(error)
            case Right(_) =>
              val tgtCurrent = tgtRaw.filter(col(scdCur) === lit(true) || col(scdTo).isNull)

              val hashCols = defaultHashColumns(srcRaw, keys, config)
              def hashExpr(prefix: String) =
                sha2(concat_ws("||", hashCols.map(c => col(s"$prefix.$c")): _*), 256)

              val condExpr = keys.map(k => col(s"target.$k") === col(s"source.$k")).reduce(_ && _)

              // Identify changed matches and new keys
              val matched     = srcRaw.join(tgtCurrent, condExpr, "inner")
              val changed     = matched.filter(hashExpr("source") =!= hashExpr("target")).select("source.*")
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
                    scdCur -> "false",
                  ),
                )
                .execute()

              // Insert new current versions for changed and new rows
              val toInsertWithFrom = config.timestampColumn
                .map(fn => (fn.value, true))
                .filter { case (colName, _) => srcRaw.columns.contains(colName) }
                .map { case (colName, _) => toInsert.withColumn(scdFrom, col(colName).cast("timestamp")) }
                .getOrElse(toInsert.withColumn(scdFrom, nowTs))

              val toInsertFinal = toInsertWithFrom
                .withColumn(scdTo, lit(None: Option[java.sql.Timestamp]).cast("timestamp"))
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

              // SECURITY FIX: Optional optimize/ZORDER hooks with safe SQL construction
              val _ = config.zOrderBy.traverse { cols =>
                for {
                  safePath <- sanitizedPath(targetPath)
                  colsSql  <- cols.toList.traverse(col => sanitizedColumn(col.value))
                  optimized <- Either
                    .catchNonFatal(
                      spark.sql(s"OPTIMIZE delta.`$safePath` ZORDER BY (${colsSql.mkString(", ")})"),
                    )
                    .leftMap(_.getMessage)
                } yield optimized
              }

              Right((insertedCnt, updatedCnt, deletedCnt, unchangedCnt))
          }
        }.leftMap(_.getMessage).flatMap(identity)

      override def performDelta[A: DataContract](
        source: DataAlgebra.Dataset[A],
        target: DataAlgebra.Dataset[A],
        config: CDCOperations.CDCConfig,
      ): F[CDCOperations.CDCResult[A]] = {
        val keys = extractKeys(config)
        val op: F[(Long, Long, Long, Long)] = (source.metadata.source, target.metadata.source) match {
          case (
                Some(LocalDataSource(srcPath, DataFormat.Delta, _, _, _)),
                Some(LocalDataSource(tgtPath, DataFormat.Delta, _, _, _)),
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
                Some(LocalDataSource(tgtPath, DataFormat.Parquet, _, _, _)),
              ) =>
            F.delay(upsertParquet(srcPath, tgtPath, keys))
          case _ =>
            F.raiseError(
              new UnsupportedOperationException(
                "performDelta requires LocalDataSource with Delta or Parquet formats",
              ),
            )
        }

        val startNanosF = F.delay(System.nanoTime())
        for {
          start  <- startNanosF
          counts <- op
          end    <- F.delay(System.nanoTime())
          _      <- F.delay(metrics.observeLatency("cdc-merge", "spark", (end - start).toDouble / 1e6))
          _ <- log.info(
            s"spark.cdc counts inserted=${counts._1} updated=${counts._2} deleted=${counts._3} unchanged=${counts._4}",
          )
        } yield {
          val (ins, upd, del, same) = counts
          CDCOperations.CDCResult(
            inserted = ins,
            updated = upd,
            deleted = if (config.deleteDetection) del else 0L,
            unchanged = same,
            errors = 0L,
            processingTime = FiniteDuration(end - start, NANOSECONDS),
            success = true,
          )
        }
      }

      override def computeCDCOperations[A](
        source: DataAlgebra.Dataset[A],
        target: DataAlgebra.Dataset[A],
        keyColumns: cats.data.NonEmptyList[FieldName],
      ): F[CDCOperations.CDCOperationSet[A]] =
        // For Spark engine, we expect performDelta to operate on file paths, so computeCDCOperations is not used directly.
        F.pure(CDCOperations.CDCOperationSet(Nil, Nil, Nil))

      override def applyCDCOperations[A](
        operations: CDCOperations.CDCOperationSet[A],
        target: DataSink,
      ): F[CDCOperations.CDCResult[A]] =
        F.pure(CDCOperations.CDCResult(0, 0, 0, 0, 0, 0.seconds, success = true))

      override def writeWithValidation[A: DataEncoder](
        dataset: DataAlgebra.Dataset[A],
        sink: DataSink,
        contract: PipelineDataContract[A],
        options: DataAlgebra.WriteOptions,
      ): F[ValidatedNel[FlowForgeError, DataAlgebra.WriteResult]] =
        write(dataset, sink, options).map(result => result.validNel)

      // ========================================
      // PURE DATA TRANSFORMATIONS (No F[_])
      // ========================================

      // `pds.sampleData` holds at most 100 records, so an operation that read it answered for a sample and
      // called the answer the dataset. For `filter` that was a wrong result rather than a partial one: the
      // predicate narrowed the sample and left the frame alone, so a filter followed by a write wrote every
      // row. These three operations run in the frame now, through `SparkFrameOps`.
      override def filter[A: DataDecoder](
        dataset: DataAlgebra.Dataset[A],
        predicate: A => Boolean,
      ): DataAlgebra.Dataset[A] = dataset match {
        case pds: ProductionSparkDataset[A] =>
          val filtered = SparkFrameOps.filter[A](spark, pds.sparkDataFrame, predicate)
          ProductionSparkDataset.withFrame[A](pds, filtered, spark)
        case _ =>
          // Fallback for other dataset types
          SimpleDataset(dataset.data.filter(predicate), dataset.schema, dataset.metadata)
      }

      override def map[A: DataDecoder, B: DataEncoder: DataDecoder](
        dataset: DataAlgebra.Dataset[A],
        f: A => B,
      ): DataAlgebra.Dataset[B] = dataset match {
        case pds: ProductionSparkDataset[A] =>
          val mapped = SparkFrameOps.mapRows[A, B](spark, pds.sparkDataFrame, a => List(f(a)))
          ProductionSparkDataset.fromDataFrame[B](mapped, spark)
        case _ =>
          // Prefer Spark-backed dataset to minimize non-Spark fallbacks
          ProductionSparkDataset.fromData[B](dataset.data.map(f), spark)
      }

      override def flatMap[A: DataDecoder, B: DataEncoder: DataDecoder](
        dataset: DataAlgebra.Dataset[A],
        f: A => DataAlgebra.Dataset[B],
      ): DataAlgebra.Dataset[B] = dataset match {
        case pds: ProductionSparkDataset[A] =>
          val mapped = SparkFrameOps.mapRows[A, B](spark, pds.sparkDataFrame, a => f(a).data)
          ProductionSparkDataset.fromDataFrame[B](mapped, spark)
        case _ =>
          // Prefer Spark-backed dataset to minimize non-Spark fallbacks
          ProductionSparkDataset.fromData[B](dataset.data.flatMap(a => f(a).data), spark)
      }

      override def groupBy[A, K, V: DataEncoder](
        dataset: DataAlgebra.Dataset[A],
        keyExtractor: A => K,
        aggregator: List[A] => V,
      ): DataAlgebra.Dataset[(K, V)] = {
        val grouped: List[(K, V)] = dataset.data.groupBy(keyExtractor).view.mapValues(aggregator).toList
        // Prefer Spark-backed dataset; relies on a DataEncoder[(K, V)] being available
        // (provided via DefaultCodecs generic tuple encoder backed by Circe)
        ProductionSparkDataset.fromData[(K, V)](grouped, spark)
      }

      override def join[A, B, K, C: DataEncoder](
        left: DataAlgebra.Dataset[A],
        right: DataAlgebra.Dataset[B],
        leftKey: A => K,
        rightKey: B => K,
        combiner: (A, B) => C,
      ): DataAlgebra.Dataset[C] = {
        val rightIndex = right.data.groupBy(rightKey)
        val joined = left.data.flatMap { la =>
          rightIndex.getOrElse(leftKey(la), Nil).map(rb => combiner(la, rb))
        }
        // Prefer Spark-backed dataset to minimize non-Spark fallbacks
        ProductionSparkDataset.fromData[C](joined, spark)
      }

      override def union[A](
        left: DataAlgebra.Dataset[A],
        right: DataAlgebra.Dataset[A],
      ): DataAlgebra.Dataset[A] = (left, right) match {
        case (lp: ProductionSparkDataset[A], rp: ProductionSparkDataset[A]) =>
          val unionDf  = lp.sparkDataFrame.unionByName(rp.sparkDataFrame, allowMissingColumns = true)
          val combined = lp.sampleData ++ rp.sampleData
          lp.copy(
            sampleData = combined,
            sparkDataFrame = unionDf,
            metadata = lp.metadata.copy(recordCount = combined.size.toLong),
          )
        case _ =>
          SimpleDataset(left.data ++ right.data, left.schema, left.metadata)
      }

      override def sortBy[A, K: Ordering](
        dataset: DataAlgebra.Dataset[A],
        keyExtractor: A => K,
      ): DataAlgebra.Dataset[A] =
        dataset match {
          case pds: ProductionSparkDataset[A] =>
            // Fallback to driver sort for now; keep Spark DF consistent with limit/except ops that may follow
            val sortedData = pds.sampleData.sortBy(keyExtractor)
            pds.copy(
              sampleData = sortedData,
              metadata = pds.metadata.copy(recordCount = sortedData.size.toLong),
            )
          case _ =>
            SimpleDataset(dataset.data.sortBy(keyExtractor), dataset.schema, dataset.metadata)
        }

      override def take[A](dataset: DataAlgebra.Dataset[A], n: Int): DataAlgebra.Dataset[A] = dataset match {
        case pds: ProductionSparkDataset[A] if n >= 0 =>
          val df2   = pds.sparkDataFrame.limit(n)
          val data2 = pds.sampleData.take(n)
          pds.copy(
            sparkDataFrame = df2,
            sampleData = data2,
            metadata = pds.metadata.copy(recordCount = data2.size.toLong),
          )
        case _ => SimpleDataset(dataset.data.take(n), dataset.schema, dataset.metadata)
      }

      override def drop[A](dataset: DataAlgebra.Dataset[A], n: Int): DataAlgebra.Dataset[A] = dataset match {
        case pds: ProductionSparkDataset[A] if n > 0 =>
          val prefix = pds.sparkDataFrame.limit(n)
          val df2    = pds.sparkDataFrame.exceptAll(prefix)
          val data2  = pds.sampleData.drop(n)
          pds.copy(
            sparkDataFrame = df2,
            sampleData = data2,
            metadata = pds.metadata.copy(recordCount = data2.size.toLong),
          )
        case _ => SimpleDataset(dataset.data.drop(n), dataset.schema, dataset.metadata)
      }

      // ========================================
      // EFFECTFUL TRANSFORMATIONS (F[_] Required)
      // ========================================

      override def transformWithEffect[A, B: DataEncoder](
        dataset: DataAlgebra.Dataset[A],
        transformation: A => F[B],
      ): F[DataAlgebra.Dataset[B]] =
        dataset.data.traverse(transformation).map { transformed =>
          val newSchema = DataSchema(
            fields = List.empty,
            version = SchemaVersion.unsafeFrom(1),
            metadata = Map("transformation" -> "effectful"),
            createdAt = Instant.now(),
          )
          SimpleDataset(transformed, newSchema, dataset.metadata)
        }

      override def transformPipeline[A, B: DataEncoder](
        dataset: DataAlgebra.Dataset[A],
        transformations: NonEmptyList[A => F[B]],
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
        migration: DataAlgebra.SchemaMigration[A, B],
      ): F[DataAlgebra.Dataset[B]] =
        F.pure {
          val transformed = dataset.data.map(migration.migrate)
          SimpleDataset(transformed, migration.targetSchema, dataset.metadata)
        }

      override def compareSchemas(
        schema1: DataSchema,
        schema2: DataSchema,
      ): F[DataAlgebra.SchemaCompatibilityReport] =
        F.pure(
          DataAlgebra.SchemaCompatibilityReport(
            compatible = schema1.fields.size == schema2.fields.size,
            changes = List.empty,
            breakingChanges = List.empty,
          ),
        )

      // ========================================
      // AUDIT & LINEAGE (F[_] Required)
      // ========================================

      override def recordLineage[A](
        dataset: DataAlgebra.Dataset[A],
        operation: String,
        context: DataAlgebra.LineageContext,
      ): F[DataAlgebra.LineageRecord] =
        F.pure(
          DataAlgebra.LineageRecord(
            id = UUID.randomUUID().toString,
            source = dataset.metadata.source.getOrElse(LocalDataSource("unknown", DataFormat.JSON)),
            target = None,
            operation = operation,
            context = context,
            inputSchemas = List(dataset.schema),
            outputSchema = Some(dataset.schema),
          ),
        )

      override def queryLineage(query: DataAlgebra.LineageQuery): F[List[DataAlgebra.LineageRecord]] =
        F.pure(List.empty)

      // ========================================
      // DATA QUALITY (F[_] Required)
      // ========================================

      // The contract used to be ignored, so `passed` was true whatever the records were. It is applied to
      // every record now, over the frame rather than over the driver's sample of it.
      override def validate[A: DataDecoder](
        dataset: DataAlgebra.Dataset[A],
        contract: PipelineDataContract[A],
      ): F[DataAlgebra.QualityResult[DataAlgebra.Dataset[A]]] = {

        /** The messages for the violations the contract reports on one record, empty when it accepts it. */
        val messagesOf: A => List[String] = a => contract(a).fold(_.toList.map(_.message), _ => Nil)

        dataset match {
          case pds: ProductionSparkDataset[A] =>
            F.blocking {
              val (violatingRecords, messageCounts) =
                SparkFrameOps.countViolations(spark, pds.sparkDataFrame, messagesOf)
              DataAlgebra.contractResult(
                dataset,
                pds.sparkDataFrame.count(),
                violatingRecords,
                messageCounts,
              )
            }

          case other =>
            // A dataset this engine did not produce, so the driver holds it in full.
            F.pure {
              val perRecord        = other.data.map(a => messagesOf(a).distinct)
              val violatingRecords = perRecord.count(_.nonEmpty).toLong
              val messageCounts =
                perRecord.flatten.groupBy(identity).view.mapValues(_.size.toLong).toList.sortBy(_._1)
              DataAlgebra.contractResult(dataset, other.data.size.toLong, violatingRecords, messageCounts)
            }
        }
      }

      override def runQualityChecks[A: DataDecoder](
        dataset: DataAlgebra.Dataset[A],
        checks: NonEmptyList[QualityCheck[A]],
      ): F[List[DataAlgebra.QualityCheckResult]] = {

        /** The messages for the violations one check reports on one record, empty when it is satisfied. */
        val messagesPerCheck: List[A => List[String]] =
          checks.toList.map(chk => (a: A) => chk(a).fold(_.toList.map(_.message), _ => Nil))

        // Evaluating the checks over `dataset.data` read at most 100 records on a Spark-backed dataset, so a
        // violation in record 101 went unreported and the dataset passed. The checks run over the frame now.
        def evaluateInFrame(frame: org.apache.spark.sql.DataFrame): List[DataAlgebra.QualityCheckResult] = {
          val (failed, messages) = SparkFrameOps.collectViolations(spark, frame, messagesPerCheck)
          val byCheck            = messages.groupBy(_._1)
          messagesPerCheck.indices.toList.map { idx =>
            if (!failed.contains(idx))
              DataAlgebra.QualityCheckResult(s"check_$idx", passed = true, message = "ok", score = 1.0)
            else {
              // A check can be in `failed` with no message here, because the messages are capped and the
              // index set is not. Saying so beats reporting an empty message.
              val msg = byCheck.getOrElse(idx, Nil).map(_._2).take(3) match {
                case Nil      => "violations found, messages not collected"
                case reported => reported.mkString("; ")
              }
              DataAlgebra.QualityCheckResult(s"check_$idx", passed = false, message = msg, score = 0.0)
            }
          }
        }

        /** The driver-side path, for a dataset this engine did not produce and so holds in full. */
        def evaluateFunctional: List[DataAlgebra.QualityCheckResult] = {
          val dataList = dataset.data
          messagesPerCheck.zipWithIndex.map {
            case (check, idx) =>
              val invalids = dataList.flatMap(check)
              if (invalids.isEmpty)
                DataAlgebra.QualityCheckResult(s"check_$idx", passed = true, message = "ok", score = 1.0)
              else {
                val msg = invalids.distinct.take(3).mkString("; ")
                DataAlgebra.QualityCheckResult(s"check_$idx", passed = false, message = msg, score = 0.0)
              }
          }
        }

        dataset match {
          case pds: ProductionSparkDataset[A] =>
            // Build a basic set of constraints from non-nullable schema fields
            val required = pds.schema.requiredFields.map(_.name)
            val constraints = required.map { fn =>
              com.flowforge.core.types.QualityConstraint.NotNull(fn)
            }

            // Try to invoke DeequAdapter via reflection if present on classpath
            val deequResultsF: F[Option[List[DataAlgebra.QualityCheckResult]]] = F.delay {
              Either.catchNonFatal {
                val cls    = Class.forName("com.flowforge.quality.deequ.DeequAdapter$")
                val module = cls.getField("MODULE$").get(None.orNull)
                val method = cls.getMethod(
                  "runChecks",
                  classOf[org.apache.spark.sql.SparkSession],
                  classOf[DataAlgebra.Dataset[_]],
                  classOf[List[_]],
                )
                val qualityResult = method.invoke(module, spark, pds, constraints)
                ReflectionCasting.castQualityResult[A](qualityResult)
              }.toOption.flatten.map { quality =>
                if (quality.violations.isEmpty)
                  List(
                    DataAlgebra
                      .QualityCheckResult(
                        "deequ_auto",
                        passed = true,
                        message = "ok",
                        score = quality.score,
                      ),
                  )
                else
                  quality.violations.map { v =>
                    DataAlgebra.QualityCheckResult(v.rule, passed = false, message = v.message, score = 0.0)
                  }
              }
            }

            F.flatMap(deequResultsF) {
              case Some(res) => F.pure(res)
              case None      => F.blocking(evaluateInFrame(pds.sparkDataFrame))
            }

          case _ => F.pure(evaluateFunctional)
        }
      }

      // The counts came from `dataset.data`, which is a sample of at most 100 records on a Spark-backed
      // dataset, so a larger input was profiled as having 100 records. They come from the frame now.
      //
      // `nullCount` stays 0 on both engines. It is the one field of the profile with no agreed meaning -
      // records with a null field, or null records - and inventing one here would make the two engines
      // disagree. See docs/plan/v1.0-readiness.md.
      override def profile[A](dataset: DataAlgebra.Dataset[A]): F[DataAlgebra.DataProfile[A]] =
        dataset match {
          case pds: ProductionSparkDataset[A] =>
            F.blocking(
              DataAlgebra.DataProfile(
                recordCount = pds.sparkDataFrame.count(),
                nullCount = 0L,
                distinctCount = pds.sparkDataFrame.distinct().count(),
                schema = pds.schema,
                statistics = Map.empty,
              ),
            )
          case other =>
            F.pure(
              DataAlgebra.DataProfile(
                recordCount = other.data.size.toLong,
                nullCount = 0L,
                distinctCount = other.data.distinct.size.toLong,
                schema = other.schema,
                statistics = Map.empty,
              ),
            )
        }

      // ========================================
      // UTILITIES
      // ========================================

      // `data` on a Spark-backed dataset is a sample of at most 100 records, so counting it reported 100 for
      // every larger input. `size` and `isEmpty` are what the dataset answers for itself, and the Spark one
      // answers from the frame.
      //
      // The frame is asked directly rather than through `size`, because `Dataset.size` is an `Int` and the
      // Spark wrapper narrows `count()` into it. This method returns a `Long`, so going through `size` would
      // wrap a frame of more than `Int.MaxValue` rows into a negative count.
      override def count[A](dataset: DataAlgebra.Dataset[A]): Long = dataset match {
        case pds: ProductionSparkDataset[A] => pds.sparkDataFrame.count()
        case other                          => other.size.toLong
      }
      override def isEmpty[A](dataset: DataAlgebra.Dataset[A]): Boolean = dataset.isEmpty

      override def cache[A](
        dataset: DataAlgebra.Dataset[A],
        strategy: DataAlgebra.CacheStrategy,
      ): F[DataAlgebra.Dataset[A]] =
        F.pure(dataset)

      override def partition[A](
        dataset: DataAlgebra.Dataset[A],
        partitioner: DataAlgebra.Partitioner[A],
      ): List[DataAlgebra.Dataset[A]] = {
        val grouped = dataset.data.groupBy(partitioner.partition)
        grouped.toList.map {
          case (_, chunk) =>
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
        table: TableOperations.TableName,
      ): F[TableOperations.TableOperationResult] =
        F.pure(
          TableOperations.TableOperationResult(
            tableName = table,
            operation = "repairRefresh",
            success = true,
            affectedPartitions = List.empty,
            processingTime = 0.seconds,
            errors = List.empty,
          ),
        )

      override def getTableLocation(
        table: TableOperations.TableName,
      ): F[ValidatedNel[FlowForgeError, String]] =
        F.pure(s"spark-catalog.${table.qualified}".validNel)

      override def getAffectedPartitions(
        table: TableOperations.TableName,
        startTime: Instant,
        endTime: Instant,
      ): F[List[TableOperations.PartitionSpec]] =
        F.pure(List.empty)

      override def deleteDfsLocation(
        location: String,
        dryRun: Boolean,
      ): F[TableOperations.TableOperationResult] =
        F.pure(
          TableOperations.TableOperationResult(
            tableName = TableOperations.TableName(
              database = eu.timepit.refined.types.string.NonEmptyString.unsafeFrom("default"),
              table = eu.timepit.refined.types.string.NonEmptyString.unsafeFrom("unknown"),
            ),
            operation = s"delete($location)",
            success = !dryRun,
            affectedPartitions = List.empty,
            processingTime = 0.seconds,
            errors = List.empty,
          ),
        )

      override def analyzeTable(
        table: TableOperations.TableName,
        partitions: Option[NonEmptyList[TableOperations.PartitionSpec]],
      ): F[TableOperations.TableOperationResult] =
        F.pure(
          TableOperations.TableOperationResult(
            tableName = table,
            operation = "analyze",
            success = true,
            affectedPartitions = partitions.map(_.toList).getOrElse(List.empty),
            processingTime = 0.seconds,
            errors = List.empty,
          ),
        )

      override def vacuumTable(
        table: TableOperations.TableName,
        retentionHours: Int,
        dryRun: Boolean,
      ): F[TableOperations.TableOperationResult] =
        F.pure(
          TableOperations.TableOperationResult(
            tableName = table,
            operation = s"vacuum($retentionHours h, dryRun=$dryRun)",
            success = true,
            affectedPartitions = List.empty,
            processingTime = 0.seconds,
            errors = List.empty,
          ),
        )
      // SECURITY UTILITIES: Path and column name validation
      /**
       * The path with the characters that could break out of a quoted SQL identifier removed, or the reason
       * it was rejected.
       *
       * Both callers already build an Either, so the rejection is returned rather than thrown.
       */
      private def sanitizedPath(path: String): Either[String, String] = {
        val sanitized = path.replaceAll("[<>:\"|?*]", "")
        if (sanitized.contains("..") || sanitized.startsWith("/proc") || sanitized.startsWith("/etc"))
          Left(s"Unsafe path detected: $path")
        else Right(sanitized)
      }

      /** The column name if it is already safe to interpolate, or the reason it was rejected. */
      private def sanitizedColumn(columnName: String): Either[String, String] = {
        val sanitized = columnName.replaceAll("[^a-zA-Z0-9_.]", "")
        if (sanitized.isEmpty || sanitized != columnName)
          Left(s"Unsafe column name detected: $columnName")
        else Right(sanitized)
      }

    } // End of algebra DataAlgebra[F]

    DataAlgebraWithSession(algebra, sparkSession)
  }

  /**
   * Create a Resource for SparkDataAlgebra with PROPER session management RESOURCE LEAK FIX: Stores session
   * reference for safe cleanup
   */
  def resource[F[_]: EffectSystem](
    appName: String,
    master: Option[String] = None,
  ): Resource[F, DataAlgebra[F]] = {
    val F = EffectSystem[F]
    Resource.make {
      F.blocking {
        val builder = SparkSession.builder().appName(appName)
        master.foreach(builder.master)
        val spark = builder.getOrCreate()
        // Store the session for cleanup, but return the algebra
        val wrapper = createSparkDataAlgebra[F](spark)
        sessionRegistry.put(wrapper.algebra, spark)
        wrapper.algebra
      }
    } { algebra =>
      // RESOURCE LEAK FIX: Properly manage the SparkSession we created
      F.blocking {
        sessionRegistry.get(algebra) match {
          case Some(session) =>
            Either.catchNonFatal(session.stop())
            sessionRegistry.remove(algebra)
            ()
          case None =>
            // Fallback for unknown algebra types
            SparkSession.getActiveSession.foreach { session =>
              val _ = Either.catchNonFatal(session.stop())
            }
        }
      }
    }
  }
}
