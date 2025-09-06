package com.flowforge.engines.spark

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.flowforge.core.algebra.{ CDCOperations, DataAlgebra, EffectSystem }
import com.flowforge.core.impl.SimpleDataset
import com.flowforge.core.types._
import org.apache.spark.sql.SparkSession
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.nio.file.Files

class DeltaMergeITSpec extends AnyFunSuite with Matchers {

  // Guard: run only with -DwithSparkIT=true or env WITH_SPARK_IT=true
  private def shouldRun: Boolean =
    sys.props.get("withSparkIT").contains("true") || sys.env.get("WITH_SPARK_IT").contains("true")

  implicit val F: EffectSystem[IO] = com.flowforge.core.instances.EffectInstances.catsEffectSystemInstance
  implicit val dcAny: com.flowforge.core.algebra.DataContract[Map[String, Any]] =
    com.flowforge.core.algebra.DataContract.empty[Map[String, Any]]

  test("Delta MERGE basic upsert (guarded)") {
    assume(shouldRun, "Skipping Spark IT: set -DwithSparkIT=true or WITH_SPARK_IT=true")
    // Additional guard: Spark 3.5 is not compatible with very new JDKs (e.g., 24+).
    val jv = sys.props.getOrElse("java.version", "unknown")
    def major(v: String): Int =
      try {
        val s = if (v.startsWith("1.")) v.drop(2) else v
        s.takeWhile(_.isDigit).toIntOption.getOrElse(0)
      } catch { case _: Throwable => 0 }
    assume(major(jv) > 0 && major(jv) <= 21, s"Skipping Spark IT due to unsupported JDK version: $jv")

    val spark = SparkSession.builder().appName("ff-it").master("local[*]").getOrCreate()
    try {
      import spark.implicits._
      val tmpDir = Files.createTempDirectory("ff-it").toFile
      val src    = new java.io.File(tmpDir, "src-delta").getAbsolutePath
      val tgt    = new java.io.File(tmpDir, "tgt-delta").getAbsolutePath

      Seq((1, "a"), (2, "b")).toDF("id", "v").write.format("delta").mode("overwrite").save(src)
      Seq((1, "a0"), (3, "c")).toDF("id", "v").write.format("delta").mode("overwrite").save(tgt)

      val schema = DataSchema.builder
        .addField("id", DataType.Integer, required = true)
        .addField("v", DataType.String, required = true)
        .build

      val srcDs: DataAlgebra.Dataset[Map[String, Any]] = SimpleDataset(
        data = Nil,
        schema = schema,
        metadata = DataAlgebra.DatasetMetadata(
          0L,
          schema,
          1,
          java.time.Instant.now(),
          Some(LocalDataSource(src, DataFormat.Delta)),
        ),
      )

      val tgtDs: DataAlgebra.Dataset[Map[String, Any]] = SimpleDataset(
        data = Nil,
        schema = schema,
        metadata = DataAlgebra.DatasetMetadata(
          0L,
          schema,
          1,
          java.time.Instant.now(),
          Some(LocalDataSource(tgt, DataFormat.Delta)),
        ),
      )

      val algebra: DataAlgebra[IO] = SparkDataAlgebra.createSparkDataAlgebra[IO](spark).algebra
      val cfg = CDCOperations.CDCConfig(
        keyColumns = cats.data.NonEmptyList.one(RefinedTypes.FieldName.unsafeFrom("id")),
      )
      val res = algebra.performDelta(srcDs, tgtDs, cfg).unsafeRunSync()
      res.success shouldBe true
      res.updated shouldBe 1L
      res.inserted shouldBe 1L
      res.deleted shouldBe 1L
    } finally
      spark.stop()
  }
}
