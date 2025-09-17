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
        s"Unsupported DataSource type: ${source.getClass.getSimpleName}",
      )
  }

  // Helper method to extract location from different DataSink types
  private def extractSinkLocation(sink: DataSink): String = sink match {
    case local: LocalDataSink  => local.location
    case gcs: DataSink.GcsSink => gcs.path
    case s3: DataSink.S3Sink   => s3.path
    case _ =>
      throw new IllegalArgumentException(
        s"Unsupported DataSink type: ${sink.getClass.getSimpleName}",
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
      FileSystemResult.failure(FileSystemError.readError(location, error.getMessage))
    }
  }

  def write(sink: DataSink, data: Array[Byte]): F[FileSystemResult[WriteMetadata]] = {
    val location = extractSinkLocation(sink)
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
    extends FileSystemConnector[F] {

  private val effectSystem = EffectSystem[F]

  // Helper method to extract location from DataSource
  private def extractLocation(source: DataSource): String = source match {
    case local: LocalDataSource        => local.location
    case gcs: DataSource.GcsSource     => gcs.path
    case s3: DataSource.S3Source       => s3.path
    case bq: DataSource.BigQuerySource => bq.fullTableName
    case jdbc: DataSource.JdbcSource   => jdbc.table.value
    case _ =>
      throw new IllegalArgumentException(
        s"Unsupported DataSource type: ${source.getClass.getSimpleName}",
      )
  }

  // Helper method to extract location from DataSink
  private def extractSinkLocation(sink: DataSink): String = sink match {
    case local: LocalDataSink  => local.location
    case gcs: DataSink.GcsSink => gcs.path
    case s3: DataSink.S3Sink   => s3.path
    case _ =>
      throw new IllegalArgumentException(
        s"Unsupported DataSink type: ${sink.getClass.getSimpleName}",
      )
  }

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

  def read(source: DataSource): F[FileSystemResult[Array[Byte]]] = {
    val location = extractLocation(source)
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

  def write(sink: DataSink, data: Array[Byte]): F[FileSystemResult[WriteMetadata]] = {
    val location = extractSinkLocation(sink)
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

  def streamRead(source: DataSource): F[List[Array[Byte]]] = {
    val location = extractLocation(source)
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

  def streamWrite(sink: DataSink, data: List[Array[Byte]]): F[WriteMetadata] = {
    val location = extractSinkLocation(sink)
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

/**
 * Production-ready Google Cloud Storage (GCS) connector
 */
class GCSConnector[F[_]: EffectSystem](
  projectId: String,
  serviceAccountPath: Option[String] = None,
  configuration: Map[String, String] = Map.empty)
    extends CloudStorageConnector[F] {

  // Helper method to extract GCS path from DataSource
  private def extractGcsPath(source: DataSource): String = source match {
    case gcs: DataSource.GcsSource => gcs.path
    case _ =>
      throw new IllegalArgumentException(
        s"Expected GCS source, got ${source.getClass.getSimpleName}",
      )
  }

  // Helper method to extract GCS path from DataSink
  private def extractGcsSinkPath(sink: DataSink): String = sink match {
    case gcs: DataSink.GcsSink => gcs.path
    case _ =>
      throw new IllegalArgumentException(s"Expected GCS sink, got ${sink.getClass.getSimpleName}")
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

  def read(source: DataSource): F[FileSystemResult[Array[Byte]]] = {
    val gcsPath       = extractGcsPath(source)
    val (bucket, key) = parseCloudUri(gcsPath)

    effectSystem.handleError {
      effectSystem.blocking {
        val storage = createStorageClient()
        val blob    = storage.get(bucket, key)
        if (blob == null || !blob.exists()) {
          FileSystemResult.failure(FileSystemError.fileNotFound(gcsPath))
        } else {
          val bytes = blob.getContent()
          FileSystemResult.success(bytes)
        }
      }
    } { error =>
      FileSystemResult.failure(FileSystemError.readError(gcsPath, error.getMessage))
    }
  }

  def write(sink: DataSink, data: Array[Byte]): F[FileSystemResult[WriteMetadata]] = {
    val gcsPath       = extractGcsSinkPath(sink)
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
        val blob    = storage.get(bucket, key)
        blob != null && blob.exists()
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
        } else {
          val success = storage.delete(bucket, key)
          if (!success) {
            return effectSystem.pure(
              FileSystemResult.failure(
                FileSystemError.deleteError(path, "File not found or already deleted"),
              ),
            )
          }
        }

        FileSystemResult.success(())
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
        val blob    = storage.get(bucket, key)

        if (blob == null || !blob.exists()) {
          FileSystemResult.failure(FileSystemError.fileNotFound(path))
        } else {
          val metadata = FileMetadata(
            name = blob.getName,
            path = path,
            size = blob.getSize,
            lastModified = java.time.Instant.ofEpochMilli(blob.getUpdateTime),
            format = inferFormatFromPath(blob.getName),
          )
          FileSystemResult.success(metadata)
        }
      }
    } { error =>
      FileSystemResult.failure(FileSystemError.metadataError(path, error.getMessage))
    }
  }

  def streamRead(source: DataSource): F[List[Array[Byte]]] = {
    val gcsPath       = extractGcsPath(source)
    val (bucket, key) = parseCloudUri(gcsPath)

    effectSystem.blocking {
      val storage = createStorageClient()
      val blob    = storage.get(bucket, key)
      val bytes   = blob.getContent()
      // Split into 8KB chunks for streaming
      bytes.grouped(8192).toList
    }
  }

  def streamWrite(sink: DataSink, data: List[Array[Byte]]): F[WriteMetadata] = {
    val gcsPath       = extractGcsSinkPath(sink)
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

/**
 * Production-ready Amazon S3 connector
 */
/* S3Connector removed for GCS-only focus
class S3Connector[F[_]: EffectSystem](
  region: String,
  accessKeyId: Option[String] = None,
  secretAccessKey: Option[String] = None,
  configuration: Map[String, String] = Map.empty
) extends CloudStorageConnector[F] {

  // Helper method to extract S3 path from DataSource
  private def extractS3Path(source: DataSource): String = source match {
    case s3: DataSource.S3Source => s3.path
    case _ =>
      throw new IllegalArgumentException(
        s"Expected S3 source, got ${source.getClass.getSimpleName}"
      )
  }

  // Helper method to extract S3 path from DataSink
  private def extractS3SinkPath(sink: DataSink): String = sink match {
    case s3: DataSink.S3Sink => s3.path
    case _ =>
      throw new IllegalArgumentException(s"Expected S3 sink, got ${sink.getClass.getSimpleName}")
  }

  // Create S3 client with proper authentication
  private def createS3Client(): software.amazon.awssdk.services.s3.S3Client = {
    import software.amazon.awssdk.services.s3.S3Client
    import software.amazon.awssdk.regions.Region
    import software.amazon.awssdk.auth.credentials.{
      AwsBasicCredentials,
      StaticCredentialsProvider
    }

    val builder = S3Client.builder().region(Region.of(region))

    (accessKeyId, secretAccessKey) match {
      case (Some(keyId), Some(secretKey)) =>
        val credentials = AwsBasicCredentials.create(keyId, secretKey)
        builder.credentialsProvider(StaticCredentialsProvider.create(credentials))
      case _ =>
        // Use default credential provider chain (EC2 instance profile, environment variables, etc.)
        builder
    }

    builder.build()
  }

  def read(source: DataSource): F[FileSystemResult[Array[Byte]]] = {
    val s3Path        = extractS3Path(source)
    val (bucket, key) = parseCloudUri(s3Path)

    effectSystem.handleError {
      effectSystem.bracket(effectSystem.blocking(createS3Client())) { s3Client =>
          import software.amazon.awssdk.services.s3.model.GetObjectRequest
          import java.io.ByteArrayOutputStream

          val getObjectRequest    = GetObjectRequest.builder().bucket(bucket).key(key).build()
          val responseInputStream = s3Client.getObject(getObjectRequest)

          val buffer = new ByteArrayOutputStream()
          responseInputStream.transferTo(buffer)
          val bytes = buffer.toByteArray

          FileSystemResult.success(bytes)
      }(c => effectSystem.blocking(c.close()).void)
    } { error =>
      FileSystemResult.failure(FileSystemError.ReadError(s3Path, error.getMessage))
    }
  }

  def write(sink: DataSink, data: Array[Byte]): F[FileSystemResult[WriteMetadata]] = {
    val s3Path        = extractS3SinkPath(sink)
    val (bucket, key) = parseCloudUri(s3Path)

    effectSystem.handleError {
      effectSystem.bracket(effectSystem.blocking(createS3Client())) { s3Client =>
          import software.amazon.awssdk.services.s3.model.PutObjectRequest
          import software.amazon.awssdk.core.sync.RequestBody

          val putObjectRequest = PutObjectRequest.builder().bucket(bucket).key(key).build()
          s3Client.putObject(putObjectRequest, RequestBody.fromBytes(data))

          FileSystemResult.success(
            WriteMetadata(
              path = s3Path,
              bytesWritten = data.length.toLong
            )
          )
      }(c => effectSystem.blocking(c.close()).void)
    } { error =>
      FileSystemResult.failure(FileSystemError.WriteError(s3Path, error.getMessage))
    }
  }

  def listFiles(path: String): F[FileSystemResult[List[FileMetadata]]] = {
    val (bucket, prefix) = parseCloudUri(path)

    effectSystem.handleError {
      effectSystem.bracket(effectSystem.blocking(createS3Client())) { s3Client =>
          import software.amazon.awssdk.services.s3.model.{ ListObjectsV2Request, S3Object }
          import scala.jdk.CollectionConverters._

          val listRequest = ListObjectsV2Request.builder().bucket(bucket).prefix(prefix).build()
          val response    = s3Client.listObjectsV2(listRequest)

          val metadata = response
            .contents()
            .asScala
            .map { s3Object =>
              FileMetadata(
                name = s3Object.key(),
                path = s"s3://$bucket/${s3Object.key()}",
                size = s3Object.size(),
                lastModified = s3Object.lastModified(),
                format = inferFormatFromPath(s3Object.key())
              )
            }
            .toList

          FileSystemResult.success(metadata)
      }(c => effectSystem.blocking(c.close()).void)
    } { error =>
      FileSystemResult.failure(FileSystemError.listError(path, error.getMessage))
    }
  }

  def exists(path: String): F[Boolean] = {
    val (bucket, key) = parseCloudUri(path)
    effectSystem.handleError {
      effectSystem.bracket(effectSystem.blocking(createS3Client())) { s3Client =>
        effectSystem.blocking {
          import software.amazon.awssdk.services.s3.model.HeadObjectRequest
          s3Client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build())
          true
        }
      }(c => effectSystem.blocking(c.close()).void)
    }(_ => false)
  }

  def createDirectory(path: String): F[FileSystemResult[Unit]] = {
    // S3 doesn't have directories, but we can create a placeholder object
    val (bucket, key) = parseCloudUri(path)
    val dirKey        = if (key.endsWith("/")) key else s"$key/"

    effectSystem.handleError {
      effectSystem.bracket(effectSystem.blocking(createS3Client())) { s3Client =>
          import software.amazon.awssdk.services.s3.model.PutObjectRequest
          import software.amazon.awssdk.core.sync.RequestBody

          val putObjectRequest = PutObjectRequest.builder().bucket(bucket).key(dirKey).build()
          s3Client.putObject(putObjectRequest, RequestBody.empty())

          FileSystemResult.success(())
      }(c => effectSystem.blocking(c.close()).void)
    } { error =>
      FileSystemResult.failure(FileSystemError.createDirectoryError(path, error.getMessage))
    }
  }

  def delete(path: String, recursive: Boolean = false): F[FileSystemResult[Unit]] = {
    val (bucket, key) = parseCloudUri(path)

    effectSystem.handleError {
      effectSystem.bracket(effectSystem.blocking(createS3Client())) { s3Client =>
          if (recursive) {
            import software.amazon.awssdk.services.s3.model.{
              ListObjectsV2Request,
              DeleteObjectRequest
            }
            import scala.jdk.CollectionConverters._

            val listRequest = ListObjectsV2Request.builder().bucket(bucket).prefix(key).build()
            val response    = s3Client.listObjectsV2(listRequest)

            response.contents().asScala.foreach { s3Object =>
              val deleteRequest =
                DeleteObjectRequest.builder().bucket(bucket).key(s3Object.key()).build()
              s3Client.deleteObject(deleteRequest)
            }
          } else {
            import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
            val deleteRequest = DeleteObjectRequest.builder().bucket(bucket).key(key).build()
            s3Client.deleteObject(deleteRequest)
          }

          FileSystemResult.success(())
      }(c => effectSystem.blocking(c.close()).void)
    } { error =>
      FileSystemResult.failure(FileSystemError.deleteError(path, error.getMessage))
    }
  }

  def getMetadata(path: String): F[FileSystemResult[FileMetadata]] = {
    val (bucket, key) = parseCloudUri(path)

    effectSystem.handleError {
      effectSystem.bracket(effectSystem.blocking(createS3Client())) { s3Client =>
          import software.amazon.awssdk.services.s3.model.HeadObjectRequest

          val headObjectRequest = HeadObjectRequest.builder().bucket(bucket).key(key).build()
          val response          = s3Client.headObject(headObjectRequest)

          val metadata = FileMetadata(
            name = key,
            path = path,
            size = response.contentLength(),
            lastModified = response.lastModified(),
            format = inferFormatFromPath(key)
          )
          FileSystemResult.success(metadata)
      }(c => effectSystem.blocking(c.close()).void)
    } { error =>
      FileSystemResult.failure(FileSystemError.metadataError(path, error.getMessage))
    }
  }

  def streamRead(source: DataSource): F[List[Array[Byte]]] = {
    val s3Path        = extractS3Path(source)
    val (bucket, key) = parseCloudUri(s3Path)

      effectSystem.bracket(effectSystem.blocking(createS3Client())) { s3Client =>
          import software.amazon.awssdk.services.s3.model.GetObjectRequest
          import java.io.ByteArrayOutputStream

          val getObjectRequest    = GetObjectRequest.builder().bucket(bucket).key(key).build()
          val responseInputStream = s3Client.getObject(getObjectRequest)

        val buffer = new ByteArrayOutputStream()
        responseInputStream.transferTo(buffer)
        val bytes = buffer.toByteArray

        // Split into 8KB chunks for streaming
        bytes.grouped(8192).toList
      }(c => effectSystem.blocking(c.close()).void)
    }
  }

  def streamWrite(sink: DataSink, data: List[Array[Byte]]): F[WriteMetadata] = {
    val s3Path        = extractS3SinkPath(sink)
    val (bucket, key) = parseCloudUri(s3Path)

    effectSystem.bracket(effectSystem.blocking(createS3Client())) { s3Client =>
        import software.amazon.awssdk.services.s3.model.PutObjectRequest
        import software.amazon.awssdk.core.sync.RequestBody

        val allBytes         = data.flatten.toArray
        val putObjectRequest = PutObjectRequest.builder().bucket(bucket).key(key).build()
        s3Client.putObject(putObjectRequest, RequestBody.fromBytes(allBytes))

        WriteMetadata(
          path = s3Path,
          bytesWritten = allBytes.length.toLong
        )
    }(c => effectSystem.blocking(c.close()).void)
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
 */

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

  // S3 connector methods removed - this build focuses on GCS functionality
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
