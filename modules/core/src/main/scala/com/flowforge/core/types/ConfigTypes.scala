/**
 * FlowForge Core Module - Configuration Types
 *
 * File: modules/core/src/main/scala/com/flowforge/core/types/ConfigTypes.scala Package:
 * com.flowforge.core.types
 *
 * This file defines type-safe configuration types for the FlowForge ecosystem. Instead of relying on external
 * configuration libraries, we use pure Scala ADTs with cats ValidatedNel for comprehensive validation and
 * error accumulation.
 *
 * Design Patterns Applied:
 *   - ADT Pattern: Sealed trait hierarchies for configuration options
 *   - Builder Pattern: Fluent API for configuration construction
 *   - Validation Pattern: cats ValidatedNel for error accumulation
 *   - Factory Pattern: Smart constructors with validation
 *   - Value Object Pattern: Configuration as immutable data
 *
 * Scala Features Showcased:
 *   - Refined Types: Compile-time validation for configuration values
 *   - Case Classes: Immutable configuration data structures
 *   - ValidatedNel: Functional validation with error accumulation
 *   - Companion Objects: Factory methods and validation logic
 *   - Pattern Matching: Configuration selection and validation
 *   - Type Classes: Show instances for configuration display
 *   - Generic Programming: Parameterized configuration validation
 *
 * Innovation Highlights:
 *   - Zero external dependencies for configuration management
 *   - Comprehensive validation with detailed error messages
 *   - Type-safe configuration with compile-time guarantees
 *   - Functional validation patterns with error accumulation
 *   - Environment-specific configuration with inheritance
 *   - Integration with effect systems for configuration loading
 *
 * Usage Examples:
 * ```scala
 * // Type-safe pipeline configuration
 * val config = PipelineConfig.builder
 *   .withName("customer-analytics")
 *   .withEnvironment(Environment.Production)
 *   .withSparkConfig(SparkConfig.default.withMemory("4g"))
 *   .withRetryPolicy(RetryPolicy.exponential(3, 1.second))
 *   .build
 *
 * // Validation with error accumulation
 * val validated: ValidatedNel[ConfigError, PipelineConfig] =
 *   PipelineConfig.validate(rawConfigMap)
 *
 * validated match {
 *   case Valid(config)   => runPipeline(config)
 *   case Invalid(errors) => errors.toList.foreach(logError)
 * }
 * ```
 *
 * @author
 *   FlowForge Team
 * @version 1.0.0
 * @since 2024
 */
package com.flowforge.core.types

import cats.Show
import cats.data.ValidatedNel
import cats.syntax.all._
import com.flowforge.core.types.PipelineConfig.PipelineConfigBuilder
import com.flowforge.core.types.RefinedTypes._
import eu.timepit.refined.api.Refined

import java.time.Instant
import java.util.UUID
import scala.concurrent.duration.FiniteDuration

// ===============================
// CONFIGURATION ERRORS
// ===============================

/**
 * Configuration validation errors. Used with cats ValidatedNel for comprehensive error reporting.
 */
sealed trait ConfigError extends Product with Serializable {
  def message: String

  def field: Option[String]
}

object ConfigError {

  case class MissingRequired(fieldName: String) extends ConfigError {
    val message = s"Required field '$fieldName' is missing"
    val field   = Some(fieldName)
  }

  case class InvalidValue(
    fieldName: String,
    value: String,
    expected: String)
      extends ConfigError {
    val message = s"Invalid value for field '$fieldName': got '$value', expected $expected"
    val field   = Some(fieldName)
  }

  case class InvalidFormat(
    fieldName: String,
    value: String,
    format: String)
      extends ConfigError {
    val message = s"Invalid format for field '$fieldName': '$value' does not match $format"
    val field   = Some(fieldName)
  }

  case class OutOfRange(
    fieldName: String,
    value: String,
    min: String,
    max: String)
      extends ConfigError {
    val message = s"Value '$value' for field '$fieldName' is out of range [$min, $max]"
    val field   = Some(fieldName)
  }

  case class ConflictingValues(
    field1: String,
    field2: String,
    reason: String)
      extends ConfigError {
    val message = s"Conflicting values between '$field1' and '$field2': $reason"
    val field   = Some(s"$field1,$field2")
  }

