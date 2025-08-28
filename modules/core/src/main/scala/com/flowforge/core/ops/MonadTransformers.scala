package com.flowforge.core.ops

import cats.data._
import cats.syntax.all._
import cats.{Applicative, Functor, Monad}
import com.flowforge.core.algebra.TypeClasses.ConfigReader._
import com.flowforge.core.ValidationError
import com.flowforge.core.types._

/**
 * Complete monad transformers integration for FlowForge
 *
 * When you need to stack effects like a functional programming ninja. This is where we compose
 * OptionT, EitherT, ReaderT, WriterT, StateT into beautiful, type-safe effect stacks.
 */
object MonadTransformers {

  // ===== COMMON TRANSFORMER STACKS =====

  /**
   * Common effect stack: ReaderT + EitherT + IO Configuration + Error Handling + Effects
   */
  type AppStack[F[_], A] = ReaderT[EitherT[F, FlowForgeError, *], PipelineConfig, A]

  /**
   * Creates an AppStack operation
   */
  def appStack[F[_], A](
    f: PipelineConfig => F[Either[FlowForgeError, A]]
  ): AppStack[F, A] =
    ReaderT(config => EitherT(f(config)))

  /**
   * Lifts a pure value into AppStack
   */
  def pure[F[_]: Applicative, A](value: A): AppStack[F, A] =
    ReaderT.pure(value)

  /**
   * Lifts an effect into AppStack
   */
  def liftF[F[_]: Functor, A](fa: F[A]): AppStack[F, A] =
    ReaderT.liftF(EitherT.liftF(fa))

  /**
   * Raises an error in AppStack
   */
  def raiseError[F[_]: Applicative, A](error: FlowForgeError): AppStack[F, A] =
    ReaderT.pure(error).flatMap(e => ReaderT.liftF(EitherT.leftT(e)))

  // ===== OPTONT OPERATIONS =====

  /**
   * Enhanced OptionT operations
   */
  object OptionTOps {

    /**
     * Creates an OptionT from a nullable value
     */
    def fromNullable[F[_]: Applicative, A](value: A): OptionT[F, A] =
      OptionT.fromOption[F](Option(value))

    /**
     * Safely gets a value or computes a default
     */
    def getOrElseF[F[_]: Monad, A](
      optT: OptionT[F, A],
      default: => F[A]
    ): F[A] =
      optT.getOrElseF(default)

    /**
     * Filters with a monadic predicate
     */
    def filterF[F[_]: Monad, A](
      optT: OptionT[F, A],
      predicate: A => F[Boolean]
    ): OptionT[F, A] =
      OptionT(optT.value.flatMap {
        case Some(a) => predicate(a).map(if (_) Some(a) else None)
        case None    => Monad[F].pure(None)
      })

    /**
     * Converts OptionT to EitherT with custom error
     */
    def toEitherT[F[_]: Functor, E, A](
      optT: OptionT[F, A],
      error: => E
    ): EitherT[F, E, A] =
      EitherT(optT.value.map(_.toRight(error)))

    /**
     * Parallel OptionT operations
     */
    def parMapN[F[_]: Applicative, A, B, C](
      optA: OptionT[F, A],
      optB: OptionT[F, B]
    )(f: (A, B) => C): OptionT[F, C] =
      OptionT((optA.value, optB.value).mapN { (a, b) =>
        (a, b).mapN(f)
      })
  }

  // ===== EITHERT OPERATIONS =====

  /**
   * Enhanced EitherT operations
   */
  object EitherTOps {

    /**
     * Creates an EitherT from a Try
     */
    def fromTry[F[_]: Applicative, A](
      tryValue: scala.util.Try[A]
    ): EitherT[F, Throwable, A] =
      EitherT.fromEither[F](tryValue.toEither)

    /**
     * Creates an EitherT with validation
     */
    def fromValidated[F[_]: Applicative, E, A](
      validated: ValidatedNel[E, A]
    ): EitherT[F, NonEmptyList[E], A] =
      EitherT.fromEither[F](validated.toEither)

    /**
     * Recovers from specific error types
     */
    def recoverWith[F[_]: Monad, E, A](
      eitherT: EitherT[F, E, A],
      pf: PartialFunction[E, EitherT[F, E, A]]
    ): EitherT[F, E, A] =
      eitherT.leftFlatMap(e => pf.applyOrElse(e, (_: E) => EitherT.leftT[F, A](e)))

    /**
     * Maps errors to a different type
     */
    def mapError[F[_]: Functor, E1, E2, A](
      eitherT: EitherT[F, E1, A],
      f: E1 => E2
    ): EitherT[F, E2, A] =
      eitherT.leftMap(f)

    /**
     * Validates with accumulating errors
     */
    def validateWith[F[_]: Applicative, E, A, B](
      eitherT: EitherT[F, NonEmptyList[E], A],
      validator: A => ValidatedNel[E, B]
    ): EitherT[F, NonEmptyList[E], B] =
      eitherT.subflatMap(a => validator(a).toEither)
  }

