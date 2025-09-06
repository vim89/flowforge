/**
 * FlowForge Core Module - Data Modeling Types
 *
 * File: modules/core/src/main/scala/com/flowforge/core/types/DataTypes.scala Package:
 * com.flowforge.core.types
 *
 * This file defines the core data modeling types used throughout the FlowForge ecosystem. These types
 * represent the fundamental building blocks for data engineering pipelines, including data formats, sources,
 * sinks, and schemas.
 *
 * Design Patterns Applied:
 *   - ADT Pattern: Sealed trait hierarchies for type-safe data modeling
 *   - Value Object Pattern: Immutable data containers with business meaning
 *   - Phantom Types: Compile-time type safety without runtime cost
 *   - Type Class Pattern: Polymorphic operations over data types
 *   - Builder Pattern: Fluent API for complex data structure construction
 *
 * Scala Features Showcased:
 *   - Refined Types: Compile-time validation for data constraints
 *   - Case Classes: Immutable data modeling with structural equality
 *   - Sealed Traits: Exhaustive pattern matching guarantees
 *   - Type Aliases: Domain-specific names for complex types
 *   - Companion Objects: Factory methods and implicit instances
 *   - Generic Programming: Parameterized data structures
 *   - Self Types: Compositional constraints and mixins
 *
 * Innovation Highlights:
 *   - Type-safe data format representation with compile-time validation
 *   - Schema evolution support with versioning and compatibility
 *   - Data quality constraints embedded in type system
 *   - Performance-optimized data structures for big data processing
 *   - Integration with popular data formats (Parquet, Avro, JSON, etc.)
 *   - Cloud storage abstraction with provider-agnostic operations
 *
 * Usage Examples:
 * ```scala
 * // Type-safe data source definition
 * val source = DataSource.gcs(
 *   bucket = BucketName("my-data-lake"),
 *   prefix = "events/2024/01/",
 *   format = DataFormat.Parquet,
 * )
 *
 * // Schema-validated data processing
 * val schema = DataSchema.builder
 *   .addField("user_id", DataType.String, required = true)
 *   .addField("timestamp", DataType.Timestamp, required = true)
 *   .addField("event_name", DataType.String, required = true)
 *   .build
 *
 * // Type-safe pipeline configuration
 * val config = PipelineConfig.builder
 *   .withSource(source)
 *   .withSchema(schema)
 *   .withQualityRules(QualityRules.standard)
 *   .build
 * ```
 *
 * @author
 *   FlowForge Team
 * @version 1.0.0
 * @since 2024
 */
package com.flowforge.core.types

import cats.Show
import cats.syntax.show._
import com.flowforge.core.contracts.derive.Shape
import com.flowforge.core.types.DataSink.WriteMode
import eu.timepit.refined.api.Refined
import eu.timepit.refined.collection.NonEmpty
import eu.timepit.refined.numeric.{ NonNegative, Positive }
import eu.timepit.refined.string.MatchesRegex
import shapeless.HList

import java.time.Instant

// ===============================
// REFINED TYPE ALIASES
// ===============================

/**
 * Type-safe string types using refined types. These provide compile-time validation for common data
 * engineering patterns.
 */
object RefinedTypes {

  import eu.timepit.refined.W

  // String pattern types
  type NonEmptyString = String Refined NonEmpty
  type BucketNameType = String Refined MatchesRegex[W.`"^[a-z0-9][a-z0-9-]*[a-z0-9]$"`.T]
  type TableNameType  = String Refined MatchesRegex[W.`"^[a-zA-Z_][a-zA-Z0-9_]*$"`.T]
  type FieldNameType  = String Refined MatchesRegex[W.`"^[a-zA-Z_][a-zA-Z0-9_]*$"`.T]
  type ProjectIdType  = String Refined MatchesRegex[W.`"^[a-z][a-z0-9-]*[a-z0-9]$"`.T]

  // Numeric types
  type PositiveInt    = Int Refined Positive
  type NonNegativeInt = Int Refined NonNegative
  type PortNumber     = Int Refined eu.timepit.refined.numeric.Interval.Closed[W.`1`.T, W.`65535`.T]

  // Simplified case classes - removing AnyVal extension due to refined type conflicts
  case class BucketName(value: String) {
    // Runtime validation instead of compile-time for dynamic values
    require(value.matches("^[a-z0-9][a-z0-9-]*[a-z0-9]$"), s"Invalid bucket name: $value")
  }