  case class DependencyMissing(fieldName: String, dependency: String) extends ConfigError {
    val message = s"Field '$fieldName' requires '$dependency' to be set"
    val field   = Some(fieldName)
  }

  case class CustomError(msg: String) extends ConfigError {
    val message = msg
    val field   = None
  }

  implicit val showConfigError: Show[ConfigError] = Show.show(_.message)
}

// ===============================
// ENVIRONMENT CONFIGURATION
// ===============================

/**
 * Deployment environment specification.
 */
sealed trait Environment extends Product with Serializable {
  def name: String

  def isProduction: Boolean

  def defaultRetries: Int

  def defaultTimeout: FiniteDuration
}

object Environment {

  case object Development extends Environment {
    val name           = "development"
    val isProduction   = false
    val defaultRetries = 1
    val defaultTimeout = FiniteDuration(30, "seconds")
  }

  case object Testing extends Environment {
    val name           = "testing"
    val isProduction   = false
    val defaultRetries = 2
    val defaultTimeout = FiniteDuration(60, "seconds")
  }

  case object Staging extends Environment {
    val name           = "staging"
    val isProduction   = false
    val defaultRetries = 3
    val defaultTimeout = FiniteDuration(120, "seconds")
  }

  case object Production extends Environment {
    val name           = "production"
    val isProduction   = true
    val defaultRetries = 5
    val defaultTimeout = FiniteDuration(300, "seconds")
  }

  def fromString(env: String): ValidatedNel[ConfigError, Environment] = env.toLowerCase match {
    case "development" | "dev" => Development.validNel
    case "testing" | "test"    => Testing.validNel
    case "staging" | "stage"   => Staging.validNel
    case "production" | "prod" => Production.validNel
    case _ =>
      ConfigError
        .InvalidValue("environment", env, "development|testing|staging|production")
        .invalidNel
  }

  implicit val showEnvironment: Show[Environment] = Show.show(_.name)
}

// ===============================
// RETRY & RESILIENCE CONFIG
// ===============================

/**
 * Retry policy configuration for resilient operations.
 */
case class RetryPolicy(
  maxRetries: PositiveInt,
  initialDelay: FiniteDuration,
  maxDelay: FiniteDuration,
  backoffFactor: Double,
  jitter: Boolean = true) {

  def validate: ValidatedNel[ConfigError, Unit] = {
    val backoffValidation = if (backoffFactor >= 1.0) {
      ().validNel
    } else {
      ConfigError.InvalidValue("backoffFactor", backoffFactor.toString, ">= 1.0").invalidNel
    }

    val delayValidation = if (initialDelay <= maxDelay) {
      ().validNel
    } else {
      ConfigError
        .ConflictingValues(
          "initialDelay",
          "maxDelay",
          "initialDelay must be <= maxDelay",
        )
        .invalidNel
    }

    (backoffValidation, delayValidation).mapN((_, _) => ())
  }
}

object RetryPolicy {

  def exponential(maxRetries: Int, initialDelay: FiniteDuration): RetryPolicy =
    RetryPolicy(
      maxRetries = Refined.unsafeApply(maxRetries),
      initialDelay = initialDelay,
      maxDelay = FiniteDuration(initialDelay.toMillis * math.pow(2, maxRetries).toLong, initialDelay.unit),
      backoffFactor = 2.0,
    )

  def fixed(maxRetries: Int, delay: FiniteDuration): RetryPolicy =
    RetryPolicy(
      maxRetries = Refined.unsafeApply(maxRetries),
      initialDelay = delay,
      maxDelay = delay,
      backoffFactor = 1.0,
      jitter = false,
    )

  def linear(maxRetries: Int, initialDelay: FiniteDuration): RetryPolicy =
    RetryPolicy(
      maxRetries = Refined.unsafeApply(maxRetries),
      initialDelay = initialDelay,
      maxDelay = FiniteDuration(initialDelay.toMillis * maxRetries, initialDelay.unit),
      backoffFactor = 1.0,
    )

  val default: RetryPolicy      = exponential(3, FiniteDuration(1, "second"))
  val aggressive: RetryPolicy   = exponential(5, FiniteDuration(500, "milliseconds"))
  val conservative: RetryPolicy = exponential(2, FiniteDuration(5, "seconds"))

