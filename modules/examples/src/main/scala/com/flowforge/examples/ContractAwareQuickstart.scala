package com.flowforge.examples

import cats.effect.{ IO, IOApp }
import cats.implicits._
import com.flowforge.core.algebra.EffectSystem
import com.flowforge.core.instances.EffectInstances.catsEffectSystemInstance
import com.flowforge.core.types.{ DataFormat, DataSink, DataSource, PipelineBuilder2 }
import com.flowforge.core.types.{ SchemaEvolutionPolicy, SchemaWitness }

/**
 * CONTRACT-AWARE QUICKSTART DEMO
 *
 * This example demonstrates FlowForge's unique USP: compile-time contract enforcement that makes pipelines
 * unbuildable when schema drift occurs.
 *
 * Following CLAUDE.md principles:
 *   - Pure functional pipeline composition using Kleisli arrows
 *   - Phantom types for compile-time safety without runtime overhead
 *   - Immutable data structures throughout the pipeline
 *   - Type-safe resource management with automatic cleanup
 *   - First-class functions for transformation composition
 *
 * KEY DEMONSTRATION:
 *   1. Pipeline compiles when output matches contract
 *   2. Pipeline fails to compile when schema drifts
 *   3. One-line fix restores compilation
 *
 * This is the "tiny repo with screenshots" proof from brutal-truth-2025-09-06.md
 */
object ContractAwareQuickstart extends IOApp.Simple {

  // === CONTRACT DEFINITIONS ===
  // These represent the expected schemas that pipelines must comply with

  case class SalesDataContract(
    transactionId: String,
    customerId: String,
    amount: BigDecimal,
    currency: String,
    timestamp: Long)

  case class ProcessedSalesContract(
    transactionId: String,
    customerId: String,
    normalizedAmount: Double,
    currency: String,
    processed: Boolean)

  // === PIPELINE DATA TYPES ===
  // These represent the actual data structures our pipeline produces

  case class RawSalesRecord(
    transactionId: String,
    customerId: String,
    amount: BigDecimal,
    currency: String,
    timestamp: Long)

  // ✅ MATCHING SCHEMA - This will compile successfully
  case class ProcessedSalesRecord(
    transactionId: String,
    customerId: String,
    normalizedAmount: Double,
    currency: String,
    processed: Boolean)

  // ❌ DRIFTED SCHEMA - This would cause compilation failure
  // Uncomment to see the compile error:
  /*
  case class ProcessedSalesRecordDrifted(
    transactionId: String,
    customerId: String,
    normalizedValue: Double,  // ❌ Field name drift: 'normalizedValue' vs 'normalizedAmount'
    currency: String,
    processed: Boolean
  )
   */

  def run: IO[Unit] = {
    println("🚀 FlowForge Contract-Aware Quickstart Demo")
    println("=" * 60)

    for {
      _ <- IO.println("\n📋 CONTRACT ENFORCEMENT DEMONSTRATION")
      _ <- demonstrateSuccessfulContractValidation
      _ <- IO.println("\n📋 SCHEMA EVOLUTION POLICY EXAMPLES")
      _ <- demonstrateSchemaEvolutionPolicies
      _ <- IO.println("\n🎯 Contract-First Development Complete!")
      _ <- IO.println("\n💡 Key Takeaway: Pipelines become unbuildable when contracts drift.")
      _ <- IO.println("   This is FlowForge's unique compile-time guarantee!")
    } yield ()
  }

  /**
   * Demonstrates successful pipeline compilation when schemas match contracts exactly. This is the "happy
   * path" that proves our system works correctly.
   */
  def demonstrateSuccessfulContractValidation: IO[Unit] = for {
    _ <- IO.println("✅ Building pipeline with matching contract schema...")

    source = DataSource.gcs("demo-bucket", "sales/raw/*.parquet", DataFormat.Parquet)
    sink   = DataSink.gcs("demo-bucket", "sales/processed/", DataFormat.Parquet)

    // Mock readers/writers for demonstration
    readSales = (source: DataSource) =>
      IO.pure(RawSalesRecord("tx-001", "cust-001", BigDecimal("99.99"), "USD", System.currentTimeMillis()))

    processSales = (raw: RawSalesRecord) =>
      IO.pure(
        ProcessedSalesRecord(
          transactionId = raw.transactionId,
          customerId = raw.customerId,
          normalizedAmount = raw.amount.toDouble / 100.0, // Normalize to 0-1 range example
          currency = raw.currency,
          processed = true,
        ),
      )

    writeSales = (processed: ProcessedSalesRecord, sink: DataSink) =>
      IO.println(s"💾 Writing to sink: $processed")

    // 🎯 THIS IS THE KEY: Pipeline with contract enforcement
    contractValidatedPipeline = PipelineBuilder2[IO]("sales-processing")
      .withDescription("Sales data processing with contract validation")
      .addTransform[RawSalesRecord](_ => readSales(source))
      .addTransform[ProcessedSalesRecord](processSales)
      // 🔒 CONTRACT ENFORCEMENT: This requires SchemaWitness evidence
      .buildWithExactContract[ProcessedSalesContract] // ✅ This compiles because schemas match!

    result <- contractValidatedPipeline.execute(())
    _      <- writeSales(result, sink)
    _      <- IO.println("✅ Pipeline built and executed successfully - contracts match!")
  } yield ()

