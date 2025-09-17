package com.flowforge.quality.deequ

import com.flowforge.core.algebra.DataAlgebra
import com.flowforge.core.types.{ QualityConstraint => FFConstraint }
import com.flowforge.engines.spark.ProductionSparkDataset
import org.apache.spark.sql.SparkSession

import java.io.{ File, PrintWriter }
import scala.sys.process._

object ExternalDeequRunner {

  final case class RunnerResult(
    passed: Boolean,
    score: Double,
    violations: List[Violation])
  final case class Violation(rule: String, message: String)

  def runChecks[A](
    spark: SparkSession,
    dataset: DataAlgebra.Dataset[A],
    constraints: List[FFConstraint],
  ): Either[String, DataAlgebra.QualityResult[DataAlgebra.Dataset[A]]] = {
    val jarOpt = sys.props.get("ff.dq.runner").orElse(sys.env.get("FF_DEEQU_RUNNER_JAR"))
    if (jarOpt.isEmpty) {
      Left("Runner jar not provided. Set -Dff.dq.runner=/path/to/jar or FF_DEEQU_RUNNER_JAR env var")
    } else {
      val jar = jarOpt.get

      val modeAndPath = dataset match {
        case p: ProductionSparkDataset[A] =>
          val tmp  = File.createTempFile("ff_dq_tmp", ""); tmp.delete(); tmp.mkdirs()
          val path = new File(tmp, "data").getAbsolutePath
          p.sparkDataFrame.coalesce(1).write.mode("overwrite").parquet(path)
          Some(("parquet", path))
        case _ => None
      }

      if (modeAndPath.isEmpty) {
        Left("External runner requires a Spark-backed dataset")
      } else {
        val (mode, inputPath) = modeAndPath.get

        val constraintsFile = File.createTempFile("ff_constraints", ".json")
        val json            = toJson(constraints)
        val pw              = new PrintWriter(constraintsFile)
        try pw.write(json)
        finally pw.close()

        val cmd = Seq(
          "java",
          "-Dnet.bytebuddy.experimental=true",
          "-jar",
          jar,
          "--mode",
          mode,
          "--input",
          inputPath,
          "--constraints",
          constraintsFile.getAbsolutePath,
        )
        val stdout = new StringBuilder
        val stderr = new StringBuilder
        val logger = ProcessLogger(
          (line: String) => { stdout.append(line + "\n"); () },
          (line: String) => { stderr.append(line + "\n"); () },
        )
        val code = cmd.!(logger)
        if (code != 0) {
          Left(s"Runner failed: ${stderr.toString}")
        } else {
          import io.circe.parser._
          val out = stdout.toString.trim.split("\n").lastOption.getOrElse("{}")
          parse(out) match {
            case Left(_) => Left("Invalid runner JSON")
            case Right(parsed) =>
              val cur    = parsed.hcursor
              val passed = cur.get[Boolean]("passed").getOrElse(false)
              val score  = cur.get[Double]("score").getOrElse(0.0)
              val violations: List[DataAlgebra.QualityViolation] =
                cur.downField("violations").as[List[io.circe.Json]].getOrElse(Nil).flatMap { jsonViolation =>
                  val cursor = jsonViolation.hcursor
                  for {
                    rule <- cursor.get[String]("rule").toOption
                    msg  <- cursor.get[String]("message").toOption
                  } yield DataAlgebra.QualityViolation(
                    rule = rule,
                    message = msg,
                    severity = DataAlgebra.ViolationSeverity.Warning,
                    recordsAffected = 0L,
                  )
                }
              Right(
                DataAlgebra.QualityResult(dataset, passed = passed, violations = violations, score = score),
              )
          }
        }
      }
    }
  }

  private def toJson(constraints: List[FFConstraint]): String = {
    import io.circe.Json
    val arr = constraints.map {
      case FFConstraint.NotNull(f, _) =>
        Json.obj("type" -> Json.fromString("not_null"), "field" -> Json.fromString(f.value))
      case FFConstraint.Unique(f, _) =>
        Json.obj("type" -> Json.fromString("unique"), "field" -> Json.fromString(f.value))
      case FFConstraint.Distinctness(f, r, _) =>
        Json.obj(
          "type"     -> Json.fromString("distinctness"),
          "field"    -> Json.fromString(f.value),
          "minRatio" -> Json.fromDoubleOrNull(r),
        )
      case FFConstraint.Pattern(f, regex, _) =>
        Json.obj(
          "type"  -> Json.fromString("pattern"),
          "field" -> Json.fromString(f.value),
          "regex" -> Json.fromString(regex),
        )
      case FFConstraint.Range(f, min, max, _) =>
        Json.obj(
          "type"  -> Json.fromString("range"),
          "field" -> Json.fromString(f.value),
          "min"   -> min.map(Json.fromDoubleOrNull).getOrElse(Json.Null),
          "max"   -> max.map(Json.fromDoubleOrNull).getOrElse(Json.Null),
        )
      case FFConstraint.Min(f, min, _) =>
        Json.obj(
          "type"  -> Json.fromString("min"),
          "field" -> Json.fromString(f.value),
          "min"   -> Json.fromDoubleOrNull(min),
        )
      case FFConstraint.Max(f, max, _) =>
        Json.obj(
          "type"  -> Json.fromString("max"),
          "field" -> Json.fromString(f.value),
          "max"   -> Json.fromDoubleOrNull(max),
        )
      case FFConstraint.NullRateBelow(f, thr, _) =>
        Json.obj(
          "type"        -> Json.fromString("null_rate_below"),
          "field"       -> Json.fromString(f.value),
          "maxNullRate" -> Json.fromDoubleOrNull(thr),
        )
      case FFConstraint.Compliance(name, sql, _) =>
        Json.obj(
          "type"         -> Json.fromString("compliance"),
          "ruleName"     -> Json.fromString(name),
          "predicateSql" -> Json.fromString(sql),
        )
    }
    Json.obj("constraints" -> Json.fromValues(arr)).noSpaces
  }
}
