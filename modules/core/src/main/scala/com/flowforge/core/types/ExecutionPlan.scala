package com.flowforge.core.types

/**
 * Pipeline execution plan for optimization.
 */
sealed trait ExecutionPlan extends Product with Serializable

object ExecutionPlan {
  case object Sequential                                  extends ExecutionPlan
  case object Parallel                                    extends ExecutionPlan
  case class Hybrid(parallelStages: Set[String])          extends ExecutionPlan
  case class Distributed(partitions: Int, executors: Int) extends ExecutionPlan
}
