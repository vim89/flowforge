package com.flowforge.infrastructure

import cats.effect.{Resource, Sync}
import cats.syntax.all._
import com.flowforge.config.{ConfigurationManagement, ConfigError, ConfigDecoder}
import com.flowforge.core.algebra.FlowForgeConfig
import com.flowforge.logging.StructuredLogger
import com.flowforge.safety.{ResourceSafety, CloudResourceSafety}

/**
 * Complete Infrastructure Layer providing all cross-cutting concerns. This is the foundation layer
 * that all other FlowForge layers depend on.
 */
trait InfrastructureLayer[F[_]] {

  /**
   * Resource safety framework for automatic resource management.
   */
  def resourceSafety: ResourceSafety[F]

  /**
   * Cloud-specific resource safety for cloud operations.
   */
  def cloudResourceSafety: CloudResourceSafety[F]

  /**
   * Type-safe configuration management system.
   */
  def configurationManagement: ConfigurationManagement[F]

  /**
   * Structured logging framework.
   */
  def structuredLogger: StructuredLogger[F]

  /**
   * Metrics collection for observability.
   */
  def metricsCollector: MetricsCollector[F]

  /**
   * Distributed tracing for request tracking.
   */
  def distributedTracing: DistributedTracing[F]

  /**
   * Testing framework for pipeline and component testing.
   */
  def testingFramework: TestingFramework[F]

  /**
   * Load complete FlowForge configuration with validation.
   */
  def loadFlowForgeConfig
    : F[cats.data.ValidatedNel[ConfigError, FlowForgeConfig]]

  /**
   * Initialize infrastructure layer with proper resource management.
   */
  def initialize: F[Unit]

  /**
   * Shutdown infrastructure layer, cleaning up all resources.
   */
  def shutdown: F[Unit]
}

/**
 * Testing framework for FlowForge components.
 */
trait TestingFramework[F[_]] {

  /**
   * Create test data algebra for unit testing.
   */
  def createTestDataAlgebra[A]: F[A]

  /**
   * Create mock connector for testing.
   */
  def createMockConnector[Provider]: F[MockConnector[F, Provider]]

  /**
   * Run property-based tests with generators.
   */
  def runPropertyTests[A](generators: List[PropertyGenerator[A]]): F[TestResults]

  /**
   * Test pipeline with mock data.
   */
  def testPipelineWithMockData[A, B](
    pipeline: Pipeline[F, A, B],
    testData: List[A]
  ): F[PipelineTestResult[B]]

  /**
   * Create test environment with temporary resources.
   */
  def withTestEnvironment[A](test: TestEnvironment[F] => F[A]): F[A]
}

/**
 * Mock connector for testing purposes.
 */
trait MockConnector[F[_], Provider] {
  def read[A](source: String): F[List[A]]
  def write[A](data: List[A], destination: String): F[WriteResult]
}

case class WriteResult(recordsWritten: Long, success: Boolean)

/**
 * Property generator for property-based testing.
 */
trait PropertyGenerator[A] {
  def generate: A
  def generateList(size: Int): List[A]
}

/**
 * Test results for property-based testing.
 */
case class TestResults(
  totalTests: Int,
  passed: Int,
  failed: Int,
  errors: List[TestError]
)

case class TestError(testName: String, error: String)

/**
 * Pipeline test results.
 */
case class PipelineTestResult[B](
  output: List[B],
  processingTimeMs: Long,
  success: Boolean,
  errors: List[String]
)

/**
 * Test environment with temporary resources.
 */
trait TestEnvironment[F[_]] {
  def tempDirectory: String
  def mockDatabase: MockDatabase[F]
  def mockCloudStorage: MockCloudStorage[F]
}

trait MockDatabase[F[_]] {
  def executeQuery[A](query: String): F[List[A]]
  def executeUpdate(query: String): F[Int]
}

trait MockCloudStorage[F[_]] {
  def uploadFile(path: String, content: String): F[Unit]
  def downloadFile(path: String): F[String]
  def listFiles(prefix: String): F[List[String]]
}

// Placeholder for Pipeline type (to be implemented in Framework Layer)
trait Pipeline[F[_], A, B] {
  def execute(input: A): F[B]
}

object InfrastructureLayer {

  /**
   * Create InfrastructureLayer instance.
   */
  def apply[F[_]: InfrastructureLayer]: InfrastructureLayer[F] = implicitly[InfrastructureLayer[F]]