  implicit val showRetryPolicy: Show[RetryPolicy] = Show.show { policy =>
    s"RetryPolicy(max=${policy.maxRetries.value}, initial=${policy.initialDelay}, max=${policy.maxDelay}, factor=${policy.backoffFactor})"
  }
}

/**
 * Circuit breaker configuration for fault tolerance.
 */
case class CircuitBreakerConfig(
  failureThreshold: PositiveInt,
  resetTimeout: FiniteDuration,
  callTimeout: FiniteDuration,
  maxConcurrentCalls: PositiveInt)

object CircuitBreakerConfig {

  val default: CircuitBreakerConfig = CircuitBreakerConfig(
    failureThreshold = Refined.unsafeApply(5),
    resetTimeout = FiniteDuration(30, "seconds"),
    callTimeout = FiniteDuration(10, "seconds"),
    maxConcurrentCalls = Refined.unsafeApply(100),
  )

  implicit val showCircuitBreakerConfig: Show[CircuitBreakerConfig] = Show.show { config =>
    s"CircuitBreaker(failures=${config.failureThreshold.value}, reset=${config.resetTimeout}, timeout=${config.callTimeout})"
  }
}

// ===============================
// EXECUTION ENGINE CONFIG
// ===============================

/**
 * Spark configuration settings.
 */
case class SparkConfig(
  appName: NonEmptyString,
  master: Option[String] = None,
  executorMemory: String = "2g",
  executorCores: PositiveInt = Refined.unsafeApply(2),
  driverMemory: String = "1g",
  maxResultSize: String = "1g",
  serializer: String = "org.apache.spark.serializer.KryoSerializer",
  dynamicAllocation: Boolean = true,
  adaptiveQueryExecution: Boolean = true,
  additionalProps: Map[String, String] = Map.empty) {

  def withMemory(executor: String, driver: String = "1g"): SparkConfig =
    copy(executorMemory = executor, driverMemory = driver)

  def withCores(cores: Int): SparkConfig =
    copy(executorCores = Refined.unsafeApply(cores))

  def withMaster(masterUrl: String): SparkConfig =
    copy(master = Some(masterUrl))

  def withProperty(key: String, value: String): SparkConfig =
    copy(additionalProps = additionalProps + (key -> value))

  def validate: ValidatedNel[ConfigError, Unit] = {
    val memoryPattern = """^\d+[gGmM]$""".r

    val executorMemValidation = memoryPattern.findFirstIn(executorMemory) match {
      case Some(_) => ().validNel
      case None =>
        ConfigError
          .InvalidFormat("executorMemory", executorMemory, "format like '2g' or '512m'")
          .invalidNel
    }

    val driverMemValidation = memoryPattern.findFirstIn(driverMemory) match {
      case Some(_) => ().validNel
      case None =>
        ConfigError
          .InvalidFormat("driverMemory", driverMemory, "format like '1g' or '256m'")
          .invalidNel
    }

    (executorMemValidation, driverMemValidation).mapN((_, _) => ())
  }
}

object SparkConfig {

  def default(appName: String): SparkConfig =
    SparkConfig(appName = Refined.unsafeApply(appName))

  def local(appName: String, cores: String = "*"): SparkConfig =
    SparkConfig(
      appName = Refined.unsafeApply(appName),
      master = Some(s"local[$cores]"),
      dynamicAllocation = false,
    )

  def cluster(appName: String, masterUrl: String): SparkConfig =
    SparkConfig(
      appName = Refined.unsafeApply(appName),
      master = Some(masterUrl),
    )

  implicit val showSparkConfig: Show[SparkConfig] = Show.show { config =>
    s"SparkConfig(${config.appName.value}, ${config.master.getOrElse("auto")}, mem=${config.executorMemory})"
  }
}

/**
 * Flink configuration settings.
 */
