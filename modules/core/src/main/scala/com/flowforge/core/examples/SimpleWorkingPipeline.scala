/**
 * Simple working pipeline example demonstrating FlowForge core functionality.
 *
 * This example shows how to:
 *   1. Create type-safe pipeline stages 2. Use functional composition with Kleisli arrows 3. Execute
 *      pipelines with proper error handling 4. Demonstrate the architectural patterns
 */
package com.flowforge.core.examples

import cats.data.Kleisli
import cats.effect.{ IO, IOApp }
import cats.implicits._
import com.flowforge.core.algebra.EffectSystem
import com.flowforge.core.instances.EffectInstances.catsEffectSystemInstance

/**
 * Simple working pipeline that demonstrates core FlowForge patterns without external dependencies.
 */
object SimpleWorkingPipeline extends IOApp.Simple {

  // Sample data types for demonstration
  case class RawRecord(
    id: String,
    value: Double,
    category: String)
  case class ProcessedRecord(
    id: String,
    normalizedValue: Double,
    category: String,
    processed: Boolean = true)
  case class AggregatedRecord(
    category: String,
    totalValue: Double,
    count: Int)

  // Use the instance directly instead of creating implicit conflicts
  def es: EffectSystem[IO] = catsEffectSystemInstance

  def run: IO[Unit] = {
    println("🚀 FlowForge Simple Working Pipeline Demo")

    for {
      _      <- IO.println("\n📊 Phase 1: Data Extraction & Processing")
      result <- runCompletePipeline
      _      <- IO.println(s"\n✅ Pipeline completed successfully!")
      _      <- IO.println(s"📈 Final result: $result")
      _      <- IO.println("\n🎯 FlowForge Core Architecture Demo Complete")
    } yield ()
  }

  /**
   * Demonstrates complete pipeline using Kleisli composition
   */
  def runCompletePipeline: IO[List[AggregatedRecord]] = {
    // Define pipeline stages as Kleisli arrows for functional composition

    // Stage 1: Data extraction (mock)
    val extractStage: Kleisli[IO, Unit, List[RawRecord]] = Kleisli { _ =>
      val mockData = List(
        RawRecord("001", 150.0, "sales"),
        RawRecord("002", 75.0, "marketing"),
        RawRecord("003", 300.0, "sales"),
        RawRecord("004", 120.0, "support"),
        RawRecord("005", 200.0, "marketing"),
      )
      IO.println(s"📥 Extracted ${mockData.length} records") *> IO.pure(mockData)
    }

    // Stage 2: Data transformation with validation
    val transformStage: Kleisli[IO, List[RawRecord], List[ProcessedRecord]] = Kleisli { rawData =>
      IO.println("🔄 Transforming records...") *>
        rawData.traverse { record =>
          IO.delay {
            val normalizedValue = math.min(record.value / 500.0, 1.0) // Normalize to [0,1]
            ProcessedRecord(record.id, normalizedValue, record.category)
          }
        }.flatTap(processed => IO.println(s"✅ Transformed ${processed.length} records"))
    }

    // Stage 3: Data aggregation
    val aggregateStage: Kleisli[IO, List[ProcessedRecord], List[AggregatedRecord]] = Kleisli { processedData =>
      IO.println("📊 Aggregating by category...") *>
        IO.delay {
          processedData
            .groupBy(_.category)
            .map {
              case (category, records) =>
                AggregatedRecord(
                  category = category,
                  totalValue = records.map(_.normalizedValue).sum,
                  count = records.length,
                )
            }
            .toList
            .sortBy(_.category)
        }.flatTap(aggregated => IO.println(s"📈 Created ${aggregated.length} aggregated records"))
    }

    // Stage 4: Quality validation
    val qualityStage: Kleisli[IO, List[AggregatedRecord], List[AggregatedRecord]] = Kleisli { aggregated =>
      IO.println("🛡️  Validating data quality...") *>
        aggregated.traverse { record =>
          if (record.count > 0 && record.totalValue >= 0) {
            IO.pure(record)
          } else {
            IO.raiseError(new RuntimeException(s"Quality check failed for record: $record"))
          }
        }.flatTap(_ => IO.println("✅ All quality checks passed"))
    }

    // Compose the complete pipeline using Kleisli's andThen
    val completePipeline: Kleisli[IO, Unit, List[AggregatedRecord]] =
      extractStage.andThen(transformStage).andThen(aggregateStage).andThen(qualityStage)

    // Execute the pipeline
    completePipeline.run(())
  }

  /**
   * Demonstrates error handling and recovery patterns
   */
  def demonstrateErrorHandling: IO[Unit] = {
    println("\n🚨 Error Handling Demo")

    val flakyOperation: IO[String] = IO.delay {
      if (scala.util.Random.nextDouble() > 0.5) {
        "Success!"
      } else {
        throw new RuntimeException("Random failure occurred")
      }
    }

    val withRetryAndFallback = flakyOperation
      .handleErrorWith(_ => IO.pure("Fallback value"))
      .flatTap(result => IO.println(s"Result: $result"))

    withRetryAndFallback.void
  }

  /**
   * Demonstrates resource safety patterns
   */
  def demonstrateResourceSafety: IO[Unit] = {
    println("\n🛡️ Resource Safety Demo")

    val resourceOperation = es.bracket(
      acquire = IO.println("🔓 Acquiring resource...") *> IO.pure("mock-resource"),
    )(
      use = resource =>
        IO.println(s"⚙️  Using resource: $resource") *>
          IO.delay(s"Processed with $resource"),
    )(
      release = resource => IO.println(s"🔒 Released resource: $resource"),
    )

    resourceOperation.flatMap(result => IO.println(s"✅ Resource operation result: $result")).void
  }
}
