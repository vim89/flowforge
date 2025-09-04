# FlowForge Infrastructure Layer Design

> Archived: Superseded by ADR-013 Infrastructure Layer Implementation. See `docs/adr/013-infrastructure-layer.md`.

## Overview

The Infrastructure Layer is the **foundational layer** of FlowForge's layered architecture, providing cross-cutting concerns and system-level services that all other layers depend upon.

## Critical Gap Identified

⚠️ **ARCHITECTURAL ISSUE**: The Infrastructure Layer was **completely missing** from the codebase despite being extensively documented in the layered architecture. This document defines what needs to be implemented.

## Infrastructure Layer Components

### 1. Resource Safety Framework

**Purpose**: Automatic resource management with bracket patterns

```scala
package com.flowforge.infrastructure.safety

trait ResourceSafety[F[_]] {
  def bracket[A, B](acquire: F[A])(use: A => F[B])(release: A => F[Unit]): F[B]
  def bracketCase[A, B](acquire: F[A])(use: A => F[B])(release: (A, ExitCase[Throwable]) => F[Unit]): F[B]
  def resource[A](acquire: F[A])(release: A => F[Unit]): Resource[F, A]
}

// Cloud connector safety
trait CloudResourceSafety[F[_]] extends ResourceSafety[F] {
  def safeConnection[Provider <: CloudProvider, A](
    provider: Provider
  )(use: Connection[Provider] => F[A]): F[A]
  
  def safeFileHandle[A](path: CloudPath)(use: FileHandle => F[A]): F[A]
}

// Stream resource management  
trait StreamResourceSafety[F[_]] {
  def safeStream[A](acquire: F[fs2.Stream[F, A]]): fs2.Stream[F, A]
  def resourceStream[A](resource: Resource[F, A]): fs2.Stream[F, A]
}
```

### 2. Configuration Management System (CCM Replacement)

**Purpose**: Type-safe configuration management replacing CCM

```scala
package com.flowforge.infrastructure.config

trait ConfigurationManagement[F[_]] {
  def loadTypeSafeConfig[T: ConfigDecoder](key: String): F[ValidatedNel[ConfigError, T]]
  def loadOptionalConfig[T: ConfigDecoder](key: String): F[Option[T]]
  def watchConfig[T: ConfigDecoder](key: String): fs2.Stream[F, T]
  def refreshConfig: F[Unit]
}

// Environment-specific configuration
trait EnvironmentConfig[F[_]] {
  def loadForEnvironment[T: ConfigDecoder](
    key: String, 
    env: Environment
  ): F[ValidatedNel[ConfigError, T]]
}

// Configuration validation
trait ConfigValidator[T] {
  def validate(raw: Map[String, String]): ValidatedNel[ConfigError, T]
}

// Built-in configuration types
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
```

### 3. Testing Framework

**Purpose**: Comprehensive testing utilities for pipelines and components

```scala
package com.flowforge.infrastructure.testing

trait TestingFramework[F[_]] {
  def createTestAlgebra[A <: DataAlgebra[F]]: F[A]
  def createMockConnector[Provider <: CloudProvider]: F[CloudConnector[F, Provider]]
  def runPropertyTests[A](generators: List[Gen[A]]): F[TestResults]
}

// Property-based testing support
trait PropertyTesting[F[_]] {
  def testPipelineProperties[A, B](
    pipeline: Pipeline[F, A, B]
  )(generators: Gen[A]): F[PropertyTestResults]
  
  def testAlgebraLaws[A <: DataAlgebra[F]](algebra: A): F[LawTestResults]
}

// Integration testing utilities
trait IntegrationTesting[F[_]] {
  def withTestDatabase[A](test: DatabaseConnection => F[A]): F[A]
  def withTestCloudStorage[A](test: CloudStorage => F[A]): F[A]
  def withTestKafka[A](test: KafkaTestHarness => F[A]): F[A]
}

// Test data generation
trait TestDataGeneration[F[_]] {
  def generateTestDataset[A: Gen](size: Int): F[Dataset[A]]
  def generateCloudData[A: Gen](location: CloudPath, size: Int): F[Unit]
}
```

