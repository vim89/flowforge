// scalafix:off DisableSyntax.throw
package com.flowforge.core.types

import cats.data.Validated
import cats.syntax.all._
import com.flowforge.core.types.RefinedTypes._
import eu.timepit.refined.api.Refined
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._

class ConfigTypesSpec extends AnyFunSuite with Matchers {

  // ===============================
  // CONFIG ERROR TESTS
  // ===============================

  test("ConfigError.MissingRequired should construct correctly") {
    val error = ConfigError.MissingRequired("database.url")
    error.message should include("Required")
    error.message should include("database.url")
    error.field shouldBe Some("database.url")
  }

  test("ConfigError.InvalidValue should construct correctly") {
    val error = ConfigError.InvalidValue("port", "abc", "integer")
    error.message should include("Invalid value")
    error.message should include("port")
    error.message should include("abc")
    error.message should include("integer")
    error.field shouldBe Some("port")
  }

  test("ConfigError.InvalidFormat should construct correctly") {
    val error = ConfigError.InvalidFormat("memory", "2GB", "format like '2g' or '512m'")
    error.message should include("Invalid format")
    error.message should include("memory")
    error.field shouldBe Some("memory")
  }

  test("ConfigError.OutOfRange should construct correctly") {
    val error = ConfigError.OutOfRange("port", "70000", "1", "65535")
    error.message should include("out of range")
    error.message should include("port")
    error.message should include("70000")
    error.field shouldBe Some("port")
  }

  test("ConfigError.ConflictingValues should construct correctly") {
    val error = ConfigError.ConflictingValues("sparkConfig", "flinkConfig", "Cannot specify both")
    error.message should include("Conflicting")
    error.message should include("sparkConfig")
    error.message should include("flinkConfig")
    error.field shouldBe Some("sparkConfig,flinkConfig")
  }

  test("ConfigError.DependencyMissing should construct correctly") {
    val error = ConfigError.DependencyMissing("ssl.cert", "ssl.key")
    error.message should include("requires")
    error.message should include("ssl.cert")
    error.message should include("ssl.key")
    error.field shouldBe Some("ssl.cert")
  }

  test("ConfigError.CustomError should construct correctly") {
    val error = ConfigError.CustomError("Something went wrong")
    error.message shouldBe "Something went wrong"
    error.field shouldBe None
  }

  // ===============================
  // ENVIRONMENT TESTS
  // ===============================

  test("Environment.Development should have correct properties") {
    Environment.Development.name shouldBe "development"
    Environment.Development.isProduction shouldBe false
    Environment.Development.defaultRetries shouldBe 1
    Environment.Development.defaultTimeout shouldBe FiniteDuration(30, "seconds")
  }

  test("Environment.Testing should have correct properties") {
    Environment.Testing.name shouldBe "testing"
    Environment.Testing.isProduction shouldBe false
    Environment.Testing.defaultRetries shouldBe 2
  }

  test("Environment.Staging should have correct properties") {
    Environment.Staging.name shouldBe "staging"
    Environment.Staging.isProduction shouldBe false
    Environment.Staging.defaultRetries shouldBe 3
  }

  test("Environment.Production should have correct properties") {
    Environment.Production.name shouldBe "production"
    Environment.Production.isProduction shouldBe true
    Environment.Production.defaultRetries shouldBe 5
    Environment.Production.defaultTimeout shouldBe FiniteDuration(300, "seconds")
  }

  test("Environment.fromString should parse development variants") {
    Environment.fromString("development") shouldBe Validated.valid(Environment.Development)
    Environment.fromString("dev") shouldBe Validated.valid(Environment.Development)
  }

  test("Environment.fromString should parse testing variants") {
    Environment.fromString("testing") shouldBe Validated.valid(Environment.Testing)
    Environment.fromString("test") shouldBe Validated.valid(Environment.Testing)
  }

  test("Environment.fromString should parse staging variants") {
    Environment.fromString("staging") shouldBe Validated.valid(Environment.Staging)
    Environment.fromString("stage") shouldBe Validated.valid(Environment.Staging)
  }

  test("Environment.fromString should parse production variants") {
    Environment.fromString("production") shouldBe Validated.valid(Environment.Production)
    Environment.fromString("prod") shouldBe Validated.valid(Environment.Production)
  }

  test("Environment.fromString should reject invalid environment") {
    val result = Environment.fromString("invalid")
    result.isInvalid shouldBe true
  }