  // ===== READERT OPERATIONS =====

  /**
   * Enhanced ReaderT operations
   */
  object ReaderTOps {

    /**
     * Creates a ReaderT that reads a specific config field
     */
    def asks[F[_]: Applicative, Config, A](
      f: Config => A
    ): ReaderT[F, Config, A] =
      ReaderT(config => Applicative[F].pure(f(config)))

    /**
     * Modifies the environment before reading
     */
    def withConfig[F[_], Config1, Config2, A](
      readerT: ReaderT[F, Config2, A],
      f: Config1 => Config2
    ): ReaderT[F, Config1, A] =
      readerT.local(f)

    /**
     * Provides a default environment
     */
    def provide[F[_], Config, A](
      readerT: ReaderT[F, Config, A],
      config: Config
    ): F[A] =
      readerT.run(config)

    /**
     * Combines multiple ReaderT operations
     */
    def sequence[F[_]: Applicative, Config, A](
      readers: List[ReaderT[F, Config, A]]
    ): ReaderT[F, Config, List[A]] =
      ReaderT(config => readers.traverse(_.run(config)))
  }

  // ===== WRITERT OPERATIONS =====

  /**
   * Enhanced WriterT operations
   */
  object WriterTOps {

    /**
     * Creates a WriterT that logs a message
     */
    def tell[F[_]: Applicative, L, A](
      log: L,
      value: A
    ): WriterT[F, L, A] =
      WriterT.put(value)(log)

    /**
     * Logs conditionally
     */
    def tellWhen[F[_]: Applicative, L, A](
      condition: Boolean,
      log: L,
      value: A
    )(implicit L: cats.Monoid[L]): WriterT[F, L, A] =
      if (condition) WriterT.put(value)(log)
      else WriterT.value(value)

    /**
     * Maps over the log output
     */
    def mapLog[F[_]: Functor, L1, L2, A](
      writerT: WriterT[F, L1, A],
      f: L1 => L2
    ): WriterT[F, L2, A] =
      writerT.mapWritten(f)

    /**
     * Clears the log
     */
    def clearLog[F[_]: Functor, L, A](
      writerT: WriterT[F, L, A]
    )(implicit L: cats.Monoid[L]): WriterT[F, L, A] =
      writerT.mapWritten(_ => L.empty)
  }

  // ===== STATET OPERATIONS =====

  /**
   * Enhanced StateT operations
   */
  object StateTOps {

    /**
     * Creates a StateT that modifies state
     */
    def modify[F[_]: Applicative, S](
      f: S => S
    ): StateT[F, S, Unit] =
      StateT.modify[F, S](f)

    /**
     * Gets a piece of the state
     */
    def gets[F[_]: Applicative, S, A](
      f: S => A
    ): StateT[F, S, A] =
      StateT.inspect[F, S, A](f)

    /**
     * Conditionally modifies state
     */
    def modifyWhen[F[_]: Applicative, S](
      condition: Boolean,
      f: S => S
    ): StateT[F, S, Unit] =
      if (condition) StateT.modify[F, S](f)
      else StateT.pure[F, S, Unit](())

    /**
     * Runs StateT with initial state
     */
    def runWith[F[_], S, A](
      stateT: StateT[F, S, A],
      initialState: S
    ): F[(S, A)] =
      stateT.run(initialState)

    /**
     * Focuses on a part of the state (lens-like)
     */
    def zoom[F[_]: Monad, S1, S2, A](
      stateT: StateT[F, S2, A],
      get: S1 => S2,
      set: (S1, S2) => S1
    ): StateT[F, S1, A] =
      StateT { s1 =>
        stateT.run(get(s1)).map { case (s2, a) =>
          (set(s1, s2), a)
        }
      }
  }

  // ===== COMPLEX TRANSFORMER STACKS =====

  /**
   * The ultimate stack: ReaderT + WriterT + StateT + EitherT
   */
  type UltimateStack[F[_], Config, Log, State, Error, A] =
    ReaderT[WriterT[StateT[EitherT[F, Error, *], State, *], Log, *], Config, A]

  /**
   * Creates an UltimateStack operation
   */
  def ultimateStack[F[_]: Monad, Config, Log, State, Error, A](
    f: Config => State => F[Either[Error, (State, Log, A)]]
  ): UltimateStack[F, Config, Log, State, Error, A] =
    ReaderT { config =>
      WriterT {
        StateT { state =>
          EitherT(f(config)(state).map(_.map { case (newState, log, result) =>
            (newState, (log, result))
          }))
        }
      }
    }

  /**
   * Pipeline-specific ultimate stack
   */
  type PipelineStack[F[_], A] =
    UltimateStack[F, PipelineConfig, List[String], PipelineMetrics, FlowForgeError, A]

  case class PipelineMetrics(
    recordsProcessed: Long = 0,
    errorsEncountered: Int = 0,
    processingTimeMs: Long = 0
  )

  /**
   * Convenience functions for PipelineStack
   */
  object PipelineStackOps {

