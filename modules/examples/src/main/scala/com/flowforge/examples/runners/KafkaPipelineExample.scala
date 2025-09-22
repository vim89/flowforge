package com.flowforge.examples.runners

import com.flowforge.core.PipelineBuilder
import com.flowforge.core.algebra.{ DataAlgebra, EffectSystem }
import com.flowforge.core.contracts.{ SchemaConforms, SchemaPolicy }
import com.flowforge.core.contracts.derive.Shape
import com.flowforge.core.types._
import com.flowforge.engines.spark.SparkDataAlgebra
import com.flowforge.engines.flink.FlinkDataAlgebra
import com.flowforge.framework.PipelineExecution

/**
  * KafkaPipelineExample
  * --------------------
  * Reads from a simulated Kafka topic (JSONL file) into a typed FlowForge pipeline,
  * then writes Parquet. Demonstrates using the SAME pipeline with Spark or Flink
  * by only swapping the DataAlgebra.
  *
  * Run:
  *   sbt "examples/runMain com.flowforge.examples.runners.KafkaPipelineExample --engine spark --topics-dir target/examples/topics --topic users_out --output target/examples/kafka-spark-out"
  *   sbt "examples/runMain com.flowforge.examples.runners.KafkaPipelineExample --engine flink --topics-dir target/examples/topics --topic users_out --output target/examples/kafka-flink-out"
  */
object KafkaPipelineExample {

  final case class User(id: Long, email: String, age: Int)
  implicit val userShape: Shape[User] = Shape.gen[User]
  implicit val conformsExact: SchemaConforms[User, User, SchemaPolicy.Exact] = implicitly

  final case class Args(engine: String, topicsDir: String, topic: String, output: String)

  def main(raw: Array[String]): Unit = {
    val args = parseArgs(raw.toList).getOrElse {
      println("Usage: --engine spark|flink --topics-dir <dir> --topic <name> --output <dir>")
      sys.exit(1)
    }
    args.engine.toLowerCase match {
      case "spark" => runWithSpark(args)
      case "flink" => runWithFlink(args)
      case other   => println(s"Unknown engine: $other"); sys.exit(1)
    }
  }

  def runWithSpark(args: Args): Unit = {
    import cats.effect.IO
    import cats.effect.unsafe.implicits.global
    import com.flowforge.core.instances.EffectInstances.catsEffectSystemInstance
    import com.flowforge.core.algebra.FlowforgeResource
    import org.apache.spark.sql.SparkSession

    // EffectSystem instance provided by import

    val sparkR = FlowforgeResource.make(IO {
      SparkSession.builder().appName("KafkaPipelineExample").master("local[*]")
        .config("spark.ui.enabled", "false").getOrCreate()
    })(s => IO(s.stop()).void)

    sparkR.use { spark =>
      val dao: DataAlgebra[IO] = SparkDataAlgebra.createSparkDataAlgebra[IO](spark).algebra
      exampleRun[IO]("spark", dao, args)
    }.unsafeRunSync()
  }

  def runWithFlink(args: Args): Unit = {
    import cats.effect.IO
    import cats.effect.unsafe.implicits.global
    import com.flowforge.core.instances.EffectInstances.catsEffectSystemInstance
    // EffectSystem instance provided by import

    val dao: DataAlgebra[IO] = new FlinkDataAlgebra[IO]()
    exampleRun[IO]("flink", dao, args).unsafeRunSync()
  }

  def exampleRun[F[_]](label: String, dao: DataAlgebra[F], args: Args)(implicit E: EffectSystem[F]): F[Unit] = {
    val topicPath = s"${args.topicsDir}/${args.topic}.jsonl"

    // Minimal JSON decoder/encoder for User for both engines
    implicit val userDecoder: com.flowforge.core.algebra.DataDecoder[User] = new com.flowforge.core.algebra.DataDecoder[User] {
      import io.circe.parser.parse
      import com.flowforge.core.algebra.{ EncodedData => FFEncodedData, CorruptedData => FFCorruptedData }
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

    implicit val userEncoder: com.flowforge.core.algebra.DataEncoder[User] =
      new com.flowforge.core.algebra.DataEncoder[User] {
        import io.circe.Json
        import com.flowforge.core.algebra.{ EncodedData => FFEncodedData, UnsupportedFormat => FFUnsupportedFormat }
        def encode(u: User, format: DataFormat) = format match {
          case DataFormat.JSON | DataFormat.JSONL =>
            val json: Json = Json.obj(
              "id"    -> Json.fromLong(u.id),
              "email" -> Json.fromString(u.email),
              "age"   -> Json.fromInt(u.age),
            )
            Right(FFEncodedData(json.noSpaces.getBytes("UTF-8"), format))
          case other => Left(FFUnsupportedFormat(other, "User"))
        }
        def schema(format: DataFormat): DataSchema =
          DataSchema.builder.addField("id", DataType.Long).addField("email", DataType.String).addField("age", DataType.Integer).build
        def estimateSize(u: User, f: DataFormat): Long      = 64
        def supportsFormat(format: DataFormat): Boolean     = format == DataFormat.JSON || format == DataFormat.JSONL
        def optimizationHints(u: User, f: DataFormat)       = com.flowforge.core.algebra.EncodingHints.default
      }

    // Spark path expects DataFormat.JSON; InMemory/Flink supports JSONL natively
    val srcFormat = if (label == "spark") DataFormat.JSON else DataFormat.JSONL
    val src  = DataSource.local(topicPath, srcFormat)
    val sink = DataSink.local(args.output, DataFormat.Parquet)

    val pipeline = PipelineBuilder[F](s"kafka-pipeline-$label")
      .addTypedSource[User, User, SchemaPolicy.Exact](TypedSource(src), _ => E.pure(User(0, "sample@example.com", 0)))
      .addTransform[User](u => E.pure(u.copy(age = u.age + 5)))
      .addTypedSink[User, SchemaPolicy.Exact](TypedSink(sink), (out, d) =>
        E.flatMap(dao.read[User](src)) { ds => E.map(dao.write(ds, d))(_ => ()) },
      )
      .build()

    E.flatMap(E.delay(println(s"[$label-kafka-pipeline] reading $topicPath and writing Parquet to ${args.output}"))) { _ =>
      E.map(PipelineExecution.execute(pipeline)(()))(_ => ())
    }
  }

  private def parseArgs(xs: List[String]): Option[Args] = {
    def next(rest: List[String]): (Option[String], List[String]) = rest match {
      case v :: tail if !v.startsWith("--") => (Some(v), tail)
      case _                                 => (None, rest)
    }
    def loop(rest: List[String], e: Option[String], d: Option[String], t: Option[String], o: Option[String]): Option[Args] = rest match {
      case "--engine" :: tail =>
        val (v, r) = next(tail); loop(r, v.orElse(e), d, t, o)
      case "--topics-dir" :: tail =>
        val (v, r) = next(tail); loop(r, e, v.orElse(d), t, o)
      case "--topic" :: tail =>
        val (v, r) = next(tail); loop(r, e, d, v.orElse(t), o)
      case "--output" :: tail =>
        val (v, r) = next(tail); loop(r, e, d, t, v.orElse(o))
      case Nil => for { eng <- e; dir <- d; tp <- t; out <- o } yield Args(eng, dir, tp, out)
      case _ :: tail => loop(tail, e, d, t, o)
    }
    loop(xs, None, None, None, None)
  }
}