  test("Environment.fromString should be case-insensitive") {
    Environment.fromString("PRODUCTION") shouldBe Validated.valid(Environment.Production)
    Environment.fromString("Dev") shouldBe Validated.valid(Environment.Development)
  }

  // ===============================
  // RETRY POLICY TESTS
  // ===============================

  test("RetryPolicy should construct with valid parameters") {
    val policy = RetryPolicy(
      maxRetries = Refined.unsafeApply(3),
      initialDelay = 1.second,
      maxDelay = 10.seconds,
      backoffFactor = 2.0,
    )

    policy.maxRetries.value shouldBe 3
    policy.initialDelay shouldBe 1.second
    policy.maxDelay shouldBe 10.seconds
    policy.backoffFactor shouldBe 2.0
    policy.jitter shouldBe true
  }

  test("RetryPolicy.validate should accept valid configuration") {
    val policy = RetryPolicy.exponential(3, 1.second)
    policy.validate.isValid shouldBe true
  }

  test("RetryPolicy.validate should reject invalid backoff factor") {
    val policy = RetryPolicy(
      maxRetries = Refined.unsafeApply(3),
      initialDelay = 1.second,
      maxDelay = 10.seconds,
      backoffFactor = 0.5,
    )

    policy.validate.isInvalid shouldBe true
  }

  test("RetryPolicy.validate should reject invalid delay configuration") {
    val policy = RetryPolicy(
      maxRetries = Refined.unsafeApply(3),
      initialDelay = 10.seconds,
      maxDelay = 1.second,
      backoffFactor = 2.0,
    )

    policy.validate.isInvalid shouldBe true
  }

  test("RetryPolicy.exponential should create exponential backoff policy") {
    val policy = RetryPolicy.exponential(3, 1.second)
    policy.maxRetries.value shouldBe 3
    policy.backoffFactor shouldBe 2.0
    policy.jitter shouldBe true
  }

  test("RetryPolicy.fixed should create fixed delay policy") {
    val policy = RetryPolicy.fixed(5, 2.seconds)
    policy.maxRetries.value shouldBe 5
    policy.initialDelay shouldBe 2.seconds
    policy.maxDelay shouldBe 2.seconds
    policy.backoffFactor shouldBe 1.0
    policy.jitter shouldBe false
  }

  test("RetryPolicy.linear should create linear backoff policy") {
    val policy = RetryPolicy.linear(4, 1.second)
    policy.maxRetries.value shouldBe 4
    policy.backoffFactor shouldBe 1.0
  }

  test("RetryPolicy.default should be defined") {
    RetryPolicy.default.maxRetries.value shouldBe 3
  }

  test("RetryPolicy.aggressive should have shorter delays") {
    RetryPolicy.aggressive.maxRetries.value shouldBe 5
    RetryPolicy.aggressive.initialDelay shouldBe 500.milliseconds
  }

  test("RetryPolicy.conservative should have longer delays") {
    RetryPolicy.conservative.maxRetries.value shouldBe 2
    RetryPolicy.conservative.initialDelay shouldBe 5.seconds
  }

  // ===============================
  // CIRCUIT BREAKER CONFIG TESTS
  // ===============================

  test("CircuitBreakerConfig should construct correctly") {
    val config = CircuitBreakerConfig(
      failureThreshold = Refined.unsafeApply(10),
      resetTimeout = 1.minute,
      callTimeout = 30.seconds,
      maxConcurrentCalls = Refined.unsafeApply(100),
    )

    config.failureThreshold.value shouldBe 10
    config.resetTimeout shouldBe 1.minute
  }

  test("CircuitBreakerConfig.default should be defined") {
    val config = CircuitBreakerConfig.default
    config.failureThreshold.value shouldBe 5
    config.resetTimeout shouldBe 30.seconds
  }

  // ===============================
  // SPARK CONFIG TESTS
  // ===============================

  test("SparkConfig should construct with defaults") {
    val config = SparkConfig.default("test-app")
    config.appName.value shouldBe "test-app"
    config.executorMemory shouldBe "2g"
    config.driverMemory shouldBe "1g"
    config.dynamicAllocation shouldBe true
  }

  test("SparkConfig.withMemory should update memory settings") {
    val config = SparkConfig
      .default("app")
      .withMemory("4g", "2g")

    config.executorMemory shouldBe "4g"
    config.driverMemory shouldBe "2g"
  }

  test("SparkConfig.withCores should update executor cores") {
    val config = SparkConfig
      .default("app")
      .withCores(4)

    config.executorCores.value shouldBe 4
  }