  object BucketName {
    def unsafeFrom(value: String): BucketName = BucketName(value)
  }

  case class TableName(value: String) {
    require(value.matches("^[a-zA-Z_][a-zA-Z0-9_]*$"), s"Invalid table name: $value")
  }

  object TableName {
    def unsafeFrom(value: String): TableName = TableName(value)
  }

  case class FieldName(value: String) {
    require(value.matches("^[a-zA-Z_][a-zA-Z0-9_]*$"), s"Invalid field name: $value")
  }

  object FieldName {
    def unsafeFrom(value: String): FieldName = FieldName(value)
  }

  case class ProjectId(value: String) {
    require(value.matches("^[a-z][a-z0-9-]*[a-z0-9]$"), s"Invalid project ID: $value")
  }

  object ProjectId {
    def unsafeFrom(value: String): ProjectId = ProjectId(value)
  }

  case class DatasetId(value: String) {
    require(value.nonEmpty, "Dataset ID cannot be empty")
  }

  object DatasetId {
    def unsafeFrom(value: String): DatasetId = DatasetId(value)
  }

  case class SchemaVersion(value: Int) {
    require(value > 0, s"Schema version must be positive: $value")
  }

  object SchemaVersion {
    def unsafeFrom(value: Int): SchemaVersion = SchemaVersion(value)
  }

  // Show instances for simple case classes
  implicit val showBucketName: Show[BucketName]       = Show.show(_.value)
  implicit val showTableName: Show[TableName]         = Show.show(_.value)
  implicit val showFieldName: Show[FieldName]         = Show.show(_.value)
  implicit val showProjectId: Show[ProjectId]         = Show.show(_.value)
  implicit val showDatasetId: Show[DatasetId]         = Show.show(_.value)
  implicit val showSchemaVersion: Show[SchemaVersion] = Show.show(_.value.toString)
}

import com.flowforge.core.types.RefinedTypes._

// ===============================
// DATA FORMATS
// ===============================

/**
 * Supported data formats for reading and writing. Each format has specific capabilities and performance
 * characteristics.
 */
sealed trait DataFormat extends Product with Serializable {
  def fileExtension: String

  def mimeType: String

  def isColumnOriented: Boolean

  def supportsSchemaEvolution: Boolean

  def compressionSupport: List[CompressionType]
}

object DataFormat {

  /**
   * Apache Parquet - columnar storage format. Optimal for analytics workloads with excellent compression.
   */
  case object Parquet extends DataFormat {
    val fileExtension           = ".parquet"
    val mimeType                = "application/octet-stream"
    val isColumnOriented        = true
    val supportsSchemaEvolution = true
    val compressionSupport      = List(CompressionType.Snappy, CompressionType.Gzip, CompressionType.LZ4)
  }

  /**
   * Apache Avro - row-oriented format with rich schema support. Excellent for streaming and schema evolution.
   */
  case object Avro extends DataFormat {
    val fileExtension           = ".avro"
    val mimeType                = "application/avro"
    val isColumnOriented        = false
    val supportsSchemaEvolution = true
    val compressionSupport =
      List(CompressionType.Snappy, CompressionType.Deflate, CompressionType.Bzip2)
  }

  /**
   * Comma-separated values - simple text format. Universal compatibility but limited schema support.
   */
  case object CSV extends DataFormat {
    val fileExtension           = ".csv"
    val mimeType                = "text/csv"
    val isColumnOriented        = false
    val supportsSchemaEvolution = false
    val compressionSupport      = List(CompressionType.Gzip, CompressionType.Bzip2)
  }

  /**
   * JSON - human-readable text format. Flexible schema but larger file sizes.
   */
  case object JSON extends DataFormat {
    val fileExtension           = ".json"
    val mimeType                = "application/json"
    val isColumnOriented        = false
    val supportsSchemaEvolution = true
    val compressionSupport      = List(CompressionType.Gzip, CompressionType.Bzip2)
  }

  /**
   * JSON Lines - newline-delimited JSON. Streamable JSON format for large datasets.
   */
  case object JSONL extends DataFormat {
    val fileExtension           = ".jsonl"
    val mimeType                = "application/x-ndjson"
    val isColumnOriented        = false
    val supportsSchemaEvolution = true
    val compressionSupport      = List(CompressionType.Gzip, CompressionType.Bzip2)
  }

