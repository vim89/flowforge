# flowforge

## Teaser

### 🎯 **The brutal truth about Data Engineering today**
**Data engineering is broken.** And we're all pretending it's fine.

Let's be honest -
1. Most data pipeline frameworks treat types as suggestions.
2. Config files are strings.
3. Schemas are "validated" at runtime.
4. Data quality is an afterthought.

#### **What's actually wrong:**
- **Configuration Hell** - YAML/JSON configs everywhere, runtime failures galore
- **Type Chaos** - `String` everywhere, no compile-time guarantees
- **Effect Anarchy** - Side effects scattered, no resource safety
- **Template Madness** - Maven archetypes with 2000+ line Velocity templates
- **Cloud Lock-in** - Write once, run nowhere else
- **Quality Afterthought** - Manual data quality checks, always too late
- **Schema Evolution Hell** - Break everything, rollback manually
- **Audit Nightmare** - Scattered logging, incomplete traces
- **Runtime Roulette** - Deploy and pray, discover errors in production


Here's what we do differently:

_🛑 Reality-first note (2025-09-03): Current repo provides runtime contract validation and type-safe builders; strict compile-time contract enforcement is on the roadmap._
```scala
// Fails fast via contract validation today; compile-time enforcement planned
val pipeline = DataPipelineFactory[IO]
  .source(blob"gs://raw-data/sales/*.parquet")
  .contract(SalesDataContract.strict)  // Contract validation (runtime today; compile-time planned)
  .transform(_.filter(_.amount >= 999))    // Type-safe transformations
  .quality(nonNull("invoice_number") and unique("customer_id"))  // Built-in quality checks
  .sink(BigQuerySink("analytics.customers"))
  .build

// Run it with automatic retry, monitoring, and error handling
pipeline.run.unsafeRunSync()
```
**Today (2025-09-03):** Type-safe interfaces, effect-safe orchestration, runtime contracts, scaffolding in place.  
**Partially implemented:** Compile-time schema gates are available via the typed path (TypedSource/TypedSink/PipelineBuilder2 with shapeless LabelledGeneric evidence). Legacy/untyped APIs still exist; CI/scalafix enforcement to block them is planned but not yet active.  
**Roadmap:** Build-time physical schema checks (sbt plugin), deprecate/unify public APIs as typed-only, production-hardening and full auditing.

For an honest, module-by-module status: see `docs/design/GROUND_REALITY_REPORT.md` (updated 2025-09-03).

### 📊 **Quantified outcomes (targets) vs today**

| **Aspect** | **Industry standard** | **Today (MVR)** | **Target (Roadmap)** |
|------------|-----------------------|------------------|---------------------|
| Setup Time | 2–3 days | Minutes (g8 scaffold) | ~30 seconds quickstart |
| Runtime Errors | Frequent | Reduced via contracts/tests | Zero‑class via compile/runtime gates |
| Config Bugs | Common | Typed decoders + validation | “Impossible” by design + CI gates |
| Cloud Portability | Rewrites | Local/HDFS implemented | Multi‑cloud adapters (GCS/S3/BQ/Kafka/Azure) |

**FlowForge** represents a shift from runtime chaos toward compile‑time confidence. We’re building the next‑generation Scala data pipeline framework, with today’s repo focused on the foundational architecture and an honest path to production.
Transform data engineering from error-prone scripting to type-safe, composable, and maintainable pipelines that scale from startup MVPs to enterprise production workloads.

**FlowForge** is a **Data Engineering Pipeline Archetype** built with **Scala's modern functional ecosystem**. We leverage **type safety** and **effect systems (ZIO/Cats‑Effect)** with **convention over configuration** to create maintainable pipelines. Production‑ready guarantees are an explicit roadmap item.


### 🔥 **Get ready for the revolution!**

---

## Compile-time Data contracts & Fiber-safe Data pipelines: Scala's Effect system in action

### Pitch
Writing data pipelines? Say goodbye to config/metadata chaos!
Let Scala's type & effect systems co-author them with you. We'll show how we built a contract-first, pluggable platform using Giter8, Refined, Cats Effect, ZIO delivering compile-time guarantees and fiber-safe execution.

#### **Disclaimer: Kyo & Caprese's effect systems will be experimental at this stage.**

### Description
Reality note: Today the repo enforces correctness with runtime contracts and type‑safe APIs; compile‑time enforcement is in progress. What if your data platform stopped relying on configuration‑driven or metadata‑driven logic—and postmortems—and instead enforced correctness, traceability, and effect boundaries at compile time?

In this project, we'll design the architecture and iteratively implement a production‑grade data pipeline archetype in Scala (MVR today; MVP/v1 on the roadmap).
We will use Giter8 templates to scaffold pipelines that are contract-driven, type-safe, effectful, and pluggable all enforced through the Scala type system and effect libraries.

- Giter8 templates bootstrap consistent projects (compile‑time contracts on the roadmap)
- Refined types validate configuration before runtime
- Cats ValidatedNel catches multi-rule violations in DQ checks
- Type classes enable pluggable validation and ingestion
- ZIO Layers and Cats Effect offer fiber-safe orchestration
- Trait-based runners target Spark first; Flink/Kafka execution on the roadmap
- Data Quality and custom rules enforce data contract quality at runtime
- Strictly Experimental: Kyo and Caprese add effect composition and static guarantees

## Archetype: What is archetype?
Archetype is designed to streamline the process of creating data pipelines for Data Engineers and Developers by providing a consistent, best-practices-based framework.
This toolkit will leverage SBT (Giter8) templates to generate standardized project structures quickly.

Reference: Similar to how Maven archetypes work, this will allow users to create new data pipeline projects with predefined configurations, dependencies, and code templates. Using velocity templates, we can create customizable and reusable project blueprints.
**Note: We will avoid the complexity of Maven and instead use SBT and Giter8 for Scala projects.**

An Archetype is a model, a pattern used to generate a new project that shares general structure/design with other projects.

An archetype is defined as an original pattern or model from which all other things of the same kind are made. The name fits as we are trying to provide a system that provides a consistent means of generating Data pipeline projects.

Archetype will help Data Engineers create Data pipeline workflows, and provides developers with the means to generate parameterized versions of those data-pipeline templates.