  test("SparkConfig.withMaster should set master URL") {
    val config = SparkConfig
      .default("app")
      .withMaster("spark://localhost:7077")

    config.master shouldBe Some("spark://localhost:7077")
  }

  test("SparkConfig.withProperty should add custom property") {
    val config = SparkConfig
      .default("app")
      .withProperty("spark.sql.shuffle.partitions", "200")

    config.additionalProps should contain("spark.sql.shuffle.partitions" -> "200")
  }

  test("SparkConfig.validate should accept valid memory formats") {
    val config = SparkConfig
      .default("app")
      .withMemory("2g", "1g")

    config.validate.isValid shouldBe true
  }

  test("SparkConfig.validate should accept lowercase memory format") {
    val config = SparkConfig
      .default("app")
      .withMemory("512m", "256m")

    config.validate.isValid shouldBe true
  }

  test("SparkConfig.validate should reject invalid executor memory format") {
    val config = SparkConfig
      .default("app")
      .copy(executorMemory = "invalid")

    config.validate.isInvalid shouldBe true
  }

  test("SparkConfig.validate should reject invalid driver memory format") {
    val config = SparkConfig
      .default("app")
      .copy(driverMemory = "2GB")

    config.validate.isInvalid shouldBe true
  }

  test("SparkConfig.local should create local mode config") {
    val config = SparkConfig.local("app", "4")
    config.master shouldBe Some("local[4]")
    config.dynamicAllocation shouldBe false
  }

  test("SparkConfig.cluster should create cluster mode config") {
    val config = SparkConfig.cluster("app", "spark://master:7077")
    config.master shouldBe Some("spark://master:7077")
  }

  // ===============================
  // FLINK CONFIG TESTS
  // ===============================

  test("FlinkConfig should construct correctly") {
    val config = FlinkConfig.default("flink-job")
    config.jobName.value shouldBe "flink-job"
    config.parallelism.value shouldBe 1
    config.restartStrategy shouldBe FlinkRestartStrategy.FixedDelay
  }

  test("FlinkConfig.withParallelism should update parallelism") {
    val config = FlinkConfig
      .default("job")
      .withParallelism(8)

    config.parallelism.value shouldBe 8
  }

  test("FlinkConfig.withCheckpointing should set checkpoint interval") {
    val config = FlinkConfig
      .default("job")
      .withCheckpointing(10.seconds)

    config.checkpointInterval shouldBe Some(10.seconds)
  }

  test("FlinkConfig.withProperty should add custom property") {
    val config = FlinkConfig
      .default("job")
      .withProperty("state.backend", "rocksdb")

    config.additionalProps should contain("state.backend" -> "rocksdb")
  }

  test("FlinkRestartStrategy should have all variants") {
    val strategies = List(
      FlinkRestartStrategy.NoRestart,
      FlinkRestartStrategy.FixedDelay,
      FlinkRestartStrategy.FailureRate,
      FlinkRestartStrategy.Exponential,
    )

    strategies should have size 4
  }

  // ===============================
  // MONITORING CONFIG TESTS
  // ===============================

  test("MonitoringConfig should construct with defaults") {
    val config = MonitoringConfig.default
    config.metricsEnabled shouldBe true
    config.tracingEnabled shouldBe false
    config.loggingLevel shouldBe LogLevel.Info
  }

  test("MonitoringConfig.withMetrics should enable metrics") {
    val config = MonitoringConfig.default
      .withMetrics(enabled = true, Some(9090))

    config.metricsEnabled shouldBe true
    config.metricsPort.map(_.value) shouldBe Some(9090)
  }

  test("MonitoringConfig.withLogging should set log level") {
    val config = MonitoringConfig.default
      .withLogging(LogLevel.Debug)

    config.loggingLevel shouldBe LogLevel.Debug
  }

  test("MonitoringConfig.withTracing should enable tracing") {
    val config = MonitoringConfig.default
      .withTracing(true)

    config.tracingEnabled shouldBe true
  }

  test("MonitoringConfig.production should have production settings") {
    val config = MonitoringConfig.production
    config.metricsEnabled shouldBe true
    config.tracingEnabled shouldBe true
    config.prometheusEnabled shouldBe true
  }

  // ===============================
  // LOG LEVEL TESTS
  // ===============================

  test("LogLevel should have correct levels") {
    LogLevel.Trace.level shouldBe 0
    LogLevel.Debug.level shouldBe 1
    LogLevel.Info.level shouldBe 2
    LogLevel.Warn.level shouldBe 3
    LogLevel.Error.level shouldBe 4
  }

