package com.flowforge.validation

import cats.effect.{ ExitCode, IO, IOApp, Resource }
import com.flowforge.core.algebra.EffectSystem
import com.flowforge.core.instances.EffectInstances._
import org.apache.spark.sql.SparkSession
import scopt.OParser
object SchemaValidateCli extends IOApp {

  sealed trait Mode
  object Mode {
    case object Parquet extends Mode
    case object Delta   extends Mode
    case object Hive    extends Mode
  }

  sealed trait ExpectedFormat
  object ExpectedFormat {
    case object Spark extends ExpectedFormat
  }

  final case class Args(
    mode: Mode = Mode.Parquet,
    pathOrTable: String = "",
    expectedSchemaJson: java.io.File = new java.io.File(""),
    expectedFormat: ExpectedFormat = ExpectedFormat.Spark,
    master: Option[String] = None)

  private val builder = OParser.builder[Args]
  private val parser = {
    import builder._
    OParser.sequence(
      programName("ff-validate-schema"),
      head("FlowForge", "schema-validate"),
      opt[String]("mode")
        .required()
        .action((m, a) =>
          a.copy(mode = m.toLowerCase match {
            case "parquet" => Mode.Parquet
            case "delta"   => Mode.Delta
            case "hive"    => Mode.Hive
            case other     => throw new IllegalArgumentException(s"Unknown mode: $other")
          }),
        )
        .text("parquet | delta | hive"),
      opt[String]("input")
        .required()
        .action((p, a) => a.copy(pathOrTable = p))
        .text("path or table"),
      opt[java.io.File]("expected-json")
        .required()
        .action((f, a) => a.copy(expectedSchemaJson = f))
        .text("expected schema file (Spark JSON or Avro or JSON Schema)"),
      opt[String]("expected-format")
        .optional()
        .action((s, a) =>
          a.copy(expectedFormat = s.toLowerCase match {
            case "spark" | "" => ExpectedFormat.Spark
            case other =>
              throw new IllegalArgumentException(
                s"Unsupported expected-format: $other (only 'spark' supported in this build)",
              )
          }),
        )
        .text("spark (default)"),
      opt[String]("master")
        .optional()
        .action((m, a) => a.copy(master = Some(m)))
        .text("Spark master, e.g., local[*]"),
    )
  }

  def run(args: List[String]): IO[ExitCode] =
    OParser.parse(parser, args, Args()) match {
      case Some(cfg) => validate(cfg).as(ExitCode.Success)
      case None      => IO.pure(ExitCode(2))
    }

  private def sparkResource(master: Option[String]): Resource[IO, SparkSession] =
    Resource.make {
      EffectSystem[IO].blocking {
        val builder =
          SparkSession.builder().appName("ff-validate-schema").config("spark.ui.enabled", "false")
        master.orElse(sys.env.get("SPARK_MASTER")).foreach(builder.master)
        builder.getOrCreate()
      }
    }(s => EffectSystem[IO].blocking(s.stop()).void)

  private def validate(args: Args): IO[Unit] =
    sparkResource(args.master).use { spark =>
      val expectedRaw       = scala.io.Source.fromFile(args.expectedSchemaJson).mkString
      val expectedCanonical = Canonical.fromFile(expectedRaw, args.expectedFormat)
      val df = args.mode match {
        case Mode.Parquet => spark.read.parquet(args.pathOrTable)
        case Mode.Delta   => spark.read.format("delta").load(args.pathOrTable)
        case Mode.Hive    => spark.table(args.pathOrTable)
      }
      val actualCanonical = Canonical.fromSpark(df.schema)
      Canonical.diff(expectedCanonical, actualCanonical) match {
        case Nil   => IO.println("OK: Schemas match")
        case diffs => IO.raiseError(new RuntimeException(Canonical.prettyDiffs(diffs)))
      }
    }

  // Canonical model for schema diffs
  object Canonical {
    final case class Field(
      name: String,
      tpe: String,
      nullable: Boolean)
    final case class Model(fields: List[Field])

    sealed trait Diff
    object Diff {
      case class MissingField(name: String) extends Diff
      case class ExtraField(name: String)   extends Diff
      case class TypeMismatch(
        name: String,
        expected: String,
        actual: String)
          extends Diff
      case class NullabilityMismatch(
        name: String,
        expected: Boolean,
        actual: Boolean)
          extends Diff
    }

    import io.circe._
    import io.circe.parser._

    def fromSpark(s: org.apache.spark.sql.types.StructType): Model = {
      val fields = s.fields.toList.map(f => Field(f.name, f.dataType.typeName, f.nullable))
      Model(fields)
    }

    def fromFile(json: String, fmt: ExpectedFormat): Model = fmt match {
      case ExpectedFormat.Spark =>
        parse(json).toOption.flatMap(_.hcursor.downField("fields").as[List[Json]].toOption) match {
          case Some(arr) =>
            val fields = arr.flatMap { jsonField =>
              val cursor = jsonField.hcursor
              for {
                name <- cursor.get[String]("name").toOption
                tpe <- cursor
                  .downField("type")
                  .get[String]("type")
                  .orElse(cursor.get[String]("type"))
                  .toOption
                nul <- cursor.get[Boolean]("nullable").toOption
              } yield Field(name, tpe, nul)
            }
            Model(fields)
          case None => Model(Nil)
        }
    }

    private def normalizeTypeName(s: String): String = s.toLowerCase match {
      case "int" | "integer" => "integer"
      case "long"            => "long"
      case "double"          => "double"
      case "float"           => "float"
      case "string"          => "string"
      case "boolean"         => "boolean"
      case other             => other
    }

    def diff(expected: Model, actual: Model): List[Diff] = {
      val expMap  = expected.fields.map(f => f.name -> f).toMap
      val actMap  = actual.fields.map(f => f.name -> f).toMap
      val missing = expMap.keySet.diff(actMap.keySet).toList.map(Diff.MissingField)
      val extra   = actMap.keySet.diff(expMap.keySet).toList.map(Diff.ExtraField)
      val common = expMap.keySet.intersect(actMap.keySet).toList.flatMap { n =>
        val expected = expMap(n); val actual = actMap(n)
        val d1 =
          if (normalizeTypeName(expected.tpe) != normalizeTypeName(actual.tpe))
            Some(Diff.TypeMismatch(n, expected.tpe, actual.tpe))
          else None
        val d2 =
          if (expected.nullable != actual.nullable)
            Some(Diff.NullabilityMismatch(n, expected.nullable, actual.nullable))
          else None
        List(d1, d2).flatten
      }
      missing ++ extra ++ common
    }

    def prettyDiffs(ds: List[Diff]): String = {
      val lines = ds.map {
        case Diff.MissingField(n)       => s"- Missing field: $n"
        case Diff.ExtraField(n)         => s"- Extra field: $n"
        case Diff.TypeMismatch(n, e, a) => s"- Type mismatch for $n: expected=$e actual=$a"
        case Diff.NullabilityMismatch(n, e, a) =>
          s"- Nullability mismatch for $n: expected=$e actual=$a"
      }
      ("Schema differences:" :: lines).mkString("\n")
    }
  }
}
