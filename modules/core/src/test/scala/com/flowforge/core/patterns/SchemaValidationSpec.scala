package com.flowforge.core.patterns

import com.flowforge.core.algebra.SchemaIncompatible
import com.flowforge.core.patterns.ValidationTypes._
import com.flowforge.core.types.{ DataSchema, DataType, StructField }
import com.flowforge.core.types.RefinedTypes.{ FieldName, SchemaVersion }
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

/**
 * Comprehensive test suite for SchemaValidation.
 *
 * Coverage targets:
 * - Statement coverage: 100%
 * - Branch coverage: 100%
 * - All edge cases and error paths
 */
class SchemaValidationSpec extends AnyFunSuite with Matchers {

  // Helper schemas for testing
  val emptySchema: DataSchema = DataSchema.builder.build

  val simpleSchema: DataSchema = DataSchema.builder
    .addField("id", DataType.String, required = true)
    .addField("name", DataType.String, required = true)
    .addField("age", DataType.Integer, required = false)
    .build

  val compatibleSchema: DataSchema = DataSchema.builder
    .addField("id", DataType.String, required = true)
    .addField("name", DataType.String, required = true)
    .build

  test("compatible validates identical schemas") {
    val result = SchemaValidation.compatible(simpleSchema, simpleSchema)
    result.isValid shouldBe true
  }

  test("compatible validates when source has all target required fields") {
    val source = DataSchema.builder
      .addField("id", DataType.String, required = true)
      .addField("name", DataType.String, required = true)
      .addField("email", DataType.String, required = true)
      .build

    val target = DataSchema.builder
      .addField("id", DataType.String, required = true)
      .addField("name", DataType.String, required = true)
      .build

    val result = SchemaValidation.compatible(source, target)
    result.isValid shouldBe true
  }

  test("compatible validates when source has extra fields") {
    val source = DataSchema.builder
      .addField("id", DataType.String, required = true)
      .addField("name", DataType.String, required = true)
      .addField("extra", DataType.String, required = true)
      .build

    val target = DataSchema.builder
      .addField("id", DataType.String, required = true)
      .build

    val result = SchemaValidation.compatible(source, target)
    result.isValid shouldBe true
  }

  test("compatible rejects when source missing required target field") {
    val source = DataSchema.builder
      .addField("id", DataType.String, required = true)
      .build

    val target = DataSchema.builder
      .addField("id", DataType.String, required = true)
      .addField("name", DataType.String, required = true)
      .build

    val result = SchemaValidation.compatible(source, target)
    result.isInvalid shouldBe true
    getErrors(result).map(_.head) should matchPattern {
      case Some(_: SchemaIncompatible) =>
    }
  }

  test("compatible validates when source missing optional target field") {
    val source = DataSchema.builder
      .addField("id", DataType.String, required = true)
      .build

    val target = DataSchema.builder
      .addField("id", DataType.String, required = true)
      .addField("name", DataType.String, required = false)
      .build

    val result = SchemaValidation.compatible(source, target)
    result.isValid shouldBe true
  }

  test("compatible validates Integer to Long promotion") {
    val source = DataSchema.builder
      .addField("value", DataType.Integer, required = true)
      .build

    val target = DataSchema.builder
      .addField("value", DataType.Long, required = true)
      .build

    val result = SchemaValidation.compatible(source, target)
    result.isValid shouldBe true
  }

  test("compatible validates Float to Double promotion") {
    val source = DataSchema.builder
      .addField("value", DataType.Float, required = true)
      .build

    val target = DataSchema.builder
      .addField("value", DataType.Double, required = true)
      .build

    val result = SchemaValidation.compatible(source, target)
    result.isValid shouldBe true
  }

  test("compatible validates VarChar to String promotion") {
    val source = DataSchema.builder
      .addField("text", DataType.varchar(100), required = true)
      .build

    val target = DataSchema.builder
      .addField("text", DataType.String, required = true)
      .build

    val result = SchemaValidation.compatible(source, target)
    result.isValid shouldBe true
  }

  test("compatible validates nullable inner types") {
    val source = DataSchema.builder
      .addField("value", DataType.Nullable(DataType.Integer), required = false)
      .build

    val target = DataSchema.builder
      .addField("value", DataType.Nullable(DataType.Integer), required = false)
      .build

    val result = SchemaValidation.compatible(source, target)
    result.isValid shouldBe true
  }

  test("compatible validates non-nullable to nullable type") {
    val source = DataSchema.builder
      .addField("value", DataType.Integer, required = true)
      .build

    val target = DataSchema.builder
      .addField("value", DataType.Nullable(DataType.Integer), required = false)
      .build

    val result = SchemaValidation.compatible(source, target)
    result.isValid shouldBe true
  }

