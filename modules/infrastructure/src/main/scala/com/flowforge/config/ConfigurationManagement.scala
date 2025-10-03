package com.flowforge.config

import cats.data.ValidatedNel
import cats.syntax.either._
import cats.effect.Sync
import com.flowforge.core.algebra.{ ConfigurationAlgebra, FlowForgeConfig }
import com.flowforge.core.safety.Safety
import com.flowforge.core.safety.ErrorMapper
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
      Sync[F].delay(implicitly[ConfigDecoder[T]].decode(config, key))

    def watchConfig[T: ConfigDecoder](key: String)(onChange: T => F[Unit]): F[Unit] =
      // Minimal implementation: expose explicit reload via reloadConfig; background watching is
      // environment-specific and requires a concrete effect to run callbacks. Provide a no-op here.
      // Callers can schedule polling using their chosen effect runtime and `reloadConfig`.
      Sync[F].unit

    def reloadConfig: F[Unit] =
      Sync[F].delay {
        config = ConfigFactory.load()
      }
  }

  // Basic decoders
  implicit val stringDecoder: ConfigDecoder[String] = (config: Config, path: String) =>
    Safety
      .safely(config.getString(path))(ErrorMapper.default)
      .leftMap(_ => ConfigError.MissingKey(path))
      .toValidatedNel

  implicit val intDecoder: ConfigDecoder[Int] = (config: Config, path: String) =>
    Safety
      .safely(config.getInt(path))(ErrorMapper.default)
      .leftMap(_ => ConfigError.MissingKey(path))
      .toValidatedNel

  implicit val booleanDecoder: ConfigDecoder[Boolean] = (config: Config, path: String) =>
    Safety
      .safely(config.getBoolean(path))(ErrorMapper.default)
      .leftMap(_ => ConfigError.MissingKey(path))
      .toValidatedNel

  // Temporary simple FlowForgeConfig decoder - TODO: implement proper decoding
  implicit val flowForgeConfigDecoder: ConfigDecoder[FlowForgeConfig] =
    (cfg: Config, path: String) => {
      // Flatten Typesafe config to a flat Map[String,String] (dot paths) and delegate to core decoder
      import scala.jdk.CollectionConverters._
      val entries = cfg.entrySet().asScala.toList
      val flat: Map[String, String] = entries.flatMap { e =>
        val key = e.getKey
        Safety.safely(cfg.getString(key))(ErrorMapper.default).toOption.map(v => key -> v)
      }.toMap
      val coreDecoder = ConfigurationAlgebra.flowForgeConfigDecoder
      coreDecoder
        .decode(flat)
        .leftMap(_.map(err => ConfigError.ParseError(path, err.toString)))
    }
}
