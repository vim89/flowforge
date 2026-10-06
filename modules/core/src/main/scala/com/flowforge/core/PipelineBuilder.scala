package com.flowforge.core

import cats.data.Kleisli
import com.flowforge.core.algebra.EffectSystem
import com.flowforge.core.contracts.{ SchemaConforms, SchemaPolicy }
import com.flowforge.core.exec.{ StageChain, StageComposer, StageKind }
import com.flowforge.core.lineage.OpenLineageEmitter
import com.flowforge.core.observability.Tracer
import com.flowforge.core.types.BuilderState.{ WithContract, WithTransform }
import com.flowforge.core.types.{
  BuilderState,
  DataSink,
  DataSource,
  PipelineConfig,
  PipelineStage,
  TypedSink,
  TypedSource,
}
import com.flowforge.framework.{ Pipeline => FFPipeline, PipelineMetadata }

/**
 * 100% Compile-time Contract-aware Pipeline builder
 *
 * Implements the complete specification from compile-time contract documents:
 *   - Phantom types ensure all required stages present before build
 *   - Explicit DataContract and SchemaPolicy parameters
 *   - SchemaConforms evidence required for all typed endpoints
 *   - Incomplete pipelines are literally unbuildable (won't compile)
 *
 * "Pipelines will not even build if source or target schema do not match or align"
 */
