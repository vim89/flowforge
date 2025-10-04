package com.flowforge.examples.spark

import cats.syntax.either._
import org.apache.spark.sql.SparkSession

/**
 * Generate tiny Parquet fixture for UsersPipeline example. This creates a small sample dataset that can be
 * processed in <10 seconds.
 */
object GenerateFixture {

  case class RawUser(
    id: String,
    email: String,
    age: Option[Int],
    country: String,
    signupDate: String,
    isActive: Boolean)

  def main(args: Array[String]): Unit = {
    val builder = SparkSession
      .builder()
      .appName("FlowForge-Fixture-Generator")
    sys.env.get("SPARK_MASTER").foreach(builder.master)
    val spark = builder.getOrCreate()

    import spark.implicits._

    val _ = Either.catchNonFatal {
      // Create tiny dataset - just enough to demonstrate functionality
      val rawUsers = Seq(
        RawUser("u001", "alice@example.com", Some(28), "USA", "2023-01-15", true),
        RawUser("u002", "bob@test.com", Some(35), "Canada", "2023-02-20", true),
        RawUser("u003", "charlie@demo.org", Some(22), "UK", "2023-03-10", false),
        RawUser("u004", "diana@sample.net", Some(41), "Australia", "2023-04-05", true),
        RawUser("u005", "eve@example.co.uk", Some(29), "UK", "2023-05-12", true),
        // Add a couple edge cases for quality validation
        RawUser("u006", "frank@test.ca", Some(19), "Canada", "2023-06-18", false),
        RawUser("u007", "grace@demo.com.au", Some(52), "Australia", "2023-07-22", true),
        RawUser("u008", "henry@sample.com", Some(30), "USA", "2023-08-14", true),
      )

      val fixtureDir = "modules/examples-spark/src/main/resources/fixtures"
      val outputPath = s"$fixtureDir/raw-users.parquet"

      // Ensure output directory exists and is clean
      val outputFile = new java.io.File(outputPath)
      if (outputFile.exists()) {
        def deleteRecursively(file: java.io.File): Unit = {
          if (file.isDirectory) {
            file.listFiles().foreach(deleteRecursively)
          }
          file.delete()
        }
        deleteRecursively(outputFile)
      }

      // Write tiny Parquet fixture
      rawUsers
        .toDF()
        .coalesce(1) // Single partition for small fixture
        .write
        .mode("overwrite")
        .parquet(outputPath)

      println(s"✅ Generated Parquet fixture with ${rawUsers.size} records at: $outputPath")
      println(s"File size: ~${estimateSize(rawUsers.size)} bytes")
      println("Ready for <10s UsersPipeline example!")

    }
    Either.catchNonFatal(spark.stop())
  }

  private def estimateSize(records: Int): Int =
    // Rough estimate: ~150 bytes per record in Parquet with compression
    records * 150
}
