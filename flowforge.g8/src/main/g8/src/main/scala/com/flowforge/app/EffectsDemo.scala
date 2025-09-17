package com.flowforge.app

import com.flowforge.core.algebra.EffectSystem

object EffectsDemo {

  /**
    * Demonstrate FlowForge's polymorphic EffectSystem:
    * - parallel computation via parTraverse
    * - resource safety via bracket
    */
  def demo[F[_]](implicit F: EffectSystem[F]): F[(Int, Boolean)] = {
    val closed = new java.util.concurrent.atomic.AtomicBoolean(false)

    val use = F.map(F.parTraverse((1 to 5).toList)(i => F.delay(i * i)))(_.sum)

    F.flatMap(F.bracket(F.pure(()))(_ => use)(_ => F.delay { closed.set(true); () })) { sum =>
      F.map(F.delay(closed.get()))(closedNow => (sum, closedNow))
    }
  }
}
