package com.flowforge.contracts.syntax

import com.flowforge.contracts.syntax.ContractDSL._
import com.flowforge.contracts.{ FieldConstraint, FieldType }
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

/**
 * Pins the behaviour of the fluent chain.
 *
 * The builders are immutable, so each step hands back a new value and the field in progress has to be folded
 * into its contract before the chain moves on. These tests check that nothing is dropped on the way: every
 * field arrives, in order, with the constraints that were attached to it.
 */
class ContractDSLSpec extends AnyFunSuite with Matchers {

  test("fields arrive in declaration order") {
    val schema = Contract("user")
      .field("id").required.long.positive
      .field("email").required.string.email
      .field("name").optional.string.minLength(2)
      .build

    schema.name.value shouldBe "user"
    schema.fields.map(_.name.value) shouldBe List("id", "email", "name")
  }

  test("the last field in the chain is not dropped") {
    // `build` has to commit the field in progress, not just the ones already folded in.
    val schema = Contract("c").field("only").required.boolean.build

    schema.fields.map(_.name.value) shouldBe List("only")
    schema.fields.head.dataType shouldBe FieldType.BooleanType
  }

  test("required and optional set nullability") {
    val schema = Contract("c")
      .field("a").required.string
      .field("b").optional.string
      .build

    schema.fields.map(_.nullable) shouldBe List(false, true)
  }

  test("constraints stay on the field they were attached to") {
    val schema = Contract("c")
      .field("short").required.string.minLength(1).maxLength(5)
      .field("plain").required.string
      .build

    schema.fields.head.constraints shouldBe List(
      FieldConstraint.MinLength(1),
      FieldConstraint.MaxLength(5),
    )
    schema.fields(1).constraints shouldBe empty
  }

  test("describedAs attaches to the field, not the contract") {
    val schema = Contract("c")
      .field("sku").required.string.describedAs("stock keeping unit")
      .field("qty").required.int
      .build

    schema.fields.head.description shouldBe Some("stock keeping unit")
    schema.fields(1).description shouldBe None
  }

  test("contract level settings survive a field chain") {
    val schema = Contract("c")
      .field("a").required.string
      .withSLA("hourly")
      .withOwner("DataPlatformTeam")
      .withMetadata("domain", "sales")
      .build

    schema.metadata shouldBe Map(
      "domain" -> "sales",
      "sla"    -> "hourly",
      "owner"  -> "DataPlatformTeam",
    )
    schema.fields.map(_.name.value) shouldBe List("a")
  }

  test("withVersion sets the schema version") {
    // This used to be discarded: `build` computed the version and then wrote a hardcoded 1.
    Contract("c").field("a").required.string.withVersion(3, 1, 0).build.version.value shouldBe 3
  }

  test("a contract with no version declared stays at 1") {
    Contract("c").field("a").required.string.build.version.value shouldBe 1

    // ContractSchema only carries a positive integer, so a major of 0 has no representation there.
    Contract("c").field("a").required.string.withVersion(0, 9, 0).build.version.value shouldBe 1
  }

  test("the shipped examples build") {
    Examples.userContract.fields.map(_.name.value) shouldBe
      List("id", "email", "name", "age", "phone", "website")
    Examples.transactionContract.version.value shouldBe 2
    Examples.productContract.fields should have size 7
  }
}
