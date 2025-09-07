# $name$

FlowForge data engineering pipeline with **100% compile-time contract validation**.

## 🎯 FlowForge USP Demonstrated

This pipeline proves FlowForge's core value proposition:

- ✅ **Compile-time contract validation** - pipelines won't even build if contracts drift
- ✅ **Type-safe transformations** - impossible to build broken pipelines  
- ✅ **Schema evolution policies** - control how schemas can evolve over time

## Generated Configuration

- **Effect System**: $effect_system$ 
- **Execution Engine**: $execution_engine$
- **Cloud Provider**: $cloud_provider$

## Quick Start

### 1. Build and Run
```bash
sbt compile
# Should succeed - all contracts align

sbt run  
# Pipeline executes successfully
```

### 2. See Contract Drift Prevention

Edit `src/main/scala/Contract.scala` and change:
```scala
case class UserEvent(
  id: Long,        // <- Change this to: id: String,
  userId: Long,
  eventName: String,
  timestamp: Long
)
```

Then try to build:
```bash
sbt compile
# Should fail with clear error showing contract drift!
```

Revert the change and build again:
```bash
sbt compile  
# Should succeed again
```

## What You Just Proved

🎯 **FlowForge's Core USP**: "Data pipelines will not even build if source or target schema do not match or align!"

- ✅ **Contract drift caught at compile time** - not runtime
- ✅ **Clear error messages** - know exactly what's wrong
- ✅ **Zero performance impact** - all validation happens during build
- ✅ **Type-safe composition** - impossible to build broken pipelines

## Project Structure

```
$name;format="norm"$/
├── src/main/scala/
│   ├── Contract.scala          # Data contracts - edit these!
│   └── Pipeline.scala          # Pipeline definition  
├── src/test/scala/            # Tests
├── build.sbt                 # Dependencies and config
└── README.md                 # This file
```

## Customization

### 1. Update Contracts

Edit `Contract.scala` to match your data:

```scala
case class YourInputEvent(
  // Add your fields here
  customField: String,
  amount: Double
)

case class YourProcessedEvent(
  // Add your output fields here  
  customField: String,
  amount: Double,
  processedAt: java.time.Instant
)
```

### 2. Configure Sources and Sinks

Update the source and sink configurations in `Pipeline.scala` for your environment.

## Schema Evolution Policies

FlowForge supports multiple schema evolution policies:

- **`Exact`**: Perfect match required (name, type, order)
- **`ExactUnordered`**: Perfect match (name, type) but flexible order
- **`Backward`**: New fields allowed if Optional or have defaults
- **`Forward`**: Missing fields allowed in output
- **`Full`**: Anything goes (escape hatch for development)

## Common Tasks

### Format Code
```bash
sbt scalafmt
```

### Run Tests  
```bash
sbt test
```

### Demo Contract Drift
```bash
make demo-contract-drift
```

## Support

- [FlowForge Documentation](https://flowforge.dev/docs)
- [GitHub Issues](https://github.com/flowforge/flowforge/issues)
- [Community Discussions](https://github.com/flowforge/flowforge/discussions)

---

*Generated with FlowForge $flowforgeVersion$ • 100% Compile-Time Contracts*