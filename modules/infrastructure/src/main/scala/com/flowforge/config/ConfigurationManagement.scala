package com.flowforge.config

import cats.data.ValidatedNel
import cats.effect.Sync
import com.flowforge.core.algebra.{ConfigurationAlgebra, FlowForgeConfig}
import com.typesafe.config.{ Config, ConfigFactory }

import scala.util.{ Failure, Success, Try }

/**
 * Configuration error types.
 */
sealed trait ConfigError {
  def message: String
}

object ConfigError {
  case class MissingKey(key: String) extends ConfigError {
    def message: String = s"Configuration key '$key' is missing"
  }

  case class InvalidValue(
    key: String,
    value: String,
    expectedType: String)
      extends ConfigError {
    def message: String = s"Invalid value '$value' for key '$key', expected $expectedType"
  }

  case class ParseError(key: String, cause: String) extends ConfigError {
    def message: String = s"Parse error for key '$key': $cause"
  }
}

/**
 * Type class for configuration decoders.
 */
trait ConfigDecoder[A] {
  def decode(config: Config, path: String): ValidatedNel[ConfigError, A]
}

/**
 * Type-safe configuration management system.
 */
trait ConfigurationManagement[F[_]] {
  def loadTypeSafeConfig[T: ConfigDecoder](key: String): F[ValidatedNel[ConfigError, T]]
  def watchConfig[T: ConfigDecoder](key: String)(onChange: T => F[Unit]): F[Unit]
  def reloadConfig: F[Unit]
}

object ConfigurationManagement {
  def forTypesafeConfig[F[_]: Sync]: ConfigurationManagement[F] =
    new TypesafeConfigurationManagement[F]

  private class TypesafeConfigurationManagement[F[_]: Sync] extends ConfigurationManagement[F] {
    private var config: Config = ConfigFactory.load()

    def loadTypeSafeConfig[T: ConfigDecoder](key: String): F[ValidatedNel[ConfigError, T]] =
      Sync[F].delay {
        Try {
          implicitly[ConfigDecoder[T]].decode(config, key)
        } match {
          case Success(result) => result
          case Failure(ex) =>
            cats.data.Validated.invalidNel(ConfigError.ParseError(key, ex.getMessage))
        }
      }

    def watchConfig[T: ConfigDecoder](key: String)(onChange: T => F[Unit]): F[Unit] =
      Sync[F].delay {
        // TODO: Implement config watching
        ()
      }

    def reloadConfig: F[Unit] =
      Sync[F].delay {
        config = ConfigFactory.load()
      }
  }

  // Basic decoders
  implicit val stringDecoder: ConfigDecoder[String] = new ConfigDecoder[String] {
    def decode(config: Config, path: String): ValidatedNel[ConfigError, String] =
      Try(config.getString(path)) match {
        case Success(value) => cats.data.Validated.validNel(value)
        case Failure(_)     => cats.data.Validated.invalidNel(ConfigError.MissingKey(path))
      }
  }

  implicit val intDecoder: ConfigDecoder[Int] = new ConfigDecoder[Int] {
    def decode(config: Config, path: String): ValidatedNel[ConfigError, Int] =
      Try(config.getInt(path)) match {
        case Success(value) => cats.data.Validated.validNel(value)
        case Failure(_)     => cats.data.Validated.invalidNel(ConfigError.MissingKey(path))
      }
  }

  implicit val booleanDecoder: ConfigDecoder[Boolean] = new ConfigDecoder[Boolean] {
    def decode(config: Config, path: String): ValidatedNel[ConfigError, Boolean] =
      Try(config.getBoolean(path)) match {
        case Success(value) => cats.data.Validated.validNel(value)
        case Failure(_)     => cats.data.Validated.invalidNel(ConfigError.MissingKey(path))
      }
  }

  // Temporary simple FlowForgeConfig decoder - TODO: implement proper decoding
  implicit val flowForgeConfigDecoder: ConfigDecoder[FlowForgeConfig] =
    new ConfigDecoder[FlowForgeConfig] {
      def decode(cfg: Config, path: String): ValidatedNel[ConfigError, FlowForgeConfig] = {
        // Flatten Typesafe config to a flat Map[String,String] (dot paths) and delegate to core decoder
        import scala.jdk.CollectionConverters._
        val entries = cfg.entrySet().asScala.toList
        val flat: Map[String, String] = entries.flatMap { e =>
          val k = e.getKey
          // Try to read everything as string; downstream decoders parse as needed
          scala.util.Try(cfg.getString(k)).toOption.map(v => k -> v)
        }.toMap
        val coreDecoder = ConfigurationAlgebra.flowForgeConfigDecoder
        coreDecoder
          .decode(flat)
          .leftMap(_.map(err => ConfigError.ParseError(path, err.toString)))
      }
    }
}
