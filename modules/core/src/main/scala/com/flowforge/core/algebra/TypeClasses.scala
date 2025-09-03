/**
 * FlowForge Core Module - Type Classes
 *
 * File: modules/core/src/main/scala/com/flowforge/core/algebra/TypeClasses.scala Package:
 * com.flowforge.core.algebra
 *
 * This file defines custom type classes specific to the FlowForge ecosystem. These type classes enable
 * polymorphic operations across different data types, formats, and engines while maintaining type safety and
 * composability.
 *
 * Design Patterns Applied:
 *   - Type Class Pattern: Ad-hoc polymorphism through implicit resolution
 *   - Strategy Pattern: Different implementations per data type
 *   - Adapter Pattern: Uniform interface for diverse underlying systems
 *   - Decorator Pattern: Composable capabilities through type class stacking
 *   - Template Method Pattern: Abstract algorithms with concrete implementations
 *
 * Scala Features Showcased:
 *   - Higher-Kinded Types: Generic programming over type constructors
 *   - Type Classes: Capability-based programming with implicits
 *   - Implicit Resolution: Automatic capability injection
 *   - Generic Derivation: Automatic instance generation
 *   - Type-Level Programming: Compile-time computation and validation
 *   - Self Types: Compositional type constraints
 *   - Phantom Types: Compile-time markers for type safety
 *
 * Innovation Highlights:
 *   - Data encoding/decoding abstraction across formats (Parquet, Avro, JSON)
 *   - Schema evolution support with compatibility checking
 *   - Quality constraints as first-class type class operations
 *   - Metrics collection with composable aggregation patterns
 *   - Configuration reading with validation and error accumulation
 *   - Serialization with performance optimization hints
 *
 * Usage Examples:
 * ```scala
 * // Automatic encoding/decoding for any supported format
 * def processData[A: DataEncoder: DataDecoder](data: A, format: DataFormat): F[A] =
 *   for {
 *     encoded   <- DataEncoder[A].encode(data, format)
 *     processed <- processEncodedData(encoded)
 *     decoded   <- DataDecoder[A].decode(processed, format)
 *   } yield decoded
 *
 * // Polymorphic quality checking
 * def validateDataset[A: DataContract](dataset: Dataset[A]): ValidationResult[A] =
 *   DataContract[A].validate(dataset)
 *
 * // Configuration with automatic validation
 * def loadConfig[A: ConfigReader](source: Map[String, String]): ValidatedNel[ConfigError, A] =
 *   ConfigReader[A].read(source)
 * ```
 *
 * @author
 *   FlowForge Team
 * @version 1.0.0
 * @since 2024
 */
package com.flowforge.core.algebra

import cats.Show
import cats.data.{ NonEmptyList, ValidatedNel }
import cats.syntax.all._
import com.flowforge.core.types._

import java.time.Instant
import scala.concurrent.duration.FiniteDuration

// ===============================
// DATA ENCODING/DECODING
// ===============================

/**
 * Type class for encoding data to different formats.
 *
 * Enables polymorphic data serialization across formats like Parquet, Avro, JSON, etc. Implementations can
 * optimize for specific data types and formats while providing a uniform interface.
 *
 * @tparam A
 *   The data type to encode
 */
trait DataEncoder[A] {

  /**
   * Encode data to the specified format.
   *
   * @param data
   *   The data to encode
   * @param format
   *   Target data format
   * @return
   *   Encoded binary data with metadata
   */
  def encode(data: A, format: DataFormat): Either[EncodingError, EncodedData]

  /**
   * Get the schema for this data type in the specified format.
   *
   * @param format
   *   Target data format
   * @return
   *   Schema representation for the format
   */
  def schema(format: DataFormat): DataSchema

  /**
   * Estimate the encoded size for planning and optimization.
   *
   * @param data
   *   The data to estimate size for
   * @param format
   *   Target data format
   * @return
   *   Estimated size in bytes
   */
  def estimateSize(data: A, format: DataFormat): Long

  /**
   * Check if this encoder supports the given format.
   *
   * @param format
   *   Data format to check
   * @return
   *   True if format is supported
   */
  def supportsFormat(format: DataFormat): Boolean

  /**
   * Get performance hints for encoding optimization.
   *
   * @param data
   *   The data to analyze
   * @param format
   *   Target data format
   * @return
   *   Optimization hints
   */
  def optimizationHints(data: A, format: DataFormat): EncodingHints
}