  /**
   * Apache ORC - optimized row columnar format. Hive-native format with excellent compression.
   */
  case object ORC extends DataFormat {
    val fileExtension           = ".orc"
    val mimeType                = "application/octet-stream"
    val isColumnOriented        = true
    val supportsSchemaEvolution = true
    val compressionSupport      = List(CompressionType.Zlib, CompressionType.Snappy, CompressionType.LZ4)
  }

  /**
   * Delta Lake format - ACID transactions on data lakes. Combines benefits of data lakes and data warehouses.
   */
  case object Delta extends DataFormat {
    val fileExtension           = ".delta"
    val mimeType                = "application/octet-stream"
    val isColumnOriented        = true
    val supportsSchemaEvolution = true
    val compressionSupport      = List(CompressionType.Snappy, CompressionType.Gzip, CompressionType.LZ4)
  }

  implicit val showDataFormat: Show[DataFormat] = Show.show {
    case Parquet => "PARQUET"
    case Avro    => "AVRO"
    case CSV     => "CSV"
    case JSON    => "JSON"
    case JSONL   => "JSONL"
    case ORC     => "ORC"
    case Delta   => "DELTA"
  }
}

/**
 * Compression algorithms supported by different data formats.
 */
sealed trait CompressionType extends Product with Serializable

object CompressionType {
  case object None extends CompressionType

  case object Gzip extends CompressionType

  case object Snappy extends CompressionType

  case object LZ4 extends CompressionType

  case object Bzip2 extends CompressionType

  case object Deflate extends CompressionType

  case object Zlib extends CompressionType

  case object Zstd extends CompressionType

  implicit val showCompressionType: Show[CompressionType] = Show.show {
    case None    => "NONE"
    case Gzip    => "GZIP"
    case Snappy  => "SNAPPY"
    case LZ4     => "LZ4"
    case Bzip2   => "BZIP2"
    case Deflate => "DEFLATE"
    case Zlib    => "ZLIB"
    case Zstd    => "ZSTD"
  }
}

// ===============================
// DATA TYPES & SCHEMA
// ===============================

/**
 * Data type system for schema definition. Supports common data types with null safety and precision
 * specification.
 */
sealed trait DataType extends Product with Serializable {
  def isNullable: Boolean

  def sqlType: String
}

object DataType {

  // Primitive types
  case object Boolean extends DataType {
    val isNullable = false
    val sqlType    = "BOOLEAN"
  }

  case object Byte extends DataType {
    val isNullable = false
    val sqlType    = "TINYINT"
  }

  case object Short extends DataType {
    val isNullable = false
    val sqlType    = "SMALLINT"
  }

  case object Integer extends DataType {
    val isNullable = false
    val sqlType    = "INTEGER"
  }

  case object Long extends DataType {
    val isNullable = false
    val sqlType    = "BIGINT"
  }

  case object Float extends DataType {
    val isNullable = false
    val sqlType    = "FLOAT"
  }

  case object Double extends DataType {
    val isNullable = false
    val sqlType    = "DOUBLE"
  }

  case class Decimal(precision: PositiveInt, scale: NonNegativeInt) extends DataType {
    val isNullable = false
    val sqlType    = s"DECIMAL($precision,$scale)"
  }

  case object String extends DataType {
    val isNullable = false
    val sqlType    = "STRING"
  }

  case class VarChar(maxLength: PositiveInt) extends DataType {
    val isNullable = false
    val sqlType    = s"VARCHAR($maxLength)"
  }

  case object Binary extends DataType {
    val isNullable = false
    val sqlType    = "BINARY"
  }

  // Temporal types
  case object Date extends DataType {
    val isNullable = false
    val sqlType    = "DATE"
  }

  case object Time extends DataType {
    val isNullable = false
    val sqlType    = "TIME"
  }

  case object Timestamp extends DataType {
    val isNullable = false
    val sqlType    = "TIMESTAMP"
  }

  case object TimestampNtz extends DataType {
    val isNullable = false
    val sqlType    = "TIMESTAMP_NTZ"
  }