### Why archetype?
Using archetypes provides a great way to enable Developers, Data Engineers work quickly in a way consistent with best practices employed by your project or organization. Within any Data pipeline project, we use archetypes to try and get our Developers / Data Engineers up and running as quickly as possible by providing a sample project that demonstrates many of the best practices & features of Data Engineering, while introducing new users to the best practices employed by DV.

### How archetype benefits?
In a matter of seconds, a new developer / data engineer can have a working Data pipeline project to use as a jumping board for investigating more of the features in Data Engineering library functions / Utilities, Archetype & Housekeeping pipelines & workflows.
We have also tried to make the Archetype mechanism additive, and by that we mean allowing portions of a project to be captured in an archetype so that pieces or aspects of a project can be added to existing projects.

## Data Contracts: What are Data Contracts?

A Data Contract is a set of attributes published by the data provider (source) that ensures the integrity, authenticity and availability of data to multiple downstream consumer (target) systems. The target system will validate all three (integrity, authenticity and availability) aspects of the data asset, and consumption of the data depends on the contract validation status. The set of Data Contract requirements are, but not limited to:

- Agreement on table metadata: The structure of the data asset should contains all the available column and their respective data types, along with the constraints details, partition specification (if any), table file type and provider details upon by source. The contract should not change without prior notice to consumers.
- Agreement of Data quality validation rules: There are certain Data Quality rules that a data asset should always abide by e.g uniqueness of columns, not null columns, other business rules. The data contract should also provide the acceptable range of each DQ rule which will satisfy the integrity of the data asset.
- Agreement on SLA: Usually the requirement is to have the data asset availability by a fixed time. Any delay in data availability will results in delay in consumption at target end. 
- Agreement on source daily data refresh range: The requirement is to check any ad-hoc data refresh beyond the communicated range without any prior communication can be gated and to avoid long running consumption pipeline at target system end. 
- Agreement on owner and support details: The requirement is to have the contact details of the data owners to broadcast alerts in the event of contract violation failure.

### Why ?
Describe the key business problem(s) addressed by this architecture, including goals relative to previous or alternative approaches

### What ?
The key facet(s) of the architecture that enable it to address the business problem(s)

### How ?
connect the dots between the key facet(s) of the architecture and the business value it will deliver
It's usually best to work through at least the Context, Non-functional Requirements, and Design sections below before attempting to distill the result here.


### Context: How is a Data Contract maintained today?
Today, Data Contracts are maintained manually, usually through a written document and are only validated in the event of any failure. Currently the entire debugging and alerting process is a time consuming one and sometime breaches the SLA at target system end.

### What is proposed for Data Contract Automation and how it can be achieved?
It is proposed to make the process of Data Contract validation fully automated, that includes publishing and managing the data contract, data subscription process, validation of contract post subscription and alerting in the event of failure. It is the validation of all (integrity, authenticity and availability) aspects of the data, which will results in more reliable and time efficient entry of data into target systems.

Each aspect of the Data Contract e.g Structure, Data Quality, SLA etc can be looked upon as KPI which the data asset should satisfy. We can store these KPI info in a machine readable format which can be compared with the Metric Aggregator APIs to find Data Contract validity. A deviation in KPI values may lead to an incident on the source data team based on the nature and severity of violation.

### Type-Safe Archetypes & Data Contracts: Automating Trustworthy Data Pipelines with the Scala Ecosystem

#### Why?
Modern data teams face growing pressure to deliver scalable, reliable, and trustworthy pipelinesâ€”often across teams and platforms. Yet, pipeline development remains fragmented, error-prone, and time-consuming, especially when enforcing data contracts and maintaining schema integrity, SLAs, or DQ policies across domains. Traditional approaches (manual config, YAMLs, untyped code) result in late-stage failures, costly delays, and broken consumer trust.

#### What?
We present a solution built on a metadata-driven Archetype framework using Scala and its functional ecosystem. It enables data teams to rapidly scaffold data pipelines through reusable, strongly-typed templates (archetypes), while embedding data contracts (schemas, SLA guarantees, DQ rules) directly into the generated code. The system uses type-safe definitions, config validation, and automated runtime enforcement, ensuring all aspects of the pipeline are validated and observable from day one.


#### How? 
Using the power of Scala's type system, Cats, ZIO, and type classes, the archetype enforces structure at compile time, eliminating schema drift and runtime surprises. Metadata is modeled as case classes with refined types, ensuring only valid configurations enter the build stage. Pipeline stages are composed using Kleisli arrows, data quality is validated via ValidatedNel, and contracts are enforced at runtime using generated code that integrates with metric aggregators and alerting systems. The result is a resilient, scalable, and extensible platform that improves developer onboarding, increases trust in data, and aligns producers and consumers through automated validation â€” all made possible by the composability and safety guarantees of Scala.

----
## 🛠 **Technical Architecture Design**

A framework is built of pieces made exactly for each other and works as one thing instead of a group of libraries.

This framework/project will be a true integrated system for Data Engineering & Data pipelines - which collectively works with other libraries, but pieces built specifically for each other to Data Engineering that work as one cohesive whole.
Framework must be unified abstraction layer. This framework makes best architectural decisions as well.
Wherever applicable Data Engineers interact with APIs & exposed patterns/ layers not the underlying logic which libraries gives us to use.

Cats ValidatedNel catches multi-rule violations in DQ checks. Trait-based runners switch between Spark, Flink, and Kafka

API Simplification: Follow "Rails for Data Pipelines" philosophy - convention over configuration

Understand the `build.sbt`, `project/plugins.sbt` & `project/Dependencies.scala` files and align to the project idea, design and architecture - for implementation.

### 🏗 **FlowForge Layered Architecture**

FlowForge follows a strict layered architecture with clear separation of concerns and dependency inversion:

#### **🎯 Application Layer** 
*User-facing components for rapid development*
- **Templates & Generators**: Giter8-based project scaffolding
- **Examples & Demos**: Reference implementations showcasing best practices
- **Benchmarks**: Performance measurement and optimization tools

#### **🔧 Framework Layer**
*High-level abstractions for pipeline construction*
- **Pipeline Builders**: Both runtime-safe (PipelineBuilder) and compile-time-safe (PipelineBuilder2) APIs
- **Pipeline Combinators**: Functional composition patterns (sequence, parallel, conditional, retry)
- **Quality Framework**: Integrated data quality management with ValidatedNel
- **Template Engine**: Code generation and project scaffolding

