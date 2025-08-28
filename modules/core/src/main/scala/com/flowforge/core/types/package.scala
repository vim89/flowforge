package com.flowforge.core

/**
 * FlowForge Core Module - Type System & Domain Types
 *
 * File: modules/core/src/main/scala/com/flowforge/core/Types.scala Package: com.flowforge.core
 *
 * This file defines the core type system for FlowForge, showcasing advanced Scala type-level
 * programming and providing a foundation for type-safe data engineering.
 *
 * Design Patterns Applied:
 *   - Value Object Pattern: Refined domain types
 *   - Type State Pattern: Phantom types for state safety
 *   - Newtype Pattern: Zero-cost wrapper types
 *   - Sealed Trait Pattern: ADTs for configuration
 *   - F-Bounded Polymorphism: Self-referential type bounds
 *
 * Scala Features Showcased:
 *   - Phantom Types: Compile-time state tracking
 *   - Refined Types: Custom validation without external libraries
 *   - ADTs: Algebraic Data Types for configuration
 *   - Variance: Covariant/contravariant relationships
 *   - Type Bounds: Upper and lower bounds
 *   - Path-Dependent Types: Context-sensitive types
 *   - Type Aliases: Domain-specific names
 *   - Case Classes: Product types with pattern matching
 *   - Sealed Traits: Sum types for exhaustive matching
 *   - Self Types: Dependency specification
 *   - Structural Types: Duck typing
 *
 * Innovation Highlights:
 *   - Phantom Types prevent invalid operations at compile time
 *   - Custom validation without external library dependencies
 *   - Type-safe builder pattern with state progression
 *   - Zero-cost abstractions using value classes
 *   - Compile-time configuration validation
 *
 * @author
 *   FlowForge Team
 * @version 1.0.0
 * @since 2024
 */

import cats.data.ValidatedNel

import scala.language.{higherKinds, implicitConversions}
import scala.util.{Failure, Success, Try}
import java.time.{Duration, Instant, LocalDate}
import java.util.UUID

/**
 * Package object containing type aliases and utility types for the core module.
 *
 * This centralizes common type aliases and provides a convenient import point for all core types
 * used throughout FlowForge.
 */
