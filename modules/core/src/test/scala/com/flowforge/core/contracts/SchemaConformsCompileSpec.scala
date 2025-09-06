package com.flowforge.core.contracts
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers._

final case class OutA(id: Long, name: String)
final case class CExact(id: Long, name: String)
final case class CDrifted(id: Long) // missing name

class SchemaConformsCompileSpec extends AnyWordSpec {
  "SchemaConforms" should {
    "compile when shapes match under Exact" in {
      """implicitly[SchemaConforms[OutA, CExact, SchemaPolicy.Exact]]""" should compile
    }
    "fail to compile when a field is missing under Exact" in
      assertTypeError("""implicitly[SchemaConforms[OutA, CDrifted, SchemaPolicy.Exact]]""")
  }
}