  // Complex types
  case class Array(elementType: DataType) extends DataType {
    val isNullable = false
    val sqlType    = s"ARRAY<${elementType.sqlType}>"
  }

  case class Map(keyType: DataType, valueType: DataType) extends DataType {
    val isNullable = false
    val sqlType    = s"MAP<${keyType.sqlType},${valueType.sqlType}>"
  }

  case class Struct(fields: List[StructField]) extends DataType {
    val isNullable = false
    val sqlType = {
      val fieldTypes = fields.map(f => s"${f.name.value}:${f.dataType.sqlType}").mkString(",")
      s"STRUCT<$fieldTypes>"
    }
  }

  // Nullable wrapper
  case class Nullable(innerType: DataType) extends DataType {
    val isNullable = true
    val sqlType    = innerType.sqlType
  }

  // Convenience constructors
  def decimal(precision: Int, scale: Int): Decimal = {
    val refinedPrecision = eu.timepit.refined.refineV[Positive](precision) match {
      case Right(value) => value
      case Left(_)      => throw new IllegalArgumentException(s"Precision must be positive: $precision")
    }
    val refinedScale = eu.timepit.refined.refineV[NonNegative](scale) match {
      case Right(value) => value
      case Left(_)      => throw new IllegalArgumentException(s"Scale must be non-negative: $scale")
    }
    Decimal(refinedPrecision, refinedScale)
  }

  def varchar(maxLength: Int): VarChar = {
    val refined = eu.timepit.refined.refineV[Positive](maxLength) match {
      case Right(value) => value
      case Left(_)      => throw new IllegalArgumentException(s"Max length must be positive: $maxLength")
    }
    VarChar(refined)
  }

  def nullable(dataType: DataType): DataType =
    if (dataType.isNullable) dataType else Nullable(dataType)

  implicit val showDataType: Show[DataType] = Show.show(_.sqlType)
}

/**
 * Structure field definition for complex types.
 */
case class StructField(
  name: FieldName,
  dataType: DataType,
  nullable: Boolean = false,
  metadata: Map[String, String] = Map.empty) {
  def isRequired: Boolean = !nullable

  def withMetadata(key: String, value: String): StructField =
    copy(metadata = metadata + (key -> value))
}

object StructField {
  def apply(name: String, dataType: DataType): StructField =
    new StructField(FieldName(name), dataType)

  def required(name: String, dataType: DataType): StructField =
    new StructField(FieldName(name), dataType, nullable = false)

  def optional(name: String, dataType: DataType): StructField =
    new StructField(
      FieldName(name),
      DataType.nullable(dataType),
      nullable = true,
    )
}

/**
 * Schema definition for structured data.
 */
case class DataSchema(
  fields: List[StructField],
  version: SchemaVersion,
  metadata: Map[String, String] = Map.empty,
  createdAt: Instant = Instant.now()) {

  def fieldNames: List[String] = fields.map(_.name.value)

  def fieldByName(name: String): Option[StructField] =
    fields.find(_.name.value == name)

  def requiredFields: List[StructField] = fields.filter(_.isRequired)

  def optionalFields: List[StructField] = fields.filter(!_.isRequired)

  def withMetadata(key: String, value: String): DataSchema =
    copy(metadata = metadata + (key -> value))

  def evolve(newFields: List[StructField]): DataSchema =
    copy(
      fields = fields ++ newFields,
      version = SchemaVersion(version.value + 1),
      createdAt = Instant.now(),
    )
}

object DataSchema {

  /**
   * Fluent builder for schema construction.
   */
  case class SchemaBuilder private (
    fields: List[StructField] = List.empty,
    metadata: Map[String, String] = Map.empty) {

    def addField(
      name: String,
      dataType: DataType,
      required: Boolean = true,
    ): SchemaBuilder = {
      val field = if (required) {
        StructField.required(name, dataType)
      } else {
        StructField.optional(name, dataType)
      }
      copy(fields = fields :+ field)
    }

    def addField(field: StructField): SchemaBuilder =
      copy(fields = fields :+ field)

    def withMetadata(key: String, value: String): SchemaBuilder =
      copy(metadata = metadata + (key -> value))

    def build: DataSchema =
      DataSchema(
        fields = fields,
        version = SchemaVersion(1),
        metadata = metadata,
      )
  }

  def builder: SchemaBuilder = SchemaBuilder()

