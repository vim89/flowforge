package com.flowforge.examples.runners

import cats.effect.IO
import com.flowforge.core.PipelineBuilder
import com.flowforge.core.algebra.{DataAlgebra, EffectSystem}
import com.flowforge.core.contracts.derive.Shape
import com.flowforge.core.contracts.{SchemaConforms, SchemaPolicy}
import com.flowforge.core.types._
import com.flowforge.engines.flink.FlinkDataAlgebra
import com.flowforge.engines.spark.SparkDataAlgebra
import com.flowforge.framework.PipelineExecution

/**
  * RunnerWiringExample
  * --------------------
  *
  * Example showing how to wire the SAME pipeline to different engines
  * by swapping the DataAlgebra implementation (Spark vs Flink), and how Kafka could be
  * configured at the edges (simulated here for portability).
  *
  * Run:
  *   sbt "examples/runMain com.flowforge.examples.runners.RunnerWiringExample --engine spark --input examples-data/users.csv --output target/examples/spark-out"
  *   sbt "examples/runMain com.flowforge.examples.runners.RunnerWiringExample --engine flink --input examples-data/users.csv --output target/examples/flink-out"
  *
  * Notes:
  *  - This sample uses CSV input and Parquet output to avoid extra dependencies.
  *  - Kafka config is shown and validated (simulated), but not used to read/write in this sample.
  *  - The point of this example is: your pipeline is engine-agnostic; you only swap the DataAlgebra.
  */
object RunnerWiringExample {

  // 1) Define your business type and contract evidence
  final case class User(id: Long, email: String, age: Int)
  implicit val userShape: Shape[User] = Shape.gen[User]
  implicit val conformsExact: SchemaConforms[User, User, SchemaPolicy.Exact] = implicitly

  // Terse CLI arg parsing for demo purposes
  final case class Args(engine: String, input: String, output: String, mode: String)

  def main(raw: Array[String]): Unit = {
    val args = parseArgs(raw.toList).getOrElse {
      println("Usage: --engine spark|flink --input <csv> --output <dir> [--kafka]")
      sys.exit(1)
    }

    // Choose the engine by selecting a DataAlgebra implementation.
    // Note: swapping engines does not require rewriting your pipeline.
    args.engine.toLowerCase match {
      case "spark" => runWithSpark(args)
      case "flink" => runWithFlink(args)
      case other =>
        println(s"Unknown engine: $other (expected spark|flink)")
        sys.exit(1)
    }
  }

  // Spark Runner
  def runWithSpark(args: Args): Unit = {
    import cats.effect.IO
    import cats.effect.unsafe.implicits.global
    import com.flowforge.core.instances.EffectInstances.catsEffectSystemInstance
    import org.apache.spark.sql.SparkSession

    // EffectSystem instance (implicit) for IO
    import com.flowforge.core.algebra.FlowforgeResource
    // EffectSystem instance provided by import

    val sparkR: FlowforgeResource[IO, SparkSession] = FlowforgeResource.make(IO {
      SparkSession.builder().appName("RunnerWiringExample").master("local[*]")
        .config("spark.ui.enabled", "false").getOrCreate()
    })(s => IO(s.stop()).void)

    sparkR.use { spark =>
      val dao: DataAlgebra[IO] = SparkDataAlgebra.createSparkDataAlgebra[IO](spark).algebra
      exampleRun[IO]("spark", dao, args)
    }.unsafeRunSync()
  }

  // Flink Runner
  def runWithFlink(args: Args): Unit = {
    import cats.effect.unsafe.implicits.global
    import com.flowforge.core.instances.EffectInstances.catsEffectSystemInstance
    // EffectSystem instance provided by import

    // FlinkDataAlgebra delegates to a safe in-memory implementation for this demo
    val dao: DataAlgebra[IO] = new FlinkDataAlgebra[IO]()
    exampleRun("flink", dao, args).unsafeRunSync()
  }

