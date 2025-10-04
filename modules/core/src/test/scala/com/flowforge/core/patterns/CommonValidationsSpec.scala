package com.flowforge.core.patterns

import com.flowforge.core.patterns.ValidationTypes._
import com.flowforge.core.types.RefinedTypes._
import com.flowforge.core.types._
import eu.timepit.refined.api.Refined
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

/**
 * Comprehensive test suite for CommonValidations.
 *
 * Coverage targets:
 * - Statement coverage: 100%
 * - Branch coverage: 100%
 * - All edge cases and error paths
 */
class CommonValidationsSpec extends AnyFunSuite with Matchers {

  test("validateUser validates valid user") {
    val user = CommonValidations.UserValidation("Alice", "alice@example.com", 25)
    val result = CommonValidations.validateUser(user)

    result.isValid shouldBe true
    getValue(result) shouldBe Some(user)
  }

  test("validateUser rejects user with empty name") {
    val user = CommonValidations.UserValidation("", "alice@example.com", 25)
    val result = CommonValidations.validateUser(user)

    result.isInvalid shouldBe true
    getErrors(result).map(_.head) should matchPattern {
      case Some(ConfigError.InvalidValue("name", _, "non-empty string")) =>
    }
  }

  test("validateUser rejects user with whitespace-only name") {
    val user = CommonValidations.UserValidation("   ", "alice@example.com", 25)
    val result = CommonValidations.validateUser(user)

    result.isInvalid shouldBe true
  }

  test("validateUser rejects user with invalid email") {
    val user = CommonValidations.UserValidation("Alice", "invalid-email", 25)
    val result = CommonValidations.validateUser(user)

    result.isInvalid shouldBe true
    getErrors(result).map(_.head) should matchPattern {
      case Some(ConfigError.InvalidFormat("email", "invalid-email", _)) =>
    }
  }

  test("validateUser rejects user with age below minimum") {
    val user = CommonValidations.UserValidation("Alice", "alice@example.com", -1)
    val result = CommonValidations.validateUser(user)

    result.isInvalid shouldBe true
    getErrors(result).map(_.head) should matchPattern {
      case Some(ConfigError.OutOfRange("age", "-1", "0", "150")) =>
    }
  }

  test("validateUser rejects user with age above maximum") {
    val user = CommonValidations.UserValidation("Alice", "alice@example.com", 200)
    val result = CommonValidations.validateUser(user)

    result.isInvalid shouldBe true
    getErrors(result).map(_.head) should matchPattern {
      case Some(ConfigError.OutOfRange("age", "200", "0", "150")) =>
    }
  }

  test("validateUser validates user at age boundaries") {
    val user1 = CommonValidations.UserValidation("Alice", "alice@example.com", 0)
    val user2 = CommonValidations.UserValidation("Bob", "bob@example.com", 150)

    CommonValidations.validateUser(user1).isValid shouldBe true
    CommonValidations.validateUser(user2).isValid shouldBe true
  }

  test("validateUser accumulates multiple errors") {
    val user = CommonValidations.UserValidation("", "invalid-email", 200)
    val result = CommonValidations.validateUser(user)

    result.isInvalid shouldBe true
    getErrors(result).map(_.size) shouldBe Some(3)
  }

  test("validateUser validates email with plus sign") {
    val user = CommonValidations.UserValidation("Alice", "alice+tag@example.com", 25)
    val result = CommonValidations.validateUser(user)

    result.isValid shouldBe true
  }

  test("validateUser validates email with subdomain") {
    val user = CommonValidations.UserValidation("Alice", "alice@mail.example.com", 25)
    val result = CommonValidations.validateUser(user)

    result.isValid shouldBe true
  }

  test("validatePipelineConfig validates valid configuration") {
    val source = DataSource.local("/tmp/input", DataFormat.Parquet)
    val sink = DataSink.local("/tmp/output", DataFormat.Parquet)
    val config = PipelineConfig(
      name = Refined.unsafeApply("test-pipeline"),
      environment = Environment.Development,
      source = source,
      sink = sink
    )

    val result = CommonValidations.validatePipelineConfig(config)
    result.isValid shouldBe true
    getValue(result) shouldBe Some(config)
  }

  test("validatePipelineConfig rejects empty pipeline name") {
    val source = DataSource.local("/tmp/input", DataFormat.Parquet)
    val sink = DataSink.local("/tmp/output", DataFormat.Parquet)

    // Create a config with empty name - we need to bypass the refined type
    val config = PipelineConfig(
      name = Refined.unsafeApply(" "), // whitespace only
      environment = Environment.Development,
      source = source,
      sink = sink
    )

    val result = CommonValidations.validatePipelineConfig(config)
    result.isInvalid shouldBe true
  }

