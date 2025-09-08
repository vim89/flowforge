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

  // Shape instances
  implicit val userShape: Shape[User]                         = Shape.gen[User]
  implicit val userWithAgeShape: Shape[UserWithAge]           = Shape.gen[UserWithAge]
  implicit val userMissingEmailShape: Shape[UserMissingEmail] = Shape.gen[UserMissingEmail]

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
  }
}