object DataEncoder {

  /**
   * Type class summoner for DataEncoder.
   */
  def apply[A](implicit ev: DataEncoder[A]): DataEncoder[A] = ev

  /**
   * Create a DataEncoder instance from encoding function.
   */
  def instance[A](
    encodeF: (A, DataFormat) => Either[EncodingError, EncodedData],
    schemaF: DataFormat => DataSchema,
  ): DataEncoder[A] = new DataEncoder[A] {
    def encode(data: A, format: DataFormat): Either[EncodingError, EncodedData] =
      encodeF(data, format)

    def schema(format: DataFormat): DataSchema =
      schemaF(format)

    def estimateSize(data: A, format: DataFormat): Long =
      1024L // Default conservative estimate

    def supportsFormat(format: DataFormat): Boolean =
      true // Default to supporting all formats

    def optimizationHints(data: A, format: DataFormat): EncodingHints =
      EncodingHints.default
  }
}

/**
 * Type class for decoding data from different formats.
 *
 * Companion to DataEncoder, enables polymorphic deserialization with format-specific optimizations and error
 * handling.
 *
 * @tparam A
 *   The data type to decode to
 */
trait DataDecoder[A] {

  /**
   * Decode data from the specified format.
   *
   * @param encodedData
   *   Binary data to decode
   * @param format
   *   Source data format
   * @return
   *   Decoded data or decoding error
   */
  def decode(encodedData: EncodedData, format: DataFormat): Either[DecodingError, A]

  /**
   * Validate that encoded data conforms to expected schema.
   *
   * @param encodedData
   *   Data to validate
   * @param expectedSchema
   *   Schema to validate against
   * @return
   *   Validation result
   */
  def validateSchema(
    encodedData: EncodedData,
    expectedSchema: DataSchema,
  ): Either[SchemaError, Unit]

  /**
   * Decode with schema evolution support.
   *
   * @param encodedData
   *   Data to decode
   * @param format
   *   Source format
   * @param targetSchema
   *   Target schema for evolution
   * @return
   *   Decoded data with schema evolution applied
   */
  def decodeWithEvolution(
    encodedData: EncodedData,
    format: DataFormat,
    targetSchema: DataSchema,
  ): Either[DecodingError, A]

  /**
   * Check if this decoder can handle the given format.
   *
   * @param format
   *   Data format to check
   * @return
   *   True if format is supported
   */
  def supportsFormat(format: DataFormat): Boolean
}

object DataDecoder {

  /**
   * Type class summoner for DataDecoder.
   */
  def apply[A](implicit ev: DataDecoder[A]): DataDecoder[A] = ev

  /**
   * Create a DataDecoder instance from decoding function.
   */
  def instance[A](
    decodeF: (EncodedData, DataFormat) => Either[DecodingError, A],
  ): DataDecoder[A] = new DataDecoder[A] {
    def decode(encodedData: EncodedData, format: DataFormat): Either[DecodingError, A] =
      decodeF(encodedData, format)

    def validateSchema(
      encodedData: EncodedData,
      expectedSchema: DataSchema,
    ): Either[SchemaError, Unit] =
      Right(()) // Default no-op validation

    def decodeWithEvolution(
      encodedData: EncodedData,
      format: DataFormat,
      targetSchema: DataSchema,
    ): Either[DecodingError, A] =
      decode(encodedData, format) // Default to simple decode

    def supportsFormat(format: DataFormat): Boolean =
      true
  }
}

// ===============================
// DATA CONTRACTS
// ===============================

/**
 * Type class for data contract validation.
 *
 * Enables compile-time and runtime validation of data against business rules and quality constraints.
 * Supports composition of multiple validation rules with error accumulation.
 *
 * @tparam A
 *   The data type to validate
 */
trait DataContract[A] {

  /**
   * Validate data against all contract rules.
   *
   * @param data
   *   The data to validate
   * @return
   *   Validation result with accumulated errors
   */
  def validate(data: A): ValidatedNel[ContractViolation, A]

  /**
   * Get all validation rules for this contract.
   *
   * @return
   *   List of validation rules
   */
  def rules: List[ValidationRule[A]]

  /**
   * Get the schema requirements for this contract.
   *
   * @return
   *   Required data schema
   */
  def requiredSchema: DataSchema

  /**
   * Check if data satisfies minimum contract requirements.
   *
   * @param data
   *   The data to check
   * @return
   *   True if minimum requirements are met
   */
  def satisfiesMinimum(data: A): Boolean

