# $name$ - FlowForge v1.0.0 Data Pipeline

Generated from FlowForge template with **F-polymorphic effects** and **compile-time contracts**.

## 🚀 Quick Start (< 2 minutes)

```bash
# 1. Compile (this validates all contracts at compile-time!)
sbt compile

# 2. Run the complete pipeline
sbt "runMain $organization$.$name;format="word"$.UsersPipelineApp"
```

**Expected Output:**
```
🚀 FlowForge v1.0.0 F-Polymorphic Pipeline Starting
✅ Writing to Parquet: CleanedUser(1,alice johnson,alice@example.com,28,USA,true)
✅ Writing to Delta with constraints: EnrichedUser(1,alice johnson,alice@example.com,28,USA,true,middle,North America)
✅ Pipeline completed successfully: EnrichedUser(...)
📊 Contract validation: PASSED (compile-time enforced)
🔍 Quality checks: COMPLETED  
📈 Lineage events: EMITTED
💾 Delta constraints: APPLIED
```

## ✨ What This Template Demonstrates

### 🔒 **Compile-Time Contracts (FlowForge's Core USP)**
- Pipelines **literally won't compile** if schemas don't match
- Zero runtime contract failures - all validation at build time
- 5 schema evolution policies: `Exact`, `ExactUnordered`, `Backward`, `Forward`, `Full`

### ⚡ **F-Polymorphic Effects**
- Works with **any effect system** (`IO`, `ZIO`, `Future`, etc.)
- Clean separation: pure transformations + effectful I/O
- Resource-safe operations with automatic cleanup

### 🔄 **Complete Data Journey**
- **CSV → Parquet → Delta** with full type safety
- Runtime quality checks + Delta table constraints
- Automatic lineage emission with OpenLineage
- Production-ready error handling

## 📋 Generated Configuration

- **Effect System**: $effect_system$ (F-polymorphic with Cats Effect instance)
- **Execution Engine**: $execution_engine$ (Spark 3.5.6)  
- **Cloud Provider**: $cloud_provider$
- **Quality Checks**: $if(include_dq.truthy)$✅ Deequ integration enabled$else$Native Spark checks only$endif$
- **Lineage**: $if(include_lineage.truthy)$✅ OpenLineage events enabled$else$Noop lineage emitter$endif$

## 🧪 Testing Contract Validation

### See Compile-Time Failures in Action

Open `src/main/scala/ContractDemos.scala` and **uncomment the failure examples**:

```scala
// Uncomment this and run sbt compile - it will FAIL!
case class IncompleteUser(id: Long, name: String) // Missing email, age, country, isActive
def failingPipeline[F[_]: EffectSystem](): F[Unit] = {
  val pipeline = PipelineBuilder[F]("failing")
    .addTypedSource[IncompleteUser, RawUser, SchemaPolicy.Exact](source, _ => ???)
  // ☝️ This will not compile! Missing fields detected at compile time
}
```

**You'll get a clear compile error:**
```
FlowForge: Contract drift (policy: SchemaPolicy.Exact)
Out: IncompleteUser vs Contract: RawUser
Missing: email, age, country, isActive
```

### Try All 5 Schema Policies

```scala
// 1. EXACT: Perfect match required
PipelineBuilder[F]("exact")
  .addTypedSource[User, User, SchemaPolicy.Exact](source, reader)

// 2. BACKWARD: Contract ⊆ Output (allows extra fields in output)  
PipelineBuilder[F]("backward")
  .addTypedSource[ExtendedUser, User, SchemaPolicy.Backward](source, reader)

// 3. FORWARD: Output ⊆ Contract (allows extra fields in contract)
PipelineBuilder[F]("forward") 
  .addTypedSource[User, ExtendedUser, SchemaPolicy.Forward](source, reader)

// 4. EXACT_UNORDERED: Same fields, any order
PipelineBuilder[F]("exact-unordered")
  .addTypedSource[User, User, SchemaPolicy.ExactUnordered](source, reader)

// 5. FULL: Allow anything (escape hatch)
PipelineBuilder[F]("full")
  .addTypedSource[AnyUser, AnyOtherUser, SchemaPolicy.Full](source, reader)
```

## 📁 Project Structure

```
src/main/scala/
├── Pipeline.scala           # Main F-polymorphic pipeline
├── ContractDemos.scala     # Contract validation examples  
└── ...                     # Additional pipeline components

data/                       # Sample input data
output/                     # Pipeline output directory
```

### File Organization (Following CLAUDE.md Guidelines)

- **Domain models** at top of files with their Shape derivations
- **Related types grouped** when justified (e.g., all User variants)  
- **One primary class per file** for navigation efficiency
- **Companions live together** with main types

