/**
 * FlowForge Core Module - Package Object
 *
 * File: modules/core/src/main/scala/com/flowforge/core/package.scala Package: com.flowforge.core
 *
 * This package object provides convenient imports, type aliases, and utility functions for the
 * FlowForge core module. It serves as the main entry point for users of the core functionality.
 *
 * Design Patterns Applied:
 *   - Facade Pattern: Simplified interface to complex subsystems
 *   - Module Pattern: Organized namespace for related functionality
 *   - Factory Pattern: Convenient constructors for common types
 *   - Adapter Pattern: Unified interface for different implementations
 *
 * Scala Features Showcased:
 *   - Package Objects: Namespace-level definitions
 *   - Type Aliases: Domain-specific names for complex types
 *   - Implicit Conversions: Seamless API experience
 *   - Extension Methods: Adding functionality to existing types
 *   - Import Organization: Clean API surface
 *   - Generic Programming: Parameterized utility functions
 *
 * Innovation Highlights:
 *   - Zero-import experience for common operations
 *   - Consistent naming conventions across all types
 *   - Performance-optimized common operations
 *   - Type-safe shortcuts for complex patterns
 *   - Seamless integration with effect systems
 *
 * @author
 *   FlowForge Team
 * @version 1.0.0
 * @since 2024
 */
package com.flowforge

import scala.language.{ higherKinds, implicitConversions }
import scala.concurrent.duration.FiniteDuration
import scala.util.{ Failure, Success, Try }
import java.time.{ Instant, LocalDate }
import java.util.UUID

import cats.data.{ Kleisli, NonEmptyList, Reader, State, Validated, ValidatedNel, Writer }
import cats.effect.{ Clock, Resource }
import cats.{ Applicative, Functor, Monad }
import cats.implicits._

/**
 * The core package object provides the main API for FlowForge core functionality.
 *
 * This includes:
 *   - Type aliases for common patterns
 *   - Implicit conversions for seamless API usage
 *   - Utility functions for common operations
 *   - Integration points with effect systems
 *   - Performance-optimized operations
 *
 * Usage:
 * ```scala
 * import com.flowforge.core._
 *
 * // Type-safe pipeline construction
 * val pipeline = Pipeline
 *   .builder[IO]
 *   .withName("example-pipeline")
 *   .addSource(dataSource)
 *   .addTransform(transformation)
 *   .addSink(dataSink)
 *   .build
 *
 * // Effect-safe execution
 * val result = pipeline.validate().prepare().execute(inputData)
 * ```
 */
