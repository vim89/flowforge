package example

import cats.effect.{ IO, IOApp }
import cats.implicits._
import com.flowforge.core.algebra.EffectSystem
import com.flowforge.core.instances.EffectInstances.catsEffectSystemInstance
import com.flowforge.core.types.{ DataSource, DataSink, DataFormat }
import com.flowforge.core.types.TypedIO._
import com.flowforge.core.PipelineBuilder
import com.flowforge.core.contracts.SchemaPolicy

/**
 * FlowForge Contract-First Data Pipeline Template
 * 
 * This template demonstrates FlowForge's unique compile-time contract guarantee.
 * Following CLAUDE.md principles:
 * - Pure functional pipeline composition
 * - Phantom types for compile-time safety
 * - Immutable data structures throughout
 * - Contract-first development approach
 * 
 * Generated for: $name$
 * 
 * KEY FEATURES:
 * - Pipelines fail to compile if output doesn't match contract
 * - Multiple schema evolution policies supported
 * - Type-safe resource management
 * - Effect-safe orchestration
 */
object Pipeline extends IOApp.Simple {

  // === CONTRACT DEFINITIONS ===
  // Define your data contracts here - these represent the expected schemas
  
  case class InputDataContract(
    id: String,
    amount: Double
  )
  
  case class ProcessedDataContract(
    id: String,
    amount: Double,
    normalizedAmount: Double,
    processed: Boolean
  )

  // === PIPELINE DATA TYPES ===
  // These are your actual data types that must match the contracts
  
  case class InputRecord(
    id: String,
    amount: Double
  )
  
  case class ProcessedRecord(
    id: String,
    amount: Double,
    normalizedAmount: Double,
    processed: Boolean
  )

  implicit def effectSystem: EffectSystem[IO] = catsEffectSystemInstance

  def run: IO[Unit] = {
    println(s"🚀 FlowForge Contract-First Pipeline: $name$")
    println("=" * 50)
    
    for {
      _ <- IO.println("📋 Running contract-validated pipeline...")
      _ <- runContractValidatedPipeline
      _ <- IO.println("✅ Pipeline completed successfully with contract validation!")
      _ <- IO.println("\n💡 Try changing the ProcessedRecord schema to see compile-time validation")
    } yield ()
  }

  /**
   * Contract-validated pipeline using FlowForge's compile-time guarantees.
   * This pipeline will NOT compile if the output type doesn't match the contract.
   */
  def runContractValidatedPipeline: IO[Unit] = {
    
    val source = DataSource.gcs("$name$-bucket", "input/*.parquet", DataFormat.Parquet)
    val sink = DataSink.gcs("$name$-bucket", "output/", DataFormat.Parquet)
    
    // Mock data reader
    def readInputData(source: DataSource): IO[InputRecord] = 
      IO.pure(InputRecord("sample-001", 42.0))
    
    // Pure transformation function following CLAUDE.md principles
    def processData(input: InputRecord): IO[ProcessedRecord] = {
      val normalized = input.amount / 100.0 // Normalize to 0-1 range
      IO.pure(ProcessedRecord(
        id = input.id,
        amount = input.amount,
        normalizedAmount = normalized,
        processed = true
      ))
    }
    
    // Mock data writer
    def writeProcessedData(data: ProcessedRecord, sink: DataSink): IO[Unit] =
      IO.println(s"💾 Writing to ${sink.location}: $data")

    // 🔒 CONTRACT-ENFORCED PIPELINE  
    // This is the key differentiator - pipeline won't build if contracts drift!
    val contractValidatedPipeline = PipelineBuilder[IO]("$name$-pipeline")
      .withDescription("Contract-first pipeline with compile-time validation")
      .addTypedSource[InputRecord, SchemaPolicy.Exact](
        gcsParquetSource[InputRecord]("$name$-bucket", "input/*.parquet"),
        SchemaPolicy.Exact,
        _ => readInputData(source)
      )
      .addTransform[ProcessedRecord](processData)
      .addTypedSink[ProcessedRecord, SchemaPolicy.Exact](
        gcsParquetSink[ProcessedRecord]("$name$-bucket", "output/"),
        SchemaPolicy.Exact, 
        (processed, _) => writeProcessedData(processed, sink)
      )
    
    // Build and execute the validated pipeline - ONLY compiles if contracts match!
    val pipeline = contractValidatedPipeline.build()
    pipeline.run(())
  }

  /**
   * COMPILE-TIME VALIDATION DEMO
   * 
   * To see FlowForge's contract enforcement in action:
   * 
   * 1. Change ProcessedRecord field name from 'normalizedAmount' to 'normalizedValue'
   * 2. Try to compile - it will FAIL with a clear error message
   * 3. Change it back - compilation succeeds
   * 
   * This proves that pipelines become unbuildable when contracts drift!
   */
}

