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
import com.flowforge.core.types._
import com.flowforge.core.types.PipelineTypes._

/**
 * Represents a complete FlowForge pipeline.
 */
case class FlowForgePipeline[F[_]: EffectSystem](
  name: String,
  source: DataSource,
  sink: DataSink,
  transformations: List[PipelineComponent[F, Any, Any]],
  validations: List[QualityCheck[Any]],
  config: Option[PipelineConfig]) {

  /**
   * Execute the pipeline with the given input data and return accumulated validations.
   */
  def executeValidated[A](inputData: A): F[cats.data.ValidatedNel[FlowForgeError, A]] = {
    val F = EffectSystem[F]

    // Apply transformations sequentially within F
    val transformedF: F[Any] =
      transformations.foldLeft(F.pure(inputData.asInstanceOf[Any])) { (acc, transform) =>
        acc.flatMap(data => transform.run(data))
      }

    // Validate after transformations; accumulate all validation errors
    transformedF.map { data =>
      val results = validations.map(_(data))
      results.sequence.map(_ => data.asInstanceOf[A])
    }
  }

  /**
   * Execute the pipeline and raise validation failures in the effect channel.
   * This avoids throwing exceptions and preserves functional error handling.
   */
  def execute[A](inputData: A): F[A] = {
    val F = EffectSystem[F]
    executeValidated(inputData).flatMap {
      case cats.data.Validated.Valid(a)   => F.pure(a)
      case cats.data.Validated.Invalid(e) =>
        F.raiseError(new RuntimeException(s"Validation failed: ${e.toList.map(_.message).mkString(", ")}"))
    }
  }

  /**
   * Validate the pipeline configuration.
   */
  def validate: ConfigValidation[Unit] =
    // Pipeline-level validation logic would go here
    ().validNel
}
