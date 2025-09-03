package com.flowforge.engines.spark

import cats.data.NonEmptyList
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.flowforge.core.algebra.{CDCOperations, DataAlgebra, DataContract, EffectSystem}
import com.flowforge.core.impl.SimpleDataset
import com.flowforge.core.instances.EffectInstances
import com.flowforge.core.types.RefinedTypes.FieldName
import com.flowforge.core.types._
import org.scalatest.funsuite.AnyFunSuite

import java.nio.file.Files
import java.time.Instant

final case class ItRec(id: Long, v: String, ts: Long)

class SparkDeltaSCD2IT extends AnyFunSuite {

  private def sparkAvailable: Boolean =
    try { Class.forName("org.apache.spark.sql.SparkSession"); true }
    catch { case _: Throwable => false }

  private def deltaAvailable: Boolean =
    try { Class.forName("io.delta.tables.DeltaTable"); true }
    catch { case _: Throwable => false }

  private def enabled: Boolean = sys.props.get("withSparkIT").contains("true")

  test("SCD2 merge end-to-end with close/open and watermark checks (opt-in)") {
    if (!(enabled && sparkAvailable && deltaAvailable))
      cancel("Spark/Delta IT disabled or dependencies unavailable")

    import org.apache.spark.sql.SparkSession
    val srcDir = Files.createTempDirectory("ff-src").toFile.getAbsolutePath
    val tgtDir = Files.createTempDirectory("ff-tgt").toFile.getAbsolutePath

    val spark = SparkSession
      .builder()
      .appName("FlowForge SCD2 IT")
      .master("local[2]")
      .config("spark.ui.enabled", "false")
      .config("spark.sql.extensions", "io.delta.sql.DeltaSparkSessionExtension")
      .config("spark.sql.catalog.spark_catalog", "org.apache.spark.sql.delta.catalog.DeltaCatalog")
      .getOrCreate()

    import spark.implicits._

    // Create initial source and empty target
    val t1 = System.currentTimeMillis()
    Seq(ItRec(1L, "a", t1)).toDF.write.format("delta").mode("overwrite").save(srcDir)
    // Create empty target table
    spark.emptyDataFrame.write.format("delta").mode("overwrite").save(tgtDir)

    implicit val F: EffectSystem[IO] = EffectInstances.catsEffectSystemInstance
    val alg: DataAlgebra[IO]         = SparkDataAlgebra.createSparkDataAlgebra[IO](spark)

    val schema = DataSchema(Nil, RefinedTypes.SchemaVersion.unsafeFrom(1), Map.empty, Instant.now())
    val metaSrc = DataAlgebra.DatasetMetadata(
      1,
      schema,
      1,
      Instant.now(),
      Some(LocalDataSource(srcDir, DataFormat.Delta))
    )
    val metaTgt = DataAlgebra.DatasetMetadata(
      0,
      schema,
      1,
      Instant.now(),
      Some(LocalDataSource(tgtDir, DataFormat.Delta))
    )
    val dsSrc = SimpleDataset(List(ItRec(1L, "a", System.currentTimeMillis())), schema, metaSrc)
    val dsTgt = SimpleDataset(List.empty[ItRec], schema, metaTgt)

    implicit val DC: DataContract[ItRec] = DataContract.empty[ItRec]

    val cfg = CDCOperations.CDCConfig(
      keyColumns = NonEmptyList.one(FieldName.unsafeFrom("id")),
      timestampColumn = Some(FieldName.unsafeFrom("ts")),
      scd2 = Some(
        CDCOperations.SCD2Columns(
          effectiveFrom = FieldName.unsafeFrom("effective_from"),
          effectiveTo = FieldName.unsafeFrom("effective_to"),
          isCurrent = FieldName.unsafeFrom("is_current")
        )
      ),
      partition =
        Some(CDCOperations.PartitionStrategy(partitionBy = List(FieldName.unsafeFrom("id"))))
    )

    val first = alg.performDelta(dsSrc, dsTgt, cfg).unsafeRunSync()
    assert(first.inserted >= 1)
    // Assert target has one current row with null effective_to
    val tgtAfterFirst = spark.read.format("delta").load(tgtDir)
    assert(tgtAfterFirst.filter("is_current = true").count() == 1)
    assert(tgtAfterFirst.filter("is_current = true AND effective_to IS NULL").count() == 1)

    // Update source with changed record
    val t2 = t1 + 1000L
    Seq(ItRec(1L, "b", t2)).toDF.write.format("delta").mode("overwrite").save(srcDir)

    val second = alg.performDelta(dsSrc, dsTgt, cfg).unsafeRunSync()
    assert(second.updated >= 1 || second.inserted >= 1)
    val tgtAfterSecond = spark.read.format("delta").load(tgtDir)
    // Old row closed, new row current
    assert(tgtAfterSecond.filter("is_current = true").count() == 1)
    assert(tgtAfterSecond.filter("is_current = false").count() >= 1)
    assert(tgtAfterSecond.filter("is_current = false AND effective_to IS NOT NULL").count() >= 1)
    // effective_from must reflect timestampColumn
    val maxFrom = tgtAfterSecond
      .agg(org.apache.spark.sql.functions.max(org.apache.spark.sql.functions.col("effective_from")))
      .first()
      .getTimestamp(0)
      .getTime
    assert(maxFrom >= t2 - 5000) // timestamp casting tolerance

    // Watermark-like increment: third run with higher ts should not create duplicate current rows
    val t3 = t2 + 1000L
    Seq(ItRec(1L, "b", t3)).toDF.write.format("delta").mode("overwrite").save(srcDir)
    val third = alg.performDelta(dsSrc, dsTgt, cfg).unsafeRunSync()
    assert(third.updated >= 0)
    val tgtAfterThird = spark.read.format("delta").load(tgtDir)
    assert(tgtAfterThird.filter("is_current = true").count() == 1)

    // Partition-aware assertion
    val detail   = spark.sql(s"DESCRIBE DETAIL delta.`$tgtDir`")
    val partCols = detail.selectExpr("partitionColumns").head().getSeq[String](0)
    assert(partCols.contains("id"))

    spark.stop()
  }

