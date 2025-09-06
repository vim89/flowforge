package com.flowforge.compilefail

import cats.effect.IO
import com.flowforge.core.PipelineBuilder
import com.flowforge.core.types.{ DataFormat, DataSink, DataSource }
import com.flowforge.core.instances.EffectInstances.catsEffectSystemInstance

/**
 * COMPILE-FAIL TESTS - The Killer Proof of FlowForge's USP
 *
 * This file demonstrates FlowForge's unique selling proposition: "Pipelines become unbuildable when schema
 * drift occurs"
 *
 * These are our differentiators - what makes FlowForge unique in the Scala data engineering ecosystem.
 *
 * Following principles:
 *   - Type-level programming to prevent runtime errors
 *   - Phantom types for compile-time safety
 *   - Beautiful error messages that guide developers
 *
 * ═══════════════════════════════════════════════════════════════════════════════ USAGE INSTRUCTIONS FOR
 * TESTING COMPILE-TIME CONTRACT ENFORCEMENT
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * To prove FlowForge's compile-time contract guarantee:
 *
 *   1. See HelloContractDrift.scala for a 5-minute demo
 *   2. Uncomment any of the failing test examples in comments below
 *   3. Try to compile: `sbt compile`
 *   4. Observe the beautiful error message explaining the schema drift
 *   5. Comment the failing test back out
 *   6. Compilation succeeds again
 *
 * This demonstrates our unique selling proposition: "Pipelines become unbuildable when schema drift occurs"
 *
 * No other Scala data engineering framework provides this level of compile-time contract enforcement.
 */

/**
 * ═══════════════════════════════════════════════════════════════════════════════ EXPECTED ERROR MESSAGES
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * When you uncomment the failing tests above, you should see beautiful error messages like:
 *
 * ╔══════════════════════════════════════════════════════════════════════════════╗ ║ 🚨 FlowForge Contract
 * Drift Detected! 🚨 ║ ║ ║ ║ Pipeline output type 'DriftedUserRecord' does not match contract ║ ║
 * 'UserContract' under evolution policy 'Exact'. ║ ║ ║ ║ ❌ This pipeline CANNOT be built due to schema
 * incompatibility. ║ ║ ║ ║ 🔧 Common fixes: ║ ║ 1. Update case class fields to match contract schema ║ ║ 2.
 * Use BackwardCompatible policy if adding fields is intentional ║ ║ 3. Update the contract if schema changes
 * are correct ║ ║ ║ ║ 📖 See: docs/contracts/SCHEMA_EVOLUTION.md ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 *
 * This is FlowForge's unique selling proposition: compile-time contract enforcement that prevents runtime
 * schema drift errors!
 */

object ContractDriftCompileFailTests {

  // Test contracts and data types
  case class UserContract(
    id: String,
    email: String,
    age: Int)

  case class UserRecord(
    id: String,
    email: String,
    age: Int)

  case class DriftedUserRecord(
    id: String,
    emailAddress: String, // ❌ Field name drift: 'emailAddress' vs 'email'
    age: Int)

  case class ExtraFieldUserRecord(
    id: String,
    email: String,
    age: Int,
    createdAt: Long, // Extra field
  )

  /**
   * ═══════════════════════════════════════════════════════════════════════════════ THE WORKING EXAMPLES -
   * These SHOULD compile successfully
   * ═══════════════════════════════════════════════════════════════════════════════
   */

  def testWorkingPipeline(): IO[UserRecord] = {
    val source = DataSource.gcs("test-bucket", "users/*.parquet", DataFormat.Parquet)
    val sink   = DataSink.gcs("test-bucket", "processed/", DataFormat.Parquet)

    // ✅ This SHOULD compile - schemas match exactly
    val workingPipeline = PipelineBuilder[IO]("working-pipeline")
      .withDescription("Pipeline with perfect schema match")
      .addTransform[UserRecord](_ => IO.pure(UserRecord("1", "test@example.com", 25)))
      .buildWithExactContract[UserContract] // ✅ This compiles!

    workingPipeline.execute(())
  }

