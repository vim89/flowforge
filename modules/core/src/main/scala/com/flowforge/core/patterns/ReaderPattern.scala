package com.flowforge.core.patterns

import cats.data.{ Kleisli, Reader, ReaderT }
import cats.effect.{ Resource, Sync }
import cats.implicits._
import cats.{ Applicative, Monad }
import com.flowforge.core.algebra.{ DataAlgebra, DataDecoder, EffectSystem }
import com.flowforge.core.instances.DataInstances
import com.flowforge.core.types._
import eu.timepit.refined.api.Refined

import java.time.Instant
import scala.concurrent.duration.FiniteDuration

/**
 * 🚀 **FlowForge Reader Pattern - Functional Dependency Injection**
 *
 * This module implements the Reader monad pattern for dependency injection in FlowForge pipelines.
 * It integrates seamlessly with the existing Kleisli-based pipeline architecture to provide clean,
 * composable DI.
 *
 * **Key Benefits:**
 *   - **Type-Safe DI**: All dependencies resolved at compile time
 *   - **Composable**: Reader instances compose naturally via monad operations
 *   - **Testable**: Easy to provide test implementations
 *   - **Pure Functional**: No side effects in dependency resolution
 *   - **Effect Polymorphic**: Works with any effect system F[_]
 *   - **Pipeline Integration**: Works with existing FlowForge components
 *
 * **Usage Patterns:**
 *   - Configuration injection for pipeline components
 *   - Service layer dependency injection
 *   - Cross-cutting concerns (logging, metrics, auditing)
 *   - Multi-environment support (dev, staging, prod)
 *
 * @author
 *   FlowForge Core Team
 * @since 0.1.0
 */

object ReaderPattern {

  // ===============================
  // CORE DEPENDENCY TYPES
  // ===============================

  /**
   * Main dependency container for FlowForge applications
   */
  final case class FlowForgeDependencies[F[_]](
    config: PipelineConfig,
    dataAlgebra: DataAlgebra[F],
    logger: Logger[F],
    metrics: MetricsCollector[F],
    auditService: AuditService[F],
    secretManager: SecretManager[F],
    resourceManager: ResourceManager[F]
  )

  /**
   * Database-specific dependencies
   */
  final case class DatabaseDependencies[F[_]](
    connectionPool: ConnectionPool[F],
    transactionManager: TransactionManager[F],
    migrationService: MigrationService[F]
  )

  /**
   * Cloud services dependencies
   */
  final case class CloudDependencies[F[_]](
    storageService: StorageService[F],
    queueService: QueueService[F],
    notificationService: NotificationService[F],
    monitoringService: MonitoringService[F]
  )

  /**
   * Complete application context integrating with FlowForge types
   */
  final case class AppContext[F[_]](
    core: FlowForgeDependencies[F],
    database: Option[DatabaseDependencies[F]] = None,
    cloud: Option[CloudDependencies[F]] = None,
    environment: Environment,
    requestId: RequestId,
    timestamp: Instant
  ) {

    /**
     * Create a child context with new request ID
     */
    def newRequest: AppContext[F] =
      copy(requestId = RequestId.generate, timestamp = Instant.now())

    /**
     * Access configuration safely
     */
    def config: PipelineConfig = core.config

    /**
     * Get environment-specific configuration
     */
    def envConfig[A](key: String): Option[A] =
      core.config.settings
        .get(s"${environment.toString.toLowerCase}.$key")
        .orElse(core.config.settings.get(key))
        .asInstanceOf[Option[A]]
  }

  // ===============================
  // READER TYPE ALIASES (integrate with existing)
  // ===============================

  /**
   * Core Reader type for FlowForge applications
   */
  type FlowForgeReader[F[_], A] = Reader[AppContext[F], A]

  /**
   * ReaderT for effect-based operations
   */
  type FlowForgeReaderT[F[_], A] = ReaderT[F, AppContext[F], A]

  /**
   * Kleisli arrow with AppContext (integrates with existing pipeline system)
   */
  type ContextualOperation[F[_], A, B] = Kleisli[F, (AppContext[F], A), B]

  /**
   * Pipeline component with dependency injection (extends existing PipelineComponent)
   */
  type DIComponent[F[_], A, B] = ReaderT[F, AppContext[F], Kleisli[F, A, B]]

  // ===============================
  // SERVICE ABSTRACTIONS
  // ===============================