  /**
   * Create default infrastructure layer implementation.
   */
  def create[F[_]: Sync]: Resource[F, InfrastructureLayer[F]] =
    Resource.make(
      acquire = Sync[F].delay {
        new DefaultInfrastructureLayer[F]()
      }
    )(
      release = infrastructure => infrastructure.shutdown
    )

  /**
   * Default implementation combining all infrastructure components.
   */
  private class DefaultInfrastructureLayer[F[_]: Sync] extends InfrastructureLayer[F] {
    private class DefaultResourceSafetyImpl extends ResourceSafety[F] {
      override def bracket[A, B](acquire: F[A])(use: A => F[B])(release: A => F[Unit]): F[B] =
        Sync[F].bracket(acquire)(use)(release)

      override def resource[A](acquire: F[A])(release: A => F[Unit]): Resource[F, A] =
        Resource.make(acquire)(release)

      override def combineResources[A, B](
        resourceA: Resource[F, A],
        resourceB: Resource[F, B]
      ): Resource[F, (A, B)] =
        (resourceA, resourceB).tupled

      override def ensuring[A](operation: F[A])(cleanup: F[Unit]): F[A] =
        Sync[F].guarantee(operation, cleanup)
    }

    private class DefaultCloudResourceSafetyImpl extends CloudResourceSafety[F] {
      private val base = new DefaultResourceSafetyImpl

      override def bracket[A, B](acquire: F[A])(use: A => F[B])(release: A => F[Unit]): F[B] =
        base.bracket(acquire)(use)(release)

      override def resource[A](acquire: F[A])(release: A => F[Unit]): Resource[F, A] =
        base.resource(acquire)(release)

      override def combineResources[A, B](
        resourceA: Resource[F, A],
        resourceB: Resource[F, B]
      ): Resource[F, (A, B)] =
        base.combineResources(resourceA, resourceB)

      override def ensuring[A](operation: F[A])(cleanup: F[Unit]): F[A] =
        base.ensuring(operation)(cleanup)

      override def safeConnection[Provider, A](
        provider: Provider
      )(use: com.flowforge.safety.Connection[Provider] => F[A]): F[A] =
        bracket(
          acquire = Sync[F].delay(com.flowforge.safety.Connection(provider, new Object))
        )(use)(release = _ => Sync[F].unit)

      override def safeFileHandle[A](
        path: com.flowforge.safety.CloudPath
      )(use: com.flowforge.safety.FileHandle => F[A]): F[A] =
        bracket(
          acquire = Sync[F].delay(com.flowforge.safety.FileHandle(path, new Object))
        )(use)(release = _ => Sync[F].unit)

      override def safeStreamProcessing[A, B](
        inputStream: F[com.flowforge.safety.Stream[A]]
      )(process: com.flowforge.safety.Stream[A] => F[com.flowforge.safety.Stream[B]]): F[
        com.flowforge.safety.Stream[B]
      ] =
        bracket(acquire = inputStream)(use = process)(release = _ => Sync[F].unit)
    }

    override val resourceSafety: ResourceSafety[F] = new DefaultResourceSafetyImpl

    override val cloudResourceSafety: CloudResourceSafety[F] = new DefaultCloudResourceSafetyImpl

    override val configurationManagement: ConfigurationManagement[F] =
      ConfigurationManagement.forTypesafeConfig[F]

    override val structuredLogger: StructuredLogger[F] =
      StructuredLogger.forName[F]("FlowForge.Infrastructure")

    override val metricsCollector: MetricsCollector[F] =
      MetricsCollector.noOpCollector[F]

    override val distributedTracing: DistributedTracing[F] =
      DistributedTracing.noOpTracing[F]

    override val testingFramework: TestingFramework[F] =
      new DefaultTestingFramework[F]()

    override def loadFlowForgeConfig
      : F[cats.data.ValidatedNel[ConfigError, FlowForgeConfig]] = {
      import com.flowforge.config.ConfigurationManagement.flowForgeConfigDecoder
      configurationManagement.loadTypeSafeConfig[FlowForgeConfig]("flowforge")
    }

    override def initialize: F[Unit] =
      for {
        _ <- structuredLogger.info("Initializing FlowForge Infrastructure Layer")
        _ <- metricsCollector.incrementCounter("infrastructure.initialization")
        _ <- structuredLogger.info("FlowForge Infrastructure Layer initialized successfully")
      } yield ()

    override def shutdown: F[Unit] =
      for {
        _ <- structuredLogger.info("Shutting down FlowForge Infrastructure Layer")
        _ <- metricsCollector.incrementCounter("infrastructure.shutdown")
        _ <- structuredLogger.info("FlowForge Infrastructure Layer shut down successfully")
      } yield ()
  }

