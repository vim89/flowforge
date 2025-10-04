/**
 * FlowForge Enhanced Contracts DSL
 *
 * Provides idiomatic syntax extensions for building data contracts in a fluent, type-safe manner. This DSL
 * significantly reduces verbosity while maintaining compile-time safety.
 */
package com.flowforge.contracts.syntax

import cats.data.NonEmptyList
import cats.implicits._
import com.flowforge.contracts._
import com.flowforge.core.types.RefinedTypes.SchemaVersion
import eu.timepit.refined.types.string.NonEmptyString

import scala.util.matching.Regex

/**
 * Enhanced Contract Builder with fluent DSL
 */
case class ContractBuilder(name: String) {
  private var fields: List[FieldBuilder]          = List.empty
  private var versionOpt: Option[ContractVersion] = None
  private var slaOpt: Option[String]              = None
  private var ownerOpt: Option[String]            = None
  private var metadata: Map[String, String]       = Map.empty

  def field(name: String): FieldBuilder = {
    val fieldBuilder = FieldBuilder(name, this)
    fields = fieldBuilder :: fields
    fieldBuilder
  }

  def withSLA(sla: String): ContractBuilder = {
    slaOpt = Some(sla)
    this
  }

  def withOwner(owner: String): ContractBuilder = {
    ownerOpt = Some(owner)
    this
  }

  def withVersion(
    major: Int,
    minor: Int,
    patch: Int,
  ): ContractBuilder = {
    versionOpt = Some(ContractVersion(major, minor, patch))
    this
  }

  def withMetadata(key: String, value: String): ContractBuilder = {
    metadata = metadata + (key -> value)
    this
  }

  private[syntax] def addField(fieldBuilder: FieldBuilder): ContractBuilder =
    // Field is already added to the list in field() method
    this

  def build: ContractSchema = {
    versionOpt.getOrElse(ContractVersion(1, 0, 0))
    val finalMetadata = metadata ++
      slaOpt.map("sla" -> _) ++
      ownerOpt.map("owner" -> _)

    ContractSchema(
      name = NonEmptyString.unsafeFrom(name),
      fields = fields.reverse.map(_.build),
      version = SchemaVersion.unsafeFrom(1),
      metadata = finalMetadata,
    )
  }
}

/**
 * Enhanced Field Builder with fluent DSL
 */
case class FieldBuilder(name: String, parent: ContractBuilder) {
  private var fieldType: Option[FieldType] = None

  private var isOptional: Boolean                = false
  private var constraints: List[FieldConstraint] = List.empty
  private var descriptionOpt: Option[String]     = None

  // Type specification methods
  def required: TypedFieldBuilder = {
    isOptional = false
    TypedFieldBuilder(this)
  }

  def optional: TypedFieldBuilder = {
    isOptional = true
    TypedFieldBuilder(this)
  }

  private[syntax] def setFieldType(ft: FieldType): FieldBuilder = {
    fieldType = Some(ft)
    this
  }

  private[syntax] def addConstraint(constraint: FieldConstraint): FieldBuilder = {
    constraints = constraint :: constraints
    this
  }

  private[syntax] def setDescription(desc: String): FieldBuilder = {
    descriptionOpt = Some(desc)
    this
  }

  def build: FieldContract =
    FieldContract(
      name = NonEmptyString.unsafeFrom(name),
      dataType = fieldType.getOrElse(FieldType.StringType), // Default to string
      nullable = isOptional,
      constraints = constraints.reverse,
      description = descriptionOpt,
    )
}

/**
 * Typed Field Builder that provides type-specific methods
 */