  test("compatible rejects incompatible data types") {
    val source = DataSchema.builder
      .addField("value", DataType.String, required = true)
      .build

    val target = DataSchema.builder
      .addField("value", DataType.Integer, required = true)
      .build

    val result = SchemaValidation.compatible(source, target)
    result.isInvalid shouldBe true
  }

  test("compatible rejects Long to Integer downcast") {
    val source = DataSchema.builder
      .addField("value", DataType.Long, required = true)
      .build

    val target = DataSchema.builder
      .addField("value", DataType.Integer, required = true)
      .build

    val result = SchemaValidation.compatible(source, target)
    result.isInvalid shouldBe true
  }

  test("compatible rejects Double to Float downcast") {
    val source = DataSchema.builder
      .addField("value", DataType.Double, required = true)
      .build

    val target = DataSchema.builder
      .addField("value", DataType.Float, required = true)
      .build

    val result = SchemaValidation.compatible(source, target)
    result.isInvalid shouldBe true
  }

  test("evolutionCompatible validates identical schemas") {
    val result = SchemaValidation.evolutionCompatible(simpleSchema, simpleSchema)
    result.isValid shouldBe true
  }

  test("evolutionCompatible validates adding optional field") {
    val oldSchema = DataSchema.builder
      .addField("id", DataType.String, required = true)
      .addField("name", DataType.String, required = true)
      .build

    val newSchema = DataSchema.builder
      .addField("id", DataType.String, required = true)
      .addField("name", DataType.String, required = true)
      .addField("email", DataType.String, required = false)
      .build

    val result = SchemaValidation.evolutionCompatible(oldSchema, newSchema)
    result.isValid shouldBe true
  }

  test("evolutionCompatible validates removing optional field") {
    val oldSchema = DataSchema.builder
      .addField("id", DataType.String, required = true)
      .addField("name", DataType.String, required = true)
      .addField("email", DataType.String, required = false)
      .build

    val newSchema = DataSchema.builder
      .addField("id", DataType.String, required = true)
      .addField("name", DataType.String, required = true)
      .build

    val result = SchemaValidation.evolutionCompatible(oldSchema, newSchema)
    result.isValid shouldBe true
  }

  test("evolutionCompatible rejects removing required field") {
    val oldSchema = DataSchema.builder
      .addField("id", DataType.String, required = true)
      .addField("name", DataType.String, required = true)
      .build

    val newSchema = DataSchema.builder
      .addField("id", DataType.String, required = true)
      .build

    val result = SchemaValidation.evolutionCompatible(oldSchema, newSchema)
    result.isInvalid shouldBe true
    getErrors(result).map(_.head) should matchPattern {
      case Some(_: SchemaIncompatible) =>
    }
  }

  test("evolutionCompatible rejects adding required field without default") {
    val oldSchema = DataSchema.builder
      .addField("id", DataType.String, required = true)
      .build

    val newSchema = DataSchema.builder
      .addField("id", DataType.String, required = true)
      .addField("name", DataType.String, required = true)
      .build

    val result = SchemaValidation.evolutionCompatible(oldSchema, newSchema)
    result.isInvalid shouldBe true
  }

  test("evolutionCompatible rejects incompatible type changes") {
    val oldSchema = DataSchema.builder
      .addField("id", DataType.String, required = true)
      .addField("value", DataType.Integer, required = true)
      .build

    val newSchema = DataSchema.builder
      .addField("id", DataType.String, required = true)
      .addField("value", DataType.String, required = true)
      .build

    val result = SchemaValidation.evolutionCompatible(oldSchema, newSchema)
    result.isInvalid shouldBe true
  }

  test("evolutionCompatible validates compatible type promotions") {
    val oldSchema = DataSchema.builder
      .addField("value", DataType.Integer, required = true)
      .build

    val newSchema = DataSchema.builder
      .addField("value", DataType.Long, required = true)
      .build

    val result = SchemaValidation.evolutionCompatible(oldSchema, newSchema)
    result.isValid shouldBe true
  }

  test("evolutionCompatible validates nullable type evolution") {
    val oldSchema = DataSchema.builder
      .addField("value", DataType.Integer, required = true)
      .build

    val newSchema = DataSchema.builder
      .addField("value", DataType.Nullable(DataType.Integer), required = false)
      .build

    val result = SchemaValidation.evolutionCompatible(oldSchema, newSchema)
    result.isValid shouldBe true
  }

  test("hasRequiredFields validates all required fields present") {
    val schema = DataSchema.builder
      .addField("id", DataType.String, required = true)
      .addField("name", DataType.String, required = true)
      .addField("email", DataType.String, required = true)
      .build

    val result = SchemaValidation.hasRequiredFields(schema, List("id", "name", "email"))
    result.isValid shouldBe true
  }

