package com.flowforge.config

import cats.data.{ NonEmptyList, Validated, ValidatedNel }
import cats.effect.Sync
import cats.syntax.all._
import com.flowforge.core.algebra.CDCOperations
import com.flowforge.core.types.RefinedTypes.FieldName
import com.typesafe.config.{ Config, ConfigFactory }

import scala.concurrent.duration._
import scala.util.{ Failure, Success, Try }

/**
 * Type-safe configuration management system - replacement for CCM. Provides compile-time safe
 * configuration loading with validation.
 */
trait ConfigurationManagement[F[_]] {

  /**
   * Load type-safe configuration with validation. Returns all validation errors if configuration is
   * invalid.
   */
  def loadTypeSafeConfig[T: ConfigDecoder](key: String): F[ValidatedNel[ConfigError, T]]

  /**
   * Load optional configuration - returns None if key doesn't exist.
   */
  def loadOptionalConfig[T: ConfigDecoder](key: String): F[Option[T]]

  /**
   * Watch configuration for changes - returns stream of updates.
   */
  def watchConfig[T: ConfigDecoder](key: String): fs2.Stream[F, T]

  /**
   * Refresh configuration from source.
   */
  def refreshConfig: F[Unit]

  /**
   * Load configuration for specific environment.
   */
  def loadForEnvironment[T: ConfigDecoder](
    key: String,
    env: Environment
  ): F[ValidatedNel[ConfigError, T]]
}

/**
 * Configuration decoder type class for automatic config parsing.
 */
trait ConfigDecoder[T] {
  def decode(config: Config, path: String): ValidatedNel[ConfigError, T]
}

/**
 * Configuration validation errors.
 */
sealed trait ConfigError extends Product with Serializable {
  def message: String
}

object ConfigError {
  case class MissingKey(key: String) extends ConfigError {
    override def message: String = s"Configuration key '$key' is missing"
  }

  case class InvalidType(key: String, expected: String, actual: String) extends ConfigError {
    override def message: String = s"Configuration key '$key' expected $expected but got $actual"
  }

  case class ValidationFailed(key: String, reason: String) extends ConfigError {
    override def message: String = s"Configuration validation failed for '$key': $reason"
  }

  case class ParseError(key: String, error: String) extends ConfigError {
    override def message: String = s"Failed to parse configuration '$key': $error"
  }
}

/**
 * Environment types for configuration management.
 */
sealed trait Environment extends Product with Serializable

object Environment {
  case object Development extends Environment
  case object Staging     extends Environment
  case object Production  extends Environment

  def fromString(env: String): Option[Environment] = env.toLowerCase match {
    case "dev" | "development" => Some(Development)
    case "staging" | "stage"   => Some(Staging)
    case "prod" | "production" => Some(Production)
    case _                     => None
  }
}

/**
 * FlowForge configuration data structures.
 */
case class FlowForgeConfig(
  pipeline: PipelineConfig,
  engines: EngineConfig,
  connectors: ConnectorConfig,
  monitoring: MonitoringConfig,
  audit: AuditConfig
)

case class PipelineConfig(
  name: String,
  batchSize: Int,
  parallelism: Int,
  timeout: FiniteDuration
)

case class EngineConfig(
  spark: SparkConfig,
  flink: Option[FlinkConfig] = None
)

case class SparkConfig(
  appName: String,
  master: String,
  executorMemory: String,
  driverMemory: String
)

case class FlinkConfig(
  jobName: String,
  parallelism: Int
)

case class ConnectorConfig(
  gcs: Option[GCSConfig] = None,
  s3: Option[S3Config] = None,
  bigquery: Option[BigQueryConfig] = None,
  kafka: Option[KafkaConfig] = None
)

case class GCSConfig(
  projectId: String,
  bucketName: String,
  credentialsPath: Option[String] = None
)

case class S3Config(
  region: String,
  bucketName: String,
  accessKeyId: String,
  secretAccessKey: String
)

case class BigQueryConfig(
  projectId: String,
  datasetId: String,
  credentialsPath: Option[String] = None
)

case class KafkaConfig(
  bootstrapServers: String,
  groupId: String,
  autoOffsetReset: String = "earliest"
)

case class MonitoringConfig(
  enableMetrics: Boolean = true,
  enableTracing: Boolean = true,
  metricsPort: Int = 9090
)

case class AuditConfig(
  enableAuditing: Boolean = true,
  auditLogPath: String = "/tmp/flowforge-audit.log"
)

object ConfigurationManagement {

