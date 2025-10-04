package com.flowforge.infrastructure.config

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.flowforge.config.ConfigurationManagement
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class ConfigurationManagementCoverageSpec extends AnyFunSuite with Matchers {
  test("Reload and basic decoders error paths") {
    val cm = ConfigurationManagement.forTypesafeConfig[IO]
    noException should be thrownBy cm.reloadConfig.unsafeRunSync()
    import ConfigurationManagement._
    val cfg = com.typesafe.config.ConfigFactory.parseString("flowforge.bad.int = \"x\"")
    intDecoder.decode(cfg, "flowforge.bad.int").isInvalid shouldBe true
    stringDecoder.decode(cfg, "flowforge.missing").isInvalid shouldBe true
  }
}
