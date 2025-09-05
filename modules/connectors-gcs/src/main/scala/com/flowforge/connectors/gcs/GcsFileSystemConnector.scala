package com.flowforge.connectors.gcs

import cats.implicits._
import com.flowforge.connectors._
import com.flowforge.core.algebra.EffectSystem
import com.flowforge.core.types._

/**
 * Minimal Google Cloud Storage connector implementing the FileSystemConnector interface using
 * google-cloud-storage client. Operations are wrapped in EffectSystem[F] for effect safety.
 */
class GcsFileSystemConnector[F[_]: EffectSystem](
  storage: com.google.cloud.storage.Storage)
    extends com.flowforge.connectors.filesystem.FileSystemConnector[F] {

  private val F = EffectSystem[F]

  private case class GsUri(bucket: String, key: String)

  private def parseGsUri(path: String): Either[ConnectorError, GsUri] =
    if (path.startsWith("gs://")) {
      val rest   = path.stripPrefix("gs://")
      val idx    = rest.indexOf('/')
      val bucket = if (idx >= 0) rest.substring(0, idx) else rest
      val key    = if (idx >= 0) rest.substring(idx + 1) else ""
      if (bucket.nonEmpty) Right(GsUri(bucket, key))
      else Left(FileSystemError.MetadataError(path, "Invalid GCS URI: missing bucket"))
    } else Left(FileSystemError.MetadataError(path, "Invalid GCS URI: must start with gs://"))

  private def toPath(ds: DataSource): String = ds match {
    case g: DataSource.GcsSource => g.path
    case l: LocalDataSource      => l.location
    case other                   => s"unsupported://${other.getClass.getSimpleName}"
  }

  private def toPath(sink: DataSink): String = sink match {
    case g: DataSink.GcsSink => g.path
    case l: LocalDataSink    => l.location
    case other               => s"unsupported://${other.getClass.getSimpleName}"
  }

  def read(source: DataSource): F[FileSystemResult[Array[Byte]]] = source match {
    case g: DataSource.GcsSource =>
      parseGsUri(g.path) match {
        case Left(err) => F.pure(FileSystemResult.failure(err))
        case Right(GsUri(bucket, key)) =>
          F.handleError {
            F.blocking {
              val blob = storage.get(bucket, key)
              if (blob == null) FileSystemResult.failure(FileSystemError.FileNotFound(g.path))
              else FileSystemResult.success(blob.getContent())
            }
          }(t => FileSystemResult.failure(FileSystemError.ReadError(g.path, t.getMessage)))
      }
    case other =>
      F.pure(
        FileSystemResult.failure(
          FileSystemError.ReadError(toPath(other), "Unsupported source type for GCS connector"),
        ),
      )
  }

  def write(sink: DataSink, data: Array[Byte]): F[FileSystemResult[WriteMetadata]] = sink match {
    case g: DataSink.GcsSink =>
      parseGsUri(g.path) match {
        case Left(err) => F.pure(FileSystemResult.failure(err))
        case Right(GsUri(bucket, key)) =>
          F.handleError {
            F.blocking {
              val blobInfo = com.google.cloud.storage.BlobInfo.newBuilder(bucket, key).build()
              storage.create(blobInfo, data)
              FileSystemResult.success(WriteMetadata(path = g.path, bytesWritten = data.length.toLong))
            }
          }(t => FileSystemResult.failure(FileSystemError.WriteError(g.path, t.getMessage)))
      }
    case other =>
      F.pure(
        FileSystemResult.failure(
          FileSystemError.WriteError(toPath(other), "Unsupported sink type for GCS connector"),
        ),
      )
  }

  def listFiles(path: String): F[FileSystemResult[List[FileMetadata]]] =
    parseGsUri(path) match {
      case Left(err) => F.pure(FileSystemResult.failure(err))
      case Right(GsUri(bucket, keyPrefix)) =>
        F.handleError {
          F.blocking {
            import scala.jdk.CollectionConverters._
            val page = storage.list(
              bucket,
              com.google.cloud.storage.Storage.BlobListOption.prefix(keyPrefix),
              com.google.cloud.storage.Storage.BlobListOption.currentDirectory(),
            )
            val files = page.iterateAll().asScala.toList.map { b =>
              val size       = Option(b.getSize).map(_.longValue()).getOrElse(0L)
              val updateTime = Option(b.getUpdateTime).map(_.longValue()).getOrElse(0L)
              FileMetadata(
                name = b.getName,
                path = s"gs://$bucket/${b.getName}",
                size = size,
                lastModified = java.time.Instant.ofEpochMilli(updateTime),
                format = DataFormat.JSON, // unknown; caller can infer from name
              )
            }
            FileSystemResult.success(files)
          }
        }(t => FileSystemResult.failure(FileSystemError.ListError(path, t.getMessage)))
    }

  def exists(path: String): F[Boolean] =
    parseGsUri(path) match {
      case Left(_)              => F.pure(false)
      case Right(GsUri(b, key)) => F.blocking(storage.get(b, key) != null)
    }

  def createDirectory(path: String): F[FileSystemResult[Unit]] =
    parseGsUri(path) match {
      case Left(err) => F.pure(FileSystemResult.failure(err))
      case Right(GsUri(b, key)) =>
        val dirKey = if (key.endsWith("/")) key else s"$key/"
        F.handleError {
          F.blocking {
            val info = com.google.cloud.storage.BlobInfo.newBuilder(b, dirKey).build()
            storage.create(info, Array.emptyByteArray)
            FileSystemResult.success(())
          }
        }(t => FileSystemResult.failure(FileSystemError.CreateDirectoryError(path, t.getMessage)))
    }

  def delete(path: String, recursive: Boolean = false): F[FileSystemResult[Unit]] =
    parseGsUri(path) match {
      case Left(err) => F.pure(FileSystemResult.failure(err))
      case Right(GsUri(b, key)) =>
        F.handleError {
          F.blocking {
            if (recursive) {
              import scala.jdk.CollectionConverters._
              val blobs = storage
                .list(b, com.google.cloud.storage.Storage.BlobListOption.prefix(key))
                .iterateAll()
                .asScala
                .toList
              blobs.foreach(bx => storage.delete(bx.getBlobId))
              FileSystemResult.success(())
            } else {
              val ok = storage.delete(b, key)
              if (ok) FileSystemResult.success(())
              else FileSystemResult.failure(FileSystemError.DeleteError(path, "Not found"))
            }
          }
        }(t => FileSystemResult.failure(FileSystemError.DeleteError(path, t.getMessage)))
    }

  def getMetadata(path: String): F[FileSystemResult[FileMetadata]] =
    parseGsUri(path) match {
      case Left(err) => F.pure(FileSystemResult.failure(err))
      case Right(GsUri(b, key)) =>
        F.handleError {
          F.blocking {
            val blob = storage.get(b, key)
            if (blob == null) FileSystemResult.failure(FileSystemError.FileNotFound(path))
            else {
              val size       = Option(blob.getSize).map(_.longValue()).getOrElse(0L)
              val updateTime = Option(blob.getUpdateTime).map(_.longValue()).getOrElse(0L)
              FileSystemResult.success(
                FileMetadata(
                  name = blob.getName,
                  path = s"gs://$b/${blob.getName}",
                  size = size,
                  lastModified = java.time.Instant.ofEpochMilli(updateTime),
                  format = DataFormat.JSON,
                ),
              )
            }
          }
        }(t => FileSystemResult.failure(FileSystemError.MetadataError(path, t.getMessage)))
    }

  def streamRead(source: DataSource): F[List[Array[Byte]]] =
    read(source).map {
      case FileSystemResult.Success(bytes) => bytes.grouped(8192).toList
      case FileSystemResult.Failure(err)   => throw new RuntimeException(err.message)
    }

  def streamWrite(sink: DataSink, data: List[Array[Byte]]): F[WriteMetadata] =
    write(sink, data.flatten.toArray).map {
      case FileSystemResult.Success(meta) => meta
      case FileSystemResult.Failure(err)  => throw new RuntimeException(err.message)
    }
}

object GcsFileSystemConnector {
  def default[F[_]: EffectSystem]: GcsFileSystemConnector[F] = {
    val storage = com.google.cloud.storage.StorageOptions.getDefaultInstance.getService
    new GcsFileSystemConnector[F](storage)
  }
}
