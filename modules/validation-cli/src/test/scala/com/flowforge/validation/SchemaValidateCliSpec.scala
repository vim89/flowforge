package com.flowforge.validation

import com.flowforge.validation.SchemaValidateCli.Canonical
import org.apache.spark.sql.types._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class SchemaValidateCliSpec extends AnyFunSuite with Matchers {

  test("Canonical.fromFile parses Spark-style JSON schema") {
    val json =
      """
        |{
        |  "fields": [
        |    { "name": "id",     "type": {"type": "integer"}, "nullable": false },
        |    { "name": "amount", "type": "double",            "nullable": true  }
        |  ]
        |}
        |""".stripMargin

    val model = Canonical.fromFile(json, SchemaValidateCli.ExpectedFormat.Spark)
    model.fields.map(_.name) should contain inOrder ("id", "amount")
    model.fields.find(_.name == "id").get.tpe shouldBe "integer"
    model.fields.find(_.name == "id").get.nullable shouldBe false
    model.fields.find(_.name == "amount").get.tpe shouldBe "double"
    model.fields.find(_.name == "amount").get.nullable shouldBe true
  }

  test("Canonical.fromSpark + diff detects mismatches") {
    val sparkSchema = StructType(
      Array(
        StructField("id", IntegerType, nullable = false),
        StructField("amount", DoubleType, nullable = true),
        StructField("flag", BooleanType, nullable = false),
      ),
    )
    val actual = Canonical.fromSpark(sparkSchema)

    // Expected missing the flag and with different nullability for amount
    val expectedJson =
      """
        |{
        |  "fields": [
        |    { "name": "id",     "type": "integer", "nullable": false },
        |    { "name": "amount", "type": "double",  "nullable": false }
        |  ]
        |}
        |""".stripMargin
    val expected = Canonical.fromFile(expectedJson, SchemaValidateCli.ExpectedFormat.Spark)

    val diffs = Canonical.diff(expected, actual)
    diffs.exists {
      case Canonical.Diff.ExtraField(n) => n == "flag"
      case _                            => false
    } shouldBe true
    diffs.exists {
      case Canonical.Diff.NullabilityMismatch(n, exp, act) => n == "amount" && !exp && act
      case _                                               => false
    } shouldBe true

    val msg = Canonical.prettyDiffs(diffs)
    msg should include("Schema differences:")
    msg should include("Extra field: flag")
    msg should include("Nullability mismatch for amount")
  }

  test("Canonical.diff returns empty list when schemas match") {
    val schema = StructType(
      Array(
        StructField("id", IntegerType, nullable = false),
        StructField("amount", DoubleType, nullable = true),
      ),
    )
    val actual = Canonical.fromSpark(schema)
    val expected = Canonical.fromFile(
      """
        |{
        |  "fields": [
        |    { "name": "id",     "type": "integer", "nullable": false },
        |    { "name": "amount", "type": "double",  "nullable": true }
        |  ]
        |}
        |""".stripMargin,
      SchemaValidateCli.ExpectedFormat.Spark,
    )
    Canonical.diff(expected, actual) shouldBe Nil
  }
}