  /**
   * Create ConfigurationManagement instance.
   */
  def apply[F[_]: ConfigurationManagement]: ConfigurationManagement[F] =
    implicitly[ConfigurationManagement[F]]

  /**
   * Default implementation using Typesafe Config.
   */
  implicit def forTypesafeConfig[F[_]: Sync]: ConfigurationManagement[F] =
    new TypesafeConfigManagement[F]

  private class TypesafeConfigManagement[F[_]: Sync] extends ConfigurationManagement[F] {

    private val config = ConfigFactory.load()

    override def loadTypeSafeConfig[T: ConfigDecoder](
      key: String
    ): F[ValidatedNel[ConfigError, T]] =
      Sync[F].delay {
        if (config.hasPath(key)) {
          ConfigDecoder[T].decode(config, key)
        } else {
          ConfigError.MissingKey(key).invalidNel
        }
      }

    override def loadOptionalConfig[T: ConfigDecoder](key: String): F[Option[T]] =
      Sync[F].delay {
        if (config.hasPath(key)) {
          ConfigDecoder[T].decode(config, key).toOption
        } else {
          None
        }
      }

    override def watchConfig[T: ConfigDecoder](key: String): fs2.Stream[F, T] =
      // TODO: Implement configuration watching with file system monitoring
      fs2.Stream.empty

    override def refreshConfig: F[Unit] =
      Sync[F].delay {
        // TODO: Implement configuration refresh
        ()
      }

    override def loadForEnvironment[T: ConfigDecoder](
      key: String,
      env: Environment
    ): F[ValidatedNel[ConfigError, T]] = {
      val envKey = s"${env.toString.toLowerCase}.$key"
      loadTypeSafeConfig[T](envKey).flatMap { result =>
        result match {
          case cats.data.Validated.Invalid(_) =>
            // Fall back to non-environment specific key
            loadTypeSafeConfig[T](key)
          case valid =>
            Sync[F].pure(valid)
        }
      }
    }
  }
}

/**
 * Configuration decoder instances for common types.
 */
object ConfigDecoder {

  def apply[T: ConfigDecoder]: ConfigDecoder[T] = implicitly[ConfigDecoder[T]]

  // Basic type decoders
  implicit val stringDecoder: ConfigDecoder[String] = new ConfigDecoder[String] {
    override def decode(config: Config, path: String): ValidatedNel[ConfigError, String] =
      Try(config.getString(path)) match {
        case Success(value) => value.validNel
        case Failure(ex)    => ConfigError.ParseError(path, ex.getMessage).invalidNel
      }
  }

  implicit val intDecoder: ConfigDecoder[Int] = new ConfigDecoder[Int] {
    override def decode(config: Config, path: String): ValidatedNel[ConfigError, Int] =
      Try(config.getInt(path)) match {
        case Success(value) => value.validNel
        case Failure(ex)    => ConfigError.ParseError(path, ex.getMessage).invalidNel
      }
  }

  implicit val booleanDecoder: ConfigDecoder[Boolean] = new ConfigDecoder[Boolean] {
    override def decode(config: Config, path: String): ValidatedNel[ConfigError, Boolean] =
      Try(config.getBoolean(path)) match {
        case Success(value) => value.validNel
        case Failure(ex)    => ConfigError.ParseError(path, ex.getMessage).invalidNel
      }
  }

  implicit val finiteDurationDecoder: ConfigDecoder[FiniteDuration] =
    new ConfigDecoder[FiniteDuration] {
      override def decode(config: Config, path: String): ValidatedNel[ConfigError, FiniteDuration] =
        Try(config.getDuration(path).toMillis.millis) match {
          case Success(value) => value.validNel
          case Failure(ex)    => ConfigError.ParseError(path, ex.getMessage).invalidNel
        }
    }

  // ---------- Helpers for lists and NonEmptyList ----------
  implicit def listDecoder[A](implicit dec: ConfigDecoder[A]): ConfigDecoder[List[A]] =
    new ConfigDecoder[List[A]] {
      override def decode(config: Config, path: String): ValidatedNel[ConfigError, List[A]] = {
        // Try parsing as config list first
        val configListResult: Option[ValidatedNel[ConfigError, List[A]]] = 
          Try(config.getConfigList(path)).toOption
            .map(_.toArray.toList.map(_.asInstanceOf[Config]))
            .map { cfgs =>
              cfgs.zipWithIndex.map { case (c, idx) =>
                dec
                  .decode(c, "")
                  .leftMap(_.map(e => ConfigError.ValidationFailed(s"$path[$idx]", e.message)))
              }.sequence
                .map(_.toList)
            }

        // Try parsing as string list if config list fails
        val stringListResult: Option[ValidatedNel[ConfigError, List[A]]] =
          Try(config.getStringList(path)).toOption.map { stringList =>
            val strings = stringList.toArray.toList.map(_.toString).map(_.asInstanceOf[A])
            Validated.valid[NonEmptyList[ConfigError], List[A]](strings)
          }

        // Return the first successful result or empty list
        configListResult
          .orElse(stringListResult)
          .getOrElse(Validated.valid[NonEmptyList[ConfigError], List[A]](List.empty[A]))
      }
    }

