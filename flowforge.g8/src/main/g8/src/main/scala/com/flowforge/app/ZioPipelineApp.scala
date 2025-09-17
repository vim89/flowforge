package com.flowforge.app

import com.flowforge.core.PipelineBuilder
import com.flowforge.core.algebra.{ EffectSystem }
import com.flowforge.core.contracts.{ SchemaConforms, SchemaPolicy }
import com.flowforge.core.contracts.derive.Shape
import com.flowforge.core.types._
import com.flowforge.engines.spark.SparkDataAlgebra
import com.flowforge.framework.PipelineExecution
import org.apache.spark.sql.SparkSession
import zio.{ Task, ZIO, ZIOAppDefault }
import com.flowforge.core.algebra.{ DataEncoder => FFEncoder, EncodedData => FFEncodedData }
import com.flowforge.core.algebra.{ DataDecoder => FFDecoder, EncodedData => FFEncodedData, CorruptedData => FFCorruptedData }
import io.circe.parser.parse

/**
 * ZIO Task variant to demonstrate effect-system polymorphism.
 */
object ZioPipelineApp extends ZIOAppDefault {

  final case class User(id: Long, email: String, age: Int)
  implicit val userShape: Shape[User] = Shape.gen[User]
  implicit val conformsExact: SchemaConforms[User, User, SchemaPolicy.Exact] = implicitly
  implicit val es: EffectSystem[Task] = com.flowforge.core.instances.EffectInstances.zioEffectSystemInstance

  private def mkSpark: Task[SparkSession] = ZIO.attempt {
    SparkSession.builder().appName("FlowForgeZIO").master("local[*]").config("spark.ui.enabled", "false").getOrCreate()
  }
  private def stopSpark(s: SparkSession): Task[Unit] = ZIO.attempt(s.stop()).ignore

  // Minimal JSON decoder for ZIO path
  implicit val userDecoder: FFDecoder[User] = new FFDecoder[User] {
    def decode(ed: FFEncodedData, format: DataFormat) = format match {
      case DataFormat.JSON | DataFormat.JSONL =>
        parse(new String(ed.data, "UTF-8")).left.map(e => FFCorruptedData(e.getMessage)).flatMap { json =>
          val c = json.hcursor
          for {
            id    <- c.get[Long]("id").left.map(e => FFCorruptedData(e.getMessage))
            email <- c.get[String]("email").left.map(e => FFCorruptedData(e.getMessage))
            age   <- c.get[Int]("age").left.map(e => FFCorruptedData(e.getMessage))
          } yield User(id, email, age)
        }
      case other => Left(FFCorruptedData("Unsupported format: " + other.toString))
    }
    def validateSchema(ed: FFEncodedData, expected: DataSchema) = Right(())
    def decodeWithEvolution(ed: FFEncodedData, format: DataFormat, target: DataSchema) = decode(ed, format)
    def supportsFormat(format: DataFormat) = format == DataFormat.JSON || format == DataFormat.JSONL
  }

  // Minimal encoder to satisfy typeclass bounds during write
  implicit val userEncoder: FFEncoder[User] = com.flowforge.core.algebra.DataEncoder.instance[User](
    (u, format) => Right(FFEncodedData("{}".getBytes("UTF-8"), format)),
    _ => DataSchema.builder.addField("id", DataType.Long).addField("email", DataType.String).addField("age", DataType.Integer).build,
  )

  def run = {
    val program: Task[Unit] =
      ZIO.acquireReleaseWith(mkSpark)(s => stopSpark(s).orDie) { spark =>
        val dao = SparkDataAlgebra.createSparkDataAlgebra[Task](spark).algebra
        val src = DataSource.local("data/users.csv", DataFormat.CSV)
        val snk = DataSink.local("target/out/users_zio", DataFormat.Parquet)

        val pipeline = PipelineBuilder[Task]("users-pipeline-zio")
          .addTypedSource[User, User, SchemaPolicy.Exact](TypedSource(src), _ => ZIO.succeed(User(0L, "sample@example.com", 0)))
          .addTransform[User](u => ZIO.succeed(u.copy(age = Math.max(0, u.age))))
          .addTypedSink[User, SchemaPolicy.Exact](TypedSink(snk), (_, d) => dao.read[User](src).flatMap(ds => dao.write(ds, d)).unit)
          .build()

        for {
          ds <- dao.read[User](src)
          _  <- ZIO.fromEither(Right(())) // placeholder side-effect; shows generic Task flow
          _  <- PipelineExecution.execute(pipeline)(()) // EffectSystem handles Task
        } yield ()
      }
    program
  }
}
