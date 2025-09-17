package com.flowforge.quality
sealed trait Severity
object Severity {
  case object Info  extends Severity
  case object Warn  extends Severity
  case object Error extends Severity
}

final case class CheckResult(passed: Boolean, details: String)

/**
 * Engine-agnostic asset check.
 * @tparam A
 *   engine-specific dataset/collection type (e.g., Spark Dataset[_], Flink DataSet, iterable batch, etc.)
 *   `eval` encapsulates the check logic and returns a `CheckResult`.
 */
final case class AssetCheck[A](
  name: String,
  eval: A => CheckResult,
  severity: Severity,
  owner: String,
  hint: String)
