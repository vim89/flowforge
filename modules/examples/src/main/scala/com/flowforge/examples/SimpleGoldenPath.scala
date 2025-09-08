package com.flowforge.examples

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.flowforge.core.PipelineBuilder
import com.flowforge.core.contracts.SchemaPolicy
import com.flowforge.core.contracts.derive.Shape
import com.flowforge.core.instances.EffectInstances._
import com.flowforge.core.lineage.OpenLineageEmitter
import com.flowforge.core.types._

/**
 * FlowForge v1.0.0 Simple Golden Path Example
 *
 * Demonstrates the complete FlowForge value proposition in a minimal, working form: Typed Contract → Pipeline
 * Builder → Lineage Events
 *
 * This is the canonical "10-minute win" example per v100-plan Step 6.
 */
object SimpleGoldenPath {

  // Simple domain model
  case class SalesRecord(
    id: Long,
    customerId: String,
    amount: Double,
    region: String)

  // Typed contract evidence
  implicit val salesRecordShape: Shape[SalesRecord] = Shape.gen[SalesRecord]

  def main(args: Array[String]): Unit = {
    println("=== FlowForge v1.0.0 Simple Golden Path Demo ===")
    println("Typed Contract → Pipeline Builder → OpenLineage")
    println()

    runSimpleGoldenPath().unsafeRunSync()

    println("\n✅ Golden Path completed successfully!")
    println("✅ Contract-driven pipeline built and executed")
    println("✅ OpenLineage events emitted (noop mode)")
  }

  private def runSimpleGoldenPath(): IO[Unit] = {

    // Define typed source and sink using available factory methods
    val dataSource = TypedSource[SalesRecord](
      DataSource.gcs("demo", "input", DataFormat.Parquet),
    )

    val dataSink = TypedSink[SalesRecord](
      DataSink.gcs("demo", "output", DataFormat.Parquet),
    )

    // Create OpenLineage emitter (noop by default per plan)
    val lineageEmitter = OpenLineageEmitter.noop[IO]

    // Build the golden path pipeline with all FlowForge features
    val pipeline = PipelineBuilder[IO]("simple-golden-path")
      .withDescription("Golden path: Contract → Builder → Lineage")
      .withLineageEmitter(lineageEmitter)
      .addTypedSource[SalesRecord, SalesRecord, SchemaPolicy.Exact](
        dataSource,
        _ => IO.pure(SalesRecord(1L, "CUSTOMER001", 100.0, "NORTH")),
      )
      .addTransform[SalesRecord] { record =>
        // Simple data transformation
        IO.pure(
          record.copy(
            region = record.region.toUpperCase,
            amount = BigDecimal(record.amount).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble,
          ),
        )
      }
      .addTypedSink[SalesRecord, SchemaPolicy.Exact](
        dataSink,
        (record, _) =>
          IO {
            println(s"✅ Would write record: $record")
            println("✅ Contract validation passed at compile time")
          },
      )
      .build()

    println("✅ Pipeline built with compile-time contract validation")
    println("✅ OpenLineage emitter configured (noop mode)")

    // Execute the pipeline - this will emit START/COMPLETE lineage events
    pipeline.executeWithMonitoring(()).flatMap { result =>
      IO {
        println(s"✅ Pipeline executed: ${result.status}")
        println(s"   Duration: ${result.duration}")
        println(s"   Pipeline ID: ${result.pipelineId}")
        if (result.errors.nonEmpty) {
          println(s"   Errors: ${result.errors}")
        }
      }
    }
  }
}
