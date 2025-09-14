package com.flowforge.core.lineage

import com.flowforge.core.algebra.EffectSystem

import java.util.concurrent.ArrayBlockingQueue
import scala.util.Random

final class AsyncOpenLineageEmitter[F[_]: EffectSystem](underlying: OpenLineageEmitter[F], capacity: Int = 1024)
    extends OpenLineageEmitter[F] {

  private val F  = EffectSystem[F]
  private val q  = new ArrayBlockingQueue[() => F[Either[LineageError, Unit]]](capacity)
  private val rnd = new Random()

  // Start single consumer fiber
  private val _fiber = EffectSystem[F].start(loop)

  private def loop: F[Unit] =
    F.flatMap(F.blocking(q.take())) { thunk =>
      // retry with exponential backoff + jitter on Left
      def attempt(retries: Int, delayMs: Long): F[Unit] =
        F.flatMap(thunk()) {
          case Right(_) => F.unit
          case Left(_) if retries > 0 =>
            val jitter = new Random().nextInt(250).toLong
            F.flatMap(F.sleep(scala.concurrent.duration.Duration(delayMs + jitter, scala.concurrent.duration.MILLISECONDS))) {
              _ => attempt(retries - 1, math.min(delayMs * 2, 5000L))
            }
          case Left(_) => F.unit // give up silently; best-effort semantics
        }
      attempt(retries = 5, delayMs = 250)
    } match {
      case x => F.flatMap(x)(_ => loop)
    }

  private def enqueue(thunk: => F[Either[LineageError, Unit]]): F[Either[LineageError, Unit]] =
    F.map(F.blocking(q.offer(() => thunk))) { ok => if (ok) Right(()) else Left(LineageError("lineage queue full")) }

  def emitJobStart(namespace: String, jobName: String, runId: String): F[Either[LineageError, Unit]] =
    enqueue(underlying.emitJobStart(namespace, jobName, runId))

  def emitJobComplete(namespace: String, jobName: String, runId: String): F[Either[LineageError, Unit]] =
    enqueue(underlying.emitJobComplete(namespace, jobName, runId))

  def emitJobFail(namespace: String, jobName: String, runId: String, error: String): F[Either[LineageError, Unit]] =
    enqueue(underlying.emitJobFail(namespace, jobName, runId, error))
}
