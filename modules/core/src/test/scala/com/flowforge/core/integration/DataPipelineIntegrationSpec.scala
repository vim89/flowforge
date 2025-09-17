/**
 * Integration tests for complete data pipeline scenarios.
 *
 * These tests verify that different FlowForge components work together correctly in realistic data processing
 * scenarios.
 */

package com.flowforge.core.integration

import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import cats.implicits._
import com.flowforge.core.instances.EffectInstances.catsEffectSystemInstance
import org.scalatest.funspec.AsyncFunSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._

class DataPipelineIntegrationSpec extends AsyncFunSpec with AsyncIOSpec with Matchers {

  implicit val es = catsEffectSystemInstance

  // Mock data types for testing
  case class RawData(
    id: String,
    value: Double,
    timestamp: Long)
  case class ProcessedData(
    id: String,
    normalizedValue: Double,
    category: String)
  case class AggregatedData(
    category: String,
    totalValue: Double,
    count: Int)

  describe("Complete Data Pipeline Integration") {

    it("should process data through extraction, transformation, and loading stages") {
      // Simulate data extraction
      val extractData: IO[List[RawData]] = es.delay {
        List(
          RawData("1", 100.0, System.currentTimeMillis() - 3600000),
          RawData("2", 250.0, System.currentTimeMillis() - 1800000),
          RawData("3", 75.0, System.currentTimeMillis() - 900000),
          RawData("4", 400.0, System.currentTimeMillis()),
          RawData("5", 180.0, System.currentTimeMillis() - 2700000),
        )
      }

      // Simulate data transformation with validation
      def transformData(raw: RawData): IO[Option[ProcessedData]] = es.delay {
        if (raw.value > 0) {
          val normalizedValue = math.min(raw.value / 500.0, 1.0) // Normalize to [0, 1]
          val category        = if (raw.value > 200) "high" else if (raw.value > 100) "medium" else "low"
          Some(ProcessedData(raw.id, normalizedValue, category))
        } else None // Filter out invalid data
      }

      // Simulate data aggregation
      def aggregateData(processed: List[ProcessedData]): IO[List[AggregatedData]] = es.delay {
        processed
          .groupBy(_.category)
          .map {
            case (category, items) =>
              AggregatedData(
                category = category,
                totalValue = items.map(_.normalizedValue).sum,
                count = items.length,
              )
          }
          .toList
          .sortBy(_.category)
      }

      // Simulate data loading with retry logic
      def loadData(aggregated: List[AggregatedData]): IO[String] = {
        var attempts = 0
        val operation = es.delay {
          attempts += 1
          if (attempts < 2) throw new RuntimeException("Temporary database failure")
          s"Successfully loaded ${aggregated.length} aggregated records"
        }
        es.retryWithBackoff(operation, maxRetries = 3, initialDelay = 10.millis)
      }

      // Execute the complete pipeline
      val pipeline = for {
        // Extract
        raw <- extractData

        // Transform (parallel processing)
        transformed <- es.parTraverse(raw)(transformData)
        processed = transformed.flatten

        // Validate we have expected data
        _ <- es.delay(assert(processed.nonEmpty, "Should have processed data"))

        // Aggregate
        aggregated <- aggregateData(processed)

        // Load with error handling
        result <- loadData(aggregated)

      } yield (raw, processed, aggregated, result)

      pipeline.map {
        case (raw, processed, aggregated, result) =>
          raw should have length 5
          processed should have length 5 // All data should be valid
          aggregated should not be empty
          result should include("Successfully loaded")

          // Verify data transformations
          processed.foreach { p =>
            p.normalizedValue should be >= 0.0
            p.normalizedValue should be <= 1.0
            // p.category should be oneOf ("low", "medium", "high")
          }

          // Verify aggregations
          val totalOriginalCount   = processed.length
          val totalAggregatedCount = aggregated.map(_.count).sum
          totalAggregatedCount should equal(totalOriginalCount)
      }
    }

    it("should handle pipeline failures gracefully") {
      var cleanupCalled = false

      val failingPipeline = es
        .bracket(
          acquire = es.delay("database-connection"),
        )(
          use = _ => es.raiseError(new RuntimeException("Pipeline processing failed")),
        )(
          release = _ => es.delay { cleanupCalled = true },
        )
        .attempt

      failingPipeline.map { result =>
        result shouldBe a[Left[_, _]]
        cleanupCalled should be(true)
      }
    }

    it("should support concurrent pipeline execution") {
      def createPipeline(id: Int): IO[String] = {
        val processingTime = (50 + (id % 3) * 25).millis // Vary processing times
        es.sleep(processingTime) >> es.pure(s"Pipeline-$id completed")
      }

      val start = System.currentTimeMillis()

      val concurrentPipelines = es.parTraverse((1 to 5).toList)(createPipeline)

      concurrentPipelines.map { results =>
        val elapsed = System.currentTimeMillis() - start

        results should have length 5
        results should contain("Pipeline-1 completed")
        results should contain("Pipeline-5 completed")

        // Should complete much faster than sequential execution
        elapsed should be < 200L // Much less than 5 * 100ms = 500ms
      }
    }

    it("should maintain data lineage through transformations") {
      case class LineageRecord(
        operation: String,
        inputIds: List[String],
        outputIds: List[String],
        timestamp: Long)

      var lineageRecords = List.empty[LineageRecord]

      def recordLineage(
        operation: String,
        inputIds: List[String],
        outputIds: List[String],
      ): IO[Unit] = es.delay {
        lineageRecords = LineageRecord(
          operation,
          inputIds,
          outputIds,
          System.currentTimeMillis(),
        ) :: lineageRecords
      }

      val input = List("raw-1", "raw-2", "raw-3")

      val pipelineWithLineage = for {
        // Transform step 1
        step1Results <- es.traverse(input)(id => es.pure(s"$id-transformed"))
        _            <- recordLineage("transform", input, step1Results)

        // Transform step 2 (aggregation)
        step2Result = List(s"aggregated-${step1Results.length}-records")
        _ <- recordLineage("aggregate", step1Results, step2Result)

        // Final output
        finalResult = s"final-output-${step2Result.head}"
        _ <- recordLineage("output", step2Result, List(finalResult))

      } yield (finalResult, lineageRecords.reverse)

      pipelineWithLineage.map {
        case (result, lineage) =>
          result should equal("final-output-aggregated-3-records")
          lineage should have length 3

          lineage(0).operation should equal("transform")
          lineage(0).inputIds should equal(input)

          lineage(1).operation should equal("aggregate")
          lineage(1).inputIds.length should equal(3)

          lineage(2).operation should equal("output")
          lineage(2).outputIds should have length 1
      }
    }

    it("should support data quality validation") {
      case class QualityResult(passed: Boolean, errors: List[String])

      def validateDataQuality(data: List[ProcessedData]): IO[QualityResult] = es.delay {
        val errors = scala.collection.mutable.ListBuffer[String]()

        // Check for null/empty IDs
        if (data.exists(_.id.isEmpty)) {
          errors += "Found records with empty IDs"
        }

        // Check for out-of-range values
        val invalidValues = data.filter(d => d.normalizedValue < 0 || d.normalizedValue > 1)
        if (invalidValues.nonEmpty) {
          errors += s"Found ${invalidValues.length} records with invalid normalized values"
        }

        // Check for missing categories
        if (data.exists(d => !Set("low", "medium", "high").contains(d.category))) {
          errors += "Found records with invalid categories"
        }

        QualityResult(errors.isEmpty, errors.toList)
      }

      val validData = List(
        ProcessedData("1", 0.5, "medium"),
        ProcessedData("2", 0.8, "high"),
        ProcessedData("3", 0.2, "low"),
      )

      val invalidData = List(
        ProcessedData("", 0.5, "medium"),  // Invalid: empty ID
        ProcessedData("2", 1.5, "high"),   // Invalid: out of range
        ProcessedData("3", 0.2, "invalid"), // Invalid: bad category
      )

      val validationTest = for {
        validResult   <- validateDataQuality(validData)
        invalidResult <- validateDataQuality(invalidData)
      } yield (validResult, invalidResult)

      validationTest.map {
        case (valid, invalid) =>
          valid.passed should be(true)
          valid.errors should be(empty)

          invalid.passed should be(false)
          invalid.errors should have length 3
          invalid.errors should contain("Found records with empty IDs")
          invalid.errors should contain("Found 1 records with invalid normalized values")
          invalid.errors should contain("Found records with invalid categories")
      }
    }
  }
}
