/**
 * FlowForge Core Module - Package Object
 *
 * File: modules/core/src/main/scala/com/flowforge/core/package.scala Package: com.flowforge.core
 *
 * This package object provides convenient imports, type aliases, and utility functions for the
 * FlowForge core module. It serves as the main entry point for users of the core functionality,
 * offering a clean and intuitive API surface.
 *
 * Design Patterns Applied:
 *   - Facade Pattern: Simplified interface to complex subsystems
 *   - Module Pattern: Organized namespace for related functionality
 *   - Factory Pattern: Convenient constructors for common types
 *   - Adapter Pattern: Unified interface for different implementations
 *   - Type Alias Pattern: Domain-specific names for complex types
 *
 * Scala Features Showcased:
 *   - Package Objects: Namespace-level definitions and imports
 *   - Type Aliases: Readable names for complex parameterized types
 *   - Implicit Conversions: Seamless API experience
 *   - Extension Methods: Adding functionality to existing types
 *   - Import Organization: Clean, discoverable API surface
 *   - Generic Programming: Parameterized utility functions
 *   - Value Classes: Zero-cost abstractions for type safety
 *
 * Innovation Highlights:
 *   - Zero-import experience for common operations
 *   - Consistent naming conventions across all types
 *   - Performance-optimized common operations using value classes
 *   - Type-safe shortcuts for complex functional patterns
 *   - Seamless integration with effect systems (ZIO, Cats-Effect)
 *   - Compile-time validation for data pipeline configurations
 *
 * Usage Examples:
 * ```scala
 * import com.flowforge.core._
 *
 * // Type-safe pipeline construction
 * val pipeline = Pipeline
 *   .builder[IO]
 *   .withName("customer-analytics")
 *   .withSource(DataSource.gcs("my-bucket", "events/", Parquet))
 *   .withSink(DataSink.bigquery("project", "dataset", "table"))
 *   .withQuality(QualityRules.standard)
 *   .build
 *
 * // Effect-polymorphic operations
 * def processData[F[_]: EffectSystem](data: List[String]): F[ProcessedData] =
 *   for {
 *     validated <- data.parTraverseEffect(validateItem)
 *     processed <- validated.traverseEffect(processItem)
 *     result    <- aggregateResults(processed)
 *   } yield result
 *
 * // Configuration with validation
 * val config = PipelineConfig.builder
 *   .withName("etl-pipeline")
 *   .withEnvironment(Environment.Production)
 *   .withSpark(SparkConfig.cluster("etl-app", "spark://master:7077"))
 *   .build
 * ```
 *
 * @author
 *   FlowForge Team
 * @version 1.0.0
 * @since 2024
 */
package com.flowforge

import cats.data._
import cats.effect.Resource
import cats.syntax.all._
import com.flowforge.core.algebra.EffectSystem
import com.flowforge.core.types.RefinedTypes.{BucketName, TableName}
import com.flowforge.core.types._

import java.time.{Duration, Instant}
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

/**
 * The core package object provides the main API for FlowForge core functionality.
 *
 * This includes:
 *   - Type aliases for common patterns and complex types
 *   - Implicit conversions for seamless API usage
 *   - Utility functions for common operations
 *   - Integration points with effect systems
 *   - Performance-optimized operations
 *   - Validation and error handling utilities
 *
 * Key Design Principles:
 *   - Convention over Configuration: Smart defaults for common use cases
 *   - Type Safety: Compile-time validation wherever possible
 *   - Effect Polymorphism: Work with any F[_] that has an EffectSystem instance
 *   - Functional Purity: All operations are referentially transparent
 *   - Performance: Zero-cost abstractions and optimized implementations
 *   - Discoverability: Intuitive naming and comprehensive documentation
 */