### 4. Logging & Observability Framework

**Purpose**: Structured logging, metrics, and distributed tracing

```scala
package com.flowforge.infrastructure.observability

// Structured logging
trait StructuredLogger[F[_]] {
  def info(message: String, context: Map[String, String] = Map.empty): F[Unit]
  def warn(message: String, context: Map[String, String] = Map.empty): F[Unit]
  def error(message: String, error: Throwable, context: Map[String, String] = Map.empty): F[Unit]
  def debug(message: String, context: Map[String, String] = Map.empty): F[Unit]
}

// Metrics collection
trait MetricsCollector[F[_]] {
  def incrementCounter(name: String, tags: Map[String, String] = Map.empty): F[Unit]
  def recordGauge(name: String, value: Double, tags: Map[String, String] = Map.empty): F[Unit]
  def recordHistogram(name: String, value: Double, tags: Map[String, String] = Map.empty): F[Unit]
  def recordTimer[A](name: String)(operation: F[A]): F[A]
}

// Distributed tracing  
trait DistributedTracing[F[_]] {
  def createSpan[A](operationName: String)(operation: F[A]): F[A]
  def addSpanTag(key: String, value: String): F[Unit]
  def getCurrentTraceId: F[Option[String]]
}

// Data lineage tracking
trait LineageTracking[F[_]] {
  def recordDatasetAccess[A](dataset: Dataset[A], operation: String): F[Unit]
  def recordTransformation[A, B](
    input: Dataset[A], 
    output: Dataset[B], 
    transformation: String
  ): F[Unit]
  def generateLineageGraph: F[LineageGraph]
}
```

## Implementation Priority

### Phase 1: Critical Foundation (Week 1-2)
1. **Resource Safety Framework** - Required for all cloud operations
2. **Basic Configuration Management** - Replace CCM dependencies  
3. **Core Testing Utilities** - Support development workflow

### Phase 2: Production Infrastructure (Week 3-4)
1. **Comprehensive Logging** - Production monitoring requirements
2. **Metrics Collection** - Performance and health monitoring
3. **Advanced Testing** - Integration and property-based testing

### Phase 3: Enterprise Features (Week 5-6)
1. **Distributed Tracing** - Production debugging and monitoring
2. **Data Lineage Tracking** - Compliance and data governance
3. **Advanced Configuration** - Environment management and hot-reloading

## Integration with Existing Layers

```scala
// Core Layer depends on Infrastructure
class DataAlgebra[F[_]](
  implicit 
  resourceSafety: ResourceSafety[F],
  config: ConfigurationManagement[F],
  logger: StructuredLogger[F]
)

// Service Layer uses Infrastructure for cloud operations
class CloudConnector[F[_], Provider](
  implicit
  cloudSafety: CloudResourceSafety[F],
  metrics: MetricsCollector[F],
  tracing: DistributedTracing[F]
)

// Framework Layer uses Infrastructure for pipeline orchestration
class Pipeline[F[_], A, B](
  implicit
  logger: StructuredLogger[F],
  metrics: MetricsCollector[F],
  lineage: LineageTracking[F]
)
```

## Current Status

❌ **NOT IMPLEMENTED**: Entire Infrastructure Layer missing from codebase
🎯 **PRIORITY**: Critical for production readiness  
📅 **TIMELINE**: 4-6 weeks for complete implementation
⚠️ **BLOCKER**: Other layers cannot be production-ready without Infrastructure Layer

This Infrastructure Layer is **mandatory** for FlowForge's production readiness and architectural integrity.
> Archived (2025-09-04): Superseded by ADR-013 Infrastructure Layer Implementation. See `docs/adr/013-infrastructure-layer.md` and Evidence.
