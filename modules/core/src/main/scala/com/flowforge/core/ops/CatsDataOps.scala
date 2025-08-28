package com.flowforge.core.ops

import cats.data._
import cats.syntax.all._
import cats.{Applicative, Eval, Functor, Monad}
import com.flowforge.core.algebra.TypeClasses.ConfigReader.PipelineConfig
import com.flowforge.core.validation.ValidationResult
import com.flowforge.core.types._

import java.time.Instant

/**
 * Complete Cats Data manipulation utilities
 *
 * This is the cats data playground - Reader for dependency injection, Writer for logging, State
 * for... well, state, Eval for lazy evaluation, and Validated for bulletproof validation.
 * Everything you need for serious functional programming.
 */
object CatsDataOperations {

  // ===== READER MONAD OPERATIONS =====

  /**
   * Pipeline configuration reader
   */
  type ConfigReader[A] = Reader[PipelineConfig, A]

  /**
   * Creates a config reader
   */
  def config[A](f: PipelineConfig => A): ConfigReader[A] = Reader(f)

  /**
   * Common configuration readers
   */
  object ConfigReaders {
    val parallelism: ConfigReader[Int]       = config(_.parallelism)
    val batchSize: ConfigReader[Int]         = config(_.batchSize)
    val timeoutMs: ConfigReader[Long]        = config(_.timeoutMs)
    val enableLogging: ConfigReader[Boolean] = config(_.enableLogging)

    def sourceConfig(name: String): ConfigReader[Option[SourceConfig]] =
      config(_.sources.get(name))

    def targetConfig(name: String): ConfigReader[Option[TargetConfig]] =
      config(_.targets.get(name))
  }

  /**
   * Combines multiple readers applicatively
   */
  def combineReaders[A, B, C](
    ra: ConfigReader[A],
    rb: ConfigReader[B]
  )(f: (A, B) => C): ConfigReader[C] =
    (ra, rb).mapN(f)

  /**
   * ReaderT for effect + configuration
   */
  type ConfigReaderT[F[_], A] = ReaderT[F, PipelineConfig, A]

  /**
   * Creates an effectful config reader
   */
  def configF[F[_], A](f: PipelineConfig => F[A]): ConfigReaderT[F, A] =
    ReaderT(f)

  /**
   * Common effectful readers
   */
  object ConfigReaderTs {
    def loadSource[F[_]: Monad](name: String): ConfigReaderT[F, F[DataSource]] =
      configF(config => Monad[F].pure(??? /* actual loading logic */ ))

    def validateConfig[F[_]: Applicative]: ConfigReaderT[F, ValidationResult[Unit]] =
      configF(config => Applicative[F].pure(().valid))
  }

  // ===== WRITER MONAD OPERATIONS =====

  /**
   * Pipeline logging with Writer
   */
  type PipelineLog  = List[LogEntry]
  type LogWriter[A] = Writer[PipelineLog, A]

  /**
   * Log entry structure
   */
  case class LogEntry(
    timestamp: Instant,
    level: LogLevel,
    message: String,
    metadata: Map[String, String] = Map.empty
  )

  sealed trait LogLevel
  case object Debug extends LogLevel
  case object Info  extends LogLevel
  case object Warn  extends LogLevel
  case object Error extends LogLevel

  /**
   * Creates a log writer
   */
  def logged[A](value: A, entry: LogEntry): LogWriter[A] =
    Writer(List(entry), value)

  /**
   * Convenience logging functions
   */
  object LogWriters {
    def debug[A](message: String, value: A): LogWriter[A] =
      logged(value, LogEntry(Instant.now(), Debug, message))

    def info[A](message: String, value: A): LogWriter[A] =
      logged(value, LogEntry(Instant.now(), Info, message))

    def warn[A](message: String, value: A): LogWriter[A] =
      logged(value, LogEntry(Instant.now(), Warn, message))

    def error[A](message: String, value: A): LogWriter[A] =
      logged(value, LogEntry(Instant.now(), Error, message))

    def pure[A](value: A): LogWriter[A] = Writer.value(value)

    def tell(entry: LogEntry): LogWriter[Unit] = Writer.tell(List(entry))
  }

  /**
   * WriterT for effect + logging
   */
  type LogWriterT[F[_], A] = WriterT[F, PipelineLog, A]

  /**
   * Creates an effectful log writer
   */
  def loggedF[F[_], A](fa: F[A], entry: LogEntry): LogWriterT[F, A] =
    WriterT(fa.map(a => (List(entry), a)))

