package com.flowforge.core.errors

import com.flowforge.core.types.FlowForgeError
import com.flowforge.core.types.ValidationError
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class FlowForgeErrorUtilsCoverageSpec extends AnyFunSuite with Matchers {
  test("fromThrowable and factory helpers") {
    val ex  = new RuntimeException("boom")
    val err = FlowForgeError.fromThrowable(ex, Map("k" -> "v"))
    err.message should include ("boom")

    val mf = FlowForgeError.missingField("id")
    mf.fieldName shouldBe "id"

    val sv = FlowForgeError.schemaViolation("f", "string", "int")
    sv.field shouldBe "f"

    // Ensure show instance does not crash
    FlowForgeError.showFlowForgeError.show(ValidationError.MissingRequiredField("x")) should include ("FlowForgeError")
  }
}

