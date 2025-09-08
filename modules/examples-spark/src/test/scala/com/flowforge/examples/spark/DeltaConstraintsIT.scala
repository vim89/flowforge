package com.flowforge.examples.spark

import cats.effect.testing.scalatest.AsyncIOSpec
import cats.effect.{ IO, Resource }
import com.flowforge.examples.spark.UsersPipeline.EnrichedUser
import org.apache.spark.sql.{ DataFrame, SparkSession }
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers

import java.nio.file.Files
import scala.util.Try

/**
 * Integration test demonstrating Delta Lake constraints with FlowForge pipelines.
 *
 * Per v1.0 plan: "Guard an IT that creates a Delta table and shows NOT NULL / CHECK constraints"
 *
 * This test proves:
 *   - Delta table creation with schema constraints
 *   - NOT NULL constraint enforcement
 *   - CHECK constraint validation
 *   - Integration with FlowForge data quality framework
 *   - Proper error handling when constraints are violated
 */
class DeltaConstraintsIT extends AsyncFlatSpec with AsyncIOSpec with Matchers {

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
          .config("spark.sql.shuffle.partitions", "2") // Reduce for local testing
          .config("spark.test.home", System.getProperty("java.io.tmpdir"))
          .getOrCreate()
      },
    )(spark => IO.delay(spark.stop()))

  "Delta table with NOT NULL constraints" should "enforce schema validation" in
    createSparkResource().use { spark =>
      for {
        // Create Delta table with NOT NULL constraints
        tablePath <- IO.delay(Files.createTempDirectory("delta-not-null-test").toString)
        _         <- createUsersTableWithConstraints(spark, tablePath)

        // Test valid data insertion - should succeed
        validResult <- insertValidUserData(spark, tablePath)
        _           <- IO.delay(validResult should be(true))

        // Test invalid data insertion - should fail with constraint violation
        invalidResult <- insertInvalidUserData(spark, tablePath)
        _             <- IO.delay(invalidResult should be(false))

        // Verify table contents
        count <- IO.delay(spark.read.format("delta").load(tablePath).count())
        _     <- IO.delay(count should be(2)) // Only valid records should be present

      } yield succeed
    }

  "Delta table with CHECK constraints" should "validate business rules" in
    createSparkResource().use { spark =>
      for {
        // Create Delta table with CHECK constraints
        tablePath <- IO.delay(Files.createTempDirectory("delta-check-test").toString)
        _         <- createUsersTableWithCheckConstraints(spark, tablePath)

        // Test data that passes CHECK constraints
        validResult <- insertValidAgeData(spark, tablePath)
        _           <- IO.delay(validResult should be(true))

        // Test data that violates CHECK constraints (age < 13 or > 120)
        invalidResult <- insertInvalidAgeData(spark, tablePath)
        _             <- IO.delay(invalidResult should be(false))

        // Verify business rule enforcement
        users <- IO.delay {
          import spark.implicits._
          spark.read.format("delta").load(tablePath).as[EnrichedUser].collect()
        }
        _ <- IO.delay {
          users.foreach { user =>
            user.age should be >= 13
            user.age should be <= 120
          }
        }

      } yield succeed
    }

  "FlowForge pipeline with Delta sink" should "integrate with table constraints" in
    createSparkResource().use { spark =>
      for {
        // Create Delta table with comprehensive constraints
        tablePath <- IO.delay(Files.createTempDirectory("delta-pipeline-test").toString)
        _         <- createComprehensiveUsersTable(spark, tablePath)

        // Generate sample data using FlowForge pipeline approach
        sampleData <- generateFlowForgeSampleData(spark)

        // Attempt to write data through Delta - should enforce all constraints
        _ <- writeWithDeltaConstraints(spark, sampleData, tablePath)

        // Verify that only valid data was written
        finalCount <- IO.delay(spark.read.format("delta").load(tablePath).count())
        _          <- IO.delay(finalCount should be > 0L) // Some valid data should exist

        // Verify all records meet constraints
        _ <- validateAllConstraints(spark, tablePath)

        _ <- IO.println(s"✅ Delta constraints IT passed - ${finalCount} valid records written")

      } yield succeed
    }

  private def createUsersTableWithConstraints(spark: SparkSession, tablePath: String): IO[Unit] =
    IO.delay {
      import spark.implicits._

      // Create initial empty DataFrame with correct schema
      val emptyUsers = spark.emptyDataset[EnrichedUser].toDF()

      // Write initial table structure
      emptyUsers.write.format("delta").mode("overwrite").save(tablePath)

      // Add NOT NULL constraints
      spark.sql(s"""
        ALTER TABLE delta.`$tablePath` 
        ALTER COLUMN id SET NOT NULL
      """)

      spark.sql(s"""
        ALTER TABLE delta.`$tablePath` 
        ALTER COLUMN email SET NOT NULL
      """)

      println(s"✅ Created Delta table with NOT NULL constraints at: $tablePath")
    }

  private def createUsersTableWithCheckConstraints(spark: SparkSession, tablePath: String): IO[Unit] =
    IO.delay {
      import spark.implicits._

      // Create initial table
      val emptyUsers = spark.emptyDataset[EnrichedUser].toDF()
      emptyUsers.write.format("delta").mode("overwrite").save(tablePath)

      // Add CHECK constraints for business rules
      spark.sql(s"""
        ALTER TABLE delta.`$tablePath` 
        ADD CONSTRAINT valid_age CHECK (age >= 13 AND age <= 120)
      """)

      spark.sql(s"""
        ALTER TABLE delta.`$tablePath` 
        ADD CONSTRAINT valid_email CHECK (email RLIKE '^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$$')
      """)

      println(s"✅ Created Delta table with CHECK constraints at: $tablePath")
    }

  private def createComprehensiveUsersTable(spark: SparkSession, tablePath: String): IO[Unit] =
    IO.delay {
      import spark.implicits._

      // Create initial table
      val emptyUsers = spark.emptyDataset[EnrichedUser].toDF()
      emptyUsers.write.format("delta").mode("overwrite").save(tablePath)

      // Add comprehensive constraints
      spark.sql(s"""
        ALTER TABLE delta.`$tablePath` 
        ALTER COLUMN id SET NOT NULL
      """)

      spark.sql(s"""
        ALTER TABLE delta.`$tablePath` 
        ALTER COLUMN email SET NOT NULL
      """)

      spark.sql(s"""
        ALTER TABLE delta.`$tablePath` 
        ADD CONSTRAINT valid_age CHECK (age >= 13 AND age <= 120)
      """)

      spark.sql(s"""
        ALTER TABLE delta.`$tablePath` 
        ADD CONSTRAINT valid_email CHECK (email RLIKE '^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$$')
      """)

      spark.sql(s"""
        ALTER TABLE delta.`$tablePath` 
        ADD CONSTRAINT valid_region CHECK (region IN ('North America', 'Europe', 'Asia', 'Oceania', 'Other'))
      """)

      println(s"✅ Created comprehensive Delta table with all constraints at: $tablePath")
    }

  private def insertValidUserData(spark: SparkSession, tablePath: String): IO[Boolean] =
    IO.delay {
      Try {
        import spark.implicits._

        val validUsers = Seq(
          EnrichedUser("u001", "alice@example.com", 28, "USA", 1672531200L, true, "middle", "North America"),
          EnrichedUser("u002", "bob@test.com", 35, "Canada", 1672531200L, true, "middle", "North America"),
        )

        validUsers.toDF().write.format("delta").mode("append").save(tablePath)
        true
      }.getOrElse(false)
    }

  private def insertInvalidUserData(spark: SparkSession, tablePath: String): IO[Boolean] =
    IO.delay {
      Try {
        import spark.implicits._

        // This should fail due to NOT NULL constraint violation
        val invalidUsers = Seq(
          EnrichedUser(
            null,
            "invalid@test.com",
            25,
            "USA",
            1672531200L,
            true,
            "young",
            "North America",
          ),                                                                                     // NULL id
          EnrichedUser("u003", null, 30, "Canada", 1672531200L, true, "middle", "North America"), // NULL email
        )

        invalidUsers.toDF().write.format("delta").mode("append").save(tablePath)
        false // Should not reach here if constraints work
      }.recover {
        case _: Exception => true // Expected constraint violation
      }.get
    }

  private def insertValidAgeData(spark: SparkSession, tablePath: String): IO[Boolean] =
    IO.delay {
      Try {
        import spark.implicits._

        val validAgeUsers = Seq(
          EnrichedUser("u001", "alice@example.com", 25, "USA", 1672531200L, true, "young", "North America"),
          EnrichedUser("u002", "bob@test.com", 45, "Canada", 1672531200L, true, "middle", "North America"),
        )

        validAgeUsers.toDF().write.format("delta").mode("append").save(tablePath)
        true
      }.getOrElse(false)
    }

  private def insertInvalidAgeData(spark: SparkSession, tablePath: String): IO[Boolean] =
    IO.delay {
      Try {
        import spark.implicits._

        // This should fail due to CHECK constraint violation
        val invalidAgeUsers = Seq(
          EnrichedUser(
            "u003",
            "child@test.com",
            8,
            "USA",
            1672531200L,
            true,
            "young",
            "North America",
          ), // age < 13
          EnrichedUser(
            "u004",
            "elder@test.com",
            150,
            "Canada",
            1672531200L,
            true,
            "senior",
            "North America",
          ), // age > 120
        )

        invalidAgeUsers.toDF().write.format("delta").mode("append").save(tablePath)
        false // Should not reach here
      }.recover {
        case _: Exception => true // Expected constraint violation
      }.get
    }

  private def generateFlowForgeSampleData(spark: SparkSession): IO[DataFrame] =
    IO.delay {
      import spark.implicits._

      // Generate mix of valid and invalid data to test constraints
      val mixedData = Seq(
        EnrichedUser(
          "u001",
          "alice@example.com",
          28,
          "USA",
          1672531200L,
          true,
          "middle",
          "North America",
        ), // Valid
        EnrichedUser(
          "u002",
          "bob@test.com",
          35,
          "Canada",
          1672531200L,
          true,
          "middle",
          "North America",
        ),                                                                             // Valid
        EnrichedUser("u003", "", 25, "Germany", 1672531200L, true, "young", "Europe"), // Invalid email
        EnrichedUser(
          "u004",
          "diana@sample.net",
          8,
          "Australia",
          1672531200L,
          true,
          "young",
          "Oceania",
        ), // Invalid age
        EnrichedUser("u005", "eve@example.co.uk", 45, "UK", 1672531200L, true, "middle", "Europe"), // Valid
      )

      mixedData.toDF()
    }

  private def writeWithDeltaConstraints(
    spark: SparkSession,
    data: DataFrame,
    tablePath: String,
  ): IO[Boolean] =
    IO.delay {
      Try {
        // Attempt to write all data - Delta should reject invalid records
        data.write.format("delta").mode("append").save(tablePath)
        true
      }.recover {
        case ex: Exception =>
          // Expected for some invalid data - this is normal behavior
          println(s"Delta constraint enforcement working: ${ex.getMessage}")
          false
      }.getOrElse(false)
    }

  private def validateAllConstraints(spark: SparkSession, tablePath: String): IO[Unit] =
    IO.delay {
      import spark.implicits._

      val users = spark.read.format("delta").load(tablePath).as[EnrichedUser].collect()

      users.foreach { user =>
        // Validate NOT NULL constraints
        user.id should not be null
        user.email should not be null

        // Validate CHECK constraints
        user.age should be >= 13
        user.age should be <= 120
        user.email should fullyMatch regex "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
        Set("North America", "Europe", "Asia", "Oceania", "Other") should contain(user.region)
      }

      println(s"✅ All ${users.length} records pass constraint validation")
    }
}