  /**
   * Combine this contract with another contract.
   *
   * @param other
   *   Contract to combine with
   * @return
   *   Combined contract with all rules
   */
  def combine(other: DataContract[A]): DataContract[A]

  /**
   * Create a relaxed version of this contract for testing.
   *
   * @return
   *   Contract with non-critical rules made optional
   */
  def relaxed: DataContract[A]
}

object DataContract {

  /**
   * Type class summoner for DataContract.
   */
  def apply[A](implicit ev: DataContract[A]): DataContract[A] = ev

  /**
   * Create a DataContract from validation rules.
   */
  def fromRules[A](
    validationRules: List[ValidationRule[A]],
    schema: DataSchema,
  ): DataContract[A] = new DataContract[A] {

    val rules: List[ValidationRule[A]] = validationRules
    val requiredSchema: DataSchema     = schema

    def validate(data: A): ValidatedNel[ContractViolation, A] = {
      val results = rules.map(_.validate(data))
      results.sequence.map(_ => data)
    }

    def satisfiesMinimum(data: A): Boolean = {
      val criticalRules = rules.filter(_.severity.shouldBlock)
      criticalRules.forall(_.validate(data).isValid)
    }

    def combine(other: DataContract[A]): DataContract[A] =
      DataContract.fromRules(rules ++ other.rules, requiredSchema)

    def relaxed: DataContract[A] = {
      val nonCriticalRules = rules.filterNot(_.severity.shouldBlock)
      DataContract.fromRules(nonCriticalRules, requiredSchema)
    }
  }

  /**
   * Create an empty contract that accepts all data.
   */
  def empty[A]: DataContract[A] = fromRules(List.empty, DataSchema.builder.build)

  /**
   * Create a strict contract with comprehensive validation.
   */
  def strict[A](schema: DataSchema, rules: ValidationRule[A]*): DataContract[A] =
    fromRules(rules.toList, schema)
}

// ===============================
// SERIALIZATION
// ===============================

/**
 * Type class for high-performance binary serialization.
 *
 * Optimized for large-scale data processing with minimal overhead. Supports custom serialization strategies
 * per data type.
 *
 * @tparam A
 *   The data type to serialize
 */
trait DataSerializer[A] {

  /**
   * Serialize data to binary format.
   *
   * @param data
   *   The data to serialize
   * @return
   *   Serialized binary data
   */
  def serialize(data: A): Array[Byte]

  /**
   * Deserialize data from binary format.
   *
   * @param bytes
   *   Binary data to deserialize
   * @return
   *   Deserialized data or error
   */
  def deserialize(bytes: Array[Byte]): Either[SerializationError, A]

  /**
   * Get the serialization format identifier.
   *
   * @return
   *   Format identifier for version compatibility
   */
  def formatId: String

  /**
   * Estimate serialized size without actually serializing.
   *
   * @param data
   *   The data to estimate size for
   * @return
   *   Estimated size in bytes
   */
  def estimateSize(data: A): Int

  /**
   * Check if this serializer is compatible with given format ID.
   *
   * @param formatId
   *   Format identifier to check
   * @return
   *   True if compatible
   */
  def isCompatible(formatId: String): Boolean
}

object DataSerializer {

  /**
   * Type class summoner for DataSerializer.
   */
  def apply[A](implicit ev: DataSerializer[A]): DataSerializer[A] = ev

  /**
   * Create a DataSerializer instance from serialization functions.
   */
  def instance[A](
    serializeF: A => Array[Byte],
    deserializeF: Array[Byte] => Either[SerializationError, A],
    format: String,
  ): DataSerializer[A] = new DataSerializer[A] {
    def serialize(data: A): Array[Byte]                                = serializeF(data)
    def deserialize(bytes: Array[Byte]): Either[SerializationError, A] = deserializeF(bytes)
    val formatId: String                                               = format
    def estimateSize(data: A): Int              = serialize(data).length // Simple but accurate
    def isCompatible(formatId: String): Boolean = this.formatId == formatId
  }
}

// ===============================
// CONFIGURATION READING
// ===============================

/**
 * Type class for reading configuration from various sources.
 *
 * Enables polymorphic configuration loading with validation and error accumulation. Supports environment
 * variables, property files, command line arguments, etc.
 *
 * @tparam A
 *   The configuration type to read
 */
