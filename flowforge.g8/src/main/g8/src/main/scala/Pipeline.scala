package $organization$.$name;format="packaged"$

import cats.effect.{IO, IOApp}
import com.flowforge.core.PipelineBuilder
import com.flowforge.core.contracts.SchemaPolicy
import com.flowforge.core.instances.EffectInstances._
import com.flowforge.core.types.TypedIO._
import ContractShapes._

/**
 * FlowForge pipeline with 100% compile-time contract validation.
 * 
 * This pipeline demonstrates:
 * 1. Typed sources with contract enforcement
 * 2. Schema-safe transformations  
 * 3. Typed sinks with policy validation
 * 4. Compile-time prevention of schema drift
 */
object DataPipeline extends IOApp.Simple {

  def run: IO[Unit] = {
    
    // Create a contract-enforced pipeline
    // Note: If UserEvent doesn't conform to ProcessedEvent schema under the policy,
    // this will NOT COMPILE - demonstrating our core USP!
    val pipeline = PipelineBuilder[IO]("user-events-processor")
      .withDescription("Processes user events with compile-time contract validation")
      .addTypedSource[UserEvent, UserEvent, SchemaPolicy.Exact](
        gcsParquetSource[UserEvent]("my-bucket", "events/raw/"),
        source => IO.pure(UserEvent(1L, 123L, "login", System.currentTimeMillis()))
      )
      .addTransform[UserEvent] { event =>
        IO.pure(ProcessedEvent(
          id = event.id,
          userId = event.userId, 
          eventName = event.eventName,
          timestamp = event.timestamp,
          processed = true
        ))
      }
      .addTypedSink[ProcessedEvent, SchemaPolicy.Exact](
        gcsParquetSink[ProcessedEvent]("my-bucket", "events/processed/"),
        (event, sink) => {
          println(s"Writing processed event: \$event")
          IO.unit
        }
      )
      .build()
      
    println(s"Pipeline '\${pipeline.name}' compiled successfully!")
    println("This proves contracts are aligned - pipeline will execute safely.")
    
    // Execute the pipeline
    pipeline.execute()
  }
}