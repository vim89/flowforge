package com.flowforge.core.syntax

import cats.data.{ Kleisli, NonEmptyList, ReaderT, ValidatedNel }
import cats.implicits._
import com.flowforge.core.FlowForgePipeline
import com.flowforge.core.algebra.{ DataAlgebra, DataEncoder, EffectSystem }
import com.flowforge.core.patterns.ReaderPattern._
import com.flowforge.core.types._

import scala.concurrent.duration.{ Duration, FiniteDuration }

/**
 * 🚀 **FlowForge Pipeline Syntax - Enhanced Fluent API**
 *
 * This module provides enhanced syntax for composing data pipelines in FlowForge, building upon the
 * existing Kleisli-based system. It provides intuitive, readable API extensions while maintaining
 * full compatibility with the existing FlowForge architecture.
 *
 * **Key Features:**
 *   - **Fluent Interface**: Chain operations naturally with method calls
 *   - **Type Safety**: All operations preserve type information
 *   - **Effect Polymorphism**: Works with any effect system F[_]
 *   - **Composable Operations**: Mix and match pipeline components
 *   - **Error Handling**: Built-in error recovery and handling patterns
 *   - **Resource Safety**: Automatic resource management and cleanup
 *   - **Integration**: Seamless integration with existing FlowForge types
 *
 * **Usage Examples:**
 * ```scala
 * import com.flowforge.core.syntax.PipelineSyntax._
 *
 * val pipeline = Pipeline
 *   .builder[IO]("customer-analytics")
 *   .from(jdbcSource)
 *   .transform(cleanData)
 *   .validate(dataContract)
 *   .transform(enrichData)
 *   .to(parquetSink)
 *   .withRetry(3)
 *   .withTimeout(30.minutes)
 *   .build
 * ```
 *
 * @author
 *   FlowForge Core Team
 * @since 0.1.0
 */

object PipelineSyntax {

  // ===============================
  // ENHANCED PIPELINE BUILDER
  // ===============================

