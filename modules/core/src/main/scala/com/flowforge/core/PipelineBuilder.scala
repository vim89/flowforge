package com.flowforge.core

import cats.data.Kleisli
import com.flowforge.core.algebra.EffectSystem
import com.flowforge.core.contracts.{ SchemaConforms, SchemaPolicy }
import com.flowforge.core.types.{
  BuilderState,
  DataFormat,
  DataSink,
  DataSource,
  Environment,
  HasContract,
  HasSource,
  HasTransform,
  Pipeline,
  PipelineConfig,
  PipelineStage,
  TypedSink,
  TypedSource,
}

/**
 * 100% Compile-Time Contract-Aware Pipeline Builder
 *
 * Implements the complete specification from compile-time contract documents:
 *   - Phantom types ensure all required stages present before build
 *   - Explicit DataContract and SchemaPolicy parameters
 *   - SchemaConforms evidence required for all typed endpoints
 *   - Incomplete pipelines are literally unbuildable (won't compile)
 *
 * USP: "Pipelines will not even build if source or target schema do not match or align"
 */
case class PipelineBuilder[S <: BuilderState, F[_]: EffectSystem, In, Out] private (
  name: String,
  description: String = "",
  stages: List[PipelineStage[F, _, _]] = List.empty,
  config: Option[PipelineConfig] = None) {

  def withDescription(desc: String): PipelineBuilder[S, F, In, Out] =
    copy(description = desc)

  def withConfig(c: PipelineConfig): PipelineBuilder[S, F, In, Out] =
    copy(config = Some(c))

  /**
   * Add typed source with explicit contract and policy. This is the ONLY way to add sources - no untyped
   * escape hatches.
   *
   * SOURCE: produced C must conform to declared contract R under policy P Advances phantom state: Empty ->
   * HasSource with HasContract
   */
  def addTypedSource[C, R, P <: SchemaPolicy](
    source: TypedSource[R],
    reader: DataSource => F[C],
  )(implicit ev: SchemaConforms[C, R, P],
  ): PipelineBuilder[HasSource with HasContract, F, Unit, C] = {
    val stage = PipelineStage.Source[F, C](
      name = s"contract-source-${stages.size}",
      description = s"Contract-aware source with compile-time validation",
      dataSource = source.underlying,
      execute = Kleisli(_ => reader(source.underlying)),
    )
    PipelineBuilder[HasSource with HasContract, F, Unit, C](
      name,
      description,
      stages :+ stage,
      config,
    )
  }

  /**
   * Add transformation stage. Advances phantom state: HasSource with HasContract -> HasSource with
   * HasContract with HasTransform
   */
  def addTransform[C](
    transform: Out => F[C],
  )(implicit
    evidence: S <:< (HasSource with HasContract),
  ): PipelineBuilder[HasSource with HasContract with HasTransform, F, In, C] = {
    val stage = PipelineStage.Transform[F, Out, C](
      name = s"contract-transform-${stages.size}",
      description = "Contract-aware transformation",
      execute = Kleisli(transform),
    )
    PipelineBuilder[HasSource with HasContract with HasTransform, F, In, C](
      name,
      description,
      stages :+ stage,
      config,
    )
  }

  /**
   * Add typed sink with explicit contract and policy. This is the ONLY way to add sinks - no untyped escape
   * hatches.
   *
   * SINK: current Out must conform to declared contract R under policy P Advances phantom state: ... ->
   * Complete (HasSource with HasContract with HasTransform with HasSink)
   */
  def addTypedSink[R, P <: SchemaPolicy](
    sink: TypedSink[R],
    writer: (Out, DataSink) => F[Unit],
  )(implicit
    transformComplete: S <:< (HasSource with HasContract with HasTransform),
    ev: SchemaConforms[Out, R, P],
  ): PipelineBuilder[BuilderState.Complete, F, In, Out] = {
    val stage = PipelineStage.Sink[F, Out](
      name = s"contract-sink-${stages.size}",
      description = s"Contract-aware sink with compile-time validation",
      dataSink = sink.underlying,
      execute = Kleisli(data =>
        // Schema conformance is enforced at compile time via SchemaConforms evidence
        // Runtime validation could be added here if needed
        writer(data, sink.underlying),
      ),
    )
    PipelineBuilder[BuilderState.Complete, F, In, Out](
      name,
      description,
      stages :+ stage,
      config,
    )
  }

  /**
   * Build pipeline - ONLY available when all required stages are present. This is the key compile-time
   * guarantee: incomplete pipelines cannot be built.
   */
  def build(
  )(implicit
    complete: S <:< BuilderState.Complete,
  ): Pipeline[F, In, Out] =
    Pipeline(
      name = name,
      description = description,
      stages = stages,
      config = config.getOrElse(
        PipelineConfig(
          name = eu.timepit.refined.api.Refined.unsafeApply(if (name.nonEmpty) name else "default"),
          version = "1.0.0",
          environment = Environment.Development,
          source = DataSource.gcs("default", "default", DataFormat.Parquet),
          sink = DataSink.gcs("default", "default", DataFormat.Parquet),
        ),
      ),
    )
}

object PipelineBuilder {

  /**
   * Create new pipeline builder. Starts with Empty phantom state - must add source, transform, and sink to
   * build.
   */
  def apply[F[_]: EffectSystem](name: String): PipelineBuilder[BuilderState.Empty, F, Unit, Unit] =
    PipelineBuilder[BuilderState.Empty, F, Unit, Unit](name)
}
