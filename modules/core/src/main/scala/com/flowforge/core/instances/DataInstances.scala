package com.flowforge.core.instances

import com.flowforge.core.algebra.{ DataAlgebra, EffectSystem }

/**
 * Centralized factory accessors for default FlowForge instances.
 */
object DataInstances {

  /**
   * Create a working, effect-separated in-memory DataAlgebra foundation.
   */
  def createMockDataAlgebra[F[_]: EffectSystem]: DataAlgebra[F] =
    new com.flowforge.core.impl.InMemoryDataAlgebra[F]
}