  // Shared pipeline
  def exampleRun[F[_]](label: String, dao: DataAlgebra[F], args: Args)(implicit E: EffectSystem[F]): F[Unit] = {

    val log = (msg: String) => E.delay(println(s"[$label] $msg"))

    // Simulated Kafka configuration (for real Kafka, wire connector & engine specifics)
    val cfgKafkaF: F[Unit] = if (args.mode == "kafka") configureKafka[F](label) else E.pure(())

    val src  = DataSource.local(args.input, DataFormat.CSV)
    val sink = DataSink.local(args.output, DataFormat.Parquet)

    // Minimal decoder for User (JSON and CSV)
    implicit val userDecoder: com.flowforge.core.algebra.DataDecoder[User] =
      new com.flowforge.core.algebra.DataDecoder[User] {
        import com.flowforge.core.algebra.{CorruptedData => FFCorruptedData, EncodedData => FFEncodedData}
        import io.circe.parser.parse

        def decode(ed: FFEncodedData, format: DataFormat) = format match {
          case DataFormat.JSON | DataFormat.JSONL =>
            // JSON object -> User
            parse(new String(ed.data, "UTF-8"))
              .left.map(e => FFCorruptedData(e.getMessage))
              .flatMap { json =>
                val c = json.hcursor
                for {
                  id    <- c.get[Long]("id").left.map(e => FFCorruptedData(e.getMessage))
                  email <- c.get[String]("email").left.map(e => FFCorruptedData(e.getMessage))
                  age   <- c.get[Int]("age").left.map(e => FFCorruptedData(e.getMessage))
                } yield User(id, email, age)
              }

          case DataFormat.CSV =>
            // CSV line: id,email,age  (no header here; Spark handles headers before decoding records)
            val line   = new String(ed.data, "UTF-8")
            val parts  = line.split(",", -1)
            if (parts.length >= 3) {
              scala.util.Try {
                val id    = parts(0).trim.toLong
                val email = parts(1).trim
                val age   = parts(2).trim.toInt
                User(id, email, age)
              }.toEither.left.map(e => FFCorruptedData(s"CSV parse error: ${e.getMessage}"))
            } else Left(FFCorruptedData(s"CSV requires 3 columns (id,email,age), got: ${parts.toList}"))

          case other =>
            Left(FFCorruptedData("Unsupported format: " + other.toString))
        }

        def validateSchema(ed: FFEncodedData, expected: DataSchema) = Right(())

        def decodeWithEvolution(ed: FFEncodedData, format: DataFormat, target: DataSchema) =
          decode(ed, format)

        // Advertise support for JSON/JSONL and CSV
        def supportsFormat(format: DataFormat) =
          format == DataFormat.JSON || format == DataFormat.JSONL || format == DataFormat.CSV
      }

    implicit val userEncoder: com.flowforge.core.algebra.DataEncoder[User] = {
      import com.flowforge.core.algebra._
      DataEncoder.instance[User](
        (data, format) => format match {
          case DataFormat.JSON | DataFormat.JSONL =>
            val js = s"""{"id":%d,"email":"%s","age":%d}""".format(data.id, data.email, data.age)
            Right(EncodedData(js.getBytes("UTF-8"), format))
          case other => Left(UnsupportedFormat(other, "User"))
        },
        _ => DataSchema(
          fields = List(
            StructField(RefinedTypes.FieldName.unsafeFrom("id"), DataType.Long, nullable = false),
            StructField(RefinedTypes.FieldName.unsafeFrom("email"), DataType.String, nullable = false),
            StructField(RefinedTypes.FieldName.unsafeFrom("age"), DataType.Integer, nullable = false),
          ),
          version = RefinedTypes.SchemaVersion.unsafeFrom(1),
          metadata = Map.empty,
          createdAt = java.time.Instant.now(),
        ),
      )
    }

    // Sanity: ensure decoder supports the chosen format (helps catch misconfig early)
    require(userDecoder.supportsFormat(DataFormat.CSV), "User decoder must support CSV for this example")

    val pipeline = PipelineBuilder[F](s"users-pipeline-$label")
      .addTypedSource[User, User, SchemaPolicy.Exact](TypedSource(src), _ => E.pure(User(0, "sample@example.com", 0)))
      .addTransform[User](u => E.pure(u.copy(age = math.max(0, u.age))))
      .addTypedSink[User, SchemaPolicy.Exact](
        TypedSink(sink),
        (u: User, d) =>
          E.flatMap(dao.read(src)) { ds =>
            dao.write(ds, d)(userEncoder)
            log("Done")
          }
          // Use the value coming from the pipeline; this keeps the transform pure and avoids re-reading.
          // E.flatMap(dao.write(dao.single(u), d))(_ => ())
      )
      .build()

    // Sequential execution using EffectSystem only (no Cats constraints required)
    E.flatMap(cfgKafkaF) { _ =>
      val maybeKafkaFlow: F[Unit] =
        if (args.mode == "kafka") kafkaFlow[F](label, args) else E.pure(())
      E.flatMap(maybeKafkaFlow) { _ =>
      E.flatMap(log(s"Reading CSV: ${args.input}")) { _ =>
        E.flatMap(dao.read[User](src)) { ds =>
          E.flatMap(log(s"Records (sample): ${ds.metadata.recordCount}")) { _ =>
            E.flatMap(log(s"Executing typed pipeline to Parquet: ${args.output}")) { _ =>
              E.flatMap(PipelineExecution.execute(pipeline)(())) { _ =>
                log("Done.")
              }
            }
          }
        }
      }
    }}
  }

