package com.flowforge.config

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class ConfigurationManagementSpec extends AnyWordSpec with Matchers {
  "TypesafeConfigurationManagement" should {
    "load invalid for missing key and allow reload without error" in {
      val cm = ConfigurationManagement.forTypesafeConfig[IO]
      import ConfigurationManagement._
      val v = cm.loadTypeSafeConfig[String]("flowforge.missing.key").unsafeRunSync()
      v.isInvalid shouldBe true
      noException should be thrownBy cm.reloadConfig.unsafeRunSync()
    }
  }
}
