/**
 * HelloContractDrift - The 5-Minute FlowForge Demo
 * 
 * This file demonstrates FlowForge's unique selling proposition in under 5 minutes:
 * "Pipelines become unbuildable when schema drift occurs"
 * 
 * Instructions:
 * 1. Try to compile this file as-is: `scala HelloContractDrift.scala`
 * 2. Observe the compilation failure with beautiful error message
 * 3. Fix the schema drift by changing 'emailAddress' to 'email'
 * 4. Compilation succeeds!
 * 
 * This proves that FlowForge catches schema drift at compile time,
 * not at runtime like other frameworks.
 */

import cats.effect.{ IO, IOApp }
import com.flowforge.core.types._
import com.flowforge.core.instances.EffectInstances.catsEffectSystemInstance

object HelloContractDrift extends IOApp.Simple {

  // === CONTRACT DEFINITION ===
  // This represents what our data should look like
  case class UserContract(
    id: String,
    email: String,      // ← Note: field is called 'email'
    age: Int
  )

  // === PIPELINE OUTPUT ===
  // This is what our pipeline actually produces
  case class UserRecord(
    id: String,
    email: String,         // ✅ FIXED: field is now called 'email' to match contract
    age: Int
  )

  def run: IO[Unit] = {
    println("🚀 FlowForge HelloContractDrift Demo")
    
    // Define our data source and sink
    val source = DataSource.gcs("demo-bucket", "users/*.parquet", DataFormat.Parquet)
    val sink = DataSink.gcs("demo-bucket", "processed/", DataFormat.Parquet)
    
    // Try to build a pipeline with schema drift
    // ❌ This will NOT compile because 'emailAddress' != 'email'
    val pipeline = PipelineBuilder2[IO]("hello-contract-drift")
      .withDescription("Demo pipeline showing contract drift detection")
      .addTransform[UserRecord](_ => 
        IO.pure(UserRecord("1", "test@example.com", 25)))
      .buildWithExactContract[UserContract]  // ❌ Compilation failure here!
      
    // If compilation succeeded, execute the pipeline and discard result
    pipeline.execute(()).void
  }
}

/*
 * EXPECTED ERROR MESSAGE:
 * 
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║                     🚨 FlowForge Contract Drift Detected! 🚨                ║
 * ║                                                                              ║
 * ║  Pipeline output type 'UserRecord' does not match contract 'UserContract'   ║
 * ║  under evolution policy 'Exact'.                                             ║
 * ║                                                                              ║
 * ║  ❌ This pipeline CANNOT be built due to schema incompatibility.             ║
 * ║                                                                              ║
 * ║  🔧 Common fixes:                                                            ║
 * ║    1. Change 'emailAddress' to 'email' in UserRecord                        ║
 * ║    2. Use BackwardCompatible policy if adding fields is intentional          ║
 * ║    3. Update the contract if schema changes are correct                      ║
 * ║                                                                              ║
 * ║  📖 See: docs/contracts/SCHEMA_EVOLUTION.md                                  ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 * 
 * THE FIX:
 * Change line 33 from:
 *   emailAddress: String,  // ❌ Wrong field name
 * to:
 *   email: String,         // ✅ Correct field name
 * 
 * Then recompile - it will succeed!
 */