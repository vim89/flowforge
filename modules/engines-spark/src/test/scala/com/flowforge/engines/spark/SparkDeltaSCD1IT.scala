package com.flowforge.engines.spark

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.flowforge.core.algebra.{ CDCOperations, DataAlgebra, DataContract, EffectSystem }
import com.flowforge.core.impl.SimpleDataset
import com.flowforge.core.instances.EffectInstances
import com.flowforge.core.types._
import org.scalatest.funsuite.AnyFunSuite

import java.nio.file.Files
import java.time.Instant

final case class S1(id: Long, v: String)

class SparkDeltaSCD1IT extends AnyFunSuite {
  private def enabled: Boolean = sys.props.get("withSparkIT").contains("true")

  test("SCD1 upsert using Delta MERGE (opt-in)") {
    if (!enabled) cancel("set -DwithSparkIT=true to enable")
    import org.apache.spark.sql.SparkSession
    val srcDir = Files.createTempDirectory("ff-scd1-src").toFile.getAbsolutePath
    val tgtDir = Files.createTempDirectory("ff-scd1-tgt").toFile.getAbsolutePath
    val spark  = SparkSession.builder().appName("ff-scd1").master("local[2]")
      .config("spark.sql.extensions","io.delta.sql.DeltaSparkSessionExtension")
      .config("spark.sql.catalog.spark_catalog","org.apache.spark.sql.delta.catalog.DeltaCatalog").getOrCreate()
    val df1 = spark.range(1, 3)
      .withColumnRenamed("id", "id")
      .withColumn("v", org.apache.spark.sql.functions.expr("CASE id WHEN 1 THEN 'a' ELSE 'b' END"))
      .selectExpr("CAST(id AS BIGINT) AS id", "v")
    df1.write.format("delta").mode("overwrite").save(srcDir)
    spark.emptyDataFrame.write.format("delta").mode("overwrite").save(tgtDir)

    implicit val F: EffectSystem[IO] = EffectInstances.catsEffectSystemInstance
    val alg: DataAlgebra[IO]         = SparkDataAlgebra.createSparkDataAlgebra[IO](spark).algebra
    implicit val DC: DataContract[S1] = DataContract.empty[S1]
    val schema = DataSchema(Nil, RefinedTypes.SchemaVersion.unsafeFrom(1), Map.empty, Instant.now())
    def meta(path:String)= DataAlgebra.DatasetMetadata(0,schema,1,Instant.now(),Some(LocalDataSource(path, DataFormat.Delta)))
    val dsSrc = SimpleDataset(List(S1(1,"a"),S1(2,"b")), schema, meta(srcDir))
    val dsTgt = SimpleDataset(List.empty[S1], schema, meta(tgtDir))
    val cfg   = CDCOperations.CDCConfig(keyColumns = cats.data.NonEmptyList.one(RefinedTypes.FieldName.unsafeFrom("id")))

    val first = alg.performDelta(dsSrc, dsTgt, cfg).unsafeRunSync()
    assert(first.inserted == 2)
    val count1 = spark.read.format("delta").load(tgtDir).count()
    assert(count1 == 2)

    // Update one record -> upsert replaces value
    val df2 = spark.range(1, 4).filter("id <> 2")
      .withColumn("v", org.apache.spark.sql.functions.expr("CASE id WHEN 1 THEN 'z' ELSE 'c' END"))
      .selectExpr("CAST(id AS BIGINT) AS id", "v")
    df2.write.format("delta").mode("overwrite").save(srcDir)
    val second = alg.performDelta(dsSrc, dsTgt, cfg).unsafeRunSync()
    assert(second.updated >= 1)
    val df = spark.read.format("delta").load(tgtDir)
    assert(df.filter("id = 1 and v = 'z'").count() == 1)
    spark.stop()
  }
}
