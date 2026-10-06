/**
 * FlowForge Enhanced Contracts DSL
 *
 * Provides idiomatic syntax extensions for building data contracts in a fluent, type-safe manner. This DSL
 * significantly reduces verbosity while maintaining compile-time safety.
 *
 * Every builder here is immutable: a step returns a new builder carrying the contract built so far, rather
 * than mutating a shared root. That is why a field-level builder holds the contract it came from. The chain
 * `Contract("u").field("id").required.long.positive.field("email")...` therefore has to commit the field in
 * progress whenever it moves on, which is what [[FieldBuilder.commit]] does.
 */
package com.flowforge.contracts.syntax

import cats.data.NonEmptyList
import cats.implicits._
import com.flowforge.contracts._
import com.flowforge.core.types.RefinedTypes.SchemaVersion
import eu.timepit.refined.types.string.NonEmptyString

import scala.util.matching.Regex

/**
 * Accumulates the parts of a contract. `fields` is in declaration order.
 */
case class ContractBuilder(
  name: String,
  fields: List[FieldContract] = List.empty,
  versionOpt: Option[ContractVersion] = None,
  slaOpt: Option[String] = None,
  ownerOpt: Option[String] = None,
  metadata: Map[String, String] = Map.empty) {

  def field(name: String): FieldBuilder = FieldBuilder(name, this)

  def withSLA(sla: String): ContractBuilder = copy(slaOpt = Some(sla))

  def withOwner(owner: String): ContractBuilder = copy(ownerOpt = Some(owner))

  def withVersion(
    major: Int,
    minor: Int,
    patch: Int,
  ): ContractBuilder = copy(versionOpt = Some(ContractVersion(major, minor, patch)))

  def withMetadata(key: String, value: String): ContractBuilder =
    copy(metadata = metadata + (key -> value))

  private[syntax] def addField(fieldContract: FieldContract): ContractBuilder =
    copy(fields = fields :+ fieldContract)

  def build: ContractSchema = {
    val finalMetadata = metadata ++
      slaOpt.map("sla" -> _) ++
      ownerOpt.map("owner" -> _)

    ContractSchema(
      name = NonEmptyString.unsafeFrom(name),
      fields = fields,
      // ContractSchema carries a single positive integer version, so only the major part survives. A major
      // of 0 has no representation there and falls back to 1.
      version = SchemaVersion.unsafeFrom(versionOpt.map(_.major).filter(_ > 0).getOrElse(1)),
      metadata = finalMetadata,
    )
  }
}

/**
 * One field in progress, together with the contract it belongs to.
 */
case class FieldBuilder(
  name: String,
  parent: ContractBuilder,
  fieldType: Option[FieldType] = None,
  isOptional: Boolean = false,
  constraints: List[FieldConstraint] = List.empty,
  descriptionOpt: Option[String] = None) {

  // Type specification methods
  def required: TypedFieldBuilder = TypedFieldBuilder(copy(isOptional = false))

  def optional: TypedFieldBuilder = TypedFieldBuilder(copy(isOptional = true))

  private[syntax] def setFieldType(ft: FieldType): FieldBuilder = copy(fieldType = Some(ft))

  private[syntax] def addConstraint(constraint: FieldConstraint): FieldBuilder =
    copy(constraints = constraints :+ constraint)

  private[syntax] def setDescription(desc: String): FieldBuilder = copy(descriptionOpt = Some(desc))

  /** Folds this field into its contract. Called whenever the chain leaves the field. */
  private[syntax] def commit: ContractBuilder = parent.addField(build)

  def build: FieldContract =
    FieldContract(
      name = NonEmptyString.unsafeFrom(name),
      dataType = fieldType.getOrElse(FieldType.StringType), // Default to string
      nullable = isOptional,
      constraints = constraints,
      description = descriptionOpt,
    )
}

/**
 * Typed Field Builder that provides type-specific methods
 */