  // Common schema patterns
  def eventSchema: DataSchema = builder
    .addField("event_id", DataType.String, required = true)
    .addField("timestamp", DataType.Timestamp, required = true)
    .addField("event_type", DataType.String, required = true)
    .addField("user_id", DataType.String, required = false)
    .addField("session_id", DataType.String, required = false)
    .addField("properties", DataType.Map(DataType.String, DataType.String), required = false)
    .withMetadata("pattern", "event_log")
    .build

  def userSchema: DataSchema = builder
    .addField("user_id", DataType.String, required = true)
    .addField("email", DataType.String, required = true)
    .addField("name", DataType.String, required = true)
    .addField("created_at", DataType.Timestamp, required = true)
    .addField("last_login", DataType.Timestamp, required = false)
    .addField("is_active", DataType.Boolean, required = true)
    .withMetadata("pattern", "user_profile")
    .build

  implicit val showDataSchema: Show[DataSchema] = Show.show { schema =>
    val fieldList = schema.fields.map { field =>
      s"  ${field.name.show}: ${field.dataType.show}${if (field.nullable) "?" else ""}"
    }.mkString("\n")

    s"""DataSchema(v${schema.version.show}) {
       |$fieldList
       |}""".stripMargin
  }
}

// ===============================
// DATA SOURCES & SINKS
// ===============================

/**
 * Abstract data source configuration. Represents where data comes from in a pipeline.
 */
sealed trait DataSource extends Product with Serializable {
  def format: DataFormat

  def compression: CompressionType

  def schema: Option[DataSchema]
}

/**
 * Local file system data source (extension for sealed trait)
 */
case class LocalDataSource(
  location: String,
  format: DataFormat,
  compression: CompressionType = CompressionType.None,
  schema: Option[DataSchema] = None,
  id: Option[String] = None)
    extends DataSource

object DataSource {

  /**
   * Google Cloud Storage data source.
   */
  case class GcsSource(
    bucket: BucketName,
    prefix: String,
    format: DataFormat,
    compression: CompressionType = CompressionType.None,
    schema: Option[DataSchema] = None,
    recursive: Boolean = false)
      extends DataSource {

    def path: String = s"gs://${bucket.show}/$prefix"

    def withSchema(dataSchema: DataSchema): GcsSource =
      copy(schema = Some(dataSchema))

    def withCompression(comp: CompressionType): GcsSource =
      copy(compression = comp)
  }

  /**
   * Amazon S3 data source.
   */
  case class S3Source(
    bucket: BucketName,
    prefix: String,
    format: DataFormat,
    compression: CompressionType = CompressionType.None,
    schema: Option[DataSchema] = None,
    region: String = "us-east-1",
    recursive: Boolean = false)
      extends DataSource {

    def path: String = s"s3://${bucket.show}/$prefix"

    def withSchema(dataSchema: DataSchema): S3Source =
      copy(schema = Some(dataSchema))

    def withCompression(comp: CompressionType): S3Source =
      copy(compression = comp)
  }

  /**
   * Google BigQuery data source.
   */
  case class BigQuerySource(
    project: ProjectId,
    dataset: DatasetId,
    table: TableName,
    format: DataFormat = DataFormat.Parquet,
    compression: CompressionType = CompressionType.None,
    schema: Option[DataSchema] = None,
    filter: Option[String] = None)
      extends DataSource {

    def fullTableName: String = s"${project.show}.${dataset.show}.${table.show}"

    def withSchema(dataSchema: DataSchema): BigQuerySource =
      copy(schema = Some(dataSchema))

    def withFilter(sqlFilter: String): BigQuerySource =
      copy(filter = Some(sqlFilter))
  }

  /**
   * JDBC data source.
   */
  case class JdbcSource(
    url: String,
    table: TableName,
    format: DataFormat = DataFormat.Parquet,
    compression: CompressionType = CompressionType.None,
    schema: Option[DataSchema] = None,
    driver: String,
    user: Option[String] = None,
    password: Option[String] = None,
    query: Option[String] = None)
      extends DataSource {

    def withSchema(dataSchema: DataSchema): JdbcSource =
      copy(schema = Some(dataSchema))

    def withQuery(sql: String): JdbcSource =
      copy(query = Some(sql))
  }