#### **📊 Domain Layer**
*Core business logic and data modeling*
- **Pipeline Types**: Type-safe pipeline definitions with Kleisli composition
- **Data Contracts**: Compile-time and runtime schema validation
- **Schema Evolution**: Automated schema migration and compatibility checking
- **Pipeline Metrics**: Comprehensive monitoring and observability data models

#### **🔌 Service Layer**
*Integration and execution services*
- **Connectors**: Pluggable data source/sink implementations (GCS, S3, BigQuery, Kafka, Azure)
- **Execution Engines**: Multi-engine support (Spark, Flink, local execution)
- **Quality Implementations**: Concrete quality check implementations (Deequ integration)
- **Monitoring**: Real-time pipeline observability and alerting

#### **🏛 Core Layer**
*Foundational abstractions and type system*
- **Data Algebra**: Universal data operations with effect polymorphism
- **Effect System**: Unified abstraction over Cats-Effect/ZIO with tagless final pattern
- **Core Types**: Phantom types, refined types, and algebraic data types
- **FP Patterns**: Higher-kinded types, type classes, and functional composition utilities

#### **🛡 Infrastructure Layer**
*Cross-cutting concerns and system-level services*
- **Resource Safety**: Automatic resource management with bracket patterns
- **Testing Framework**: Comprehensive testing utilities for pipelines and components
- **Configuration**: Type-safe configuration management (replacing CCM)
- **Logging & Observability**: Structured logging, metrics, and distributed tracing


### 🔧 **Key Architectural Principles (with current status)**

1. **Dependency Inversion**: Higher layers depend on abstractions, not concretions
2. **Effect Polymorphism**: All operations work with any F[_]: EffectSystem (implemented; IO/ZIO instances provided)
3. **Type Safety**: Phantom types and refined types prevent runtime errors
4. **Composability**: Kleisli arrows enable functional pipeline composition
5. **Resource Safety**: Bracket patterns guarantee cleanup in all execution paths
6. **Multi-Engine**: Abstract execution allows switching between Spark/Flink/Local (Spark implemented; Flink/others planned)
7. **Plugin Architecture**: Connectors and quality checks are pluggable via type classes

### 🎯 Low-Level Design & Design Patterns
#### 🏛 SOLID Principles Implementation: Use all SOLID principles where applicable
- **S - Single Responsibility Principle**
- **O - Open/Closed Principle**
- **L - Liskov Substitution Principle**
- **I - Interface Segregation Principle**
- **D - Dependency Inversion Principle** - Depend on abstractions, not concretions

#### 🏗 Design Patterns
- **Creational Patterns**: Use all creational patterns where applicable
- **Structural Patterns**: Use all structural patterns where applicable
- **Behavioral Patterns**: Use all behavioral patterns where applicable

#### **Functional Programming Foundation**
Apply only where they fit perfectly to solve that bit of problem or make other things relevant & elegant.

- **Potential & power of Scala ecosystem**: We must us potential & power of Scala ecosystem but not over-engineer it. All possible USPs of Scala
- **Write idiomatic Scala code**: We must write idiomatic Scala code, not Java in Scala. We must use Scala to its fullest potential.
- **Purely Functional**: Emphasizing pure functions and immutability to create predictable and maintainable code.
- **Immutability**: All data structures are immutable by default, ensuring thread safety and predictability.
- **First-Class Functions**: Functions are treated as first-class citizens, allowing them to be passed as arguments, returned from other functions, and assigned to variables.
- **Higher-Order Functions**: Functions that take other functions as parameters or return them as results, enabling powerful abstractions and code reuse.
- **Pure Functions**: Functions that always produce the same output for the same input and have no side effects, making them easier to reason about and test.
- **Function Composition**: Combining simple functions to build more complex ones, promoting modularity and code reuse.
- **Pattern Matching**: A mechanism for checking a value against a pattern, allowing for concise and expressive handling of different data structures.
- **Concurrency with Futures**: Using Scala's `Future` to handle asynchronous computations, enabling non-blocking and concurrent programming.
- **Error Handling with Either and Try**: Using `Either` and `Try` to represent computations that may fail, providing a functional approach to error handling.
- **Implicits**: Using implicit parameters and conversions to enable type class instances and enhance code readability.
- **Monads**: Abstractions that represent computations as a series of steps, enabling chaining of operations while managing side effects (e.g., Option, Either, Future).
- **Type Classes**: A way to define generic interfaces that can be implemented for different types, enabling ad-hoc polymorphism and code reuse.
- **Algebraic Data Types (ADTs)**: Composite types formed by combining other types, such as case classes and sealed traits, allowing for expressive modeling of data.
- **Referential Transparency**: The property that an expression can be replaced with its corresponding value without changing the program's behavior, facilitating reasoning about code.
- **Lazy Evaluation**: Deferring computation until the result is needed, which can improve performance and enable the creation of infinite data structures.
- **Tail Recursion**: A special case of recursion where the recursive call is the last operation in a function, allowing for optimization and preventing stack overflow.
- **Type Safety 100 %**: Leveraging Scala's strong static type system to catch errors at compile. Builder Pattern with Type Safety.
- **Complete Monadic Ecosystem**: Utilizing libraries like Cats and Scalaz to work with monads, functors, and applicatives for elegant handling of side effects and asynchronous computations.
- **Convention over Configuration with Functional Purity**: Emphasizing sensible defaults and reducing boilerplate while maintaining pure functions and immutability.
- **Effect Systems**: Developer Choice Architecture - 
  - **ZIO OR Cats-Effect** (not both) per project
  - **Unified abstraction** enables switching without code changes
  - **Template generation** chooses effect system upfront
  - **Clean separation** prevents dependency conflicts
  - **Type-safe resource management** with automatic cleanup
  - **Fiber-safe concurrency** for parallel processing
  - **Comprehensive ecosystem** for functional programming
  - **Interoperability** with existing libraries and frameworks
  - **Decorator Pattern with Effect Systems**: Using the decorator pattern to add additional behavior to existing components in a type-safe manner, while leveraging the capabilities of effect systems like ZIO or Cats Effect to manage side effects and resource safety. time, ensuring robust and reliable applications.

### 🔬 **Effect System Research Findings & Architecture Decision**
Note: Interfaces reflect the research (pure Spark ops are not wrapped in F; external IO/orchestration use F). Spark implementation is being hardened to remove in‑memory scaffolding.

