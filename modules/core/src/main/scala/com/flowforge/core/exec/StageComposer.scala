package com.flowforge.core.exec

import cats.data.Kleisli
import com.flowforge.core.algebra.EffectSystem
import com.flowforge.core.lineage.{ LineageError, OpenLineageEmitter }
import com.flowforge.core.observability.Tracer

/**
 * A stage reduced to the only two things execution needs: a name, and an arrow to run.
 *
 * Builders hold richer stage types (`PipelineStage`, `InMemoryStage`). Those carry sources, sinks and
 * contracts, none of which execution looks at. Narrowing to this type is what lets every builder share one
 * composer instead of each writing its own fold.
 */
final class ExecutableStage[F[_]] private (val name: String, val run: Kleisli[F, Any, Any])

object ExecutableStage {

  /**
   * Erase a stage arrow to `Any => Any`.
   *
   * The cast is safe because a builder only ever appends a stage whose input type is the previous stage's
   * output type, and it is unchecked because that chaining is tracked in the builder's type parameters
   * rather than in the stage list. Keeping the cast here means it exists once rather than once per builder.
   */
  def apply[F[_]](name: String, stage: Kleisli[F, _, _]): ExecutableStage[F] =
    new ExecutableStage[F](name, stage.asInstanceOf[Kleisli[F, Any, Any]])
}

/**
 * Composes pipeline stages into a single arrow and decorates each stage with tracing and lineage.
 *
 * This is the only place in core that answers "how is a list of stages run". It previously had three
 * answers: `PipelineBuilder.build`, `LineageRunner.runWithEmitter`, and the in-memory builders. They
 * disagreed, and each one was missing something the others had.
 */
object StageComposer {

  private val DefaultNamespace = "flowforge"

  /**
   * Build the arrow that runs `stages` in order.
   *
   * Tracing and lineage are per stage. A stage's span and its START/COMPLETE events cover that stage only,
   * so stage durations are comparable to each other. When an emitter is given, the whole run is also
   * wrapped in a pipeline level START/COMPLETE pair sharing one run id.
   *
   * The run id is generated per execution, not per build, because one built pipeline may be run many times
   * and OpenLineage expects a run id to identify a single run.
   */
  def compose[F[_], In, Out](
    pipelineName: String,
    stages: List[ExecutableStage[F]],
    tracer: Option[Tracer[F]] = None,
    lineage: Option[OpenLineageEmitter[F]] = None,
  )(implicit F: EffectSystem[F],
  ): Kleisli[F, In, Out] = {
    val erased = Kleisli[F, Any, Any] { input =>
      lineage match {
        case None          => chain(stages, tracer, None).run(input)
        case Some(emitter) => runTracked(pipelineName, stages, tracer, emitter, input)
      }
    }
    erased.asInstanceOf[Kleisli[F, In, Out]]
  }

  /** Emitter, run id and namespace, resolved once per execution and shared by every stage of that run. */
  private final case class RunContext[F[_]](
    emitter: OpenLineageEmitter[F],
    runId: String,
    namespace: String)

  private def runTracked[F[_]](
    pipelineName: String,
    stages: List[ExecutableStage[F]],
    tracer: Option[Tracer[F]],
    emitter: OpenLineageEmitter[F],
    input: Any,
  )(implicit F: EffectSystem[F],
  ): F[Any] = {
    // Both reads are effects: generateRunId reads the clock and the environment.
    val context = F.delay(
      RunContext(
        emitter,
        OpenLineageEmitter.generateRunId(pipelineName),
        sys.env.getOrElse("OPENLINEAGE_NAMESPACE", DefaultNamespace),
      ),
    )
    F.flatMap(context) { ctx =>
      around(ctx, pipelineName, chain(stages, tracer, Some(ctx)).run(input))
    }
  }

  private def chain[F[_]](
    stages: List[ExecutableStage[F]],
    tracer: Option[Tracer[F]],
    lineage: Option[RunContext[F]],
  )(implicit F: EffectSystem[F],
  ): Kleisli[F, Any, Any] =
    stages.foldLeft(Kleisli.ask[F, Any]) { (acc, stage) =>
      acc.andThen(observe(stage, tracer, lineage))
    }

  /** Wrap one stage. The span and the events cover this stage and nothing before it. */
  private def observe[F[_]](
    stage: ExecutableStage[F],
    tracer: Option[Tracer[F]],
    lineage: Option[RunContext[F]],
  )(implicit F: EffectSystem[F],
  ): Kleisli[F, Any, Any] =
    Kleisli[F, Any, Any] { in =>
      val body   = stage.run.run(in)
      val traced = tracer.fold(body)(t => t.inSpan(stage.name)(body))
      lineage.fold(traced)(ctx => around(ctx, stage.name, traced))
    }

  /** START before, COMPLETE on success, FAIL on error. The original error is always re-raised. */
  private def around[F[_], A](
    ctx: RunContext[F],
    jobName: String,
    fa: F[A],
  )(implicit F: EffectSystem[F],
  ): F[A] =
    F.flatMap(emit(ctx.emitter.emitJobStart(ctx.namespace, jobName, ctx.runId))) { _ =>
      F.flatMap(F.attempt(fa)) {
        case Right(a) =>
          F.map(emit(ctx.emitter.emitJobComplete(ctx.namespace, jobName, ctx.runId)))(_ => a)
        case Left(e) =>
          F.flatMap(emit(ctx.emitter.emitJobFail(ctx.namespace, jobName, ctx.runId, e.getMessage)))(_ =>
            F.raiseError[A](e),
          )
      }
    }

  /**
   * Discard the outcome of an emission.
   *
   * Lineage describes the pipeline, it is not on the data path, so a lineage backend being down is not a
   * reason to fail a production run. The emitters report their own failures: `HttpOpenLineageEmitter` logs
   * and returns `Left`. This also catches an emitter that throws rather than returning `Left`, since an
   * emitter is supplied by the caller and is not trusted to honour its signature.
   */
  private def emit[F[_]](
    fa: F[Either[LineageError, Unit]],
  )(implicit F: EffectSystem[F],
  ): F[Unit] =
    F.map(F.attempt(fa))(_ => ())
}
