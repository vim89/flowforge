/**
 * FlowForge Core Module - Pipeline Builder
 *
 * File: modules/core/src/main/scala/com/flowforge/core/PipelineBuilder.scala
 * Package: com.flowforge.core
 *
 * Type-safe, fluent builder for constructing FlowForge data pipelines.
 */
package com.flowforge.core

import cats.data._
import cats.syntax.all._
import com.flowforge.core.algebra.EffectSystem
import com.flowforge.core.types._
import com.flowforge.core.types.PipelineTypes._

/**
 * Fluent builder for constructing data pipelines.
 * 
 * Provides a type-safe way to build complex pipelines with validation.
 */
case class PipelineBuilder[F[_]: EffectSystem] private (
  name: Option[String] = None,
  source: Option[DataSource] = None,
  sink: Option[DataSink] = None,
  transformations: List[PipelineComponent[F, Any, Any]] = List.empty,
  validations: List[QualityCheck[Any]] = List.empty,
  config: Option[PipelineConfig] = None
) {

  def withName(pipelineName: String): PipelineBuilder[F] =
    copy(name = Some(pipelineName))

  def withSource(dataSource: DataSource): PipelineBuilder[F] =
    copy(source = Some(dataSource))

  def withSink(dataSink: DataSink): PipelineBuilder[F] =
    copy(sink = Some(dataSink))

  def addTransformation[A, B](transform: A => F[B]): PipelineBuilder[F] = {
    val component = Kleisli(transform).asInstanceOf[PipelineComponent[F, Any, Any]]
    copy(transformations = transformations :+ component)
  }

  def addValidation[A](validation: QualityCheck[A]): PipelineBuilder[F] = {
    val check = validation.asInstanceOf[QualityCheck[Any]]
    copy(validations = validations :+ check)
  }

  def withConfig(pipelineConfig: PipelineConfig): PipelineBuilder[F] =
    copy(config = Some(pipelineConfig))

  def build: ConfigValidation[FlowForgePipeline[F]] = {
    val nameValidation = name.toValidNel(ConfigError.MissingRequired("name"))
    val sourceValidation = source.toValidNel(ConfigError.MissingRequired("source"))
    val sinkValidation = sink.toValidNel(ConfigError.MissingRequired("sink"))

    nameValidation.product(sourceValidation).product(sinkValidation).map {
      case ((n, src), snk) => FlowForgePipeline(n, src, snk, transformations, validations, config)
    }
  }
}

object PipelineBuilder {
  def empty[F[_]: EffectSystem]: PipelineBuilder[F] = PipelineBuilder[F]()
}