package object core {

  // ===============================
  // TYPE ALIASES FOR COMMON PATTERNS
  // ===============================

  /**
   * Common type aliases for frequently used complex types. These provide more readable
   * domain-specific names.
   */

  // Effect types
  type Effect[F[_], A]              = F[A]
  type EffectValidation[F[_], E, A] = F[ValidatedNel[E, A]]
  type ConfigValidation[A]          = ValidatedNel[ConfigError, A]
  type ErrorOr[A]                   = Either[FlowForgeError, A]

  // Pipeline types
  type PipelineComponent[F[_], A, B] = Kleisli[F, A, B]
  type PipelineResult[A]             = Either[NonEmptyList[FlowForgeError], A]
  type ValidationResult[A]           = ValidatedNel[FlowForgeError, A]

  // Data types
  type DataContract[A]                = A => ValidationResult[Unit]
  type DataTransformation[F[_], A, B] = A => F[B]
  type QualityCheck[A]                = A => ValidationResult[A]

  // Configuration types
  type ConfigReader[A]     = Reader[Map[String, String], ConfigValidation[A]]
  type MetricsWriter[A]    = Writer[List[String], A]
  type PipelineState[S, A] = State[S, A]

  // Resource types
  type SafeResource[F[_], R]        = Resource[F, R]
  type ResourceAcquisition[F[_], R] = F[R]
  type ResourceRelease[F[_], R]     = R => F[Unit]

  // Timing and measurement
  type TimedResult[A]       = (A, FiniteDuration)
  type TimestampedResult[A] = (A, Instant)

  // Import common instances and syntax automatically

  // ===============================
  // SMART CONSTRUCTORS & UTILITIES
  // ===============================

  /**
   * Smart constructors for common FlowForge types. These provide convenient, validated construction
   * of core types.
   */

  object Pipeline {

    /**
     * Create a pipeline builder with effect system support.
     *
     * @tparam F
     *   Effect type (IO, Task, etc.)
     * @return
     *   Pipeline builder for fluent construction
     */
    def builder[F[_]: EffectSystem]: com.flowforge.core.PipelineBuilder[F] =
      com.flowforge.core.PipelineBuilder.empty[F]

    /**
     * Create a simple transformation pipeline.
     *
     * @param f
     *   Transformation function
     * @tparam F
     *   Effect type
     * @tparam A
     *   Input type
     * @tparam B
     *   Output type
     * @return
     *   Pipeline component
     */
    def transform[F[_]: EffectSystem, A, B](f: A => F[B]): PipelineComponent[F, A, B] =
      Kleisli(f)

    /**
     * Create a validation pipeline component.
     *
     * @param validator
     *   Validation function
     * @tparam F
     *   Effect type
     * @tparam A
     *   Type to validate
     * @return
     *   Validation pipeline component
     */
    def validate[F[_]: EffectSystem, A](
      validator: A => ValidationResult[A]
    ): PipelineComponent[F, A, A] =
      Kleisli { a =>
        validator(a).fold(
          errors => EffectSystem[F].raiseError(errors.head.asInstanceOf[Throwable]),
          success => EffectSystem[F].pure(success)
        )
      }
  }

  /**
   * Utility functions for working with effects and validations.
   */
  object Effects {

    /**
     * Lift a pure value into any effect.
     */
    def pure[F[_]: EffectSystem, A](value: A): F[A] =
      EffectSystem[F].pure(value)

    /**
     * Create an effect that fails with the given error.
     */
    def raiseError[F[_]: EffectSystem, A](error: Throwable): F[A] =
      EffectSystem[F].raiseError(error)

    /**
     * Create an effect from a Try.
     */
    def fromTry[F[_]: EffectSystem, A](tried: Try[A]): F[A] =
      EffectSystem[F].fromTry(tried)

    /**
     * Create an effect from an Either.
     */
    def fromEither[F[_]: EffectSystem, A](either: Either[Throwable, A]): F[A] =
      EffectSystem[F].fromEither(either)

    /**
     * Create an effect from a Future.
     */
    def fromFuture[F[_]: EffectSystem, A](
      future: => Future[A]
    )(implicit ec: ExecutionContext): F[A] =
      EffectSystem[F].fromFuture(future)

    /**
     * Convert a validation to an effect.
     */
    def fromValidation[F[_]: EffectSystem, E <: Throwable, A](
      validation: ValidatedNel[E, A]
    ): F[A] =
      validation.fold(
        errors => EffectSystem[F].raiseError(errors.head),
        success => EffectSystem[F].pure(success)
      )

    /**
     * Run multiple effects in parallel and collect results.
     */
    def parAll[F[_]: EffectSystem, A](effects: List[F[A]]): F[List[A]] =
      EffectSystem[F].parSequence(effects)

    /**
     * Run effects sequentially and collect results.
     */
    def seqAll[F[_]: EffectSystem, A](effects: List[F[A]]): F[List[A]] =
      EffectSystem[F].sequence(effects)

    /**
     * Repeat an effect until a condition is met.
     */
    def repeatUntil[F[_]: EffectSystem, A](
      fa: F[A]
    )(condition: A => Boolean): F[A] =
      EffectSystem[F].repeatUntil(fa)(condition)

    /**
     * Time the execution of an effect.
     */
    def timed[F[_]: EffectSystem, A](fa: F[A]): F[(A, FiniteDuration)] =
      EffectSystem[F].timed(fa)
  }

  /**
   * Validation utilities for common patterns.
   */
  object Validation {

    /**
     * Create a successful validation.
     */
    def valid[E, A](value: A): ValidatedNel[E, A] =
      value.validNel

    /**
     * Create a failed validation.
     */
    def invalid[E, A](error: E): ValidatedNel[E, A] =
      error.invalidNel

    /**
     * Create a failed validation with multiple errors.
     */
    def invalidNel[E, A](errors: NonEmptyList[E]): ValidatedNel[E, A] =
      Validated.invalid(errors)

    /**
     * Validate a required field is not null.
     */
    def required[A](fieldName: String, value: Option[A]): ConfigValidation[A] =
      value.toValidNel(ConfigError.MissingRequired(fieldName))

    /**
     * Validate a string matches a pattern.
     */
    def matchesPattern(
      fieldName: String,
      value: String,
      pattern: String
    ): ConfigValidation[String] =
      if (value.matches(pattern)) {
        value.validNel
      } else {
        ConfigError.InvalidFormat(fieldName, value, s"pattern $pattern").invalidNel
      }

    /**
     * Validate a number is within range.
     */
    def inRange(
      fieldName: String,
      value: Double,
      min: Double,
      max: Double
    ): ConfigValidation[Double] =
      if (value >= min && value <= max) {
        value.validNel
      } else {
        ConfigError.OutOfRange(fieldName, value.toString, min.toString, max.toString).invalidNel
      }

    /**
     * Combine multiple validations.
     */
    def combine[A, B, C](
      va: ValidatedNel[ConfigError, A],
      vb: ValidatedNel[ConfigError, B]
    )(f: (A, B) => C): ValidatedNel[ConfigError, C] =
      (va, vb).mapN(f)

    /**
     * Validate all elements in a list.
     */
    def validateAll[A, B](
      list: List[A]
    )(validator: A => ValidatedNel[ConfigError, B]): ValidatedNel[ConfigError, List[B]] =
      list.traverse(validator)
  }

  // ===============================
  // IMPLICIT CONVERSIONS & EXTENSIONS
  // ===============================

  /**
   * Implicit conversions for seamless API usage. These enable natural syntax for common operations.
   */

  /**
   * Convert string to validated bucket name.
   */
  implicit class StringToBucketName(private val str: String) extends AnyVal {
    def bucketName: BucketName = BucketName(str)
  }

  /**
   * Convert string to validated table name.
   */
  implicit class StringToTableName(private val str: String) extends AnyVal {
    def tableName: TableName = TableName(str)
  }

  /**
   * Enhance lists with FlowForge-specific operations.
   */
  implicit class FlowForgeListOps[A](private val list: List[A]) extends AnyVal {

    /**
     * Apply validation to all elements and collect results.
     */
    def validateAll[E](validator: A => ValidatedNel[E, A]): ValidatedNel[E, List[A]] =
      list.traverse(validator)

    /**
     * Apply an effect to all elements in parallel.
     */
    def parMapEffect[F[_]: EffectSystem, B](f: A => F[B]): F[List[B]] =
      EffectSystem[F].parTraverse(list)(f)

    /**
     * Apply an effect to all elements sequentially.
     */
    def mapEffect[F[_]: EffectSystem, B](f: A => F[B]): F[List[B]] =
      EffectSystem[F].traverse(list)(f)

    /**
     * Find the first element that satisfies an effectful predicate.
     */
    def findEffect[F[_]: EffectSystem](predicate: A => F[Boolean]): F[Option[A]] = {
      def loop(remaining: List[A]): F[Option[A]] = remaining match {
        case Nil => EffectSystem[F].pure(None)
        case head :: tail =>
          predicate(head).flatMap { matches =>
            if (matches) EffectSystem[F].pure(Some(head))
            else loop(tail)
          }
      }

      loop(list)
    }
  }

  /**
   * Enhance maps with configuration-specific operations.
   */
  implicit class ConfigMapOps(private val map: Map[String, String]) extends AnyVal {

    /**
     * Get a required string value.
     */
    def getRequired(key: String): ConfigValidation[String] =
      map.get(key).toValidNel(ConfigError.MissingRequired(key))

    /**
     * Get an optional string value.
     */
    def getOptional(key: String): ConfigValidation[Option[String]] =
      map.get(key).validNel

    /**
     * Get a required integer value.
     */
    def getInt(key: String): ConfigValidation[Int] =
      getRequired(key).andThen { value =>
        Try(value.toInt).fold(
          _ => ConfigError.InvalidFormat(key, value, "integer").invalidNel,
          int => int.validNel
        )
      }

    /**
     * Get a required boolean value.
     */
    def getBoolean(key: String): ConfigValidation[Boolean] =
      getRequired(key).andThen { value =>
        value.toLowerCase match {
          case "true" | "yes" | "1" => true.validNel
          case "false" | "no" | "0" => false.validNel
          case _                    => ConfigError.InvalidFormat(key, value, "boolean").invalidNel
        }
      }

    /**
     * Get a required duration value.
     */
    def getDuration(key: String): ConfigValidation[FiniteDuration] =
      getRequired(key).andThen { value =>
        Try(Duration.parse(value)).fold(
          _ => ConfigError.InvalidFormat(key, value, "ISO-8601 duration").invalidNel,
          d => FiniteDuration(d.toMillis, "millis").validNel
        )
      }
  }

  // ===============================
  // CONVENIENCE CONSTRUCTORS
  // ===============================

  /**
   * Convenient factory methods for common FlowForge types.
   */

  /**
   * Create a GCS data source.
   */
  def gcsSource(bucket: String, prefix: String, format: DataFormat): DataSource.GcsSource =
    DataSource.gcs(bucket, prefix, format)

  /**
   * Create an S3 data source.
   */
  def s3Source(bucket: String, prefix: String, format: DataFormat): DataSource.S3Source =
    DataSource.s3(bucket, prefix, format)

  /**
   * Create a BigQuery data source.
   */
  def bigquerySource(project: String, dataset: String, table: String): DataSource.BigQuerySource =
    DataSource.bigQuery(project, dataset, table)

  /**
   * Create a GCS data sink.
   */
  def gcsSink(bucket: String, prefix: String, format: DataFormat): DataSink.GcsSink =
    DataSink.gcs(bucket, prefix, format)

  /**
   * Create an S3 data sink.
   */
  def s3Sink(bucket: String, prefix: String, format: DataFormat): DataSink.S3Sink =
    DataSink.s3(bucket, prefix, format)

  /**
   * Create a Spark configuration.
   */
  def sparkConfig(appName: String): SparkConfig =
    SparkConfig.default(appName)

  /**
   * Create a Flink configuration.
   */
  def flinkConfig(jobName: String): FlinkConfig =
    FlinkConfig.default(jobName)

  /**
   * Create a retry policy with exponential backoff.
   */
  def retryPolicy(maxRetries: Int, initialDelay: FiniteDuration): RetryPolicy =
    RetryPolicy.exponential(maxRetries, initialDelay)

  // ===============================
  // COMMON CONSTANTS
  // ===============================

  /**
   * Common constants used throughout FlowForge.
   */

  val DefaultTimeout: FiniteDuration = FiniteDuration(300, "seconds")
  val DefaultRetryAttempts: Int      = 3
  val DefaultBackoffFactor: Double   = 2.0
  val DefaultParallelism: Int        = Runtime.getRuntime.availableProcessors()

  // Data format constants
  val Parquet: DataFormat = DataFormat.Parquet
  val Avro: DataFormat    = DataFormat.Avro
  val CSV: DataFormat     = DataFormat.CSV
  val JSON: DataFormat    = DataFormat.JSON
  val JSONL: DataFormat   = DataFormat.JSONL
  val ORC: DataFormat     = DataFormat.ORC
  val Delta: DataFormat   = DataFormat.Delta

  // Environment constants
  val Development: Environment = Environment.Development
  val Testing: Environment     = Environment.Testing
  val Staging: Environment     = Environment.Staging
  val Production: Environment  = Environment.Production

  // ===============================
  // PIPELINE BUILDER
  // ===============================

  // Moved PipelineBuilder and FlowForgePipeline to separate files to avoid
  // package object anti-pattern - see PipelineBuilder.scala and FlowForgePipeline.scala
}