case class TypedFieldBuilder(fieldBuilder: FieldBuilder) {

  // Basic types
  def string: StringFieldBuilder =
    StringFieldBuilder(fieldBuilder.setFieldType(FieldType.StringType))

  def int: NumericFieldBuilder[Int] =
    NumericFieldBuilder[Int](fieldBuilder.setFieldType(FieldType.IntType))

  def long: NumericFieldBuilder[Long] =
    NumericFieldBuilder[Long](fieldBuilder.setFieldType(FieldType.LongType))

  def double: NumericFieldBuilder[Double] =
    NumericFieldBuilder[Double](fieldBuilder.setFieldType(FieldType.DoubleType))

  def boolean: FieldTerminator =
    FieldTerminator(fieldBuilder.setFieldType(FieldType.BooleanType))

  def timestamp: FieldTerminator =
    FieldTerminator(fieldBuilder.setFieldType(FieldType.TimestampType))

  def decimal(precision: Int, scale: Int): NumericFieldBuilder[BigDecimal] =
    NumericFieldBuilder[BigDecimal](fieldBuilder.setFieldType(FieldType.DecimalType(precision, scale)))

  def array(elementType: FieldType): FieldTerminator =
    FieldTerminator(fieldBuilder.setFieldType(FieldType.ArrayType(elementType)))
}

/**
 * String-specific field builder with string constraints
 */
case class StringFieldBuilder(fieldBuilder: FieldBuilder) extends FieldStep[StringFieldBuilder] {

  protected def withField(fb: FieldBuilder): StringFieldBuilder = copy(fieldBuilder = fb)

  def minLength(length: Int): StringFieldBuilder = constrain(FieldConstraint.MinLength(length))

  def maxLength(length: Int): StringFieldBuilder = constrain(FieldConstraint.MaxLength(length))

  def matches(regex: Regex): StringFieldBuilder = constrain(FieldConstraint.Pattern(regex))

  def matches(pattern: String): StringFieldBuilder = constrain(FieldConstraint.Pattern(pattern.r))

  def oneOf(values: String*): StringFieldBuilder = constrain(FieldConstraint.OneOf(values.toSet))

  def email: StringFieldBuilder = constrain(FieldConstraint.Pattern(ContractDSL.Patterns.EMAIL))

  def url: StringFieldBuilder = constrain(FieldConstraint.Pattern(ContractDSL.Patterns.URL))

  def uuid: StringFieldBuilder = constrain(FieldConstraint.Pattern(ContractDSL.Patterns.UUID))
}

/**
 * Numeric field builder with numeric constraints
 */
case class NumericFieldBuilder[T](fieldBuilder: FieldBuilder) extends FieldStep[NumericFieldBuilder[T]] {

  protected def withField(fb: FieldBuilder): NumericFieldBuilder[T] = copy(fieldBuilder = fb)

  def min(minValue: Double): NumericFieldBuilder[T] =
    constrain(FieldConstraint.Range(minValue, Double.MaxValue))

  def max(maxValue: Double): NumericFieldBuilder[T] =
    constrain(FieldConstraint.Range(Double.MinValue, maxValue))

  def range(minValue: Double, maxValue: Double): NumericFieldBuilder[T] =
    constrain(FieldConstraint.Range(minValue, maxValue))

  def positive: NumericFieldBuilder[T] = constrain(FieldConstraint.Range(0.0, Double.MaxValue))

  def negative: NumericFieldBuilder[T] = constrain(FieldConstraint.Range(Double.MinValue, 0.0))
}

/**
 * What every field-level step can do: add a constraint of its own kind, describe itself, or leave the field
 * and go back to the contract. `Self` is the concrete step type so a constraint method keeps the
 * type-specific methods available.
 */
trait FieldStep[Self] {
  def fieldBuilder: FieldBuilder

  protected def withField(fb: FieldBuilder): Self

  protected def constrain(constraint: FieldConstraint): Self =
    withField(fieldBuilder.addConstraint(constraint))

  def describedAs(description: String): Self =
    withField(fieldBuilder.setDescription(description))

  def field(name: String): FieldBuilder = fieldBuilder.commit.field(name)

  def withSLA(sla: String): ContractBuilder = fieldBuilder.commit.withSLA(sla)

  def withOwner(owner: String): ContractBuilder = fieldBuilder.commit.withOwner(owner)

  def withVersion(
    major: Int,
    minor: Int,
    patch: Int,
  ): ContractBuilder = fieldBuilder.commit.withVersion(major, minor, patch)

  def build: ContractSchema = fieldBuilder.commit.build
}

/**
 * Field step for types that carry no constraints of their own.
 */
case class FieldTerminator(fieldBuilder: FieldBuilder) extends FieldStep[FieldTerminator] {
  protected def withField(fb: FieldBuilder): FieldTerminator = copy(fieldBuilder = fb)
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
    def requiredString: StringFieldBuilder = ContractBuilder(name).field(name).required.string

    def optionalString: StringFieldBuilder = ContractBuilder(name).field(name).optional.string
  }
}