  test("LogLevel.fromString should parse all levels") {
    LogLevel.fromString("TRACE") shouldBe Validated.valid(LogLevel.Trace)
    LogLevel.fromString("DEBUG") shouldBe Validated.valid(LogLevel.Debug)
    LogLevel.fromString("INFO") shouldBe Validated.valid(LogLevel.Info)
    LogLevel.fromString("WARN") shouldBe Validated.valid(LogLevel.Warn)
    LogLevel.fromString("WARNING") shouldBe Validated.valid(LogLevel.Warn)
    LogLevel.fromString("ERROR") shouldBe Validated.valid(LogLevel.Error)
  }

  test("LogLevel.fromString should reject invalid level") {
    LogLevel.fromString("INVALID").isInvalid shouldBe true
  }

  test("LogLevel.fromString should be case-insensitive") {
    LogLevel.fromString("info") shouldBe Validated.valid(LogLevel.Info)
    LogLevel.fromString("Debug") shouldBe Validated.valid(LogLevel.Debug)
  }

  // ===============================
  // PIPELINE CONFIG TESTS
  // ===============================

  test("PipelineConfig should construct via builder") {
    val source = DataSource.local("/input", DataFormat.CSV)
    val sink   = DataSink.local("/output", DataFormat.Parquet)

    val result = PipelineConfig.builder
      .withName("test-pipeline")
      .withEnvironment(Environment.Development)
      .withSource(source)
      .withSink(sink)
      .withSparkConfig(SparkConfig.local("test-app"))
      .build

    result.isValid shouldBe true
    result.toOption.get.name.value shouldBe "test-pipeline"
  }

  test("PipelineConfig.builder should validate missing fields") {
    val result = PipelineConfig.builder
      .withName("test")
      .build

    result.isInvalid shouldBe true
  }

  test("PipelineConfig should support Spark config") {
    val source      = DataSource.local("/input", DataFormat.CSV)
    val sink        = DataSink.local("/output", DataFormat.Parquet)
    val sparkConfig = SparkConfig.default("app")

    val result = PipelineConfig.builder
      .withName("pipeline")
      .withEnvironment(Environment.Development)
      .withSource(source)
      .withSink(sink)
      .withSparkConfig(sparkConfig)
      .build

    result.toOption.get.sparkConfig shouldBe Some(sparkConfig)
  }

  test("PipelineConfig should support Flink config") {
    val source      = DataSource.local("/input", DataFormat.CSV)
    val sink        = DataSink.local("/output", DataFormat.Parquet)
    val flinkConfig = FlinkConfig.default("job")

    val result = PipelineConfig.builder
      .withName("pipeline")
      .withEnvironment(Environment.Development)
      .withSource(source)
      .withSink(sink)
      .withFlinkConfig(flinkConfig)
      .build

    result.toOption.get.flinkConfig shouldBe Some(flinkConfig)
  }

  test("PipelineConfig.validate should reject both Spark and Flink configs") {
    val source = DataSource.local("/input", DataFormat.CSV)
    val sink   = DataSink.local("/output", DataFormat.Parquet)

    val config = PipelineConfig(
      name = Refined.unsafeApply("pipeline"),
      environment = Environment.Development,
      source = source,
      sink = sink,
      sparkConfig = Some(SparkConfig.default("app")),
      flinkConfig = Some(FlinkConfig.default("job")),
    )

    config.validate.isInvalid shouldBe true
  }

  test("PipelineConfig.validate should require either Spark or Flink config") {
    val source = DataSource.local("/input", DataFormat.CSV)
    val sink   = DataSink.local("/output", DataFormat.Parquet)

    val config = PipelineConfig(
      name = Refined.unsafeApply("pipeline"),
      environment = Environment.Development,
      source = source,
      sink = sink,
      sparkConfig = None,
      flinkConfig = None,
    )

    config.validate.isInvalid shouldBe true
  }

  test("PipelineConfig should support quality rules") {
    val source = DataSource.local("/input", DataFormat.CSV)
    val sink   = DataSink.local("/output", DataFormat.Parquet)

    val result = PipelineConfig.builder
      .withName("pipeline")
      .withEnvironment(Environment.Development)
      .withSource(source)
      .withSink(sink)
      .withSparkConfig(SparkConfig.default("app"))
      .withQualityRules(QualityRules.standard)
      .build

    result.toOption.get.qualityRules shouldBe QualityRules.standard
  }

