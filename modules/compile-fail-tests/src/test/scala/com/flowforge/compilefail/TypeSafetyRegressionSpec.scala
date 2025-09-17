package com.flowforge.compilefail

import cats.data.Kleisli
import cats.effect.IO
import cats.implicits._
import com.flowforge.core.FlowForgePipeline
import com.flowforge.core.instances.EffectInstances._
import com.flowforge.core.syntax.PipelineSyntax._
import com.flowforge.core.types._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * Type Safety Regression Tests
 *
 * These tests ensure that the elimination of Any types in FlowForgePipeline provides compile-time safety
 * guarantees. These tests MUST NOT COMPILE to prove that type mismatches are caught at compile time.
 *
 * Verify compile failures with: `sbt compile-fail-tests/test:compile`
 */
class TypeSafetyRegressionSpec extends AnyWordSpec with Matchers {

  case class User(
    id: Long,
    name: String,
    email: String)
  case class Product(
    id: String,
    name: String,
    price: Double)

  "Type-Safe FlowForgePipeline" should {

    "FAIL TEST #1: Type parameter mismatch in pipeline creation" in {
      // This should fail because we declare String input but try to pass User
      val _ = 42 // Prevent formatter from removing braces

      assertTypeError("""
        val pipeline: FlowForgePipeline[IO, String, String] = FlowForgePipeline[IO, User, String](
          name = "mismatched-pipeline",
          source = DataSource.gcs("bucket", "path", DataFormat.Parquet),
          sink = DataSink.gcs("bucket", "output", DataFormat.Parquet),
          transformation = Kleisli[IO, User, String](user => IO.pure(user.name)),
          validations = List.empty,
          config = None
        )
      """)
    }

    "FAIL TEST #2: Type parameter mismatch in transformation chain" in {
      // This should fail because transformation expects User but pipeline has String input
      val _ = 42 // Prevent formatter from removing braces

      assertTypeError("""
        val userTransform: PipelineComponent[IO, User, String] = 
          Kleisli[IO, User, String](user => IO.pure(user.name))
          
        val pipeline = FlowForgePipeline[IO, String, String](
          name = "mismatched-transform",
          source = DataSource.gcs("bucket", "path", DataFormat.Parquet),
          sink = DataSink.gcs("bucket", "output", DataFormat.Parquet),
          transformation = userTransform, // Type mismatch: expects User but pipeline has String
          validations = List.empty,
          config = None
        )
      """)
    }

    "FAIL TEST #3: Type parameter mismatch in validation" in {
      // This should fail because validation expects Product but pipeline outputs String
      val _ = 42 // Prevent formatter from removing braces

      assertTypeError("""
        val productValidation: QualityCheck[Product] = 
          (product: Product) => if (product.price > 0) ().validNel else FlowForgeError.ValidationError("Invalid price").invalidNel
          
        val pipeline = FlowForgePipeline[IO, String, String](
          name = "mismatched-validation",
          source = DataSource.gcs("bucket", "path", DataFormat.Parquet),
          sink = DataSink.gcs("bucket", "output", DataFormat.Parquet),
          transformation = Kleisli[IO, String, String](s => IO.pure(s.toUpperCase)),
          validations = List(productValidation), // Type mismatch: expects Product but pipeline outputs String
          config = None
        )
      """)
    }

    "Builder supports chained type-safe transformations" in
      // This compiles: String -> User -> Product via properly typed transforms
      assertCompiles("""
        val builder = EnhancedPipelineBuilder.from[IO, String]("test", DataSource.gcs("bucket", "path", DataFormat.Parquet))
          .transform[User](s => IO.pure(User(1L, s, s + "@example.com")))
          .transform[Product](user => IO.pure(Product(user.name, user.email, 0.0)))
      """)

    "FAIL TEST #5: Attempt to use Any type explicitly should not be possible" in {
      // This demonstrates that Any cannot be used with the type-safe pipeline
      val _ = 42 // Prevent formatter from removing braces

      assertTypeError("""
        val anyTransform: PipelineComponent[IO, Any, Any] = 
          Kleisli[IO, Any, Any](any => IO.pure(any))
          
        val pipeline = FlowForgePipeline[IO, String, String](
          name = "any-pipeline",
          source = DataSource.gcs("bucket", "path", DataFormat.Parquet),
          sink = DataSink.gcs("bucket", "output", DataFormat.Parquet),
          transformation = anyTransform, // Should fail: Any not compatible with String types
          validations = List.empty,
          config = None
        )
      """)
    }

    "demonstrate that valid type-safe pipelines DO compile correctly" in {
      // This test SHOULD compile to prove the system works for valid cases
      val validPipeline = FlowForgePipeline[IO, User, String](
        name = "valid-type-safe-pipeline",
        source = DataSource.gcs("bucket", "users", DataFormat.Parquet),
        sink = DataSink.gcs("bucket", "names", DataFormat.Parquet),
        transformation = Kleisli[IO, User, String](user => IO.pure(user.name.toUpperCase)),
        validations = List((name: String) =>
          if (name.nonEmpty) ().validNel else FlowForgeError.ValidationError("Empty name").invalidNel,
        ),
        config = None,
      )

      validPipeline.name shouldBe "valid-type-safe-pipeline"
    }

    "demonstrate that valid builder chain works correctly" in {
      // This test SHOULD compile to prove type-safe composition works
      val validBuilder = EnhancedPipelineBuilder
        .from[IO, User]("test", DataSource.gcs("bucket", "path", DataFormat.Parquet))
        .transform[String](user => IO.pure(user.name))
        .map[String](name => name.toUpperCase)
        .filter(_.nonEmpty)
        .to(DataSink.gcs("bucket", "output", DataFormat.Parquet))

      val pipeline = validBuilder.build
      pipeline should be(a[Right[_, _]])
    }
  }
}