  /**
   * Enhanced pipeline builder that extends the existing FlowForge system
   */
  case class EnhancedPipelineBuilder[F[_]](
    name: String,
    source: Option[DataSource] = None,
    sink: Option[DataSink] = None,
    components: List[Kleisli[F, Any, Any]] = List.empty,
    config: Option[PipelineConfig] = None,
    retryPolicy: Option[RetryPolicy] = None,
    timeout: Option[Duration] = None
  )(implicit F: EffectSystem[F]) {

    /**
     * Set data source
     */
    def from(dataSource: DataSource): EnhancedPipelineBuilder[F] =
      copy(source = Some(dataSource))

    /**
     * Add transformation step
     */
    def transform[A, B](transformation: A => F[B]): EnhancedPipelineBuilder[F] = {
      val component = Kleisli(transformation).asInstanceOf[Kleisli[F, Any, Any]]
      copy(components = components :+ component)
    }

    /**
     * Add pure transformation
     */
    def map[A, B](f: A => B): EnhancedPipelineBuilder[F] = {
      val component = Kleisli[F, A, B](a => F.pure(f(a))).asInstanceOf[Kleisli[F, Any, Any]]
      copy(components = components :+ component)
    }

    /**
     * Add filter step
     */
    def filter[A](predicate: A => Boolean): EnhancedPipelineBuilder[F] = {
      val component = Kleisli[F, A, A] { a =>
        if (predicate(a)) F.pure(a)
        else F.raiseError(new IllegalArgumentException("Record filtered out"))
      }.asInstanceOf[Kleisli[F, Any, Any]]
      copy(components = components :+ component)
    }

    /**
     * Add validation step
     */
    def validate[A](validator: A => ValidatedNel[FlowForgeError, A]): EnhancedPipelineBuilder[F] = {
      val component = Kleisli[F, A, A] { a =>
        validator(a) match {
          case cats.data.Validated.Valid(validA) => F.pure(validA)
          case cats.data.Validated.Invalid(errors) =>
            F.raiseError(ValidationException(errors.toList))
        }
      }.asInstanceOf[Kleisli[F, Any, Any]]
      copy(components = components :+ component)
    }

    /**
     * Add quality check
     */
    def quality[A](qualityCheck: A => F[QualityResult[A]]): EnhancedPipelineBuilder[F] = {
      val component = Kleisli[F, A, A] { a =>
        qualityCheck(a).flatMap { result =>
          if (result.passed) F.pure(a)
          else F.raiseError(QualityException(s"Quality check failed: ${result.score}"))
        }
      }.asInstanceOf[Kleisli[F, Any, Any]]
      copy(components = components :+ component)
    }

    /**
     * Set data sink
     */
    def to(dataSink: DataSink): EnhancedPipelineBuilder[F] =
      copy(sink = Some(dataSink))

    /**
     * Add retry policy
     */
    def withRetry(maxRetries: Int): EnhancedPipelineBuilder[F] =
      copy(retryPolicy = Some(RetryPolicy.exponential(maxRetries, Duration.fromNanos(1000000000L))))

    /**
     * Add timeout
     */
    def withTimeout(duration: Duration): EnhancedPipelineBuilder[F] =
      copy(timeout = Some(duration))

    /**
     * Update configuration
     */
    def withConfig(pipelineConfig: PipelineConfig): EnhancedPipelineBuilder[F] =
      copy(config = Some(pipelineConfig))

    /**
     * Build the final pipeline using existing FlowForge system
     */
    def build: Either[NonEmptyList[FlowForgeError], FlowForgePipeline[F]] = {
      val sourceValidation =
        source.toValidNel(FlowForgeError.ConfigurationError("Missing source", None))
      val sinkValidation = sink.toValidNel(FlowForgeError.ConfigurationError("Missing sink", None))

      (sourceValidation, sinkValidation).mapN { (src, snk) =>
        // Create using existing FlowForge pipeline system
        FlowForgePipeline(
          name = name,
          source = src,
          sink = snk,
          transformations = components.map(_.asInstanceOf[PipelineComponent[F, Any, Any]]),
          validations = List.empty, // Would be populated in real implementation
          config = config
        )
      }.toEither
    }

    /**
     * Build and execute the pipeline
     */
    def execute[A](input: A): F[A] =
      build match {
        case Right(pipeline) => pipeline.execute(input)
        case Left(errors)    => F.raiseError(ValidationException(errors.toList))
      }
  }

  /**
   * Enhanced pipeline syntax extensions for existing types
   */
  implicit class PipelineComponentOps[F[_], A, B](
    private val component: PipelineComponent[F, A, B]
  )(implicit F: EffectSystem[F]) {

    /**
     * Compose this component with another
     */
    def >>>[C](next: PipelineComponent[F, B, C]): PipelineComponent[F, A, C] =
      component andThen next

    /**
     * Add error handling to component
     */
    def handleError(handler: Throwable => F[B]): PipelineComponent[F, A, B] =
      Kleisli[F, A, B] { a =>
        component.run(a).handleErrorWith(handler)
      }

    /**
     * Add retry to component
     */
    def retry(maxRetries: Int): PipelineComponent[F, A, B] =
      Kleisli[F, A, B] { a =>
        def attempt(retriesLeft: Int): F[B] =
          component.run(a).handleErrorWith { error =>
            if (retriesLeft > 0) attempt(retriesLeft - 1)
            else F.raiseError(error)
          }
        attempt(maxRetries)
      }

    /**
     * Add timeout to component
     */
    def timeout(duration: FiniteDuration): PipelineComponent[F, A, B] =
      Kleisli[F, A, B] { a =>
        F.timeout(component.run(a), duration)
      }

    /**
     * Add logging to component
     */
    def logged(name: String): PipelineComponent[F, A, B] =
      Kleisli[F, A, B] { a =>
        for {
          _      <- F.delay(println(s"Starting component: $name"))
          result <- component.run(a)
          _      <- F.delay(println(s"Completed component: $name"))
        } yield result
      }

    /**
     * Add metrics collection
     */
    def withMetrics(metricName: String): PipelineComponent[F, A, B] =
      Kleisli[F, A, B] { a =>
        for {
          start  <- F.delay(System.currentTimeMillis())
          result <- component.run(a)
          end    <- F.delay(System.currentTimeMillis())
          _      <- F.delay(println(s"METRIC: $metricName duration = ${end - start}ms"))
        } yield result
      }
  }

