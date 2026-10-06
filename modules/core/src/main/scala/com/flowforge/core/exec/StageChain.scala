package com.flowforge.core.exec

import cats.data.Kleisli
import com.flowforge.core.algebra.EffectSystem

/**
 * What a stage is for, as far as anything outside the stage can tell.
 *
 * Every builder reported its own stage counts its own way: one read a `isTransform` field, two used
 * `isInstanceOf`, two used `collect`, and two just reported the stage count. Five answers to one question is
 * five chances to disagree. A builder now says what kind a stage is when it appends it, and the counts come
 * from that.
 */
sealed abstract class StageKind extends Product with Serializable

object StageKind {

  /** Reads from outside the pipeline, so it takes no input. */
  case object Source extends StageKind

  /** Turns the previous stage's output into the next stage's input. */
  case object Transform extends StageKind

  /** Checks the data against a contract and passes it through. */
  case object Quality extends StageKind

  /** Writes to outside the pipeline, so it produces no value. */
  case object Sink extends StageKind
}

/**
 * The stages of a pipeline, in order, holding the type chain that runs through them.
 *
 * A `List[Kleisli[F, ?, ?]]` cannot hold this. A list has one element type, and a pipeline's stages do not:
 * stage one is `A => B` and stage two is `B => C`. The old composer erased every stage to
 * `Kleisli[F, Any, Any]`, folded the list, and cast the result back to the types the builder claimed. Erasure
 * makes that cast silent, so a builder whose types did not line up produced an arrow that handed one stage's
 * output to a stage expecting something else, with no cast error to say so.
 *
 * This type is the same sequence with the links kept. `Link` holds the type between two stages as its own
 * parameter, so appending a stage that does not read the previous stage's output does not compile and there
 * is nothing left to cast.
 *
 * The trade: `Mid` is hidden inside `Link`, so the chain can be folded but not re-indexed or reordered after
 * it is built. That is what a type-aligned sequence costs in Scala 2.13, and nothing here reorders stages
 * after building.
 */
sealed abstract class StageChain[F[_], In, Out] extends Product with Serializable {

  /**
   * Each stage's name and kind, most recently appended first.
   *
   * Reversed because that is the order a cons list can be extended in without copying. Appending with `:+`
   * copies every node before it, so a chain of n stages would hold n lists of average length n/2 rather than
   * sharing one spine, and a pipeline whose stages are generated rather than written out would pay for it.
   * Only `names` needs the running order, and only once per build.
   */
  private[exec] def entriesReversed: List[StageChain.Entry]

  /** How many stages the chain holds. Held per link, because a builder reads it on every append. */
  def size: Int

  /** The stage names, in the order they run. */
  final def names: List[String] = entriesReversed.reverseIterator.map(_.name).toList

  /** How many stages of one kind the chain holds. */
  final def count(kind: StageKind): Int = entriesReversed.count(_.kind == kind)

  /** Append a stage. It has to read what the chain currently produces. */
  final def andThen[Next](
    stageName: String,
    kind: StageKind,
    stage: Kleisli[F, Out, Next],
  ): StageChain[F, In, Next] =
    StageChain.Link(this, StageChain.Entry(stageName, kind), stage)

  /**
   * The arrow that runs the whole chain, with `decorate` applied to each stage.
   *
   * `decorate` is applied at each stage's own input and output types rather than at `Any`, which is what
   * removes the need to erase. See [[StageDecorator]].
   *
   * Building the arrow descends one frame per stage, so the chain's depth is bounded by the JVM stack: 10k
   * stages build and run, 50k overflow. A left fold would not have that bound, but folding needs the chain
   * reversed, and reversing a type-aligned sequence needs the same cast this type exists to remove. The bound
   * is the one being accepted, and 10k is far past a pipeline anyone would run: every stage carries a span
   * and a START/COMPLETE pair, so a chain near the bound is unusable for reasons that have nothing to do with
   * the stack. [[StageChainSpec]] pins 10k so the bound cannot quietly drop.
   */
  private[exec] def arrow(decorate: StageDecorator[F])(implicit F: EffectSystem[F]): Kleisli[F, In, Out]
}

object StageChain {

  /** A chain with no stages. Its arrow is the identity, which is the honest answer for no stages. */
  def empty[F[_], A]: StageChain[F, A, A] = Identity[F, A]()

  private[exec] final case class Entry(name: String, kind: StageKind)

  /**
   * `arrow` is an abstract method each case overrides rather than a match on the two cases.
   *
   * A match would have to recover `In =:= Out` for `Identity` and the hidden `Mid` for `Link` from the
   * scrutinee's type, which is GADT refinement. Scala 2.13 does that unreliably. Inside a case's own body its
   * type parameters are already in scope, so there is nothing to infer and the same source compiles on 2.13
   * and 3.3.
   */
  private final case class Identity[F[_], A]() extends StageChain[F, A, A] {

    private[exec] def entriesReversed: List[Entry] = Nil

    val size: Int = 0

    private[exec] def arrow(
      decorate: StageDecorator[F],
    )(implicit F: EffectSystem[F],
    ): Kleisli[F, A, A] = Kleisli((a: A) => F.pure(a))
  }

  private final case class Link[F[_], In, Mid, Out](
    init: StageChain[F, In, Mid],
    entry: Entry,
    stage: Kleisli[F, Mid, Out])
      extends StageChain[F, In, Out] {

    // Both are computed once per append and share the preceding link's spine, so appending is constant
    // time and the whole chain holds one list. The next stage's name is derived from `size`, so a builder
    // reads it on every append.
    private[exec] val entriesReversed: List[Entry] = entry :: init.entriesReversed

    val size: Int = init.size + 1

    private[exec] def arrow(
      decorate: StageDecorator[F],
    )(implicit F: EffectSystem[F],
    ): Kleisli[F, In, Out] = init.arrow(decorate).andThen(decorate(entry.name, stage))
  }
}

/**
 * Per-stage wrapping, applied at the stage's own types.
 *
 * The type parameters are on `apply` rather than on the trait on purpose. A decorator typed
 * `Kleisli[F, Any, Any] => Kleisli[F, Any, Any]` is what forced the composer to erase its stages in the first
 * place. One that can be applied at whatever types a stage happens to have does not.
 */
private[exec] trait StageDecorator[F[_]] {
  def apply[A, B](stageName: String, stage: Kleisli[F, A, B]): Kleisli[F, A, B]
}