Based on comprehensive research and analysis of FlowForge's production-grade pipeline requirements, **Effect Systems are essential** for our data engineering platform. Here are the key findings:

#### **🎯 Critical Distinction: Spark vs Non-Spark Effects**

The research revealed a crucial architectural principle:
- **Spark Operations**: Already have distributed execution, fault tolerance, and retry mechanisms built-in
- **Non-Spark Effects**: JDBC connections, HTTP APIs, filesystem operations, audit logging, schema registry interactions require proper effect management  
- **Pipeline Orchestration**: Composing heterogeneous systems (Spark + databases + APIs + file systems) demands effect composition
- **Resource Safety**: Multi-cloud operations, connection pooling, and cleanup require bracket patterns

**Key Decision**: Effect systems are NOT needed for pure Spark transformations, but are **mandatory** for:
1. External system integration (databases, APIs, file systems)
2. Pipeline orchestration across multiple engines
3. Audit logging and lineage tracking
4. Schema registry and metadata management
5. Typed error handling and recovery patterns

#### **📋 Production Pipeline Concerns (35+ Requirements)**

Research expanded the original 12 pipeline concerns into 35+ production-grade requirements:

**1. Correctness & Safety (6 principles)**
- Data contracts validation at compile-time (schemas as types, ADTs, phantom types)
- Schema evolution & compatibility rules (forward/backward/full compatibility)
- Business transformations with typed error channels (no silent failures)
- Idempotency & reprocessing guarantees (support backfills, retries)
- Exactly-once vs at-least-once semantics made explicit
- Dead-letter queues (DLQ) for failed records, record-level retries

**2. Scalability & Performance (6 principles)**
- Affected partitions computation (process only what changed)
- Partition-aware joins and shuffle minimization
- CDC & incremental updates (Kimball, delta lake, append vs merge)
- Compression & encoding (Parquet/Avro/Arrow)
- Batch-stream unification (treat stream as first-class, batch = replay)
- Concurrency & ordering guarantees (partition ordering vs global)

**3. Reliability & Resilience (6 principles)**
- Audit logging around every stage
- Lineage & provenance tracked as first-class data
- Monitoring data freshness (latency SLAs, data timeliness)
- Distributed systems hazards (network partitions, retries, clock skew)
- Fault isolation (restart at partition-level, not entire pipeline)
- Transactional sink writes / outbox pattern for multi-sink outputs

**4. Governance & Maintainability (8+ principles)**
- Profile & anomaly detection before and after transformations
- Data quality checks pre/post ingestion
- Versioned transformations for reproducibility
- Reprocessability of historical data
- Data contracts as APIs (review schema changes like code changes)
- Observability hooks (metrics, logs, traces)
- Audit + lineage integrated with catalog (BigQuery, Glue, DataHub, etc)
- DDIA-derived concerns: consistency models, serialization, distributed transactions

#### **🏗 Technical Implementation Strategy**

**Tagless Final + Typed Errors + Schema Evolution**:

```scala
// Core pipeline algebra with typed errors
trait DataPipelineAlg[F[_]] {
  def readSource[A: DataDecoder](src: DataSource): F[Either[DataError, Dataset[A]]]
  def validate[A](ds: Dataset[A], contract: DataContract[A]): F[ValidatedNel[DataError, Dataset[A]]]
  def transform[A, B: DataEncoder](ds: Dataset[A])(f: A => B): F[Dataset[B]]
  def performCDC[A](src: Dataset[A], tgt: Dataset[A], keys: NonEmptyList[FieldName]): F[CDCResult[A]]
  def audit[A](stage: String, ds: Dataset[A]): F[Unit]
  def write[A: DataEncoder](ds: Dataset[A], sink: DataSink): F[WriteResult]
}

// Typed error hierarchy
sealed trait DataError extends Product with Serializable {
  def message: String
}

object DataError {
  case class DecodeError(source: String, cause: Option[String], message: String) extends DataError
  case class SchemaMismatch(expected: DataSchema, actual: DataSchema, message: String) extends DataError
  case class ContractViolation(reason: String, details: Map[String, String]) extends DataError
  case class StorageError(location: String, cause: Option[String], message: String) extends DataError
  case class CDCError(reason: String, message: String) extends DataError
}

// Pipeline composition with Kleisli arrows
val pipeline: Kleisli[F, PipelineInput, PipelineResult] = for {
  raw    <- readKleisli
  valid  <- validateKleisli  
  clean  <- transformKleisli
  delta  <- cdcKleisli
  result <- writeKleisli
} yield result
```

#### **🔄 Schema Evolution & Migration Strategy**

**Schema Registry with Automatic Migration**:
- **DataDecoder[A]** returns `Either[DataError, A]` instead of throwing exceptions
- **SchemaCompatibility** checks determine forward/backward compatibility
- **SchemaMigration** trait enables pluggable record transformations
- **SafeDecode** utility applies migrations before decoding
- **Version-aware** encoding/decoding with automatic schema registry integration

#### **⚡ Effect System Usage Guidelines**

1. **Spark-Only Operations**: Use `F.pure(dataset)` for pure Spark transformations (map/filter/join)
2. **External IO**: Use `Sync[F].delay(...)` for JDBC, HTTP, filesystem operations
3. **Resource Management**: All external integrations must use `Resource[F, _]` for cleanup
4. **Error Handling**: Use `EitherT[F, DataError, A]` for composable error handling
5. **Validation**: Multi-error scenarios use `ValidatedNel[DataError, A]`, never fail-fast exceptions
6. **Testing**: Provide both `Id` interpreter (for unit tests) and `IO` interpreter (for integration tests)

#### **🎮 Multi-Engine Strategy**

Effect polymorphism enables true multi-engine support:
- **SparkDataAlgebra[F]**: Spark-based implementation
- **FlinkDataAlgebra[F]**: Flink-based implementation  
- **LocalDataAlgebra[F]**: In-memory implementation for testing
- **Same business logic**: Pipeline definitions work across all engines
- **Engine-specific optimizations**: Each algebra can optimize for its runtime

This research confirms that Effect Systems are not optional ceremony, but essential infrastructure for production-grade data engineering platforms. They enable composability, testability, resource safety, and typed error handling across heterogeneous systems while maintaining the flexibility to work with multiple execution engines.

