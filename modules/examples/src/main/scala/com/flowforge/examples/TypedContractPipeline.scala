package com.flowforge.examples

import cats.effect.IO
import com.flowforge.contracts.sales.TransactionsV1
import com.flowforge.core.PipelineBuilder
import com.flowforge.core.algebra.EffectSystem
import com.flowforge.core.instances.EffectInstances
import com.flowforge.core.types._

/**
 * TYPED CONTRACT PIPELINE - Updated to use FlowForge's compile-time contract guarantees
 *
 * This example demonstrates contract-first pipeline development using generated contracts from the
 * contracts-sdk module.
 *
 * Key improvements:
 *   - Uses contract-enforced build methods instead of deprecated build()
 *   - Demonstrates compile-time schema validation
 *   - Shows proper error handling with beautiful messages
 */
object TypedContractPipeline {
  implicit val F: EffectSystem[IO] = EffectInstances.catsEffectSystemInstance

  def buildContractEnforced(): com.flowforge.core.types.Pipeline[IO, Unit, TransactionsV1] =
    PipelineBuilder[IO]("typed-sales-pipeline")
      .withDescription("Contract-enforced sales pipeline using generated contracts")
      .addTransform[TransactionsV1](_ =>
        F.pure(TransactionsV1("INV-1", "C-1", 123.45, System.currentTimeMillis())),
      )
      .addTransform[TransactionsV1](x => F.pure(x.copy(amount = x.amount * 1.00)))
      // ✅ Contract enforcement: pipeline output must match TransactionsV1 contract
      .buildWithExactContract[TransactionsV1] // Compiles because schemas match!

  // Legacy method for backwards compatibility - deprecated
  @deprecated("Use buildContractEnforced() for compile-time contract validation", "0.1.0")
  def build(): com.flowforge.core.types.Pipeline[IO, Unit, TransactionsV1] =
    buildContractEnforced()
}
