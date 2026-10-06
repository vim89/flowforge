package com.flowforge.core.exec

import cats.data.Kleisli
import com.flowforge.core.algebra.EffectSystem
import com.flowforge.core.lineage.{ LineageError, OpenLineageEmitter }
import com.flowforge.core.observability.Tracer

/**
 * Composes pipeline stages into a single arrow and decorates each stage with tracing and lineage.
 *
 * This is the only place in core that answers "how is a chain of stages run". `PipelineBuilder.build` and the
 * in-memory builders each used to answer it separately. They disagreed, and each one was missing something
 * the others had.
 *
 * It holds no casts. The stages arrive as a [[StageChain]], which carries the type between each pair of
 * stages, so the arrow this builds has the caller's `In` and `Out` because the chain does, not because it was
 * told to.
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
    stages: StageChain[F, In, Out],
    tracer: Option[Tracer[F]] = None,
    lineage: Option[OpenLineageEmitter[F]] = None,
  )(implicit F: EffectSystem[F],
  ): Kleisli[F, In, Out] =
    Kleisli[F, In, Out] { input =>
      lineage match {
        case None          => stages.arrow(decorator(tracer, None)).run(input)
        case Some(emitter) => runTracked(pipelineName, stages, tracer, emitter, input)
      }
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

  private def runTracked[F[_], In, Out](
    pipelineName: String,
    stages: StageChain[F, In, Out],
    tracer: Option[Tracer[F]],
    emitter: OpenLineageEmitter[F],
    input: In,
  )(implicit F: EffectSystem[F],
  ): F[Out] = {
    // Both reads are effects: generateRunId reads the environment and otherwise draws a random id.
    val context = F.delay(
      RunContext(
        emitter,
        OpenLineageEmitter.generateRunId(pipelineName),
        sys.env.getOrElse("OPENLINEAGE_NAMESPACE", DefaultNamespace),
      ),
    )
    F.flatMap(context) { ctx =>
      around(
        ctx,
        pipelineName,
        ctx.pipelineRunId,
        stages.arrow(decorator(tracer, Some(ctx))).run(input),
      )
    }
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
  private def decorator[F[_]](
    tracer: Option[Tracer[F]],
    lineage: Option[RunContext[F]],
  )(implicit F: EffectSystem[F],
  ): StageDecorator[F] =
    new StageDecorator[F] {
      def apply[A, B](stageName: String, stage: Kleisli[F, A, B]): Kleisli[F, A, B] =
        Kleisli[F, A, B] { in =>
          val body   = stage.run(in)
          val traced = tracer.fold(body)(t => t.inSpan(stageName)(body))
          lineage.fold(traced) { ctx =>
            // Random rather than OpenLineageEmitter.generateRunId: an orchestrator supplied id names the
            // pipeline run, so handing it to a stage would recreate the one-id-many-jobs problem.
            F.flatMap(F.delay(java.util.UUID.randomUUID().toString))(around(ctx, stageName, _, traced))
          }
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
