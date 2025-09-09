# Getting Started with FlowForge

FlowForge is a revolutionary data engineering pipeline archetype built with Scala's modern functional ecosystem, providing compile-time data contracts and fiber-safe data pipelines.

## Quick Start

### 1. Generate a New Pipeline Project

```bash
sbt new vim89/flowforge.g8
```

### 2. Basic Pipeline Example

```scala
import cats.effect.IO
import com.flowforge.core.syntax.PipelineSyntax._
import com.flowforge.contracts.syntax.ContractDSL._

// Define data contract
val userContract = Contract("user")
  .field("id").required.long.positive
  .field("email").required.string.email.maxLength(255)
  .field("name").required.string.minLength(2).maxLength(100)
  .withSLA("hourly")
  .withOwner("DataPlatformTeam")
  .build

// Create type-safe pipeline
val pipeline = EnhancedPipelineBuilder.from[IO, User]("user-pipeline", source)
  .transform[CleanUser](user => IO.pure(cleanUser(user)))
  .validate(user => if (user.isValid) ().validNel else ValidationError("Invalid user").invalidNel)
  .to(sink)
  .build
```

### 3. Key Features

- **100% Type-Safe**: Compile-time guarantees eliminate runtime errors
- **Functional-First**: Pure functions, immutability, and effect management
- **Contract-Driven**: Data contracts ensure schema compliance
- **Multi-Engine**: Switch between Spark, Flink, and Kafka seamlessly
- **Production-Ready**: Built-in monitoring, lineage, and error handling

### 4. Project Structure

```
src/main/scala/
├── contracts/           # Data contracts and schemas
├── pipelines/          # Pipeline definitions
├── transformations/    # Data transformation logic
└── Main.scala         # Application entry point
```

### 5. Next Steps

1. Read the [Architecture Overview](architecture.md)
2. Explore [Contract System](contracts.md) 
3. Learn [Pipeline Patterns](patterns.md)
4. See [Production Deployment](deployment.md)

## Key Concepts

### Type-Safe Pipelines
FlowForge ensures your pipelines are type-safe from source to sink, catching schema mismatches at compile time.

### Effect Management
Uses Cats Effect or ZIO for fiber-safe concurrency and resource management.

### Contract-First Design
Define data contracts that are enforced throughout your pipeline execution.