  private def getStringList(config: Config, path: String): Either[ConfigError, List[String]] =
    Try(config.getStringList(path)).toOption
      .map(list => list.toArray.toList.map(_.toString))
      .toRight(ConfigError.MissingKey(path))

  private def decodeFieldName(str: String): ValidatedNel[ConfigError, FieldName] =
    Either
      .catchNonFatal(FieldName.unsafeFrom(str))
      .leftMap(t => ConfigError.ValidationFailed("fieldName", t.getMessage))
      .toValidatedNel

  private def nelOfFieldNames(
    config: Config,
    path: String
  ): ValidatedNel[ConfigError, NonEmptyList[FieldName]] =
    if (!config.hasPath(path)) ConfigError.MissingKey(path).invalidNel
    else {
      val strs = config.getStringList(path).asInstanceOf[java.util.List[String]]
      val list = strs.toArray.toList.map(_.toString)
      NonEmptyList
        .fromList(list)
        .toValidNel[ConfigError](ConfigError.ValidationFailed(path, "must be non-empty"))
        .andThen(_.traverse { s =>
          Either
            .catchNonFatal(FieldName.unsafeFrom(s))
            .leftMap(t => ConfigError.ValidationFailed(path, t.getMessage))
            .toValidatedNel
        })
    }

  private def optFieldName(
    config: Config,
    path: String
  ): ValidatedNel[ConfigError, Option[FieldName]] =
    if (config.hasPath(path))
      stringDecoder.decode(config, path).andThen(s => decodeFieldName(s)).map(Some(_))
    else None.validNel

  // ---------- CDC Config decoders ----------
  implicit val scd2ColumnsDecoder: ConfigDecoder[CDCOperations.SCD2Columns] =
    new ConfigDecoder[CDCOperations.SCD2Columns] {
      override def decode(
        config: Config,
        path: String
      ): ValidatedNel[ConfigError, CDCOperations.SCD2Columns] = {
        val c = if (path.nonEmpty) config.getConfig(path) else config
        (
          stringDecoder.decode(c, "effectiveFrom").andThen(decodeFieldName),
          stringDecoder.decode(c, "effectiveTo").andThen(decodeFieldName),
          stringDecoder.decode(c, "isCurrent").andThen(decodeFieldName)
        ).mapN(CDCOperations.SCD2Columns.apply)
      }
    }

  implicit val partitionStrategyDecoder: ConfigDecoder[CDCOperations.PartitionStrategy] =
    new ConfigDecoder[CDCOperations.PartitionStrategy] {
      override def decode(
        config: Config,
        path: String
      ): ValidatedNel[ConfigError, CDCOperations.PartitionStrategy] = {
        val c = if (path.nonEmpty) config.getConfig(path) else config
        val parts =
          if (c.hasPath("partitionBy"))
            getStringList(c, "partitionBy").map(_.map(FieldName.unsafeFrom)).toValidatedNel
          else List.empty[FieldName].validNel
        parts.map(lst => CDCOperations.PartitionStrategy(lst))
      }
    }

  implicit val cdcConfigDecoder: ConfigDecoder[CDCOperations.CDCConfig] =
    new ConfigDecoder[CDCOperations.CDCConfig] {
      override def decode(
        config: Config,
        path: String
      ): ValidatedNel[ConfigError, CDCOperations.CDCConfig] = {
        val c = if (path.nonEmpty) config.getConfig(path) else config
        (
          nelOfFieldNames(c, "keyColumns"),
          optFieldName(c, "timestampColumn"),
          booleanDecoder.decode(c, "deleteDetection").orElse(true.validNel),
          intDecoder.decode(c, "batchSize").orElse(10000.validNel),
          (if (c.hasPath("scd2")) scd2ColumnsDecoder.decode(c, "scd2").map(Some(_))
           else None.validNel),
          (if (c.hasPath("partition")) partitionStrategyDecoder.decode(c, "partition").map(Some(_))
           else None.validNel),
          (if (c.hasPath("hashColumns")) nelOfFieldNames(c, "hashColumns").map(Some(_))
           else None.validNel),
          booleanDecoder.decode(c, "optimizeAfterMerge").orElse(false.validNel),
          (if (c.hasPath("zOrderBy")) nelOfFieldNames(c, "zOrderBy").map(Some(_))
           else None.validNel)
        ).mapN(CDCOperations.CDCConfig.apply)
      }
    }