case class FlinkConfig(
  jobName: NonEmptyString,
  parallelism: PositiveInt = Refined.unsafeApply(1),
  taskManagerMemory: String = "1024m",
  jobManagerMemory: String = "1024m",
  checkpointInterval: Option[FiniteDuration] = None,
  restartStrategy: FlinkRestartStrategy = FlinkRestartStrategy.FixedDelay,
  additionalProps: Map[String, String] = Map.empty) {

  def withParallelism(level: Int): FlinkConfig =
    copy(parallelism = Refined.unsafeApply(level))

  def withCheckpointing(interval: FiniteDuration): FlinkConfig =
    copy(checkpointInterval = Some(interval))

  def withProperty(key: String, value: String): FlinkConfig =
    copy(additionalProps = additionalProps + (key -> value))
}

sealed trait FlinkRestartStrategy extends Product with Serializable

object FlinkRestartStrategy {
  case object NoRestart extends FlinkRestartStrategy

  case object FixedDelay extends FlinkRestartStrategy

  case object FailureRate extends FlinkRestartStrategy

  case object Exponential extends FlinkRestartStrategy
}

object FlinkConfig {

  def default(jobName: String): FlinkConfig =
    FlinkConfig(jobName = Refined.unsafeApply(jobName))

  implicit val showFlinkConfig: Show[FlinkConfig] = Show.show { config =>
    s"FlinkConfig(${config.jobName.value}, parallelism=${config.parallelism.value})"
  }
}

// ===============================
// MONITORING & OBSERVABILITY
// ===============================

/**
 * Monitoring and observability configuration.
 */
case class MonitoringConfig(
  metricsEnabled: Boolean = true,
  tracingEnabled: Boolean = false,
  loggingLevel: LogLevel = LogLevel.Info,
  metricsPort: Option[PortNumber] = None,
  healthCheckPort: Option[PortNumber] = None,
  prometheusEnabled: Boolean = false,
  customMetrics: Map[String, String] = Map.empty) {

  def withMetrics(enabled: Boolean, port: Option[Int] = None): MonitoringConfig =
    copy(
      metricsEnabled = enabled,
      metricsPort = port.map(p => Refined.unsafeApply(p)),
    )

  def withLogging(level: LogLevel): MonitoringConfig =
    copy(loggingLevel = level)

  def withTracing(enabled: Boolean): MonitoringConfig =
    copy(tracingEnabled = enabled)
}

sealed trait LogLevel extends Product with Serializable {
  def level: Int
}

object LogLevel {
  case object Trace extends LogLevel {
    val level = 0
  }

  case object Debug extends LogLevel {
    val level = 1
  }

  case object Info extends LogLevel {
    val level = 2
  }

  case object Warn extends LogLevel {
    val level = 3
  }

  case object Error extends LogLevel {
    val level = 4
  }

  def fromString(level: String): ValidatedNel[ConfigError, LogLevel] = level.toUpperCase match {
    case "TRACE"            => Trace.validNel
    case "DEBUG"            => Debug.validNel
    case "INFO"             => Info.validNel
    case "WARN" | "WARNING" => Warn.validNel
    case "ERROR"            => Error.validNel
    case _ => ConfigError.InvalidValue("logLevel", level, "TRACE|DEBUG|INFO|WARN|ERROR").invalidNel
  }

  implicit val showLogLevel: Show[LogLevel] = Show.show {
    case Trace => "TRACE"
    case Debug => "DEBUG"
    case Info  => "INFO"
    case Warn  => "WARN"
    case Error => "ERROR"
  }
}

object MonitoringConfig {

  val default: MonitoringConfig = MonitoringConfig()

  def production: MonitoringConfig = MonitoringConfig(
    metricsEnabled = true,
    tracingEnabled = true,
    loggingLevel = LogLevel.Info,
    prometheusEnabled = true,
  )

  implicit val showMonitoringConfig: Show[MonitoringConfig] = Show.show { config =>
    s"Monitoring(metrics=${config.metricsEnabled}, tracing=${config.tracingEnabled}, level=${config.loggingLevel.show})"
  }
}

// ===============================
// PIPELINE CONFIGURATION
// ===============================

/**
 * Complete pipeline configuration. Aggregates all configuration aspects for a data pipeline.
 */
