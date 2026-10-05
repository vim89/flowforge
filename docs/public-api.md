# flowforge Public API (v1.0)

Status: pre-1.0. APIs listed here are intended for public use. The binary compatibility promise described below starts at the 1.0.0 release; releases before that may break it.

## Core public APIs

### Primary pipeline construction
- **Core**: `com.flowforge.core.*` - Main pipeline builder and execution system
- **Contracts**: `com.flowforge.core.contracts.*` - Schema validation and policy enforcement  
- **Types**: the types named in this document under `com.flowforge.core.types` - `DataSchema`, `DataType`, `StructField`, `QualityConstraint`, `DataSource`, `DataSink`, `RefinedTypes`. The rest of the package is not covered by this surface.
- **Main Builder**: `com.flowforge.core.PipelineBuilder` - 100% compile-time contract enforcement

### Data algebra & operations
- **DataAlgebra**: `com.flowforge.core.algebra.DataAlgebra[F[_]]` - Core data operations abstraction
- **Quality Framework**: `com.flowforge.core.algebra.DataAlgebra.QualityResult[A]` - Quality validation results
- **Pipeline Types**: `com.flowforge.core.FlowForgePipeline[F[_]: EffectSystem, A, B]` - Type-safe pipeline execution

### Quality constraints DSL
```scala
// Public constraint types - v1.0 stable
com.flowforge.core.types.QualityConstraint:
  - NotNull(field: FieldName, severity: QualitySeverity)
  - Unique(field: FieldName, severity: QualitySeverity) 
  - Range(field: FieldName, min: Option[Double], max: Option[Double], severity: QualitySeverity)
  - Pattern(field: FieldName, regex: String, severity: QualitySeverity)
  - Compliance(name: String, predicate: String, severity: QualitySeverity)
```

## Engine implementations (v1.0 Stable)

### Spark integration
- **ProductionSparkDataset**: `com.flowforge.engines.spark.ProductionSparkDataset[A]`
  - File operations: `writeParquet()`, `writeDelta()`
  - Spark interop: `asSparkDataset()`, `show()`, `cache()`, `persist()`, `repartition()`
  - Factory methods: `fromDataFrame()`, `fromData()`
- **SparkDataAlgebra**: Spark-specific DataAlgebra implementation

### Flink integration
- **FlinkDataAlgebra**: `com.flowforge.engines.flink.FlinkDataAlgebra[F]` (minimal parity via in‑memory delegate).
- Cross‑build: Scala 2.12 only (Flink Scala API constraint). Not feature‑parity with Spark in 1.0; stable façade guaranteed.

## Data Quality (v1.0 dual-mode)

### Quality validation framework
- **DeequAdapter**: `com.flowforge.quality.deequ.DeequAdapter.runChecks()`
  - **Native Mode** (default): Pure Spark checks, zero dependencies
  - **Deequ Mode** (optional): Amazon Deequ VerificationSuite integration
  - **Graceful Fallback**: Automatic fallback from Deequ to native on errors

### Quality configuration
- **Native Mode**: Always available, uses Spark DataFrame operations
- **Deequ Enhancement**: Enable via `-Dff.quality.mode=deequ` system property
- **Version Support**: Deequ 2.0.12‑spark‑3.5 when available on classpath

## Lineage & observability (v1.0 auto-emit)

### OpenLineage integration
- **Automatic Emission**: START/COMPLETE/FAIL events for pipelines and stages
- **Configuration**: Via environment variables
  - `OPENLINEAGE_URL`: Target endpoint (default: `http://localhost:5000/api/v1/lineage`)
  - `OPENLINEAGE_NAMESPACE`: Lineage namespace (default: `"flowforge"`)
- **Zero-Config**: Works out of the box with Marquez docker-compose setup

## Key features (v1.0 guarantees)

### Type safety
- **100% Compile-Time Contracts**: Pipelines won't build if schemas don't match (improved implementation)
- **Phantom-State Builder**: Type system prevents incomplete pipelines
- **Schema Policy System**: Exact, ExactUnordered, ExactUnorderedCI, ExactOrdered, ExactOrderedCI, ExactByPosition, Backward, Forward, Full policies
- **TypeShape ADT**: Clean, functional schema representation replacing old SchemaAST
- **Policy-Based Comparison**: Maintainable, extensible schema validation engine
- **Refined Types**: `FieldName`, `SchemaVersion` with compile-time validation

