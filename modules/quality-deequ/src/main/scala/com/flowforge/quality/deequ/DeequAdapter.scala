package com.flowforge.quality.deequ

import com.amazon.deequ.VerificationResult
import com.amazon.deequ.checks.{ Check, CheckLevel }
import com.amazon.deequ.constraints.ConstraintStatus
import com.amazon.deequ.VerificationSuite
import org.apache.spark.sql.SparkSession
import com.flowforge.core.algebra.DataAlgebra
import com.flowforge.core.types.{ QualityConstraint => FFConstraint, RefinedTypes }
import com.flowforge.engines.spark.ProductionSparkDataset

/**
 * Minimal Deequ adapter: supports not_null and unique quality constraints using Deequ.
 * MVP scope: map core QualityConstraint.NotNull and QualityConstraint.Unique to Deequ checks.
 */
object DeequAdapter {

  def runChecks[A](
    spark: SparkSession,
    dataset: DataAlgebra.Dataset[A],
    constraints: List[FFConstraint],
  ): DataAlgebra.QualityResult[DataAlgebra.Dataset[A]] = {
    val dfOpt = dataset match {
      case p: ProductionSparkDataset[A] => Some(p.sparkDataFrame)
      case _                            => None
    }
    dfOpt match {
      case None =>
        DataAlgebra.QualityResult(dataset, passed = true, violations = Nil, score = 1.0)
      case Some(df) =>
        val check = constraints.foldLeft(new Check(CheckLevel.Error, "flowforge-deequ")) { (acc, qc) =>
          qc match {
            case FFConstraint.NotNull(field, _) => acc.isComplete(field.value)
            case FFConstraint.Unique(field, _)  => acc.isUnique(field.value)
            case _                                               => acc // ignore unsupported for MVP
          }
        }
        val result: VerificationResult = VerificationSuite().onData(df).addChecks(Seq(check)).run()
        val constraintResults = result.checkResults.values.flatMap(_.constraintResults).toList
        val violations: List[DataAlgebra.QualityViolation] = constraintResults.collect {
          case cr if cr.status != ConstraintStatus.Success =>
            val msg  = cr.message.getOrElse("quality constraint failed")
            val name = cr.constraint.toString
            DataAlgebra.QualityViolation(rule = name, message = msg, severity = DataAlgebra.ViolationSeverity.Warning, recordsAffected = 0L)
        }
        val allPassed = violations.isEmpty
        val score     = if (constraintResults.isEmpty) 1.0 else (constraintResults.size - violations.size).toDouble / constraintResults.size
        DataAlgebra.QualityResult(dataset, passed = allPassed, violations = violations, score = score)
      }
  }
}