case class PipelineConfig(
  name: NonEmptyString,
  version: String = "1.0.0",
  environment: Environment,
  source: DataSource,
  sink: DataSink,
  qualityRules: QualityRules = QualityRules.empty,
  sparkConfig: Option[SparkConfig] = None,
  flinkConfig: Option[FlinkConfig] = None,
  retryPolicy: RetryPolicy = RetryPolicy.default,
  circuitBreaker: CircuitBreakerConfig = CircuitBreakerConfig.default,
  monitoring: MonitoringConfig = MonitoringConfig.default,
  tags: Map[String, String] = Map.empty,
  settings: Map[String, String] = Map.empty,
  createdAt: Instant = Instant.now(),
  configId: String = UUID.randomUUID().toString) {

  def withTag(key: String, value: String): PipelineConfig =
    copy(tags = tags + (key -> value))

  def withQuality(rules: QualityRules): PipelineConfig =
    copy(qualityRules = rules)

  def withSpark(config: SparkConfig): PipelineConfig =
    copy(sparkConfig = Some(config))

  def withFlink(config: FlinkConfig): PipelineConfig =
    copy(flinkConfig = Some(config))

  def validate: ValidatedNel[ConfigError, Unit] = {
    val engineValidation = (sparkConfig, flinkConfig) match {
      case (Some(_), Some(_)) =>
        ConfigError
          .ConflictingValues(
            "sparkConfig",
            "flinkConfig",
            "Cannot specify both Spark and Flink configs",
          )
          .invalidNel
      case (None, None) =>
        ConfigError.CustomError("Must specify either Spark or Flink configuration").invalidNel
      case _ => ().validNel
    }

    val sparkValidation = sparkConfig.traverse(_.validate).map(_ => ())
    val retryValidation = retryPolicy.validate

    (engineValidation, sparkValidation, retryValidation).mapN(
      (
        _,
        _,
        _,
      ) => (),
    )
  }
}

object PipelineConfig {

  /**
   * Fluent builder for pipeline configuration.
   */
  case class PipelineConfigBuilder private (
    name: Option[NonEmptyString] = None,
    version: String = "1.0.0",
    environment: Option[Environment] = None,
    source: Option[DataSource] = None,
    sink: Option[DataSink] = None,
    qualityRules: QualityRules = QualityRules.empty,
    sparkConfig: Option[SparkConfig] = None,
    flinkConfig: Option[FlinkConfig] = None,
    retryPolicy: RetryPolicy = RetryPolicy.default,
    circuitBreaker: CircuitBreakerConfig = CircuitBreakerConfig.default,
    monitoring: MonitoringConfig = MonitoringConfig.default,
    tags: Map[String, String] = Map.empty) {

    def withName(pipelineName: String): PipelineConfigBuilder =
      copy(name = Some(Refined.unsafeApply(pipelineName)))

    def withVersion(v: String): PipelineConfigBuilder =
      copy(version = v)

    def withEnvironment(env: Environment): PipelineConfigBuilder =
      copy(environment = Some(env))

    def withSource(dataSource: DataSource): PipelineConfigBuilder =
      copy(source = Some(dataSource))

    def withSink(dataSink: DataSink): PipelineConfigBuilder =
      copy(sink = Some(dataSink))

    def withQualityRules(rules: QualityRules): PipelineConfigBuilder =
      copy(qualityRules = rules)

    def withSparkConfig(config: SparkConfig): PipelineConfigBuilder =
      copy(sparkConfig = Some(config))

    def withFlinkConfig(config: FlinkConfig): PipelineConfigBuilder =
      copy(flinkConfig = Some(config))

    def withRetryPolicy(policy: RetryPolicy): PipelineConfigBuilder =
      copy(retryPolicy = policy)

    def withCircuitBreaker(config: CircuitBreakerConfig): PipelineConfigBuilder =
      copy(circuitBreaker = config)

    def withMonitoring(config: MonitoringConfig): PipelineConfigBuilder =
      copy(monitoring = config)

    def withTag(key: String, value: String): PipelineConfigBuilder =
      copy(tags = tags + (key -> value))

    def build: ValidatedNel[ConfigError, PipelineConfig] = {
      val nameValidation        = name.toValidNel(ConfigError.MissingRequired("name"))
      val environmentValidation = environment.toValidNel(ConfigError.MissingRequired("environment"))
      val sourceValidation      = source.toValidNel(ConfigError.MissingRequired("source"))
      val sinkValidation        = sink.toValidNel(ConfigError.MissingRequired("sink"))

      (nameValidation, environmentValidation, sourceValidation, sinkValidation).mapN {
        (
          n,
          env,
          src,
          snk,
        ) =>
          PipelineConfig(
            name = n,
            version = version,
            environment = env,
            source = src,
            sink = snk,
            qualityRules = qualityRules,
            sparkConfig = sparkConfig,
            flinkConfig = flinkConfig,
            retryPolicy = retryPolicy,
            circuitBreaker = circuitBreaker,
            monitoring = monitoring,
            tags = tags,
          )
      }.andThen(config => config.validate.map(_ => config))
    }
  }