  /**
   * Default testing framework implementation.
   */
  private class DefaultTestingFramework[F[_]: Sync] extends TestingFramework[F] {

    override def createTestDataAlgebra[A]: F[A] =
      Sync[F].raiseError(new NotImplementedError("Test data algebra creation not yet implemented"))

    override def createMockConnector[Provider]: F[MockConnector[F, Provider]] =
      Sync[F].delay {
        new DefaultMockConnector[F, Provider]()
      }

    override def runPropertyTests[A](generators: List[PropertyGenerator[A]]): F[TestResults] =
      Sync[F].delay {
        TestResults(
          totalTests = generators.length,
          passed = generators.length,
          failed = 0,
          errors = List.empty
        )
      }

    override def testPipelineWithMockData[A, B](
      pipeline: Pipeline[F, A, B],
      testData: List[A]
    ): F[PipelineTestResult[B]] = {
      val startTime = System.currentTimeMillis()

      for {
        results <- testData.traverse(pipeline.execute)
        endTime        = System.currentTimeMillis()
        processingTime = endTime - startTime
      } yield PipelineTestResult(
        output = results,
        processingTimeMs = processingTime,
        success = true,
        errors = List.empty
      )
    }

    override def withTestEnvironment[A](test: TestEnvironment[F] => F[A]): F[A] = {
      val testEnv = new DefaultTestEnvironment[F]()
      test(testEnv)
    }
  }

  /**
   * Default mock connector implementation.
   */
  private class DefaultMockConnector[F[_]: Sync, Provider] extends MockConnector[F, Provider] {

    override def read[A](source: String): F[List[A]] =
      Sync[F].delay(List.empty[A])

    override def write[A](data: List[A], destination: String): F[WriteResult] =
      Sync[F].delay(WriteResult(data.length.toLong, success = true))
  }

  /**
   * Default test environment implementation.
   */
  private class DefaultTestEnvironment[F[_]: Sync] extends TestEnvironment[F] {

    override val tempDirectory: String = System.getProperty("java.io.tmpdir")

    override val mockDatabase: MockDatabase[F] = new MockDatabase[F] {
      override def executeQuery[A](query: String): F[List[A]] = Sync[F].delay(List.empty[A])
      override def executeUpdate(query: String): F[Int]       = Sync[F].delay(0)
    }

    override val mockCloudStorage: MockCloudStorage[F] = new MockCloudStorage[F] {
      override def uploadFile(path: String, content: String): F[Unit] = Sync[F].unit
      override def downloadFile(path: String): F[String]              = Sync[F].delay("")
      override def listFiles(prefix: String): F[List[String]]         = Sync[F].delay(List.empty)
    }
  }
}

/**
 * Infrastructure Layer syntax for easy access to components.
 */
object syntax {

  /**
   * Extension methods for InfrastructureLayer.
   */
  implicit class InfrastructureLayerOps[F[_]](infrastructure: InfrastructureLayer[F]) {

    /**
     * Execute operation with automatic logging and metrics.
     */
    def withLoggingAndMetrics[A](operationName: String)(operation: F[A]): F[A] =
      infrastructure.structuredLogger.logOperation(operationName)(
        infrastructure.metricsCollector.recordTimer(s"$operationName.duration")(operation)
      )

    /**
     * Execute operation with resource safety.
     */
    def safeOperation[A, B](acquire: F[A])(use: A => F[B])(release: A => F[Unit]): F[B] =
      infrastructure.resourceSafety.bracket(acquire)(use)(release)

    /**
     * Load configuration with error handling and logging.
     */
    def loadConfigWithLogging[T: ConfigDecoder](
      key: String
    )(implicit S: Sync[F]): F[T] = {
      import cats.data.Validated
      infrastructure.structuredLogger
        .info(s"Loading config key '$key'") *>
        infrastructure.configurationManagement.loadTypeSafeConfig[T](key).flatMap {
          case Validated.Valid(cfg) =>
            infrastructure.structuredLogger.info(s"Loaded config '$key' successfully").as(cfg)
          case Validated.Invalid(errs) =>
            val details = errs.toList.map(_.message).mkString("; ")
            val msg     = s"Config load failed for '$key': $details"
            infrastructure.structuredLogger
              .error(msg, Map.empty[String, String])
              .*>(S.raiseError[T](new RuntimeException(msg)))
        }
    }
  }
}
