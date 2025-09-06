package com.flowforge.core.contracts
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers._

final case class BwdOut(id: Long, name: Option[String]) // optional new field ok
final case class BwdContract(id: Long)

class SchemaConformsPolicyLawsSpec extends AnyWordSpec {
  "Backward policy" should {
    "allow adding optional fields" in {
      """implicitly[SchemaConforms[BwdOut, BwdContract, SchemaPolicy.Backward]]""" should compile
    }
  }
}