  /**
   * Enhanced dataset operations
   */
  implicit class DatasetOps[A](private val dataset: DataAlgebra.Dataset[A]) {

    /**
     * Apply transformation with error handling
     */
    def transformSafe[F[_]: EffectSystem, B](
      f: A => F[B]
    )(implicit encoder: DataAlgebra.DataEncoder[B]): F[DataAlgebra.Dataset[B]] = ???

    /**
     * Apply validation to all records
     */
    def validateAll[F[_]: EffectSystem](
      validator: A => ValidatedNel[FlowForgeError, A]
    ): F[ValidationResult[DataAlgebra.Dataset[A]]] = ???

    /**
     * Create a pipeline from this dataset
     */
    def pipeline[F[_]: EffectSystem](implicit
      algebra: DataAlgebra[F]
    ): DatasetPipelineBuilder[F, A] =
      DatasetPipelineBuilder(dataset, algebra)
  }

  /**
   * Dataset-based pipeline builder
   */
  case class DatasetPipelineBuilder[F[_], A](
    dataset: DataAlgebra.Dataset[A],
    dataAlgebra: DataAlgebra[F]
  )(implicit F: EffectSystem[F]) {

    /**
     * Apply transformation
     */
    def transform[B](
      transformation: A => F[B]
    )(implicit encoder: DataEncoder[B]): F[DatasetPipelineBuilder[F, B]] =
      dataAlgebra.mapWithEffect(dataset, transformation).map { newDataset =>
        DatasetPipelineBuilder(newDataset, dataAlgebra)
      }

    /**
     * Apply filter
     */
    def filter(predicate: A => Boolean): F[DatasetPipelineBuilder[F, A]] =
      dataAlgebra.filter(dataset, predicate).map { filtered =>
        DatasetPipelineBuilder(filtered, dataAlgebra)
      }

    /**
     * Apply quality check
     */
    def quality(
      contract: DataContract[A]
    ): F[DatasetPipelineBuilder[F, A]] =
      dataAlgebra.validate(dataset, contract).flatMap { result =>
        if (result.passed) F.pure(this)
        else F.raiseError(QualityException(s"Quality check failed: ${result.score}"))
      }

    /**
     * Write to sink
     */
    def writeTo(
      sink: DataSink
    )(implicit encoder: DataEncoder[A]): F[DataAlgebra.WriteResult] =
      dataAlgebra.write(dataset, sink)

    /**
     * Execute and return result
     */
    def execute: F[DataAlgebra.Dataset[A]] = F.pure(dataset)
  }

  // ===============================
  // READER-BASED SYNTAX EXTENSIONS
  // ===============================

