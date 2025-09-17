# FlowForge Public API (v1.0)

## Status: Proposed 1.0 Surface (Pre-RC)

Per v1.0 plan: This document labels the **proposed FlowForge 1.0 public API surface** until RC. All APIs listed here are intended for public consumption and will maintain binary compatibility within the 1.x series.

**⚠️ Until RC**: APIs may still evolve based on feedback and testing.

## Core Public APIs

### Primary Pipeline Construction
- **Core**: `com.flowforge.core.*` - Main pipeline builder and execution system
- **Contracts**: `com.flowforge.core.contracts.*` - Schema validation and policy enforcement  
- **Types**: `com.flowforge.core.types.*` - Type-safe data structures and builders
- **Main Builder**: `com.flowforge.core.PipelineBuilder` - 100% compile-time contract enforcement

### Data Algebra & Operations
- **DataAlgebra**: `com.flowforge.core.algebra.DataAlgebra[F[_], DS[_]]` - Core data operations abstraction
- **Quality Framework**: `com.flowforge.core.algebra.DataAlgebra.QualityResult[A]` - Quality validation results
- **Pipeline Types**: `com.flowforge.core.types.Pipeline[Input, Output]` - Type-safe pipeline execution

### Quality Constraints DSL
```scala
// Public constraint types - v1.0 stable
com.flowforge.core.types.QualityConstraint:
  - NotNull(field: FieldName, severity: QualitySeverity)
  - Unique(field: FieldName, severity: QualitySeverity) 
  - Range(field: FieldName, min: Option[Double], max: Option[Double], severity: QualitySeverity)
  - Pattern(field: FieldName, regex: String, severity: QualitySeverity)
  - Compliance(name: String, predicate: String, severity: QualitySeverity)
```

## Engine Implementations (v1.0 Stable)

### Spark Integration
- **ProductionSparkDataset**: `com.flowforge.engines.spark.ProductionSparkDataset[A]`
  - File operations: `writeParquet()`, `writeDelta()`, `writeCSV()`
  - Schema operations: `printSchema()`, `show()`
  - Factory methods: `fromDataFrame()`, `fromParquet()`, `fromDelta()`
- **SparkDataAlgebra**: Spark-specific DataAlgebra implementation

### Flink Integration  
- **FlinkDataStream**: `com.flowforge.engines.flink.FlinkDataStream[A]`
- **FlinkDataAlgebra**: Streaming-specific DataAlgebra implementation

## Data Quality (v1.0 Dual-Mode)

### Quality Validation Framework
- **DeequAdapter**: `com.flowforge.quality.deequ.DeequAdapter.runChecks()`
  - **Native Mode** (default): Pure Spark checks, zero dependencies
  - **Deequ Mode** (optional): Amazon Deequ VerificationSuite integration
  - **Graceful Fallback**: Automatic fallback from Deequ to native on errors

### Quality Configuration
- **Native Mode**: Always available, uses Spark DataFrame operations
- **Deequ Enhancement**: Enable via `-Dff.quality.mode=deequ` system property
- **Version Support**: Deequ 2.0.11-spark-3.5 when available on classpath

## Lineage & Observability (v1.0 Auto-Emit)

### OpenLineage Integration
- **Automatic Emission**: START/COMPLETE/FAIL events for pipelines and stages
- **Configuration**: Via environment variables
  - `OPENLINEAGE_URL`: Target endpoint (default: `http://localhost:5000/api/v1/lineage`)
  - `OPENLINEAGE_NAMESPACE`: Lineage namespace (default: `"flowforge"`)
- **Zero-Config**: Works out of the box with Marquez docker-compose setup

## Key Features (v1.0 Guarantees)

### Type Safety
- **100% Compile-Time Contracts**: Pipelines won't build if schemas don't match
- **Phantom-State Builder**: Type system prevents incomplete pipelines  
- **Schema Policy Enforcement**: Exact, Backward, Forward compatibility policies
- **Refined Types**: `FieldName`, `SchemaVersion` with compile-time validation