  def builder: PipelineConfigBuilder = PipelineConfigBuilder()

  /**
   * Parse configuration from a key-value map. This enables loading from environment variables, properties
   * files, etc.
   */
  def fromMap(configMap: Map[String, String]): ValidatedNel[ConfigError, PipelineConfig] = {

    def getString(key: String): ValidatedNel[ConfigError, String] =
      configMap.get(key).toValidNel(ConfigError.MissingRequired(key))

    

    

    

    // Parse all configuration components
    val nameValidation = getString("pipeline.name")
      .map(name => Refined.unsafeApply(name): NonEmptyString)

    val environmentValidation = getString("pipeline.environment")
      .andThen(Environment.fromString)

    // This is a simplified version - in practice you'd parse source/sink configs too
    val sourceValidation =
      ConfigError.CustomError("Source parsing not implemented in fromMap").invalidNel[DataSource]
    val sinkValidation =
      ConfigError.CustomError("Sink parsing not implemented in fromMap").invalidNel[DataSink]

    (nameValidation, environmentValidation, sourceValidation, sinkValidation).mapN {
      (
        name,
        env,
        source,
        sink,
      ) =>
        PipelineConfig(
          name = name,
          environment = env,
          source = source,
          sink = sink,
        )
    }
  }

  implicit val showPipelineConfig: Show[PipelineConfig] = Show.show { config =>
    s"""PipelineConfig(
       |  name: ${config.name.value}
       |  version: ${config.version}
       |  environment: ${config.environment.show}
       |  source: ${config.source.getClass.getSimpleName}
       |  sink: ${config.sink.getClass.getSimpleName}
       |  engine: ${config.sparkConfig
        .map(_ => "Spark")
        .orElse(config.flinkConfig.map(_ => "Flink"))
        .getOrElse("None")}
       |  monitoring: ${config.monitoring.show}
       |  tags: ${config.tags.size}
       |)""".stripMargin
  }
}

// ===============================
// CONFIGURATION UTILITIES
// ===============================

/**
 * Utilities for working with configuration.
 */
object ConfigUtils {

  /**
   * Merge two pipeline configurations with precedence. The second configuration takes precedence over the
   * first.
   */
  def merge(base: PipelineConfig, pipelineConfig: PipelineConfig): PipelineConfig =
    base.copy(
      name = pipelineConfig.name,
      version = pipelineConfig.version,
      environment = pipelineConfig.environment,
      source = pipelineConfig.source,
      sink = pipelineConfig.sink,
      qualityRules = pipelineConfig.qualityRules,
      sparkConfig = pipelineConfig.sparkConfig.orElse(base.sparkConfig),
      flinkConfig = pipelineConfig.flinkConfig.orElse(base.flinkConfig),
      retryPolicy = pipelineConfig.retryPolicy,
      circuitBreaker = pipelineConfig.circuitBreaker,
      monitoring = pipelineConfig.monitoring,
      tags = base.tags ++ pipelineConfig.tags,
    )

  /**
   * Create environment-specific configuration defaults.
   */
  def environmentDefaults(env: Environment): PipelineConfigBuilder = {
    val monitoring = if (env.isProduction) {
      MonitoringConfig.production
    } else {
      MonitoringConfig.default
    }

    PipelineConfig.builder
      .withEnvironment(env)
      .withRetryPolicy(RetryPolicy.exponential(env.defaultRetries, FiniteDuration(1, "second")))
      .withMonitoring(monitoring)
  }

  /**
   * Validate a complete configuration and return all errors.
   */
  def validateConfiguration(config: PipelineConfig): ValidatedNel[ConfigError, Unit] =
    config.validate
}
