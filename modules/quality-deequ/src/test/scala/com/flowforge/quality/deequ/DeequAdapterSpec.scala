// scalafix:off DisableSyntax.var DisableSyntax.throw DisableSyntax.null DisableSyntax.noUnsafeRunSync
package com.flowforge.quality.deequ

import com.flowforge.core.algebra.DataAlgebra
import com.flowforge.core.types._
import com.flowforge.engines.spark.ProductionSparkDataset
import org.apache.spark.sql.SparkSession
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

case class Txn(id: String, amount: Double)

class DeequAdapterSpec extends AnyFunSuite with Matchers {
  test("not_null and unique constraints pass/fail appropriately") {
    val spark = SparkSession
      .builder()
      .master("local[2]")
      .appName("ff-deequ-test")
      .config("spark.ui.enabled", "false")
      .config("spark.ui.showConsoleProgress", "false")
      .config("spark.sql.shuffle.partitions", "1")
      .config("spark.driver.bindAddress", "127.0.0.1")
      .config("spark.driver.host", "127.0.0.1")
      .getOrCreate()
    try {
      import spark.implicits._
      val good = Seq(Txn("a", 10.0), Txn("b", 20.0)).toDF()
      val bad  = Seq(Txn(Option.empty[String].orNull, 5.0), Txn("a", 7.0)).toDF()

      val schemaEmpty = DataSchema(
        fields = Nil,
        version = RefinedTypes.SchemaVersion.unsafeFrom(1),
        metadata = Map.empty,
      )
      val metaGood = DataAlgebra.DatasetMetadata(
        recordCount = good.count(),
        schema = schemaEmpty,
        partitions = good.rdd.getNumPartitions,
        createdAt = java.time.Instant.now(),
        source = None,
      )
      val metaBad = DataAlgebra.DatasetMetadata(
        recordCount = bad.count(),
        schema = schemaEmpty,
        partitions = bad.rdd.getNumPartitions,
        createdAt = java.time.Instant.now(),
        source = None,
      )
      val goodDs = ProductionSparkDataset[Map[String, Any]](Nil, good, schemaEmpty, metaGood)
      val badDs  = ProductionSparkDataset[Map[String, Any]](Nil, bad, schemaEmpty, metaBad)

      val notNull =
        com.flowforge.core.types.QualityConstraint.NotNull(RefinedTypes.FieldName.unsafeFrom("id"))
      val unique = com.flowforge.core.types.QualityConstraint.Unique(RefinedTypes.FieldName.unsafeFrom("id"))
      val pattern =
        com.flowforge.core.types.QualityConstraint
          .Pattern(RefinedTypes.FieldName.unsafeFrom("id"), "^[a-z]+$")
      val range =
        com.flowforge.core.types.QualityConstraint
          .Range(RefinedTypes.FieldName.unsafeFrom("amount"), Some(0.0), Some(1000.0))
      val compliance =
        com.flowforge.core.types.QualityConstraint.Compliance("positive_amount", "amount > 0")
      val checks = List(notNull, unique, pattern, range, compliance)

      val resGood = DeequAdapter.runChecks(spark, goodDs, checks)
      resGood.passed shouldBe true
      resGood.violations shouldBe empty

      val resBad = DeequAdapter.runChecks(spark, badDs, checks)
      resBad.passed shouldBe false
      resBad.violations.nonEmpty shouldBe true
    } finally spark.stop()
  }
}
