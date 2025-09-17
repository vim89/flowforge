package com.flowforge.compilefail

import org.scalatest.funsuite.AnyFunSuite

/**
 * Realistic Spark-style pipelines demonstrating compile-time failures per SchemaPolicy.
 * Each test uses PipelineBuilder with typed endpoints so the compiler must materialize
 * SchemaConforms evidence, triggering compile-time validation.
 */
class SparkPolicyFailuresSpec extends AnyFunSuite {
  test("Exact: extra output field fails") {
    assertTypeError(
      """
         |import cats.effect.IO
         |import com.flowforge.core.PipelineBuilder
         |import com.flowforge.core.contracts.SchemaPolicy
         |import com.flowforge.contracts.{ TypedSink, TypedSource }
         |import com.flowforge.core.types.{ DataFormat, DataSink, DataSource }
         |
         |case class Contract(id: Long, name: String)
         |case class Producer(id: Long, name: String, age: Int)
         |
         |val src  = TypedSource[Contract](DataSource.local("/tmp/s1", DataFormat.Parquet))
         |val sink = TypedSink[Contract](DataSink.local("/tmp/d1", DataFormat.Parquet))
         |
         |PipelineBuilder[IO]("exact-extra")
         |  .addTypedSource[Producer, Contract, SchemaPolicy.Exact](src, _ => IO.pure(Producer(1L, "a", 42)))
         |  .noTransform
         |  .addTypedSink[Contract, SchemaPolicy.Exact](sink, (_, _) => IO.unit)
         |""".stripMargin)
  }

  test("ExactUnordered: type mismatch still fails (names equal, order irrelevant)") {
    assertTypeError(
      """
         |import cats.effect.IO
         |import com.flowforge.core.PipelineBuilder
         |import com.flowforge.core.contracts.SchemaPolicy
         |import com.flowforge.contracts.{ TypedSink, TypedSource }
         |import com.flowforge.core.types.{ DataFormat, DataSink, DataSource }
         |
         |case class Contract(id: Long, name: String)
         |case class Producer(id: Long, name: Int)
         |
         |val src  = TypedSource[Contract](DataSource.local("/tmp/s2", DataFormat.Parquet))
         |val sink = TypedSink[Contract](DataSink.local("/tmp/d2", DataFormat.Parquet))
         |
         |PipelineBuilder[IO]("exact-unordered-type-mismatch")
         |  .addTypedSource[Producer, Contract, SchemaPolicy.ExactUnordered](src, _ => IO.pure(Producer(1L, 10)))
         |  .noTransform
         |  .addTypedSink[Contract, SchemaPolicy.ExactUnordered](sink, (_, _) => IO.unit)
         |""".stripMargin)
  }

  test("ExactUnorderedCI: type mismatch fails even if names differ by case") {
    assertTypeError(
      """
         |import cats.effect.IO
         |import com.flowforge.core.PipelineBuilder
         |import com.flowforge.core.contracts.SchemaPolicy
         |import com.flowforge.contracts.{ TypedSink, TypedSource }
         |import com.flowforge.core.types.{ DataFormat, DataSink, DataSource }
         |
         |case class Contract(ID: Long, NAME: String)
         |case class Producer(id: Long, name: Int)
         |
         |val src  = TypedSource[Contract](DataSource.local("/tmp/s3", DataFormat.Parquet))
         |val sink = TypedSink[Contract](DataSink.local("/tmp/d3", DataFormat.Parquet))
         |
         |PipelineBuilder[IO]("exact-unordered-ci-type-mismatch")
         |  .addTypedSource[Producer, Contract, SchemaPolicy.ExactUnorderedCI](src, _ => IO.pure(Producer(1L, 10)))
         |  .noTransform
         |  .addTypedSink[Contract, SchemaPolicy.ExactUnorderedCI](sink, (_, _) => IO.unit)
         |""".stripMargin)
  }

  test("ExactOrdered: order mismatch fails") {
    assertTypeError(
      """
         |import cats.effect.IO
         |import com.flowforge.core.PipelineBuilder
         |import com.flowforge.core.contracts.SchemaPolicy
         |import com.flowforge.contracts.{ TypedSink, TypedSource }
         |import com.flowforge.core.types.{ DataFormat, DataSink, DataSource }
         |
         |case class Contract(a: Int, b: String)
         |case class Producer(b: String, a: Int)
         |
         |val src  = TypedSource[Contract](DataSource.local("/tmp/s4", DataFormat.Parquet))
         |val sink = TypedSink[Contract](DataSink.local("/tmp/d4", DataFormat.Parquet))
         |
         |PipelineBuilder[IO]("exact-ordered-order-mismatch")
         |  .addTypedSource[Producer, Contract, SchemaPolicy.ExactOrdered](src, _ => IO.pure(Producer("x", 1)))
         |  .noTransform
         |  .addTypedSink[Contract, SchemaPolicy.ExactOrdered](sink, (_, _) => IO.unit)
         |""".stripMargin)
  }

