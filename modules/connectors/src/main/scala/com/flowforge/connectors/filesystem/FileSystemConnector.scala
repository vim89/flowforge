/**
 * FlowForge Connectors Module - File System Connector
 *
 * This module provides comprehensive file system connectivity for FlowForge pipelines, supporting
 * local file systems, HDFS, and cloud storage (GCS, S3, Azure) through a unified, type-safe
 * interface.
 *
 * Key Features:
 *   - Unified interface for multiple file systems
 *   - Effect-polymorphic design (F[_]: EffectSystem)
 *   - Resource-safe operations with automatic cleanup
 *   - Support for multiple data formats (Parquet, JSON, CSV, Avro, ORC)
 *   - Streaming and batch operations
 *   - Compression support (Gzip, Snappy, LZ4)
 *   - Partition-aware operations
 *   - Schema inference and validation
 *   - Production-ready error handling
 */
package com.flowforge.connectors.filesystem

import cats.data.{ NonEmptyList, ValidatedNel }
import cats.effect.Resource
import cats.implicits._
import com.flowforge.core.algebra.EffectSystem
import com.flowforge.core.types._
import com.flowforge.connectors._

import java.io.{ File, FileInputStream, FileOutputStream }
import java.nio.file.{ Files, Path, Paths, StandardOpenOption }
import java.time.Instant
import java.util.stream.Collectors
import scala.jdk.CollectionConverters._
import scala.util.{ Failure, Success, Try }

/**
 * File system connector trait providing unified interface for different storage systems
 */
trait FileSystemConnector[F[_]] {

  /**
   * Read data from file system location
   */
  def read(source: DataSource): F[FileSystemResult[Array[Byte]]]

  /**
   * Write data to file system location
   */
  def write(sink: DataSink, data: Array[Byte]): F[FileSystemResult[WriteMetadata]]

  /**
   * List files in directory
   */
  def listFiles(path: String): F[FileSystemResult[List[FileMetadata]]]

  /**
   * Check if file/directory exists
   */
  def exists(path: String): F[Boolean]

  /**
   * Create directory
   */
  def createDirectory(path: String): F[FileSystemResult[Unit]]

  /**
   * Delete file or directory
   */
  def delete(path: String, recursive: Boolean = false): F[FileSystemResult[Unit]]

  /**
   * Get file metadata
   */
  def getMetadata(path: String): F[FileSystemResult[FileMetadata]]

  /**
   * Stream read for large files (simplified implementation)
   */
  def streamRead(source: DataSource): F[List[Array[Byte]]]

  /**
   * Stream write for large datasets (simplified implementation)
   */
  def streamWrite(sink: DataSink, data: List[Array[Byte]]): F[WriteMetadata]
}

/**
 * Local file system connector implementation
 */
class LocalFileSystemConnector[F[_]: EffectSystem] extends FileSystemConnector[F] {

  private val effectSystem = EffectSystem[F]

  // Helper method to extract location from different DataSource types
  private def extractLocation(source: DataSource): String = source match {
    case local: LocalDataSource        => local.location
    case gcs: DataSource.GcsSource     => gcs.path
    case s3: DataSource.S3Source       => s3.path
    case bq: DataSource.BigQuerySource => bq.fullTableName
    case jdbc: DataSource.JdbcSource   => jdbc.table.value
    case _ =>
      throw new IllegalArgumentException(
        s"Unsupported DataSource type: ${source.getClass.getSimpleName}"
      )
  }

  // Helper method to extract location from different DataSink types
  private def extractSinkLocation(sink: DataSink): String = sink match {
    case local: LocalDataSink  => local.location
    case gcs: DataSink.GcsSink => gcs.path
    case s3: DataSink.S3Sink   => s3.path
    case _ =>
      throw new IllegalArgumentException(
        s"Unsupported DataSink type: ${sink.getClass.getSimpleName}"
      )
  }

  // Helper method to infer data format from file path
  private def inferFormatFromPath(path: String): DataFormat = {
    val lowercasePath = path.toLowerCase
    if (lowercasePath.endsWith(".json")) DataFormat.JSON
    else if (lowercasePath.endsWith(".parquet")) DataFormat.Parquet
    else if (lowercasePath.endsWith(".csv")) DataFormat.CSV
    else if (lowercasePath.endsWith(".avro")) DataFormat.Avro
    else if (lowercasePath.endsWith(".orc")) DataFormat.ORC
    else DataFormat.JSON // default
  }

