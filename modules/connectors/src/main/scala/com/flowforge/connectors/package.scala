/**
 * FlowForge Connectors Package
 *
 * Core connector definitions and abstractions for data sources and sinks.
 */
package com.flowforge

import com.flowforge.core.types._

package object connectors {

  // Type aliases for common connector types
  type DataSource      = com.flowforge.core.types.DataSource
  type DataSink        = com.flowforge.core.types.DataSink
  type DataFormat      = com.flowforge.core.types.DataFormat
  type CompressionType = com.flowforge.core.types.CompressionType

  // Common connector result types
  sealed trait ConnectorResult[+T]

  object ConnectorResult {
    case class Success[T](value: T)           extends ConnectorResult[T]
    case class Failure(error: ConnectorError) extends ConnectorResult[Nothing]

    def success[T](value: T): ConnectorResult[T]                 = Success(value)
    def failure(error: ConnectorError): ConnectorResult[Nothing] = Failure(error)
    def failure(message: String): ConnectorResult[Nothing]       = Failure(ConnectorError(message))
  }

  // File system specific result types (alias for compatibility)
  type FileSystemResult[T] = ConnectorResult[T]
  object FileSystemResult {
    def success[T](value: T): FileSystemResult[T]                 = ConnectorResult.Success(value)
    def failure(error: ConnectorError): FileSystemResult[Nothing] = ConnectorResult.Failure(error)

    // Pattern matching helpers
    object Success {
      def unapply[T](result: FileSystemResult[T]): Option[T] = result match {
        case ConnectorResult.Success(value) => Some(value)
        case _                              => None
      }
    }

    object Failure {
      def unapply(result: FileSystemResult[_]): Option[ConnectorError] = result match {
        case ConnectorResult.Failure(error) => Some(error)
        case _                              => None
      }
    }

    // Helper methods for checking results
    implicit class FileSystemResultOps[T](result: FileSystemResult[T]) {
      def isSuccess: Boolean = result match {
        case ConnectorResult.Success(_) => true
        case _                          => false
      }

      def isFailure: Boolean = !isSuccess
    }
  }

  // Connector error types
  case class ConnectorError(
    message: String,
    cause: Option[Throwable] = None,
    code: String = "CONNECTOR_ERROR")

  // File system specific error types
  object FileSystemError {
    def fileNotFound(path: String): ConnectorError =
      ConnectorError(s"File not found: $path", code = "FILE_NOT_FOUND")

    def directoryNotFound(path: String): ConnectorError =
      ConnectorError(s"Directory not found: $path", code = "DIRECTORY_NOT_FOUND")

    def accessDenied(path: String): ConnectorError =
      ConnectorError(s"Access denied: $path", code = "ACCESS_DENIED")

    def ioError(path: String, cause: Throwable): ConnectorError =
      ConnectorError(s"IO error for path: $path", Some(cause), "IO_ERROR")

    def readError(path: String, message: String): ConnectorError =
      ConnectorError(s"Read error for path $path: $message", code = "READ_ERROR")

    def writeError(path: String, message: String): ConnectorError =
      ConnectorError(s"Write error for path $path: $message", code = "WRITE_ERROR")

    def listError(path: String, message: String): ConnectorError =
      ConnectorError(s"List error for path $path: $message", code = "LIST_ERROR")

    def createDirectoryError(path: String, message: String): ConnectorError =
      ConnectorError(s"Create directory error for path $path: $message", code = "CREATE_DIR_ERROR")

    def deleteError(path: String, message: String): ConnectorError =
      ConnectorError(s"Delete error for path $path: $message", code = "DELETE_ERROR")

    def metadataError(path: String, message: String): ConnectorError =
      ConnectorError(s"Metadata error for path $path: $message", code = "METADATA_ERROR")

    def NotImplemented(operation: String): ConnectorError =
      ConnectorError(s"Operation not implemented: $operation", code = "NOT_IMPLEMENTED")
  }

  // Metadata types for connector operations
  case class WriteMetadata(
    path: String,
    bytesWritten: Long,
    recordsWritten: Option[Long] = None)

  case class FileMetadata(
    name: String,
    path: String,
    size: Long,
    lastModified: java.time.Instant,
    format: DataFormat = DataFormat.JSON)
}