  // Convenience factory methods
  def gcs(
    bucket: String,
    prefix: String,
    format: DataFormat,
  ): GcsSource =
    GcsSource(BucketName(bucket), prefix, format)

  def s3(
    bucket: String,
    prefix: String,
    format: DataFormat,
  ): S3Source =
    S3Source(BucketName(bucket), prefix, format)

  def bigQuery(
    project: String,
    dataset: String,
    table: String,
  ): BigQuerySource =
    BigQuerySource(
      ProjectId(project),
      DatasetId(dataset),
      TableName(table),
    )

  def jdbc(
    url: String,
    table: String,
    driver: String,
  ): JdbcSource =
    JdbcSource(url, TableName(table), driver = driver)
}

/**
 * Abstract data sink configuration. Represents where data goes in a pipeline.
 */
sealed trait DataSink extends Product with Serializable {
  def format: DataFormat

  def compression: CompressionType

  def writeMode: WriteMode
}

/**
 * Local file system data sink (extension for sealed trait)
 */
case class LocalDataSink(
  location: String,
  format: DataFormat,
  compression: CompressionType = CompressionType.None,
  writeMode: DataSink.WriteMode = DataSink.WriteMode.Append,
  id: Option[String] = None)
    extends DataSink

object DataSink {

  /**
   * Write modes for data sinks.
   */
  sealed trait WriteMode extends Product with Serializable

  object WriteMode {
    case object Append extends WriteMode

    case object Overwrite extends WriteMode

    case object ErrorIfExists extends WriteMode

    case object Ignore extends WriteMode

    implicit val showWriteMode: Show[WriteMode] = Show.show {
      case Append        => "APPEND"
      case Overwrite     => "OVERWRITE"
      case ErrorIfExists => "ERROR_IF_EXISTS"
      case Ignore        => "IGNORE"
    }
  }

  /**
   * Google Cloud Storage data sink.
   */
  case class GcsSink(
    bucket: BucketName,
    prefix: String,
    format: DataFormat,
    compression: CompressionType = CompressionType.None,
    writeMode: WriteMode = WriteMode.Append,
    partitionBy: List[String] = List.empty)
      extends DataSink {

    def path: String = s"gs://${bucket.show}/$prefix"

    def withWriteMode(mode: WriteMode): GcsSink =
      copy(writeMode = mode)

    def withCompression(comp: CompressionType): GcsSink =
      copy(compression = comp)

    def partitionedBy(columns: String*): GcsSink =
      copy(partitionBy = columns.toList)
  }

  /**
   * Amazon S3 data sink.
   */
  case class S3Sink(
    bucket: BucketName,
    prefix: String,
    format: DataFormat,
    compression: CompressionType = CompressionType.None,
    writeMode: WriteMode = WriteMode.Append,
    region: String = "us-east-1",
    partitionBy: List[String] = List.empty)
      extends DataSink {

    def path: String = s"s3://${bucket.show}/$prefix"

    def withWriteMode(mode: WriteMode): S3Sink =
      copy(writeMode = mode)

    def withCompression(comp: CompressionType): S3Sink =
      copy(compression = comp)

    def partitionedBy(columns: String*): S3Sink =
      copy(partitionBy = columns.toList)
  }

  // Convenience factory methods
  def gcs(
    bucket: String,
    prefix: String,
    format: DataFormat,
  ): GcsSink =
    GcsSink(BucketName(bucket), prefix, format)

  def s3(
    bucket: String,
    prefix: String,
    format: DataFormat,
  ): S3Sink =
    S3Sink(BucketName(bucket), prefix, format)
}

// ===============================
// TYPED SINK (COMPILE-TIME SCHEMA)
// ===============================

/**
 * A sink that encodes its expected schema at the type level as an HList of labelled fields. Use with
 * PipelineBuilder.addTypedSink to enforce compile-time schema compatibility between pipeline output type and
 * sink expectation. Record-typed endpoints; schema carried by compile-time Shape evidence.
 */
final case class TypedSource[R](underlying: DataSource)(implicit val shape: Shape[R])

/** A source that encodes its expected schema at the type level. */
final case class TypedSink[R](underlying: DataSink)(implicit val shape: Shape[R])

// ===============================
// DATA QUALITY TYPES
// ===============================

/**
 * Data quality constraint definitions.
 */
sealed trait QualityConstraint extends Product with Serializable {
  def name: String

