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
   * output type, and it is unchecked because that chaining is tracked in the builder's type parameters rather
   * than in the stage list. Keeping the cast here means it exists once rather than once per builder.
   */
  def apply[F[_]](name: String, stage: Kleisli[F, _, _]): ExecutableStage[F] =
    new ExecutableStage[F](name, erase(stage))

  /**
   * Erase a stage arrow that is run only for its effect, keeping its input as the result.
   *
   * This is what a sink needs. A sink writes and returns `Unit`, but the builder that appended it goes on
   * declaring the data type, so the value written is what the pipeline produces and it has to survive the
   * stage. Without this the composed arrow returned `()` while claiming the data type, and the lie stayed
   * invisible for as long as nobody looked at the result.
   */
  def effectOnly[F[_]](name: String, stage: Kleisli[F, _, _])(implicit F: EffectSystem[F])
    : ExecutableStage[F] = {
    val run = erase(stage)
    new ExecutableStage[F](name, Kleisli(in => F.map(run.run(in))(_ => in)))
  }

  private def erase[F[_]](stage: Kleisli[F, _, _]): Kleisli[F, Any, Any] =
    stage.asInstanceOf[Kleisli[F, Any, Any]]
}

/**
 * Composes pipeline stages into a single arrow and decorates each stage with tracing and lineage.
 *
 * This is the only place in core that answers "how is a list of stages run". `PipelineBuilder.build` and the
 * in-memory builders each used to answer it separately. They disagreed, and each one was missing something
 * the others had.
 */
object StageComposer {

  private val DefaultNamespace = "flowforge"

  /**
   * Build the arrow that runs `stages` in order.
   *
   * Tracing and lineage are per stage. A stage's span and its START/COMPLETE events cover that stage only, so
   * stage durations are comparable to each other. When an emitter is given, the whole run is also wrapped in
   * a pipeline level START/COMPLETE pair.
   *
   * Run ids are generated per execution, not per build, because one built pipeline may be run many times and
   * a run id identifies a single run.
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

  /**
   * Emitter, namespace and the pipeline's own run id, resolved once per execution.
   *
   * The pipeline run id is here because every stage needs to know it. Stage run ids are not, because each
   * stage makes its own.
   */
  private final case class RunContext[F[_]](
    emitter: OpenLineageEmitter[F],
    pipelineRunId: String,
    namespace: String)

  private def runTracked[F[_]](
    pipelineName: String,
    stages: List[ExecutableStage[F]],
    tracer: Option[Tracer[F]],
    emitter: OpenLineageEmitter[F],
    input: Any,
  )(implicit F: EffectSystem[F],
  ): F[Any] = {
    // Both reads are effects: generateRunId reads the environment and otherwise draws a random id.
    val context = F.delay(
      RunContext(
        emitter,
        OpenLineageEmitter.generateRunId(pipelineName),
        sys.env.getOrElse("OPENLINEAGE_NAMESPACE", DefaultNamespace),
      ),
    )
    F.flatMap(context) { ctx =>
      around(ctx, pipelineName, ctx.pipelineRunId, chain(stages, tracer, Some(ctx)).run(input))
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

  /**
   * Wrap one stage. The span and the events cover this stage and nothing before it.
   *
   * The stage gets its own run id. In OpenLineage a run is one execution of one job, so a run id that is
   * attached to the pipeline job and to every stage job describes a run that belongs to several jobs at once,
   * and a backend reading those events cannot tell which job the run is. The link back to the pipeline run
   * belongs in a parent run facet, which the current `emitJobStart(namespace, jobName, runId)` signature
   * cannot carry; adding it means changing the emitter interface, which is left out of this fix.
   */
  private def observe[F[_]](
    stage: ExecutableStage[F],
    tracer: Option[Tracer[F]],
    lineage: Option[RunContext[F]],
  )(implicit F: EffectSystem[F],
  ): Kleisli[F, Any, Any] =
    Kleisli[F, Any, Any] { in =>
      val body   = stage.run.run(in)
      val traced = tracer.fold(body)(t => t.inSpan(stage.name)(body))
      lineage.fold(traced) { ctx =>
        // Random rather than OpenLineageEmitter.generateRunId: an orchestrator supplied id names the
        // pipeline run, so handing it to a stage would recreate the one-id-many-jobs problem.
        F.flatMap(F.delay(java.util.UUID.randomUUID().toString))(around(ctx, stage.name, _, traced))
      }
    }

  /** START before, COMPLETE on success, FAIL on error. The original error is always re-raised. */
  private def around[F[_], A](
    ctx: RunContext[F],
    jobName: String,
    runId: String,
    fa: F[A],
  )(implicit F: EffectSystem[F],
  ): F[A] =
    F.flatMap(emit(ctx.emitter.emitJobStart(ctx.namespace, jobName, runId))) { _ =>
      F.flatMap(F.attempt(fa)) {
        case Right(a) =>
          F.map(emit(ctx.emitter.emitJobComplete(ctx.namespace, jobName, runId)))(_ => a)
        case Left(e) =>
          F.flatMap(emit(ctx.emitter.emitJobFail(ctx.namespace, jobName, runId, e.getMessage)))(_ =>
            F.raiseError[A](e),
          )
      }
    }

  /**
   * Discard the outcome of an emission.
   *
   * Lineage describes the pipeline, it is not on the data path, so a lineage backend being down is not a
   * reason to fail a production run. The emitters report their own failures: `HttpOpenLineageEmitter` logs
   * and returns `Left`.
   *
   * The argument is by name and is built under `suspend` because an emitter is supplied by the caller and is
   * not trusted to honour its signature. An emitter that throws on the way to returning its effect would
   * otherwise throw while the argument was being evaluated, which is before `attempt` can see it.
   */
  private def emit[F[_]](
    fa: => F[Either[LineageError, Unit]],
  )(implicit F: EffectSystem[F],
  ): F[Unit] =
    F.map(F.attempt(F.suspend(fa)))(_ => ())
}