case class PipelineBuilder[S <: BuilderState, F[_]: EffectSystem, In, Out] private (
  name: String,
  description: String = "",
  stages: StageChain[F, In, Out],
  config: Option[PipelineConfig] = None,
  lineageEmitter: Option[OpenLineageEmitter[F]] = None,
  tracer: Option[Tracer[F]] = None) {

  def withDescription(desc: String): PipelineBuilder[S, F, In, Out] =
    copy(description = desc)

  def withConfig(c: PipelineConfig): PipelineBuilder[S, F, In, Out] =
    copy(config = Some(c))

  def withLineageEmitter(emitter: OpenLineageEmitter[F]): PipelineBuilder[S, F, In, Out] =
    copy(lineageEmitter = Some(emitter))

  /** Attach a tracer implementation (no-op by default). */
  def withTracer(t: Tracer[F]): PipelineBuilder[S, F, In, Out] =
    copy(tracer = Some(t))

  /**
   * Append a stage and move to the next phantom state.
   *
   * `copy` cannot do this because the phantom state and the Out type change, and `copy` returns the same
   * type. That forced a hand-written constructor call in each stage method, and three of them listed five of
   * the six fields, so a tracer attached before the first stage was dropped. Carrying the fields here means
   * there is one place to update when a field is added.
   *
   * The arrow has to read what the builder currently produces, which is what makes the stage chain and the
   * builder's own type parameters say the same thing rather than two things that happen to agree.
   */
  private def advance[S2 <: BuilderState, Out2](
    stageName: String,
    kind: StageKind,
    arrow: Kleisli[F, Out, Out2],
  ): PipelineBuilder[S2, F, In, Out2] =
    PipelineBuilder[S2, F, In, Out2](
      name,
      description,
      stages.andThen(stageName, kind, arrow),
      config,
      lineageEmitter,
      tracer,
    )

  /**
   * Add typed source with explicit contract and policy. This is the ONLY way to add sources - no untyped
   * escape hatches.
   *
   * SOURCE: produced C must conform to declared contract R under policy P Advances phantom state: Empty ->
   * HasSource with HasContract
   *
   * A source reads from outside the pipeline, so its arrow takes no input. The `Out =:= Unit` evidence
   * restricts it to a builder that has not produced a value yet; without it a source could be appended after
   * a transform, which discarded the transform's output. At `Empty` the builder's `Out` is already `Unit`, so
   * the evidence resolves on its own and no caller names it.
   */
  def addTypedSource[C, R, P <: SchemaPolicy](
    source: TypedSource[R],
    reader: DataSource => F[C],
  )(implicit
    ev: SchemaConforms[C, R, P],
    atStart: Out =:= Unit,
  ): PipelineBuilder[WithContract, F, In, C] = {
    val stage = PipelineStage.Source[F, C](
      name = s"contract-source-${stages.size}",
      description = s"Contract-aware source with compile-time validation",
      dataSource = source.underlying,
      execute = Kleisli(_ => reader(source.underlying)),
    )
    // `=:=` is a function in both Scala versions, so the source's Unit input is adapted rather than cast.
    advance[WithContract, C](stage.name, StageKind.Source, stage.execute.local[Out](atStart))
  }

  /**
   * Add transformation stage. Advances phantom state: HasSource with HasContract -> HasSource with
   * HasContract with HasTransform
   */
  def addTransform[C](
    transform: Out => F[C],
  )(implicit
    evidence: S <:< WithContract,
  ): PipelineBuilder[WithTransform, F, In, C] = {
    val stage = PipelineStage.Transform[F, Out, C](
      name = s"contract-transform-${stages.size}",
      description = "Contract-aware transformation",
      execute = Kleisli(transform),
    )
    advance[WithTransform, C](stage.name, StageKind.Transform, stage.execute)
  }

  /**
   * For noTransform we can use identity transform. By our design - builder only permits transforms once
   * source and contract are in place. This is the classic phantom-type/typestate builder pattern: the
   * compiler forces the steps Same preconditions, zero logic, keeps the types.
   * @param evSC
   * @param A
   * @return
   */
  def noTransform(implicit evSC: S <:< WithContract, A: cats.Applicative[F])
    : PipelineBuilder[WithTransform, F, In, Out] =
    addTransform((o: Out) => A.pure(o))

  /**
   * Add typed sink with explicit contract and policy. This is the ONLY way to add sinks - no untyped escape
   * hatches.
   *
   * SINK: current Out must conform to declared contract R under policy P Advances phantom state: HasTransform
   * -> Complete (HasSource with HasContract with HasTransform with HasSink)
   *
   * The pipeline's output type becomes `Unit`, because that is what a sink produces: the writer returns
   * `F[Unit]` and the stage has nothing else to hand on. It used to keep the record as the output type while
   * the stage returned unit, so running the pipeline and reading its result threw a `ClassCastException`.
   */
  def addTypedSink[R, P <: SchemaPolicy](
    sink: TypedSink[R],
    writer: (Out, DataSink) => F[Unit],
  )(implicit
    transformComplete: S <:< WithTransform,
    ev: SchemaConforms[Out, R, P],
  ): PipelineBuilder[BuilderState.Complete, F, In, Unit] = {
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
    advance[BuilderState.Complete, Unit](stage.name, StageKind.Sink, stage.execute)
  }

  /**
   * Build pipeline - ONLY available when all required stages are present. This is the key compile-time
   * guarantee: incomplete pipelines cannot be built.
   */
  def build(
  )(implicit
    complete: S <:< BuilderState.Complete,
  ): FFPipeline[F, In, Out] = {

    val kleisli: Kleisli[F, In, Out] = StageComposer.compose[F, In, Out](
      pipelineName = name,
      stages = stages,
      tracer = tracer,
      lineage = lineageEmitter,
    )

    val md = PipelineMetadata(
      name = name,
      stages = stages.names,
      transformations = stages.count(StageKind.Transform),
      qualityChecks = 0,
      tags = Map(
        "builder" -> "contract-aware",
        "lineage" -> (if (lineageEmitter.isDefined) "configured" else "none"),
      ),
    )

    FFPipeline(kleisli, md)
  }

}

/** The only entry point to the builder: a pipeline starts empty and the phantom state goes up from there. */
object PipelineBuilder {

  /**
   * Create new pipeline builder. Starts with Empty phantom state - must add source, transform, and sink to
   * build.
   */
  def apply[F[_]: EffectSystem](name: String): PipelineBuilder[BuilderState.Empty, F, Unit, Unit] =
    // `stages` has no default: an empty chain's input and output types are the same type, which is only true
    // of a builder that has not added a stage yet, so there is nothing for a default to mean elsewhere.
    PipelineBuilder[BuilderState.Empty, F, Unit, Unit](
      name,
      stages = StageChain.empty[F, Unit],
    )
}
