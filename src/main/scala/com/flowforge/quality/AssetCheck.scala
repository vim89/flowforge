package com.flowforge.quality
import org.apache.spark.sql.Dataset
sealed trait Severity
object Severity {
  case object Info extends Severity; case object Warn extends Severity; case object Error extends Severity
}
final case class CheckResult(passed: Boolean, details: String)
final case class AssetCheck(
  name: String,
  eval: Dataset[_] => CheckResult,
  severity: Severity,
  owner: String,
  hint: String)