  /**
   * Demonstrates different schema evolution policies and their compile-time behavior. Shows how different
   * compatibility modes allow or prevent compilation.
   */
  def demonstrateSchemaEvolutionPolicies: IO[Unit] = {

    // Extended pipeline output with extra fields
    case class ExtendedProcessedSalesRecord(
      transactionId: String,
      customerId: String,
      normalizedAmount: Double,
      currency: String,
      processed: Boolean,
      // Extra fields for backward compatibility demo
      processingTimestamp: Long = System.currentTimeMillis(),
      batchId: String = "batch-001")

    // Minimal pipeline output with fewer fields
    case class MinimalProcessedSalesRecord(
      transactionId: String,
      customerId: String,
      normalizedAmount: Double)

    for {
      _ <- IO.println("🔄 Testing Backward Compatible Policy (pipeline has extra fields)...")
      // This should compile - pipeline output is superset of contract
      backwardCompatiblePipeline = PipelineBuilder2[IO]("backward-compatible-demo")
        .addTransform[ExtendedProcessedSalesRecord](_ =>
          IO.pure(ExtendedProcessedSalesRecord("tx-002", "cust-002", 0.5, "USD", true)),
        )
        .buildWithBackwardCompatibleContract[ProcessedSalesContract]

      result1 <- backwardCompatiblePipeline.execute(())
      _       <- IO.println("✅ Backward compatible pipeline compiled successfully!")

      _ <- IO.println("🔄 Testing Forward Compatible Policy (contract has extra fields)...")
      // This should compile - pipeline output is subset of contract
      forwardCompatiblePipeline = PipelineBuilder2[IO]("forward-compatible-demo")
        .addTransform[MinimalProcessedSalesRecord](_ =>
          IO.pure(MinimalProcessedSalesRecord("tx-003", "cust-003", 0.75)),
        )
        .buildWithContract[ProcessedSalesContract, SchemaEvolutionPolicy.ForwardCompatible]

      result2 <- forwardCompatiblePipeline.execute(())
      _       <- IO.println("✅ Forward compatible pipeline compiled successfully!")

    } yield ()
  }
}

/**
 * COMPILE FAILURE DEMONSTRATION
 *
 * To see FlowForge's contract enforcement in action, uncomment the sections below. These will cause
 * compilation to fail with clear error messages.
 */
object ContractDriftDemo {

  /*
   * ❌ UNCOMMENT TO SEE COMPILE FAILURE:
   *
   * This demonstrates what happens when pipeline output doesn't match the contract.
   * The compilation will fail with a detailed error message pointing to the mismatch.
   */

  /*
  case class DriftedPipelineOutput(
    transactionId: String,
    customerId: String,
    normalizedValue: Double,  // ❌ Wrong field name - should be 'normalizedAmount'
    currency: String,
    processed: Boolean
  )

  def demonstrateCompileFailure: IO[Unit] = {
    val failingPipeline = PipelineBuilder2[IO]("contract-drift-demo")
      .addTransform[DriftedPipelineOutput](_ =>
        IO.pure(DriftedPipelineOutput("tx-001", "cust-001", 0.5, "USD", true)))
      // ❌ THIS WILL NOT COMPILE:
      .buildWithExactContract[ContractAwareQuickstart.ProcessedSalesContract]

    // ERROR MESSAGE WILL BE:
    // ╔══════════════════════════════════════════════════════════════════════════════╗
    // ║                     🚨 FlowForge Contract Drift Detected! 🚨                ║
    // ║                                                                              ║
    // ║  Pipeline output type 'DriftedPipelineOutput' does not match contract       ║
    // ║  'ProcessedSalesContract' under evolution policy 'Exact'.                   ║
    // ║                                                                              ║
    // ║  ❌ This pipeline CANNOT be built due to schema incompatibility.             ║
    // ║                                                                              ║
    // ║  🔧 Common fixes:                                                            ║
    // ║    1. Update case class fields to match contract schema                      ║
    // ║    2. Use BackwardCompatible policy if adding fields is intentional          ║
    // ║    3. Update the contract if schema changes are correct                      ║
    // ║                                                                              ║
    // ║  📖 See: docs/contracts/SCHEMA_EVOLUTION.md                                  ║
    // ╚══════════════════════════════════════════════════════════════════════════════╝

    failingPipeline.run(())
  }

  // 🔧 THE FIX - Change field name to match contract:
  case class FixedPipelineOutput(
    transactionId: String,
    customerId: String,
    normalizedAmount: Double,  // ✅ Correct field name matches contract
    currency: String,
    processed: Boolean
  )

  def demonstrateCompileFix: IO[Unit] = {
    val fixedPipeline = PipelineBuilder2[IO]("fixed-contract-demo")
      .addTransform[FixedPipelineOutput](_ =>
        IO.pure(FixedPipelineOutput("tx-001", "cust-001", 0.5, "USD", true)))
      .buildWithExactContract[ContractAwareQuickstart.ProcessedSalesContract] // ✅ Now compiles!

    fixedPipeline.run(())
  }
   */
}
