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
    violations: List[Violation],
  )
  final case class Violation(rule: String, message: String)

  def runChecks[A](
    spark: SparkSession,
    dataset: DataAlgebra.Dataset[A],
    constraints: List[FFConstraint],
  ): Either[String, DataAlgebra.QualityResult[DataAlgebra.Dataset[A]]] = {
    val jar = sys.props.get("ff.dq.runner").orElse(sys.env.get("FF_DEEQU_RUNNER_JAR")).getOrElse {
      return Left("Runner jar not provided. Set -Dff.dq.runner=/path/to/jar or FF_DEEQU_RUNNER_JAR env var")
    }

    val (mode, inputPath) = dataset match {
      case p: ProductionSparkDataset[A] =>
        val tmp = File.createTempFile("ff_dq_tmp", ""); tmp.delete(); tmp.mkdirs()
        val path = new File(tmp, "data").getAbsolutePath
        p.sparkDataFrame.coalesce(1).write.mode("overwrite").parquet(path)
        ("parquet", path)
      case _ => return Left("External runner requires a Spark-backed dataset")
    }

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
      (line: String) => { stderr.append(line + "\n"); () }
    )
    val code   = cmd.!(logger)
    if (code != 0) return Left(s"Runner failed: ${stderr.toString}")

    import io.circe.parser._
    val out = stdout.toString.trim.split("\n").lastOption.getOrElse("{}")
    val cur = parse(out).fold(_ => return Left("Invalid runner JSON"), _.hcursor)
    val passed = cur.get[Boolean]("passed").getOrElse(false)
    val score  = cur.get[Double]("score").getOrElse(0.0)
    val violations: List[DataAlgebra.QualityViolation] =
      cur.downField("violations").as[List[io.circe.Json]].getOrElse(Nil).flatMap { j =>
        val c = j.hcursor
        for {
          rule <- c.get[String]("rule").toOption
          msg  <- c.get[String]("message").toOption
        } yield DataAlgebra.QualityViolation(
          rule = rule,
          message = msg,
          severity = DataAlgebra.ViolationSeverity.Warning,
          recordsAffected = 0L,
        )
      }
    Right(DataAlgebra.QualityResult(dataset, passed = passed, violations = violations, score = score))
  }

  private def toJson(constraints: List[FFConstraint]): String = {
    import io.circe.Json
    val arr = constraints.map {
      case FFConstraint.NotNull(f, _) =>
        Json.obj("type" -> Json.fromString("not_null"), "field" -> Json.fromString(f.value))
      case FFConstraint.Unique(f, _) =>
        Json.obj("type" -> Json.fromString("unique"), "field" -> Json.fromString(f.value))
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
      case FFConstraint.Compliance(name, sql, _) =>
        Json.obj(
          "type"       -> Json.fromString("compliance"),
          "ruleName"   -> Json.fromString(name),
          "predicateSql" -> Json.fromString(sql),
        )
    }
    Json.obj("constraints" -> Json.fromValues(arr)).noSpaces
  }
}