trait ConfigReader[A] {

  /**
   * Read configuration from a source.
   *
   * @param source
   *   Configuration source (key-value pairs)
   * @return
   *   Parsed and validated configuration or errors
   */
  def read(source: Map[String, String]): ValidatedNel[ConfigError, A]

  /**
   * Get the configuration keys this reader expects.
   *
   * @return
   *   List of expected configuration keys
   */
  def expectedKeys: List[String]

  /**
   * Get default values for optional configuration.
   *
   * @return
   *   Map of default values
   */
  def defaults: Map[String, String]

  /**
   * Validate configuration without parsing.
   *
   * @param source
   *   Configuration source
   * @return
   *   Validation errors if any
   */
  def validateKeys(source: Map[String, String]): ValidatedNel[ConfigError, Unit]

  /**
   * Read configuration with environment variable fallback.
   *
   * @param source
   *   Primary configuration source
   * @param envPrefix
   *   Prefix for environment variables
   * @return
   *   Configuration with environment fallback
   */
  def readWithEnvFallback(
    source: Map[String, String],
    envPrefix: String,
  ): ValidatedNel[ConfigError, A]
}

object ConfigReader {

  /**
   * Type class summoner for ConfigReader.
   */
  def apply[A](implicit ev: ConfigReader[A]): ConfigReader[A] = ev

  /**
   * Create a ConfigReader instance from reading function.
   */
  def instance[A](
    readF: Map[String, String] => ValidatedNel[ConfigError, A],
    keys: List[String],
  ): ConfigReader[A] = new ConfigReader[A] {
    def read(source: Map[String, String]): ValidatedNel[ConfigError, A] = readF(source)
    val expectedKeys: List[String]                                      = keys
    val defaults: Map[String, String]                                   = Map.empty

    def validateKeys(source: Map[String, String]): ValidatedNel[ConfigError, Unit] = {
      val missing = expectedKeys.filterNot(key => source.contains(key) || defaults.contains(key))
      if (missing.nonEmpty) {
        NonEmptyList.fromList(missing.map(ConfigError.MissingRequired)) match {
          case Some(errors) => errors.invalid
          case None         => ().validNel
        }
      } else {
        ().validNel
      }
    }

    def readWithEnvFallback(
      source: Map[String, String],
      envPrefix: String,
    ): ValidatedNel[ConfigError, A] = {
      val envVars = sys.env.map {
        case (k, v) =>
          if (k.startsWith(envPrefix)) {
            k.drop(envPrefix.length).toLowerCase -> v
          } else {
            k -> v
          }
      }
      read(source ++ envVars)
    }
  }
}

// ===============================
// METRICS COLLECTION
// ===============================

/**
 * Type class for collecting metrics from data processing operations.
 *
 * Enables polymorphic metrics collection across different data types and processing stages. Supports
 * composable metric aggregation.
 *
 * @tparam A
 *   The data type to collect metrics from
 */
trait MetricsCollector[A] {

  /**
   * Collect metrics from data.
   *
   * @param data
   *   The data to collect metrics from
   * @return
   *   Collected metrics
   */
  def collect(data: A): ProcessingMetrics

  /**
   * Collect metrics with timing information.
   *
   * @param data
   *   The data to analyze
   * @param duration
   *   Processing duration
   * @return
   *   Metrics with timing data
   */
  def collectTimed(data: A, duration: FiniteDuration): ProcessingMetrics

  /**
   * Get the metric names this collector produces.
   *
   * @return
   *   List of metric names
   */
  def metricNames: List[String]

  /**
   * Combine metrics from multiple data samples.
   *
   * @param samples
   *   List of data samples
   * @return
   *   Aggregated metrics
   */
  def aggregate(samples: List[A]): ProcessingMetrics

  /**
   * Check if metrics collection is enabled for this data type.
   *
   * @return
   *   True if metrics should be collected
   */
  def isEnabled: Boolean

  /**
   * Create a filtered version that only collects specified metrics.
   *
   * @param metricFilter
   *   Names of metrics to collect
   * @return
   *   Filtered metrics collector
   */
  def filterMetrics(metricFilter: Set[String]): MetricsCollector[A]
}

object MetricsCollector {

  /**
   * Type class summoner for MetricsCollector.
   */
  def apply[A](implicit ev: MetricsCollector[A]): MetricsCollector[A] = ev

