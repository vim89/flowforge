package com.flowforge.app

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.Assertions._

class PolicyCompileFailSpec extends AnyFunSuite {

  test("Exact policy rejects extra field in producer") {
    assertTypeError(
      """
         |import cats.effect.IO
         |import com.flowforge.core.PipelineBuilder
         |import com.flowforge.core.contracts.SchemaPolicy
         |import com.flowforge.core.contracts.SchemaConforms
         |import com.flowforge.core.types._
         |import com.flowforge.core.contracts.derive.Shape
         |
         |final case class User(id: Long, email: String)
         |final case class Producer(id: Long, email: String, age: Int)
         |
         |implicit val s1: Shape[User] = Shape.gen[User]
         |implicit val s2: Shape[Producer] = Shape.gen[Producer]
         |implicit val ev: SchemaConforms[Producer, User, SchemaPolicy.Exact] = implicitly
         |
         |val src  = TypedSource[User](DataSource.local("in", DataFormat.CSV))
         |val sink = TypedSink[User](DataSink.local("out", DataFormat.Parquet))
         |
         |val builder = PipelineBuilder[IO]("p")
         |  .addTypedSource[Producer, User, SchemaPolicy.Exact](src, _ => IO.pure(null.asInstanceOf[Producer]))
         |  .addTypedSink[User, SchemaPolicy.Exact](sink, (_, _) => IO.pure(()))
         |  .build()
      """.stripMargin,
    )
  }

  test("ExactOrdered rejects order mismatch") {
    assertTypeError(
      """
         |import cats.effect.IO
         |import com.flowforge.core.PipelineBuilder
         |import com.flowforge.core.contracts.{ SchemaPolicy, SchemaConforms }
         |import com.flowforge.core.contracts.derive.Shape
         |import com.flowforge.core.types._
         |
         |final case class C1(a: Int, b: String)
         |final case class C2(b: String, a: Int)
         |
         |implicit val s1: Shape[C1] = Shape.gen[C1]
         |implicit val s2: Shape[C2] = Shape.gen[C2]
         |implicit val ev: SchemaConforms[C2, C1, SchemaPolicy.ExactOrdered] = implicitly
         |
         |val src  = TypedSource[C1](DataSource.local("in", DataFormat.CSV))
         |val sink = TypedSink[C1](DataSink.local("out", DataFormat.Parquet))
         |
         |val builder = PipelineBuilder[IO]("p")
         |  .addTypedSource[C2, C1, SchemaPolicy.ExactOrdered](src, _ => IO.pure(null.asInstanceOf[C2]))
         |  .addTypedSink[C1, SchemaPolicy.ExactOrdered](sink, (_, _) => IO.pure(()))
         |  .build()
      """.stripMargin,
    )
  }

  test("ExactUnordered allows different order") {
    assertCompiles(
      """
         |import cats.effect.IO
         |import com.flowforge.core.algebra.EffectSystem
         |implicit val es: EffectSystem[IO] = com.flowforge.core.instances.EffectInstances.catsEffectSystemInstance
         |import com.flowforge.core.PipelineBuilder
         |import com.flowforge.core.contracts.{ SchemaPolicy, SchemaConforms }
         |import com.flowforge.core.contracts.derive.Shape
         |import com.flowforge.core.types._
         |
         |final case class C1(a: Int, b: String)
         |final case class C2(b: String, a: Int)
         |
         |implicit val s1: Shape[C1] = Shape.gen[C1]
         |implicit val s2: Shape[C2] = Shape.gen[C2]
         |implicit val ev: SchemaConforms[C2, C1, SchemaPolicy.ExactUnordered] = implicitly
         |
         |val src  = TypedSource[C1](DataSource.local("in", DataFormat.CSV))
         |val sink = TypedSink[C1](DataSink.local("out", DataFormat.Parquet))
         |
         |val builder = PipelineBuilder[IO]("p")
         |  .addTypedSource[C2, C1, SchemaPolicy.ExactUnordered](src, _ => IO.pure(null.asInstanceOf[C2]))
         |  .addTransform[C2](c => IO.pure(c))
         |  .addTypedSink[C1, SchemaPolicy.ExactUnordered](sink, (_, _) => IO.pure(()))
         |  .build()
      """.stripMargin,
    )
  }

