package com.flowforge.core

import cats.data.Kleisli
import cats.implicits.catsSyntaxTuple2Semigroupal
import cats.{ Applicative, Monad }
import com.flowforge.core.types.FlowForgeError

/**
 * Advanced composition patterns for FlowForge pipelines
 *
 * This is where functional composition gets serious - we're talking Kleisli arrows for dependency
 * injection, Free monads for DSL building, and Tagless Final for effect abstraction.
 */
object CompositionPatterns {

  // ===== KLEISLI COMPOSITION =====

  /**
   * Pipeline composition using Kleisli arrows Think of this as dependency injection meets function
   * composition
   */
  type PipelineKleisli[F[_], Config, A, B] = Kleisli[F, (Config, A), B]

  /**
   * Creates a Kleisli-based pipeline component
   */
  def kleisliComponent[F[_]: Monad, Config, A, B](
    f: (Config, A) => F[B]
  ): PipelineKleisli[F, Config, A, B] =
    Kleisli(f.tupled)

  /**
   * Composes Kleisli pipelines - this is where the magic happens
   */
  def composePipelines[F[_]: Monad, Config, A, B, C](
    first: PipelineKleisli[F, Config, A, B],
    second: PipelineKleisli[F, Config, B, C]
  ): PipelineKleisli[F, Config, A, C] =
    first.andThen(
      second
        .local[(Config, A)] { case (config, a) =>
          (config, a) // Pass config through
        }
        .compose(Kleisli.ask[(Config, A)].map(_._1).map((_, _)))
    )

  /**
   * Parallel composition using Applicative
   */
  def parallelKleisli[F[_]: Applicative, Config, A, B, C](
    left: PipelineKleisli[F, Config, A, B],
    right: PipelineKleisli[F, Config, A, C]
  ): PipelineKleisli[F, Config, A, (B, C)] =
    Kleisli { case (config, a) =>
      (left.run((config, a)), right.run((config, a))).mapN((_, _))
    }

  // ===== FREE MONAD DSL =====

  /**
   * Free monad algebra for pipeline operations This lets us build a DSL that's interpreted later
   */
  sealed trait PipelineOp[A]

  case class Extract[Source, Data](source: Source)         extends PipelineOp[Data]
  case class Transform[A, B](data: A, transformer: A => B) extends PipelineOp[B]
  case class Validate[A](data: A, validator: A => ValidationResult[A])
      extends PipelineOp[ValidationResult[A]]
  case class Load[Data, Target](data: Data, target: Target) extends PipelineOp[Unit]
  case class Log[A](message: String, data: A)               extends PipelineOp[A]

  type PipelineDSL[A] = Free[PipelineOp, A]

  /**
   * Smart constructors for our DSL
   */
  object PipelineDSL {
    def extract[Source, Data](source: Source): PipelineDSL[Data] =
      Free.liftF(Extract(source))

    def transform[A, B](data: A, transformer: A => B): PipelineDSL[B] =
      Free.liftF(Transform(data, transformer))

    def validate[A](
      data: A,
      validator: A => ValidationResult[A]
    ): PipelineDSL[ValidationResult[A]] =
      Free.liftF(Validate(data, validator))

    def load[Data, Target](data: Data, target: Target): PipelineDSL[Unit] =
      Free.liftF(Load(data, target))

    def log[A](message: String, data: A): PipelineDSL[A] =
      Free.liftF(Log(message, data))
  }

  /**
   * Natural transformation to interpret our DSL
   */
  class PipelineInterpreter[F[_]: Monad] extends (PipelineOp ~> F) {
    def apply[A](op: PipelineOp[A]): F[A] = op match {
      case Extract(source) =>
        // Actual extraction logic would go here
        Monad[F].pure(source.asInstanceOf[A])

      case Transform(data, transformer) =>
        Monad[F].pure(transformer(data))

      case Validate(data, validator) =>
        Monad[F].pure(validator(data))

      case Load(data, target) =>
        // Actual loading logic would go here
        Monad[F].pure(())

      case Log(message, data) =>
        // Actual logging would go here
        Monad[F].pure(data)
    }
  }

  // ===== TAGLESS FINAL =====

  /**
   * Tagless Final pattern for effect abstraction This is the modern way to do dependency injection
   * in FP
   */
  trait PipelineAlgebra[F[_]] {
    def extract[Source, Data](source: Source): F[Data]
    def transform[A, B](data: A)(transformer: A => F[B]): F[B]
    def validate[A](data: A)(validator: A => F[ValidationResult[A]]): F[ValidationResult[A]]
    def load[Data, Target](data: Data, target: Target): F[Unit]
    def log[A](message: String)(data: A): F[A]
  }

  /**
   * Business logic using Tagless Final
   */
  def businessPipeline[F[_]: Monad](implicit
    algebra: PipelineAlgebra[F]
  ): F[ValidationResult[String]] = {
    import algebra._

    for {
      raw     <- extract[String, String]("input.csv")
      _       <- log("Extracted data")(raw)
      cleaned <- transform(raw)(data => Monad[F].pure(data.trim))
      _       <- log("Transformed data")(cleaned)
      validated <- validate(cleaned)(data =>
        Monad[F].pure(if (data.nonEmpty) data.valid else "Empty data".invalidNel)
      )
      _ <- validated.fold(
        errors => log(s"Validation failed: $errors")(errors.toString).void,
        success => load(success, "output.csv")
      )
    } yield validated
  }

  /**
   * Concrete implementation for Cats Effect
   */
  implicit def catsEffectPipelineAlgebra[F[_]: cats.effect.Sync]: PipelineAlgebra[F] =
    new PipelineAlgebra[F] {
      def extract[Source, Data](source: Source): F[Data] =
        cats.effect.Sync[F].delay(source.asInstanceOf[Data])

      def transform[A, B](data: A)(transformer: A => F[B]): F[B] =
        transformer(data)

      def validate[A](data: A)(validator: A => F[ValidationResult[A]]): F[ValidationResult[A]] =
        validator(data)

      def load[Data, Target](data: Data, target: Target): F[Unit] =
        cats.effect.Sync[F].delay(println(s"Loading $data to $target"))

      def log[A](message: String)(data: A): F[A] =
        cats.effect.Sync[F].delay {
          println(s"LOG: $message")
          data
        }
    }

  // ===== HIGHER-ORDER COMPOSITION =====

  /**
   * Composing effectful functions with error handling
   */
  def composeWithErrorHandling[F[_]: Monad, A, B, C](
    f: A => F[Either[FlowForgeError, B]],
    g: B => F[Either[FlowForgeError, C]]
  ): A => F[Either[FlowForgeError, C]] = { a =>
    f(a).flatMap(
      _.fold(
        error => Monad[F].pure(error.asLeft[C]),
        b => g(b)
      )
    )
  }

  /**
   * Parallel composition with error accumulation
   */
  def parallelWithValidation[F[_]: Applicative, A, B, C](
    f: A => F[ValidationResult[B]],
    g: A => F[ValidationResult[C]]
  ): A => F[ValidationResult[(B, C)]] = { a =>
    (f(a), g(a)).mapN((vb, vc) => (vb, vc).mapN((_, _)))
  }
}
