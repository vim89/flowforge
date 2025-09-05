package com.flowforge.quality.deequ

import com.flowforge.core.algebra.DataAlgebra
import com.flowforge.core.types.{QualityConstraint => FFConstraint}
import com.flowforge.engines.spark.ProductionSparkDataset
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions._

/**
 * Spark-native quality checks (NotNull, Unique) — replaces Deequ to support Scala 2.13 only builds.
 */
object DeequAdapter {

  def runChecks[A](
    spark: SparkSession, // kept for signature parity; not used in spark-native checks
    dataset: DataAlgebra.Dataset[A],
    constraints: List[FFConstraint],
  ): DataAlgebra.QualityResult[DataAlgebra.Dataset[A]] = {
    val dfOpt = dataset match {
      case p: ProductionSparkDataset[A] => Some(p.sparkDataFrame)
      case _                            => None
    }
    dfOpt match {
      case None => DataAlgebra.QualityResult(dataset, passed = true, violations = Nil, score = 1.0)
      case Some(df) =>
        val results: List[(FFConstraint, Boolean, Long)] = constraints.map {
          case c @ FFConstraint.NotNull(field, _) =>
            val nulls  = df.filter(col(field.value).isNull).count()
            val passed = nulls == 0
            (c, passed, nulls)
          case c @ FFConstraint.Unique(field, _) =>
            val total      = df.count()
            val distinctCt = df.select(col(field.value)).distinct().count()
            val dups       = total - distinctCt
            (c, dups == 0, dups)
          case c @ FFConstraint.Range(field, min, max, _) =>
            val v      = col(field.value).cast("double")
            val geMin  = min.map(m => v.geq(lit(m))).getOrElse(lit(true))
            val leMax  = max.map(m => v.leq(lit(m))).getOrElse(lit(true))
            val valid  = geMin && leMax && v.isNotNull
            val failed = df.filter(not(valid)).count()
            (c, failed == 0, failed)
          case c @ FFConstraint.Pattern(field, regex, _) =>
            val s      = col(field.value).cast("string")
            val valid  = s.isNotNull && s.rlike(regex)
            val failed = df.filter(not(valid)).count()
            (c, failed == 0, failed)
          case c @ FFConstraint.Compliance(_, predicate, _) =>
            val failed = df.filter(not(expr(predicate))).count()
            (c, failed == 0, failed)
          case c => (c, true, 0L) // unsupported => pass-through
        }
        val violations: List[DataAlgebra.QualityViolation] = results.collect {
          case (rule, false, affected) =>
            DataAlgebra.QualityViolation(
              rule = rule.toString,
              message = s"Quality rule failed: $rule",
              severity = DataAlgebra.ViolationSeverity.Warning,
              recordsAffected = affected,
            )
        }
        val total = results.size.max(1)
        val ok    = results.count { case (_, p, _) => p }
        val score = ok.toDouble / total
        DataAlgebra.QualityResult(dataset, passed = violations.isEmpty, violations = violations, score = score)
    }
  }
}
