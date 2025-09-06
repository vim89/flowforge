package com.flowforge.quality.deequ.runner

import org.apache.spark.sql.SparkSession
import scopt.OParser

object Runner {

  final case class Args(
    mode: String = "parquet",
    input: String = "",
    constraintsJson: String = "",
  )

  sealed trait QC
  object QC {
    final case class NotNull(field: String) extends QC
    final case class Unique(field: String)  extends QC
    final case class Pattern(field: String, regex: String, minFraction: Option[Double]) extends QC
    final case class Range(field: String, min: Option[Double], max: Option[Double], minFraction: Option[Double])
        extends QC
    final case class Compliance(ruleName: String, predicateSql: String, minFraction: Option[Double]) extends QC
  }

  final case class ConstraintFile(constraints: List[Map[String, Any]])

  private val builder = OParser.builder[Args]
  private val parser = {
    import builder._
    OParser.sequence(
      programName("ff-deequ-runner"),
      opt[String]("mode").required().action((m, a) => a.copy(mode = m)),
      opt[String]("input").required().action((p, a) => a.copy(input = p)),
      opt[String]("constraints").required().action((c, a) => a.copy(constraintsJson = c)),
    )
  }

  def main(args: Array[String]): Unit = {
    OParser.parse(parser, args, Args()) match {
      case Some(cfg) =>
        val spark = SparkSession
          .builder()
          .appName("ff-deequ-runner")
          .config("spark.ui.enabled", "false")
          .getOrCreate()
        try run(spark, cfg)
        finally spark.stop()
      case None =>
        System.exit(2)
    }
  }

  private def run(spark: SparkSession, cfg: Args): Unit = {
    import io.circe.parser._
    import com.amazon.deequ.checks.{ Check, CheckLevel }
    import com.amazon.deequ.VerificationSuite

    val df = cfg.mode.toLowerCase match {
      case "parquet" => spark.read.parquet(cfg.input)
      case "delta"   => spark.read.format("delta").load(cfg.input)
      case "hive"    => spark.table(cfg.input)
      case "csv"     => spark.read.option("header", "true").option("inferSchema", "true").csv(cfg.input)
      case other     => throw new IllegalArgumentException(s"Unknown mode: $other")
    }

    val jsonStr = scala.io.Source.fromFile(cfg.constraintsJson).mkString
    val parsed  = parse(jsonStr).toOption.getOrElse(throw new IllegalArgumentException("Invalid JSON"))
    val arr     = parsed.hcursor.downField("constraints").as[List[io.circe.Json]].getOrElse(Nil)

    val check0 = new Check(CheckLevel.Error, "flowforge-deequ-runner")
    val check = arr.foldLeft(check0) { (acc, jsonConstraint) =>
      val cursor = jsonConstraint.hcursor
      val tpe = cursor.get[String]("type").getOrElse("")
      tpe.toLowerCase match {
        case "not_null" =>
          acc.isComplete(cursor.get[String]("field").getOrElse(throw new IllegalArgumentException("field missing")))
        case "unique" =>
          acc.isUnique(cursor.get[String]("field").getOrElse(throw new IllegalArgumentException("field missing")))
        case "pattern" =>
          val fld = cursor.get[String]("field").getOrElse(throw new IllegalArgumentException("field missing"))
          val rgx = cursor.get[String]("regex").getOrElse(throw new IllegalArgumentException("regex missing"))
          // deequ has hasPattern; minFraction optional is ignored for now
          acc.hasPattern(fld, rgx.r)
        case "range" =>
          val fld = cursor.get[String]("field").getOrElse(throw new IllegalArgumentException("field missing"))
          val min = cursor.get[Double]("min").toOption
          val max = cursor.get[Double]("max").toOption
          (min, max) match {
            case (Some(a), Some(b)) => acc.isContainedIn(fld, Array(a.toString, b.toString))
            case (Some(a), None)    => acc.isGreaterThan(fld, a.toString)
            case (None, Some(b))    => acc.isLessThan(fld, b.toString)
            case _                  => acc
          }
        case "compliance" =>
          val rule = cursor.get[String]("ruleName").getOrElse("rule")
          val sql = cursor.get[String]("predicateSql").getOrElse(throw new IllegalArgumentException("predicateSql missing"))
          acc.satisfies(sql, rule)
        case _ => acc
      }
    }

    val result           = VerificationSuite().onData(df).addChecks(Seq(check)).run()
    val constraintResults = result.checkResults.values.flatMap(_.constraintResults).toList

    import io.circe.Json
    val violations = constraintResults.collect {
      case cr if cr.status.toString != "Success" =>
        Json.obj(
          "rule"     -> Json.fromString(cr.constraint.toString),
          "message"  -> Json.fromString(cr.message.getOrElse("failed")),
          "severity" -> Json.fromString("Warning"),
        )
    }
    val score =
      if (constraintResults.isEmpty) 1.0
      else (constraintResults.size - violations.size).toDouble / constraintResults.size
    val out = Json.obj(
      "passed"     -> Json.fromBoolean(violations.isEmpty),
      "score"      -> Json.fromDoubleOrNull(score),
      "violations" -> Json.fromValues(violations),
    )
    println(out.noSpaces)
  }
}

