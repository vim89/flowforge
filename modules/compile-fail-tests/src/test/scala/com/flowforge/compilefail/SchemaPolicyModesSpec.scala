package com.flowforge.compilefail

import com.flowforge.core.contracts.{ SchemaConforms, SchemaPolicy }
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * Negative/positive compile-time tests for extended schema policies imported from the Scala 3 PoC and adapted
 * for Scala 2 macros.
 */
class SchemaPolicyModesSpec extends AnyWordSpec with Matchers {

  // Reordered fields with the same names/types
  case class A(id: Long, name: String)
  case class B(name: String, id: Long)

  // Same positions, different names, same types
  case class C(x: Long, y: String)
  case class D(id: Long, name: String)

  "ExactOrdered" should {
    "fail when fields are reordered" in {
      val _ = 42
      assertTypeError("""
        implicitly[SchemaConforms[B, A, SchemaPolicy.ExactOrdered]]
      """)
    }
  }

  "ExactUnorderedCI" should {
    "succeed when fields are reordered" in
      assertCompiles("""
        implicitly[SchemaConforms[B, A, SchemaPolicy.ExactUnorderedCI]]
      """)
  }

  "ExactByPosition" should {
    "succeed when types match by position regardless of names" in
      assertCompiles("""
        implicitly[SchemaConforms[C, D, SchemaPolicy.ExactByPosition]]
      """)
    "fail when a type differs at a position" in {
      val _ = 42
      assertTypeError("""
        implicitly[SchemaConforms[(String, Long), D, SchemaPolicy.ExactByPosition]]
      """)
    }
  }
}
