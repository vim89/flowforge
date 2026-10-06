/**
 * FlowForge Connectors Module - File System Connector
 *
 * This module provides comprehensive file system connectivity for FlowForge pipelines, supporting local file
 * systems, HDFS, and cloud storage (GCS, S3, Azure) through a unified, type-safe interface.
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

import cats.implicits._
import com.flowforge.connectors._
import com.flowforge.core.algebra.EffectSystem
import com.flowforge.core.types._

import java.nio.file.{ Files, Path, Paths, StandardOpenOption }
import java.time.Instant
import java.util.stream.Collectors

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
 * Where an operation reads from or writes to.
 *
 * Every connector below asked this same question with the same code, and none of them could answer it for a
 * source kind it does not handle. The answer is an Option rather than a String so that "I cannot address
 * this" is a value the caller can report, not an exception thrown out of a method that promised a path.
 */
private[filesystem] trait LocationResolution[F[_]] {

  protected val effectSystem: EffectSystem[F]

  /** How this connector describes what it accepts, used in the message when something else arrives. */
  protected def addressable: String = "a supported"

  /** The path or table this source names, or None for a source kind this connector cannot address. */
  protected def locationOf(source: DataSource): Option[String] = source match {
    case local: LocalDataSource        => Some(local.location)
    case gcs: DataSource.GcsSource     => Some(gcs.path)
    case s3: DataSource.S3Source       => Some(s3.path)
    case bq: DataSource.BigQuerySource => Some(bq.fullTableName)
    case jdbc: DataSource.JdbcSource   => Some(jdbc.table.value)
    case _                             => None
  }

  /** The path this sink names, or None for a sink kind this connector cannot address. */
  protected def locationOf(sink: DataSink): Option[String] = sink match {
    case local: LocalDataSink  => Some(local.location)
    case gcs: DataSink.GcsSink => Some(gcs.path)
    case s3: DataSink.S3Sink   => Some(s3.path)
    case _                     => None
  }

  /** Runs `op` on the source's location. An unusable source comes back as a failure result. */
  protected def onLocation[A](
    source: DataSource,
  )(
    op: String => F[FileSystemResult[A]],
  ): F[FileSystemResult[A]] =
    locationOf(source).fold(
      effectSystem.pure[FileSystemResult[A]](
        FileSystemResult.failure(unsupported("source", source.getClass)),
      ),
    )(op)

  /** Runs `op` on the sink's location. An unusable sink comes back as a failure result. */
  protected def onSinkLocation[A](
    sink: DataSink,
  )(
    op: String => F[FileSystemResult[A]],
  ): F[FileSystemResult[A]] =
    locationOf(sink).fold(
      effectSystem.pure[FileSystemResult[A]](
        FileSystemResult.failure(unsupported("sink", sink.getClass)),
      ),
    )(op)

  /**
   * The streaming variant. Those operations return the payload itself, with no room in the type for a failure
   * value, so an unusable source travels in the effect's error channel instead.
   */
  protected def onLocationOrRaise[A](source: DataSource)(op: String => F[A]): F[A] =
    locationOf(source).fold(
      effectSystem.raiseError[A](ConnectorFailure(unsupported("source", source.getClass))),
    )(op)

  /** The streaming variant for sinks. See [[onLocationOrRaise]]. */
  protected def onSinkLocationOrRaise[A](sink: DataSink)(op: String => F[A]): F[A] =
    locationOf(sink).fold(
      effectSystem.raiseError[A](ConnectorFailure(unsupported("sink", sink.getClass))),
    )(op)

  private def unsupported(kind: String, got: Class[_]): ConnectorError =
    ConnectorError(
      s"Expected $addressable $kind, got: ${got.getSimpleName}",
      code = s"UNSUPPORTED_${kind.toUpperCase}",
    )
}

/**
 * Local file system connector implementation
 */
class LocalFileSystemConnector[F[_]: EffectSystem] extends FileSystemConnector[F] with LocationResolution[F] {

  protected val effectSystem: EffectSystem[F] = EffectSystem[F]

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

