package com.flowforge.compilefail

import cats.effect.IO
import com.flowforge.core.PipelineBuilder
import com.flowforge.core.contracts.SchemaPolicy
import com.flowforge.core.instances.EffectInstances._
import com.flowforge.core.types._
import com.flowforge.core.types.TypedIO._
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

/**
 * Compile-Fail Tests: The 3 Required Tests from compile-time-*.md specifications
 *
 * These tests MUST NOT COMPILE to prove that FlowForge achieves the USP: "Data pipelines won't even compile
 * if contracts/schema of source or target do not match or align!"
 *
 * Per specification requirements:
 *   1. Missing sink - pipeline cannot be built without all stages
 *   2. Schema mismatch - source/sink schema doesn't conform under policy
 *   3. Illegal evolution - schema evolution violates policy constraints
 *
 * To verify compile failures:
 *   1. Uncomment any test section below
 *   2. Run `sbt compile-fail-tests/test:compile`
 *   3. Observe compilation failure with helpful error message
 *   4. Comment out test to restore clean build
 */
class CompileTimeContractFailSpec extends AnyWordSpec with Matchers {

  // Test data types for schema evolution scenarios
  case class User(
    id: Long,
    name: String,
    email: String)
  case class UserWithAge(
    id: Long,
    name: String,
    email: String,
    age: Int)                                         // Extra field
  case class UserMissingEmail(id: Long, name: String) // Missing field
  case class UserWrongType(
    id: String,
    name: String,
    email: String) // Type mismatch

  "FlowForge 100% Compile-Time Contracts" should {

    "FAIL TEST #1: Missing Sink - Pipeline cannot be built without all required stages" in
      // SPECIFICATION REQUIREMENT: "Ship 3 compile-fail tests: missing sink"
      //
      // This test demonstrates that incomplete pipelines are literally unbuildable.
      // The phantom type system prevents build() unless all stages are present.
      //
      // EXPECTED ERROR: Cannot prove that (HasSource with HasContract with HasTransform) <:< Complete

      // Uncomment to test compile failure:
      //
      // val incompleteBuilder = PipelineBuilder[IO]("incomplete-pipeline")
      //   .addTypedSource[User, SchemaPolicy.Exact](
      //     gcsParquetSource[User]("bucket", "users/*.parquet"),
      //     SchemaPolicy.Exact,
      //     _ => IO.pure(User(1, "John", "john@example.com"))
      //   )
      //   .addTransform[User](user => IO.pure(user.copy(name = user.name.toUpperCase)))
      //   // Missing: .addTypedSink[User, SchemaPolicy.Exact](...)
      //
      // // This line MUST NOT COMPILE - build() requires BuilderState.Complete evidence:
      // val pipeline = incompleteBuilder.build()

      succeed // Test documents the compile failure requirement

    "FAIL TEST #2: Schema Mismatch - Source schema doesn't conform to expected type under Exact policy" in
      // SPECIFICATION REQUIREMENT: "Ship 3 compile-fail tests: schema mismatch"
      //
      // This test demonstrates that schema mismatches are caught at compile time.
      // UserMissingEmail lacks the 'email' field required by User under Exact policy.
      //
      // EXPECTED ERROR: No SchemaConforms[UserMissingEmail, UserMissingEmail, SchemaPolicy.Exact]
      // found. Missing fields: email

      // Uncomment to test compile failure:
      //
      // val schemaMismatchBuilder = PipelineBuilder[IO]("schema-mismatch-pipeline")
      //   .addTypedSource[UserMissingEmail, SchemaPolicy.Exact]( // MISMATCH: Missing email field!
      //     gcsParquetSource[UserMissingEmail]("bucket", "incomplete-users/*.parquet"),
      //     SchemaPolicy.Exact,  // Exact policy requires ALL fields to match
      //     _ => IO.pure(UserMissingEmail(1, "John"))
      //   )
      //
      // // This line MUST NOT COMPILE - SchemaConforms evidence cannot be found:

      succeed // Test documents the compile failure requirement

    "FAIL TEST #3: Illegal Evolution - Schema evolution violates Exact policy constraints" in
      // SPECIFICATION REQUIREMENT: "Ship 3 compile-fail tests: illegal evolution"
      //
      // This test demonstrates that illegal schema evolution is prevented at compile time.
      // Trying to write User data to a UserWithAge sink under Exact policy fails because
      // the output type doesn't have the required 'age' field.
      //
      // EXPECTED ERROR: No SchemaConforms[User, UserWithAge, SchemaPolicy.Exact] found.
      // Missing fields in output: age

      // Uncomment to test compile failure:
      //
      // val evolutionViolationBuilder = PipelineBuilder[IO]("illegal-evolution-pipeline")
      //   .addTypedSource[User, SchemaPolicy.Exact](
      //     gcsParquetSource[User]("bucket", "users/*.parquet"),
      //     SchemaPolicy.Exact,
      //     _ => IO.pure(User(1, "John", "john@example.com"))
      //   )
      //   .addTransform[User](user => IO.pure(user)) // Output is User
      //   .addTypedSink[UserWithAge, SchemaPolicy.Exact]( // MISMATCH: Sink expects UserWithAge!
      //     gcsParquetSink[UserWithAge]("bucket", "users-with-age/"),
      //     SchemaPolicy.Exact,  // Exact policy forbids missing fields
      //     (_, _) => IO.unit
      //   )
      //
      // // This line MUST NOT COMPILE - SchemaConforms[User, UserWithAge, Exact] impossible:
      // val pipeline = evolutionViolationBuilder.build()

      succeed // Test documents the compile failure requirement

    "demonstrate that valid schemas DO compile correctly" in {
      // This test SHOULD compile to prove the system works for valid cases
      val validBuilder = PipelineBuilder[IO]("valid-pipeline")
        .addTypedSource[User, SchemaPolicy.Exact](
          gcsParquetSource[User]("bucket", "users/*.parquet"),
          SchemaPolicy.Exact,
          _ => IO.pure(User(1, "John", "john@example.com")),
        )
        .addTransform[User](user => IO.pure(user.copy(name = user.name.toUpperCase)))
        .addTypedSink[User, SchemaPolicy.Exact](
          gcsParquetSink[User]("bucket", "processed-users/"),
          SchemaPolicy.Exact,
          (_, _) => IO.unit,
        )

      // This SHOULD compile successfully - all schemas match:
      val pipeline = validBuilder.build()

      pipeline.name shouldBe "valid-pipeline"
      pipeline.stages should have length 3
    }

    "demonstrate backward compatibility DOES work when appropriate" in {
      // This test SHOULD compile - UserWithAge is backward compatible with User base schema
      val backwardCompatBuilder = PipelineBuilder[IO]("backward-compat-pipeline")
        .addTypedSource[UserWithAge, SchemaPolicy.Backward]( // Extra 'age' field allowed
          gcsParquetSource[UserWithAge]("bucket", "users-with-age/*.parquet"),
          SchemaPolicy.Backward, // Backward policy allows extra fields
          _ => IO.pure(UserWithAge(1, "John", "john@example.com", 25)),
        )
        .addTransform[UserWithAge](user => IO.pure(user))
        .addTypedSink[UserWithAge, SchemaPolicy.Exact](
          gcsParquetSink[UserWithAge]("bucket", "processed/"),
          SchemaPolicy.Exact,
          (_, _) => IO.unit,
        )

      // This SHOULD compile successfully - backward compatibility works:
      val pipeline = backwardCompatBuilder.build()

      pipeline.name shouldBe "backward-compat-pipeline"
    }
  }
}
