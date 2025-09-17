package com.flowforge.connectors.capabilities

sealed trait SupportsMerge
sealed trait SupportsBatch

/**
 * Abstract sink handle parameterized by record type `R` and capability tag `Caps`. Engine-specific
 * implementations wrap their concrete sink object in `impl`.
 */
final case class Sink[R, Caps, Handle](handle: Handle)

/** Type class describing how to write engine-specific datasets `A` into a sink. */
trait CanWrite[A, R, Caps, Handle] {
  def write(s: Sink[R, Caps, Handle], ds: A): Unit
}

object Writes {

  /** Merge-capable write routed via a type class instance provided by the engine module. */
  def writeMerge[A, R, Handle](
    s: Sink[R, SupportsMerge, Handle],
    ds: A,
  )(implicit W: CanWrite[A, R, SupportsMerge, Handle],
  ): Unit =
    W.write(s, ds)

  /** Batch-capable write routed via a type class instance provided by the engine module. */
  def writeBatch[A, R, Handle](
    s: Sink[R, SupportsBatch, Handle],
    ds: A,
  )(implicit W: CanWrite[A, R, SupportsBatch, Handle],
  ): Unit =
    W.write(s, ds)
}