  /**
   * Logging service abstraction
   */
  trait Logger[F[_]] {
    def debug(message: String): F[Unit]
    def info(message: String): F[Unit]
    def warn(message: String): F[Unit]
    def error(message: String, throwable: Option[Throwable] = None): F[Unit]
    def withContext(context: Map[String, String]): Logger[F]
  }

  /**
   * Metrics collection service
   */
  trait MetricsCollector[F[_]] {
    def counter(name: String, value: Long, tags: Map[String, String] = Map.empty): F[Unit]
    def gauge(name: String, value: Double, tags: Map[String, String] = Map.empty): F[Unit]
    def histogram(name: String, value: Double, tags: Map[String, String] = Map.empty): F[Unit]
    def timer[A](name: String, tags: Map[String, String] = Map.empty)(operation: F[A]): F[A]
  }

  /**
   * Audit service for compliance and tracing
   */
  trait AuditService[F[_]] {
    def recordAccess(resource: String, action: String, user: String): F[Unit]
    def recordDataChange(table: String, operation: String, recordCount: Long): F[Unit]
    def recordPipelineExecution(pipelineId: String, status: String): F[Unit]
    def queryAuditLog(query: AuditQuery): F[List[AuditRecord]]
  }

  /**
   * Secret management service
   */
  trait SecretManager[F[_]] {
    def getSecret(key: String): F[Option[String]]
    def storeSecret(key: String, value: String): F[Unit]
    def deleteSecret(key: String): F[Unit]
    def rotateSecret(key: String): F[String]
  }

  /**
   * Resource management service
   */
  trait ResourceManager[F[_]] {
    def acquireResource[R](name: String, config: ResourceConfig): Resource[F, R]
    def releaseResource(name: String): F[Unit]
    def listResources: F[List[ResourceInfo]]
    def healthCheck: F[Map[String, ResourceHealth]]
  }

  // ===============================
  // CLOUD SERVICE ABSTRACTIONS
  // ===============================

  trait StorageService[F[_]] {
    def read(path: String): F[Array[Byte]]
    def write(path: String, data: Array[Byte]): F[Unit]
    def exists(path: String): F[Boolean]
    def list(prefix: String): F[List[String]]
    def delete(path: String): F[Unit]
  }

  trait QueueService[F[_]] {
    def publish[A](queue: String, message: A): F[Unit]
    def subscribe[A](queue: String): F[QueueSubscription[F, A]]
    def createQueue(name: String, config: QueueConfig): F[Unit]
    def deleteQueue(name: String): F[Unit]
  }

  trait NotificationService[F[_]] {
    def sendEmail(to: List[String], subject: String, body: String): F[Unit]
    def sendSlack(channel: String, message: String): F[Unit]
    def sendWebhook(url: String, payload: Map[String, Any]): F[Unit]
  }

  trait MonitoringService[F[_]] {
    def createAlert(name: String, condition: String, actions: List[AlertAction]): F[Unit]
    def updateAlert(name: String, enabled: Boolean): F[Unit]
    def deleteAlert(name: String): F[Unit]
    def listAlerts: F[List[AlertInfo]]
  }

  // ===============================
  // DATABASE ABSTRACTIONS
  // ===============================

  trait ConnectionPool[F[_]] {
    def withConnection[A](operation: Connection[F] => F[A]): F[A]
    def stats: F[PoolStats]
    def health: F[PoolHealth]
  }

  trait TransactionManager[F[_]] {
    def transaction[A](operation: F[A]): F[A]
    def rollback: F[Unit]
    def commit: F[Unit]
  }

  trait MigrationService[F[_]] {
    def runMigrations: F[MigrationResult]
    def rollbackMigration(version: String): F[MigrationResult]
    def migrationStatus: F[List[MigrationInfo]]
  }

  trait Connection[F[_]] {
    def query[A](sql: String, params: List[Any]): F[List[A]]
    def execute(sql: String, params: List[Any]): F[Int]
    def batch(operations: List[SqlOperation]): F[List[Int]]
  }

  // ===============================
  // READER-BASED OPERATIONS
  // ===============================

  /**
   * High-level operations using Reader pattern integrated with FlowForge
   */
  object Operations {

    /**
     * Log a message with automatic context injection
     */
    def log[F[_]: Applicative](level: LogLevel, message: String): FlowForgeReaderT[F, Unit] =
      ReaderT { context =>
        val logger = context.core.logger
        level match {
          case LogLevel.Debug => logger.debug(message)
          case LogLevel.Info  => logger.info(message)
          case LogLevel.Warn  => logger.warn(message)
          case LogLevel.Error => logger.error(message)
        }
      }

