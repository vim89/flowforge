package com.flowforge.examples.spark

import cats.effect.testing.scalatest.AsyncIOSpec
import cats.effect.{ IO, Resource }
import com.flowforge.examples.spark.UsersPipeline.EnrichedUser
import org.apache.spark.sql.{ DataFrame, SparkSession }
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers

import java.nio.file.Files
import scala.util.Try

// Moved from examples-spark: guarded Spark/Delta IT, opt-in via -DwithSparkIT=true or WITH_SPARK_IT=true
class DeltaConstraintsIT extends AsyncFlatSpec with AsyncIOSpec with Matchers {

  private val shouldRun: Boolean =
    sys.props.get("withSparkIT").contains("true") || sys.env.get("WITH_SPARK_IT").contains("true")

  private def createSparkResource(): Resource[IO, SparkSession] =
    Resource.make(
      IO.delay {
        SparkSession
          .builder()
          .appName("FlowForge-Delta-Constraints-IT")
          .master("local[2]")
          .config("spark.sql.extensions", "io.delta.sql.DeltaSparkSessionExtension")
          .config("spark.sql.catalog.spark_catalog", "org.apache.spark.sql.delta.catalog.DeltaCatalog")
          .config("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
          .config("spark.sql.adaptive.enabled", "true")
          .config("spark.sql.shuffle.partitions", "2")
          .getOrCreate()
      },
    )(spark => IO.delay(spark.stop()))

  "Delta table with NOT NULL constraints" should "enforce schema validation" in
    createSparkResource().use { spark =>
      if (!shouldRun)
        IO { org.scalatest.Assertions.assume(shouldRun, "Skipping Spark IT: set -DwithSparkIT=true or WITH_SPARK_IT=true"); succeed }
      else (
      for {
        tablePath    <- IO.delay(Files.createTempDirectory("delta-not-null-test").toString)
        _            <- createUsersTableWithConstraints(spark, tablePath)
        validResult  <- insertValidUserData(spark, tablePath)
        _            <- IO.delay(validResult should be(true))
        invalidResult <- insertInvalidUserData(spark, tablePath)
        _            <- IO.delay(invalidResult should be(false))
        count        <- IO.delay(spark.read.format("delta").load(tablePath).count())
        _            <- IO.delay(count should be(2))
      } yield succeed)
    }

  "Delta table with CHECK constraints" should "validate business rules" in
    createSparkResource().use { spark =>
      if (!shouldRun)
        IO { org.scalatest.Assertions.assume(shouldRun, "Skipping Spark IT: set -DwithSparkIT=true or WITH_SPARK_IT=true"); succeed }
      else (
      for {
        tablePath   <- IO.delay(Files.createTempDirectory("delta-check-test").toString)
        _           <- createUsersTableWithCheckConstraints(spark, tablePath)
        validResult <- insertValidAgeData(spark, tablePath)
        _           <- IO.delay(validResult should be(true))
        invalidResult <- insertInvalidAgeData(spark, tablePath)
        _           <- IO.delay(invalidResult should be(false))
        users       <- IO.delay { import spark.implicits._; spark.read.format("delta").load(tablePath).as[EnrichedUser].collect() }
        _           <- IO.delay { users.foreach(u => { u.age should be >= 13; u.age should be <= 120 }) }
      } yield succeed)
    }

  "FlowForge pipeline with Delta sink" should "integrate with table constraints" in
    createSparkResource().use { spark =>
      if (!shouldRun)
        IO { org.scalatest.Assertions.assume(shouldRun, "Skipping Spark IT: set -DwithSparkIT=true or WITH_SPARK_IT=true"); succeed }
      else (
      for {
        tablePath  <- IO.delay(Files.createTempDirectory("delta-pipeline-test").toString)
        _          <- createComprehensiveUsersTable(spark, tablePath)
        sampleData <- generateFlowForgeSampleData(spark)
        _          <- writeWithDeltaConstraints(spark, sampleData, tablePath)
        finalCount <- IO.delay(spark.read.format("delta").load(tablePath).count())
        _          <- IO.delay(finalCount should be > 0L)
        _          <- validateAllConstraints(spark, tablePath)
      } yield succeed)
    }

  private def createUsersTableWithConstraints(spark: SparkSession, tablePath: String): IO[Unit] = IO.delay {
    import spark.implicits._
    val emptyUsers = spark.emptyDataset[EnrichedUser].toDF()
    emptyUsers.write.format("delta").mode("overwrite").save(tablePath)
    spark.sql(s"ALTER TABLE delta.`$tablePath` ALTER COLUMN id SET NOT NULL")
    spark.sql(s"ALTER TABLE delta.`$tablePath` ALTER COLUMN email SET NOT NULL")
  }

  private def createUsersTableWithCheckConstraints(spark: SparkSession, tablePath: String): IO[Unit] = IO.delay {
    import spark.implicits._
    val emptyUsers = spark.emptyDataset[EnrichedUser].toDF()
    emptyUsers.write.format("delta").mode("overwrite").save(tablePath)
    spark.sql(s"ALTER TABLE delta.`$tablePath` ADD CONSTRAINT valid_age CHECK (age >= 13 AND age <= 120)")
    spark.sql(s"ALTER TABLE delta.`$tablePath` ADD CONSTRAINT valid_email CHECK (email RLIKE '^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\\\.[A-Za-z]{2,}$$')")
  }

  private def createComprehensiveUsersTable(spark: SparkSession, tablePath: String): IO[Unit] = IO.delay {
    import spark.implicits._
    val emptyUsers = spark.emptyDataset[EnrichedUser].toDF()
    emptyUsers.write.format("delta").mode("overwrite").save(tablePath)
    spark.sql(s"ALTER TABLE delta.`$tablePath` ALTER COLUMN id SET NOT NULL")
    spark.sql(s"ALTER TABLE delta.`$tablePath` ALTER COLUMN email SET NOT NULL")
    spark.sql(s"ALTER TABLE delta.`$tablePath` ADD CONSTRAINT valid_age CHECK (age >= 13 AND age <= 120)")
    spark.sql(s"ALTER TABLE delta.`$tablePath` ADD CONSTRAINT valid_email CHECK (email RLIKE '^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\\\.[A-Za-z]{2,}$$')")
    spark.sql(s"ALTER TABLE delta.`$tablePath` ADD CONSTRAINT valid_region CHECK (region IN ('North America','Europe','Asia','Oceania','Other'))")
  }

  private def insertValidUserData(spark: SparkSession, tablePath: String): IO[Boolean] = IO.delay {
    import spark.implicits._
    Try {
      Seq(
        EnrichedUser("u001", "alice@example.com", 28, "USA", 1672531200L, true, "middle", "North America"),
        EnrichedUser("u002", "bob@test.com", 35, "Canada", 1672531200L, true, "middle", "North America"),
      ).toDF().write.format("delta").mode("append").save(tablePath)
      true
    }.getOrElse(false)
  }

  private def insertInvalidUserData(spark: SparkSession, tablePath: String): IO[Boolean] = IO.delay {
    import spark.implicits._
    Try {
      Seq(
        EnrichedUser(null, "invalid@test.com", 25, "USA", 1672531200L, true, "young", "North America"),
        EnrichedUser("u003", null, 30, "Canada", 1672531200L, true, "middle", "North America"),
      ).toDF().write.format("delta").mode("append").save(tablePath)
      false
    }.recover { case _: Exception => true }.get
  }

  private def insertValidAgeData(spark: SparkSession, tablePath: String): IO[Boolean] = IO.delay {
    import spark.implicits._
    Try {
      Seq(
        EnrichedUser("u001", "alice@example.com", 25, "USA", 1672531200L, true, "young", "North America"),
        EnrichedUser("u002", "bob@test.com", 45, "Canada", 1672531200L, true, "middle", "North America"),
      ).toDF().write.format("delta").mode("append").save(tablePath)
      true
    }.getOrElse(false)
  }

  private def insertInvalidAgeData(spark: SparkSession, tablePath: String): IO[Boolean] = IO.delay {
    import spark.implicits._
    Try {
      Seq(
        EnrichedUser("u004", "bad@test.com", 10, "USA", 1672531200L, true, "young", "North America"),
      ).toDF().write.format("delta").mode("append").save(tablePath)
      false
    }.recover { case _: Exception => true }.get
  }

  private def generateFlowForgeSampleData(spark: SparkSession): IO[DataFrame] = IO.delay {
    import spark.implicits._
    Seq(
      EnrichedUser("u010", "ok@example.com", 30, "USA", 1672531200L, true, "middle", "North America"),
      EnrichedUser("u011", "ok2@example.com", 40, "USA", 1672531200L, true, "middle", "North America"),
    ).toDF()
  }

  private def writeWithDeltaConstraints(spark: SparkSession, df: DataFrame, tablePath: String): IO[Unit] = IO.delay {
    df.write.format("delta").mode("append").save(tablePath)
  }

  private def validateAllConstraints(spark: SparkSession, tablePath: String): IO[Unit] = IO.delay {
    import spark.implicits._
    spark.read.format("delta").load(tablePath).as[EnrichedUser].collect().foreach { u =>
      assert(u.id != null)
      assert(u.email != null)
      assert(u.age >= 13 && u.age <= 120)
      assert(Set("North America", "Europe", "Asia", "Oceania", "Other").contains(u.region))
    }
  }
}

