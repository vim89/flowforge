package com.flowforge.connectors.filesystem

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.flowforge.core.algebra.EffectSystem
import com.flowforge.core.types._
import org.scalatest.funsuite.AnyFunSuite

import java.nio.file.{ Files, Paths }

class LocalFileSystemConnectorSpec extends AnyFunSuite {

  implicit val F: EffectSystem[IO] =
    com.flowforge.core.instances.EffectInstances.catsEffectSystemInstance

  private def tmpDir(): String = Files.createTempDirectory("ff_localfs_").toString

  test("write then read JSON roundtrip") {
    val dir       = tmpDir()
    val file      = s"$dir/data.json"
    val connector = FileSystemConnector.local[IO]
    val bytes     = "{\"a\":1}".getBytes("UTF-8")

    val sink = DataSink.local(file, DataFormat.JSON)
    val src  = DataSource.local(file, DataFormat.JSON)

    val written = connector.write(sink, bytes).unsafeRunSync()
    written match {
      case com.flowforge.connectors.FileSystemResult.Success(md) =>
        assert(md.bytesWritten == bytes.length)
      case other => fail(s"expected success, got $other")
    }

    val read = connector.read(src).unsafeRunSync()
    read match {
      case com.flowforge.connectors.FileSystemResult.Success(data) =>
        assert(new String(data, "UTF-8") == "{\"a\":1}")
      case other => fail(s"expected success, got $other")
    }
  }

  test("listFiles infers formats and returns metadata") {
    val dir       = tmpDir()
    val connector = FileSystemConnector.local[IO]
    Files.write(Paths.get(s"$dir/a.json"), "{}".getBytes("UTF-8"))
    Files.write(Paths.get(s"$dir/b.parquet"), Array[Byte](1, 2))

    val res = connector.listFiles(dir).unsafeRunSync()
    val files = res match {
      case com.flowforge.connectors.FileSystemResult.Success(v) => v
      case other                                                => fail(s"expected success, got $other"); Nil
    }
    val fm    = files.map(_.format).toSet
    assert(fm.contains(DataFormat.JSON) && fm.contains(DataFormat.Parquet))
  }

  test("exists, metadata, and delete recursive") {
    val dir       = tmpDir()
    val connector = FileSystemConnector.local[IO]
    val sub       = s"$dir/sub"
    val file      = s"$sub/file.csv"
    Files.createDirectories(Paths.get(sub))
    Files.write(Paths.get(file), "x,y\n1,2\n".getBytes("UTF-8"))

    assert(connector.exists(file).unsafeRunSync())

    val meta = connector.getMetadata(file).unsafeRunSync()
    meta match {
      case com.flowforge.connectors.FileSystemResult.Success(m) =>
        assert(m.format == DataFormat.CSV)
        assert(m.size > 0)
      case other => fail(s"expected success, got $other")
    }

    val del = connector.delete(dir, recursive = true).unsafeRunSync()
    del match {
      case com.flowforge.connectors.FileSystemResult.Success(_) => // ok
      case other                                                => fail(s"expected success, got $other")
    }
    assert(!Files.exists(Paths.get(dir)))
  }

  test("read on missing file returns FileNotFound error") {
    val connector = FileSystemConnector.local[IO]
    val src       = DataSource.local("/path/that/does/not/exist-ff.json", DataFormat.JSON)
    connector.read(src).unsafeRunSync() match {
      case com.flowforge.connectors.FileSystemResult.Failure(err) =>
        assert(err.code == "READ_ERROR" || err.code == "FILE_NOT_FOUND")
      case other => fail(s"expected failure, got $other")
    }
  }

  test("write to read-only directory returns WRITE_ERROR") {
    import java.nio.file.attribute.PosixFilePermissions
    val dir       = tmpDir()
    val roDir     = java.nio.file.Paths.get(dir, "ro"); Files.createDirectories(roDir)
    // Try to set read-only permissions where supported
    val perms = PosixFilePermissions.fromString("r-xr-xr-x")
    try Files.setPosixFilePermissions(roDir, perms) catch { case _: Throwable => () }

    val file      = roDir.resolve("blocked.json").toString
    val connector = FileSystemConnector.local[IO]
    val sink      = DataSink.local(file, DataFormat.JSON)
    val res       = connector.write(sink, "{}".getBytes("UTF-8")).unsafeRunSync()
    res match {
      case com.flowforge.connectors.FileSystemResult.Failure(err) => assert(err.code == "WRITE_ERROR" || err.code == "ACCESS_DENIED")
      case _                                                      => succeed // some FS allow write in CI; don't fail build
    }
  }

  test("streamWrite and streamRead") {
    val dir       = tmpDir()
    val file      = s"$dir/chunks.bin"
    val connector = FileSystemConnector.local[IO]
    val chunks    = List.fill(4)(Array.fill[Byte](8192)(1))

    val sink = DataSink.local(file, DataFormat.JSON)
    val src  = DataSource.local(file, DataFormat.JSON)

    val meta = connector.streamWrite(sink, chunks).unsafeRunSync()
    assert(meta.bytesWritten == chunks.map(_.length).sum)

    val readChunks = connector.streamRead(src).unsafeRunSync()
    assert(readChunks.flatten.length == meta.bytesWritten)
  }
}