    /**
     * Record metrics with context
     */
    def recordMetric[F[_]: Applicative](
      name: String,
      value: Double,
      metricType: MetricType = MetricType.Gauge
    ): FlowForgeReaderT[F, Unit] =
      ReaderT { context =>
        val metrics = context.core.metrics
        val tags = Map(
          "environment" -> context.environment.toString,
          "request_id"  -> context.requestId.value
        )
        metricType match {
          case MetricType.Counter   => metrics.counter(name, value.toLong, tags)
          case MetricType.Gauge     => metrics.gauge(name, value, tags)
          case MetricType.Histogram => metrics.histogram(name, value, tags)
        }
      }

    /**
     * Access configuration with environment awareness
     */
    def getConfig[F[_]: Applicative, A](
      key: String,
      default: A
    ): FlowForgeReaderT[F, A] =
      ReaderT { context =>
        val config = context.envConfig[A](key).getOrElse(default)
        Applicative[F].pure(config)
      }

    /**
     * Execute operation with audit logging
     */
    def withAudit[F[_]: Monad, A](
      resource: String,
      action: String,
      operation: FlowForgeReaderT[F, A]
    ): FlowForgeReaderT[F, A] =
      for {
        context <- ReaderT.ask[F, AppContext[F]]
        _       <- ReaderT.liftF(context.core.auditService.recordAccess(resource, action, "system"))
        result  <- operation
        _ <- ReaderT.liftF(
          context.core.auditService.recordAccess(resource, s"${action}_completed", "system")
        )
      } yield result

    /**
     * Execute operation with retry logic
     */
    def withRetry[F[_]: Monad, A](
      maxRetries: Int,
      operation: FlowForgeReaderT[F, A]
    )(implicit F: EffectSystem[F]): FlowForgeReaderT[F, A] =
      ReaderT { context =>
        def retry(attempt: Int): F[A] =
          operation.run(context).handleErrorWith { error =>
            if (attempt < maxRetries) {
              context.core.logger
                .warn(s"Operation failed, retrying (attempt ${attempt + 1}/$maxRetries)") *>
                retry(attempt + 1)
            } else {
              F.raiseError(error)
            }
          }

        retry(0)
      }

    /**
     * Execute operation with timeout
     */
    def withTimeout[F[_]: Monad, A](
      duration: FiniteDuration,
      operation: FlowForgeReaderT[F, A]
    )(implicit F: EffectSystem[F]): FlowForgeReaderT[F, A] =
      ReaderT { context =>
        F.timeout(operation.run(context), duration).handleErrorWith { _ =>
          context.core.logger.error(s"Operation timed out after $duration") *>
            F.raiseError(
              new java.util.concurrent.TimeoutException(s"Operation timed out after $duration")
            )
        }
      }

    /**
     * Access secret safely
     */
    def getSecret[F[_]: Monad](key: String): FlowForgeReaderT[F, Option[String]] =
      ReaderT { context =>
        context.core.secretManager.getSecret(key)
      }

    /**
     * Create a data reader with dependency injection
     */
    def createDataReader[F[_]: Monad, A](
      source: DataSource
    )(implicit decoder: DataDecoder[A]): FlowForgeReaderT[F, DataAlgebra.Dataset[A]] =
      for {
        context <- ReaderT.ask[F, AppContext[F]]
        dataset <- ReaderT.liftF(
          context.core.dataAlgebra.read[A](source)(decoder)
        )
      } yield dataset
  }

  // ===============================
  // PIPELINE INTEGRATION HELPERS
  // ===============================

  /**
   * Create a pipeline component with dependency injection Integrates with existing FlowForge
   * PipelineComponent system
   */
  def component[F[_]: Monad, A, B](
    name: String,
    logic: (AppContext[F], A) => F[B]
  ): DIComponent[F, A, B] =
    ReaderT { context =>
      Applicative[F].pure {
        Kleisli[F, A, B] { input =>
          logic(context, input)
        }
      }
    }

  /**
   * Create a validation component with context
   */
  def validationComponent[F[_]: Monad, A](
    name: String,
    validator: (AppContext[F], A) => F[cats.data.ValidatedNel[FlowForgeError, A]]
  ): ReaderT[F, AppContext[F], Kleisli[F, A, cats.data.ValidatedNel[FlowForgeError, A]]] =
    ReaderT { context =>
      Applicative[F].pure {
        Kleisli[F, A, cats.data.ValidatedNel[FlowForgeError, A]] { input =>
          validator(context, input)
        }
      }
    }

