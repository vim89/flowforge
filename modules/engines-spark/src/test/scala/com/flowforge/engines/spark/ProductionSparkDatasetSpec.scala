package com.flowforge.engines.spark

import com.flowforge.core.algebra.DataAlgebra
import com.flowforge.core.types.{ DataSchema, DataType }
import org.apache.spark.sql.{ AnalysisException, DataFrame, SparkSession }
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.time.Instant

/**
 * Covers `asSparkDataset`, which used to throw `UnsupportedOperationException` on every call.
 *
 * The case class lives in the companion because Spark derives a product encoder from its type, which needs a
 * type it can name from outside the test instance.
 */
object ProductionSparkDatasetSpec {
  final case class User(id: Long, name: String)
}

class ProductionSparkDatasetSpec extends AnyFunSuite with Matchers {

  import ProductionSparkDatasetSpec.User

  private val schema =
    DataSchema.builder.addField("id", DataType.Long).addField("name", DataType.String).build

  /**
   * Wrap a frame directly rather than through `fromDataFrame`, which would pull a `DataDecoder` and its JSON
   * sampling into a test about the typed view.
   */
  private def wrap(df: DataFrame): ProductionSparkDataset[User] =
    ProductionSparkDataset[User](
      sampleData = Nil,
      sparkDataFrame = df,
      schema = schema,
      metadata = DataAlgebra.DatasetMetadata(
        recordCount = df.count(),
        schema = schema,
        partitions = df.rdd.getNumPartitions,
        createdAt = Instant.now(),
      ),
    )

  private def withSpark[B](f: SparkSession => B): B = {
    val spark = SparkSession.builder().appName("ff-production-dataset-spec").master("local[2]").getOrCreate()
    try f(spark)
    finally spark.stop()
  }

  test("a frame carrying the element's columns becomes a typed Dataset of that element") {
    withSpark { spark =>
      import spark.implicits._
      val rows = List(User(1L, "Alice"), User(2L, "Bob"))
      val ds   = wrap(rows.toDF()).asSparkDataset

      ds.collect().toList shouldBe rows
    }
  }

  test("a frame missing one of the element's columns fails at the conversion") {
    withSpark { spark =>
      import spark.implicits._
      val idsOnly = List(1L, 2L).toDF("id")

      // Schema-on-read: the mismatch is reported where the binding happens, so the caller does not get a
      // Dataset that fails later in whichever operation first reads `name`.
      val thrown = intercept[AnalysisException](wrap(idsOnly).asSparkDataset)
      thrown.getMessage should include("name")
    }
  }
}
