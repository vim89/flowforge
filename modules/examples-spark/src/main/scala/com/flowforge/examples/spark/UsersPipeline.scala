package com.flowforge.examples.spark

import cats.effect.{ IO, Resource }
import com.flowforge.core.algebra.DataAlgebra.{ DatasetMetadata, QualityResult }
import com.flowforge.core.types.{ DataSchema, QualityConstraint => FFConstraint, StructField }
import com.flowforge.core.types.RefinedTypes.{ FieldName, SchemaVersion }
import java.time.Instant
import com.flowforge.engines.spark.ProductionSparkDataset
import com.flowforge.quality.deequ.DeequAdapter
import org.apache.spark.sql.{ DataFrame, SparkSession }
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types._

/**
 * Polished end-to-end Spark pipeline example demonstrating FlowForge v1.0 capabilities.
 *
 * Features demonstrated:
 *   - Type-safe dataset transformations with ProductionSparkDataset
 *   - Data quality validation with dual-mode support (native/Deequ)
 *   - Lineage tracking (when configured with OpenLineage)
 *   - Delta Lake integration with constraints
 *   - Error handling with Either monads (CLAUDE.md compliance)
 *   - Resource-safe Spark operations
 */
object UsersPipeline {

  // Helper to create ProductionSparkDataset without DataDecoder
  private def createDataset[A](df: DataFrame, sampleData: List[A]): ProductionSparkDataset[A] = {
    val schema = DataSchema(
      fields = List.empty[StructField],
      version = SchemaVersion.unsafeFrom(1), // Version 1
      metadata = Map.empty,
      createdAt = Instant.now(),
    )
    val metadata = DatasetMetadata(
      recordCount = df.count(),
      schema = schema,
      partitions = df.rdd.getNumPartitions,
      createdAt = Instant.now(),
      source = None,
    )
    ProductionSparkDataset(sampleData, df, schema, metadata)
  }

  // Domain models
  case class RawUser(
    id: String,
    email: String,
    age: Option[Int],
    country: String,
    signupDate: String,
    isActive: Boolean)

  case class CleanedUser(
    id: String,
    email: String,
    age: Int,
    country: String,
    signupTimestamp: Long,
    isActive: Boolean)

  case class EnrichedUser(
    id: String,
    email: String,
    age: Int,
    country: String,
    signupTimestamp: Long,
    isActive: Boolean,
    ageGroup: String,
    region: String)

  /**
   * Complete pipeline execution with resource safety and error handling
   */
  def run(): IO[Unit] = {
    val sparkConfig = Map(
      "spark.master"                                  -> "local[*]",
      "spark.app.name"                                -> "FlowForge-UsersPipeline-Example",
      "spark.sql.extensions"                          -> "io.delta.sql.DeltaSparkSessionExtension",
      "spark.sql.catalog.spark_catalog"               -> "org.apache.spark.sql.delta.catalog.DeltaCatalog",
      "spark.serializer"                              -> "org.apache.spark.serializer.KryoSerializer",
      "spark.sql.adaptive.enabled"                    -> "true",
      "spark.sql.adaptive.coalescePartitions.enabled" -> "true",
    )

    createSparkResource(sparkConfig).use { spark =>
      for {
        _ <- IO.println("🚀 Starting FlowForge UsersPipeline example")
        _ <- executeUsersPipeline(spark)
        _ <- IO.println("✅ UsersPipeline completed successfully")
      } yield ()
    }
  }

  private def createSparkResource(config: Map[String, String]): Resource[IO, SparkSession] =
    Resource.make(
      IO.delay {
        val builder = SparkSession.builder()
        config.foreach { case (key, value) => builder.config(key, value) }
        builder.getOrCreate()
      },
    )(spark => IO.delay(spark.stop()))

  private def executeUsersPipeline(spark: SparkSession): IO[Unit] =
    for {
      // Generate sample data
      rawData <- generateSampleData(spark)
      sampleRawUsers = List(RawUser("sample", "sample@test.com", Some(25), "USA", "2023-01-01", true))
      rawDataset     = createDataset(rawData, sampleRawUsers)
      _ <- IO.println(s"📊 Generated ${rawData.count()} raw user records")

      // Execute pipeline stages
      cleanedResult <- cleanUserData(rawDataset)
      cleanedDataset <- cleanedResult match {
        case Right(ds)   => IO.pure(ds)
        case Left(error) => IO.raiseError(new RuntimeException(s"Data cleaning failed: $error"))
      }

      // Quality validation
      qualityResult <- validateCleanedData(cleanedDataset)
      _             <- handleQualityResult(qualityResult)

      // Enrichment
      enrichedResult <- enrichUserData(qualityResult.data)
      finalDataset <- enrichedResult match {
        case Right(ds)   => IO.pure(ds)
        case Left(error) => IO.raiseError(new RuntimeException(s"Enrichment failed: $error"))
      }

      // Display and save results
      _ <- displayResults(finalDataset)
      _ <- saveResults(finalDataset)
    } yield ()