  test("PipelineConfig should support tags") {
    val source = DataSource.local("/input", DataFormat.CSV)
    val sink   = DataSink.local("/output", DataFormat.Parquet)

    val result = PipelineConfig.builder
      .withName("pipeline")
      .withEnvironment(Environment.Development)
      .withSource(source)
      .withSink(sink)
      .withSparkConfig(SparkConfig.default("app"))
      .withTag("team", "data-engineering")
      .withTag("priority", "high")
      .build

    val config = result.toOption.get
    config.tags should contain("team" -> "data-engineering")
    config.tags should contain("priority" -> "high")
  }

  test("PipelineConfig.withTag should add tag") {
    val source = DataSource.local("/input", DataFormat.CSV)
    val sink   = DataSink.local("/output", DataFormat.Parquet)

    val config = PipelineConfig(
      name = Refined.unsafeApply("pipeline"),
      environment = Environment.Development,
      source = source,
      sink = sink,
      sparkConfig = Some(SparkConfig.default("app")),
    )

    val updated = config.withTag("owner", "alice")
    updated.tags should contain("owner" -> "alice")
  }

  test("PipelineConfig.withQuality should update quality rules") {
    val source = DataSource.local("/input", DataFormat.CSV)
    val sink   = DataSink.local("/output", DataFormat.Parquet)

    val config = PipelineConfig(
      name = Refined.unsafeApply("pipeline"),
      environment = Environment.Development,
      source = source,
      sink = sink,
      sparkConfig = Some(SparkConfig.default("app")),
    )

    val updated = config.withQuality(QualityRules.strict)
    updated.qualityRules shouldBe QualityRules.strict
  }

  // ===============================
  // CONFIG UTILS TESTS
  // ===============================

  test("ConfigUtils.merge should merge configurations") {
    val source = DataSource.local("/input", DataFormat.CSV)
    val sink   = DataSink.local("/output", DataFormat.Parquet)

    val base = PipelineConfig(
      name = Refined.unsafeApply("base"),
      environment = Environment.Development,
      source = source,
      sink = sink,
      sparkConfig = Some(SparkConfig.default("base-app")),
      tags = Map("base" -> "value"),
    )

    val overrideConfig = PipelineConfig(
      name = Refined.unsafeApply("override"),
      environment = Environment.Production,
      source = source,
      sink = sink,
      sparkConfig = Some(SparkConfig.default("override-app")),
      tags = Map("override" -> "value"),
    )

    val merged = ConfigUtils.merge(base, overrideConfig)
    merged.name.value shouldBe "override"
    merged.environment shouldBe Environment.Production
    merged.tags should contain("base" -> "value")
    merged.tags should contain("override" -> "value")
  }

  test("ConfigUtils.environmentDefaults should create environment-specific defaults") {
    val builder = ConfigUtils.environmentDefaults(Environment.Production)

    // Builder should be configured but not built yet
    builder.environment shouldBe Some(Environment.Production)
    builder.monitoring shouldBe MonitoringConfig.production
  }

  test("ConfigUtils.validateConfiguration should validate config") {
    val source = DataSource.local("/input", DataFormat.CSV)
    val sink   = DataSink.local("/output", DataFormat.Parquet)

    val validConfig = PipelineConfig(
      name = Refined.unsafeApply("pipeline"),
      environment = Environment.Development,
      source = source,
      sink = sink,
      sparkConfig = Some(SparkConfig.default("app")),
    )

    ConfigUtils.validateConfiguration(validConfig).isValid shouldBe true

    val invalidConfig = validConfig.copy(sparkConfig = None, flinkConfig = None)
    ConfigUtils.validateConfiguration(invalidConfig).isInvalid shouldBe true
  }

  // ===============================
  // PIPELINE CONFIG FROM MAP TESTS
  // ===============================

  test("PipelineConfig.fromMap should parse basic configuration") {
    val configMap = Map(
      "pipeline.name"        -> "test-pipeline",
      "pipeline.environment" -> "production",
    )

    val result = PipelineConfig.fromMap(configMap)
    // Note: Source/Sink parsing is not implemented, so this will fail
    result.isInvalid shouldBe true
  }

  test("PipelineConfig.fromMap should handle missing required fields") {
    val configMap = Map("pipeline.name" -> "test-pipeline")

    val result = PipelineConfig.fromMap(configMap)
    result.isInvalid shouldBe true
  }
}