  /**
   * Pipeline operation with logging
   */
  def withLogging[F[_]: Functor, A, B](
    operation: A => F[B],
    operationName: String
  ): A => LogWriterT[F, B] = { input =>
    val startLog = LogEntry(Instant.now(), Info, s"Starting $operationName")
    val result   = operation(input)

    WriterT(result.map { output =>
      val endLog = LogEntry(Instant.now(), Info, s"Completed $operationName")
      (List(startLog, endLog), output)
    })
  }

  // ===== STATE MONAD OPERATIONS =====

  /**
   * Pipeline state for tracking metrics and progress
   */
  case class PipelineState(
    recordsProcessed: Long = 0,
    bytesProcessed: Long = 0,
    errorsEncountered: Int = 0,
    startTime: Option[Instant] = None,
    lastUpdate: Option[Instant] = None,
    customMetrics: Map[String, Double] = Map.empty
  ) {
    def withRecordsProcessed(count: Long): PipelineState =
      copy(recordsProcessed = recordsProcessed + count, lastUpdate = Some(Instant.now()))

    def withBytesProcessed(bytes: Long): PipelineState =
      copy(bytesProcessed = bytesProcessed + bytes, lastUpdate = Some(Instant.now()))

    def withError(): PipelineState =
      copy(errorsEncountered = errorsEncountered + 1, lastUpdate = Some(Instant.now()))

    def withMetric(name: String, value: Double): PipelineState =
      copy(customMetrics = customMetrics + (name -> value), lastUpdate = Some(Instant.now()))
  }

  type PipelineStateOp[A] = State[PipelineState, A]

  /**
   * State operations for pipeline metrics
   */
  object StateOps {
    def recordsProcessed(count: Long): PipelineStateOp[Unit] =
      State.modify(_.withRecordsProcessed(count))

    def bytesProcessed(bytes: Long): PipelineStateOp[Unit] =
      State.modify(_.withBytesProcessed(bytes))

    def errorEncountered: PipelineStateOp[Unit] =
      State.modify(_.withError())

    def setMetric(name: String, value: Double): PipelineStateOp[Unit] =
      State.modify(_.withMetric(name, value))

    def getMetric(name: String): PipelineStateOp[Option[Double]] =
      State.inspect(_.customMetrics.get(name))

    def getCurrentState: PipelineStateOp[PipelineState] = State.get

    def pure[A](value: A): PipelineStateOp[A] = State.pure(value)
  }

  /**
   * StateT for effect + state
   */
  type PipelineStateT[F[_], A] = StateT[F, PipelineState, A]

  /**
   * Stateful pipeline operations
   */
  def withState[F[_]: Monad, A, B](
    operation: A => F[B],
    updateState: (A, B) => PipelineState => PipelineState
  ): A => PipelineStateT[F, B] = { input =>
    StateT { state =>
      operation(input).map { output =>
        val newState = updateState(input, output)(state)
        (newState, output)
      }
    }
  }

  // ===== EVAL OPERATIONS =====

  /**
   * Lazy evaluation for expensive computations
   */
  object EvalOps {

    /**
     * Creates a lazy evaluation
     */
    def defer[A](computation: => A): Eval[A] = Eval.later(computation)

    /**
     * Creates an always-evaluated computation
     */
    def always[A](computation: => A): Eval[A] = Eval.always(computation)

    /**
     * Creates an immediately evaluated value
     */
    def now[A](value: A): Eval[A] = Eval.now(value)

    /**
     * Memoized expensive computation
     */
    def memoized[A](computation: => A): Eval[A] =
      Eval.later(computation)

    /**
     * Recursive computation with stack safety
     */
    def tailRecM[A, B](a: A)(f: A => Eval[Either[A, B]]): Eval[B] =
      Eval.tailRecM(a)(f)

    /**
     * Parallel evaluation (simulated with Eval)
     */
    def parallel[A, B](evalA: Eval[A], evalB: Eval[B]): Eval[(A, B)] =
      for {
        a <- evalA
        b <- evalB
      } yield (a, b)

    /**
     * Conditional evaluation
     */
    def when[A](condition: Boolean)(eval: => Eval[A]): Eval[Option[A]] =
      if (condition) eval.map(Some(_)) else Eval.now(None)
  }

  /**
   * Lazy pipeline operations
   */
  def lazyPipeline[A, B](
    input: A,
    transformations: List[A => Eval[A]]
  ): Eval[A] =
    transformations.foldLeft(Eval.now(input)) { (acc, transform) =>
      acc.flatMap(transform)
    }

  // ===== VALIDATED OPERATIONS =====

  /**
   * Enhanced validation operations
   */
  object ValidatedOps {

