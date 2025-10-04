package com.flowforge.connectors.filesystem

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.flowforge.core.algebra.EffectSystem
import org.scalatest.funsuite.AnyFunSuite

class LocalFileSystemNegativeSpec extends AnyFunSuite {
  implicit val F: EffectSystem[IO] =
    com.flowforge.core.instances.EffectInstances.catsEffectSystemInstance

  test("delete non-existent path returns failure") {
    val conn = FileSystemConnector.local[IO]
    val res  = conn.delete("/path/that/does/not/exist-ff", recursive = false).unsafeRunSync()
    res match {
      case com.flowforge.connectors.FileSystemResult.Failure(_) => succeed
      case _                                                    => fail("expected failure result")
    }
  }

  test("listFiles on non-directory path returns failure") {
    val conn = FileSystemConnector.local[IO]
    val file = java.nio.file.Files.createTempFile("ff_neg_", ".tmp")
    val res  = conn.listFiles(file.toString).unsafeRunSync()
    res match {
      case com.flowforge.connectors.FileSystemResult.Failure(_) => succeed
      case _                                                    => fail("expected failure result")
    }
  }

  test("getMetadata on missing file returns failure") {
    val conn = FileSystemConnector.local[IO]
    val res  = conn.getMetadata("/no/such/file-ff").unsafeRunSync()
    res match {
      case com.flowforge.connectors.FileSystemResult.Failure(_) => succeed
      case _                                                    => fail("expected failure result")
    }
  }
}