## 🔧 **Critical Implementation Guidelines from Effect System Research**
Status (2025-09-03): API separation is enforced; engine implementations are being refactored to align fully (removing unnecessary effects around pure Spark transforms).

### **Mandatory Effect Usage Separation**

Based on the Effect System research findings, FlowForge implementations **MUST** follow this separation:

#### **❌ WRONG: Using F[_] for Pure Spark Operations**
```scala
// INCORRECT - Pure Spark transformations don't need effects
def filter[A](ds: Dataset[A])(pred: A => Boolean): F[Dataset[A]] = 
  F.delay(ds.filter(pred))  // Unnecessary effect wrapper

def map[A, B](ds: Dataset[A])(f: A => B): F[Dataset[B]] = 
  F.delay(ds.map(f))  // Spark already handles this
```

#### **✅ CORRECT: Spark-Only vs External IO Separation**
```scala
// CORRECT - Pure Spark transformations as direct operations
def filter[A](ds: Dataset[A])(pred: A => Boolean): Dataset[A] = 
  ds.filter(pred)  // Direct Spark operation, no effect needed

def map[A, B](ds: Dataset[A])(f: A => B): Dataset[B] = 
  ds.map(f)  // Pure transformation, no side effects

// CORRECT - External IO operations require effects
def read[A: DataDecoder](source: DataSource): F[Dataset[A]] = 
  F.bracketCase(
    acquire = F.delay(openConnection(source)),
    use = conn => F.delay(readFromSource[A](conn)),
    release = (conn, _) => F.delay(conn.close())
  )

def write[A: DataEncoder](ds: Dataset[A], sink: DataSink): F[WriteResult] = 
  F.bracket(
    acquire = F.delay(openSink(sink)),
    use = s => F.delay(writeToSink(ds, s)),
    release = s => F.delay(s.close())
  )
```

### **Effect System Implementation Rules**

1. **Pure Spark Operations**: Return `Dataset[A]` directly, no `F[_]` wrapper
2. **External IO Operations**: Use `F[_]` with proper resource management (`bracket`, `Resource[F, _]`)
3. **Pipeline Orchestration**: Use `F[_]` for composing heterogeneous systems
4. **Configuration/Metadata**: Use `F[_]` for schema registry, config loading, audit logging
5. **Error Handling**: Use `ValidatedNel[DataError, A]` for multi-error scenarios

### **Refactoring Existing Code**

All current FlowForge implementations that use `F[_]` for pure Spark transformations **MUST** be refactored:

```scala
// Before: Effect system everywhere (INCORRECT)
trait SparkDataAlgebra[F[_]: EffectSystem] {
  def filter[A](ds: Dataset[A])(pred: A => Boolean): F[Dataset[A]]
  def map[A, B](ds: Dataset[A])(f: A => B): F[Dataset[B]]
  def read[A](source: DataSource): F[Dataset[A]]
}

// After: Proper separation (CORRECT)  
trait SparkDataAlgebra[F[_]: EffectSystem] {
  // Pure Spark operations - no effects
  def filter[A](ds: Dataset[A])(pred: A => Boolean): Dataset[A]
  def map[A, B](ds: Dataset[A])(f: A => B): Dataset[B]
  def join[A, B](left: Dataset[A], right: Dataset[B]): Dataset[(A, B)]
  
  // External IO operations - effects required
  def read[A: DataDecoder](source: DataSource): F[Dataset[A]]
  def write[A: DataEncoder](ds: Dataset[A], sink: DataSink): F[WriteResult]
  def auditOperation[A](stage: String, ds: Dataset[A]): F[Unit]
}
```

This architectural principle is **non-negotiable** and must be applied to all FlowForge implementations to align with the Effect System research findings.
- **Phantom Types**: Using phantom types to encode additional type information at compile time without affecting runtime representation, enhancing type safety and expressiveness.
- **Type-Level Programming**: Leveraging Scala's advanced type system to perform computations and enforce
- **F-Bounded Polymorphism**: Using F-bounded polymorphism to define type hierarchies where a type parameter is constrained to be a subtype of a specific type, enabling more precise typing and code reuse. For Type-Safe Composition
- **Kleisli Arrows**: Utilizing Kleisli arrows for Composable Transformations and to represent computations that produce monadic values, allowing for elegant composition of effectful functions and enabling a functional approach to building data pipelines.
- **Kleisli for effectful stages**: Kleisli composes effectful functions nicely. Each stage of the data pipeline (e.g., extraction, transformation, loading) can be represented as a Kleisli arrow, allowing for seamless composition of effectful operations while maintaining type safety and clarity.
- **Phantom-Type Builder Patterns**: Using phantom types in builder patterns to enforce correct construction of complex objects at compile time, preventing invalid states and ensuring that all required parameters are provided before building the final object. Phantom types track the current output type of stages. Using a phantom-type builder pattern to enforce correct construction of complex objects at compile time, ensuring that all required parameters are provided and valid before the object can be instantiated. Phantom types are markers used only by the compiler; they don’t exist at runtime, but enforce rules at compile time. In a builder, each added stage updates the phantom Out type. Trying to build before types align fails to compile, not crash. They exist exclusively at compile time and carry extra information that enables the compiler to enforce rules. Phantom Types provide extra information to the compiler… allow extra constraints… program fails to compile if constraints don’t hold ...
- **Higher-Kinded Type**: Employing higher-kinded types to define abstractions that can operate on type constructors, enabling the creation of generic and reusable components that work with various data structures and effect types.
- **Tagless Final Encoding**: Adopting the tagless final encoding pattern to define type-safe and extensible algebras for domain-specific languages, allowing for flexible interpretation and composition of operations without relying on concrete data types.
- **Free Monads**: Using free monads to represent computations as a series of steps, enabling the separation of program description from execution and facilitating the creation of interpreters for different execution strategies.
- **Type Classes**: Leveraging type classes to define generic interfaces that can be implemented for different types, enabling ad-hoc polymorphism and code reuse across various data structures and effect types. Adapter Pattern with Type Classes. 
- **Type class patterns**: Using type class patterns to define and implement generic behaviors for different types, allowing for flexible and reusable code that can work with various data structures and effect types.
- **Self Types**: Utilizing self types to express dependencies between traits, enabling more precise typing and ensuring that certain traits can only be mixed into classes that also extend specific other traits.
- **Structural types**: Employing structural types to define types based on their members rather than their explicit names, allowing for more flexible and dynamic typing in certain scenarios.
- **Tagless Final for Effect Abstraction**: Using the tagless final pattern to abstract over different effect types, enabling the creation of generic and reusable components that can work with various effect systems (e.g., Cats Effect, ZIO) without being tied to a specific implementation. Strategy Pattern with Tagless Final. 
- **Observer Design Pattern with Reactive Streams**: fs2. Implementing the observer design pattern using reactive streams (e.g., Akka Streams, FS2) to enable asynchronous and event-driven data processing, allowing components to react to changes in data and propagate updates through the system in a non-blocking manner.
- Cats Monads - Higher Kinded Type class
- for-comprehensions are not iterations. Step away from the concept of iterations.
- FlatMap is mental model for chained transformations.
- Cats Monads Use cases: List Combinations, Option transformations, Asynchronous chained computations, Dependent computations, Cats Monad Transformers: Higher-Kinded-Types for convenience over nested monadic values. OptionT, EitherT, Cats Data Manipulation: Readers, Writers, Evaluations, State, Data Validations.

