/**
 * FlowForge Core Module - Pipeline Implementation
 *
 * File: modules/core/src/main/scala/com/flowforge/core/FlowForgePipeline.scala Package: com.flowforge.core
 *
 * Complete FlowForge pipeline with execution capabilities.
 */
package com.flowforge.core

import cats.syntax.all._
import com.flowforge.core.algebra.EffectSystem
import com.flowforge.core.types.PipelineTypes._
import com.flowforge.core.types._

/**
 * Represents a complete FlowForge pipeline with input and output types.
 *
 * @tparam F
 *   Effect type (IO, Task, etc.)
 * @tparam A
 *   Input data type
 * @tparam B
 *   Output data type
 */
case class FlowForgePipeline[F[_]: EffectSystem, A, B](
  name: String,
  source: DataSource,
  sink: DataSink,
  transformation: PipelineComponent[F, A, B], // Single composed transformation
  validations: List[QualityCheck[B]],
  config: Option[PipelineConfig]) {

  /**
   * Execute the pipeline with the given input data and return accumulated validations.
   */
  def executeValidated(inputData: A): F[cats.data.ValidatedNel[FlowForgeError, B]] = {
    EffectSystem[F]

    // Apply single composed transformation - now completely type-safe
    val transformedF: F[B] = transformation.run(inputData)

    // Validate after transformations; accumulate all validation errors
    transformedF.map { data =>
      import cats.data.{ NonEmptyList, Validated }
      val start: Validated[NonEmptyList[FlowForgeError], Unit] = Validated.valid(())
      val combined = validations.foldLeft(start) { (acc, f) =>
        (acc, f(data)) match {
          case (Validated.Valid(_), v)                        => v
          case (i @ Validated.Invalid(_), Validated.Valid(_)) => i
          case (Validated.Invalid(e1), Validated.Invalid(e2)) => Validated.Invalid(e1.concatNel(e2))
        }
      }
      combined.map(_ => data)
    }
  }

  /**
   * Execute the pipeline and raise validation failures in the effect channel. This avoids throwing exceptions
   * and preserves functional error handling.
   */
  def execute(inputData: A): F[B] = {
    val F = EffectSystem[F]
    executeValidated(inputData).flatMap {
      case cats.data.Validated.Valid(result) => F.pure(result)
      case cats.data.Validated.Invalid(errors) =>
        val pipelineError = PipelineError.StageExecutionError(
          name,
          s"Validation failed: ${errors.toList.map(_.message).mkString(", ")}",
        )
        F.raiseError(pipelineError)
    }
  }

  /**
   * Validate the pipeline configuration.
   */
  def validate: com.flowforge.core.syntax.ValidationSyntax.ConfigValidation[Unit] =
    // Pipeline-level validation logic would go here
    ().validNel
}
