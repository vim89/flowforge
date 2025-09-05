package com.flowforge.examples

import cats.effect.IO
import com.flowforge.contracts.sales.TransactionsV1
import com.flowforge.core.algebra.EffectSystem
import com.flowforge.core.instances.EffectInstances
import com.flowforge.core.types._

object TypedContractPipeline {
  implicit val F: EffectSystem[IO] = EffectInstances.catsEffectSystemInstance

  def build(): com.flowforge.core.types.Pipeline[IO, Unit, Unit] =
    PipelineBuilder2[IO]("typed-sales-pipeline")
      .addTypedSource(
        DataSource.gcs("demo-bucket", "raw/sales/", DataFormat.Parquet),
        _ => F.pure(TransactionsV1("INV-1", "C-1", 123.45, System.currentTimeMillis())),
      )
      .addTransform[TransactionsV1](x => F.pure(x.copy(amount = x.amount * 1.00)))
      .addTypedSink(
        DataSink.gcs("demo-bucket", "curated/sales/", DataFormat.Parquet),
        (out, ds) => F.delay(println(s"Would write $out to $ds")),
      )
      .build()
}
