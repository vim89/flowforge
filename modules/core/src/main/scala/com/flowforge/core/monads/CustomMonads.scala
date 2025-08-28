package com.flowforge.core.monads

import cats.Monad
import cats.syntax.all._
import com.flowforge.core.ValidationError
import com.flowforge.core.types._

import java.time.Instant
import scala.annotation.tailrec

/**
 * Custom domain monads for FlowForge
 *
 * This is where we build monads that speak our business language. Pipeline operations, data
 * transformations, quality checks - all expressed as beautiful, composable monadic operations.
 */
object CustomMonads {

  // ===== PIPELINE RESULT MONAD =====

  /**
   * The PipelineResult monad - our bread and butter for data processing
   *
   * This monad combines:
   *   - Success/failure tracking
   *   - Metrics collection
   *   - Quality scores
   *   - Processing metadata
   */
  case class PipelineResult[A](
    value: Either[FlowForgeError, A],
    metrics: ProcessingMetrics,
    qualityScore: Double,
    metadata: Map[String, String] = Map.empty
  ) {

    def map[B](f: A => B): PipelineResult[B] =
      PipelineResult(
        value.map(f),
        metrics,
        qualityScore,
        metadata
      )

    def flatMap[B](f: A => PipelineResult[B]): PipelineResult[B] =
      value match {
        case Left(error) =>
          PipelineResult(
            Left(error),
            metrics,
            qualityScore,
            metadata
          )
        case Right(a) =>
          val next = f(a)
          PipelineResult(
            next.value,
            metrics.combine(next.metrics),
            math.min(qualityScore, next.qualityScore), // Quality is the weakest link
            metadata ++ next.metadata
          )
      }

    def withMetrics(newMetrics: ProcessingMetrics): PipelineResult[A] =
      copy(metrics = metrics.combine(newMetrics))

    def withQuality(score: Double): PipelineResult[A] =
      copy(qualityScore = math.min(qualityScore, score))

    def withMetadata(key: String, value: String): PipelineResult[A] =
      copy(metadata = metadata + (key -> value))

    def isSuccess: Boolean = value.isRight
    def isFailure: Boolean = value.isLeft

    def get: A = value.getOrElse(throw new RuntimeException("PipelineResult failed"))
    def getOrElse[B >: A](default: => B): B = value.getOrElse(default)

    def toEither: Either[FlowForgeError, A] = value
    def toOption: Option[A]                 = value.toOption
  }

  /**
   * Processing metrics for pipeline operations
   */
  case class ProcessingMetrics(
    recordsProcessed: Long = 0,
    recordsFiltered: Long = 0,
    recordsRejected: Long = 0,
    bytesProcessed: Long = 0,
    processingTimeMs: Long = 0,
    memoryUsedMB: Double = 0.0,
    customMetrics: Map[String, Double] = Map.empty
  ) {

    def combine(other: ProcessingMetrics): ProcessingMetrics =
      ProcessingMetrics(
        recordsProcessed = recordsProcessed + other.recordsProcessed,
        recordsFiltered = recordsFiltered + other.recordsFiltered,
        recordsRejected = recordsRejected + other.recordsRejected,
        bytesProcessed = bytesProcessed + other.bytesProcessed,
        processingTimeMs = processingTimeMs + other.processingTimeMs,
        memoryUsedMB = math.max(memoryUsedMB, other.memoryUsedMB),
        customMetrics = customMetrics ++ other.customMetrics
      )

    def withCustomMetric(name: String, value: Double): ProcessingMetrics =
      copy(customMetrics = customMetrics + (name -> value))

    def totalRecords: Long = recordsProcessed + recordsFiltered + recordsRejected
    def successRate: Double =
      if (totalRecords > 0) recordsProcessed.toDouble / totalRecords else 1.0
    def throughputPerSecond: Double =
      if (processingTimeMs > 0) recordsProcessed.toDouble / (processingTimeMs / 1000.0) else 0.0
  }

