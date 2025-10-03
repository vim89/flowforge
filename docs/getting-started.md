# Getting Started with FlowForge

FlowForge is a revolutionary data engineering framework built with Scala's modern functional ecosystem, providing **compile-time data contracts** and **fiber-safe data pipelines**.

## 🚀 Quick Start (30 seconds)

The smallest possible pipeline in FlowForge:

```scala
import cats.effect.IO
import com.flowforge.core.pipeline._

object QuickStart extends IOApp.Simple {
  def run: IO[Unit] =
    DataPipelineFactory[IO]
      .source(blob"gs://raw/sales.csv")
      .contract(SalesContract.strict)
      .transform(_.filter(_.amount >= 0))
      .quality(nonNull("id") and unique("id"))
      .sink(BigQuerySink("analytics.sales"))
      .build
      .run
}
```

## 📦 Installation

### 1. Generate a New Pipeline Project

```bash
sbt new vim89/flowforge.g8
```

Follow the prompts to configure:
- Project name
- Organization
- Scala version (2.13 recommended, 2.12 for Flink)
- Effect system (Cats Effect or ZIO)

### 2. Or Add to Existing Project

Add to your `build.sbt`:

```scala
libraryDependencies ++= Seq(
  "com.flowforge" %% "flowforge-core" % "1.0.0",
  "com.flowforge" %% "flowforge-contracts" % "1.0.0",
  "com.flowforge" %% "flowforge-engines-spark" % "1.0.0"
)
```

## 🏗️ Your First Pipeline

### Step 1: Define Data Contract

```scala
import com.flowforge.contracts.syntax.ContractDSL._

val userContract = Contract("user")
  .field("id").required.long.positive
  .field("email").required.string.email.maxLength(255)
  .field("name").required.string.minLength(2).maxLength(100)
  .withSLA("hourly")
  .withOwner("DataPlatformTeam")
  .build
```

### Step 2: Create Type-Safe Pipeline

```scala
import cats.effect.IO
import com.flowforge.core.syntax.PipelineSyntax._

// Define case classes matching contract
case class RawUser(id: Long, email: String, name: String)
case class CleanUser(id: Long, email: String, name: String)

// Create pipeline with compile-time schema validation
val pipeline = EnhancedPipelineBuilder
  .from[IO, RawUser]("user-pipeline", source)
  .transform[CleanUser](user => IO.pure(cleanUser(user)))
  .validate(user =>
    if (user.isValid) ().validNel
    else ValidationError("Invalid user").invalidNel
  )
  .to(sink)
  .build
```

### Step 3: Run the Pipeline

```scala
object UserPipelineApp extends IOApp.Simple {
  def run: IO[Unit] = pipeline.execute
}
```

## 🧪 3-Step Fast Feedback Loop (Developer Experience)

### 1. **Rapid Compilation** (< 3s for pure code)
```bash
sbt ffDev  # Compile + run focused tests
```

### 2. **Contract Drift Detection** (Compile-time)
```scala
// Introduce schema drift - change field name
case class DriftedUser(id: Long, emailAddress: String, name: String)
//                                ^^^^^^^^^^^^^ Changed from "email"

// Try to use in pipeline
val broken = EnhancedPipelineBuilder
  .from[IO, DriftedUser]("broken", source)
  // ❌ COMPILE ERROR: Schema drift detected!
  // Expected field 'email', found 'emailAddress'
```

### 3. **Fix or Relax Policy**
```scala
// Option A: Fix the schema
case class FixedUser(id: Long, email: String, name: String)

// Option B: Relax policy from Exact to BackwardCompatible
val pipeline = EnhancedPipelineBuilder
  .from[IO, DriftedUser, SchemaPolicy.BackwardCompatible]("flexible", source)
  // ✅ Compiles - backward compatible changes allowed
```

## 🔑 Key Features

### 100% Type-Safe
- Compile-time guarantees eliminate runtime errors
- Schema mismatches caught before deployment
- No runtime surprises

