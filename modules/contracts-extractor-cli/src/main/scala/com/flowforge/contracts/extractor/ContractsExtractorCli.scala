package com.flowforge.contracts.extractor

import cats.effect.{ ExitCode, IO, IOApp, Resource }
import com.flowforge.core.algebra.EffectSystem
import com.flowforge.core.instances.EffectInstances._
import org.apache.spark.sql.{ DataFrame, SparkSession }
import scopt.OParser

object ContractsExtractorCli extends IOApp {

  sealed trait Mode
  object Mode {
    case object Parquet     extends Mode
    case object Delta       extends Mode
    case object Hive        extends Mode
    case object CSV         extends Mode
    case object JDBC        extends Mode
    case object GenerateSDK extends Mode
  }

  final case class Args(
    mode: Mode = Mode.Parquet,
    input: String = "",
    domain: String = "default",
    entity: String = "Entity",
    namespace: String = "com.example",
    outputDir: String = "contracts",
    jdbcUrl: Option[String] = None,
    jdbcTable: Option[String] = None,
    master: Option[String] = Some("local[*]"))

  private val builder = OParser.builder[Args]
  private val parser = {
    import builder._
    OParser.sequence(
      programName("ff-contract-extractor"),
      head("FlowForge", "contracts-extractor"),
      opt[String]("mode")
        .required()
        .action((m, a) =>
          a.copy(mode = m.toLowerCase match {
            case "parquet"      => Mode.Parquet
            case "delta"        => Mode.Delta
            case "hive"         => Mode.Hive
            case "csv"          => Mode.CSV
            case "jdbc"         => Mode.JDBC
            case "generate-sdk" => Mode.GenerateSDK
            case other          => throw new IllegalArgumentException(s"Unknown mode: $other")
          }),
        )
        .text("parquet | delta | hive | csv | jdbc | generate-sdk"),
      opt[String]("input")
        .required()
        .action((p, a) => a.copy(input = p))
        .text("path, table, or identifier"),
      opt[String]("domain").optional().action((d, a) => a.copy(domain = d)).text("domain name"),
      opt[String]("entity")
        .optional()
        .action((e, a) => a.copy(entity = e))
        .text("entity name (record name)"),
      opt[String]("namespace")
        .optional()
        .action((n, a) => a.copy(namespace = n))
        .text("Avro namespace"),
      opt[String]("output")
        .optional()
        .action((o, a) => a.copy(outputDir = o))
        .text("output directory (contracts root)"),
      opt[String]("jdbc-url")
        .optional()
        .action((u, a) => a.copy(jdbcUrl = Some(u)))
        .text("JDBC url (for mode=jdbc)"),
      opt[String]("jdbc-table")
        .optional()
        .action((t, a) => a.copy(jdbcTable = Some(t)))
        .text("JDBC table (for mode=jdbc)"),
      opt[String]("master")
        .optional()
        .action((m, a) => a.copy(master = Some(m)))
        .text("Spark master (default: local[*])"),
    )
  }

  def run(args: List[String]): IO[ExitCode] =
    OParser.parse(parser, args, Args()) match {
      case Some(cfg) => extract(cfg).as(ExitCode.Success)
      case None      => IO.pure(ExitCode(2))
    }

  private def sparkResource(master: Option[String]): Resource[IO, SparkSession] =
    Resource.make {
      EffectSystem[IO].blocking {
        val builder = SparkSession
          .builder()
          .appName("ff-contract-extractor")
          .config("spark.ui.enabled", "false")
        master.orElse(sys.env.get("SPARK_MASTER")).foreach(builder.master)
        builder.getOrCreate()
      }
    }(s => EffectSystem[IO].blocking(s.stop()).void)

  private def extract(args: Args): IO[Unit] = args.mode match {
    case Mode.GenerateSDK =>
      import com.flowforge.contracts.sdk.ContractSdkGenerator
      ContractSdkGenerator.generateSdk(args.input, args.outputDir)
    case _ =>
      sparkResource(args.master).use { spark =>
        val df = args.mode match {
          case Mode.Parquet => spark.read.parquet(args.input)
          case Mode.Delta   => spark.read.format("delta").load(args.input)
          case Mode.Hive    => spark.table(args.input)
          case Mode.CSV =>
            spark.read.option("header", "true").option("inferSchema", "true").csv(args.input)
          case Mode.JDBC =>
            val url = args.jdbcUrl.getOrElse(
              throw new IllegalArgumentException("--jdbc-url required for mode=jdbc"),
            )
            val table = args.jdbcTable.getOrElse(args.input)
            spark.read.format("jdbc").option("url", url).option("dbtable", table).load()
          case Mode.GenerateSDK => throw new IllegalStateException("Should not reach here")
        }
        for {
          avro <- IO.pure(Avro.fromSpark(df, args.namespace, args.entity))
          _    <- Files.writeContracts(args.outputDir, args.domain, args.entity, avro)
        } yield ()
      }
  }