  /**
   * Monad instance for PipelineResult
   */
  implicit val pipelineResultMonad: Monad[PipelineResult] = new Monad[PipelineResult] {
    def pure[A](a: A): PipelineResult[A] =
      PipelineResult.success(a)

    def flatMap[A, B](fa: PipelineResult[A])(f: A => PipelineResult[B]): PipelineResult[B] =
      fa.flatMap(f)

    @tailrec
    def tailRecM[A, B](a: A)(f: A => PipelineResult[Either[A, B]]): PipelineResult[B] =
      f(a) match {
        case PipelineResult(Left(error), metrics, quality, metadata) =>
          PipelineResult(Left(error), metrics, quality, metadata)
        case PipelineResult(Right(Left(nextA)), metrics, quality, metadata) =>
          tailRecM(nextA)(f) // Continue with accumulated metrics
        case PipelineResult(Right(Right(b)), metrics, quality, metadata) =>
          PipelineResult(Right(b), metrics, quality, metadata)
      }
  }

  /**
   * Smart constructors and utilities for PipelineResult
   */
  object PipelineResult {

    def success[A](value: A, metrics: ProcessingMetrics = ProcessingMetrics()): PipelineResult[A] =
      PipelineResult(Right(value), metrics, 1.0)

    def failure[A](
      error: FlowForgeError,
      metrics: ProcessingMetrics = ProcessingMetrics()
    ): PipelineResult[A] =
      PipelineResult(Left(error), metrics, 0.0)

    def fromEither[A](either: Either[FlowForgeError, A]): PipelineResult[A] =
      either.fold(failure(_), success(_))

    def fromOption[A](option: Option[A], error: => FlowForgeError): PipelineResult[A] =
      option.fold(failure[A](error))(success(_))

    def fromTry[A](tryValue: scala.util.Try[A]): PipelineResult[A] =
      tryValue.fold(
        ex => failure(ProcessingError("try_conversion", "exception", ex)),
        value => success(value)
      )

    def timed[A](computation: () => A): PipelineResult[A] = {
      val start = System.currentTimeMillis()
      try {
        val result = computation()
        val end    = System.currentTimeMillis()
        success(result, ProcessingMetrics(processingTimeMs = end - start))
      } catch {
        case ex: Exception =>
          val end = System.currentTimeMillis()
          failure(
            ProcessingError("timed_computation", "exception", ex),
            ProcessingMetrics(processingTimeMs = end - start)
          )
      }
    }

    def sequence[A](results: List[PipelineResult[A]]): PipelineResult[List[A]] =
      results.foldLeft(success(List.empty[A])) { (acc, result) =>
        (acc, result).mapN(_ :+ _)
      }

    def traverse[A, B](list: List[A])(f: A => PipelineResult[B]): PipelineResult[List[B]] =
      list.foldLeft(success(List.empty[B])) { (acc, item) =>
        (acc, f(item)).mapN(_ :+ _)
      }
  }

  // ===== DATA QUALITY MONAD =====

  /**
   * DataQuality monad for tracking data quality throughout the pipeline
   */
  case class DataQuality[A](
    data: A,
    checks: List[QualityCheck],
    overallScore: Double,
    issues: List[QualityIssue] = List.empty
  ) {

    def map[B](f: A => B): DataQuality[B] =
      copy(data = f(data))

    def flatMap[B](f: A => DataQuality[B]): DataQuality[B] = {
      val next = f(data)
      DataQuality(
        next.data,
        checks ++ next.checks,
        math.min(overallScore, next.overallScore),
        issues ++ next.issues
      )
    }

    def addCheck(check: QualityCheck): DataQuality[A] =
      copy(checks = checks :+ check, overallScore = math.min(overallScore, check.score))

    def addIssue(issue: QualityIssue): DataQuality[A] =
      copy(issues = issues :+ issue)

    def isAcceptable(threshold: Double = 0.8): Boolean = overallScore >= threshold
    def hasIssues: Boolean                             = issues.nonEmpty

    def toPipelineResult: PipelineResult[A] =
      if (isAcceptable()) {
        PipelineResult.success(data).withQuality(overallScore)
      } else {
        PipelineResult.failure(
          QualityError(
            "quality_threshold",
            "below_threshold",
            new RuntimeException(s"Quality score $overallScore below threshold")
          )
        )
      }
  }

