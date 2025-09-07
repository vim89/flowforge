package $organization$.$name;format="packaged"$

import cats.effect.{IO, IOApp}
import cats.implicits._
import com.flowforge.core.PipelineBuilder
import com.flowforge.core.contracts.SchemaPolicy
import com.flowforge.core.instances.EffectInstances._
import com.flowforge.core.types.TypedIO._
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

  import com.flowforge.quality.deequ.DeequIntegration.{nonNull, unique}

  def run: IO[Unit] = {
    
    // Create a contract-enforced pipeline matching end-to-end plan spec
    val pipeline = PipelineBuilder[IO]("users-pipeline")
      .addTypedSource[User, User, SchemaPolicy.Exact](
        localParquetSource[User]("./data/users/"),
        source => IO.pure(User(1L, "Alice", "alice@example.com"))
      )
      .quality(nonNull("email") and unique("id"))
      .addTypedSink[User, SchemaPolicy.Exact](
        localParquetSink[User]("./data/output/"),
        (user, sink) => IO.println(s"✅ Processing: \$user")
      )
      .build()
      
    for {
      _ <- IO.println("🚀 FlowForge Pipeline - 100% Compile-Time Contracts")
      _ <- IO.println("✨ If this compiles, contracts are perfectly aligned!")
      
      // Execute with monitoring and lineage
      result <- pipeline.executeWithMonitoring(())
      
      _ <- result.status match {
        case com.flowforge.core.types.ExecutionStatus.Success =>
          IO.println(s"🎉 Success in \${result.duration}")
        case com.flowforge.core.types.ExecutionStatus.Failed =>
          IO.println(s"❌ Failed: \${result.errors.mkString(", ")}")
      }
      
    } yield ()
  }
}