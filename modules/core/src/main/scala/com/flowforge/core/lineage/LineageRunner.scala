package com.flowforge.core.lineage

import com.flowforge.core.algebra.EffectSystem
import com.flowforge.core.exec.{ ExecutableStage, StageComposer }
import com.flowforge.core.types.PipelineStage

object LineageRunner {

  /**
   * Execute a list of stages with stage-level START/COMPLETE/FAIL events using the provided emitter.
   *
   * This now delegates to `StageComposer`, which is also what `PipelineBuilder.build` uses. The previous
   * implementation was a second fold over the stage list and disagreed with the builder's: it named jobs
   * `stage-0`, `stage-1` instead of using the stage name, generated a fresh run id per stage so START and
   * COMPLETE never shared one, and passed the literal `"run"` as the run id to complete and fail.
   */
  def runWithEmitter[F[_], A, B](
    stages: List[PipelineStage[F, _, _]],
    emitter: OpenLineageEmitter[F],
  )(
    input: A,
  )(implicit F: EffectSystem[F],
  ): F[B] =
    StageComposer
      .compose[F, A, B](
        pipelineName = "flowforge",
        stages = stages.map(st => ExecutableStage[F](st.name, st.execute)),
        lineage = Some(emitter),
      )
      .run(input)
}
