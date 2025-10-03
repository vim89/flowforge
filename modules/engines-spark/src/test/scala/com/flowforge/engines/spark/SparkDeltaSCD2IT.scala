// scalafix:off DisableSyntax.var DisableSyntax.throw DisableSyntax.null DisableSyntax.noUnsafeRunSync
package com.flowforge.engines.spark

import cats.data.NonEmptyList
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.flowforge.core.algebra.{ CDCOperations, DataAlgebra, DataContract, EffectSystem }
import com.flowforge.core.impl.SimpleDataset
import com.flowforge.core.instances.EffectInstances
import com.flowforge.core.types.RefinedTypes.FieldName
import com.flowforge.core.types._
import org.scalatest.funsuite.AnyFunSuite

import java.nio.file.Files
import java.time.Instant

final case class S2(
  id: Long,
  v: String,
  ts: Long)

class SparkDeltaSCD2IT extends AnyFunSuite {
  private def enabled: Boolean = sys.props.get("withSparkIT").contains("true")

  test("SCD2 merge with effective_from/effective_to/is_current (opt-in)") {
    if (!enabled) cancel("set -DwithSparkIT=true to enable")
    import org.apache.spark.sql.SparkSession
    val srcDir = Files.createTempDirectory("ff-scd2-src").toFile.getAbsolutePath
    val tgtDir = Files.createTempDirectory("ff-scd2-tgt").toFile.getAbsolutePath
    val spark = SparkSession
      .builder().appName("ff-scd2").master("local[2]")
      .config("spark.sql.extensions", "io.delta.sql.DeltaSparkSessionExtension")
      .config(
        "spark.sql.catalog.spark_catalog",
        "org.apache.spark.sql.delta.catalog.DeltaCatalog",
      ).getOrCreate()
    val t1 = System.currentTimeMillis()
    val df1 = spark
      .range(1, 2)
      .withColumn("v", org.apache.spark.sql.functions.lit("a"))
      .withColumn("ts", org.apache.spark.sql.functions.lit(t1))
      .selectExpr("CAST(id AS BIGINT) AS id", "v", "CAST(ts AS BIGINT) AS ts")
    df1.write.format("delta").mode("overwrite").save(srcDir)
    spark.emptyDataFrame.write.format("delta").mode("overwrite").save(tgtDir)

    implicit val F: EffectSystem[IO]  = EffectInstances.catsEffectSystemInstance
    val alg: DataAlgebra[IO]          = SparkDataAlgebra.createSparkDataAlgebra[IO](spark).algebra
    implicit val DC: DataContract[S2] = DataContract.empty[S2]
    val schema = DataSchema(Nil, RefinedTypes.SchemaVersion.unsafeFrom(1), Map.empty, Instant.now())
    def meta(path: String) =
      DataAlgebra.DatasetMetadata(0, schema, 1, Instant.now(), Some(LocalDataSource(path, DataFormat.Delta)))
    val dsSrc = SimpleDataset(List(S2(1, "a", t1)), schema, meta(srcDir))
    val dsTgt = SimpleDataset(List.empty[S2], schema, meta(tgtDir))
    val cfg = CDCOperations.CDCConfig(
      keyColumns = NonEmptyList.one(FieldName.unsafeFrom("id")),
      timestampColumn = Some(FieldName.unsafeFrom("ts")),
      scd2 = Some(
        CDCOperations.SCD2Columns(
          effectiveFrom = FieldName.unsafeFrom("effective_from"),
          effectiveTo = FieldName.unsafeFrom("effective_to"),
          isCurrent = FieldName.unsafeFrom("is_current"),
        ),
      ),
    )

    val first = alg.performDelta(dsSrc, dsTgt, cfg).unsafeRunSync()
    assert(first.inserted >= 1)
    val tgt1 = spark.read.format("delta").load(tgtDir)
    assert(tgt1.filter("is_current = true").count() == 1)

    val t2 = t1 + 1000
    val df2 = spark
      .range(1, 2)
      .withColumn("v", org.apache.spark.sql.functions.lit("b"))
      .withColumn("ts", org.apache.spark.sql.functions.lit(t2))
      .selectExpr("CAST(id AS BIGINT) AS id", "v", "CAST(ts AS BIGINT) AS ts")
    df2.write.format("delta").mode("overwrite").save(srcDir)
    val second = alg.performDelta(dsSrc, dsTgt, cfg).unsafeRunSync()
    assert(second.updated >= 1 || second.inserted >= 1)
    val tgt2 = spark.read.format("delta").load(tgtDir)
    assert(tgt2.filter("is_current = true").count() == 1)
    assert(tgt2.filter("is_current = false").count() >= 1)
    spark.stop()
  }