  test("Backward allows extra optional field") {
    assertCompiles(
      """
         |import cats.effect.IO
         |import com.flowforge.core.algebra.EffectSystem
         |implicit val es: EffectSystem[IO] = com.flowforge.core.instances.EffectInstances.catsEffectSystemInstance
         |import com.flowforge.core.PipelineBuilder
         |import com.flowforge.core.contracts.{ SchemaPolicy, SchemaConforms }
         |import com.flowforge.core.contracts.derive.Shape
         |import com.flowforge.core.types._
         |
         |final case class V1(id: Long)
         |final case class V2(id: Long, note: Option[String])
         |
         |implicit val s1: Shape[V1] = Shape.gen[V1]
         |implicit val s2: Shape[V2] = Shape.gen[V2]
         |implicit val ev: SchemaConforms[V2, V1, SchemaPolicy.Backward] = implicitly
         |
         |val src  = TypedSource[V1](DataSource.local("in", DataFormat.CSV))
         |val sink = TypedSink[V1](DataSink.local("out", DataFormat.Parquet))
         |
         |val builder = PipelineBuilder[IO]("p")
         |  .addTypedSource[V2, V1, SchemaPolicy.Backward](src, _ => IO.pure(null.asInstanceOf[V2]))
         |  .addTransform[V2](c => IO.pure(c))
         |  .addTypedSink[V1, SchemaPolicy.Backward](sink, (_, _) => IO.pure(()))
         |  .build()
      """.stripMargin,
    )
  }

  test("Forward allows missing field in consumer") {
    assertCompiles(
      """
         |import cats.effect.IO
         |import com.flowforge.core.algebra.EffectSystem
         |implicit val es: EffectSystem[IO] = com.flowforge.core.instances.EffectInstances.catsEffectSystemInstance
         |import com.flowforge.core.PipelineBuilder
         |import com.flowforge.core.contracts.{ SchemaPolicy, SchemaConforms }
         |import com.flowforge.core.contracts.derive.Shape
         |import com.flowforge.core.types._
         |
         |final case class Producer(id: Long, desc: String)
         |final case class Consumer(id: Long)
         |
         |implicit val s1: Shape[Producer] = Shape.gen[Producer]
         |implicit val s2: Shape[Consumer] = Shape.gen[Consumer]
         |implicit val ev: SchemaConforms[Producer, Consumer, SchemaPolicy.Forward] = implicitly
         |
         |val src  = TypedSource[Consumer](DataSource.local("in", DataFormat.CSV))
         |val sink = TypedSink[Consumer](DataSink.local("out", DataFormat.Parquet))
         |
         |val builder = PipelineBuilder[IO]("p")
         |  .addTypedSource[Producer, Consumer, SchemaPolicy.Forward](src, _ => IO.pure(null.asInstanceOf[Producer]))
         |  .addTransform[Producer](c => IO.pure(c))
         |  .addTypedSink[Consumer, SchemaPolicy.Forward](sink, (_, _) => IO.pure(()))
         |  .build()
      """.stripMargin,
    )
  }

  test("Full accepts anything") {
    assertCompiles(
      """
         |import cats.effect.IO
         |import com.flowforge.core.algebra.EffectSystem
         |implicit val es: EffectSystem[IO] = com.flowforge.core.instances.EffectInstances.catsEffectSystemInstance
         |import com.flowforge.core.PipelineBuilder
         |import com.flowforge.core.contracts.{ SchemaPolicy, SchemaConforms }
         |import com.flowforge.core.contracts.derive.Shape
         |import com.flowforge.core.types._
         |
         |final case class A(x: Int)
         |final case class B(y: String)
         |
         |implicit val s1: Shape[A] = Shape.gen[A]
         |implicit val s2: Shape[B] = Shape.gen[B]
         |implicit val ev: SchemaConforms[B, A, SchemaPolicy.Full] = implicitly
         |
         |val src  = TypedSource[A](DataSource.local("in", DataFormat.CSV))
         |val sink = TypedSink[A](DataSink.local("out", DataFormat.Parquet))
         |
         |val builder = PipelineBuilder[IO]("p")
         |  .addTypedSource[B, A, SchemaPolicy.Full](src, _ => IO.pure(null.asInstanceOf[B]))
         |  .addTransform[B](c => IO.pure(c))
         |  .addTypedSink[A, SchemaPolicy.Full](sink, (_, _) => IO.pure(()))
         |  .build()
      """.stripMargin,
    )
  }
}