### Effect system support
- **Effect-Safe**: Works with any `F[_]: EffectSystem` (IO, Task, etc.)
- **Resource Management**: `com.flowforge.core.algebra.FlowforgeResource[F, _]` for acquire-and-release cleanup. `DataAlgebra` operations themselves return plain `F[_]`; the table operations in `EnterpriseTableAlgebra` return `cats.effect.Resource`.
- **Error Handling**: Either monads throughout (CONTRIBUTING.md compliance)

### Production features
- **Memory Safety**: No driver OOM through sampling strategies
- **Delta Lake Integration**: ACID transactions with table constraints
- **Multi-Cloud**: S3A/ABFS/GCS support via Spark's native drivers
- **Performance**: Adaptive query execution, partition optimization

## Examples & utilities (v1.0 reference)

### Complete pipeline example
- **UsersPipeline**: `com.flowforge.examples.spark.UsersPipeline` (test sources of the `examples` module, so it is read as a reference rather than depended on)
  - End-to-end ETL demonstration
  - Quality validation with 6 constraint types
  - Delta Lake constraints (NOT NULL, CHECK)
  - Resource-safe Spark operations

### Utility functions
- **Transformations**: Common data transformations (email normalization, age classification)
- **Quality Presets**: Pre-configured quality constraints for common use cases
- **Configuration Helpers**: Spark session setup, cloud storage recipes

## Internal APIs (not public)

### Implementation details
- **Internal**: any `*.internal.*` package, such as `com.flowforge.core.contracts.internal` (the contract macros) - implementation details, not for public use
- **Test Utilities**: Test fixtures and helpers
- **Build Configuration**: SBT modules and dependency management

### Deprecated (removed at 1.0)
- **SparkPipelineBuilder**: Use `PipelineBuilder` with Spark algebra instead
- **Legacy Contracts**: Use new quality constraint DSL
- **quality-deequ-runner CLI**: Replaced by dual-mode quality validation in DeequAdapter
- **templates module**: Removed to streamline 1.0 API surface

## Binary compatibility guarantees (1.x series)

### ✅ Stable APIs
- Public trait/class signatures
- Case class constructors and field access
- Object method signatures and companion objects
- Quality constraint types and constructors
- Engine integration APIs (Spark, Flink)

### ⚠️ Best effort
- Error message text
- Performance characteristics  
- Internal implementation details
- Non-public package contents

### ❌ No guarantee
- `*.internal.*` packages
- Test utilities and fixtures
- Build configuration
- Documentation format

## Multi-cloud storage support (v1.0)

### Storage strategy: Spark's native drivers
flowforge v1.0 uses Spark's production-ready storage drivers instead of custom connectors:

### Supported storage systems
- **Amazon S3**: Via Spark's S3A driver (`s3a://` URIs)
  - Uses `hadoop-aws` + AWS SDK v2
  - Production-ready with retry logic, multipart uploads
  - Configure via `spark.hadoop.fs.s3a.*` properties
- **Azure Data lake Gen2**: Via ABFS driver (`abfss://` URIs)
  - Uses `hadoop-azure` with native Azure SDK integration
  - Enable via `HADOOP_OPTIONAL_TOOLS=hadoop-azure`
  - Configure via `fs.azure.account.*` properties
- **Google cloud storage**: Via GCS Hadoop connector (`gs://` URIs)
  - Uses official Google Cloud Dataproc Hadoop connector
  - Production-ready with workload identity support
  - Configure via service account JSON or workload identity
- **Local/HDFS**: Standard Hadoop filesystem support

### Storage configuration recipes
Available in documentation with copy-pasteable examples for each cloud provider.

### Delta lake Multi-cloud support
- **NOT NULL constraints**: Schema-level enforcement across all storage backends
- **CHECK constraints**: Business rule validation on S3A/ABFS/GCS
- **ACID transactions**: Full Delta Lake support on all cloud storage systems
- **Schema evolution**: Compatible constraint enforcement across storage types

---

Document Status: pre-1.0
Last Updated: 2026‑10‑05