  test("SCD2 with composite keys and partitioning (opt-in)") {
    if (!enabled) cancel("set -DwithSparkIT=true to enable")
    import org.apache.spark.sql.SparkSession
    val srcDir = java.nio.file.Files.createTempDirectory("ff-scd2c-src").toFile.getAbsolutePath
    val tgtDir = java.nio.file.Files.createTempDirectory("ff-scd2c-tgt").toFile.getAbsolutePath

    val spark = SparkSession
      .builder().appName("ff-scd2-composite").master("local[2]")
      .config("spark.sql.extensions", "io.delta.sql.DeltaSparkSessionExtension")
      .config(
        "spark.sql.catalog.spark_catalog",
        "org.apache.spark.sql.delta.catalog.DeltaCatalog",
      ).getOrCreate()

    val t1 = System.currentTimeMillis()
    val df1 = spark
      .range(1, 2)
      .withColumn("cat", org.apache.spark.sql.functions.lit("A"))
      .withColumn("v", org.apache.spark.sql.functions.lit("a"))
      .withColumn("ts", org.apache.spark.sql.functions.lit(t1))
      .selectExpr("CAST(id AS BIGINT) AS id", "cat", "v", "CAST(ts AS BIGINT) AS ts")
    df1.write.format("delta").mode("overwrite").save(srcDir)
    spark.emptyDataFrame.write.format("delta").mode("overwrite").save(tgtDir)

    implicit val F: EffectSystem[IO]  = EffectInstances.catsEffectSystemInstance
    val alg: DataAlgebra[IO]          = SparkDataAlgebra.createSparkDataAlgebra[IO](spark).algebra
    implicit val DC: DataContract[S2] = DataContract.empty[S2]
    val schema = DataSchema(Nil, RefinedTypes.SchemaVersion.unsafeFrom(1), Map.empty, Instant.now())
    def meta(path: String) =
      DataAlgebra.DatasetMetadata(0, schema, 1, Instant.now(), Some(LocalDataSource(path, DataFormat.Delta)))
    val dsSrc = SimpleDataset(List(S2(1L, "a", t1)), schema, meta(srcDir))
    val dsTgt = SimpleDataset(List.empty[S2], schema, meta(tgtDir))

    val cfg = CDCOperations.CDCConfig(
      keyColumns = NonEmptyList.of(FieldName.unsafeFrom("id"), FieldName.unsafeFrom("cat")),
      timestampColumn = Some(FieldName.unsafeFrom("ts")),
      scd2 = Some(
        CDCOperations.SCD2Columns(
          effectiveFrom = FieldName.unsafeFrom("effective_from"),
          effectiveTo = FieldName.unsafeFrom("effective_to"),
          isCurrent = FieldName.unsafeFrom("is_current"),
        ),
      ),
      partition =
        Some(CDCOperations.PartitionStrategy(List(FieldName.unsafeFrom("id"), FieldName.unsafeFrom("cat")))),
    )

    alg.performDelta(dsSrc, dsTgt, cfg).unsafeRunSync()
    val tgt1 = spark.read.format("delta").load(tgtDir)
    assert(tgt1.filter("is_current = true").count() == 1)

    val t2 = t1 + 1000L
    val df2 = spark
      .range(1, 2)
      .withColumn("cat", org.apache.spark.sql.functions.lit("A"))
      .withColumn("v", org.apache.spark.sql.functions.lit("b"))
      .withColumn("ts", org.apache.spark.sql.functions.lit(t2))
      .selectExpr("CAST(id AS BIGINT) AS id", "cat", "v", "CAST(ts AS BIGINT) AS ts")
    df2.write.format("delta").mode("overwrite").save(srcDir)
    alg.performDelta(dsSrc, dsTgt, cfg).unsafeRunSync()

    val tgt2 = spark.read.format("delta").load(tgtDir)
    assert(tgt2.filter("is_current = true").count() == 1)
    assert(tgt2.filter("is_current = false").count() >= 1)
    val partCols =
      spark.sql(s"DESCRIBE DETAIL delta.`$tgtDir`").selectExpr("partitionColumns").head().getSeq[String](0)
    assert(partCols.toSet == Set("id", "cat"))
    spark.stop()
  }
}