## 🔍 Deep Dive: How It Works

### Contract Enforcement Flow

1. **Compile Time**: Magnolia derives field metadata for all case classes
2. **Type Checking**: `SchemaConforms[Out, Contract, Policy]` validates compatibility  
3. **Build Time**: Invalid pipelines fail to compile with clear error messages
4. **Runtime**: Only valid, contract-compliant pipelines execute

### Effect Polymorphism

```scala
// Generic over any effect type F[_]
class FlowForgePipeline[F[_]: EffectSystem] {
  private val F = EffectSystem[F]
  
  def processData(data: RawUser): F[CleanedUser] = 
    F.delay(cleanAndValidate(data))  // Works with IO, ZIO, etc.
}

// Instantiate with concrete effect
val ioInstance = new FlowForgePipeline[cats.effect.IO]()
val zioInstance = new FlowForgePipeline[zio.ZIO[Any, Throwable, *]]()
```

### Schema Evolution Example

```scala
// Version 1: Original contract
case class UserV1(id: Long, name: String, email: String)

// Version 2: Add optional field (backward compatible)
case class UserV2(id: Long, name: String, email: String, country: Option[String])

// This compiles - V2 producer, V1 consumer
val pipeline = PipelineBuilder[F]("evolution")
  .addTypedSource[UserV2, UserV1, SchemaPolicy.Backward](v2Source, reader)
```

## 🚀 Production Deployment

### Environment Configuration

```bash
# OpenLineage (optional)
export OPENLINEAGE_URL="https://your-lineage-endpoint/api/v1/lineage"
export OPENLINEAGE_NAMESPACE="production"

# Spark Configuration  
export SPARK_CONF="spark.master=yarn,spark.sql.adaptive.enabled=true"

# Cloud Storage (example for GCP)
export GOOGLE_APPLICATION_CREDENTIALS="/path/to/service-account.json"
```

### Docker Deployment

```dockerfile
FROM openjdk:11-jre-slim

COPY target/scala-2.13/$name$-assembly-*.jar app.jar
COPY data/ /app/data/

ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Quality Gates Integration

The pipeline includes built-in quality gates:

```bash
# All these must pass before deployment
sbt compile        # ✅ Contract validation (compile-time)
sbt test          # ✅ Unit and integration tests  
sbt assembly      # ✅ Fat JAR with all dependencies
./deploy.sh       # ✅ Deploy to production
```

## 🔧 Advanced Usage

### Custom Effect Systems

```scala
// Bring your own effect system
import zio._
import com.flowforge.core.instances.ZIOEffectInstance._

val zioVersionPipeline = new FlowForgePipeline[ZIO[Any, Throwable, *]]()
```

### Multi-Cloud Storage

```scala
// Same pipeline, different storage
val gcsSource = TypedSource[RawUser](
  DataSource.gcs("your-bucket", "input/users.csv", DataFormat.CSV)
)

val s3Sink = TypedSink[EnrichedUser](
  DataSink.s3("your-s3-bucket", "output/enriched/", DataFormat.Delta)
)
```

### Custom Quality Rules

```scala
// Add domain-specific quality checks
.addTransform[CleanedUser] { user =>
  if (isValidBusinessUser(user)) F.pure(user)
  else F.raiseError(new ValidationException(s"Business rule violation: \$user"))
}
```

## 🏃‍♂️ Next Steps

1. **Explore Contract Failures**: Uncomment examples in `ContractDemos.scala`
2. **Add Your Data**: Replace sample data with your actual datasets
3. **Extend Transformations**: Add business logic to the pipeline stages
4. **Production Setup**: Configure cloud storage and lineage endpoints
5. **Quality Rules**: Add domain-specific validation logic

## 💡 Why FlowForge?

| Problem | Traditional Approach | FlowForge Solution |
|---------|---------------------|-------------------|
| Schema drift breaks production | Runtime failures, data corruption | **Compile-time prevention** |  
| Effect system lock-in | Committed to IO/ZIO/Future | **F-polymorphic flexibility** |
| Complex quality testing | Manual validation, runtime checks | **Automatic contract + quality** |
| No lineage visibility | Custom instrumentation needed | **Built-in OpenLineage** |
| Framework coupling | Tied to Spark/Flink specifics | **Engine-agnostic transforms** |

## 📚 Learn More

- [FlowForge Documentation](https://flowforge.example.com/docs)
- [Schema Evolution Policies](https://flowforge.example.com/docs/contracts)
- [Effect System Integration](https://flowforge.example.com/docs/effects)
- [Production Deployment Guide](https://flowforge.example.com/docs/deployment)

---

**🎯 Remember**: With FlowForge, if it compiles, your contracts are valid. No surprises in production!