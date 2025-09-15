package com.flowforge.core.safety

import com.flowforge.core.types.FlowForgeError
import com.flowforge.core.types.FlowForgeError.ValidationError

/**
 * Pluggable mapping from Throwable to FlowForgeError.
 *
 * Module owners can provide more specific instances (e.g., mapping JDBC/IO exceptions to connector-specific
 * domain errors). The default keeps semantics stable by delegating to FlowForgeError.fromThrowable and
 * upgrading obvious argument errors to ValidationError.
 */
trait ErrorMapper {
  def apply(t: Throwable): FlowForgeError
}

object ErrorMapper {
  implicit val default: ErrorMapper = DefaultErrorMapper
}

object DefaultErrorMapper extends ErrorMapper {
  def apply(t: Throwable): FlowForgeError = t match {
    case iae: IllegalArgumentException =>
      ValidationError(iae.getMessage, cause = Some(iae))
    case other =>
      FlowForgeError.fromThrowable(other)
  }
}
