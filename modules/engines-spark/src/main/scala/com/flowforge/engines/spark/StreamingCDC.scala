package com.flowforge.engines.spark

import cats.syntax.all._
import com.flowforge.core.algebra.{ CDCOperations, DataAlgebra, DataContract, EffectSystem }

/**
 * Lightweight helper to apply CDC over a sequence of micro-batches, emulating a streaming pipeline by
 * repeatedly invoking performDelta and aggregating results.
 */
object StreamingCDC {

  def performDeltaStreamed[F[_]: EffectSystem, A: DataContract](
    algebra: DataAlgebra[F],
    batches: List[DataAlgebra.Dataset[A]],
    target: DataAlgebra.Dataset[A],
    config: CDCOperations.CDCConfig,
  ): F[CDCOperations.CDCResult[A]] = {
    val F = EffectSystem[F]
    batches
      .foldLeft(F.pure((0L, 0L, 0L, 0L, 0L))) { (accF, batch) =>
        for {
          acc <- accF
          res <- algebra.performDelta(batch, target, config)
        } yield {
          val (inserted: Long, updated: Long, deleted: Long, unchanged: Long, errors: Long) = acc
          (
            inserted + res.inserted,
            updated + res.updated,
            deleted + res.deleted,
            unchanged + res.unchanged,
            errors + res.errors,
          )
        }
      }
      .map {
        case (i: Long, u: Long, d: Long, n: Long, e: Long) =>
          CDCOperations.CDCResult(
            inserted = i,
            updated = u,
            deleted = d,
            unchanged = n,
            errors = e,
            processingTime = scala.concurrent.duration.Duration.Zero,
            success = true,
          )
      }
  }
}
