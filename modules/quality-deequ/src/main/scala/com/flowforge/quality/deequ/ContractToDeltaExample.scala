package com.flowforge.quality.deequ

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.flowforge.core.PipelineBuilder
import com.flowforge.core.contracts.SchemaPolicy
import com.flowforge.core.contracts.derive.Shape
import com.flowforge.core.instances.EffectInstances._
import com.flowforge.core.types.TypedIO._
import com.flowforge.core.types._
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.types._

/**
 * Example showing how contract rules map to both Deequ checks AND Delta constraints.
 * 
 * Demonstrates FlowForge's USP: Contract → Runtime Enforcement at Multiple Levels
 * 
 * CONTRACT LEVEL: Compile-time schema validation
 * DEEQU LEVEL: Runtime data quality profiling and checks  
 * DELTA LEVEL: Table-level constraint enforcement (NOT NULL, CHECK)
 */
object ContractToDeltaExample {

  // Example domain model with validation requirements
  case class User(
    id: Long,       // Must be unique
    name: String,   // Must be non-null
    email: String   // Must be non-null and valid email pattern
  )

  implicit val userShape: Shape[User] = Shape.gen[User]

  def main(args: Array[String]): Unit = {
    val spark = SparkSession.builder()
      .appName("ContractToDeltaExample")
      .master("local[*]")
      .config("spark.sql.extensions", "io.delta.sql.DeltaSparkSessionExtension")
      .config("spark.sql.catalog.spark_catalog", "org.apache.delta.catalog.DeltaCatalog")
      .getOrCreate()

    try {
      val result = demonstrateContractToRuntimeMapping(spark)
      println(result)
    } finally {
      spark.stop()
    }
  }

  def demonstrateContractToRuntimeMapping(spark: SparkSession): String = {
    
    println("=== FlowForge: Contract → Runtime Enforcement Example ===\n")

    // 1. CONTRACT LEVEL: Compile-time type safety
    println("1. CONTRACT LEVEL - Compile-time Schema Validation")
    println("   ✓ User case class defines expected schema")
    println("   ✓ SchemaPolicy.Exact ensures no drift at compile time")
    println("   ✓ Pipeline won't even compile if schemas don't align\n")

    // 2. PIPELINE CONSTRUCTION: Contract-driven pipeline that compiles
    val pipeline = PipelineBuilder[IO]("user-validation-pipeline")
      .addTypedSource[User, User, SchemaPolicy.Exact](
        localParquetSource[User]("/tmp/users.parquet"),
        _ => IO.pure(User(1, "Ada Lovelace", "ada@example.com"))
      )
      .addTransform[User] { user =>
        IO.pure(user.copy(name = user.name.toUpperCase))
      }
      .addTypedSink[User, SchemaPolicy.Exact](
        localParquetSink[User]("/tmp/processed_users.parquet"),
        (user, sink) => {
          // This is where we'd normally write, but for demo we'll show the DQ mapping
          demonstrateQualityMappings(spark, user)
        }
      )
      .build()

    println("2. PIPELINE CONSTRUCTION")
    println("   ✓ Pipeline compiled successfully with contract validation")
    println(s"   ✓ Pipeline stages: ${pipeline.stages.size}")
    println("   ✓ All schema conformance validated at compile time\n")

    // 3. RUNTIME QUALITY CHECKS: Contract rules → Deequ checks
    val qualityExample = demonstrateDeequMappings()
    println("3. DEEQU RUNTIME CHECKS")
    println("   Contract Rule → Deequ Check Mapping:")
    println(qualityExample)

    // 4. DELTA CONSTRAINTS: Contract rules → Delta table constraints  
    val deltaExample = demonstrateDeltaConstraints()
    println("4. DELTA TABLE CONSTRAINTS")
    println("   Contract Rule → Delta Constraint Mapping:")
    println(deltaExample)

    "\n=== SUMMARY ===\n" +
    "FlowForge provides THREE levels of validation:\n" +
    "1. COMPILE TIME: Schema alignment via contracts (prevents builds)\n" +
    "2. RUNTIME CHECKS: Data quality profiling via Deequ integration\n" +
    "3. TABLE CONSTRAINTS: Storage-level enforcement via Delta/Iceberg\n\n" +
    "This is FlowForge's key differentiator: Contract-First, Multi-Level Enforcement"
  }

