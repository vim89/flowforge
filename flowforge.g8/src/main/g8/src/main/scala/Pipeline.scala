package $organization$.$name;format="packaged"$

import cats.effect.{IO, IOApp}
import cats.implicits._
import com.flowforge.core.PipelineBuilder
import com.flowforge.core.contracts.SchemaPolicy
import com.flowforge.core.instances.EffectInstances._
import com.flowforge.core.types._
import ContractShapes._

/**
 * FlowForge pipeline with 100% compile-time contract validation.
 * 
 * Generated Configuration:
 * - Effect System: $effect_system$ 
 * - Execution Engine: $execution_engine$
 * - Cloud Provider: $cloud_provider$
 */
object DataPipeline extends IOApp.Simple {

  def run: IO[Unit] = {
    
    // Create a contract-enforced pipeline using current PipelineBuilder API
    val pipeline = PipelineBuilder[IO]("users-pipeline")
      .withDescription("Generated FlowForge pipeline with compile-time contracts")
      .addTypedSource[User, User, SchemaPolicy.Exact](
        gcsParquetSource[User]("demo-bucket", "users/*.parquet"),
        source => IO.pure(User(1L, "Alice", "alice@example.com"))
      )
      .addTransform[User] { user =>
        // Simple pass-through transform for demonstration
        IO.pure(user)
      }
      .addTypedSink[User, SchemaPolicy.Exact](
        gcsParquetSink[User]("demo-bucket", "output/"),
        (user, sink) => IO.println(s"✅ Processing: \$user")
      )
      .build()
      
    for {
      _ <- IO.println("🚀 FlowForge Pipeline - 100% Compile-Time Contracts")
      _ <- IO.println("✨ If this compiles, contracts are perfectly aligned!")
      
      // Execute with monitoring and lineage (OpenLineage automatically emitted)
      result <- pipeline.executeWithMonitoring(())
      
      _ <- result.status match {
        case ExecutionStatus.Success =>
          IO.println(s"🎉 Success in \${result.duration}")
        case ExecutionStatus.Failed =>
          IO.println(s"❌ Failed: \${result.errors.mkString(", ")}")
      }
      
    } yield ()
  }
}