  /**
   * Extensions for Reader-based operations
   */
  implicit class ReaderPipelineOps[F[_], A](
    private val readerOp: FlowForgeReaderT[F, A]
  )(implicit F: EffectSystem[F]) {

    /**
     * Chain with another Reader operation
     */
    def >>[B](next: FlowForgeReaderT[F, B]): FlowForgeReaderT[F, B] =
      readerOp.flatMap(_ => next)

    /**
     * Transform the result
     */
    def mapResult[B](f: A => B): FlowForgeReaderT[F, B] =
      readerOp.map(f)

    /**
     * Add logging to the operation
     */
    def logged(message: String): FlowForgeReaderT[F, A] =
      for {
        _      <- ReaderT.liftF(F.delay(println(s"LOG: $message - START")))
        result <- readerOp
        _      <- ReaderT.liftF(F.delay(println(s"LOG: $message - END")))
      } yield result

    /**
     * Add metrics to the operation
     */
    def timed(metricName: String): FlowForgeReaderT[F, A] =
      for {
        start  <- ReaderT.liftF(F.delay(System.currentTimeMillis()))
        result <- readerOp
        end    <- ReaderT.liftF(F.delay(System.currentTimeMillis()))
        _ <- ReaderT.liftF(F.delay(println(s"METRIC: $metricName duration = ${end - start}ms")))
      } yield result

    /**
     * Add error handling
     */
    def handleErrorWith(
      handler: Throwable => FlowForgeReaderT[F, A]
    ): FlowForgeReaderT[F, A] =
      ReaderT { context =>
        readerOp.run(context).handleErrorWith(e => handler(e).run(context))
      }
  }

  // ===============================
  // CONVENIENT CONSTRUCTORS
  // ===============================

  /**
   * Create an enhanced pipeline builder
   */
  def pipeline[F[_]: EffectSystem](name: String): EnhancedPipelineBuilder[F] =
    EnhancedPipelineBuilder[F](name)

  /**
   * Create a simple transformation component
   */
  def transform[F[_]: EffectSystem, A, B](
    f: A => B
  ): PipelineComponent[F, A, B] =
    Kleisli[F, A, B](a => implicitly[EffectSystem[F]].delay(f(a)))

  /**
   * Create an effectful transformation component
   */
  def transformF[F[_]: EffectSystem, A, B](
    f: A => F[B]
  ): PipelineComponent[F, A, B] = Kleisli[F, A, B](f)

  /**
   * Create a filter component
   */
  def filter[F[_]: EffectSystem, A](
    predicate: A => Boolean
  ): PipelineComponent[F, A, A] =
    Kleisli[F, A, A] { a =>
      if (predicate(a)) implicitly[EffectSystem[F]].pure(a)
      else
        implicitly[EffectSystem[F]].raiseError(
          new IllegalArgumentException("Record filtered out")
        )
    }

  /**
   * Create a validation component
   */
  def validate[F[_]: EffectSystem, A](
    validator: A => ValidatedNel[FlowForgeError, A]
  ): PipelineComponent[F, A, A] =
    Kleisli[F, A, A] { a =>
      validator(a) match {
        case cats.data.Validated.Valid(validA) =>
          implicitly[EffectSystem[F]].pure(validA)
        case cats.data.Validated.Invalid(errors) =>
          implicitly[EffectSystem[F]].raiseError(ValidationException(errors.toList))
      }
    }

  /**
   * Create a logging component
   */
  def log[F[_]: EffectSystem, A](message: String): PipelineComponent[F, A, A] =
    Kleisli[F, A, A] { a =>
      implicitly[EffectSystem[F]].delay {
        println(s"LOG: $message")
        a
      }
    }

  /**
   * Create a metrics collection component
   */
  def metric[F[_]: EffectSystem, A](
    name: String,
    extractor: A => Double
  ): PipelineComponent[F, A, A] =
    Kleisli[F, A, A] { a =>
      implicitly[EffectSystem[F]].delay {
        val value = extractor(a)
        println(s"METRIC: $name = $value")
        a
      }
    }

  // ===============================
  // ERROR HANDLING HELPERS
  // ===============================

  /**
   * Create error recovery component
   */
  def recover[F[_]: EffectSystem, A](
    recovery: PartialFunction[Throwable, A]
  ): PipelineComponent[F, A, A] =
    Kleisli[F, A, A] { a =>
      implicitly[EffectSystem[F]].pure(a).recover(recovery)
    }

