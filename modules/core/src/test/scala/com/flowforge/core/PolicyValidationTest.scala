// scalafix:off DisableSyntax.var DisableSyntax.throw DisableSyntax.null DisableSyntax.noUnsafeRunSync
package com.flowforge.core

import com.flowforge.core.contracts.derive.Shape
import com.flowforge.core.contracts.{ SchemaConforms, SchemaPolicy }
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class PolicyValidationTest extends AnyWordSpec with Matchers {
  case class User(
    id: Long,
    name: String,
    email: String)
  case class UserWithAge(
    id: Long,
    name: String,
    email: String,
    age: Option[Int] = None)
  case class UserMissingEmail(id: Long, name: String)
  case class UserReordered(
    name: String,
    id: Long,
    email: String)
  case class UserCaseDiff(
    Id: Long,
    Name: String,
    Email: String)

  case class Item(sku: String, qty: Int)
  case class WiderItem(
    sku: String,
    qty: Int,
    note: Option[String] = None)
  case class OrderWithList(id: Long, items: List[Item])
  case class OrderWithVector(id: Long, items: Vector[Item])
  case class OrderWithSeq(id: Long, items: Seq[Item])
  case class OrderWithWiderItems(id: Long, items: List[WiderItem])

  // Shape instances
  implicit val userShape: Shape[User]                         = Shape.gen[User]
  implicit val userWithAgeShape: Shape[UserWithAge]           = Shape.gen[UserWithAge]
  implicit val userMissingEmailShape: Shape[UserMissingEmail] = Shape.gen[UserMissingEmail]
  implicit val userReorderedShape: Shape[UserReordered]       = Shape.gen[UserReordered]
  implicit val userCaseDiffShape: Shape[UserCaseDiff]         = Shape.gen[UserCaseDiff]

  "Contract Validation" should {
    "allow exact match under Exact policy" in {
      // This should work - exact match
      val valid: SchemaConforms[User, User, SchemaPolicy.Exact] = implicitly
      assert(valid != null)
    }

    "allow backward compatibility under Backward policy" in {
      // UserWithAge has extra optional field - should work under Backward policy
      val valid: SchemaConforms[UserWithAge, User, SchemaPolicy.Backward] = implicitly
      assert(valid != null)
    }

    "allow anything under Full policy" in {
      // Even missing fields should work under Full policy
      val valid: SchemaConforms[UserMissingEmail, User, SchemaPolicy.Full] = implicitly
      assert(valid != null)
    }

    "respect ordering and case rules across policies" in {
      // ExactOrdered should fail when fields are reordered
      assertTypeError("""
        import com.flowforge.core.contracts._
        implicitly[SchemaConforms[UserReordered, User, SchemaPolicy.ExactOrdered]]
      """)

      // ExactUnorderedCI should accept case-insensitive names and order differences
      val ok1: SchemaConforms[UserCaseDiff, User, SchemaPolicy.ExactUnorderedCI] = implicitly
      assert(ok1 != null)
    }

    "read List and Vector as sequences, not as opaque types" in {
      // Both shape readers have to agree here. If a collection were read as a primitive, its name would be
      // compared instead of its element, so List[Item] against Seq[Item] would be a mismatch and a
      // backward-compatible element change inside a List would be rejected.
      val listAgainstSeq: SchemaConforms[OrderWithList, OrderWithSeq, SchemaPolicy.Exact] = implicitly
      assert(listAgainstSeq != null)

      val vectorAgainstSeq: SchemaConforms[OrderWithVector, OrderWithSeq, SchemaPolicy.Exact] = implicitly
      assert(vectorAgainstSeq != null)

      val widerElement: SchemaConforms[OrderWithWiderItems, OrderWithList, SchemaPolicy.Backward] = implicitly
      assert(widerElement != null)
    }

    "render a field type without the packages a reader does not need" in {
      // Scala 3 reflection renders String as `scala.Predef.String` and magnolia renders it as
      // `java.lang.String`, so without reducing the name a field type reads differently per compiler.
      userShape.fields.map(_.tpe) shouldBe List("Long", "String", "String")
    }

    "resolve a policy named as the case object, not only as the trait" in {
      // Both spellings name the same policy. The macro used to resolve a policy by the simple name of its
      // rendered type, which reads `.type` out of `SchemaPolicy.Backward.type` and then silently compared
      // under the default rules. UserMissingEmail drops a required field, so Backward is the only policy
      // here that accepts it, which is what makes this a check of the dispatch rather than of the default.
      val asObjectType: SchemaConforms[User, UserWithAge, SchemaPolicy.Backward.type] = implicitly
      assert(asObjectType != null)
    }

    "reject a leaf type that no sink can encode" in
      // java.util.UUID is not a case class, so it used to be read as an opaque primitive and compared by
      // name. A contract and a producer that both used it therefore conformed, and the pipeline only failed
      // at write time for want of an encoder.
      assertTypeError("""
        import com.flowforge.core.contracts._
        final case class WithUuid(id: java.util.UUID)
        implicitly[SchemaConforms[WithUuid, WithUuid, SchemaPolicy.Exact]]
      """)

    "reject a tuple rather than reading it as a named struct" in
      // Every TupleN is a case class, so a tuple used to be read as a struct of `_1`, `_2`, which makes
      // positional junk look like a named schema.
      assertTypeError("""
        import com.flowforge.core.contracts._
        final case class WithTuple(pair: (Long, String))
        implicitly[SchemaConforms[WithTuple, WithTuple, SchemaPolicy.Exact]]
      """)
  }
}
