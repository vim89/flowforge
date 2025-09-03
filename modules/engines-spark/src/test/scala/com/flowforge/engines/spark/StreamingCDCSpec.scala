package com.flowforge.engines.spark

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.data.NonEmptyList
import org.scalatest.funsuite.AnyFunSuite
import com.flowforge.core.algebra.{ CDCOperations, DataAlgebra, DataContract, EffectSystem }
import com.flowforge.core.impl.SimpleDataset
import com.flowforge.core.instances.{ DataInstances, EffectInstances }
import com.flowforge.core.types.DataSchema
import com.flowforge.core.types.RefinedTypes.{ FieldName, SchemaVersion }
import java.time.Instant

final case class Rec(id: Int, v: String)

class StreamingCDCSpec extends AnyFunSuite {

  private val algebra: DataAlgebra[IO] = {
    implicit val effectSystem: EffectSystem[IO] = EffectInstances.catsEffectSystemInstance
    DataInstances.createMockDataAlgebra[IO]
  }

  private def ds(data: List[Rec]) = {
    val schema = DataSchema(Nil, SchemaVersion.unsafeFrom(1), Map.empty, Instant.now())
    val meta = DataAlgebra.DatasetMetadata(
      recordCount = data.size.toLong,
      schema = schema,
      partitions = 1,
      createdAt = Instant.now(),
      source = None
    )
    SimpleDataset(data, schema, meta)
  }

  test("performDeltaStreamed aggregates micro-batch results") {
    import StreamingCDC._
    implicit val dc: DataContract[Rec] = DataContract.empty[Rec]
    
    val batches = List(ds(List(Rec(1, "a"))), ds(List(Rec(1, "b"), Rec(2, "x"))))
    val target  = ds(Nil)
    val cfg     = CDCOperations.CDCConfig(keyColumns = NonEmptyList.one(FieldName.unsafeFrom("id")))

    val res = performDeltaStreamed[IO, Rec](algebra, batches, target, cfg).unsafeRunSync()
    assert(res.inserted >= 1)
    assert(res.success)
  }
}