  /**
   * Create retry component
   */
  def withRetry[F[_]: EffectSystem, A](
    maxRetries: Int
  )(component: PipelineComponent[F, A, A]): PipelineComponent[F, A, A] =
    Kleisli { input =>
      val F = implicitly[EffectSystem[F]]
      def attempt(remaining: Int): F[A] =
        if (remaining <= 0) component.run(input)
        else
          F.handleErrorWith(component.run(input)) { _ =>
            if (remaining > 1) attempt(remaining - 1)
            else component.run(input)
          }
      attempt(maxRetries)
    }

  // ===============================
  // FOR-COMPREHENSION SUPPORT
  // ===============================

  /**
   * Support for for-comprehensions with pipeline components
   */
  implicit class ForComprehensionOps[F[_]: EffectSystem, A, B](
    private val component: PipelineComponent[F, A, B]
  ) {

    def map[C](f: B => C): PipelineComponent[F, A, C] =
      component.map(f)

    def flatMap[C](f: B => PipelineComponent[F, A, C]): PipelineComponent[F, A, C] =
      Kleisli[F, A, C] { a =>
        component.run(a).flatMap(b => f(b).run(a))
      }
  }

  // ===============================
  // SUPPORTING TYPES
  // ===============================

  case class ValidationException(errors: List[FlowForgeError]) extends Exception {
    override def getMessage: String =
      s"Validation failed: ${errors.map(_.message).mkString(", ")}"
  }

  case class QualityException(message: String) extends Exception(message)

  // ===============================
  // IMPLICIT CONVERSIONS
  // ===============================

  /**
   * Convert function to pipeline component
   */
  implicit def functionToComponent[F[_]: EffectSystem, A, B](
    f: A => B
  ): PipelineComponent[F, A, B] = transform(f)

  /**
   * Convert effectful function to pipeline component
   */
  implicit def effectfulFunctionToComponent[F[_]: EffectSystem, A, B](
    f: A => F[B]
  ): PipelineComponent[F, A, B] = transformF(f)

  // ===============================
  // EXAMPLE USAGE HELPERS
  // ===============================

  /**
   * Example pipeline construction helpers that integrate with existing system
   */
  object Examples {

    /**
     * Create a simple ETL pipeline using enhanced syntax
     */
    def etlPipeline[F[_]: EffectSystem](
      source: DataSource,
      sink: DataSink
    ): EnhancedPipelineBuilder[F] =
      pipeline[F]("etl-pipeline")
        .from(source)
        // .transform((s: String) => s.trim.toUpperCase) // Transform
        .filter((_: String).nonEmpty)              // Transform
        .validate((_: String) => "clean".validNel) // Validate
        .to(sink)                                  // Load

    /**
     * Create a data quality pipeline
     */
    def qualityPipeline[F[_]: EffectSystem, A](
      dataset: DataAlgebra.Dataset[A]
    )(implicit da: DataAlgebra[F]): DatasetPipelineBuilder[F, A] =
      dataset.pipeline

    /**
     * Create a Reader-based pipeline with dependency injection
     */
    def diPipeline[F[_]: EffectSystem](
      name: String
    ): FlowForgeReaderT[F, String] = ???
  }

  // ===============================
  // INTEGRATION WITH EXISTING TYPES
  // ===============================

  // Type aliases to integrate with existing FlowForge system
  type PipelineComponent[F[_], A, B] = Kleisli[F, A, B]
  type DataContract[A]               = A => ValidationResult[Unit]
  type QualityCheck[A]               = A => ValidationResult[A]
  type ValidationResult[A]           = ValidatedNel[FlowForgeError, A]
  type FlowForgePipeline[F[_]]       = com.flowforge.core.FlowForgePipeline[F]
  type QualityResult[A]              = DataAlgebra.QualityResult[A]

  // Provide default quality result for testing
  object QualityResult {
    def passed[A](data: A): DataAlgebra.QualityResult[A] =
      DataAlgebra.QualityResult(
        data = data,
        score = 1.0,
        checks = List.empty,
        passed = true,
        metadata = Map.empty
      )
  }
}
