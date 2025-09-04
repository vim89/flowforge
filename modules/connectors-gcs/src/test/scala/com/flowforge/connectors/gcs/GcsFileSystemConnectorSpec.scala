package com.flowforge.connectors.gcs

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.mockito.Mockito._
import org.mockito.ArgumentMatchers.{any => many, eq => meq}
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.flowforge.core.algebra.EffectSystem
import com.flowforge.core.instances.EffectInstances
import com.flowforge.core.types._

class GcsFileSystemConnectorSpec extends AnyFunSuite with Matchers {

  implicit val F: EffectSystem[IO] = EffectInstances.catsEffectSystemInstance

  private def gcsSource(path: String): DataSource = {
    val parts = path.stripPrefix("gs://").split("/", 2)
    val bucket = parts(0)
    val key    = if (parts.length > 1) parts(1) else ""
    DataSource.GcsSource(
      com.flowforge.core.types.RefinedTypes.BucketName.unsafeFrom(bucket),
      key,
      DataFormat.JSON,
    )
  }

  private def gcsSink(path: String): DataSink = {
    val parts = path.stripPrefix("gs://").split("/", 2)
    val bucket = parts(0)
    val key    = if (parts.length > 1) parts(1) else ""
    DataSink.GcsSink(
      com.flowforge.core.types.RefinedTypes.BucketName.unsafeFrom(bucket),
      key,
      DataFormat.JSON,
    )
  }

  test("read returns success when blob exists") {
    val storage = mock(classOf[com.google.cloud.storage.Storage])
    val blob    = mock(classOf[com.google.cloud.storage.Blob])
    when(storage.get("data-bucket", "file.json")).thenReturn(blob)
    when(blob.getContent()).thenReturn("hello".getBytes("UTF-8"))
    val conn = new GcsFileSystemConnector[IO](storage)
    val res  = conn.read(gcsSource("gs://data-bucket/file.json")).unsafeRunSync()
    res match {
      case com.flowforge.connectors.FileSystemResult.Success(bytes) => new String(bytes, "UTF-8") shouldBe "hello"
      case other => fail(s"Expected success, got $other")
    }
  }

  test("read returns failure when blob missing") {
    val storage = mock(classOf[com.google.cloud.storage.Storage])
    when(storage.get("data-bucket", "missing.json")).thenReturn(null)
    val conn = new GcsFileSystemConnector[IO](storage)
    val res  = conn.read(gcsSource("gs://data-bucket/missing.json")).unsafeRunSync()
    res shouldBe a[com.flowforge.connectors.ConnectorResult.Failure]
  }

  test("write creates blob and returns metadata") {
    val storage = mock(classOf[com.google.cloud.storage.Storage])
    when(storage.create(many(classOf[com.google.cloud.storage.BlobInfo]), many(classOf[Array[Byte]])))
      .thenReturn(mock(classOf[com.google.cloud.storage.Blob]))
    val conn = new GcsFileSystemConnector[IO](storage)
    val res  = conn.write(gcsSink("gs://data-bucket/out.json"), "ok".getBytes("UTF-8")).unsafeRunSync()
    res match {
      case com.flowforge.connectors.FileSystemResult.Success(meta) =>
        meta.path shouldBe "gs://data-bucket/out.json"
        meta.bytesWritten shouldBe 2L
      case _ => fail("Expected write success")
    }
  }

  test("exists checks underlying storage") {
    val storage = mock(classOf[com.google.cloud.storage.Storage])
    val blob    = mock(classOf[com.google.cloud.storage.Blob])
    when(storage.get("b", "k")).thenReturn(blob)
    val conn = new GcsFileSystemConnector[IO](storage)
    conn.exists("gs://b/k").unsafeRunSync() shouldBe true
  }

  test("listFiles returns basic metadata") {
    val storage = mock(classOf[com.google.cloud.storage.Storage])
    val page    = mock(classOf[com.google.api.gax.paging.Page[com.google.cloud.storage.Blob]])
    val blob    = mock(classOf[com.google.cloud.storage.Blob])
    when(blob.getName).thenReturn("prefix/file.json")
    when(blob.getSize).thenReturn(java.lang.Long.valueOf(5L))
    when(blob.getUpdateTime).thenReturn(java.lang.Long.valueOf(1000L))
    when(page.iterateAll()).thenReturn(java.util.Arrays.asList(blob))
    when(storage.list(meq("bucket"), many(classOf[com.google.cloud.storage.Storage.BlobListOption]), many(classOf[com.google.cloud.storage.Storage.BlobListOption]))).thenReturn(page)
    val conn = new GcsFileSystemConnector[IO](storage)
    val res  = conn.listFiles("gs://bucket/prefix").unsafeRunSync()
    res match {
      case com.flowforge.connectors.FileSystemResult.Success(files) =>
        files.head.name shouldBe "prefix/file.json"
      case _ => fail("Expected list success")
    }
  }
}