  /**
   * Create a MetricsCollector instance from collection function.
   */
  def instance[A](
    collectF: A => ProcessingMetrics,
    names: List[String],
  ): MetricsCollector[A] = new MetricsCollector[A] {
    def collect(data: A): ProcessingMetrics = collectF(data)

    def collectTimed(data: A, duration: FiniteDuration): ProcessingMetrics = {
      val baseMetrics = collect(data)
      baseMetrics.withTiming("processing_duration_ms", duration.toMillis)
    }

    val metricNames: List[String] = names

    def aggregate(samples: List[A]): ProcessingMetrics = {
      val allMetrics = samples.map(collect)
      ProcessingMetrics.combine(allMetrics)
    }

    val isEnabled: Boolean = true

    def filterMetrics(metricFilter: Set[String]): MetricsCollector[A] = {
      val filteredNames = names.filter(metricFilter.contains)
      instance(collectF, filteredNames)
    }
  }

  /**
   * Create a no-op metrics collector that collects nothing.
   */
  def noop[A]: MetricsCollector[A] = instance(_ => ProcessingMetrics.empty, List.empty)

  /**
   * Create a basic metrics collector for common data types.
   */
  def basic[A]: MetricsCollector[A] = instance(
    data => ProcessingMetrics.basic(data.toString.length, Instant.now()),
    List("data_size", "processing_time"),
  )
}

// ===============================
// SUPPORTING TYPES
// ===============================

/**
 * Encoded data with metadata.
 */
case class EncodedData(
  data: Array[Byte],
  format: DataFormat,
  schema: Option[DataSchema] = None,
  compression: CompressionType = CompressionType.None,
  metadata: Map[String, String] = Map.empty)

/**
 * Encoding optimization hints.
 */
case class EncodingHints(
  preferredBlockSize: Option[Int] = None,
  compressionLevel: Option[Int] = None,
  parallelism: Option[Int] = None,
  memoryBudget: Option[Long] = None)

object EncodingHints {
  val default: EncodingHints = EncodingHints()
}

/**
 * Error types for encoding operations.
 */
sealed trait EncodingError extends FlowForgeError
case class UnsupportedFormat(format: DataFormat, dataType: String) extends EncodingError {
  val message       = s"Format $format is not supported for data type $dataType"
  val category      = ErrorCategory.Validation
  val severity      = ErrorSeverity.Error
  val context       = Map("format" -> format.toString, "dataType" -> dataType)
  val cause         = None
  val timestamp     = Instant.now()
  val errorId       = java.util.UUID.randomUUID().toString
  val isRetryable   = false
  val recoveryHints = List("Use a supported format", "Implement custom encoder")

  def withContext(additionalContext: Map[String, Any]) =
    copy().asInstanceOf[EncodingError] // Simplified for brevity
  def withCause(underlyingCause: Throwable) =
    copy().asInstanceOf[EncodingError] // Simplified for brevity
}

/**
 * Error types for decoding operations.
 */
sealed trait DecodingError extends FlowForgeError
case class CorruptedData(details: String) extends DecodingError {
  val message       = s"Data is corrupted: $details"
  val category      = ErrorCategory.Validation
  val severity      = ErrorSeverity.Error
  val context       = Map("details" -> details)
  val cause         = None
  val timestamp     = Instant.now()
  val errorId       = java.util.UUID.randomUUID().toString
  val isRetryable   = false
  val recoveryHints = List("Check data source", "Re-download data", "Use backup data")

  def withContext(additionalContext: Map[String, Any]) =
    copy().asInstanceOf[DecodingError] // Simplified for brevity
  def withCause(underlyingCause: Throwable) =
    copy().asInstanceOf[DecodingError] // Simplified for brevity
}

/**
 * Schema validation errors.
 */
sealed trait SchemaError extends FlowForgeError
case class SchemaIncompatible(expected: DataSchema, actual: DataSchema) extends SchemaError {
  val message =
    s"Schema incompatible: expected ${expected.fields.length} fields, got ${actual.fields.length}"
  val category      = ErrorCategory.Validation
  val severity      = ErrorSeverity.Error
  val context       = Map("expected" -> expected.toString, "actual" -> actual.toString)
  val cause         = None
  val timestamp     = Instant.now()
  val errorId       = java.util.UUID.randomUUID().toString
  val isRetryable   = false
  val recoveryHints = List("Update schema", "Enable schema evolution", "Transform data")

  def withContext(additionalContext: Map[String, Any]) =
    copy().asInstanceOf[SchemaError] // Simplified for brevity
  def withCause(underlyingCause: Throwable) =
    copy().asInstanceOf[SchemaError] // Simplified for brevity
}

