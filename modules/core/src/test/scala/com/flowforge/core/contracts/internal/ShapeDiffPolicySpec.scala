package com.flowforge.core.contracts.internal

import com.flowforge.core.contracts.SchemaPolicy
import com.flowforge.core.contracts.internal.TypeShape._
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

/**
 * What each policy accepts and rejects, checked one policy at a time.
 *
 * The compile-fail suite already proves the macros reject what they should, but it can only ask the compiler,
 * so it cannot cover every policy against every kind of drift without a case class and a test file per
 * combination. [[ShapeDiff]] is the same comparison without a compiler in front of it, so the matrix fits in
 * one file and a policy's behaviour is stated rather than inferred from which files fail to compile.
 *
 * The reference is the standalone compile-time-data-contracts work these policies came from. FlowForge agrees
 * with it on every policy except `Exact`, which is deliberate and is recorded in
 * [[ShapeDiffPolicySpec.exactIsCaseSensitive]] below.
 */
class ShapeDiffPolicySpec extends AnyFunSpec with Matchers {

  import ShapeDiffPolicySpec._

  private def drift(
    policy: SchemaPolicy,
    out: TypeShape,
    contract: TypeShape,
  ): ShapeDiff.Drift =
    ShapeDiff.diff(ShapeDiff.Flags.of(policy), out, contract)

  private def conforms(
    policy: SchemaPolicy,
    out: TypeShape,
    contract: TypeShape,
  ): Boolean =
    drift(policy, out, contract).isEmpty

  describe("Exact") {
    it("accepts the same fields in a different order") {
      conforms(SchemaPolicy.Exact, reordered, user) shouldBe true
    }

    it("rejects a missing field") {
      drift(SchemaPolicy.Exact, withoutEmail, user).missing.map(_.path) shouldBe List("email")
    }

    it("rejects an extra field") {
      drift(SchemaPolicy.Exact, withAge, user).extra.map(_.path) shouldBe List("age")
    }

    it("rejects a changed field type") {
      drift(SchemaPolicy.Exact, idAsString, user).mismatched.map(_.path) shouldBe List("id")
    }

    it(exactIsCaseSensitive) {
      conforms(SchemaPolicy.Exact, differentCase, user) shouldBe false
    }
  }

  describe("ExactUnordered") {
    it("behaves like Exact, which is what makes it a back-compat alias") {
      val cases = List(reordered, withoutEmail, withAge, idAsString, differentCase)
      cases.map(conforms(SchemaPolicy.ExactUnordered, _, user)) shouldBe
        cases.map(conforms(SchemaPolicy.Exact, _, user))
    }
  }

  describe("ExactUnorderedCI") {
    it("accepts field names that differ only in case") {
      conforms(SchemaPolicy.ExactUnorderedCI, differentCase, user) shouldBe true
    }

    it("still rejects a missing field") {
      conforms(SchemaPolicy.ExactUnorderedCI, withoutEmail, user) shouldBe false
    }

    it("accepts a different order, since it is unordered") {
      conforms(SchemaPolicy.ExactUnorderedCI, reordered, user) shouldBe true
    }
  }

  describe("ExactOrdered") {
    it("accepts the fields in the contract's order") {
      conforms(SchemaPolicy.ExactOrdered, user, user) shouldBe true
    }

    it("rejects the same fields in a different order") {
      drift(SchemaPolicy.ExactOrdered, reordered, user).mismatched.map(_.path) should contain(
        "@1(name)",
      )
    }

    it("rejects field names that differ only in case") {
      conforms(SchemaPolicy.ExactOrdered, differentCase, user) shouldBe false
    }
  }

  describe("ExactOrderedCI") {
    it("accepts field names that differ only in case, in the contract's order") {
      conforms(SchemaPolicy.ExactOrderedCI, differentCase, user) shouldBe true
    }

    it("rejects a different order") {
      conforms(SchemaPolicy.ExactOrderedCI, reordered, user) shouldBe false
    }
  }

  describe("ExactByPosition") {
    it("accepts renamed fields whose types line up by position") {
      conforms(SchemaPolicy.ExactByPosition, renamed, user) shouldBe true
    }

    it("rejects a different field count") {
      drift(SchemaPolicy.ExactByPosition, withAge, user).mismatched.map(_.found) shouldBe
        List("4 fields")
    }

    it("rejects a type that differs at one position") {
      conforms(SchemaPolicy.ExactByPosition, idAsString, user) shouldBe false
    }
  }

