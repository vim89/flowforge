package com.flowforge.connectors.filesystem

import com.flowforge.core.algebra.EffectSystem
import com.flowforge.core.types._
import org.scalatest.funsuite.AnyFunSuite
import cats.effect.unsafe.implicits.global

class HDFSConnectorSpec extends AnyFunSuite {

  implicit val F: EffectSystem[cats.effect.IO] =
    com.flowforge.core.instances.EffectInstances.catsEffectSystemInstance

  private def assumeHdfsConfigured(): Unit =
    assume(
      sys.props.get("HDFS_URL").isDefined || sys.env.get("HDFS_URL").isDefined,
      "Skipping HDFS tests: set HDFS_URL system property or env var to run."
    )

  test("read non-existent path returns FileNotFound") {
    assumeHdfsConfigured()
    val url =
      sys.props.getOrElse("HDFS_URL", sys.env.getOrElse("HDFS_URL", "hdfs://localhost:8020"))
    val hdfs = new HDFSFileSystemConnector[cats.effect.IO](url, Map.empty)
    val ds   = DataSource.gcs("hdfs", "/__does_not_exist__.txt", DataFormat.JSON)
    val res  = hdfs.read(ds).unsafeRunSync()
    res match {
      case com.flowforge.connectors.FileSystemResult.Failure(err) =>
        assert(err.code == "FILE_NOT_FOUND")
      case _ => fail("Expected failure for non-existent path")
    }
  }
}