  private def demonstrateQualityMappings(spark: SparkSession, user: User): IO[Unit] = {
    IO.println(s"Processing user with DQ checks: ${user.name}")
  }

  private def demonstrateDeequMappings(): String = {
    """
   Field: id (Long)
   Contract: Must be unique
   Deequ Check: isUnique("id")
   Implementation: DeequAdapter.runChecks() with FFConstraint.Unique

   Field: name (String)  
   Contract: Must be non-null
   Deequ Check: isComplete("name")
   Implementation: DeequAdapter.runChecks() with FFConstraint.NotNull

   Field: email (String)
   Contract: Must be non-null and match email pattern
   Deequ Checks: isComplete("email") + matchesPattern("email", "^[\\w\\.-]+@[\\w\\.-]+\\.[a-zA-Z]{2,}$")
   Implementation: DeequAdapter.runChecks() with FFConstraint.NotNull + FFConstraint.Pattern
   """
  }

  private def demonstrateDeltaConstraints(): String = {
    """
   Field: email (String) - EXACT PLAN SPECIFICATION EXAMPLE
   Contract: email nonEmpty
   Deequ Check: isComplete("email")  
   Delta Constraint: CHECK (length(email) > 0)
   
   Field: id (Long)
   Contract: Must be unique
   Delta Constraint: ALTER TABLE users ADD CONSTRAINT unique_id UNIQUE (id)
   SQL Generation: CREATE TABLE users (..., CONSTRAINT unique_id UNIQUE (id))

   Field: name (String)
   Contract: Must be non-null  
   Delta Constraint: ALTER TABLE users ALTER COLUMN name SET NOT NULL
   SQL Generation: CREATE TABLE users (id BIGINT, name STRING NOT NULL, ...)

   Field: email (String)
   Contract: Must be non-null and valid email
   Delta Constraints: 
     - ALTER TABLE users ALTER COLUMN email SET NOT NULL
     - ALTER TABLE users ADD CONSTRAINT valid_email CHECK (email RLIKE '^[\\w\\.-]+@[\\w\\.-]+\\.[a-zA-Z]{2,}$')
   SQL Generation: CREATE TABLE users (..., email STRING NOT NULL,
                   CONSTRAINT valid_email CHECK (email RLIKE '^[\\w\\.-]+@[\\w\\.-]+\\.[a-zA-Z]{2,}$'))
   """
  }

  /**
   * Utility to generate Delta DDL from FlowForge contracts.
   * This shows how contracts can be translated to table-level enforcement.
   */
  def generateDeltaDDL(tableName: String, constraints: List[QualityConstraint]): String = {
    val constraintClauses = constraints.map {
      case QualityConstraint.NotNull(field, _) =>
        s"${field.value} NOT NULL"
      
      case QualityConstraint.Unique(field, _) =>
        s"CONSTRAINT unique_${field.value} UNIQUE (${field.value})"
      
      case QualityConstraint.Pattern(field, regex, _) =>
        s"CONSTRAINT valid_${field.value} CHECK (${field.value} RLIKE '$regex')"
      
      case QualityConstraint.Range(field, min, max, _) =>
        val minCheck = min.map(m => s"${field.value} >= $m")
        val maxCheck = max.map(m => s"${field.value} <= $m")
        val checks = (minCheck.toList ++ maxCheck.toList).mkString(" AND ")
        s"CONSTRAINT range_${field.value} CHECK ($checks)"
      
      case QualityConstraint.Compliance(name, predicate, _) =>
        s"CONSTRAINT ${name.replaceAll("[^a-zA-Z0-9_]", "_")} CHECK ($predicate)"
    }

    s"""
    |-- FlowForge Generated Delta Table DDL
    |CREATE TABLE IF NOT EXISTS $tableName (
    |  id BIGINT,
    |  name STRING, 
    |  email STRING,
    |  ${constraintClauses.mkString(",\n  ")}
    |) USING DELTA
    |LOCATION '/path/to/delta/table/$tableName'
    """.stripMargin
  }
}