/**
 * Serialization errors.
 */
sealed trait SerializationError extends FlowForgeError
case class SerializationFailed(reason: String) extends SerializationError {
  val message       = s"Serialization failed: $reason"
  val category      = ErrorCategory.System
  val severity      = ErrorSeverity.Error
  val context       = Map("reason" -> reason)
  val cause         = None
  val timestamp     = Instant.now()
  val errorId       = java.util.UUID.randomUUID().toString
  val isRetryable   = true
  val recoveryHints = List("Retry operation", "Check data format", "Use alternative serializer")

  def withContext(additionalContext: Map[String, Any]) =
    copy().asInstanceOf[SerializationError] // Simplified for brevity
  def withCause(underlyingCause: Throwable) =
    copy().asInstanceOf[SerializationError] // Simplified for brevity
}

/**
 * Contract violation errors.
 */
sealed trait ContractViolation extends FlowForgeError
case class RuleViolation(ruleName: String, details: String) extends ContractViolation {
  val message       = s"Contract rule '$ruleName' violated: $details"
  val category      = ErrorCategory.Business
  val severity      = ErrorSeverity.Error
  val context       = Map("rule" -> ruleName, "details" -> details)
  val cause         = None
  val timestamp     = Instant.now()
  val errorId       = java.util.UUID.randomUUID().toString
  val isRetryable   = false
  val recoveryHints = List("Fix data quality", "Update contract rules", "Contact data owner")

  def withContext(additionalContext: Map[String, Any]) =
    copy().asInstanceOf[ContractViolation] // Simplified for brevity
  def withCause(underlyingCause: Throwable) =
    copy().asInstanceOf[ContractViolation] // Simplified for brevity
}

/**
 * Validation rules for data contracts.
 */
trait ValidationRule[A] {
  def name: String
  def description: String
  def severity: ErrorSeverity
  def validate(data: A): ValidatedNel[ContractViolation, Unit]
}

object ValidationRule {
  def apply[A](
    ruleName: String,
    desc: String,
    sev: ErrorSeverity,
  )(
    validateF: A => ValidatedNel[ContractViolation, Unit],
  ): ValidationRule[A] =
    new ValidationRule[A] {
      val name              = ruleName
      val description       = desc
      val severity          = sev
      def validate(data: A) = validateF(data)
    }
}

/**
 * Processing metrics for data operations.
 */
case class ProcessingMetrics(
  recordCount: Long = 0,
  byteSize: Long = 0,
  processingTimeMs: Long = 0,
  errorCount: Long = 0,
  customMetrics: Map[String, Double] = Map.empty,
  timestamp: Instant = Instant.now()) {

  def withTiming(name: String, value: Long): ProcessingMetrics =
    copy(customMetrics = customMetrics + (name -> value.toDouble))

  def withCustom(name: String, value: Double): ProcessingMetrics =
    copy(customMetrics = customMetrics + (name -> value))

  def combine(other: ProcessingMetrics): ProcessingMetrics =
    ProcessingMetrics(
      recordCount = recordCount + other.recordCount,
      byteSize = byteSize + other.byteSize,
      processingTimeMs = processingTimeMs + other.processingTimeMs,
      errorCount = errorCount + other.errorCount,
      customMetrics = (customMetrics.toSeq ++ other.customMetrics.toSeq)
        .groupBy(_._1)
        .map { case (k, values) => k -> values.map(_._2).sum },
      timestamp = if (timestamp.isAfter(other.timestamp)) timestamp else other.timestamp,
    )
}

object ProcessingMetrics {

  val empty: ProcessingMetrics = ProcessingMetrics()

  def basic(size: Long, timestamp: Instant): ProcessingMetrics =
    ProcessingMetrics(recordCount = 1, byteSize = size, timestamp = timestamp)

  def combine(metrics: List[ProcessingMetrics]): ProcessingMetrics =
    metrics.foldLeft(empty)(_ combine _)

  implicit val showProcessingMetrics: Show[ProcessingMetrics] = Show.show { metrics =>
    s"Metrics(records=${metrics.recordCount}, size=${metrics.byteSize}, time=${metrics.processingTimeMs}ms, errors=${metrics.errorCount})"
  }
}