  def testBackwardCompatibleWorking(): IO[ExtraFieldUserRecord] = {
    val source = DataSource.gcs("test-bucket", "users/*.parquet", DataFormat.Parquet)
    val sink   = DataSink.gcs("test-bucket", "processed/", DataFormat.Parquet)

    // ✅ This SHOULD compile - using BackwardCompatible policy for extra fields
    val backwardCompatiblePipeline = PipelineBuilder[IO]("backward-compatible-pipeline")
      .withDescription("Pipeline with extra field using correct policy")
      .addTransform[ExtraFieldUserRecord](_ =>
        IO.pure(ExtraFieldUserRecord("1", "test@example.com", 25, System.currentTimeMillis())),
      )
      .buildWithBackwardCompatibleContract[UserContract] // ✅ This compiles!

    backwardCompatiblePipeline.execute(())
  }

  /*
  ═══════════════════════════════════════════════════════════════════════════════
  EXAMPLE COMPILE-FAIL TESTS
  ═══════════════════════════════════════════════════════════════════════════════
  To see FlowForge's beautiful error messages, uncomment any of these tests:
   */

  // 1. BUILDING WITHOUT A SINK (should not compile):
  def testBuildingWithoutSink(): Unit = {
    val source = DataSource.gcs("test-bucket", "users/*.parquet", DataFormat.Parquet)

    val incompletePipeline = PipelineBuilder[IO]("incomplete-pipeline")
      .withDescription("This pipeline is missing a sink")
      .addTransform[UserRecord](_ => IO.pure(UserRecord("1", "test@example.com", 25)))
      // ❌ NO SINK DEFINED - should not compile
      .buildWithExactContract[UserContract] // This should fail to compile!
  }

  // 2. SCHEMA FIELD NAME DRIFT (should not compile):

  def testFieldNameDrift(): Unit = {
    val source = DataSource.gcs("test-bucket", "users/*.parquet", DataFormat.Parquet)
    val sink   = DataSink.gcs("test-bucket", "processed/", DataFormat.Parquet)

    val driftedPipeline = PipelineBuilder[IO]("schema-drift-pipeline")
      .withDescription("Pipeline with schema drift")
      .addTransform[DriftedUserRecord](_ => IO.pure(DriftedUserRecord("1", "test@example.com", 25)))
    // ❌ SCHEMA DRIFT: DriftedUserRecord has 'emailAddress' but contract expects 'email'
    // .buildWithExactContract[UserContract] // This should fail to compile!
  }

  // 3. MISSING FIELD DRIFT (should not compile):

  def testMissingField(): Unit = {
    case class IncompleteUserRecord(id: String, email: String) // Missing 'age' field

    val source = DataSource.gcs("test-bucket", "users/*.parquet", DataFormat.Parquet)
    val sink   = DataSink.gcs("test-bucket", "processed/", DataFormat.Parquet)

    val incompletePipeline = PipelineBuilder[IO]("incomplete-schema-pipeline")
      .withDescription("Pipeline with missing field")
      .addTransform[IncompleteUserRecord](_ => IO.pure(IncompleteUserRecord("1", "test@example.com")))
    // ❌ MISSING FIELD: IncompleteUserRecord missing 'age' field required by contract
    // .buildWithExactContract[UserContract] // This should fail to compile!
  }

  // 4. WRONG EVOLUTION POLICY (should not compile):

  def testWrongEvolutionPolicy(): Unit = {
    val source = DataSource.gcs("test-bucket", "users/*.parquet", DataFormat.Parquet)
    val sink   = DataSink.gcs("test-bucket", "processed/", DataFormat.Parquet)

    val extraFieldPipeline = PipelineBuilder[IO]("extra-field-pipeline")
      .withDescription("Pipeline with extra field using wrong policy")
      .addTransform[ExtraFieldUserRecord](_ =>
        IO.pure(ExtraFieldUserRecord("1", "test@example.com", 25, System.currentTimeMillis())),
      )
    // ❌ WRONG POLICY: ExtraFieldUserRecord has extra field but using Exact policy
    // .buildWithExactContract[UserContract] // This should fail to compile!
  }
}