package object types {

  // ===============================
  // BASIC TYPE ALIASES
  // ===============================

  /**
   * Universal ID type for all entities in FlowForge. Using UUID provides uniqueness across
   * distributed systems.
   */
  type EntityId = UUID

  /**
   * Timestamp type for all temporal operations. Using Instant provides nanosecond precision and
   * timezone handling.
   */
  type Timestamp = Instant

  /**
   * Binary data representation. Used for serialization and data transfer.
   */
  type BinaryData = Array[Byte]

  /**
   * Key-value metadata type. Used for extensible metadata throughout the system.
   */
  type Metadata = Map[String, String]

  /**
   * Generic result type for operations that can fail. Provides a more descriptive alternative to
   * Either for domain operations.
   */
  type Result[+A] = Either[FlowForgeError, A]

  // ===== VALIDATION TYPES (Fix the validation result type) =====
  type ValidationError = String  // Simple string for now
  type ValidationResult[A] = ValidatedNel[String, A]  // Fixed: use String, not ValidationError


  // ===============================
  // PHANTOM TYPES FOR STATE SAFETY
  // ===============================

  /**
   * Phantom type system for tracking pipeline state at compile time.
   *
   * This innovative approach prevents invalid operations by encoding the current state of a
   * pipeline in the type system itself.
   *
   * Example:
   * ```scala
   * // This won't compile - can't execute unvalidated pipeline
   * val pipeline: Pipeline[Initialized, IO, Data] = ???
   * pipeline.execute() // Compile error!
   *
   * // This compiles - pipeline is properly validated
   * val validatedPipeline: Pipeline[Validated, IO, Data] = pipeline.validate()
   * validatedPipeline.execute() // OK!
   * ```
   */
  sealed trait PipelineState
  trait Initialized extends PipelineState
  trait Configured  extends PipelineState
  trait Validated   extends PipelineState
  trait Ready       extends PipelineState
  trait Running     extends PipelineState
  trait Completed   extends PipelineState
  trait Failed      extends PipelineState

  /**
   * Phantom type system for data quality states. Prevents processing of data that hasn't passed
   * quality checks.
   */
  sealed trait QualityState
  trait Unchecked        extends QualityState
  trait QualityValidated extends QualityState
  trait QualityFailed    extends QualityState

  /**
   * Phantom type system for resource states. Ensures proper resource lifecycle management.
   */
  sealed trait ResourceState
  trait Unacquired extends ResourceState
  trait Acquired   extends ResourceState
  trait Released   extends ResourceState

  // ===============================
  // REFINED TYPES (Custom Validation)
  // ===============================

  /**
   * Base trait for refined types with custom validation.
   *
   * This provides zero-cost refinement without external dependencies, using Scala's type system to
   * enforce constraints at compile time.
   */
  sealed abstract case class Refined[A, P](value: A)

  /**
   * Predicate types for refinement. These encode validation rules in the type system.
   */
  sealed trait Predicate[A]
  trait NonEmpty[A]    extends Predicate[A]
  trait Positive[A]    extends Predicate[A]
  trait NonNegative[A] extends Predicate[A]
  trait ValidUrl       extends Predicate[String]
  trait ValidEmail     extends Predicate[String]
  trait ValidPath      extends Predicate[String]
  trait ValidS3Bucket  extends Predicate[String]
  trait ValidGcsPath   extends Predicate[String]
  trait ValidTableName extends Predicate[String]

  /**
   * Smart constructors for refined types. These provide safe construction with validation.
   */
  object Refined {

    /**
     * Create a non-empty string. Returns None if the string is empty or whitespace-only.
     */
    def nonEmptyString(s: String): Option[Refined[String, NonEmpty[String]]] =
      if (s.trim.nonEmpty) Some(new Refined(s) {}) else None

    /**
     * Create a positive integer. Returns None if the integer is not positive.
     */
    def positiveInt(i: Int): Option[Refined[Int, Positive[Int]]] =
      if (i > 0) Some(new Refined(i) {}) else None

    /**
     * Create a non-negative integer. Returns None if the integer is negative.
     */
    def nonNegativeInt(i: Int): Option[Refined[Int, NonNegative[Int]]] =
      if (i >= 0) Some(new Refined(i) {}) else None

    /**
     * Create a valid URL string. Returns None if the string is not a valid URL format.
     */
    def validUrl(s: String): Option[Refined[String, ValidUrl]] = {
      val urlPattern = """^https?://[^\s/$.?#].[^\s]*$""".r
      if (urlPattern.matches(s)) Some(new Refined(s) {}) else None
    }

    /**
     * Create a valid email string. Returns None if the string is not a valid email format.
     */
    def validEmail(s: String): Option[Refined[String, ValidEmail]] = {
      val emailPattern = """^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$""".r
      if (emailPattern.matches(s)) Some(new Refined(s) {}) else None
    }

    /**
     * Create a valid file system path. Returns None if the string is not a valid path format.
     */
    def validPath(s: String): Option[Refined[String, ValidPath]] =
      if (s.trim.nonEmpty && !s.contains("..") && !s.contains("//"))
        Some(new Refined(s) {})
      else None

    /**
     * Create a valid S3 bucket name. Returns None if the string doesn't follow S3 bucket naming
     * rules.
     */
    def validS3Bucket(s: String): Option[Refined[String, ValidS3Bucket]] = {
      val bucketPattern = """^[a-z0-9][a-z0-9.-]*[a-z0-9]$""".r
      if (
        s.length >= 3 && s.length <= 63 &&
        bucketPattern.matches(s) &&
        !s.contains("..") &&
        !s.startsWith("xn--") &&
        !s.endsWith("-s3alias")
      ) {
        Some(new Refined(s) {})
      } else None
    }

    /**
     * Create a valid GCS path. Returns None if the string doesn't follow GCS path conventions.
     */
    def validGcsPath(s: String): Option[Refined[String, ValidGcsPath]] = {
      val gcsPattern = """^gs://[a-z0-9][a-z0-9._-]*[a-z0-9]/.*$""".r
      if (gcsPattern.matches(s)) Some(new Refined(s) {}) else None
    }

    /**
     * Create a valid table name. Returns None if the string doesn't follow table naming
     * conventions.
     */
    def validTableName(s: String): Option[Refined[String, ValidTableName]] = {
      val tablePattern = """^[a-zA-Z][a-zA-Z0-9_]*$""".r
      if (tablePattern.matches(s) && s.length <= 128)
        Some(new Refined(s) {})
      else None
    }
  }

  // ===============================
  // VALUE CLASSES (Zero-Cost Abstractions)
  // ===============================

  /**
   * Value classes provide zero-cost type safety. At runtime, these are the same as their underlying
   * types, but at compile time they provide type safety and prevent mixing.
   */

  /**
   * Pipeline identifier with type safety. Prevents mixing up different types of IDs.
   */
  final case class PipelineId(value: UUID) extends AnyVal {
    override def toString: String = s"PipelineId($value)"
  }

  object PipelineId {
    def generate(): PipelineId = PipelineId(UUID.randomUUID())
    def fromString(s: String): Option[PipelineId] =
      Try(UUID.fromString(s)).toOption.map(PipelineId(_))
  }

  /**
   * Workflow identifier with type safety.
   */
  final case class WorkflowId(value: UUID) extends AnyVal {
    override def toString: String = s"WorkflowId($value)"
  }

  object WorkflowId {
    def generate(): WorkflowId = WorkflowId(UUID.randomUUID())
    def fromString(s: String): Option[WorkflowId] =
      Try(UUID.fromString(s)).toOption.map(WorkflowId(_))
  }

  /**
   * Data source identifier with type safety.
   */
  final case class DataSourceId(value: UUID) extends AnyVal {
    override def toString: String = s"DataSourceId($value)"
  }

  object DataSourceId {
    def generate(): DataSourceId = DataSourceId(UUID.randomUUID())
    def fromString(s: String): Option[DataSourceId] =
      Try(UUID.fromString(s)).toOption.map(DataSourceId(_))
  }

  /**
   * Batch size with validation. Ensures batch sizes are always positive.
   */
  final case class BatchSize(value: Int) extends AnyVal {
    require(value > 0, s"Batch size must be positive, got: $value")
    override def toString: String = s"BatchSize($value)"
  }

  object BatchSize {
    def apply(value: Int): Option[BatchSize] =
      if (value > 0) Some(new BatchSize(value)) else None

    val default: BatchSize = new BatchSize(1000)
    val small: BatchSize   = new BatchSize(100)
    val medium: BatchSize  = new BatchSize(1000)
    val large: BatchSize   = new BatchSize(10000)
  }

  /**
   * Record count with validation. Ensures record counts are always non-negative.
   */
  final case class RecordCount(value: Long) extends AnyVal {
    require(value >= 0, s"Record count must be non-negative, got: $value")
    override def toString: String = s"RecordCount($value)"

    def +(other: RecordCount): RecordCount = new RecordCount(value + other.value)
    def -(other: RecordCount): RecordCount = new RecordCount(math.max(0, value - other.value))
  }

  object RecordCount {
    def apply(value: Long): Option[RecordCount] =
      if (value >= 0) Some(new RecordCount(value)) else None

    val zero: RecordCount            = new RecordCount(0)
    def fromInt(i: Int): RecordCount = new RecordCount(i.toLong)
  }

  // ===============================
  // CONFIGURATION ADTS
  // ===============================

  /**
   * Effect system choice for project generation. This is a fundamental architectural decision that
   * affects the entire structure of generated projects.
   */
  sealed trait EffectSystemChoice extends Product with Serializable {
    def name: String
    def dependencies: List[String]
  }

  object EffectSystemChoice {
    case object ZIO extends EffectSystemChoice {
      val name = "zio"
      val dependencies: List[String] =
        """
          |        "dev.zio" %% "zio" % "2.0.19",
          |        "dev.zio" %% "zio-streams" % "2.0.19",
          |        "dev.zio" %% "zio-interop-cats" % "23.0.03"
          |""".stripMargin.split(",").toList
    }

    case object CatsEffect extends EffectSystemChoice {
      val name = "cats-effect"
      val dependencies: List[String] =
        """
          |        "org.typelevel" %% "cats-effect" % "3.5.2",
          |        "org.typelevel" %% "cats-effect-kernel" % "3.5.2",
          |        "org.typelevel" %% "cats-effect-std" % "3.5.2"
          |""".stripMargin.split(",").toList
    }

    /**
     * Parse effect system choice from string. Used in template generation and configuration.
     */
    def fromString(s: String): Option[EffectSystemChoice] = s.toLowerCase match {
      case "zio"                         => Some(ZIO)
      case "cats-effect" | "cats" | "ce" => Some(CatsEffect)
      case _                             => None
    }

    val all: List[EffectSystemChoice] = List(ZIO, CatsEffect)
  }

  /**
   * Data refresh strategies for pipelines. Each strategy has different implications for data
   * processing.
   */
  sealed trait RefreshType extends Product with Serializable {
    def name: String
    def description: String
    def isIncremental: Boolean
  }

  object RefreshType {
    case object Incremental extends RefreshType {
      val name          = "incremental"
      val description   = "Process only new or changed data since last run"
      val isIncremental = true
    }

    case object HistoryBackfill extends RefreshType {
      val name          = "history-backfill"
      val description   = "Process historical data within a specified time range"
      val isIncremental = false
    }

    case object Snapshot extends RefreshType {
      val name          = "snapshot"
      val description   = "Create a complete snapshot of current data state"
      val isIncremental = false
    }

    case object Restatement extends RefreshType {
      val name          = "restatement"
      val description   = "Reprocess and correct historical data"
      val isIncremental = false
    }

    /**
     * Parse refresh type from string. Used in configuration parsing and validation.
     */
    def fromString(s: String): Option[RefreshType] = s.toLowerCase.replace("_", "-") match {
      case "incremental"                   => Some(Incremental)
      case "history-backfill" | "backfill" => Some(HistoryBackfill)
      case "snapshot"                      => Some(Snapshot)
      case "restatement"                   => Some(Restatement)
      case _                               => None
    }

    val all: List[RefreshType] = List(Incremental, HistoryBackfill, Snapshot, Restatement)
  }

  /**
   * Workflow types for different data engineering patterns. Each type has specific characteristics
   * and requirements.
   */
  sealed trait WorkflowType extends Product with Serializable {
    def name: String
    def description: String
    def requiresScheduling: Boolean
  }

  object WorkflowType {
    case object DataIngestion extends WorkflowType {
      val name               = "data-ingestion"
      val description        = "Extract data from external sources"
      val requiresScheduling = true
    }

    case object DataTransformation extends WorkflowType {
      val name               = "data-transformation"
      val description        = "Transform and enrich data"
      val requiresScheduling = false
    }

    case object DataQuality extends WorkflowType {
      val name               = "data-quality"
      val description        = "Validate data quality and integrity"
      val requiresScheduling = false
    }

    case object DataExport extends WorkflowType {
      val name               = "data-export"
      val description        = "Export data to external systems"
      val requiresScheduling = true
    }

    case object MLPipeline extends WorkflowType {
      val name               = "ml-pipeline"
      val description        = "Machine learning data pipeline"
      val requiresScheduling = true
    }

    /**
     * Parse workflow type from string.
     */
    def fromString(s: String): Option[WorkflowType] = s.toLowerCase.replace("_", "-") match {
      case "data-ingestion" | "ingestion"            => Some(DataIngestion)
      case "data-transformation" | "transformation"  => Some(DataTransformation)
      case "data-quality" | "quality"                => Some(DataQuality)
      case "data-export" | "export"                  => Some(DataExport)
      case "ml-pipeline" | "ml" | "machine-learning" => Some(MLPipeline)
      case _                                         => None
    }

    val all: List[WorkflowType] =
      List(DataIngestion, DataTransformation, DataQuality, DataExport, MLPipeline)
  }

  /**
   * Environment types for deployment targeting. Each environment has different characteristics and
   * constraints.
   */
  sealed trait Environment extends Product with Serializable {
    def name: String
    def isProduction: Boolean
    def allowsExperiments: Boolean
  }

  object Environment {
    case object Development extends Environment {
      val name              = "development"
      val isProduction      = false
      val allowsExperiments = true
    }

    case object Testing extends Environment {
      val name              = "testing"
      val isProduction      = false
      val allowsExperiments = true
    }

    case object Staging extends Environment {
      val name              = "staging"
      val isProduction      = false
      val allowsExperiments = false
    }

    case object Production extends Environment {
      val name              = "production"
      val isProduction      = true
      val allowsExperiments = false
    }

    /**
     * Parse environment from string.
     */
    def fromString(s: String): Option[Environment] = s.toLowerCase match {
      case "development" | "dev" => Some(Development)
      case "testing" | "test"    => Some(Testing)
      case "staging" | "stage"   => Some(Staging)
      case "production" | "prod" => Some(Production)
      case _                     => None
    }

    val all: List[Environment] = List(Development, Testing, Staging, Production)
  }

  // ===============================
  // DATA TYPES
  // ===============================

  /**
   * Content types for data encoding/decoding. Supports various data formats used in data
   * engineering.
   */
  sealed trait ContentType extends Product with Serializable {
    def mimeType: String
    def fileExtension: String
  }

  object ContentType {
    case object Json extends ContentType {
      val mimeType      = "application/json"
      val fileExtension = "json"
    }

    case object Parquet extends ContentType {
      val mimeType      = "application/octet-stream"
      val fileExtension = "parquet"
    }

    case object Avro extends ContentType {
      val mimeType      = "avro/binary"
      val fileExtension = "avro"
    }

    case object Csv extends ContentType {
      val mimeType      = "text/csv"
      val fileExtension = "csv"
    }

    case object OrcFormat extends ContentType {
      val mimeType      = "application/octet-stream"
      val fileExtension = "orc"
    }

    /**
     * Parse content type from string.
     */
    def fromString(s: String): Option[ContentType] = s.toLowerCase match {
      case "json"    => Some(Json)
      case "parquet" => Some(Parquet)
      case "avro"    => Some(Avro)
      case "csv"     => Some(Csv)
      case "orc"     => Some(OrcFormat)
      case _         => None
    }

    /**
     * Get content type from file extension.
     */
    def fromExtension(ext: String): Option[ContentType] = {
      val cleanExt = ext.toLowerCase.stripPrefix(".")
      all.find(_.fileExtension == cleanExt)
    }

    val all: List[ContentType] = List(Json, Parquet, Avro, Csv, OrcFormat)
  }

  /**
   * Data schema representation. Provides a unified way to represent data schemas across different
   * formats.
   */
  sealed trait DataSchema extends Product with Serializable {
    def fields: List[SchemaField]
    def name: Option[String]
  }

  case class StructSchema(
    fields: List[SchemaField],
    name: Option[String] = None
  ) extends DataSchema

  case class ArraySchema(
    elementType: DataSchema,
    name: Option[String] = None
  ) extends DataSchema {
    def fields: List[SchemaField] = Nil
  }

  case class MapSchema(
    keyType: DataType,
    valueType: DataSchema,
    name: Option[String] = None
  ) extends DataSchema {
    def fields: List[SchemaField] = Nil
  }

  /**
   * Schema field definition.
   */
  case class SchemaField(
    name: String,
    dataType: DataType,
    nullable: Boolean = true,
    metadata: Metadata = Map.empty
  )

  /**
   * Data types supported by FlowForge. Covers the most common types used in data engineering.
   */
  sealed trait DataType extends Product with Serializable {
    def name: String
    def isNumeric: Boolean
    def isText: Boolean
    def isTemporal: Boolean
  }

  object DataType {
    case object StringType extends DataType {
      val name       = "string"
      val isNumeric  = false
      val isText     = true
      val isTemporal = false
    }

    case object IntegerType extends DataType {
      val name       = "integer"
      val isNumeric  = true
      val isText     = false
      val isTemporal = false
    }

    case object LongType extends DataType {
      val name       = "long"
      val isNumeric  = true
      val isText     = false
      val isTemporal = false
    }

    case object DoubleType extends DataType {
      val name       = "double"
      val isNumeric  = true
      val isText     = false
      val isTemporal = false
    }

    case object DecimalType extends DataType {
      val name       = "decimal"
      val isNumeric  = true
      val isText     = false
      val isTemporal = false
    }

    case object BooleanType extends DataType {
      val name       = "boolean"
      val isNumeric  = false
      val isText     = false
      val isTemporal = false
    }

    case object DateType extends DataType {
      val name       = "date"
      val isNumeric  = false
      val isText     = false
      val isTemporal = true
    }

    case object TimestampType extends DataType {
      val name       = "timestamp"
      val isNumeric  = false
      val isText     = false
      val isTemporal = true
    }

    case object BinaryType extends DataType {
      val name       = "binary"
      val isNumeric  = false
      val isText     = false
      val isTemporal = false
    }

    /**
     * Parse data type from string.
     */
    def fromString(s: String): Option[DataType] = s.toLowerCase match {
      case "string" | "text" | "varchar" => Some(StringType)
      case "int" | "integer"             => Some(IntegerType)
      case "long" | "bigint"             => Some(LongType)
      case "double" | "float"            => Some(DoubleType)
      case "decimal" | "numeric"         => Some(DecimalType)
      case "boolean" | "bool"            => Some(BooleanType)
      case "date"                        => Some(DateType)
      case "timestamp" | "datetime"      => Some(TimestampType)
      case "binary" | "bytes"            => Some(BinaryType)
      case _                             => None
    }

    val all: List[DataType] = List(
      StringType,
      IntegerType,
      LongType,
      DoubleType,
      DecimalType,
      BooleanType,
      DateType,
      TimestampType,
      BinaryType
    )
  }

  // ===============================
  // ERROR TYPES
  // ===============================

  /**
   * Base error type for all FlowForge operations. Provides structured error handling with context
   * and causality.
   */
  sealed abstract class FlowForgeError(
    val message: String,
    val cause: Option[Throwable] = None,
    val context: Metadata = Map.empty
  ) extends Exception(message, cause.orNull)
      with Product
      with Serializable {

    /**
     * Add context to the error. Enables building rich error information progressively.
     */
    def withContext(key: String, value: String): FlowForgeError = {
      val newContext = context + (key -> value)
      this match {
        case e: ConfigurationError  => e.copy(context = newContext)
        case e: ValidationError     => e.copy(context = newContext)
        case e: DataProcessingError => e.copy(context = newContext)
        case e: ResourceError       => e.copy(context = newContext)
        case e: SystemError         => e.copy(context = newContext)
      }
    }

    /**
     * Add multiple context entries.
     */
    def withContext(entries: (String, String)*): FlowForgeError =
      entries.foldLeft(this)((error, entry) => error.withContext(entry._1, entry._2))
  }

  /**
   * Configuration-related errors.
   */
  case class ConfigurationError(
    override val message: String,
    override val cause: Option[Throwable] = None,
    override val context: Metadata = Map.empty
  ) extends FlowForgeError(message, cause, context)

  /**
   * Data processing errors.
   */
  case class DataProcessingError(
    override val message: String,
    override val cause: Option[Throwable] = None,
    override val context: Metadata = Map.empty
  ) extends FlowForgeError(message, cause, context)

  /**
   * Resource management errors.
   */
  case class ResourceError(
    override val message: String,
    override val cause: Option[Throwable] = None,
    override val context: Metadata = Map.empty
  ) extends FlowForgeError(message, cause, context)

  /**
   * System-level errors.
   */
  case class SystemError(
    override val message: String,
    override val cause: Option[Throwable] = None,
    override val context: Metadata = Map.empty
  ) extends FlowForgeError(message, cause, context)

  // ===============================
  // METRIC TYPES
  // ===============================

  /**
   * Metric types for observability and monitoring.
   */
  sealed trait MetricType extends Product with Serializable {
    def name: String
  }

  object MetricType {
    case object Counter extends MetricType {
      val name = "counter"
    }

    case object Gauge extends MetricType {
      val name = "gauge"
    }

    case object Histogram extends MetricType {
      val name = "histogram"
    }

    case object Timer extends MetricType {
      val name = "timer"
    }

    def fromString(s: String): Option[MetricType] = s.toLowerCase match {
      case "counter"   => Some(Counter)
      case "gauge"     => Some(Gauge)
      case "histogram" => Some(Histogram)
      case "timer"     => Some(Timer)
      case _           => None
    }

    val all: List[MetricType] = List(Counter, Gauge, Histogram, Timer)
  }

  /**
   * Metric value with metadata.
   */
  case class MetricValue(
    name: String,
    value: Double,
    metricType: MetricType,
    timestamp: Timestamp = Instant.now(),
    tags: Metadata = Map.empty
  )

  /**
   * Aggregated metric information.
   */
  case class MetricSummary(
    name: String,
    count: Long,
    sum: Double,
    min: Double,
    max: Double,
    avg: Double,
    timestamp: Timestamp = Instant.now()
  )

  // ===============================
  // F-BOUNDED POLYMORPHISM EXAMPLES
  // ===============================

  /**
   * Self-referential type bounds for composable components. This advanced Scala feature enables
   * type-safe composition patterns.
   */
  trait SelfComposable[Self <: SelfComposable[Self]] {

    /**
     * Compose this component with another of the same type.
     */
    def compose(other: Self): Self

    /**
     * Reference to self with proper typing.
     */
    protected def self: Self
  }

  /**
   * Example of F-bounded polymorphism in action. Transformation steps can be composed while
   * maintaining type safety.
   */
  trait TransformationStep[Self <: TransformationStep[Self]] extends SelfComposable[Self] {
    def transform[A](data: A): A

    def compose(other: Self): Self = {
      val currentSelf = self
      new TransformationStep[Self] {
        def transform[A](data: A): A = other.transform(currentSelf.transform(data))
        protected def self: Self     = this.asInstanceOf[Self]
      }.asInstanceOf[Self]
    }
  }
}