  case class QualityCheck(
    name: String,
    description: String,
    score: Double,
    metadata: Map[String, String] = Map.empty
  )

  case class QualityIssue(
    severity: IssueSeverity,
    message: String,
    field: Option[String] = None,
    recordId: Option[String] = None
  )

  sealed trait IssueSeverity
  case object Critical extends IssueSeverity
  case object Warning  extends IssueSeverity
  case object Info     extends IssueSeverity

  /**
   * Monad instance for DataQuality
   */
  implicit val dataQualityMonad: Monad[DataQuality] = new Monad[DataQuality] {
    def pure[A](a: A): DataQuality[A] =
      DataQuality(a, List.empty, 1.0)

    def flatMap[A, B](fa: DataQuality[A])(f: A => DataQuality[B]): DataQuality[B] =
      fa.flatMap(f)

    def tailRecM[A, B](a: A)(f: A => DataQuality[Either[A, B]]): DataQuality[B] = {
      @tailrec
      def loop(
        current: A,
        checks: List[QualityCheck],
        score: Double,
        issues: List[QualityIssue]
      ): DataQuality[B] =
        f(current) match {
          case DataQuality(Left(nextA), newChecks, newScore, newIssues) =>
            loop(nextA, checks ++ newChecks, math.min(score, newScore), issues ++ newIssues)
          case DataQuality(Right(b), newChecks, newScore, newIssues) =>
            DataQuality(b, checks ++ newChecks, math.min(score, newScore), issues ++ newIssues)
        }
      loop(a, List.empty, 1.0, List.empty)
    }
  }

  // ===== TRANSFORMATION MONAD =====

  /**
   * Transformation monad for tracking data transformations
   */
  case class Transformation[A](
    data: A,
    steps: List[TransformationStep],
    lineage: DataLineage
  ) {

    def map[B](f: A => B): Transformation[B] = {
      val step = TransformationStep("map", "Applied function transformation", Instant.now())
      Transformation(f(data), steps :+ step, lineage.addStep(step))
    }

    def flatMap[B](f: A => Transformation[B]): Transformation[B] = {
      val step = TransformationStep("flatMap", "Applied monadic transformation", Instant.now())
      val next = f(data)
      Transformation(
        next.data,
        steps ++ (step :: next.steps),
        lineage.combine(next.lineage).addStep(step)
      )
    }

    def withStep(step: TransformationStep): Transformation[A] =
      copy(steps = steps :+ step, lineage = lineage.addStep(step))

    def transform[B](f: A => B, description: String): Transformation[B] = {
      val step = TransformationStep("transform", description, Instant.now())
      Transformation(f(data), steps :+ step, lineage.addStep(step))
    }
  }

  case class TransformationStep(
    operation: String,
    description: String,
    timestamp: Instant,
    metadata: Map[String, String] = Map.empty
  )

  case class DataLineage(
    source: String,
    steps: List[TransformationStep],
    createdAt: Instant,
    lastModified: Instant
  ) {
    def addStep(step: TransformationStep): DataLineage =
      copy(steps = steps :+ step, lastModified = step.timestamp)

    def combine(other: DataLineage): DataLineage =
      copy(
        steps = steps ++ other.steps,
        lastModified = List(lastModified, other.lastModified).max
      )
  }