### Functional-First
- Pure functions and immutability
- Explicit effect management (Cats Effect or ZIO)
- Referential transparency

### Contract-Driven
- Data contracts enforced at compile time
- Schema evolution with policy modes
- Automatic validation

### Multi-Engine
- Write once, run on Spark, Flink, or future engines
- Engine-agnostic pipeline definitions
- Consistent semantics across engines

### Production-Ready
- Built-in monitoring and observability
- Resource safety with automatic cleanup
- Structured logging and lineage tracking

## 📁 Project Structure

Generated projects follow this structure:

```
my-pipeline/
├── src/main/scala/
│   ├── contracts/           # Data contracts and schemas
│   │   └── UserContract.scala
│   ├── pipelines/          # Pipeline definitions
│   │   └── UserPipeline.scala
│   ├── transformations/    # Data transformation logic
│   │   └── UserTransforms.scala
│   └── Main.scala         # Application entry point
├── src/test/scala/
│   └── pipelines/         # Pipeline tests
└── build.sbt
```

## 📚 Next Steps

### Essential Reading (in order)
1. [AGENTS.md](../AGENTS.md) - Complete framework guide
2. [Contracts Overview](contracts/OVERVIEW.md) - Understanding data contracts
3. [Architecture Diagrams](diagrams/architecture.md) - How components fit together
4. [ADR Index](adr/INDEX.md) - Architectural decisions

### By Use Case
- **Data Engineers**: [Pipeline Patterns](examples/)
- **Platform Engineers**: [Operating Guide](operating/)
- **Contributors**: [Contributing Guide](../CONTRIBUTING.md)

### Advanced Topics
- [Effect System](effects/bring-your-own-effect.md) - Cats Effect vs ZIO
- [Multi-Engine Abstraction](design/core-design.md)
- [Compile-Time Contracts](diagrams/compile-time-contracts/)
- [Quality & Testing](adr/014-qa-strategy.md)

## 🎯 Core Concepts

### Type-Safe Pipelines
FlowForge ensures your pipelines are type-safe from source to sink, catching schema mismatches at **compile time**, not runtime.

### Effect Management
Choose between Cats Effect or ZIO for fiber-safe concurrency and resource management. Your pipeline code stays the same.

### Contract-First Design
Define data contracts that are enforced throughout pipeline execution. The compiler prevents contract violations.

### Compile-Time Guarantees
Our killer feature: **pipelines literally cannot be built** if schemas drift. This is proven by compile-fail tests in `modules/compile-fail-tests/`.

## ❓ Troubleshooting

### Common Issues

**"Cannot find implicit SchemaConforms"**
- Your schema doesn't match the contract
- Check field names, types, and order
- Review the SchemaPolicy you're using (Exact vs BackwardCompatible, etc.)

**"Type mismatch in PipelineBuilder"**
- Phantom type state machine preventing incorrect construction
- Ensure all required components added (source, transform, sink)

**"Cross-compilation failed"**
- Using Flink? Must use Scala 2.12
- Spark works with Scala 2.13 and 2.12
- See [Compatibility Matrix](reference/compatibility.md)

## 🆘 Getting Help

- **Documentation**: Check [docs/](.) directory first
- **Examples**: See [examples/](examples/) for working code
- **Issues**: Search [GitHub Issues](https://github.com/vim89/flowforge/issues)
- **Discussions**: [GitHub Discussions](https://github.com/vim89/flowforge/discussions)

## 🚦 What's Next?

After getting your first pipeline running:

1. ✅ Add data quality checks with ValidatedNel
2. ✅ Implement proper error handling
3. ✅ Add observability (metrics, logging)
4. ✅ Write compile-fail tests to prove guarantees
5. ✅ Review [30-Point Checklist](adr/020-pipeline-30-point-checklist.md)

---

**Welcome to FlowForge - where functional programming meets data engineering reality!** 🚀