Be mindful of - 
- No Over Engineering - Keep simplicity, scalable, understandable, adaptable yet creative - We need to be creative, innovative & something like Wow such a thing can be achieved who'd have never thought about such things in Data Engineering Data Pipelines.
- The polymorphic effects of Cats-Effect already describe any effect type, and you can use ZIO Tasks instead of IO as the implementation of F - polymorphism lets me slot ZIO Task in F[_]
- We can also have typed error channel, the entire type class hierarchy starts all the way from Cats, as MonadError[F[_], E]. Usually the error channel is Throwable and makes things easier, and you can have an entire error model starting from Throwable
- You know why typed error channels haven’t made a difference yet - unless you model errors explicitly, they’re more effort than payoff - but ZIO’s typed channel is powerful when you start encoding domain-specific failures into your effects. Moreover, it forces clarity about what errors you can and should recover from, and ZIO even supports rich handling like folding, retries, and transforming failures with full type safety.
- Scala has both Functional Programming and Object Oriented design elements - for the double dispatch pattern - the choice is clear - it is going to be ADTs... (pattern matching)
- ADTs is the way to go - it is so powerful - that we can easily do triple dispatch (and more if we need) - the SUM and PRODUCT types etc.
- For single dispatch though - there are some times when modelling via inheritance is suggested - if the hierarchy is
  volatile (subclasses are getting added/removed frequently)
  If it is largely static - ADTs are the way to go - ADTs are also non-intrusive - we don't have to touch the actual class definition - instead we can
  attach the behavior from outside.


Remember all this is going to integrate with Apache Spark, Flink & other clouds so it needs to be absolutely generic.

#### Prototype Integration Principles
When integrating external patterns or libraries into FlowForge:
- **Adapter Pattern with Type Classes**: Wrap imperative APIs in functional type class interfaces
- **Compatibility Layers**: Create functional bridges that preserve existing interfaces while adding type safety
- **Effect-Safe Resource Management**: All external integrations must use Resource[F, _] for cleanup
- **Validation Aggregation**: Multi-error scenarios use ValidatedNel, never fail-fast exceptions
- **Kleisli Integration**: External operations should be composable via Kleisli arrows where possible

Example pattern for integrating imperative utilities:
```scala
// DON'T: Direct imperative integration
def badIntegration(): Map[String, String] = externalLibrary.getConfig()

// DO: Functional wrapper with effect safety
trait ConfigurationAlgebra[F[_]] {
  def loadConfig[T: ConfigDecoder](key: String): F[ValidatedNel[ConfigError, T]]
}

// Compatibility layer preserving old interface
trait LegacyCompatibility[F[_]: Sync] extends ConfigurationAlgebra[F] {
  def getLegacyConfig(key: String): F[Option[Map[String, String]]] // Preserve existing API
  def adaptToTyped[T: ConfigDecoder](config: Map[String, String]): F[ValidatedNel[ConfigError, T]]
}
```

#### Advanced Type-Level Programming Patterns
- **Phantom State Machines**: Use phantom types to encode valid state transitions at compile time
- **Dependent Types with Refinement**: Combine refined types with phantom types for maximum safety
- **Type-Level Validation**: Configuration and template validation should happen at compile time
- **Effect Channel Modeling**: Use MonadError[F[_], E] with domain-specific error ADTs for typed error handling
- Simplify Type Hierarchies: Remove intermediate traits that add no value

#### Resource Management Patterns  
- **Bracket Everything**: All resource acquisition must use bracket patterns or Resource[F, _]
- **Compositional Resource Safety**: Resources should compose via Resource.flatMap and Resource.parTupled
- **Cloud Connector Safety**: Multi-cloud operations require automatic connection cleanup
- **Stream Resource Management**: fs2.Stream operations must properly handle resource lifecycle

#### Template Generation Philosophy  
- **Functional Template Generation**: Templates use effect systems, not imperative file operations
- **Phantom-Type Builders**: Template construction prevents invalid states at compile time
- **Validation Before Generation**: Use ValidatedNel to collect all template errors before failing
- **Resource-Safe Generation**: File operations must use Resource[F, _] with proper cleanup
- Template Focus: Concentrate on 80/20 use cases instead of trying to handle every edge case

Template generation pattern:
```scala
// Template builder with phantom type safety
case class TemplateBuilder[State <: TemplateState] private (...)

// Only complete templates can be built
def build[F[_]: Sync](implicit ev: State =:= TemplateComplete): F[GeneratedProject]

// Validation aggregates errors before failing
def validate: ValidatedNel[TemplateError, TemplateBuilder[TemplateComplete]]
```

#### ETL and Pipeline Patterns
- **Kleisli-First Design**: All pipeline operations should be Kleisli[F, A, B] for composability
- **Effect-Polymorphic Operations**: ETL operations work with any F[_]: Sync, not specific effect types
- **CDC via Kleisli**: Change Data Capture operations compose functionally
- **Multi-Engine Abstraction**: Spark, Flink operations use same functional interface