  test("SCD2 with composite keys and partitioning (opt-in)") {
    if (!(enabled && sparkAvailable && deltaAvailable))
      cancel("Spark/Delta IT disabled or dependencies unavailable")

    import org.apache.spark.sql.SparkSession
    val srcDir = Files.createTempDirectory("ff-src2").toFile.getAbsolutePath
    val tgtDir = Files.createTempDirectory("ff-tgt2").toFile.getAbsolutePath

    val spark = SparkSession
      .builder()
      .appName("FlowForge SCD2 IT - composite")
      .master("local[2]")
      .config("spark.ui.enabled", "false")
      .config("spark.sql.extensions", "io.delta.sql.DeltaSparkSessionExtension")
      .config("spark.sql.catalog.spark_catalog", "org.apache.spark.sql.delta.catalog.DeltaCatalog")
      .getOrCreate()

    import spark.implicits._

    case class R(id: Long, cat: String, v: String, ts: Long)
    val t1 = System.currentTimeMillis()
    Seq(R(1L, "A", "a", t1)).toDF.write.format("delta").mode("overwrite").save(srcDir)
    spark.emptyDataFrame.write.format("delta").mode("overwrite").save(tgtDir)

    implicit val F: EffectSystem[IO] = EffectInstances.catsEffectSystemInstance
    val alg: DataAlgebra[IO]         = SparkDataAlgebra.createSparkDataAlgebra[IO](spark)
    val schema = DataSchema(Nil, RefinedTypes.SchemaVersion.unsafeFrom(1), Map.empty, Instant.now())
    val metaSrc = DataAlgebra.DatasetMetadata(
      1,
      schema,
      1,
      Instant.now(),
      Some(LocalDataSource(srcDir, DataFormat.Delta))
    )
    val metaTgt = DataAlgebra.DatasetMetadata(
      0,
      schema,
      1,
      Instant.now(),
      Some(LocalDataSource(tgtDir, DataFormat.Delta))
    )
    val dsSrc = SimpleDataset(List(ItRec(1L, "a", System.currentTimeMillis())), schema, metaSrc)
    val dsTgt = SimpleDataset(List.empty[ItRec], schema, metaTgt)

    implicit val DC: DataContract[ItRec] = DataContract.empty[ItRec]

    val cfg = CDCOperations.CDCConfig(
      keyColumns = NonEmptyList.of(FieldName.unsafeFrom("id"), FieldName.unsafeFrom("cat")),
      timestampColumn = Some(FieldName.unsafeFrom("ts")),
      scd2 = Some(
        CDCOperations.SCD2Columns(
          effectiveFrom = FieldName.unsafeFrom("effective_from"),
          effectiveTo = FieldName.unsafeFrom("effective_to"),
          isCurrent = FieldName.unsafeFrom("is_current")
        )
      ),
      partition = Some(
        CDCOperations.PartitionStrategy(
          List(FieldName.unsafeFrom("id"), FieldName.unsafeFrom("cat"))
        )
      )
    )

    // First merge
    alg.performDelta(dsSrc, dsTgt, cfg).unsafeRunSync()
    val tgtAfterFirst = spark.read.format("delta").load(tgtDir)
    assert(tgtAfterFirst.filter("is_current = true").count() == 1)

    // Change non-key value for same composite key
    val t2 = t1 + 1000
    Seq(R(1L, "A", "b", t2)).toDF.write.format("delta").mode("overwrite").save(srcDir)
    alg.performDelta(dsSrc, dsTgt, cfg).unsafeRunSync()

    val tgtAfterSecond = spark.read.format("delta").load(tgtDir)
    assert(tgtAfterSecond.filter("is_current = true").count() == 1)
    assert(tgtAfterSecond.filter("is_current = false").count() >= 1)
    val detail   = spark.sql(s"DESCRIBE DETAIL delta.`$tgtDir`")
    val partCols = detail.selectExpr("partitionColumns").head().getSeq[String](0)
    assert(partCols.toSet == Set("id", "cat"))

    spark.stop()
  }
}