  /**
   * Monad instance for Transformation
   */
  implicit val transformationMonad: Monad[Transformation] = new Monad[Transformation] {
    def pure[A](a: A): Transformation[A] = {
      val now = Instant.now()
      Transformation(a, List.empty, DataLineage("pure", List.empty, now, now))
    }

    def flatMap[A, B](fa: Transformation[A])(f: A => Transformation[B]): Transformation[B] =
      fa.flatMap(f)

    def tailRecM[A, B](a: A)(f: A => Transformation[Either[A, B]]): Transformation[B] = {
      @tailrec
      def loop(
        current: A,
        steps: List[TransformationStep],
        lineage: DataLineage
      ): Transformation[B] =
        f(current) match {
          case Transformation(Left(nextA), newSteps, newLineage) =>
            loop(nextA, steps ++ newSteps, lineage.combine(newLineage))
          case Transformation(Right(b), newSteps, newLineage) =>
            Transformation(b, steps ++ newSteps, lineage.combine(newLineage))
        }
      val now = Instant.now()
      loop(a, List.empty, DataLineage("tailRecM", List.empty, now, now))
    }
  }

  // ===== RESOURCE MONAD =====

  /**
   * Resource monad for safe resource management
   */
  case class ResourceOp[A](
    acquire: () => A,
    release: A => Unit,
    acquired: Option[A] = None
  ) {

    def map[B](f: A => B): ResourceOp[B] =
      ResourceOp(
        acquire = () => f(use()),
        release = _ => cleanup(),
        acquired = None
      )

    def flatMap[B](f: A => ResourceOp[B]): ResourceOp[B] =
      ResourceOp(
        acquire = () => {
          val a   = use()
          val bOp = f(a)
          bOp.use()
        },
        release = _ => cleanup(),
        acquired = None
      )

    def use(): A = acquired.getOrElse {
      val resource = acquire()
      // Note: In real implementation, we'd track this properly
      resource
    }

    def cleanup(): Unit = acquired.foreach(release)

    def bracket[B](use: A => B): B = {
      val resource = acquire()
      try
        use(resource)
      finally
        release(resource)
    }
  }

  /**
   * Monad instance for ResourceOp
   */
  implicit val resourceOpMonad: Monad[ResourceOp] = new Monad[ResourceOp] {
    def pure[A](a: A): ResourceOp[A] =
      ResourceOp(() => a, _ => (), Some(a))

    def flatMap[A, B](fa: ResourceOp[A])(f: A => ResourceOp[B]): ResourceOp[B] =
      fa.flatMap(f)

    def tailRecM[A, B](a: A)(f: A => ResourceOp[Either[A, B]]): ResourceOp[B] =
      ResourceOp(
        acquire = () => {
          @tailrec
          def loop(current: A): B =
            f(current).use() match {
              case Left(nextA) => loop(nextA)
              case Right(b)    => b
            }
          loop(a)
        },
        release = _ => (), // Simplified cleanup
        acquired = None
      )
  }

  // ===== PIPELINE COMPOSITION DSL =====

  /**
   * Fluent DSL for composing pipeline operations
   */
  object PipelineDSL {

    /**
     * Creates a pipeline operation from a function
     */
    def operation[A, B](f: A => B, description: String = "operation"): A => PipelineResult[B] = {
      input =>
        PipelineResult
          .timed(() => f(input))
          .withMetadata("operation", description)
    }

    /**
     * Creates a validation step
     */
    def validate[A](predicate: A => Boolean, error: String): A => PipelineResult[A] = { input =>
      if (predicate(input)) {
        PipelineResult
          .success(input)
          .withMetadata("validation", "passed")
      } else {
        PipelineResult.failure(ValidationError("input", input.toString, error))
      }
    }

    /**
     * Creates a quality check step
     */
    def qualityCheck[A](checker: A => QualityCheck): A => DataQuality[A] = { input =>
      val check = checker(input)
      DataQuality(input, List(check), check.score)
    }

    /**
     * Creates a transformation step with lineage
     */
    def transform[A, B](f: A => B, description: String): A => Transformation[B] = { input =>
      val now     = Instant.now()
      val step    = TransformationStep("transform", description, now)
      val lineage = DataLineage("transform", List(step), now, now)
      Transformation(f(input), List(step), lineage)
    }

