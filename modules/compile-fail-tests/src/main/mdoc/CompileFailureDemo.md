# FlowForge Compile-Fail Demo: Pipelines Become Unbuildable When Schema Drift Occurs

This document demonstrates FlowForge's **unique selling proposition**: compile-time contract enforcement that makes pipelines unbuildable when schema drift occurs.

## The Core USP

FlowForge is the only Scala data engineering framework that:
1. Makes pipelines **fail to compile** when schema drift occurs
2. Provides beautiful error messages that guide developers to fixes
3. Uses phantom types and type witnesses for zero runtime overhead

## Working Example First

Here's a pipeline that compiles successfully because schemas match:

```scala mdoc
import cats.effect.IO
import com.flowforge.core.types._
import com.flowforge.core.instances.EffectInstances.catsEffectSystemInstance

// Define our contract
case class UserContract(
  id: String,
  email: String,
  age: Int
)

// Define matching pipeline output
case class UserRecord(
  id: String,
  email: String,
  age: Int
)

val source = DataSource.gcs("test-bucket", "users/*.parquet", DataFormat.Parquet)
val sink = DataSink.gcs("test-bucket", "processed/", DataFormat.Parquet)

// ✅ This compiles successfully - schemas match exactly
val workingPipeline = PipelineBuilder2[IO]("working-pipeline")
  .withDescription("Pipeline with perfect schema match")
  .addTransform[UserRecord](_ => 
    IO.pure(UserRecord("1", "test@example.com", 25)))
  .buildWithExactContract[UserContract] // ✅ Compiles!
```

## Compile Failure Demo #1: Field Name Drift

Now let's see what happens when we have schema drift:

```scala mdoc:fail
// Define drifted pipeline output - note 'emailAddress' vs 'email'
case class DriftedUserRecord(
  id: String,
  emailAddress: String, // ❌ Field name drift: 'emailAddress' vs 'email'
  age: Int
)

// ❌ This will NOT compile - schema drift detected
val driftedPipeline = PipelineBuilder2[IO]("schema-drift-pipeline")
  .withDescription("Pipeline with schema drift")
  .addTransform[DriftedUserRecord](_ => 
    IO.pure(DriftedUserRecord("1", "test@example.com", 25)))
  .buildWithExactContract[UserContract] // ❌ Compilation failure!
```

**Expected Error**: FlowForge Contract Drift Detected! Pipeline output type doesn't match contract.

## Compile Failure Demo #2: Missing Field

```scala mdoc:fail
// Define incomplete pipeline output - missing 'age' field
case class IncompleteUserRecord(
  id: String,
  email: String
  // ❌ Missing 'age' field required by contract
)

// ❌ This will NOT compile - missing required field
val incompletePipeline = PipelineBuilder2[IO]("incomplete-schema-pipeline")
  .withDescription("Pipeline with missing field")
  .addTransform[IncompleteUserRecord](_ => 
    IO.pure(IncompleteUserRecord("1", "test@example.com")))
  .buildWithExactContract[UserContract] // ❌ Compilation failure!
```

## The Fix: Correct Schema Evolution Policy

Here's how to fix extra field scenarios using the correct evolution policy:

```scala mdoc
// Define pipeline output with extra field
case class ExtraFieldUserRecord(
  id: String,
  email: String,
  age: Int,
  createdAt: Long // Extra field
)

// ✅ This compiles - using BackwardCompatible policy for extra fields
val backwardCompatiblePipeline = PipelineBuilder2[IO]("backward-compatible-pipeline")
  .withDescription("Pipeline with extra field using correct policy")
  .addTransform[ExtraFieldUserRecord](_ => 
    IO.pure(ExtraFieldUserRecord("1", "test@example.com", 25, System.currentTimeMillis())))
  .buildWithBackwardCompatibleContract[UserContract] // ✅ Compiles!
```

## Why This Matters

This compile-time contract enforcement is **unique** in the Scala ecosystem:

- **dbt**: Enforces contracts at runtime/CI time, not compile time
- **Dagster**: Asset checks run at runtime, not compile time
- **Frameless**: Type-safe Spark operations, but no contract system
- **Great Expectations**: Runtime data quality, not compile-time contracts

**Only FlowForge** makes your entire pipeline unbuildable when contracts drift, catching errors before they reach production.

## The Developer Experience

1. **Fast Feedback**: Errors caught in IDE, not in production
2. **Beautiful Messages**: Clear guidance on how to fix issues  
3. **Zero Runtime Cost**: Phantom types exist only at compile time
4. **Type Safety**: Leverages Scala's type system for maximum safety

This is FlowForge's differentiator - compile-time guarantees that prevent schema drift from ever reaching production.