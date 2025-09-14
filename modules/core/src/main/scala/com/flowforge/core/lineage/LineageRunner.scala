package com.flowforge.core.lineage

import cats.data.Kleisli
import com.flowforge.core.algebra.EffectSystem
import com.flowforge.core.types.PipelineStage

object LineageRunner {
  /**
    * Execute a list of stages with stage-level START/COMPLETE/FAIL events using the provided emitter.
    */
  def runWithEmitter[F[_], A, B](
    stages: List[PipelineStage[F, _, _]],
    emitter: OpenLineageEmitter[F],
  )(input: A)(implicit F: EffectSystem[F]): F[B] = {
    def kAny(k: Kleisli[F, _, _]) = k.asInstanceOf[Kleisli[F, Any, Any]]

    def loop(idx: Int, in: Any, rem: List[PipelineStage[F, _, _]]): F[Any] = rem match {
      case Nil => F.pure(in)
      case st :: tail =>
        F.flatMap(emitter.emitJobStart("flowforge", s"stage-$idx", java.util.UUID.randomUUID.toString)) { _ =>
          F.flatMap(F.attempt(kAny(st.execute).run(in))) {
            case Right(out) =>
              F.flatMap(emitter.emitJobComplete("flowforge", s"stage-$idx", "run"))(_ => loop(idx + 1, out, tail))
            case Left(e) =>
              F.flatMap(emitter.emitJobFail("flowforge", s"stage-$idx", "run", e.getMessage))(_ => F.raiseError(e))
          }
        }
    }
    F.map(loop(0, input, stages))(_.asInstanceOf[B])
  }
}

