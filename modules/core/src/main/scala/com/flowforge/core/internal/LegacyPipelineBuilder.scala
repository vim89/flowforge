/**
 * FlowForge Core Module - Pipeline Builder
 *
 * File: modules/core/src/main/scala/com/flowforge/core/LegacyPipelineBuilder.scala Package:
 * com.flowforge.core
 *
 * Type-safe, fluent builder for constructing FlowForge data pipelines.
 */
package com.flowforge.core.internal

import cats.data._
import cats.syntax.all._
import com.flowforge.core.algebra.EffectSystem
import com.flowforge.core.types.PipelineTypes._
import com.flowforge.core.types._
import com.flowforge.core.{ ConfigValidation, FlowForgePipeline }

/**
 * Fluent builder for constructing data pipelines.
 *
 * Provides a type-safe way to build complex pipelines with validation.
 */
@deprecated(
  "Use typed LegacyPipelineBuilder (compile-time contracts). This runtime builder will be removed post-1.0.",
  "0.9.0",
)
// Legacy runtime builder: kept for migration only.
case class LegacyPipelineBuilder[F[_]: EffectSystem] private (
  name: Option[String] = None,
  source: Option[DataSource] = None,
  sink: Option[DataSink] = None,
  transformations: List[PipelineComponent[F, Any, Any]] = List.empty,
  validations: List[QualityCheck[Any]] = List.empty,
  config: Option[PipelineConfig] = None) {

  def withName(pipelineName: String): LegacyPipelineBuilder[F] =
    copy(name = Some(pipelineName))

  def withSource(dataSource: DataSource): LegacyPipelineBuilder[F] =
    copy(source = Some(dataSource))

  def withSink(dataSink: DataSink): LegacyPipelineBuilder[F] =
    copy(sink = Some(dataSink))

  def addTransformation[A, B](transform: A => F[B]): LegacyPipelineBuilder[F] = {
    val component = Kleisli(transform).asInstanceOf[PipelineComponent[F, Any, Any]]
    copy(transformations = transformations :+ component)
  }

  def addValidation[A](validation: QualityCheck[A]): LegacyPipelineBuilder[F] = {
    val check = validation.asInstanceOf[QualityCheck[Any]]
    copy(validations = validations :+ check)
  }

  def withConfig(pipelineConfig: PipelineConfig): LegacyPipelineBuilder[F] =
    copy(config = Some(pipelineConfig))

  def build: ConfigValidation[FlowForgePipeline[F]] = {
    val nameValidation   = name.toValidNel(ConfigError.MissingRequired("name"))
    val sourceValidation = source.toValidNel(ConfigError.MissingRequired("source"))
    val sinkValidation   = sink.toValidNel(ConfigError.MissingRequired("sink"))

    nameValidation.product(sourceValidation).product(sinkValidation).map {
      case ((n, src), snk) =>
        FlowForgePipeline(n, src, snk, transformations, validations, config)
    }
  }
}

object LegacyPipelineBuilder {
  def empty[F[_]: EffectSystem]: LegacyPipelineBuilder[F] = LegacyPipelineBuilder[F]()
}