  /**
   * Compose multiple DI components
   */
  def composeComponents[F[_]: Monad, A, B, C](
    first: DIComponent[F, A, B],
    second: DIComponent[F, B, C]
  ): DIComponent[F, A, C] =
    for {
      comp1 <- first
      comp2 <- second
    } yield comp1 andThen comp2

  // ===============================
  // TESTING SUPPORT
  // ===============================

  /**
   * Create a test context with mock dependencies
   */
  def testContext[F[_]: EffectSystem: Sync]: AppContext[F] = {

    AppContext[F](
      core = FlowForgeDependencies[F](
        config = PipelineConfig(
          name = Refined.unsafeApply("test-pipeline"),
          environment = Environment.Testing,
          source = DataSource.gcs("test-bucket", "test-prefix", DataFormat.Parquet),
          sink = DataSink.gcs("test-output-bucket", "test-output-prefix", DataFormat.Parquet),
          settings = Map("test" -> "true"),
          retryPolicy = RetryPolicy.default,
          qualityRules = QualityRules.empty
        ),
        dataAlgebra = TestImplementations.mockDataAlgebra[F],
        logger = TestImplementations.mockLogger[F],
        metrics = TestImplementations.mockMetrics[F],
        auditService = TestImplementations.mockAuditService[F],
        secretManager = TestImplementations.mockSecretManager[F],
        resourceManager = TestImplementations.mockResourceManager[F]
      ),
      database = Some(
        DatabaseDependencies[F](
          connectionPool = TestImplementations.mockConnectionPool[F],
          transactionManager = TestImplementations.mockTransactionManager[F],
          migrationService = TestImplementations.mockMigrationService[F]
        )
      ),
      cloud = Some(
        CloudDependencies[F](
          storageService = TestImplementations.mockStorageService[F],
          queueService = TestImplementations.mockQueueService[F],
          notificationService = TestImplementations.mockNotificationService[F],
          monitoringService = TestImplementations.mockMonitoringService[F]
        )
      ),
      environment = Environment.Testing,
      requestId = RequestId.generate,
      timestamp = Instant.now()
    )
  }

  // ===============================
  // SUPPORTING TYPES
  // ===============================

  sealed trait LogLevel extends Product with Serializable
  object LogLevel {
    case object Debug extends LogLevel
    case object Info  extends LogLevel
    case object Warn  extends LogLevel
    case object Error extends LogLevel
  }

  sealed trait MetricType extends Product with Serializable
  object MetricType {
    case object Counter   extends MetricType
    case object Gauge     extends MetricType
    case object Histogram extends MetricType
  }

  final case class RequestId(value: String) extends AnyVal
  object RequestId {
    def generate: RequestId = RequestId(java.util.UUID.randomUUID().toString)
  }

  final case class AuditRecord(
    timestamp: Instant,
    resource: String,
    action: String,
    user: String,
    details: Map[String, String]
  )

  final case class AuditQuery(
    startTime: Option[Instant],
    endTime: Option[Instant],
    resource: Option[String],
    action: Option[String],
    user: Option[String]
  )

  final case class ResourceInfo(
    name: String,
    status: ResourceStatus,
    config: Map[String, Any],
    lastHealthCheck: Instant
  )

  sealed trait ResourceStatus extends Product with Serializable
  object ResourceStatus {
    case object Healthy   extends ResourceStatus
    case object Degraded  extends ResourceStatus
    case object Unhealthy extends ResourceStatus
  }

  final case class ResourceHealth(
    status: ResourceStatus,
    message: String,
    lastCheck: Instant
  )

  case class ResourceConfig(
    timeout: scala.concurrent.duration.Duration,
    retryPolicy: RetryPolicy,
    properties: Map[String, String] = Map.empty
  )

  case class QueueSubscription[F[_], A](
    queue: String,
    receive: F[Option[A]],
    ack: A => F[Unit],
    nack: A => F[Unit]
  )

  case class QueueConfig(
    maxSize: Int,
    ttl: scala.concurrent.duration.Duration,
    dlq: Option[String] = None
  )

  case class AlertAction(
    name: String,
    config: Map[String, String]
  )