    /**
     * Validates a single field
     */
    def validateField[A](
      field: String,
      value: A,
      validators: List[A => ValidationResult[A]]
    ): ValidationResult[A] =
      validators.foldLeft(value.valid[ValidationError]) { (acc, validator) =>
        (acc, validator(value)).mapN((_, validated) => validated)
      }

    /**
     * Validates multiple fields independently
     */
    def validateFields[A, B, C](
      fieldA: ValidationResult[A],
      fieldB: ValidationResult[B]
    )(f: (A, B) => C): ValidationResult[C] =
      (fieldA, fieldB).mapN(f)

    /**
     * Validates with dependency between fields
     */
    def validateDependent[A, B](
      fieldA: ValidationResult[A],
      validateB: A => ValidationResult[B]
    ): ValidationResult[(A, B)] =
      fieldA.andThen(a => validateB(a).map(b => (a, b)))

    /**
     * Conditional validation
     */
    def validateWhen[A](
      condition: Boolean,
      validation: => ValidationResult[A],
      default: A
    ): ValidationResult[A] =
      if (condition) validation else default.valid

    /**
     * Validates a list with error accumulation
     */
    def validateList[A, B](
      list: List[A],
      validator: A => ValidationResult[B]
    ): ValidationResult[List[B]] =
      list.traverse(validator)

    /**
     * All-or-nothing validation
     */
    def validateAllOrNone[A](
      validations: List[ValidationResult[A]]
    ): ValidationResult[List[A]] =
      validations.sequence
  }

  /**
   * Domain-specific validators
   */
  object DomainValidators {

    def nonEmpty(value: String): ValidationResult[String] =
      if (value.trim.nonEmpty) value.valid
      else "Value cannot be empty".invalidNel

    def positive(value: Int): ValidationResult[Int] =
      if (value > 0) value.valid
      else "Value must be positive".invalidNel

    def inRange(min: Int, max: Int)(value: Int): ValidationResult[Int] =
      if (value >= min && value <= max) value.valid
      else s"Value must be between $min and $max".invalidNel

    def validEmail(email: String): ValidationResult[String] =
      if (email.matches("^[A-Za-z0-9+_.-]+@(.+)$")) email.valid
      else "Invalid email format".invalidNel

    def validUrl(url: String): ValidationResult[String] =
      scala.util
        .Try(new java.net.URL(url))
        .fold(
          _ => "Invalid URL format".invalidNel,
          _ => url.valid
        )
  }

  // ===== COMBINING ALL DATA TYPES =====

  /**
   * The ultimate combination: Reader + Writer + State + Validation
   */
  type FullPipelineOp[F[_], A] =
    ReaderT[WriterT[StateT[F, PipelineState, *], PipelineLog, *], PipelineConfig, A]

  /**
   * Creates a full pipeline operation
   */
  def fullPipelineOp[F[_]: Monad, A](
    f: PipelineConfig => PipelineState => F[(PipelineState, PipelineLog, A)]
  ): FullPipelineOp[F, A] =
    ReaderT { config =>
      WriterT {
        StateT { state =>
          f(config)(state).map { case (newState, logs, result) =>
            (newState, (logs, result))
          }
        }
      }
    }

  /**
   * Example usage of the full stack
   */
  def exampleFullOperation[F[_]: Monad]: FullPipelineOp[F, String] =
    fullPipelineOp { config => state =>
      val logs     = List(LogEntry(Instant.now(), Info, "Processing data"))
      val newState = state.withRecordsProcessed(100)
      val result   = s"Processed with parallelism ${config.parallelism}"

      Monad[F].pure((newState, logs, result))
    }
}

/**
 * Syntax extensions for nicer Cats data operations
 */
object CatsDataSyntax {

  implicit class ReaderOps[A, B](reader: Reader[A, B]) {
    def withDefault(default: B): Reader[A, B] = Reader(_ => default)

    def orElse(other: Reader[A, B]): Reader[A, B] = reader // simplified
  }

  implicit class WriterOps[L, A](writer: Writer[L, A]) {
    def mapLogs[L2](f: L => L2): Writer[L2, A] = writer.mapWritten(f)
  }

  implicit class StateOps[S, A](state: State[S, A]) {
    def zoom[S2](lens: S2 => S)(update: (S2, S) => S2): State[S2, A] =
      State { s2 =>
        val (newS, result) = state.run(lens(s2)).value
        (update(s2, newS), result)
      }
  }

  implicit class ValidatedOps[E, A](validated: ValidatedNel[E, A]) {
    def tapErrors(f: NonEmptyList[E] => Unit): ValidatedNel[E, A] =
      validated.leftMap { errors =>
        f(errors)
        errors
      }
  }
}