    def pure[F[_]: Monad, A](value: A): PipelineStack[F, A] =
      ReaderT.pure(value)

    def liftF[F[_]: Monad, A](fa: F[A]): PipelineStack[F, A] =
      ReaderT.liftF(WriterT.liftF(StateT.liftF(EitherT.liftF(fa))))

    def askConfig[F[_]: Monad]: PipelineStack[F, PipelineConfig] =
      ReaderT.ask

    def log[F[_]: Monad](message: String): PipelineStack[F, Unit] =
      ReaderT.liftF(WriterT.tell(List(message)))

    def modifyMetrics[F[_]: Monad](f: PipelineMetrics => PipelineMetrics): PipelineStack[F, Unit] =
      ReaderT.liftF(WriterT.liftF(StateT.modify(f)))

    def raiseError[F[_]: Monad, A](error: FlowForgeError): PipelineStack[F, A] =
      ReaderT.liftF(WriterT.liftF(StateT.liftF(EitherT.leftT(error))))

    def runPipelineStack[F[_]](
      stack: PipelineStack[F, A],
      config: PipelineConfig,
      initialMetrics: PipelineMetrics = PipelineMetrics()
    ): F[Either[FlowForgeError, (PipelineMetrics, List[String], A)]] =
      stack
        .run(config)
        .run
        .run(initialMetrics)
        .value
        .map(_.map { case (metrics, (logs, result)) =>
          (metrics, logs, result)
        })
  }

  // ===== TRANSFORMER COMBINATORS =====

  /**
   * Combines multiple transformer operations in parallel
   */
  def parMapN2[F[_]: Applicative, T[_[_], _], A, B, C](
    ta: T[F, A],
    tb: T[F, B]
  )(f: (A, B) => C)(implicit
    TApplicative: Applicative[T[F, *]]
  ): T[F, C] =
    TApplicative.map2(ta, tb)(f)

  /**
   * Sequences a list of transformer operations
   */
  def sequenceT[F[_]: Applicative, T[_[_], _], A](
    list: List[T[F, A]]
  )(implicit TApplicative: Applicative[T[F, *]]): T[F, List[A]] =
    list.sequence[T[F, *], A]

  /**
   * Traverse with transformer
   */
  def traverseT[F[_]: Applicative, T[_[_], _], A, B](
    list: List[A],
    f: A => T[F, B]
  )(implicit TApplicative: Applicative[T[F, *]]): T[F, List[B]] =
    list.traverse[T[F, *], B](f)

  // ===== REAL-WORLD EXAMPLE =====

  /**
   * Real-world pipeline operation using transformer stack
   */
  def processBatchData[F[_]: cats.effect.Async](
    batchSize: Int
  ): PipelineStack[F, List[ProcessedRecord]] = {
    import PipelineStackOps._

    for {
      config <- askConfig
      _      <- log(s"Starting batch processing with size $batchSize")

      // Simulate data processing
      _ <- modifyMetrics(_.copy(recordsProcessed = batchSize))
      _ <- liftF(cats.effect.Async[F].sleep(scala.concurrent.duration.Duration.fromNanos(100)))

      result <-
        if (batchSize > 0) {
          pure(List.fill(batchSize)(ProcessedRecord("processed")))
        } else {
          raiseError[F, List[ProcessedRecord]](
            ValidationError("batchSize", batchSize.toString, "must be positive")
          )
        }

      _ <- log(s"Completed processing ${result.length} records")
    } yield result
  }

  case class ProcessedRecord(data: String)
}

/**
 * Syntax extensions for transformer operations
 */
object TransformerSyntax {

  implicit class MonadTransformerOps[F[_], T[_[_], _], A](tfa: T[F, A]) {

    def mapT[B](f: A => B)(implicit F: Functor[F], T: Functor[T[F, *]]): T[F, B] =
      T.map(tfa)(f)

    def flatMapT[B](f: A => T[F, B])(implicit F: Monad[F], T: Monad[T[F, *]]): T[F, B] =
      T.flatMap(tfa)(f)
  }

  implicit class EitherTSyntax[F[_], E, A](eitherT: EitherT[F, E, A]) {

    def orElse(other: EitherT[F, E, A])(implicit F: Monad[F]): EitherT[F, E, A] =
      EitherT(eitherT.value.flatMap {
        case Left(_)  => other.value
        case Right(a) => F.pure(Right(a))
      })

    def toValidated(implicit F: Functor[F]): F[ValidatedNel[E, A]] =
      eitherT.value.map(_.toValidatedNel)
  }

  implicit class OptionTSyntax[F[_], A](optionT: OptionT[F, A]) {

    def orElse(other: OptionT[F, A])(implicit F: Monad[F]): OptionT[F, A] =
      OptionT(optionT.value.flatMap {
        case None => other.value
        case some => F.pure(some)
      })

    def toEither[E](error: => E)(implicit F: Functor[F]): EitherT[F, E, A] =
      EitherT(optionT.value.map(_.toRight(error)))
  }
}
