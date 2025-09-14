package com.flowforge.compilefail

import com.flowforge.core.contracts.{ SchemaConforms, SchemaPolicy }
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class SchemaPolicyMatrixSpec extends AnyWordSpec with Matchers {
  case class U(id: Long, name: String, email: String)
  case class UBackward(id: Long, name: String, email: Option[String])
  case class UForward(id: Long, name: String)
  case class UTypeWiden(id: Long, name: Any, email: String)
  case class UTypeNarrow(id: Int, name: String, email: String)

  "Backward" should {
    "allow extra output fields" in {
      case class UExtra(id: Long, name: String, email: String, age: Int)
      assertCompiles("implicitly[SchemaConforms[UExtra, U, SchemaPolicy.Backward]]")
    }
    "allow missing when contract marks optional" in {
      assertCompiles("implicitly[SchemaConforms[UForward, UBackward, SchemaPolicy.Backward]]")
    }
    "reject missing required fields" in {
      assertTypeError("implicitly[SchemaConforms[UForward, U, SchemaPolicy.Backward]]")
    }
  }

  "Forward" should {
    "allow fewer output fields" in {
      assertCompiles("implicitly[SchemaConforms[UForward, U, SchemaPolicy.Forward]]")
    }
    "reject extra output fields" in {
      assertTypeError("implicitly[SchemaConforms[U, UForward, SchemaPolicy.Forward]]")
    }
  }

  "Type changes" should {
    "reject widening under Exact" in {
      assertTypeError("implicitly[SchemaConforms[UTypeWiden, U, SchemaPolicy.Exact]]")
    }
    "reject narrowing under Exact" in {
      assertTypeError("implicitly[SchemaConforms[UTypeNarrow, U, SchemaPolicy.Exact]]")
    }
  }
}
