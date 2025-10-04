// scalafix:off DisableSyntax.var DisableSyntax.throw DisableSyntax.null DisableSyntax.noUnsafeRunSync
package com.flowforge.core

import com.flowforge.core.contracts.derive.Shape
import com.flowforge.core.contracts.{ SchemaConforms, SchemaPolicy }
import org.scalatest.wordspec.AnyWordSpec

class PolicyValidationTest extends AnyWordSpec {
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
  }
}