  case class AlertInfo(
    name: String,
    condition: String,
    enabled: Boolean,
    actions: List[AlertAction]
  )

  case class PoolStats(
    active: Int,
    idle: Int,
    max: Int
  )

  case class PoolHealth(
    healthy: Boolean,
    message: String,
    stats: PoolStats
  )

  case class MigrationResult(
    success: Boolean,
    version: String,
    message: String
  )

  case class MigrationInfo(
    version: String,
    applied: Boolean,
    appliedAt: Option[Instant]
  )

  case class SqlOperation(
    sql: String,
    params: List[Any]
  )

  // ===============================
  // TEST IMPLEMENTATIONS
  // ===============================

  private object TestImplementations {

    def mockDataAlgebra[F[_]: EffectSystem: Sync]: DataAlgebra[F] = DataInstances.createMockDataAlgebra[F]

    def mockLogger[F[_]: EffectSystem]: Logger[F] = new Logger[F] {
      def debug(message: String): F[Unit] =
        implicitly[EffectSystem[F]].delay(println(s"DEBUG: $message"))
      def info(message: String): F[Unit] =
        implicitly[EffectSystem[F]].delay(println(s"INFO: $message"))
      def warn(message: String): F[Unit] =
        implicitly[EffectSystem[F]].delay(println(s"WARN: $message"))
      def error(message: String, throwable: Option[Throwable]): F[Unit] =
        implicitly[EffectSystem[F]].delay(
          println(s"ERROR: $message ${throwable.map(_.getMessage).getOrElse("")}")
        )
      def withContext(context: Map[String, String]): Logger[F] = this
    }

    def mockMetrics[F[_]: EffectSystem]: MetricsCollector[F] = new MetricsCollector[F] {
      def counter(name: String, value: Long, tags: Map[String, String]): F[Unit] =
        implicitly[EffectSystem[F]].delay(println(s"COUNTER: $name = $value $tags"))
      def gauge(name: String, value: Double, tags: Map[String, String]): F[Unit] =
        implicitly[EffectSystem[F]].delay(println(s"GAUGE: $name = $value $tags"))
      def histogram(name: String, value: Double, tags: Map[String, String]): F[Unit] =
        implicitly[EffectSystem[F]].delay(println(s"HISTOGRAM: $name = $value $tags"))
      def timer[A](name: String, tags: Map[String, String])(operation: F[A]): F[A] = operation
    }

    // Simplified mock implementations for other services
    def mockAuditService[F[_]: EffectSystem]: AuditService[F] = new AuditService[F] {
      def recordAccess(resource: String, action: String, user: String): F[Unit] =
        implicitly[EffectSystem[F]].delay(())
      def recordDataChange(table: String, operation: String, recordCount: Long): F[Unit] =
        implicitly[EffectSystem[F]].delay(())
      def recordPipelineExecution(pipelineId: String, status: String): F[Unit] =
        implicitly[EffectSystem[F]].delay(())
      def queryAuditLog(query: AuditQuery): F[List[AuditRecord]] =
        implicitly[EffectSystem[F]].delay(List.empty)
    }

    def mockSecretManager[F[_]: EffectSystem]: SecretManager[F] = new SecretManager[F] {
      def getSecret(key: String): F[Option[String]]        = implicitly[EffectSystem[F]].delay(None)
      def storeSecret(key: String, value: String): F[Unit] = implicitly[EffectSystem[F]].delay(())
      def deleteSecret(key: String): F[Unit]               = implicitly[EffectSystem[F]].delay(())
      def rotateSecret(key: String): F[String] = implicitly[EffectSystem[F]].delay("new-secret")
    }

    def mockResourceManager[F[_]: EffectSystem]: ResourceManager[F] = new ResourceManager[F] {
      def acquireResource[R](name: String, config: ResourceConfig): Resource[F, R] = {
        // Safe no-op resource that yields a null placeholder of requested type R
        val F = implicitly[EffectSystem[F]]
        Resource.make[F, R](F.pure(null.asInstanceOf[R]))(_ => F.unit)
      }
      def releaseResource(name: String): F[Unit] = implicitly[EffectSystem[F]].delay(())
      def listResources: F[List[ResourceInfo]]   = implicitly[EffectSystem[F]].delay(List.empty)
      def healthCheck: F[Map[String, ResourceHealth]] = implicitly[EffectSystem[F]].delay(Map.empty)
    }