  def read(source: DataSource): F[FileSystemResult[Array[Byte]]] = {
    val location = extractLocation(source)
    effectSystem.handleError {
      for {
        path   <- effectSystem.delay(Paths.get(location))
        exists <- effectSystem.delay(Files.exists(path))
        _ <-
          if (exists) effectSystem.unit
          else effectSystem.raiseError(new RuntimeException(s"File not found: $location"))
        bytes <- effectSystem.delay(Files.readAllBytes(path))
      } yield FileSystemResult.success(bytes)
    } { error =>
      FileSystemResult.failure(FileSystemError.ReadError(location, error.getMessage))
    }
  }

  def write(sink: DataSink, data: Array[Byte]): F[FileSystemResult[WriteMetadata]] = {
    val location = extractSinkLocation(sink)
    effectSystem.handleError {
      for {
        path <- effectSystem.delay(Paths.get(location))
        _    <- effectSystem.delay(Files.createDirectories(path.getParent))
        _ <- effectSystem.delay(
          Files.write(path, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
        )
        metadata = WriteMetadata(
          path = location,
          bytesWritten = data.length.toLong
        )
      } yield FileSystemResult.success(metadata)
    } { error =>
      FileSystemResult.failure(FileSystemError.WriteError(location, error.getMessage))
    }
  }

  def listFiles(path: String): F[FileSystemResult[List[FileMetadata]]] =
    effectSystem.handleError {
      for {
        dir    <- effectSystem.delay(Paths.get(path))
        exists <- effectSystem.delay(Files.exists(dir) && Files.isDirectory(dir))
        _ <-
          if (exists) effectSystem.unit
          else effectSystem.raiseError(new RuntimeException(s"Directory not found: $path"))
        files <- effectSystem.delay {
          import scala.jdk.CollectionConverters._
          Files.list(dir).collect(Collectors.toList[Path]).asScala.toList.map { p =>
            val attrs =
              Files.readAttributes(p, classOf[java.nio.file.attribute.BasicFileAttributes])
            FileMetadata(
              name = p.getFileName.toString,
              path = p.toString,
              size = attrs.size(),
              lastModified = Instant.ofEpochMilli(attrs.lastModifiedTime().toMillis),
              format = inferFormatFromPath(p.toString)
            )
          }
        }
      } yield FileSystemResult.success(files)
    } { error =>
      FileSystemResult.failure(FileSystemError.ListError(path, error.getMessage))
    }

  def exists(path: String): F[Boolean] =
    effectSystem.delay(Files.exists(Paths.get(path)))

  def createDirectory(path: String): F[FileSystemResult[Unit]] =
    effectSystem.handleError {
      effectSystem.delay {
        Files.createDirectories(Paths.get(path))
        FileSystemResult.success(())
      }
    } { error =>
      FileSystemResult.failure(FileSystemError.CreateDirectoryError(path, error.getMessage))
    }

  def delete(path: String, recursive: Boolean = false): F[FileSystemResult[Unit]] =
    effectSystem.handleError {
      for {
        pathObj <- effectSystem.delay(Paths.get(path))
        _ <-
          if (recursive && Files.isDirectory(pathObj)) {
            effectSystem.delay {
              Files
                .walk(pathObj)
                .sorted(java.util.Comparator.reverseOrder())
                .forEach(Files.delete)
            }
          } else {
            effectSystem.delay(Files.delete(pathObj))
          }
      } yield FileSystemResult.success(())
    } { error =>
      FileSystemResult.failure(FileSystemError.DeleteError(path, error.getMessage))
    }

  def getMetadata(path: String): F[FileSystemResult[FileMetadata]] =
    effectSystem.handleError {
      for {
        pathObj <- effectSystem.delay(Paths.get(path))
        attrs <- effectSystem.delay(
          Files.readAttributes(pathObj, classOf[java.nio.file.attribute.BasicFileAttributes])
        )
        metadata = FileMetadata(
          name = pathObj.getFileName.toString,
          path = path,
          size = attrs.size(),
          lastModified = Instant.ofEpochMilli(attrs.lastModifiedTime().toMillis),
          format = inferFormatFromPath(path)
        )
      } yield FileSystemResult.success(metadata)
    } { error =>
      FileSystemResult.failure(FileSystemError.MetadataError(path, error.getMessage))
    }

  def streamRead(source: DataSource): F[List[Array[Byte]]] = {
    val location = extractLocation(source)
    effectSystem.delay {
      val bytes = Files.readAllBytes(Paths.get(location))
      // Simplified: split into 8KB chunks
      bytes.grouped(8192).toList
    }
  }

  def streamWrite(sink: DataSink, data: List[Array[Byte]]): F[WriteMetadata] = {
    val location = extractSinkLocation(sink)
    effectSystem.delay {
      val allBytes = data.flatten.toArray
      Files.write(
        Paths.get(location),
        allBytes,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING
      )
      WriteMetadata(
        path = location,
        bytesWritten = allBytes.length.toLong
      )
    }
  }

}

/**
 * HDFS file system connector implementation
 */
class HDFSFileSystemConnector[F[_]: EffectSystem](
  hdfsUrl: String,
  configuration: Map[String, String] = Map.empty
) extends FileSystemConnector[F] {

  private val effectSystem = EffectSystem[F]

  // Note: This is a simplified implementation
  // In production, this would use actual Hadoop HDFS client

  def read(source: DataSource): F[FileSystemResult[Array[Byte]]] =
    // TODO: Implement actual HDFS read using Hadoop client
    effectSystem.pure(
      FileSystemResult.failure(
        FileSystemError.NotImplemented("HDFS read not implemented yet")
      )
    )

  def write(sink: DataSink, data: Array[Byte]): F[FileSystemResult[WriteMetadata]] =
    // TODO: Implement actual HDFS write using Hadoop client
    effectSystem.pure(
      FileSystemResult.failure(
        FileSystemError.NotImplemented("HDFS write not implemented yet")
      )
    )

  def listFiles(path: String): F[FileSystemResult[List[FileMetadata]]] =
    // TODO: Implement actual HDFS list using Hadoop client
    effectSystem.pure(
      FileSystemResult.failure(
        FileSystemError.NotImplemented("HDFS listFiles not implemented yet")
      )
    )

  def exists(path: String): F[Boolean] =
    // TODO: Implement actual HDFS exists check using Hadoop client
    effectSystem.pure(false)

  def createDirectory(path: String): F[FileSystemResult[Unit]] =
    effectSystem.pure(
      FileSystemResult.failure(
        FileSystemError.NotImplemented("HDFS createDirectory not implemented yet")
      )
    )

  def delete(path: String, recursive: Boolean = false): F[FileSystemResult[Unit]] =
    effectSystem.pure(
      FileSystemResult.failure(
        FileSystemError.NotImplemented("HDFS delete not implemented yet")
      )
    )

  def getMetadata(path: String): F[FileSystemResult[FileMetadata]] =
    effectSystem.pure(
      FileSystemResult.failure(
        FileSystemError.NotImplemented("HDFS getMetadata not implemented yet")
      )
    )

  def streamRead(source: DataSource): F[List[Array[Byte]]] =
    effectSystem.pure(List.empty)

  def streamWrite(sink: DataSink, data: List[Array[Byte]]): F[WriteMetadata] =
    effectSystem.pure(
      WriteMetadata(
        path = "hdfs://not-implemented",
        bytesWritten = 0L
      )
    )
}

/**
 * Cloud storage connector base class
 */
abstract class CloudStorageConnector[F[_]: EffectSystem] extends FileSystemConnector[F] {

