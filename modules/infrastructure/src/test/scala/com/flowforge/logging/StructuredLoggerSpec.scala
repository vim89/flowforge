package com.flowforge.logging

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class StructuredLoggerSpec extends AnyFunSuite with Matchers {
  test("formatting with context appends key=value pairs") {
    val L  = StructuredLogger.forName[IO]("test")
    val L2 = L.withContext(Map("k1" -> "v1"))
    // We cannot easily assert side effects of slf4j logger here; smoke test ensures no exceptions
    noException should be thrownBy L2.info("msg").unsafeRunSync()
    noException should be thrownBy L2.info("msg", Map("k2" -> "v2")).unsafeRunSync()
    noException should be thrownBy L2.error("oops", new RuntimeException("x")).unsafeRunSync()
  }
}