    // Database mocks
    def mockConnectionPool[F[_]: EffectSystem]: ConnectionPool[F] = new ConnectionPool[F] {
      def withConnection[A](operation: Connection[F] => F[A]): F[A] = {
        val F = implicitly[EffectSystem[F]]
        val conn = new Connection[F] {
          def query[A](sql: String, params: List[Any]): F[List[A]] = F.pure(Nil)
          def execute(sql: String, params: List[Any]): F[Int]      = F.pure(0)
          def batch(operations: List[SqlOperation]): F[List[Int]] =
            F.pure(List.fill(operations.size)(0))
        }
        operation(conn)
      }
      def stats: F[PoolStats] =
        implicitly[EffectSystem[F]].pure(PoolStats(active = 0, idle = 0, max = 0))
      def health: F[PoolHealth] = implicitly[EffectSystem[F]].pure(
        PoolHealth(healthy = true, message = "mock healthy", stats = PoolStats(0, 0, 0))
      )
    }

    def mockTransactionManager[F[_]: EffectSystem]: TransactionManager[F] =
      new TransactionManager[F] {
        def transaction[A](operation: F[A]): F[A] = operation
        def rollback: F[Unit]                     = implicitly[EffectSystem[F]].delay(())
        def commit: F[Unit]                       = implicitly[EffectSystem[F]].delay(())
      }

    def mockMigrationService[F[_]: EffectSystem]: MigrationService[F] = new MigrationService[F] {
      def runMigrations: F[MigrationResult] =
        implicitly[EffectSystem[F]].pure(
          MigrationResult(success = true, version = "mock", message = "no-op")
        )
      def rollbackMigration(version: String): F[MigrationResult] =
        implicitly[EffectSystem[F]].pure(
          MigrationResult(success = true, version = version, message = "no-op")
        )
      def migrationStatus: F[List[MigrationInfo]] = implicitly[EffectSystem[F]].delay(List.empty)
    }

    // Cloud service mocks
    def mockStorageService[F[_]: EffectSystem]: StorageService[F] = new StorageService[F] {
      def read(path: String): F[Array[Byte]] =
        implicitly[EffectSystem[F]].delay(Array.emptyByteArray)
      def write(path: String, data: Array[Byte]): F[Unit] = implicitly[EffectSystem[F]].delay(())
      def exists(path: String): F[Boolean]                = implicitly[EffectSystem[F]].delay(false)
      def list(prefix: String): F[List[String]] = implicitly[EffectSystem[F]].delay(List.empty)
      def delete(path: String): F[Unit]         = implicitly[EffectSystem[F]].delay(())
    }

    def mockQueueService[F[_]: EffectSystem]: QueueService[F] = new QueueService[F] {
      def publish[A](queue: String, message: A): F[Unit] = implicitly[EffectSystem[F]].delay(())
      def subscribe[A](queue: String): F[QueueSubscription[F, A]] = {
        val F = implicitly[EffectSystem[F]]
        F.pure(
          QueueSubscription[F, A](
            queue = queue,
            receive = F.pure(None),
            ack = (_: A) => F.unit,
            nack = (_: A) => F.unit
          )
        )
      }
      def createQueue(name: String, config: QueueConfig): F[Unit] =
        implicitly[EffectSystem[F]].delay(())
      def deleteQueue(name: String): F[Unit] = implicitly[EffectSystem[F]].delay(())
    }

    def mockNotificationService[F[_]: EffectSystem]: NotificationService[F] =
      new NotificationService[F] {
        def sendEmail(to: List[String], subject: String, body: String): F[Unit] =
          implicitly[EffectSystem[F]].delay(())
        def sendSlack(channel: String, message: String): F[Unit] =
          implicitly[EffectSystem[F]].delay(())
        def sendWebhook(url: String, payload: Map[String, Any]): F[Unit] =
          implicitly[EffectSystem[F]].delay(())
      }

    def mockMonitoringService[F[_]: EffectSystem]: MonitoringService[F] = new MonitoringService[F] {
      def createAlert(name: String, condition: String, actions: List[AlertAction]): F[Unit] =
        implicitly[EffectSystem[F]].delay(())
      def updateAlert(name: String, enabled: Boolean): F[Unit] =
        implicitly[EffectSystem[F]].delay(())
      def deleteAlert(name: String): F[Unit] = implicitly[EffectSystem[F]].delay(())
      def listAlerts: F[List[AlertInfo]]     = implicitly[EffectSystem[F]].delay(List.empty)
    }
  }
}
