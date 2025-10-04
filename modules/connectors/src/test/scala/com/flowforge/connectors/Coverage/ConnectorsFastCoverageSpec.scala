package com.flowforge.connectors.coverage

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.flowforge.connectors.filesystem.FileSystemConnector
import com.flowforge.core.algebra.EffectSystem
import com.flowforge.core.types.{ DataFormat, DataSink, DataSource }
import org.scalatest.funsuite.AnyFunSuite

class ConnectorsFastCoverageSpec extends AnyFunSuite {
  implicit val F: EffectSystem[IO] = com.flowforge.core.instances.EffectInstances.catsEffectSystemInstance

  test("Local connector stream operations basic smoke") {
    val dir  = java.nio.file.Files.createTempDirectory("ff_cov_")
    val file = dir.resolve("a.json").toString
    val data = List("{\"x\":1}".getBytes("UTF-8"))
    val conn = FileSystemConnector.local[IO]
    val sink = DataSink.local(file, DataFormat.JSON)
    val src  = DataSource.local(file, DataFormat.JSON)
    val meta = conn.streamWrite(sink, data).unsafeRunSync()
    assert(meta.bytesWritten > 0)
    val rd = conn.streamRead(src).unsafeRunSync()
    assert(rd.nonEmpty)
  }
}