  def read(source: DataSource): F[FileSystemResult[Array[Byte]]] = onLocation(source) { location =>
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
      FileSystemResult.failure(FileSystemError.readError(location, error.getMessage))
    }
  }

  def write(sink: DataSink, data: Array[Byte]): F[FileSystemResult[WriteMetadata]] =
    onSinkLocation(sink) { location =>
      effectSystem.handleError {
        for {
          path <- effectSystem.delay(Paths.get(location))
          _    <- effectSystem.delay(Files.createDirectories(path.getParent))
          _ <- effectSystem.delay(
            Files.write(path, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING),
          )
          metadata = WriteMetadata(
            path = location,
            bytesWritten = data.length.toLong,
          )
        } yield FileSystemResult.success(metadata)
      } { error =>
        FileSystemResult.failure(FileSystemError.writeError(location, error.getMessage))
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
              format = inferFormatFromPath(p.toString),
            )
          }
        }
      } yield FileSystemResult.success(files)
    } { error =>
      FileSystemResult.failure(FileSystemError.listError(path, error.getMessage))
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
      FileSystemResult.failure(FileSystemError.createDirectoryError(path, error.getMessage))
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
      FileSystemResult.failure(FileSystemError.deleteError(path, error.getMessage))
    }

  def getMetadata(path: String): F[FileSystemResult[FileMetadata]] =
    effectSystem.handleError {
      for {
        pathObj <- effectSystem.delay(Paths.get(path))
        attrs <- effectSystem.delay(
          Files.readAttributes(pathObj, classOf[java.nio.file.attribute.BasicFileAttributes]),
        )
        metadata = FileMetadata(
          name = pathObj.getFileName.toString,
          path = path,
          size = attrs.size(),
          lastModified = Instant.ofEpochMilli(attrs.lastModifiedTime().toMillis),
          format = inferFormatFromPath(path),
        )
      } yield FileSystemResult.success(metadata)
    } { error =>
      FileSystemResult.failure(FileSystemError.metadataError(path, error.getMessage))
    }

  def streamRead(source: DataSource): F[List[Array[Byte]]] = onLocationOrRaise(source) { location =>
    effectSystem.delay {
      val bytes = Files.readAllBytes(Paths.get(location))
      // Simplified: split into 8KB chunks
      bytes.grouped(8192).toList
    }
  }

  def streamWrite(sink: DataSink, data: List[Array[Byte]]): F[WriteMetadata] =
    onSinkLocationOrRaise(sink) { location =>
      effectSystem.delay {
        val allBytes = data.flatten.toArray
        Files.write(
          Paths.get(location),
          allBytes,
          StandardOpenOption.CREATE,
          StandardOpenOption.TRUNCATE_EXISTING,
        )
        WriteMetadata(
          path = location,
          bytesWritten = allBytes.length.toLong,
        )
      }
    }

}

/**
 * HDFS file system connector implementation with production-ready Hadoop client integration
 */
