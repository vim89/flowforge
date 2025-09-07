package $organization$.$name;format="packaged"$

import com.flowforge.core.contracts.derive.Shape

/**
 * Data contract definitions for your FlowForge pipeline.
 * 
 * QUICKSTART DEMO:
 * 1. Run `sbt compile` - should succeed
 * 2. Change `id: Long` to `id: String` below to simulate contract drift
 * 3. Run `sbt compile` - should fail with clear error message showing drift
 * 4. Revert the change to fix the build
 * 
 * This demonstrates FlowForge's USP: "Pipelines will not even build if contracts drift!"
 */

// Input data contract - represents data coming from your source
case class UserEvent(
  id: Long,           // Try changing to String to see compile failure!
  userId: Long,
  eventName: String,
  timestamp: Long
)

// Output data contract - represents processed data going to your sink  
case class ProcessedEvent(
  id: Long,
  userId: Long, 
  eventName: String,
  timestamp: Long,
  processed: Boolean = true
)

// Shape instances for compile-time contract validation
object ContractShapes {
  implicit val userEventShape: Shape[UserEvent] = Shape.gen[UserEvent]
  implicit val processedEventShape: Shape[ProcessedEvent] = Shape.gen[ProcessedEvent]
}