package object core {

  // ===============================
  // TYPE ALIASES FOR COMMON PATTERNS
  // ===============================

  /**
   * Import all core types for convenience.
   */
  type BinaryData = types.BinaryData
  type Metadata   = types.Metadata
  type Result[+A] = types.Result[A]
  type EntityId   = types.EntityId
  type Timestamp  = types.Timestamp

  // Pipeline-specific aliases
  type PipelineId   = types.PipelineId
  type WorkflowId   = types.WorkflowId
  type DataSourceId = types.DataSourceId
  type BatchSize    = types.BatchSize
  type RecordCount  = types.RecordCount

  // Configuration aliases
  type EffectSystemChoice = types.EffectSystemChoice
  type RefreshType        = types.RefreshType
  type WorkflowType       = types.WorkflowType
  type Environment        = types.Environment
  type ContentType        = types.ContentType

  // Error types
  type FlowForgeError      = types.FlowForgeError
  type ConfigurationError  = types.ConfigurationError
  type ValidationError     = validation.ValidationError
  type DataProcessingError = types.DataProcessingError
  type ResourceError       = types.ResourceError
  type SystemError         = types.SystemError

  // Validation aliases
  type ValidationResult[A] = validation.ValidationResult[A]
  type ValidationRule[A]   = validation.ValidationRule[A]
  type ValidationEngine[A] = validation.ValidationEngine[A]
  type ValidationReport[A] = validation.ValidationReport[A]

  // Type class aliases
  type DataEncoder[A]      = TypeClasses.DataEncoder[A]
  type DataDecoder[A]      = TypeClasses.DataDecoder[A]
  type DataSerializer[A]   = TypeClasses.DataSerializer[A]
  type ConfigReader[A]     = TypeClasses.ConfigReader[A]
  type DataContract[A]     = TypeClasses.DataContract[A]
  type MetricsCollector[A] = TypeClasses.MetricsCollector[A]
  type Show[A]             = TypeClasses.Show[A]

  // Pipeline component aliases
  type PipelineComponent[F[_], A, B, Self <: PipelineComponent[F, A, B, Self]] =
    pipeline.PipelineComponent[F, A, B, Self]
  type Pipeline[S <: types.PipelineState, F[_], A] = pipeline.Pipeline[S, F, A]
  type PipelineBuilder[F[_]]                       = pipeline.PipelineBuilder[F]
  type PipelineArrow[F[_], A, B]                   = pipeline.PipelineArrow[F, A, B]

  // Component result aliases
  type ComponentResult[A] = pipeline.ComponentResult[A]
  type ComponentMetadata  = pipeline.ComponentMetadata
  type ComponentExecution = pipeline.ComponentExecution

  // Refined type aliases
  type Refined[A, P]  = types.Refined[A, P]
  type NonEmpty[A]    = types.NonEmpty[A]
  type Positive[A]    = types.Positive[A]
  type NonNegative[A] = types.NonNegative[A]

  // ===============================
  // VALUE CONSTRUCTORS
  // ===============================

  /**
   * Convenient constructors for common types.
   */
  object constructors {

    // ID constructors
    def pipelineId(): PipelineId            = types.PipelineId.generate()
    def workflowId(): WorkflowId            = types.WorkflowId.generate()
    def dataSourceId(): DataSourceId        = types.DataSourceId.generate()
    def componentId(): pipeline.ComponentId = pipeline.ComponentId.generate()
    def executionId(): pipeline.ExecutionId = pipeline.ExecutionId.generate()

    // Safe constructors with validation
    def batchSize(value: Int): Option[BatchSize]      = types.BatchSize(value)
    def recordCount(value: Long): Option[RecordCount] = types.RecordCount(value)

    // Refined type constructors
    def nonEmptyString(s: String): Option[Refined[String, NonEmpty[String]]] =
      types.Refined.nonEmptyString(s)
    def positiveInt(i: Int): Option[Refined[Int, Positive[Int]]] =
      types.Refined.positiveInt(i)
    def validEmail(email: String): Option[Refined[String, types.ValidEmail]] =
      types.Refined.validEmail(email)
    def validUrl(url: String): Option[Refined[String, types.ValidUrl]] =
      types.Refined.validUrl(url)
    def validS3Bucket(bucket: String): Option[Refined[String, types.ValidS3Bucket]] =
      types.Refined.validS3Bucket(bucket)
    def validGcsPath(path: String): Option[Refined[String, types.ValidGcsPath]] =
      types.Refined.validGcsPath(path)
    def validTableName(name: String): Option[Refined[String, types.ValidTableName]] =
      types.Refined.validTableName(name)

    // Effect system choice
    def zio: EffectSystemChoice        = types.EffectSystemChoice.ZIO
    def catsEffect: EffectSystemChoice = types.EffectSystemChoice.CatsEffect

    // Refresh types
    def incremental: RefreshType     = types.RefreshType.Incremental
    def historyBackfill: RefreshType = types.RefreshType.HistoryBackfill
    def snapshot: RefreshType        = types.RefreshType.Snapshot
    def restatement: RefreshType     = types.RefreshType.Restatement

    // Workflow types
    def dataIngestion: WorkflowType      = types.WorkflowType.DataIngestion
    def dataTransformation: WorkflowType = types.WorkflowType.DataTransformation
    def dataQuality: WorkflowType        = types.WorkflowType.DataQuality
    def dataExport: WorkflowType         = types.WorkflowType.DataExport
    def mlPipeline: WorkflowType         = types.WorkflowType.MLPipeline

    // Environments
    def development: Environment = types.Environment.Development
    def testing: Environment     = types.Environment.Testing
    def staging: Environment     = types.Environment.Staging
    def production: Environment  = types.Environment.Production

    // Content types
    def json: ContentType    = types.ContentType.Json
    def parquet: ContentType = types.ContentType.Parquet
    def avro: ContentType    = types.ContentType.Avro
    def csv: ContentType     = types.ContentType.Csv
    def orc: ContentType     = types.ContentType.OrcFormat
  }

  // ===============================
  // VALIDATION COMBINATORS
  // ===============================

  /**
   * Validation combinators for common patterns.
   */
  object validators {

    // Import validation combinators
    val required           = validation.Validators.required _
    val length             = validation.Validators.length _
    val minLength          = validation.Validators.minLength _
    val maxLength          = validation.Validators.maxLength _
    val range              = validation.Validators.range _
    val min                = validation.Validators.min _
    val max                = validation.Validators.max _
    val positive           = validation.Validators.positive _
    val nonNegative        = validation.Validators.nonNegative _
    val pattern            = validation.Validators.pattern _
    val email              = validation.Validators.email _
    val url                = validation.Validators.url _
    val custom             = validation.Validators.custom _
    val oneOf              = validation.Validators.oneOf _
    val nonEmpty           = validation.Validators.nonEmpty _
    val nonEmptyCollection = validation.Validators.nonEmptyCollection _
    val all                = validation.Validators.all _
    val any                = validation.Validators.any _

    // Domain-specific validators
    val validatePipelineConfig   = validation.DomainValidators.validatePipelineConfig _
    val validateDataSourceConfig = validation.DomainValidators.validateDataSourceConfig _
    val validateS3Config         = validation.DomainValidators.validateS3Config _
    val validateGcsConfig        = validation.DomainValidators.validateGcsConfig _
    val validateTableName        = validation.DomainValidators.validateTableName _
    val validateBatchSize        = validation.DomainValidators.validateBatchSize _
    val validateRefreshType      = validation.DomainValidators.validateRefreshType _

    // Validation engine creation
    def engine[A](rules: ValidationRule[A]*): ValidationEngine[A] =
      validation.ValidationEngine(rules: _*)

    def emptyEngine[A]: ValidationEngine[A] = validation.ValidationEngine.empty[A]
  }

  // ===============================
  // PIPELINE BUILDING DSL
  // ===============================

  /**
   * Pipeline building DSL for convenient pipeline construction.
   */
  object Pipeline {

    /**
     * Create a new pipeline builder.
     */
    def builder[F[_]: EffectSystem](): PipelineBuilder[F] =
      pipeline.PipelineBuilder[F]()

    /**
     * Create a simple pipeline from components.
     */
    def simple[F[_]: EffectSystem, A, B](
      name: String,
      components: PipelineComponent[F, _, _, _]*
    ): Pipeline[types.Initialized, F, A] = {
      val metadata = pipeline.PipelineMetadata(
        id = types.PipelineId.generate(),
        name = name,
        description = s"Simple pipeline with ${components.length} components",
        componentCount = components.length
      )

      pipeline.Pipeline[types.Initialized, F, A](
        components.toList,
        metadata,
        new types.Initialized {}
      )
    }

    /**
     * Create an empty initialized pipeline.
     */
    def empty[F[_]: EffectSystem, A](name: String): Pipeline[types.Initialized, F, A] = {
      val metadata = pipeline.PipelineMetadata(
        id = types.PipelineId.generate(),
        name = name,
        description = "Empty pipeline"
      )

      pipeline.Pipeline[types.Initialized, F, A](
        Nil,
        metadata,
        new types.Initialized {}
      )
    }
  }

  // ===============================
  // COMPONENT FACTORIES
  // ===============================

  /**
   * Component factories for common component types.
   */
  object Components {

    /**
     * Create a source component.
     */
    def source[F[_], A](
      name: String,
      readFunction: () => F[A]
    )(implicit F: EffectSystem[F]): pipeline.SourceComponent[F, A] =
      new pipeline.SourceComponent[F, A] {
        def read()(implicit F: EffectSystem[F]): F[A] = readFunction()

        override def metadata: pipeline.ComponentMetadata =
          pipeline.ComponentMetadata(
            id = pipeline.ComponentId.generate(),
            name = name,
            description = s"Source component: $name",
            componentType = pipeline.ComponentType.Source
          )
      }

    /**
     * Create a transform component.
     */
    def transform[F[_], A, B](
      name: String,
      transformFunction: A => B
    ): pipeline.TransformComponent[F, A, B] =
      new pipeline.TransformComponent[F, A, B] {
        def transform(input: A): B = transformFunction(input)

        override def metadata: pipeline.ComponentMetadata =
          pipeline.ComponentMetadata(
            id = pipeline.ComponentId.generate(),
            name = name,
            description = s"Transform component: $name",
            componentType = pipeline.ComponentType.Transform
          )
      }

    /**
     * Create a sink component.
     */
    def sink[F[_], A](
      name: String,
      writeFunction: A => F[Unit]
    )(implicit F: EffectSystem[F]): pipeline.SinkComponent[F, A] =
      new pipeline.SinkComponent[F, A] {
        def write(data: A)(implicit F: EffectSystem[F]): F[Unit] = writeFunction(data)

        override def metadata: pipeline.ComponentMetadata =
          pipeline.ComponentMetadata(
            id = pipeline.ComponentId.generate(),
            name = name,
            description = s"Sink component: $name",
            componentType = pipeline.ComponentType.Sink
          )
      }

    /**
     * Create a filter component.
     */
    def filter[F[_], A](
      name: String,
      predicateFunction: A => Boolean
    ): pipeline.FilterComponent[F, A] =
      new pipeline.FilterComponent[F, A] {
        def predicate(input: A): Boolean = predicateFunction(input)

        override def metadata: pipeline.ComponentMetadata =
          pipeline.ComponentMetadata(
            id = pipeline.ComponentId.generate(),
            name = name,
            description = s"Filter component: $name",
            componentType = pipeline.ComponentType.Filter
          )
      }

    /**
     * Create a validation component.
     */
    def validation[F[_], A](
      name: String,
      rules: ValidationRule[A]*
    ): pipeline.ValidationComponent[F, A] =
      new pipeline.ValidationComponent[F, A](rules.toList) {
        override def metadata: pipeline.ComponentMetadata =
          pipeline
            .ComponentMetadata(
              id = pipeline.ComponentId.generate(),
              name = name,
              description = s"Validation component: $name",
              componentType = pipeline.ComponentType.Validate
            )
            .withConfig("ruleCount", rules.length.toString)
      }
  }

  // ===============================
  // EFFECT SYSTEM UTILITIES
  // ===============================

  /**
   * Utilities for working with effect systems.
   */
  object effects {

    /**
     * Lift a pure value to an effect.
     */
    def pure[F[_]: EffectSystem, A](value: A): F[A] =
      EffectSystem[F].pure(value)

    /**
     * Lift a side-effecting computation to an effect.
     */
    def delay[F[_]: EffectSystem, A](thunk: => A): F[A] =
      EffectSystem[F].delay(thunk)

    /**
     * Suspend an effect computation.
     */
    def suspend[F[_]: EffectSystem, A](fa: => F[A]): F[A] =
      EffectSystem[F].suspend(fa)

    /**
     * Raise an error in the effect context.
     */
    def raiseError[F[_]: EffectSystem, A](error: Throwable): F[A] =
      EffectSystem[F].raiseError(error)

    /**
     * Handle errors in an effect.
     */
    def handleError[F[_]: EffectSystem, A](
      fa: F[A]
    )(handler: Throwable => F[A]): F[A] =
      EffectSystem[F].handleError(fa)(handler)

    /**
     * Execute two effects in parallel.
     */
    def parallel[F[_]: EffectSystem, A, B](fa: F[A], fb: F[B]): F[(A, B)] =
      EffectSystem[F].parProduct(fa, fb)

    /**
     * Race two effects.
     */
    def race[F[_]: EffectSystem, A, B](fa: F[A], fb: F[B]): F[Either[A, B]] =
      EffectSystem[F].race(fa, fb)

    /**
     * Execute a list of effects in parallel.
     */
    def parTraverse[F[_]: EffectSystem, A, B](
      list: List[A]
    )(f: A => F[B]): F[List[B]] =
      EffectSystem[F].parTraverse(list)(f)

    /**
     * Execute a list of effects sequentially.
     */
    def traverse[F[_]: EffectSystem, A, B](
      list: List[A]
    )(f: A => F[B]): F[List[B]] =
      EffectSystem[F].traverse(list)(f)

    /**
     * Sleep for a duration.
     */
    def sleep[F[_]: EffectSystem](duration: FiniteDuration): F[Unit] =
      EffectSystem[F].sleep(duration)

    /**
     * Add a timeout to an effect.
     */
    def timeout[F[_]: EffectSystem, A](
      fa: F[A],
      duration: FiniteDuration
    ): F[A] =
      EffectSystem[F].timeout(fa, duration)
  }

  // ===============================
  // UTILITY FUNCTIONS
  // ===============================

  /**
   * Utility functions for common operations.
   */
  object utils {

    /**
     * Safe execution with Try conversion.
     */
    def trySafely[A](operation: => A): Either[Throwable, A] =
      Try(operation).toEither

    /**
     * Time an operation.
     */
    def timed[A](operation: => A): (A, FiniteDuration) = {
      val start    = System.nanoTime()
      val result   = operation
      val end      = System.nanoTime()
      val duration = FiniteDuration(end - start, scala.concurrent.duration.NANOSECONDS)
      (result, duration)
    }

    /**
     * Retry an operation with exponential backoff.
     */
    def retryWithBackoff[F[_]: EffectSystem, A](
      operation: F[A],
      maxRetries: Int,
      initialBackoff: FiniteDuration,
      backoffMultiplier: Double = 2.0
    ): F[A] = {
      def attempt(retriesLeft: Int, currentBackoff: FiniteDuration): F[A] =
        EffectSystem[F].handleError(operation) { error =>
          if (retriesLeft > 0) {
            EffectSystem[F].productR(EffectSystem[F].sleep(currentBackoff))(
              attempt(
                retriesLeft - 1,
                FiniteDuration(
                  (currentBackoff.toNanos * backoffMultiplier).toLong,
                  scala.concurrent.duration.NANOSECONDS
                )
              )
            )
          } else {
            EffectSystem[F].raiseError(error)
          }
        }
      attempt(maxRetries, initialBackoff)
    }

    /**
     * Convert validation result to effect.
     */
    def validationToEffect[F[_]: EffectSystem, A](
      validation: ValidationResult[A]
    ): F[A] = validation match {
      case cats.data.Validated.Valid(value) => EffectSystem[F].pure(value)
      case cats.data.Validated.Invalid(errors) =>
        EffectSystem[F].raiseError(
          new RuntimeException(s"Validation failed: ${errors.toList.mkString(", ")}")
        )
    }

    /**
     * Measure memory usage of an operation.
     */
    def withMemoryMeasurement[A](operation: => A): (A, Long) = {
      val runtime      = Runtime.getRuntime
      val beforeMemory = runtime.totalMemory() - runtime.freeMemory()
      val result       = operation
      val afterMemory  = runtime.totalMemory() - runtime.freeMemory()
      (result, afterMemory - beforeMemory)
    }
  }

  // ===============================
  // IMPLICIT CONVERSIONS
  // ===============================

  /**
   * Implicit conversions for convenient API usage.
   */
  implicit def stringToNonEmptyString(s: String): Option[Refined[String, NonEmpty[String]]] =
    constructors.nonEmptyString(s)

  implicit def intToBatchSize(i: Int): Option[BatchSize] =
    constructors.batchSize(i)

  implicit def longToRecordCount(l: Long): Option[RecordCount] =
    constructors.recordCount(l)

  implicit def refinedToValue[A, P](refined: Refined[A, P]): A =
    TypeClasses.TypeInstances.refinedToValue(refined)

  // Import syntax extensions
  import validation.ValidationSyntax
  import TypeClasses.{ BinaryDataOps, ConfigMapOps, ContractOps, DataEncoderOps }
  import pipeline.{ PipelineComponentOps, PipelineOps }

  // ===============================
  // COMMON IMPORTS
  // ===============================

  /**
   * Re-export commonly used types and functions for zero-import experience.
   */

  // Core types
  val PipelineId   = types.PipelineId
  val WorkflowId   = types.WorkflowId
  val DataSourceId = types.DataSourceId
  val BatchSize    = types.BatchSize
  val RecordCount  = types.RecordCount

  // Configuration types
  val EffectSystemChoice = types.EffectSystemChoice
  val RefreshType        = types.RefreshType
  val WorkflowType       = types.WorkflowType
  val Environment        = types.Environment
  val ContentType        = types.ContentType

  // Error types
  val ConfigurationError  = types.ConfigurationError
  val ValidationError     = validation.RequiredFieldError
  val DataProcessingError = types.DataProcessingError
  val ResourceError       = types.ResourceError
  val SystemError         = types.SystemError

  // Type classes
  val DataEncoder      = TypeClasses.DataEncoder
  val DataDecoder      = TypeClasses.DataDecoder
  val DataSerializer   = TypeClasses.DataSerializer
  val ConfigReader     = TypeClasses.ConfigReader
  val DataContract     = TypeClasses.DataContract
  val MetricsCollector = TypeClasses.MetricsCollector
  val Show             = TypeClasses.Show

  // Validation
  val ValidationEngine = validation.ValidationEngine
  val Validators       = validation.Validators

  // Pipeline
  val ComponentId     = pipeline.ComponentId
  val ExecutionId     = pipeline.ExecutionId
  val ComponentType   = pipeline.ComponentType
  val ExecutionStatus = pipeline.ExecutionStatus
  val PipelineStatus  = pipeline.PipelineStatus
  val PipelineBuilder = pipeline.PipelineBuilder
  val PipelineArrow   = pipeline.PipelineArrow

  // Refined types
  val Refined = types.Refined

  // Effect system
  val EffectSystem = EffectSystem
}