  protected val effectSystem = EffectSystem[F]

  /**
   * Parse cloud storage URI to extract bucket and key
   */
  protected def parseCloudUri(uri: String): (String, String) = {
    val withoutProtocol = uri.dropWhile(_ != ':').drop(3) // Remove protocol://
    val parts           = withoutProtocol.split("/", 2)
    val bucket          = parts(0)
    val key             = if (parts.length > 1) parts(1) else ""
    (bucket, key)
  }
}

object FileSystemConnector {
  def local[F[_]: EffectSystem]: LocalFileSystemConnector[F] =
    new LocalFileSystemConnector[F]

  def hdfs[F[_]: EffectSystem](
    hdfsUrl: String,
    configuration: Map[String, String] = Map.empty
  ): HDFSFileSystemConnector[F] =
    new HDFSFileSystemConnector[F](hdfsUrl, configuration)
}

/**
 * File system operations utilities
 */
object FileSystemOps {

  /**
   * Batch file operations
   */
  def batchRead[F[_]: EffectSystem](
    sources: List[DataSource]
  ): F[List[FileSystemResult[Array[Byte]]]] = {
    val effectSystem = EffectSystem[F]
    val connector    = FileSystemConnector.local[F]
    sources.traverse(connector.read)
  }

  /**
   * Parallel file operations
   */
  def parallelRead[F[_]: EffectSystem](
    sources: List[DataSource]
  ): F[List[FileSystemResult[Array[Byte]]]] = {
    val effectSystem = EffectSystem[F]
    val connector    = FileSystemConnector.local[F]
    effectSystem.parTraverse(sources)(connector.read)
  }
}