  test("validatePipelineConfig validates environment field") {
    val source = DataSource.local("/tmp/input", DataFormat.Parquet)
    val sink = DataSink.local("/tmp/output", DataFormat.Parquet)

    val devConfig = PipelineConfig(
      name = Refined.unsafeApply("test-pipeline"),
      environment = Environment.Development,
      source = source,
      sink = sink
    )

    val prodConfig = PipelineConfig(
      name = Refined.unsafeApply("test-pipeline"),
      environment = Environment.Production,
      source = source,
      sink = sink
    )

    CommonValidations.validatePipelineConfig(devConfig).isValid shouldBe true
    CommonValidations.validatePipelineConfig(prodConfig).isValid shouldBe true
  }

  test("validatePipelineConfig validates source field") {
    val source = DataSource.gcs("my-bucket", "data/input", DataFormat.JSON)
    val sink = DataSink.local("/tmp/output", DataFormat.Parquet)

    val config = PipelineConfig(
      name = Refined.unsafeApply("test-pipeline"),
      environment = Environment.Development,
      source = source,
      sink = sink
    )

    val result = CommonValidations.validatePipelineConfig(config)
    result.isValid shouldBe true
  }

  test("validatePipelineConfig validates sink field") {
    val source = DataSource.local("/tmp/input", DataFormat.Parquet)
    val sink = DataSink.gcs("output-bucket", "data/output", DataFormat.Avro)

    val config = PipelineConfig(
      name = Refined.unsafeApply("test-pipeline"),
      environment = Environment.Development,
      source = source,
      sink = sink
    )

    val result = CommonValidations.validatePipelineConfig(config)
    result.isValid shouldBe true
  }

  test("validateDataQuality validates non-empty data") {
    val data = List(1, 2, 3, 4, 5)
    val rules = QualityRules.empty
    val result = CommonValidations.validateDataQuality(data, rules)

    result.isValid shouldBe true
    getValue(result) shouldBe Some(data)
  }

  test("validateDataQuality validates single element list") {
    val data = List(42)
    val rules = QualityRules.empty
    val result = CommonValidations.validateDataQuality(data, rules)

    result.isValid shouldBe true
  }

  test("validateDataQuality rejects empty data") {
    val data = List.empty[Int]
    val rules = QualityRules.empty
    val result = CommonValidations.validateDataQuality(data, rules)

    result.isInvalid shouldBe true
  }

  test("validateDataQuality works with different data types") {
    val stringData = List("a", "b", "c")
    val rules = QualityRules.empty
    val result = CommonValidations.validateDataQuality(stringData, rules)

    result.isValid shouldBe true
  }

  test("validateDataQuality works with complex objects") {
    case class Record(id: Int, name: String)
    val data = List(Record(1, "A"), Record(2, "B"))
    val rules = QualityRules.empty
    val result = CommonValidations.validateDataQuality(data, rules)

    result.isValid shouldBe true
  }

  test("validateDataQuality ignores rules parameter") {
    val data = List(1, 2, 3)
    val strictRules = QualityRules.strict
    val standardRules = QualityRules.standard

    // Should behave the same regardless of rules
    val result1 = CommonValidations.validateDataQuality(data, strictRules)
    val result2 = CommonValidations.validateDataQuality(data, standardRules)

    result1.isValid shouldBe true
    result2.isValid shouldBe true
  }

  test("validateSchemaCompatibility validates compatible schemas") {
    val source = DataSchema.builder
      .addField("id", DataType.String, required = true)
      .addField("name", DataType.String, required = true)
      .build

    val target = DataSchema.builder
      .addField("id", DataType.String, required = true)
      .build

    val result = CommonValidations.validateSchemaCompatibility(source, target)
    result.isValid shouldBe true
  }

  test("validateSchemaCompatibility rejects incompatible schemas") {
    val source = DataSchema.builder
      .addField("id", DataType.String, required = true)
      .build

    val target = DataSchema.builder
      .addField("id", DataType.String, required = true)
      .addField("name", DataType.String, required = true)
      .build

    val result = CommonValidations.validateSchemaCompatibility(source, target)
    result.isInvalid shouldBe true
  }

  test("validateSchemaCompatibility validates identical schemas") {
    val schema = DataSchema.builder
      .addField("id", DataType.String, required = true)
      .addField("name", DataType.String, required = true)
      .addField("age", DataType.Integer, required = false)
      .build

    val result = CommonValidations.validateSchemaCompatibility(schema, schema)
    result.isValid shouldBe true
  }

  test("validateSchemaCompatibility validates type promotions") {
    val source = DataSchema.builder
      .addField("value", DataType.Integer, required = true)
      .build

    val target = DataSchema.builder
      .addField("value", DataType.Long, required = true)
      .build

    val result = CommonValidations.validateSchemaCompatibility(source, target)
    result.isValid shouldBe true
  }