Pipeline composition example:
```scala
// Individual operations as Kleisli arrows
val extract: Kleisli[F, DataSource, Dataset[A]] = ...
val transform: Kleisli[F, Dataset[A], Dataset[B]] = ...  
val load: Kleisli[F, (Dataset[B], DataSink), WriteResult] = ...

// Compose entire pipeline
val pipeline: Kleisli[F, (DataSource, DataSink), WriteResult] = 
  extract >>> transform >>> load.local { case (ds, src, sink) => (ds, sink) }
```

#### Error Modeling Strategy
- **Domain-Specific Error ADTs**: Each domain has its own error hierarchy extending base FlowForgeError
- **MonadError Integration**: Use MonadError[F[_], E] for typed error channels where beneficial  
- **Error Recovery Patterns**: Implement retry, fallback, and circuit breaker patterns functionally
- **Error Aggregation**: Multi-step operations collect errors via ValidatedNel before failing
- Standardize Error Handling: Use consistent error types across all modules

#### Refactoring Strategy
- **Incremental Refactoring**: Refactor one module at a time, ensuring tests pass
- **Preserve Existing Interfaces**: Maintain backward compatibility during refactoring
- Reduce Abstraction Layers: Remove phantom types where runtime validation suffices
- Consolidate Configuration: Create unified configuration system instead of multiple approaches
- Performance Optimization: Replace heavy abstractions with simpler, faster alternatives
- Your goal is not just compiling project without errors so Strictly NO applying the safe approach by commenting out any of the implementation or with `???` implementations. Instead, understand entire codebase holistic approach plus git commit history to take positive attitude approaches for refactoring.

#### Libraries
Strictly do not use below libraries in this entire project, strictly anything offered by them will not be considered in our entire implementation - 1. pureconfig 2. pureconfig refined 3. pureconfig-cats-effect 4. circe-refined 5. greatExpectations 6. redefined 7. refined-cats
These libraries currently do not use, or implement using them now. Later I shall cherry-pick - 1. kittens


#### Existing prototype implementation references
- Utils: https://github.com/vim89/reference-utilities/tree/main/src/main/scala/com/vim/de/utils
- Archetype: https://github.com/vim89/reference-archetype

These github repositories (above 2 links) which I implemented in the past for my Data Engineering Data pipelines project.
Do not miss any file from project content - including all github repositories & every single .scala, .java, .xml, .conf, .yml, .yaml, .json, .properties etc etc. every single file in all the repositories.
Each & every scala file inside github repos given in project content all scala/java packages - etl, audit, databaseops, bigquery, azure, common, caseclasses, gcp, spark, secrets etc.
And maven modules having names like archetype-module, archetype-project etc.
Do learn & understand them - I want you to study every single file in them.

CCM in above github repo Utils - CCM is a private organization owned Configuration management system built in Spring Boot - A configuration management system is a software component responsible for managing application configurations - https://medium.com/walmartglobaltech/better-applications-using-dynamic-configuration-management-26c4bee5be3c
Because ours is open-source software MIT we need entire new configuration system You need to think through best suiting alternatives - research, plan, architect, decide, design & implement it fully.

#### Templating
- We want templating but we will not use giter8 template sbt plugin it's an overkill. 
- We shall create comprehensive templates for anyone who wants to build datapipeline right-away, test - Unit, Integration Property, code review,  & deploy to production all in less than 30-minutes. 
- But let's not use sbt plugin giter8 - let's use using sbt new ..../<template-name>.g8 
- Templates must create a real pipeline: scaffolded from template, validated with contracts, and executed with all we have enforced in this project idea.

## build.sbt, project/plugins.sbt & project/Dependencies.scala
- Build tool: SBT
- I have created build.sbt and it's absolutely perfect.
- Do not change anything in build.sbt
- Do not add any dependency in build.sbt
- Do not remove any dependency in build.sbt
- Versions of dependencies can be changed if required.
- Do not change scalaVersion in build.sbt
- Do not change organization in build.sbt
- Do not change name in build.sbt
- Do not change version in build.sbt
- Do not change allSettings in build.sbt


##  Session Continuity Protocol – ((ChatGPT Agents specially for you for Keeping ChatGPT Agents Agile and Aligned)

To avoid repetitive rework, confusion, or lost context when ChatGPT Agents hits its session limits, we use this structured protocol in every interaction:

### 1. Context Re‑Initialization
At the start of every session, ChatGPT Agents *must* refresh context by re-reading:
- `docs/adr`, `docs/evidence`, `docs/plan`, `AGENTS.md`, `CLAUDE.md`
- Any recent diffs or `STATE SNAPSHOT` outputs from prior sessions
- Relevant reference docs in `docs/archive/*`
- Code review of project flowforge: Entire codebase all modules, all .scala files, .github, .yml, .md, .sbt, .conf, .sh
- Perform Ground reality of codebase current situation vs ADRs vs Evidences vs Plans
- Previous Git commits (as many historical commits as required) to understand what was implemented.

