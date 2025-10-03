// scalafix:off DisableSyntax.var DisableSyntax.throw DisableSyntax.null DisableSyntax.noUnsafeRunSync
package com.flowforge.compilefail

import org.scalatest.funsuite.AnyFunSuite

/**
 * Compile-time contract failures for realistic Spark-style pipelines. These use ScalaTest's assertTypeError
 * to ensure code snippets do not typecheck (i.e., fail at compile time due to missing SchemaConforms
 * evidence).
 */
class SparkContractCompileFailSpec extends AnyFunSuite {

  test("Exact policy fails when pipeline output has extra field not in contract") {
    assertTypeError(
      """
        |import cats.effect.IO
        |import com.flowforge.core.PipelineBuilder
        |import com.flowforge.core.contracts.SchemaPolicy
        |import com.flowforge.contracts.{ TypedSink, TypedSource }
        |import com.flowforge.core.types.{ DataFormat, DataSink, DataSource }
        |
        |case class User(id: Long, email: String)
        |case class UserWithAge(id: Long, email: String, age: Int)
        |
        |val src  = TypedSource[User](DataSource.local("/tmp/src", DataFormat.Parquet))
        |val sink = TypedSink[User](DataSink.local("/tmp/sink", DataFormat.Parquet))
        |
        |val builder = PipelineBuilder[IO]("extra-field-disallowed")
        |  .addTypedSource[UserWithAge, User, SchemaPolicy.Exact](src, _ => IO.pure(UserWithAge(1L, "a@b.com", 42)))
        |  .noTransform
        |  .addTypedSink[User, SchemaPolicy.Exact](sink, (_, _) => IO.pure(()))
        |""".stripMargin,
    )
  }

  test("Backward policy fails when pipeline output changes field type") {
    assertTypeError(
      """
        |import cats.effect.IO
        |import com.flowforge.core.PipelineBuilder
        |import com.flowforge.core.contracts.SchemaPolicy
        |import com.flowforge.contracts.{ TypedSink, TypedSource }
        |import com.flowforge.core.types.{ DataFormat, DataSink, DataSource }
        |
        |case class Contract(id: Long, name: String)
        |case class ProducerWrongType(id: Long, name: Int) // type drift on 'name'
        |
        |val src  = TypedSource[Contract](DataSource.local("/tmp/src2", DataFormat.Parquet))
        |val sink = TypedSink[Contract](DataSink.local("/tmp/sink2", DataFormat.Parquet))
        |
        |val builder = PipelineBuilder[IO]("type-drift-backward")
        |  .addTypedSource[ProducerWrongType, Contract, SchemaPolicy.Backward](src, _ => IO.pure(ProducerWrongType(1L, 10)))
        |  .noTransform
        |  .addTypedSink[Contract, SchemaPolicy.Backward](sink, (_, _) => IO.pure(()))
        |""".stripMargin,
    )
  }

  test("ExactOrdered policy fails when field order differs") {
    assertTypeError(
      """
        |import cats.effect.IO
        |import com.flowforge.core.PipelineBuilder
        |import com.flowforge.core.contracts.SchemaPolicy
        |import com.flowforge.contracts.{ TypedSink, TypedSource }
        |import com.flowforge.core.types.{ DataFormat, DataSink, DataSource }
        |
        |case class ContractOrdered(a: Int, b: String)
        |case class ProducerReordered(b: String, a: Int)
        |
        |val src  = TypedSource[ContractOrdered](DataSource.local("/tmp/src3", DataFormat.Parquet))
        |val sink = TypedSink[ContractOrdered](DataSink.local("/tmp/sink3", DataFormat.Parquet))
        |
        |val builder = PipelineBuilder[IO]("order-mismatch")
        |  .addTypedSource[ProducerReordered, ContractOrdered, SchemaPolicy.ExactOrdered](src, _ => IO.pure(ProducerReordered("x", 1)))
        |  .noTransform
        |  .addTypedSink[ContractOrdered, SchemaPolicy.ExactOrdered](sink, (_, _) => IO.pure(()))
        |""".stripMargin,
    )
  }
}