  test("ExactOrderedCI: order mismatch fails even if names differ by case") {
    assertTypeError(
      """
         |import cats.effect.IO
         |import com.flowforge.core.PipelineBuilder
         |import com.flowforge.core.contracts.SchemaPolicy
         |import com.flowforge.contracts.{ TypedSink, TypedSource }
         |import com.flowforge.core.types.{ DataFormat, DataSink, DataSource }
         |
         |case class Contract(A: Int, B: String)
         |case class Producer(b: String, a: Int)
         |
         |val src  = TypedSource[Contract](DataSource.local("/tmp/s5", DataFormat.Parquet))
         |val sink = TypedSink[Contract](DataSink.local("/tmp/d5", DataFormat.Parquet))
         |
         |PipelineBuilder[IO]("exact-ordered-ci-order-mismatch")
         |  .addTypedSource[Producer, Contract, SchemaPolicy.ExactOrderedCI](src, _ => IO.pure(Producer("x", 1)))
         |  .noTransform
         |  .addTypedSink[Contract, SchemaPolicy.ExactOrderedCI](sink, (_, _) => IO.unit)
         |""".stripMargin)
  }

  test("ExactByPosition: type mismatch at position fails") {
    assertTypeError(
      """
         |import cats.effect.IO
         |import com.flowforge.core.PipelineBuilder
         |import com.flowforge.core.contracts.SchemaPolicy
         |import com.flowforge.contracts.{ TypedSink, TypedSource }
         |import com.flowforge.core.types.{ DataFormat, DataSink, DataSource }
         |
         |case class Contract(a: Long, b: String)
         |case class Producer(a: String, b: Long)
         |
         |val src  = TypedSource[Contract](DataSource.local("/tmp/s6", DataFormat.Parquet))
         |val sink = TypedSink[Contract](DataSink.local("/tmp/d6", DataFormat.Parquet))
         |
         |PipelineBuilder[IO]("by-position-type-mismatch")
         |  .addTypedSource[Producer, Contract, SchemaPolicy.ExactByPosition](src, _ => IO.pure(Producer("1", 2L)))
         |  .noTransform
         |  .addTypedSink[Contract, SchemaPolicy.ExactByPosition](sink, (_, _) => IO.unit)
         |""".stripMargin)
  }

  test("Backward: missing required contract field fails") {
    assertTypeError(
      """
         |import cats.effect.IO
         |import com.flowforge.core.PipelineBuilder
         |import com.flowforge.core.contracts.SchemaPolicy
         |import com.flowforge.contracts.{ TypedSink, TypedSource }
         |import com.flowforge.core.types.{ DataFormat, DataSink, DataSource }
         |
         |case class Contract(id: Long, name: String, email: String)
         |case class Producer(id: Long, name: String) // missing email, not optional in contract
         |
         |val src  = TypedSource[Contract](DataSource.local("/tmp/s7", DataFormat.Parquet))
         |val sink = TypedSink[Contract](DataSink.local("/tmp/d7", DataFormat.Parquet))
         |
         |PipelineBuilder[IO]("backward-missing-required")
         |  .addTypedSource[Producer, Contract, SchemaPolicy.Backward](src, _ => IO.pure(Producer(1L, "x")))
         |  .noTransform
         |  .addTypedSink[Contract, SchemaPolicy.Backward](sink, (_, _) => IO.unit)
         |""".stripMargin)
  }

  test("Forward: extra output fields fail") {
    assertTypeError(
      """
         |import cats.effect.IO
         |import com.flowforge.core.PipelineBuilder
         |import com.flowforge.core.contracts.SchemaPolicy
         |import com.flowforge.contracts.{ TypedSink, TypedSource }
         |import com.flowforge.core.types.{ DataFormat, DataSink, DataSource }
         |
         |case class Contract(id: Long, name: String)
         |case class Producer(id: Long, name: String, age: Int) // extra field not allowed in Forward
         |
         |val src  = TypedSource[Contract](DataSource.local("/tmp/s8", DataFormat.Parquet))
         |val sink = TypedSink[Contract](DataSink.local("/tmp/d8", DataFormat.Parquet))
         |
         |PipelineBuilder[IO]("forward-extra-fails")
         |  .addTypedSource[Producer, Contract, SchemaPolicy.Forward](src, _ => IO.pure(Producer(1L, "x", 10)))
         |  .noTransform
         |  .addTypedSink[Contract, SchemaPolicy.Forward](sink, (_, _) => IO.unit)
         |""".stripMargin)
  }

  test("Full: always compiles (no check)") {
    assertCompiles(
      """
         |import cats.effect.IO
         |import com.flowforge.core.PipelineBuilder
         |import com.flowforge.core.contracts.SchemaPolicy
         |import com.flowforge.core.instances.EffectInstances._
         |import com.flowforge.contracts.{ TypedSink, TypedSource }
         |import com.flowforge.core.types.{ DataFormat, DataSink, DataSource }
         |
         |case class Contract(id: Long)
         |case class Producer(a: String, b: Int)
         |
         |val src  = TypedSource[Contract](DataSource.local("/tmp/s9", DataFormat.Parquet))
         |val sink = TypedSink[Contract](DataSink.local("/tmp/d9", DataFormat.Parquet))
         |
         |val b = PipelineBuilder[IO]("full-always")
         |  .addTypedSource[Producer, Contract, SchemaPolicy.Full](src, _ => IO.pure(Producer("x", 1)))
         |  .noTransform
         |  .addTypedSink[Contract, SchemaPolicy.Full](sink, (_, _) => IO.unit)
         |""".stripMargin)
  }
}
