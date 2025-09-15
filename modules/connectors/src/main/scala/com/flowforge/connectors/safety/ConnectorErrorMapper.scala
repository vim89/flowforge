package com.flowforge.connectors.safety

import com.flowforge.core.safety.ErrorMapper
import com.flowforge.core.types.FlowForgeError
import com.flowforge.core.types.FlowForgeError.{ ConfigurationError, ValidationError }
import com.flowforge.core.types.SystemError

/**
 * Connector-focused ErrorMapper.
 *
 * Maps common connector exceptions to FlowForge domain errors for consistency across modules. Use by
 * importing `ConnectorErrorMapper._` in connector edges where Safety.in[F] is applied.
 */
object ConnectorErrorMapper {
  implicit val connectorMapper: ErrorMapper = {
    case e: java.nio.file.NoSuchFileException =>
      ValidationError(s"File not found: ${e.getMessage}", None, context = Map("cause" -> "NoSuchFile"))
        .withCause(e)
    case e: java.io.FileNotFoundException =>
      ValidationError(s"File not found: ${e.getMessage}", None, context = Map("cause" -> "FileNotFound"))
        .withCause(e)
    case e: java.io.IOException =>
      SystemError.ServiceUnavailable(serviceName = "filesystem", message = e.getMessage, cause = Some(e))
    case e: java.sql.SQLException =>
      ConfigurationError(
        s"JDBC error: ${e.getMessage}",
        context = Map("sqlState" -> String.valueOf(e.getSQLState)),
      ).withCause(e)
    case other => ErrorMapper.default(other)
  }
}