### Effect System Support
- **Effect-Safe**: Works with any `F[_]: EffectSystem` (IO, Task, etc.)
- **Resource Management**: All operations use `Resource[F, _]` for automatic cleanup
- **Error Handling**: Either monads throughout (CONTRIBUTING.md compliance)

### Production Features
- **Memory Safety**: No driver OOM through sampling strategies
- **Delta Lake Integration**: ACID transactions with table constraints
- **Multi-Cloud**: S3A/ABFS/GCS support via Spark's native drivers
- **Performance**: Adaptive query execution, partition optimization

## Examples & Utilities (v1.0 Reference)

### Complete Pipeline Example
- **UsersPipeline**: `com.flowforge.examples.spark.UsersPipeline`
  - End-to-end ETL demonstration
  - Quality validation with 6 constraint types
  - Delta Lake constraints (NOT NULL, CHECK)
  - Resource-safe Spark operations

### Utility Functions
- **Transformations**: Common data transformations (email normalization, age classification)
- **Quality Presets**: Pre-configured quality constraints for common use cases
- **Configuration Helpers**: Spark session setup, cloud storage recipes

## Internal APIs (Not Public)

### Implementation Details
- **Internal**: `com.flowforge.core.internal.*` - Implementation details, not for public use
- **Instances**: `com.flowforge.core.instances.internal.*` - Internal type class instances  
- **Test Utilities**: Test fixtures and helpers
- **Build Configuration**: SBT modules and dependency management

### Deprecated (Removed at 1.0)
- **SparkPipelineBuilder**: Use `PipelineBuilder` with Spark algebra instead
- **Legacy Contracts**: Use new quality constraint DSL
- **quality-deequ-runner CLI**: Replaced by dual-mode quality validation in DeequAdapter
- **templates module**: Removed to streamline 1.0 API surface

## Binary Compatibility Guarantees (1.x Series)

### ✅ Stable APIs
- Public trait/class signatures
- Case class constructors and field access
- Object method signatures and companion objects
- Quality constraint types and constructors
- Engine integration APIs (Spark, Flink)

### ⚠️ Best Effort
- Error message text
- Performance characteristics  
- Internal implementation details
- Non-public package contents

### ❌ No Guarantee
- `*.internal.*` packages
- Test utilities and fixtures
- Build configuration
- Documentation format

## Multi-Cloud Storage Support (v1.0)

### Storage Strategy: Spark's Native Drivers
FlowForge v1.0 uses Spark's production-ready storage drivers instead of custom connectors:

### Supported Storage Systems
- **Amazon S3**: Via Spark's S3A driver (`s3a://` URIs)
  - Uses `hadoop-aws` + AWS SDK v2
  - Production-ready with retry logic, multipart uploads
  - Configure via `spark.hadoop.fs.s3a.*` properties
- **Azure Data Lake Gen2**: Via ABFS driver (`abfss://` URIs)
  - Uses `hadoop-azure` with native Azure SDK integration
  - Enable via `HADOOP_OPTIONAL_TOOLS=hadoop-azure`
  - Configure via `fs.azure.account.*` properties
- **Google Cloud Storage**: Via GCS Hadoop connector (`gs://` URIs)
  - Uses official Google Cloud Dataproc Hadoop connector
  - Production-ready with workload identity support
  - Configure via service account JSON or workload identity
- **Local/HDFS**: Standard Hadoop filesystem support

### Storage Configuration Recipes
Available in documentation with copy-pasteable examples for each cloud provider.

### Delta Lake Multi-Cloud Support
- **NOT NULL constraints**: Schema-level enforcement across all storage backends
- **CHECK constraints**: Business rule validation on S3A/ABFS/GCS
- **ACID transactions**: Full Delta Lake support on all cloud storage systems
- **Schema evolution**: Compatible constraint enforcement across storage types

---

**Document Status**: Living document, updated with each RC milestone
**Last Updated**: FlowForge v1.0.0-RC1 preparation  
**Next Review**: At API freeze milestone