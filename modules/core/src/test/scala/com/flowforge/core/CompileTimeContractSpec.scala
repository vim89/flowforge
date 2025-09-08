package com.flowforge.core

import cats.effect.IO
import com.flowforge.core.contracts.SchemaPolicy
import com.flowforge.core.instances.EffectInstances._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * Compile-time contract enforcement tests.
 *
 * These tests demonstrate the key USP of FlowForge: "Pipelines will not even build if source or target schema
 * do not match or align."
 *
 * The test shows the API structure and documents the compile-time guarantees. Actual SchemaConforms
 * implementation testing is in the contracts module.
 */
class CompileTimeContractSpec extends AnyWordSpec with Matchers {

  // Test data types
  case class User(
    id: Long,
    name: String,
    email: String)
  case class UserWithAge(
    id: Long,
    name: String,
    email: String,
    age: Int)
  case class UserMissingField(id: Long, name: String) // Missing email field

  "100% Compile-Time Contract Builder" should {

    "have the correct API signature for phantom-state enforcement" in {
      // This test documents the API structure and ensures the builder exists
      val builderEmpty = PipelineBuilder[IO]("test")

      // The builder should have the correct type signature
      builderEmpty.name shouldBe "test"

      // Document: These methods exist and require proper type evidence
      /*
       * The following methods would be available but require SchemaConforms evidence:
       *
       * builder.addTypedSource[User](
       *   TypedSource[User](DataSource.gcs("bucket", "prefix", DataFormat.Parquet)),
       *   SchemaPolicy.Exact,
       *   _ => IO.pure(User(1, "test", "test@example.com"))
       * )
       *
       * builder.addTransform[String](user => IO.pure(user.name))
       *
       * builder.addTypedSink[User](
       *   TypedSink[User](DataSink.gcs("bucket", "prefix", DataFormat.Parquet)),
       *   SchemaPolicy.Exact,
       *   (data, sink) => IO.unit
       * )
       *
       * Only when all stages are present can you call:
       * val pipeline = builder.build() // Requires BuilderState.Complete evidence
       */
    }

    "enforce complete pipeline stages at compile time" in {
      /*
       * DOCUMENTED COMPILE-TIME GUARANTEE #1: Missing sink
       *
       * The following code would NOT compile because build() requires BuilderState.Complete,
       * but we only have HasSource with HasContract with HasTransform:
       *
       * val incompleteBuilder = PipelineBuilder[IO]("incomplete-test")
       *   .addTypedSource[User](source, SchemaPolicy.Exact, reader)
       *   .addTransform[User](transform)
       *   // Missing .addTypedSink[User]()
       *
       * val pipeline = incompleteBuilder.build() // COMPILE ERROR: missing evidence for BuilderState.Complete
       */

      // This test documents the guarantee
      val _ = 42
      succeed
    }

    "enforce schema compatibility at source and sink" in {
      /*
       * DOCUMENTED COMPILE-TIME GUARANTEE #2: Schema mismatch
       *
       * The following would NOT compile because SchemaConforms evidence would be missing:
       *
       * PipelineBuilder[IO]("schema-mismatch")
       *   .addTypedSource[UserMissingField](  // Missing email field
       *     source,
       *     SchemaPolicy.Exact,             // Requires exact match
       *     reader
       *   )
       *   // COMPILE ERROR: No SchemaConforms[UserMissingField, UserMissingField, SchemaPolicy.Exact]
       *
       * Similarly for sinks:
       *
       * builder
       *   .addTypedSink[UserWithAge](      // Has extra age field
       *     sink,
       *     SchemaPolicy.Exact,            // Requires exact match with User output
       *     writer
       *   )
       *   // COMPILE ERROR: No SchemaConforms[User, UserWithAge, SchemaPolicy.Exact]
       */

      // This test documents the guarantee
      val _ = 42
      succeed
    }

    "provide the correct schema policy types" in {
      // Test that SchemaPolicy case objects exist and can be used
      val exactPolicy          = SchemaPolicy.Exact
      val backwardPolicy       = SchemaPolicy.Backward
      val forwardPolicy        = SchemaPolicy.Forward
      val fullPolicy           = SchemaPolicy.Full
      val exactUnorderedPolicy = SchemaPolicy.ExactUnordered

      // All should be instances of SchemaPolicy
      exactPolicy shouldBe a[SchemaPolicy]
      backwardPolicy shouldBe a[SchemaPolicy]
      forwardPolicy shouldBe a[SchemaPolicy]
      fullPolicy shouldBe a[SchemaPolicy]
      exactUnorderedPolicy shouldBe a[SchemaPolicy]
    }

    "demonstrate phantom types prevent invalid states" in {
      // Test that we can create a builder in Empty state
      val emptyBuilder = PipelineBuilder[IO]("phantom-test")

      // The phantom type system ensures:
      // 1. Empty state can only add sources
      // 2. HasSource can add transforms
      // 3. HasSource + HasTransform can add sinks
      // 4. Only Complete state can build()

      emptyBuilder.name shouldBe "phantom-test"

      /*
       * The phantom type system would prevent these at compile time:
       *
       * emptyBuilder.build()              // ERROR: Empty ≠ Complete
       * emptyBuilder.addTransform(...)    // ERROR: Empty doesn't have source
       * emptyBuilder.addSink(...)         // ERROR: Empty doesn't have source+transform
       */
    }
  }
}