  def description: String

  def severity: QualitySeverity
}

object QualityConstraint {

  case class NotNull(field: FieldName, severity: QualitySeverity = QualitySeverity.Error)
      extends QualityConstraint {
    val name        = s"not_null_${field.show}"
    val description = s"Field ${field.show} must not be null"
  }

  case class Unique(field: FieldName, severity: QualitySeverity = QualitySeverity.Error)
      extends QualityConstraint {
    val name        = s"unique_${field.show}"
    val description = s"Field ${field.show} must be unique"
  }

  case class Range(
    field: FieldName,
    min: Option[Double],
    max: Option[Double],
    severity: QualitySeverity = QualitySeverity.Warning)
      extends QualityConstraint {
    val name = s"range_${field.show}"
    val description =
      (min, max) match {
        case (Some(minVal), Some(maxVal)) =>
          s"Field ${field.show} must be between $minVal and $maxVal"
        case (Some(minVal), None) => s"Field ${field.show} must be >= $minVal"
        case (None, Some(maxVal)) => s"Field ${field.show} must be <= $maxVal"
        case (None, None)         => s"Field ${field.show} range check (no bounds specified)"
      }
  }

  case class Pattern(
    field: FieldName,
    regex: String,
    severity: QualitySeverity = QualitySeverity.Warning)
      extends QualityConstraint {
    val name        = s"pattern_${field.show}"
    val description = s"Field ${field.show} must match pattern: $regex"
  }

  /**
   * Compliance constraint using a SQL-like boolean expression evaluated on the dataset. Example: predicateSql =
   * "amount > 0 AND id IS NOT NULL"
   */
  case class Compliance(
    ruleName: String,
    predicateSql: String,
    severity: QualitySeverity = QualitySeverity.Warning)
      extends QualityConstraint {
    val name        = s"compliance_${ruleName}"
    val description = s"Rows must satisfy: $predicateSql"
  }
}

/**
 * Quality constraint severity levels.
 */
sealed trait QualitySeverity extends Product with Serializable {
  def level: Int

  def shouldBlock: Boolean
}

object QualitySeverity {
  case object Info extends QualitySeverity {
    val level       = 1
    val shouldBlock = false
  }

  case object Warning extends QualitySeverity {
    val level       = 2
    val shouldBlock = false
  }

  case object Error extends QualitySeverity {
    val level       = 3
    val shouldBlock = true
  }

  case object Critical extends QualitySeverity {
    val level       = 4
    val shouldBlock = true
  }

  implicit val showQualitySeverity: Show[QualitySeverity] = Show.show {
    case Info     => "INFO"
    case Warning  => "WARNING"
    case Error    => "ERROR"
    case Critical => "CRITICAL"
  }
}

/**
 * Collection of quality constraints for a dataset.
 */
case class QualityRules(
  constraints: List[QualityConstraint],
  name: String = "default",
  version: SchemaVersion = SchemaVersion(1)) {

  def errorConstraints: List[QualityConstraint] =
    constraints.filter(_.severity.shouldBlock)

  def warningConstraints: List[QualityConstraint] =
    constraints.filter(!_.severity.shouldBlock)

  def addConstraint(constraint: QualityConstraint): QualityRules =
    copy(constraints = constraints :+ constraint)
}

object QualityRules {

  def empty: QualityRules = QualityRules(List.empty)

  def standard: QualityRules = QualityRules(
    List(
      QualityConstraint.NotNull(FieldName("id")),
      QualityConstraint.NotNull(FieldName("timestamp")),
      QualityConstraint.Range(FieldName("amount"), Some(0), None),
    ),
    name = "standard_rules",
  )

  def strict: QualityRules = QualityRules(
    List(
      QualityConstraint.NotNull(FieldName("id")),
      QualityConstraint.Unique(FieldName("id")),
      QualityConstraint.NotNull(FieldName("timestamp")),
      QualityConstraint.Range(FieldName("amount"), Some(0), Some(1000000)),
      QualityConstraint.Pattern(
        FieldName("email"),
        "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$",
      ),
    ),
    name = "strict_rules",
  )

  implicit val showQualityRules: Show[QualityRules] = Show.show { rules =>
    s"QualityRules(${rules.name}, ${rules.constraints.length} constraints)"
  }
}