> “Context engineering is the practice of structuring everything an LLM needs—prompts, memory, tools, data—to operate reliably.” ([kubiya.ai](https://www.kubiya.ai/blog/context-engineering-ai-agents?utm_source=chatgpt.com))

### 2. CHECK CONTEXT
When prompted with:
```
CHECK CONTEXT
```  
ChatGPT Agents responds with (assuming Context Re‑Initialization is done as instructed above):
- Confirmation that context is refreshed
- Any ambiguities or clarifications needed
- Summary of the last session’s accomplishments (e.g. “Completed SparkDataAlgebra type scaffold”)
- Remaining implementation points or ambiguous areas ("Core trait X exists in engines-spark; confirm integration plan?")

### 3. SESSION GOAL Declaration
After context check, I provide:
```
SESSION GOAL: <Your concise task objective>
```  
ChatGPT Agents you must respond with a clear, bullet-point plan for that session, like example below:
- Add core `SparkDataAlgebra` trait
- Wire it into `engines-spark`
- Write scaffolding tests

### 4. Incremental Implementation & Safe Checks
ChatGPT Agents should:
- Deliver only **patch diffs**
- Before code generation, detect duplicates by searching recent code
- If duplication/conflicts arise, flag:
  ```
  CONFLICT DETECTED: [describe conflict]. Please advise whether to merge, rename, or skip.
  ```
####  Scala File Organization Best Practices

**Please do not generate one large `.scala` file.**  
Instead:

- Put each **class**, **trait**, or **object** (and its companion, if any) in its **own file**, named exactly after the public type it contains (e.g. `User.scala` contains `class User` and `object User`).
- **Exceptions allowed**:
    - If you define a **sealed trait with its subclasses**, keep them together in one file (necessary for exhaustiveness checks).
    - If multiple types are tightly coupled (e.g. a mini-ADT), you may group them—but name the file in **lowerCamelCase**, such as `ast.scala` or `errors.scala`.

This supports code clarity, discoverability, and matches Scala community conventions.

#### More Guidelines

1. **Commit Messages**: Use [Conventional Commits](https://www.conventionalcommits.org/) format. Examples include:
- `feat:` for new features
- `fix:` for bug fixes
- `docs:` for documentation changes
- `test:` for test-related changes
- `chore:` for maintenance tasks

2. **Simplicity First**: Prefer simpler implementations over overly complex solutions.

3. **Uniform Structure**: Maintain a consistent code structure across modules so files and packages are easy to navigate.

4. **Explain Why**: Add comments explaining *why* something is done if it is not obvious from code alone.

5. **Architectural Decision Records (ADRs)**: For non-trivial design choices, add a short ADR (docs/adr/NNN-*.md) explaining context, the decision, and alternatives.

---

####  Why This Matters (Supported by Scala Style Guides)

- **Naming conventions**: Scala classes, traits, and objects use `UpperCamelCase`, and the filename should correspond accordingly.  
  [oai_citation:0‡Scala Documentation](https://docs.scala-lang.org/style/naming-conventions.html?utm_source=chatgpt.com)

- **File structure guidance**:
    - A class and its companion should live together in the same file.
    - `sealed` traits and their subtypes must also be in the same file for compiler safety.
    - File names usually match the main type they hold.  
      [oai_citation:1‡Scala Documentation](https://docs.scala-lang.org/style/files.html?utm_source=chatgpt.com)

- **Multi-type grouping**: When types are closely related, grouping is permitted—but only when justified. The file should then use meaningful lowerCamelCase names.  
  [oai_citation:2‡Stack Overflow](https://stackoverflow.com/questions/24365548/scala-multiple-objects-and-classes-in-a-single-file-or-each-object-class-its-ow?utm_source=chatgpt.com) [oai_citation:3‡Scala Documentation](https://docs.scala-lang.org/style/files.html?utm_source=chatgpt.com)

- **Developer ergonomics**: Having one type per file helps with navigation, findability, and code review. It avoids confusion and reduces search friction.  
  [oai_citation:4‡Software Engineering Stack Exchange](https://softwareengineering.stackexchange.com/questions/57174/which-is-better-many-class-definitions-in-the-same-file-or-every-class-definiti?utm_source=chatgpt.com)

---

####  Quick Reference Table

| Rule                                           | Description                                                                 |
|------------------------------------------------|-----------------------------------------------------------------------------|
| **One type per file**                          | Each class/trait/object gets its own file, paired with its companion.      |
| **Filename = Public Type Name**                | e.g., `Widget.scala` contains `class Widget`.                              |
| **Multi-type file allowed if tightly coupled** | Use only for ADTs or sealed hierarchies; name the file in lowerCamelCase. |

---


####  Compilation Enforcement: Safeguard Before You Implement
**Critical workflow rule for ChatGPT & Agents:**
** After every implementation step—no exceptions—before moving on, you must compile the entire project (`sbt compile`) and wait for the success confirmation.**  
If the compile fails:
- Immediately stop implementation.
- Output the compile errors with context.
- Ask for instructions on how to proceed (e.g., fix errors, revert, or revise plan).

#####  Why this matters
1. **Prevents cascading failures**: Every build step is checkpointed by a compile, dramatically reducing error propagation.
2. **Context clarity**: Compile errors signal misalignment with existing types or architecture. Better to catch early.
3. **Aligns with recommended AI tool practices**: Many developer-side agents (e.g., Sourcegraph Cody) assert “Plan → Write → Verify” cycles. Popular AI-driven workflows do the same.  [oai_citation:0‡Anthropic](https://docs.anthropic.com/en/docs/build-with-claude/prompt-engineering/claude-4-best-practices?utm_source=chatgpt.com) [oai_citation:1‡Medium](https://statistician-in-stilettos.medium.com/best-practices-i-learned-for-ai-assisted-coding-70ff7359d403?utm_source=chatgpt.com)
4. **Proactive correctness**: Most LLM-assisted development guides emphasize **enforce step‐by‐step validation** after generating code.  [oai_citation:2‡Medium](https://medium.com/%40saeedhajebi/best-practices-in-prompt-engineering-a-real-world-guide-2781d192d60d?utm_source=chatgpt.com) [oai_citation:3‡DigitalOcean](https://www.digitalocean.com/resources/articles/prompt-engineering-best-practices?utm_source=chatgpt.com)

#####  Example Dialogue Pattern

**ChatGPT Agents writes patch**  
**YOU**: Apply and compile.  
**ChatGPT Agents** (after `sbt compile`):
---

### 5. Context & Memory Hygiene
- Keep the prompt lean—summarize rather than re-share entire documents
- Use an external “scratchpad” file for session lists or code state to reference next time, mimicking memory without overloading context
- Avoid adding or removing “tools” dynamically mid-session — maintain a consistent interface

### 6. End‑of‑Session STATE SNAPSHOT
When wrapping up, ChatGPT Agents must summarize:
- Files and modules updated
- Tasks completed and still pending
- Updates to `Findings.md`, `RoadmapProposal.md`, or `design.md` if any
- Any design changes or new ideas that arose
- Provide a concise `PATCHSET.diff` of all changes and ask for review & approval to "PATCH DIRECTLY".
- If user approves, ChatGPT Agents must apply the patch directly to the codebase.
- If user requests changes, ChatGPT Agents must update the patch and re-submit for approval.
- Save the final session summary to `docs/previous-chats/session-<timestamp>.md
- Persist a short scratchpad for the next session

---

###  Why This Works
- **Structured context engineering** lays a predictable foundation for ChatGPT & Agents’ state ([LlamaIndex blog]([llamaindex.ai](https://www.llamaindex.ai/blog/context-engineering-what-it-is-and-techniques-to-consider?utm_source=chatgpt.com)))
- Summaries and checkpoints reduce token bloat and maintain conversation coherence
- Conflict detection and small patches dramatically cut down wasted labor and merge conflicts