  private def generateSampleData(spark: SparkSession): IO[DataFrame] = {
    import spark.implicits._

    IO.delay {
      val sampleUsers = Seq(
        RawUser("u001", "alice@example.com", Some(28), "USA", "2023-01-15", true),
        RawUser("u002", "bob@test.com", Some(35), "Canada", "2023-02-20", true),
        RawUser("u003", "charlie@demo.org", None, "UK", "2023-03-10", false),
        RawUser("u004", "diana@sample.net", Some(22), "Australia", "2023-04-05", true),
        RawUser("u005", "eve@example.co.uk", Some(45), "UK", "2023-05-12", true),
        RawUser("u006", "frank@test.ca", Some(19), "Canada", "2023-06-18", false),
        RawUser("u007", "grace@demo.com.au", Some(52), "Australia", "2023-07-22", true),
        RawUser("u008", "henry@sample.com", Some(30), "USA", "2023-08-14", true),
        RawUser("u009", "", Some(25), "Germany", "2023-09-09", true), // Invalid email for testing
        RawUser("u010", "ivan@example.de", Some(-5), "Germany", "2023-10-01", true), // Invalid age for testing
      )

      sampleUsers.toDF()
    }
  }

  /**
   * Data cleaning transformation with proper error handling
   */
  private def cleanUserData(dataset: ProductionSparkDataset[RawUser])
    : IO[Either[String, ProductionSparkDataset[CleanedUser]]] =
    IO.delay {
      val spark = dataset.sparkDataFrame.sparkSession
      import spark.implicits._

      scala.util.Try {
        // Apply cleaning transformations using pure functions
        val cleaned = dataset.sparkDataFrame
          .filter($"email".isNotNull && $"email" =!= "")          // Remove invalid emails
          .filter($"age".isNotNull && $"age" > 0 && $"age" < 120) // Valid age range
          .withColumn("signupTimestamp", unix_timestamp($"signupDate", "yyyy-MM-dd").cast(LongType))
          .select(
            $"id",
            $"email",
            $"age".cast(IntegerType),
            $"country",
            $"signupTimestamp",
            $"isActive",
          )

        val sampleCleanedUsers = List(CleanedUser("sample", "sample@test.com", 25, "USA", 1672531200L, true))
        createDataset(cleaned, sampleCleanedUsers)
      }.toEither.left.map(_.getMessage)
    }

  /**
   * Data quality validation using FlowForge quality framework
   */
  private def validateCleanedData(dataset: ProductionSparkDataset[CleanedUser])
    : IO[QualityResult[ProductionSparkDataset[CleanedUser]]] =
    IO.delay {
      val constraints = List(
        FFConstraint.NotNull(FieldName("id")),
        FFConstraint.NotNull(FieldName("email")),
        FFConstraint.Unique(FieldName("id")),
        FFConstraint.Range(FieldName("age"), Some(13.0), Some(100.0)),
        FFConstraint.Pattern(FieldName("email"), "^[A-Za-z0-9+_.-]+@(.+)$"),
        FFConstraint.Compliance(
          "active-user-check",
          "isActive = true OR signupTimestamp > unix_timestamp('2023-01-01', 'yyyy-MM-dd')",
        ),
      )

      val spark  = dataset.sparkDataFrame.sparkSession
      val result = DeequAdapter.runChecks(spark, dataset, constraints)
      // Cast the result to the expected type due to variance
      result.asInstanceOf[QualityResult[ProductionSparkDataset[CleanedUser]]]
    }

  private def handleQualityResult(result: QualityResult[ProductionSparkDataset[CleanedUser]]): IO[Unit] =
    IO.delay {
      println(s"\\n🔍 Quality Check Results:")
      println(s"   Passed: ${result.passed}")
      println(s"   Score: ${(result.score * 100).toInt}%")

      if (result.violations.nonEmpty) {
        println(s"   Violations found:")
        result.violations.foreach { violation =>
          println(s"     - ${violation.rule}: ${violation.message} (${violation.recordsAffected} records)")
        }
      }

      if (!result.passed) {
        println("⚠️ Quality checks failed but continuing with available data...")
      }
    }