  // Very small Avro generator from Spark StructType (flat/nested basic support)
  object Avro {
    import io.circe.{ Encoder, Json }
    import io.circe.syntax._
    import io.circe.generic.semiauto._

    final case class Field(name: String, `type`: Json)
    final case class Record(
      `type`: String = "record",
      name: String,
      namespace: String,
      fields: List[Field])

    implicit val fieldEncoder: Encoder[Field]   = deriveEncoder[Field]
    implicit val recordEncoder: Encoder[Record] = deriveEncoder[Record]

    def fromSpark(
      df: DataFrame,
      namespace: String,
      recordName: String,
    ): String = {
      val fields = df.schema.fields.toList.map { f =>
        Field(f.name, sparkTypeToAvro(f.dataType, f.nullable))
      }
      val rec = Record(name = recordName, namespace = namespace, fields = fields)
      rec.asJson.spaces2
    }

    private def sparkTypeToAvro(
      dt: org.apache.spark.sql.types.DataType,
      nullable: Boolean,
    ): io.circe.Json = {
      import org.apache.spark.sql.types._
      val base: Json = dt match {
        case IntegerType   => Json.fromString("int")
        case LongType      => Json.fromString("long")
        case DoubleType    => Json.fromString("double")
        case FloatType     => Json.fromString("float")
        case BooleanType   => Json.fromString("boolean")
        case StringType    => Json.fromString("string")
        case TimestampType => Json.fromString("long")   // epoch millis convention
        case DateType      => Json.fromString("int")    // days since epoch
        case ShortType     => Json.fromString("int")
        case ByteType      => Json.fromString("int")
        case DecimalType() => Json.fromString("string") // simplify
        case ArrayType(elem, _) =>
          io.circe.Json.obj(
            "type"  -> Json.fromString("array"),
            "items" -> sparkTypeToAvro(elem, nullable = true),
          )
        case StructType(fields) =>
          val subFields = fields.toList.map(f =>
            io.circe.Json.obj(
              "name" -> Json.fromString(f.name),
              "type" -> sparkTypeToAvro(f.dataType, f.nullable),
            ),
          )
          io.circe.Json.obj(
            "type"   -> Json.fromString("record"),
            "name"   -> Json.fromString("Sub"),
            "fields" -> io.circe.Json.fromValues(subFields),
          )
        case _ => Json.fromString("string")
      }
      if (nullable) io.circe.Json.fromValues(List(Json.fromString("null"), base)) else base
    }
  }

  object Files {
    import java.nio.file.{ Files => JFiles, Paths }

    def writeContracts(
      root: String,
      domain: String,
      entity: String,
      avroJson: String,
    ): IO[Unit] =
      IO {
        val base    = Paths.get(root)
        val avroDir = base.resolve("avro").resolve(domain)
        val dqDir   = base.resolve("dq").resolve(domain)
        val metaDir = base.resolve("metadata").resolve(domain)
        List(avroDir, dqDir, metaDir).foreach(p => if (!JFiles.exists(p)) JFiles.createDirectories(p))
        val avroPath = avroDir.resolve(s"${entity}.v1.0.0.avsc")
        val dqPath   = dqDir.resolve(s"${entity}.yaml")
        val metaPath = metaDir.resolve(s"${entity}.yaml")
        JFiles.write(avroPath, avroJson.getBytes("UTF-8"))
        val dqYaml =
          s"""|rules:
              |- name: not_null_${entity}_key
              |  type: not_null
              |  field: ${firstField(avroJson).getOrElse("id")}
              |  severity: error
              |""".stripMargin
        val metaYaml =
          s"""|owner: data-stewards@acme
              |compatibility: FULL
              |slas:
              |  freshness_minutes: 60
              |  late_data_policy: accept_with_flag
              |""".stripMargin
        JFiles.write(dqPath, dqYaml.getBytes("UTF-8"))
        val _ = JFiles.write(metaPath, metaYaml.getBytes("UTF-8"))
      }

    private def firstField(avroJson: String): Option[String] = {
      import io.circe.parser._
      parse(avroJson).toOption.flatMap(
        _.hcursor.downField("fields").downArray.downField("name").as[String].toOption,
      )
    }
  }
}