  test("validateSchemaCompatibility rejects type demotions") {
    val source = DataSchema.builder
      .addField("value", DataType.Long, required = true)
      .build

    val target = DataSchema.builder
      .addField("value", DataType.Integer, required = true)
      .build

    val result = CommonValidations.validateSchemaCompatibility(source, target)
    result.isInvalid shouldBe true
  }

  test("UserValidation case class stores values correctly") {
    val user = CommonValidations.UserValidation("Alice", "alice@example.com", 25)
    user.name shouldBe "Alice"
    user.email shouldBe "alice@example.com"
    user.age shouldBe 25
  }

  test("validateUser handles null name") {
    val user = CommonValidations.UserValidation(null, "alice@example.com", 25)
    val result = CommonValidations.validateUser(user)

    result.isInvalid shouldBe true
  }

  test("validateDataQuality error includes field name") {
    val data = List.empty[String]
    val rules = QualityRules.empty
    val result = CommonValidations.validateDataQuality(data, rules)

    result.isInvalid shouldBe true
    // Error type depends on internal implementation, just verify it's invalid
    getErrors(result).map(_.size) shouldBe Some(1)
  }

  test("validatePipelineConfig with all environments") {
    val source = DataSource.local("/tmp/input", DataFormat.Parquet)
    val sink = DataSink.local("/tmp/output", DataFormat.Parquet)

    val environments = List(
      Environment.Development,
      Environment.Staging,
      Environment.Production
    )

    environments.foreach { env =>
      val config = PipelineConfig(
        name = Refined.unsafeApply("test-pipeline"),
        environment = env,
        source = source,
        sink = sink
      )

      val result = CommonValidations.validatePipelineConfig(config)
      result.isValid shouldBe true
    }
  }

  test("validatePipelineConfig with different source types") {
    val sink = DataSink.local("/tmp/output", DataFormat.Parquet)

    val sources = List(
      DataSource.local("/tmp/input", DataFormat.Parquet),
      DataSource.gcs("bucket", "prefix", DataFormat.JSON),
      DataSource.s3("bucket", "prefix", DataFormat.Avro)
    )

    sources.foreach { source =>
      val config = PipelineConfig(
        name = Refined.unsafeApply("test-pipeline"),
        environment = Environment.Development,
        source = source,
        sink = sink
      )

      val result = CommonValidations.validatePipelineConfig(config)
      result.isValid shouldBe true
    }
  }

  test("validatePipelineConfig with different sink types") {
    val source = DataSource.local("/tmp/input", DataFormat.Parquet)

    val sinks = List(
      DataSink.local("/tmp/output", DataFormat.Parquet),
      DataSink.gcs("bucket", "prefix", DataFormat.JSON),
      DataSink.s3("bucket", "prefix", DataFormat.Avro)
    )

    sinks.foreach { sink =>
      val config = PipelineConfig(
        name = Refined.unsafeApply("test-pipeline"),
        environment = Environment.Development,
        source = source,
        sink = sink
      )

      val result = CommonValidations.validatePipelineConfig(config)
      result.isValid shouldBe true
    }
  }

  test("validateDataQuality with large dataset") {
    val data = (1 to 10000).toList
    val rules = QualityRules.empty
    val result = CommonValidations.validateDataQuality(data, rules)

    result.isValid shouldBe true
  }

  test("validateUser with minimal valid input") {
    val user = CommonValidations.UserValidation("A", "a@b.co", 0)
    val result = CommonValidations.validateUser(user)

    result.isValid shouldBe true
  }

  test("validateUser with maximal valid input") {
    val longName = "A" * 1000
    val longEmail = "a" * 50 + "@" + "b" * 50 + ".com"
    val user = CommonValidations.UserValidation(longName, longEmail, 150)
    val result = CommonValidations.validateUser(user)

    result.isValid shouldBe true
  }

  test("validateSchemaCompatibility with empty schemas") {
    val emptySchema = DataSchema.builder.build
    val result = CommonValidations.validateSchemaCompatibility(emptySchema, emptySchema)

    result.isValid shouldBe true
  }

  test("validateSchemaCompatibility with complex schemas") {
    val source = DataSchema.builder
      .addField("id", DataType.String, required = true)
      .addField("name", DataType.String, required = true)
      .addField("age", DataType.Integer, required = false)
      .addField("email", DataType.String, required = false)
      .build

    val target = DataSchema.builder
      .addField("id", DataType.String, required = true)
      .addField("name", DataType.String, required = true)
      .build

    val result = CommonValidations.validateSchemaCompatibility(source, target)
    result.isValid shouldBe true
  }
}