  describe("Backward") {
    it("accepts extra producer fields") {
      conforms(SchemaPolicy.Backward, withAge, user) shouldBe true
    }

    it("rejects a missing required field") {
      conforms(SchemaPolicy.Backward, withoutEmail, user) shouldBe false
    }

    it("accepts a missing field the contract declares optional") {
      conforms(SchemaPolicy.Backward, withoutNickname, userWithOptionalNickname) shouldBe true
    }

    it("accepts a missing field the contract gives a default") {
      conforms(SchemaPolicy.Backward, withoutNickname, userWithDefaultedNickname) shouldBe true
    }

    it("still rejects a changed field type") {
      conforms(SchemaPolicy.Backward, idAsString, user) shouldBe false
    }
  }

  describe("Forward") {
    it("accepts a producer that omits contract fields") {
      conforms(SchemaPolicy.Forward, withoutEmail, user) shouldBe true
    }

    it("rejects extra producer fields") {
      conforms(SchemaPolicy.Forward, withAge, user) shouldBe false
    }

    it("still rejects a changed field type") {
      conforms(SchemaPolicy.Forward, idAsString, user) shouldBe false
    }
  }

  describe("Full") {
    it("accepts every kind of drift, which is why it is an escape hatch") {
      val cases = List(withoutEmail, withAge, idAsString, differentCase, renamed)
      cases.map(conforms(SchemaPolicy.Full, _, user)) shouldBe cases.map(_ => true)
    }
  }

  describe("nested optionality") {
    it("rejects a producer that drops optionality inside a list") {
      conforms(SchemaPolicy.Exact, listOfInt, listOfOptionalInt) shouldBe false
    }

    it("rejects a producer that drops optionality inside a map value") {
      conforms(SchemaPolicy.Exact, mapOfInt, mapOfOptionalInt) shouldBe false
    }

    it("is not relaxed by Backward, which only relaxes whole fields") {
      conforms(SchemaPolicy.Backward, listOfInt, listOfOptionalInt) shouldBe false
    }
  }

  describe("an unresolved policy type") {
    it("compares exactly, so an abstract policy cannot widen what is accepted") {
      ShapeDiff.Flags.strictest shouldBe ShapeDiff.Flags.of(SchemaPolicy.Exact)
    }
  }
}

object ShapeDiffPolicySpec {

  /**
   * The one intentional difference from compile-time-data-contracts.
   *
   * There, `Exact` matches field names case-insensitively. In FlowForge `Exact` is case-sensitive and the
   * case-insensitive comparison is its own policy, `ExactUnorderedCI`, so a producer renaming `userId` to
   * `userid` is drift unless the contract says that is acceptable. A column name's case is load-bearing in
   * some of the stores FlowForge writes to, so accepting case drift by default would hide a real break; the
   * case-insensitive behaviour is still available, it just has to be asked for by name.
   */
  val exactIsCaseSensitive =
    "rejects field names that differ only in case, unlike compile-time-data-contracts"

  private def field(name: String, shape: TypeShape): FieldShape =
    FieldShape(name, shape, hasDefault = false, isOptional = false)

  private val id    = field("id", PrimitiveShape("Long"))
  private val name  = field("name", PrimitiveShape("String"))
  private val email = field("email", PrimitiveShape("String"))

  private val user         = StructShape(List(id, name, email))
  private val reordered    = StructShape(List(id, email, name))
  private val withAge      = StructShape(List(id, name, email, field("age", PrimitiveShape("Int"))))
  private val withoutEmail = StructShape(List(id, name))
  private val idAsString   = StructShape(List(field("id", PrimitiveShape("String")), name, email))
  private val differentCase =
    StructShape(List(field("Id", PrimitiveShape("Long")), field("NAME", PrimitiveShape("String")), email))
  private val renamed = StructShape(
    List(
      field("identifier", PrimitiveShape("Long")),
      field("label", PrimitiveShape("String")),
      field("contact", PrimitiveShape("String")),
    ),
  )

  private val nickname = PrimitiveShape("String")
  private val userWithOptionalNickname =
    StructShape(List(id, FieldShape("nickname", nickname, hasDefault = false, isOptional = true)))
  private val userWithDefaultedNickname =
    StructShape(List(id, FieldShape("nickname", nickname, hasDefault = true, isOptional = false)))
  private val withoutNickname = StructShape(List(id))

  private val listOfInt         = SequenceShape(PrimitiveShape("Int"))
  private val listOfOptionalInt = SequenceShape(OptionalShape(PrimitiveShape("Int")))
  private val mapOfInt          = MapShape(PrimitiveShape("String"), PrimitiveShape("Int"))
  private val mapOfOptionalInt  = MapShape(PrimitiveShape("String"), OptionalShape(PrimitiveShape("Int")))
}
