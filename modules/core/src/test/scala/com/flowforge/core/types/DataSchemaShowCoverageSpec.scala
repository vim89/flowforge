package com.flowforge.core.types

import cats.syntax.show._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import com.flowforge.core.testing.What

class DataSchemaShowCoverageSpec extends AnyFunSuite with Matchers {
  test("showDataSchema produces formatted output and evolve updates version", What) {
    val s1 = DataSchema.builder
      .addField("id", DataType.String, required = true)
      .addField("age", DataType.Integer, required = false)
      .withMetadata("owner", "team")
      .build
    val txt = s1.show
    txt should include ("DataSchema(v1)")
    txt should include ("id: STRING")
    txt should include ("age: INTEGER?")

    val s2 = s1.evolve(List(StructField.required("email", DataType.String)))
    s2.version.value shouldBe (s1.version.value + 1)
  }
}