  test("hasRequiredFields validates subset of required fields") {
    val schema = DataSchema.builder
      .addField("id", DataType.String, required = true)
      .addField("name", DataType.String, required = true)
      .addField("email", DataType.String, required = true)
      .build

    val result = SchemaValidation.hasRequiredFields(schema, List("id", "name"))
    result.isValid shouldBe true
  }

  test("hasRequiredFields validates empty required fields list") {
    val schema = DataSchema.builder
      .addField("id", DataType.String, required = true)
      .build

    val result = SchemaValidation.hasRequiredFields(schema, List.empty)
    result.isValid shouldBe true
  }

  test("hasRequiredFields rejects missing required field") {
    val schema = DataSchema.builder
      .addField("id", DataType.String, required = true)
      .addField("name", DataType.String, required = true)
      .build

    val result = SchemaValidation.hasRequiredFields(schema, List("id", "name", "email"))
    result.isInvalid shouldBe true
    getErrors(result).map(_.head) should matchPattern {
      case Some(_: SchemaIncompatible) =>
    }
  }

  test("hasRequiredFields rejects multiple missing fields") {
    val schema = DataSchema.builder
      .addField("id", DataType.String, required = true)
      .build

    val result = SchemaValidation.hasRequiredFields(schema, List("name", "email", "phone"))
    result.isInvalid shouldBe true
  }

  test("hasRequiredFields validates against empty schema") {
    val result = SchemaValidation.hasRequiredFields(emptySchema, List("id"))
    result.isInvalid shouldBe true
  }

  test("compatible handles complex nested nullable types") {
    val source = DataSchema.builder
      .addField("value", DataType.Nullable(DataType.Nullable(DataType.Integer)), required = false)
      .build

    val target = DataSchema.builder
      .addField("value", DataType.Nullable(DataType.Nullable(DataType.Integer)), required = false)
      .build

    val result = SchemaValidation.compatible(source, target)
    result.isValid shouldBe true
  }

  test("compatible validates Integer to Nullable Long promotion") {
    val source = DataSchema.builder
      .addField("value", DataType.Integer, required = true)
      .build

    val target = DataSchema.builder
      .addField("value", DataType.Nullable(DataType.Long), required = false)
      .build

    val result = SchemaValidation.compatible(source, target)
    result.isValid shouldBe true
  }

  test("compatible validates Float to Nullable Double promotion") {
    val source = DataSchema.builder
      .addField("value", DataType.Float, required = true)
      .build

    val target = DataSchema.builder
      .addField("value", DataType.Nullable(DataType.Double), required = false)
      .build

    val result = SchemaValidation.compatible(source, target)
    result.isValid shouldBe true
  }

  test("evolutionCompatible validates complex schema evolution scenario") {
    val oldSchema = DataSchema.builder
      .addField("id", DataType.String, required = true)
      .addField("count", DataType.Integer, required = true)
      .addField("deprecated", DataType.String, required = false)
      .build

    val newSchema = DataSchema.builder
      .addField("id", DataType.String, required = true)
      .addField("count", DataType.Long, required = true) // promoted type
      .addField("new_optional", DataType.String, required = false) // added optional
      .build

    val result = SchemaValidation.evolutionCompatible(oldSchema, newSchema)
    result.isValid shouldBe true
  }

  test("evolutionCompatible accumulates multiple incompatibilities") {
    val oldSchema = DataSchema.builder
      .addField("id", DataType.String, required = true)
      .addField("name", DataType.String, required = true)
      .addField("count", DataType.Integer, required = true)
      .build

    val newSchema = DataSchema.builder
      .addField("id", DataType.String, required = true)
      .addField("count", DataType.String, required = true) // incompatible type change
      .addField("email", DataType.String, required = true) // added required field
      .build

    val result = SchemaValidation.evolutionCompatible(oldSchema, newSchema)
    result.isInvalid shouldBe true
    // Should have multiple errors: removed required field 'name', incompatible type for 'count', added required field 'email'
    getErrors(result).map(_.size).exists(_ > 1) shouldBe true
  }

  test("compatible handles empty source schema") {
    val target = DataSchema.builder
      .addField("id", DataType.String, required = false)
      .build

    val result = SchemaValidation.compatible(emptySchema, target)
    result.isValid shouldBe true
  }

  test("compatible handles empty target schema") {
    val source = DataSchema.builder
      .addField("id", DataType.String, required = true)
      .build

    val result = SchemaValidation.compatible(source, emptySchema)
    result.isValid shouldBe true
  }

  test("compatible validates VarChar with different lengths") {
    val source = DataSchema.builder
      .addField("text", DataType.varchar(50), required = true)
      .build

    val target = DataSchema.builder
      .addField("text", DataType.varchar(100), required = true)
      .build

    // VarChar(50) and VarChar(100) are different types - should be incompatible
    val result = SchemaValidation.compatible(source, target)
    result.isInvalid shouldBe true
  }
}
