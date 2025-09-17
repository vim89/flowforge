package com.flowforge.app

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.apache.spark.sql.SparkSession
import com.flowforge.core.types._
import com.flowforge.core.algebra.DataAlgebra
import com.flowforge.engines.spark.ProductionSparkDataset
import com.flowforge.quality.deequ.DeequAdapter

class DataQualitySpec extends AnyFunSuite with Matchers {
  test("DQ constraints pass/fail appropriately on small DF") {
    val spark = SparkSession
      .builder()
      .master("local[2]")
      .appName("template-dq-test")
      .config("spark.ui.enabled", "false")
      .getOrCreate()
    try {
      import spark.implicits._
      val good = Seq((1L, "a@b.com", 20), (2L, "b@c.com", 30)).toDF("id", "email", "age")
      val bad  = Seq((null.asInstanceOf[java.lang.Long], "bad", -1)).toDF("id", "email", "age")

      val schema = DataSchema(fields = Nil, version = RefinedTypes.SchemaVersion.unsafeFrom(1), metadata = Map())
      def meta(df: org.apache.spark.sql.DataFrame) = DataAlgebra.DatasetMetadata(
        recordCount = df.count(),
        schema = schema,
        partitions = df.rdd.getNumPartitions,
        createdAt = java.time.Instant.now(),
        source = None,
      )
      val dsGood = ProductionSparkDataset[Map[String, Any]](Nil, good, schema, meta(good))
      val dsBad  = ProductionSparkDataset[Map[String, Any]](Nil, bad, schema, meta(bad))

      val dq = List[
        QualityConstraint
      ](
        QualityConstraint.NotNull(RefinedTypes.FieldName.unsafeFrom("id")),
        QualityConstraint.Unique(RefinedTypes.FieldName.unsafeFrom("id")),
        QualityConstraint.NullRateBelow(RefinedTypes.FieldName.unsafeFrom("email"), 0.0),
        QualityConstraint.Min(RefinedTypes.FieldName.unsafeFrom("age"), 0.0),
        QualityConstraint.Max(RefinedTypes.FieldName.unsafeFrom("age"), 200.0),
        QualityConstraint.Pattern(RefinedTypes.FieldName.unsafeFrom("email"), "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"),
      )
      val ok  = DeequAdapter.runChecks(spark, dsGood, dq)
      val badR = DeequAdapter.runChecks(spark, dsBad, dq)
      ok.passed shouldBe true
      badR.passed shouldBe false
      badR.violations.nonEmpty shouldBe true
    } finally spark.stop()
  }
}