case class TypedFieldBuilder(fieldBuilder: FieldBuilder) {

  // Basic types
  def string: StringFieldBuilder = {
    fieldBuilder.setFieldType(FieldType.StringType)
    new StringFieldBuilder(fieldBuilder)
  }

  def int: NumericFieldBuilder[Int] = {
    fieldBuilder.setFieldType(FieldType.IntType)
    new NumericFieldBuilder[Int](fieldBuilder)
  }

  def long: NumericFieldBuilder[Long] = {
    fieldBuilder.setFieldType(FieldType.LongType)
    new NumericFieldBuilder[Long](fieldBuilder)
  }

  def double: NumericFieldBuilder[Double] = {
    fieldBuilder.setFieldType(FieldType.DoubleType)
    new NumericFieldBuilder[Double](fieldBuilder)
  }

  def boolean: FieldTerminator = {
    fieldBuilder.setFieldType(FieldType.BooleanType)
    FieldTerminator(fieldBuilder)
  }

  def timestamp: FieldTerminator = {
    fieldBuilder.setFieldType(FieldType.TimestampType)
    FieldTerminator(fieldBuilder)
  }

  def decimal(precision: Int, scale: Int): NumericFieldBuilder[BigDecimal] = {
    fieldBuilder.setFieldType(FieldType.DecimalType(precision, scale))
    new NumericFieldBuilder[BigDecimal](fieldBuilder)
  }

  def array(elementType: FieldType): FieldTerminator = {
    fieldBuilder.setFieldType(FieldType.ArrayType(elementType))
    FieldTerminator(fieldBuilder)
  }
}

/**
 * String-specific field builder with string constraints
 */
class StringFieldBuilder(fieldBuilder: FieldBuilder) extends FieldTerminator(fieldBuilder) {

  def minLength(length: Int): StringFieldBuilder = {
    fieldBuilder.addConstraint(FieldConstraint.MinLength(length))
    this
  }

  def maxLength(length: Int): StringFieldBuilder = {
    fieldBuilder.addConstraint(FieldConstraint.MaxLength(length))
    this
  }

  def matches(regex: Regex): StringFieldBuilder = {
    fieldBuilder.addConstraint(FieldConstraint.Pattern(regex))
    this
  }

  def matches(pattern: String): StringFieldBuilder = {
    fieldBuilder.addConstraint(FieldConstraint.Pattern(pattern.r))
    this
  }

  def oneOf(values: String*): StringFieldBuilder = {
    fieldBuilder.addConstraint(FieldConstraint.OneOf(values.toSet))
    this
  }

  def email: StringFieldBuilder = {
    val emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$".r
    fieldBuilder.addConstraint(FieldConstraint.Pattern(emailRegex))
    this
  }

  def url: StringFieldBuilder = {
    val urlRegex = "^(https?|ftp)://[^\\s/$.?#].[^\\s]*$".r
    fieldBuilder.addConstraint(FieldConstraint.Pattern(urlRegex))
    this
  }

  def uuid: StringFieldBuilder = {
    val uuidRegex =
      "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$".r
    fieldBuilder.addConstraint(FieldConstraint.Pattern(uuidRegex))
    this
  }
}

/**
 * Numeric field builder with numeric constraints
 */
class NumericFieldBuilder[T](fieldBuilder: FieldBuilder) extends FieldTerminator(fieldBuilder) {

  def min(minValue: Double): NumericFieldBuilder[T] = {
    fieldBuilder.addConstraint(FieldConstraint.Range(minValue, Double.MaxValue))
    this
  }

  def max(maxValue: Double): NumericFieldBuilder[T] = {
    fieldBuilder.addConstraint(FieldConstraint.Range(Double.MinValue, maxValue))
    this
  }

  def range(minValue: Double, maxValue: Double): NumericFieldBuilder[T] = {
    fieldBuilder.addConstraint(FieldConstraint.Range(minValue, maxValue))
    this
  }

  def positive: NumericFieldBuilder[T] = {
    fieldBuilder.addConstraint(FieldConstraint.Range(0.0, Double.MaxValue))
    this
  }

  def negative: NumericFieldBuilder[T] = {
    fieldBuilder.addConstraint(FieldConstraint.Range(Double.MinValue, 0.0))
    this
  }
}

/**
 * Field terminator that allows returning to contract building
 */
case class FieldTerminator(fieldBuilder: FieldBuilder) {

  def field(name: String): FieldBuilder =
    fieldBuilder.parent.field(name)

  def withSLA(sla: String): ContractBuilder =
    fieldBuilder.parent.withSLA(sla)

  def withOwner(owner: String): ContractBuilder =
    fieldBuilder.parent.withOwner(owner)

  def withVersion(
    major: Int,
    minor: Int,
    patch: Int,
  ): ContractBuilder =
    fieldBuilder.parent.withVersion(major, minor, patch)

  def build: ContractSchema =
    fieldBuilder.parent.build

  def describedAs(description: String): FieldTerminator = {
    fieldBuilder.setDescription(description)
    this
  }
}