  // Complex type decoders
  implicit val pipelineConfigDecoder: ConfigDecoder[PipelineConfig] =
    new ConfigDecoder[PipelineConfig] {
      override def decode(
        config: Config,
        path: String
      ): ValidatedNel[ConfigError, PipelineConfig] = {
        val pipelineConfig = config.getConfig(path)

        (
          stringDecoder.decode(pipelineConfig, "name"),
          intDecoder.decode(pipelineConfig, "batchSize"),
          intDecoder.decode(pipelineConfig, "parallelism"),
          finiteDurationDecoder.decode(pipelineConfig, "timeout")
        ).mapN(PipelineConfig.apply)
      }
    }

  implicit val sparkConfigDecoder: ConfigDecoder[SparkConfig] = new ConfigDecoder[SparkConfig] {
    override def decode(config: Config, path: String): ValidatedNel[ConfigError, SparkConfig] = {
      val sparkConfig = config.getConfig(path)

      (
        stringDecoder.decode(sparkConfig, "appName"),
        stringDecoder.decode(sparkConfig, "master"),
        stringDecoder.decode(sparkConfig, "executorMemory"),
        stringDecoder.decode(sparkConfig, "driverMemory")
      ).mapN(SparkConfig.apply)
    }
  }

  // TODO: Add decoders for other configuration types as needed
  implicit val engineConfigDecoder: ConfigDecoder[EngineConfig] = new ConfigDecoder[EngineConfig] {
    override def decode(config: Config, path: String): ValidatedNel[ConfigError, EngineConfig] = {
      val engineConfig = config.getConfig(path)
      sparkConfigDecoder.decode(engineConfig, "spark").map(spark => EngineConfig(spark))
    }
  }

  implicit val monitoringConfigDecoder: ConfigDecoder[MonitoringConfig] =
    new ConfigDecoder[MonitoringConfig] {
      override def decode(
        config: Config,
        path: String
      ): ValidatedNel[ConfigError, MonitoringConfig] = {
        val monitoringConfig = config.getConfig(path)

        (
          booleanDecoder.decode(monitoringConfig, "enableMetrics"),
          booleanDecoder.decode(monitoringConfig, "enableTracing"),
          intDecoder.decode(monitoringConfig, "metricsPort")
        ).mapN(MonitoringConfig.apply)
      }
    }

  implicit val auditConfigDecoder: ConfigDecoder[AuditConfig] = new ConfigDecoder[AuditConfig] {
    override def decode(config: Config, path: String): ValidatedNel[ConfigError, AuditConfig] = {
      val auditConfig = config.getConfig(path)

      (
        booleanDecoder.decode(auditConfig, "enableAuditing"),
        stringDecoder.decode(auditConfig, "auditLogPath")
      ).mapN(AuditConfig.apply)
    }
  }

  // Placeholder for connector config decoder (to be implemented when connectors are built)
  implicit val connectorConfigDecoder: ConfigDecoder[ConnectorConfig] =
    new ConfigDecoder[ConnectorConfig] {
      override def decode(
        config: Config,
        path: String
      ): ValidatedNel[ConfigError, ConnectorConfig] =
        ConnectorConfig().validNel // Empty for now
    }

  implicit val flowForgeConfigDecoder: ConfigDecoder[FlowForgeConfig] =
    new ConfigDecoder[FlowForgeConfig] {
      override def decode(
        config: Config,
        path: String
      ): ValidatedNel[ConfigError, FlowForgeConfig] = {
        val flowforgeConfig = config.getConfig(path)

        (
          pipelineConfigDecoder.decode(flowforgeConfig, "pipeline"),
          engineConfigDecoder.decode(flowforgeConfig, "engines"),
          connectorConfigDecoder.decode(flowforgeConfig, "connectors"),
          monitoringConfigDecoder.decode(flowforgeConfig, "monitoring"),
          auditConfigDecoder.decode(flowforgeConfig, "audit")
        ).mapN(FlowForgeConfig.apply)
      }
    }
}