  /**
   * Data enrichment with business logic
   */
  private def enrichUserData(dataset: ProductionSparkDataset[CleanedUser])
    : IO[Either[String, ProductionSparkDataset[EnrichedUser]]] =
    IO.delay {
      val spark = dataset.sparkDataFrame.sparkSession
      import spark.implicits._

      scala.util.Try {
        val enriched = dataset.sparkDataFrame
          .withColumn(
            "ageGroup",
            when($"age" < 25, "young")
              .when($"age" < 45, "middle")
              .otherwise("senior"),
          )
          .withColumn(
            "region",
            when($"country".isin("USA", "Canada"), "North America")
              .when($"country".isin("UK", "Germany", "France"), "Europe")
              .when($"country".isin("Australia", "New Zealand"), "Oceania")
              .otherwise("Other"),
          )
          .select(
            $"id",
            $"email",
            $"age",
            $"country",
            $"signupTimestamp",
            $"isActive",
            $"ageGroup",
            $"region",
          )

        val sampleEnrichedUsers = List(
          EnrichedUser("sample", "sample@test.com", 25, "USA", 1672531200L, true, "young", "North America"),
        )
        createDataset(enriched, sampleEnrichedUsers)
      }.toEither.left.map(_.getMessage)
    }

  private def displayResults(dataset: ProductionSparkDataset[EnrichedUser]): IO[Unit] =
    IO.delay {
      println("\\n📈 Pipeline Results:")
      println("===================")
      dataset.sparkDataFrame.show(10, truncate = false)

      println("\\n📊 Summary Statistics:")
      dataset.sparkDataFrame
        .groupBy("region", "ageGroup")
        .count()
        .orderBy("region", "ageGroup")
        .show()
    }

  private def saveResults(dataset: ProductionSparkDataset[EnrichedUser]): IO[Unit] =
    IO.delay {
      val outputPath = "/tmp/flowforge/users-pipeline-output"

      println(s"\\n💾 Saving results to: $outputPath")
      dataset.sparkDataFrame
        .coalesce(1)
        .write
        .mode("overwrite")
        .option("header", "true")
        .csv(outputPath)

      println("✅ Results saved successfully")
    }

  // Example main method for standalone execution
  def main(args: Array[String]): Unit = {
    import cats.effect.unsafe.implicits.global
    run().unsafeRunSync()
  }
}

/**
 * Companion object with utility methods and factory functions
 */
object UsersPipelineUtils {

  /**
   * Quality validation preset for user data
   */
  def userDataQualityConstraints(): List[FFConstraint] =
    List(
      FFConstraint.NotNull(FieldName("id")),
      FFConstraint.NotNull(FieldName("email")),
      FFConstraint.Unique(FieldName("id")),
      FFConstraint.Unique(FieldName("email")),
      FFConstraint.Range(FieldName("age"), Some(13.0), Some(120.0)),
      FFConstraint.Pattern(FieldName("email"), "^[\\\\w._%+-]+@[\\\\w.-]+\\\\.[A-Za-z]{2,}$"),
      FFConstraint.Compliance("recent-signup", "signupTimestamp > unix_timestamp('2020-01-01', 'yyyy-MM-dd')"),
    )

  /**
   * Common data transformations for user pipelines
   */
  object Transformations {

    def normalizeEmail(emailCol: String = "email"): org.apache.spark.sql.Column =
      lower(trim(col(emailCol)))

    def calculateAge(birthDateCol: String = "birthDate"): org.apache.spark.sql.Column =
      floor(datediff(current_date(), col(birthDateCol)) / 365.25)

    def classifyUserSegment(ageCol: String = "age", activityCol: String = "isActive")
      : org.apache.spark.sql.Column =
      when(col(ageCol) < 25 && col(activityCol), "young-active")
        .when(col(ageCol) < 25 && !col(activityCol), "young-inactive")
        .when(col(ageCol) < 45 && col(activityCol), "middle-active")
        .when(col(ageCol) < 45 && !col(activityCol), "middle-inactive")
        .when(col(activityCol), "senior-active")
        .otherwise("senior-inactive")
  }
}