/**
 * DSL entry points and syntax extensions
 */
object ContractDSL {

  /**
   * Create a new contract with the given name
   */
  def Contract(name: String): ContractBuilder = ContractBuilder(name)

  /**
   * Predefined common regex patterns
   */
  object Patterns {
    val EMAIL: Regex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$".r
    val URL: Regex   = "^(https?|ftp)://[^\\s/$.?#].[^\\s]*$".r
    val UUID: Regex =
      "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$".r
    val PHONE_US: Regex = "^\\+?1?[-.\\s]?\\(?[0-9]{3}\\)?[-.\\s]?[0-9]{3}[-.\\s]?[0-9]{4}$".r
    val CREDIT_CARD: Regex =
      "^(?:4[0-9]{12}(?:[0-9]{3})?|5[1-5][0-9]{14}|3[47][0-9]{13}|3[0-9]{13}|6(?:011|5[0-9]{2})[0-9]{12})$".r
    val DATE_ISO: Regex     = "^\\d{4}-\\d{2}-\\d{2}$".r
    val DATETIME_ISO: Regex = "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d{3})?Z?$".r
  }

  /**
   * Example usage demonstrations
   */
  object Examples {

    /**
     * User contract with comprehensive field definitions
     */
    def userContract: ContractSchema =
      Contract("user")
        .field("id").required.long.positive
        .field("email").required.string.email.maxLength(255)
        .field("name").required.string.minLength(2).maxLength(100)
        .field("age").optional.int.range(0, 150)
        .field("phone").optional.string.matches(Patterns.PHONE_US)
        .field("website").optional.string.url
        .withSLA("hourly")
        .withOwner("DataPlatformTeam")
        .withVersion(1, 0, 0)
        .build

    /**
     * Transaction contract with financial constraints
     */
    def transactionContract: ContractSchema =
      Contract("transaction")
        .field("id").required.string.uuid
        .field("amount").required.decimal(10, 2).positive
        .field("currency").required.string.oneOf("USD", "EUR", "GBP", "JPY")
        .field("merchant_id").required.string.minLength(1).maxLength(50)
        .field("timestamp").required.timestamp
        .field("status").required.string.oneOf("pending", "completed", "failed", "cancelled")
        .withSLA("real-time")
        .withOwner("PaymentsTeam")
        .withVersion(2, 1, 0)
        .build

    /**
     * Product contract with business rules
     */
    def productContract: ContractSchema =
      Contract("product")
        .field("sku").required.string.matches("^[A-Z]{2}[0-9]{6}$".r).describedAs(
          "Product SKU in format XX123456",
        )
        .field("name").required.string.minLength(5).maxLength(200)
        .field("description").optional.string.maxLength(2000)
        .field("price").required.decimal(8, 2).positive
        .field("category_id").required.int.positive
        .field("is_active").required.boolean
        .field("tags").optional.string.describedAs("Comma-separated tags")
        .withSLA("daily")
        .withOwner("ProductTeam")
        .withVersion(1, 2, 0)
        .build
  }
}

/**
 * Implicit conversions for seamless integration
 */
object ContractSyntax {

  implicit class ContractOps(contract: ContractSchema) {
    def toDataContract[A]: DataContract[A] =
      new DataContract[A] {
        def validate(data: A): cats.data.ValidatedNel[ContractViolation, A] =
          // Basic validation - in practice this would introspect the data structure
          data.validNel

        def schema: ContractSchema   = contract
        def version: ContractVersion = ContractVersion(1, 0, 0)
        def rules: NonEmptyList[ValidationRule[A]] =
          NonEmptyList.one(ValidationRules.custom[A]("schema_valid")(_ => ().validNel))
      }
  }

  implicit class StringFieldOps(name: String) {
    def requiredString: StringFieldBuilder = {
      val builder = ContractBuilder(name).field(name)
      builder.required.string
    }

    def optionalString: StringFieldBuilder = {
      val builder = ContractBuilder(name).field(name)
      builder.optional.string
    }
  }
}
