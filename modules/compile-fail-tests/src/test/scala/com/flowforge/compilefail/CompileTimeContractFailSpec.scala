// scalafix:off DisableSyntax.var DisableSyntax.throw DisableSyntax.null DisableSyntax.noUnsafeRunSync
package com.flowforge.compilefail

import cats.effect.IO
import com.flowforge.core.PipelineBuilder
import com.flowforge.core.contracts.SchemaPolicy
import com.flowforge.core.instances.EffectInstances._
import com.flowforge.core.types.TypedIO._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * Compile-fail tests: 3 required tests
 *
 * These tests MUST NOT COMPILE to prove that flowForge achieves: "Data pipelines won't even compile if
 * contracts/schema of source or target do not match or align!"
 *
 * Per specification requirements:
 *   1. Missing sink - pipeline cannot be built without all stages
 *   2. Schema mismatch - source/sink schema doesn't conform under policy
 *   3. Illegal evolution - schema evolution violates policy constraints
 *
 * To verify compile failures:
 *   1. Run `sbt compile-fail-tests/test:compile`
 *   2. Observe compilation failure with helpful error message
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

    "FAIL TEST #1: Missing Sink - Pipeline cannot be built without all required stages" in {
      // SPECIFICATION REQUIREMENT: "Ship 3 compile-fail tests: missing sink"
      //
      // This test demonstrates that incomplete pipelines are literally unbuildable.
      // The phantom type system prevents build() unless all stages are present.
      //
      // EXPECTED ERROR: Cannot prove that (HasSource with HasContract with HasTransform) <:< Complete
      val _ = 42 // Prevent formatter from removing braces

      assertTypeError("""
        val incompleteBuilder = PipelineBuilder[IO]("incomplete-pipeline")
          .addTypedSource[User, User, SchemaPolicy.Exact](
            gcsParquetSource[User]("bucket", "users/*.parquet"),
            _ => IO.pure(User(1, "John", "john@example.com"))
          )
          .addTransform[User](user => IO.pure(user.copy(name = user.name.toUpperCase)))
          // Missing: .addTypedSink[User, SchemaPolicy.Exact](<code-here>)

        // This line MUST NOT COMPILE - build() requires BuilderState.Complete evidence:
        val pipeline = incompleteBuilder.build()
      """)
    }

    "FAIL TEST #2: Schema Mismatch - Source schema doesn't conform to expected type under Exact policy" in {
      // SPECIFICATION REQUIREMENT: "Ship 3 compile-fail tests: schema mismatch"
      //
      // This test demonstrates that schema mismatches are caught at compile time.
      // UserMissingEmail lacks the 'email' field required by User under Exact policy.
      //
      // EXPECTED ERROR: No SchemaConforms[UserMissingEmail, User, SchemaPolicy.Exact] found
      val _ = 42 // Prevent formatter from removing braces

      assertTypeError("""
        val schemaMismatchBuilder = PipelineBuilder[IO]("schema-mismatch-pipeline")
          .addTypedSource[UserMissingEmail, User, SchemaPolicy.Exact](
            gcsParquetSource[User]("bucket", "complete-users/*.parquet"),
            _ => IO.pure(UserMissingEmail(1, "John"))
          )
        implicitly[com.flowforge.core.contracts.SchemaConforms[UserMissingEmail, User, SchemaPolicy.Exact]]
      """)
    }

    "FAIL TEST #3: Illegal Evolution - Schema evolution violates Exact policy constraints" in {
      // SPECIFICATION REQUIREMENT: "Ship 3 compile-fail tests: illegal evolution"
      //
      val _ = 42 // Prevent formatter from removing braces
      // This test demonstrates that illegal schema evolution is prevented at compile time.
      // Trying to write User data to a UserWithAge sink under Exact policy fails because
      // the output type doesn't have the required 'age' field.
      //
      // EXPECTED ERROR: No SchemaConforms[User, UserWithAge, SchemaPolicy.Exact] found

      assertTypeError("""
        val evolutionViolationBuilder = PipelineBuilder[IO]("illegal-evolution-pipeline")
          .addTypedSource[User, User, SchemaPolicy.Exact](
            gcsParquetSource[User]("bucket", "users/*.parquet"),
            _ => IO.pure(User(1, "John", "john@example.com"))
          )
          .addTransform[User](user => IO.pure(user))
          .addTypedSink[UserWithAge, SchemaPolicy.Exact](
            gcsParquetSink[UserWithAge]("bucket", "users-with-age/"),
            (_, _) => IO.unit
          )

        val pipeline = evolutionViolationBuilder.build()
      """)
    }

    "demonstrate that valid schemas DO compile correctly" in {
      // This test SHOULD compile to prove the system works for valid cases
      val validBuilder = PipelineBuilder[IO]("valid-pipeline")
        .addTypedSource[User, User, SchemaPolicy.Exact](
          gcsParquetSource[User]("bucket", "users/*.parquet"),
          _ => IO.pure(User(1, "John", "john@example.com")),
        )
        .addTransform[User](user => IO.pure(user.copy(name = user.name.toUpperCase)))
        .addTypedSink[User, SchemaPolicy.Exact](
          gcsParquetSink[User]("bucket", "processed-users/"),
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
        .addTypedSource[UserWithAge, User, SchemaPolicy.Backward]( // Extra 'age' field allowed
          gcsParquetSource[User]("bucket", "users/*.parquet"),
          _ => IO.pure(UserWithAge(1, "John", "john@example.com", 25)),
        )
        .addTransform[UserWithAge](user => IO.pure(user))
        .addTypedSink[UserWithAge, SchemaPolicy.Exact](
          gcsParquetSink[UserWithAge]("bucket", "processed/"),
          (_, _) => IO.unit,
        )

      // This SHOULD compile successfully - backward compatibility works:
      val pipeline = backwardCompatBuilder.build()

      pipeline.name shouldBe "backward-compat-pipeline"
    }
  }

  /**
   * trait Expr case class Num(value: Double) extends Expr case class Sum(lhs: Expr, rhs: Expr) extends Expr
   * case class Sub(lhs: Expr, rhs: Expr) extends Expr case class Mul(lhs: Expr, rhs: Expr) extends Expr case
   * class Div(lhs: Expr, rhs: Expr) extends Expr case class Sin(expr: Expr) extends Expr case class Cos(expr:
   * Expr) extends Expr // ... and everything else
   *
   * val computation = Sum( Num(2), Sum( Div(Num(3), Num(4)), Mul( Num(2), Mul( Num(8), Sin(Num(30)) ) ) ) )
   */

}
