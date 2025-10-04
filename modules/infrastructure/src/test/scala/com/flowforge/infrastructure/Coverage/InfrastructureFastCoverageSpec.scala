package com.flowforge.infrastructure.coverage

import com.flowforge.logging.StructuredLogger
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class InfrastructureFastCoverageSpec extends AnyFunSuite with Matchers {
  test("StructuredLogger basic calls do not throw") {
    import cats.effect.IO
    import cats.effect.unsafe.implicits.global
    val L = StructuredLogger.forName[IO]("infra-cov").withContext(Map("k" -> "v"))
    noException should be thrownBy L.info("x").unsafeRunSync()
    noException should be thrownBy L.warn("y").unsafeRunSync()
    noException should be thrownBy L.error("z").unsafeRunSync()
  }
}

