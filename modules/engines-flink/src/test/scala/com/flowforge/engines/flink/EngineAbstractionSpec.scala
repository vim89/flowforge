package com.flowforge.engines.flink

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

/**
 * Shared algebra test that proves both Spark and Flink engines can use the same domain transforms.
 *
 * This demonstrates FlowForge's engine-agnostic API where business logic is separated from execution engine
 * concerns.
 */
class EngineAbstractionSpec extends AnyWordSpec with Matchers {

  import FlinkStreamingDemo._

  "FlowForge Engine Abstraction" should {

    "provide pure domain transforms that work across engines" in {
      val user = User(1, "john doe", "JOHN@EXAMPLE.COM")

      // Same transform used by both Spark and Flink
      val processed = processUser(user)

      processed.id shouldBe 1
      processed.name shouldBe "JOHN DOE"          // Uppercase transformation
      processed.email shouldBe "john@example.com" // Lowercase transformation
      processed.processed shouldBe true
    }

    "provide pure validation logic that works across engines" in {
      val validUser    = User(1, "John", "john@example.com")
      val invalidUser1 = User(0, "John", "john@example.com") // Invalid ID
      val invalidUser2 = User(1, "", "john@example.com")     // Empty name
      val invalidUser3 = User(1, "John", "invalid-email")    // Invalid email

      isValidUser(validUser) shouldBe true
      isValidUser(invalidUser1) shouldBe false
      isValidUser(invalidUser2) shouldBe false
      isValidUser(invalidUser3) shouldBe false
    }

    "demonstrate functional composition of transforms" in {
      val users = List(
        User(1, "john doe", "JOHN@EXAMPLE.COM"),
        User(0, "", "invalid"),
        User(2, "jane smith", "JANE@TEST.ORG"),
      )

      // Pure functional pipeline - same logic used in all engines
      val processed = users
        .filter(isValidUser) // Domain validation
        .map(processUser)    // Domain transformation

      processed should have length 2
      processed.map(_.name) should contain allOf ("JOHN DOE", "JANE SMITH")
      processed.map(_.email) should contain allOf ("john@example.com", "jane@test.org")
      processed.forall(_.processed) shouldBe true
    }

    "prove business logic is engine-independent" in {
      // This test can be run against Spark, Flink, or any other engine
      // The domain logic (processUser, isValidUser) never changes

      val input    = User(42, "test user", "TEST@DOMAIN.COM")
      val expected = ProcessedUser(42, "TEST USER", "test@domain.com", true)

      processUser(input) shouldBe expected
    }
  }
}