    /**
     * Combines multiple pipeline operations
     */
    def pipeline[A, B, C](
      step1: A => PipelineResult[B],
      step2: B => PipelineResult[C]
    ): A => PipelineResult[C] = { input =>
      step1(input).flatMap(step2)
    }

    /**
     * Parallel execution of multiple operations
     */
    def parallel[A, B, C](
      op1: A => PipelineResult[B],
      op2: A => PipelineResult[C]
    ): A => PipelineResult[(B, C)] = { input =>
      (op1(input), op2(input)).mapN((_, _))
    }
  }

  // ===== SYNTAX EXTENSIONS =====

  /**
   * Syntax extensions for our custom monads
   */
  object CustomMonadSyntax {

    implicit class PipelineResultOps[A](result: PipelineResult[A]) {
      def >>=[B](f: A => PipelineResult[B]): PipelineResult[B] = result.flatMap(f)
      def |>[B](f: A => B): PipelineResult[B]                  = result.map(f)
      def withTimer: PipelineResult[A] = result // Simplified - would add timing
      def logResult(logger: String => Unit): PipelineResult[A] = {
        result.value.fold(
          error => logger(s"Pipeline failed: ${error.message}"),
          value => logger(s"Pipeline succeeded: $value")
        )
        result
      }
    }

    implicit class DataQualityOps[A](quality: DataQuality[A]) {
      def ensure(predicate: A => Boolean, issue: QualityIssue): DataQuality[A] =
        if (predicate(quality.data)) quality
        else quality.addIssue(issue)

      def validateWith(validator: A => List[QualityIssue]): DataQuality[A] = {
        val issues = validator(quality.data)
        issues.foldLeft(quality)(_.addIssue(_))
      }
    }

    implicit class TransformationOps[A](trans: Transformation[A]) {
      def ~>[B](f: A => B, description: String): Transformation[B] =
        trans.transform(f, description)

      def stepwise[B](f: A => Transformation[B]): Transformation[B] =
        trans.flatMap(f)

      def trace: Transformation[A] = {
        trans.steps.foreach(step =>
          println(s"${step.timestamp}: ${step.operation} - ${step.description}")
        )
        trans
      }
    }
  }
}

/**
 * Example usage of custom monads
 */
object CustomMonadExamples {
  import CustomMonads.CustomMonadSyntax._
  import CustomMonads.PipelineDSL._
  import CustomMonads._

  /**
   * Example pipeline using PipelineResult monad
   */
  def examplePipeline(input: String): PipelineResult[Int] =
    for {
      cleaned   <- operation((s: String) => s.trim, "trim_input")(input)
      validated <- validate((s: String) => s.nonEmpty, "must_not_be_empty")(cleaned)
      parsed    <- operation((s: String) => s.toInt, "parse_to_int")(validated)
      doubled   <- operation((i: Int) => i * 2, "double_value")(parsed)
    } yield doubled

  /**
   * Example using DataQuality monad
   */
  def exampleQualityCheck(data: List[String]): DataQuality[List[String]] =
    for {
      nonNull <- DataQuality(data, List.empty, 1.0)
        .ensure(_.forall(_ != null), QualityIssue(Critical, "Null values found"))

      nonEmpty <- nonNull
        .ensure(_.nonEmpty, QualityIssue(Warning, "Empty dataset"))

      validated <- nonEmpty.validateWith { list =>
        if (list.length < 10) {
          List(QualityIssue(Warning, "Dataset smaller than expected"))
        } else List.empty
      }
    } yield validated.data

  /**
   * Example using Transformation monad
   */
  def exampleTransformation(numbers: List[Int]): Transformation[Double] =
    for {
      filtered <- transform((list: List[Int]) => list.filter(_ > 0), "filter_positive")(numbers)
      doubled  <- filtered ~> (_ map (_ * 2), "double_values")
      average  <- doubled ~> (list => list.sum.toDouble / list.length, "calculate_average")
    } yield average
}