  // Simulated Kafka configuration (validates values and demonstrates how you’d wire them)
  def configureKafka[F[_]: EffectSystem](label: String): F[Unit] = {
    val F = EffectSystem[F]
    // Example configs (these would typically come from ConfigurationAlgebra / env)
    val brokers         = "localhost:9092"
    val topicIn         = "users_in"
    val topicOut        = "users_out"
    val schemaRegistry  = Some("http://localhost:8081")

    def validateNonEmpty(name: String, v: String): F[Unit] =
      if (v.trim.nonEmpty) F.pure(()) else F.raiseError(new IllegalArgumentException(s"$name is empty"))

    F.flatMap(validateNonEmpty("brokers", brokers)) { _ =>
      F.flatMap(validateNonEmpty("topicIn", topicIn)) { _ =>
        F.flatMap(validateNonEmpty("topicOut", topicOut)) { _ =>
          F.delay(println(s"[$label] Kafka configured: brokers=$brokers in=$topicIn out=$topicOut sr=$schemaRegistry"))
        }
      }
    }
  }

  // very light arg parsing for demo
  private def parseArgs(xs: List[String]): Option[Args] = {
    def next(flag: String, rest: List[String]): (Option[String], List[String]) = rest match {
      case v :: tail if !v.startsWith("--") => (Some(v), tail)
      case _                                 => (None, rest)
    }
    def loop(rest: List[String], e: Option[String], in: Option[String], out: Option[String], m: Option[String]): Option[Args] =
      rest match {
        case "--engine" :: tail =>
          val (v, r) = next("--engine", tail); loop(r, v.orElse(e), in, out, m)
        case "--input" :: tail =>
          val (v, r) = next("--input", tail); loop(r, e, v.orElse(in), out, m)
        case "--output" :: tail =>
          val (v, r) = next("--output", tail); loop(r, e, in, v.orElse(out), m)
        case "--mode" :: tail =>
          val (v, r) = next("--mode", tail); loop(r, e, in, out, v.orElse(m))
        case "--kafka" :: tail => loop(tail, e, in, out, Some("kafka"))
        case Nil => for { eng <- e; i <- in; o <- out } yield Args(eng, i, o, m.getOrElse("file"))
        case _ :: tail => loop(tail, e, in, out, m)
      }
    loop(xs, None, None, None, None)
  }

  // Kafka facade flow: publish CSV users -> consume -> transform -> publish out
  def kafkaFlow[F[_]](label: String, args: Args)(implicit F: EffectSystem[F],
    enc: com.flowforge.core.algebra.DataEncoder[User],
    dec: com.flowforge.core.algebra.DataDecoder[User],
  ): F[Unit] = {
    import com.flowforge.examples.connectors.KafkaFacade
    val base     = "target/examples/topics"
    val topicIn  = "users_in"
    val topicOut = "users_out"

    val in  = KafkaFacade.topic[F, User](base, topicIn)
    val out = KafkaFacade.topic[F, User](base, topicOut)

    def parseCsvUsers(path: String): F[List[User]] = F.blocking {
      val src = scala.io.Source.fromFile(path)
      try src.getLines().drop(1).toList.flatMap { line =>
        val parts = line.split(",", -1)
        if (parts.length >= 3)
          Some(User(parts(0).trim.toLong, parts(1).trim, parts(2).trim.toInt))
        else None
      }
      finally src.close()
    }

    F.flatMap(parseCsvUsers(args.input)) { users =>
      F.flatMap(F.delay(println(s"[$label-kafka] publishing ${users.size} records to $topicIn"))) { _ =>
        F.flatMap(in.publishAll(users)) { _ =>
          F.flatMap(in.consumeAll) { consumed =>
            val transformed = consumed.map(u => u.copy(age = math.max(0, u.age)))
            F.flatMap(F.delay(println(s"[$label-kafka] consumed ${consumed.size} records from $topicIn"))) { _ =>
              F.flatMap(out.publishAll(transformed))(_ =>
                F.delay(println(s"[$label-kafka] published ${transformed.size} records to $topicOut"))
              )
            }
          }
        }
      }
    }
  }
}