class HDFSFileSystemConnector[F[_]: EffectSystem](
  hdfsUrl: String,
  configuration: Map[String, String] = Map.empty)
    extends FileSystemConnector[F]
    with LocationResolution[F] {

  protected val effectSystem: EffectSystem[F] = EffectSystem[F]

  // Production-ready HDFS configuration setup
  private def createHadoopConfiguration(): org.apache.hadoop.conf.Configuration = {
    val conf = new org.apache.hadoop.conf.Configuration()
    conf.set("fs.defaultFS", hdfsUrl)

    // Apply custom configuration
    configuration.foreach { case (key, value) => conf.set(key, value) }

    // Common HDFS client configurations
    conf.set("dfs.client.use.datanode.hostname", "true")
    conf.setInt("dfs.client.socket.timeout", 60000)
    conf.setInt("dfs.client.read.timeout", 60000)

    conf
  }

  def read(source: DataSource): F[FileSystemResult[Array[Byte]]] = onLocation(source) { location =>
    effectSystem.handleError {
      val acquire = effectSystem.blocking {
        val conf = createHadoopConfiguration(); org.apache.hadoop.fs.FileSystem.get(conf)
      }
      effectSystem.bracket(acquire) { fs =>
        effectSystem.blocking {
          val path = new org.apache.hadoop.fs.Path(location)
          if (!fs.exists(path)) FileSystemResult.failure(FileSystemError.fileNotFound(location))
          else {
            val inputStream = fs.open(path)
            val bytes       = inputStream.readAllBytes()
            inputStream.close()
            FileSystemResult.success(bytes)
          }
        }
      }(fs => effectSystem.blocking(fs.close()).void)
    }(error => FileSystemResult.failure(FileSystemError.readError(location, error.getMessage)))
  }

  def write(sink: DataSink, data: Array[Byte]): F[FileSystemResult[WriteMetadata]] =
    onSinkLocation(sink) { location =>
      effectSystem.handleError {
        val acquire = effectSystem.blocking {
          val conf = createHadoopConfiguration(); org.apache.hadoop.fs.FileSystem.get(conf)
        }
        effectSystem.bracket(acquire) { fs =>
          effectSystem.blocking {
            val path         = new org.apache.hadoop.fs.Path(location)
            val outputStream = fs.create(path, true)
            outputStream.write(data)
            outputStream.flush()
            outputStream.close()
            FileSystemResult.success(
              WriteMetadata(path = location, bytesWritten = data.length.toLong),
            )
          }
        }(fs => effectSystem.blocking(fs.close()).void)
      }(error => FileSystemResult.failure(FileSystemError.writeError(location, error.getMessage)))
    }

  def listFiles(path: String): F[FileSystemResult[List[FileMetadata]]] =
    effectSystem.handleError {
      val conf    = createHadoopConfiguration()
      val acquire = effectSystem.blocking(org.apache.hadoop.fs.FileSystem.get(conf))
      effectSystem.bracket(acquire) { fs =>
        effectSystem.blocking {
          val hadoopPath = new org.apache.hadoop.fs.Path(path)
          if (!fs.exists(hadoopPath) || !fs.isDirectory(hadoopPath)) {
            FileSystemResult.failure(FileSystemError.directoryNotFound(path))
          } else {
            val fileStatuses = fs.listStatus(hadoopPath)
            val metadata = fileStatuses.map { status =>
              FileMetadata(
                name = status.getPath.getName,
                path = status.getPath.toString,
                size = status.getLen,
                lastModified = java.time.Instant.ofEpochMilli(status.getModificationTime),
                format = inferFormatFromPath(status.getPath.toString),
              )
            }.toList
            FileSystemResult.success(metadata)
          }
        }
      }(fs => effectSystem.blocking(fs.close()).void)
    }(error => FileSystemResult.failure(FileSystemError.listError(path, error.getMessage)))

  def exists(path: String): F[Boolean] =
    effectSystem.handleError {
      val conf    = createHadoopConfiguration()
      val acquire = effectSystem.blocking(org.apache.hadoop.fs.FileSystem.get(conf))
      effectSystem.bracket(acquire) { fs =>
        effectSystem.blocking(fs.exists(new org.apache.hadoop.fs.Path(path)))
      }(fs => effectSystem.blocking(fs.close()).void)
    }(_ => false)

  def createDirectory(path: String): F[FileSystemResult[Unit]] =
    effectSystem.handleError {
      val conf    = createHadoopConfiguration()
      val acquire = effectSystem.blocking(org.apache.hadoop.fs.FileSystem.get(conf))
      effectSystem.bracket(acquire) { fs =>
        effectSystem.blocking {
          val hadoopPath = new org.apache.hadoop.fs.Path(path)
          val success    = fs.mkdirs(hadoopPath)
          if (success) FileSystemResult.success(())
          else
            FileSystemResult.failure(FileSystemError.createDirectoryError(path, "Failed to create directory"))
        }
      }(fs => effectSystem.blocking(fs.close()).void)
    }(error => FileSystemResult.failure(FileSystemError.createDirectoryError(path, error.getMessage)))

  def delete(path: String, recursive: Boolean = false): F[FileSystemResult[Unit]] =
    effectSystem.handleError {
      val conf    = createHadoopConfiguration()
      val acquire = effectSystem.blocking(org.apache.hadoop.fs.FileSystem.get(conf))
      effectSystem.bracket(acquire) { fs =>
        effectSystem.blocking {
          val hadoopPath = new org.apache.hadoop.fs.Path(path)
          val success    = fs.delete(hadoopPath, recursive)
          if (success) FileSystemResult.success(())
          else FileSystemResult.failure(FileSystemError.deleteError(path, "Failed to delete"))
        }
      }(fs => effectSystem.blocking(fs.close()).void)
    }(error => FileSystemResult.failure(FileSystemError.deleteError(path, error.getMessage)))

  def getMetadata(path: String): F[FileSystemResult[FileMetadata]] =
    effectSystem.handleError {
      val conf    = createHadoopConfiguration()
      val acquire = effectSystem.blocking(org.apache.hadoop.fs.FileSystem.get(conf))
      effectSystem.bracket(acquire) { fs =>
        effectSystem.blocking {
          val hadoopPath = new org.apache.hadoop.fs.Path(path)
          if (!fs.exists(hadoopPath)) FileSystemResult.failure(FileSystemError.fileNotFound(path))
          else {
            val status = fs.getFileStatus(hadoopPath)
            val metadata = FileMetadata(
              name = status.getPath.getName,
              path = path,
              size = status.getLen,
              lastModified = java.time.Instant.ofEpochMilli(status.getModificationTime),
              format = inferFormatFromPath(path),
            )
            FileSystemResult.success(metadata)
          }
        }
      }(fs => effectSystem.blocking(fs.close()).void)
    }(error => FileSystemResult.failure(FileSystemError.metadataError(path, error.getMessage)))

  def streamRead(source: DataSource): F[List[Array[Byte]]] = onLocationOrRaise(source) { location =>
    effectSystem.bracket(
      effectSystem.blocking(org.apache.hadoop.fs.FileSystem.get(createHadoopConfiguration())),
    ) { fs =>
      val path = new org.apache.hadoop.fs.Path(location)
      effectSystem.bracket(effectSystem.blocking(fs.open(path))) { inputStream =>
        effectSystem.blocking {
          val bytes = inputStream.readAllBytes()
          bytes.grouped(8192).toList
        }
      }(is => effectSystem.blocking(is.close()).void)
    }(fs => effectSystem.blocking(fs.close()).void)
  }

  def streamWrite(sink: DataSink, data: List[Array[Byte]]): F[WriteMetadata] =
    onSinkLocationOrRaise(sink) { location =>
      effectSystem.bracket(
        effectSystem.blocking(org.apache.hadoop.fs.FileSystem.get(createHadoopConfiguration())),
      ) { fs =>
        val path = new org.apache.hadoop.fs.Path(location)
        effectSystem.bracket(effectSystem.blocking(fs.create(path, true))) { outputStream =>
          effectSystem.blocking {
            val totalBytes = data.map(_.length.toLong).sum
            data.foreach(outputStream.write)
            outputStream.flush()
            WriteMetadata(path = location, bytesWritten = totalBytes)
          }
        }(os => effectSystem.blocking(os.close()).void)
      }(fs => effectSystem.blocking(fs.close()).void)
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
}

/**
 * Cloud storage connector base class
 */
abstract class CloudStorageConnector[F[_]: EffectSystem]
    extends FileSystemConnector[F]
    with LocationResolution[F] {

  protected val effectSystem: EffectSystem[F] = EffectSystem[F]

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

/**
 * Production-ready Google Cloud Storage (GCS) connector
 */
class GCSConnector[F[_]: EffectSystem](
  projectId: String,
  serviceAccountPath: Option[String] = None,
  configuration: Map[String, String] = Map.empty)
    extends CloudStorageConnector[F] {

  override protected def addressable: String = "a GCS"

  // This connector talks to one bucket store, so it narrows what the shared resolution accepts.
  override protected def locationOf(source: DataSource): Option[String] = source match {
    case gcs: DataSource.GcsSource => Some(gcs.path)
    case _                         => None
  }

  override protected def locationOf(sink: DataSink): Option[String] = sink match {
    case gcs: DataSink.GcsSink => Some(gcs.path)
    case _                     => None
  }

  // Create GCS client with proper authentication
  private def createStorageClient(): com.google.cloud.storage.Storage = {
    import com.google.cloud.storage.StorageOptions
    import com.google.auth.oauth2.ServiceAccountCredentials
    import java.io.FileInputStream

    val builder = StorageOptions.newBuilder().setProjectId(projectId)

    serviceAccountPath.foreach { path =>
      val credentials = ServiceAccountCredentials.fromStream(new FileInputStream(path))
      builder.setCredentials(credentials)
    }

    builder.build().getService
  }

  def read(source: DataSource): F[FileSystemResult[Array[Byte]]] = onLocation(source) { gcsPath =>
    val (bucket, key) = parseCloudUri(gcsPath)

    effectSystem.handleError {
      effectSystem.blocking {
        val storage = createStorageClient()
        // The GCS client returns null for a blob that is not there, so Option is the lookup here.
        Option(storage.get(bucket, key)).filter(_.exists()) match {
          case None       => FileSystemResult.failure(FileSystemError.fileNotFound(gcsPath))
          case Some(blob) => FileSystemResult.success(blob.getContent())
        }
      }
    } { error =>
      FileSystemResult.failure(FileSystemError.readError(gcsPath, error.getMessage))
    }
  }

  def write(sink: DataSink, data: Array[Byte]): F[FileSystemResult[WriteMetadata]] = onSinkLocation(sink) {
    gcsPath =>
      val (bucket, key) = parseCloudUri(gcsPath)

      effectSystem.handleError {
        effectSystem.blocking {
          val storage = createStorageClient()
          import com.google.cloud.storage.BlobInfo
          import com.google.cloud.storage.BlobId

          val blobId   = BlobId.of(bucket, key)
          val blobInfo = BlobInfo.newBuilder(blobId).build()
          storage.create(blobInfo, data)

          FileSystemResult.success(
            WriteMetadata(
              path = gcsPath,
              bytesWritten = data.length.toLong,
            ),
          )
        }
      } { error =>
        FileSystemResult.failure(FileSystemError.writeError(gcsPath, error.getMessage))
      }
  }

  def listFiles(path: String): F[FileSystemResult[List[FileMetadata]]] = {
    val (bucket, prefix) = parseCloudUri(path)

    effectSystem.handleError {
      effectSystem.blocking {
        val storage = createStorageClient()
        import com.google.cloud.storage.Storage.BlobListOption
        import scala.jdk.CollectionConverters._

        val blobs = storage.list(bucket, BlobListOption.prefix(prefix)).iterateAll().asScala
        val metadata = blobs.map { blob =>
          FileMetadata(
            name = blob.getName,
            path = s"gs://$bucket/${blob.getName}",
            size = blob.getSize,
            lastModified = java.time.Instant.ofEpochMilli(blob.getUpdateTime),
            format = inferFormatFromPath(blob.getName),
          )
        }.toList

        FileSystemResult.success(metadata)
      }
    } { error =>
      FileSystemResult.failure(FileSystemError.listError(path, error.getMessage))
    }
  }

  def exists(path: String): F[Boolean] = {
    val (bucket, key) = parseCloudUri(path)
    effectSystem.handleError {
      effectSystem.blocking {
        val storage = createStorageClient()
        Option(storage.get(bucket, key)).exists(_.exists())
      }
    }(_ => false)
  }

  def createDirectory(path: String): F[FileSystemResult[Unit]] = {
    // GCS doesn't have directories, but we can create a placeholder object
    val (bucket, key) = parseCloudUri(path)
    val dirKey        = if (key.endsWith("/")) key else s"$key/"

    effectSystem.handleError {
      effectSystem.blocking {
        val storage = createStorageClient()
        import com.google.cloud.storage.BlobInfo
        import com.google.cloud.storage.BlobId

        val blobId   = BlobId.of(bucket, s"${dirKey}_$$folder$$")
        val blobInfo = BlobInfo.newBuilder(blobId).build()
        storage.create(blobInfo, Array.empty[Byte])

        FileSystemResult.success(())
      }
    } { error =>
      FileSystemResult.failure(FileSystemError.createDirectoryError(path, error.getMessage))
    }
  }

  def delete(path: String, recursive: Boolean = false): F[FileSystemResult[Unit]] = {
    val (bucket, key) = parseCloudUri(path)

    effectSystem.handleError {
      effectSystem.blocking {
        val storage = createStorageClient()

        if (recursive) {
          import com.google.cloud.storage.Storage.BlobListOption
          import scala.jdk.CollectionConverters._

          val blobs = storage.list(bucket, BlobListOption.prefix(key)).iterateAll().asScala
          blobs.foreach(blob => storage.delete(blob.getBlobId))
          FileSystemResult.success(())
        } else if (storage.delete(bucket, key)) {
          FileSystemResult.success(())
        } else {
          // A single-object delete reports a missing object by returning false rather than raising. This used
          // to `return` from inside the blocking thunk, which never reached the caller.
          FileSystemResult.failure(
            FileSystemError.deleteError(path, "File not found or already deleted"),
          )
        }
      }
    } { error =>
      FileSystemResult.failure(FileSystemError.deleteError(path, error.getMessage))
    }
  }

  def getMetadata(path: String): F[FileSystemResult[FileMetadata]] = {
    val (bucket, key) = parseCloudUri(path)

    effectSystem.handleError {
      effectSystem.blocking {
        val storage = createStorageClient()
        Option(storage.get(bucket, key)).filter(_.exists()) match {
          case None => FileSystemResult.failure(FileSystemError.fileNotFound(path))
          case Some(blob) =>
            FileSystemResult.success(
              FileMetadata(
                name = blob.getName,
                path = path,
                size = blob.getSize,
                lastModified = java.time.Instant.ofEpochMilli(blob.getUpdateTime),
                format = inferFormatFromPath(blob.getName),
              ),
            )
        }
      }
    } { error =>
      FileSystemResult.failure(FileSystemError.metadataError(path, error.getMessage))
    }
  }

  def streamRead(source: DataSource): F[List[Array[Byte]]] = onLocationOrRaise(source) { gcsPath =>
    val (bucket, key) = parseCloudUri(gcsPath)

    effectSystem.blocking {
      val storage = createStorageClient()
      val blob    = storage.get(bucket, key)
      val bytes   = blob.getContent()
      // Split into 8KB chunks for streaming
      bytes.grouped(8192).toList
    }
  }

  def streamWrite(sink: DataSink, data: List[Array[Byte]]): F[WriteMetadata] = onSinkLocationOrRaise(sink) {
    gcsPath =>
      val (bucket, key) = parseCloudUri(gcsPath)

      effectSystem.blocking {
        val storage = createStorageClient()
        import com.google.cloud.storage.BlobInfo
        import com.google.cloud.storage.BlobId

        val allBytes = data.flatten.toArray
        val blobId   = BlobId.of(bucket, key)
        val blobInfo = BlobInfo.newBuilder(blobId).build()
        storage.create(blobInfo, allBytes)

        WriteMetadata(
          path = gcsPath,
          bytesWritten = allBytes.length.toLong,
        )
      }
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
}

object FileSystemConnector {
  def local[F[_]: EffectSystem]: LocalFileSystemConnector[F] =
    new LocalFileSystemConnector[F]

  def hdfs[F[_]: EffectSystem](
    hdfsUrl: String,
    configuration: Map[String, String] = Map.empty,
  ): HDFSFileSystemConnector[F] =
    new HDFSFileSystemConnector[F](hdfsUrl, configuration)

  def gcs[F[_]: EffectSystem](
    projectId: String,
    serviceAccountPath: Option[String] = None,
    configuration: Map[String, String] = Map.empty,
  ): GCSConnector[F] =
    new GCSConnector[F](projectId, serviceAccountPath, configuration)
}

/**
 * File system operations utilities
 */
object FileSystemOps {

  /**
   * Batch file operations
   */
  def batchRead[F[_]: EffectSystem](
    sources: List[DataSource],
  ): F[List[FileSystemResult[Array[Byte]]]] = {
    EffectSystem[F]
    val connector = FileSystemConnector.local[F]
    sources.traverse(connector.read)
  }

  /**
   * Parallel file operations
   */
  def parallelRead[F[_]: EffectSystem](
    sources: List[DataSource],
  ): F[List[FileSystemResult[Array[Byte]]]] = {
    val effectSystem = EffectSystem[F]
    val connector    = FileSystemConnector.local[F]
    effectSystem.parTraverse(sources)(connector.read)
  }
}
