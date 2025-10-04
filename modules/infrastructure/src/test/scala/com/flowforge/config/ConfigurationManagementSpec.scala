// scalafix:off DisableSyntax.var DisableSyntax.throw DisableSyntax.null DisableSyntax.noUnsafeRunSync
package com.flowforge.config

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ConfigurationManagementSpec extends AnyWordSpec with Matchers {
  "TypesafeConfigurationManagement" should {
    "load invalid for missing key and allow reload without error" in {
      val cm = ConfigurationManagement.forTypesafeConfig[IO]
      import ConfigurationManagement._
      val v = cm.loadTypeSafeConfig[String]("flowforge.missing.key").unsafeRunSync()
      v.isInvalid shouldBe true
      noException should be thrownBy cm.reloadConfig.unsafeRunSync()
    }

    "decode valid values and reload without error" in {
      import ConfigurationManagement._
      val cfg = com.typesafe.config.ConfigFactory.parseString(
        """
        |flowforge.ok.str = "v"
        |flowforge.ok.int = 42
        |flowforge.ok.bool = true
        |""".stripMargin,
      )
      stringDecoder.decode(cfg, "flowforge.ok.str").isValid shouldBe true
      intDecoder.decode(cfg, "flowforge.ok.int").isValid shouldBe true
      booleanDecoder.decode(cfg, "flowforge.ok.bool").isValid shouldBe true

      val cm = ConfigurationManagement.forTypesafeConfig[IO]
      noException should be thrownBy cm.reloadConfig.unsafeRunSync()
    }
  }
}
