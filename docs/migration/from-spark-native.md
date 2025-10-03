# Migrating from Spark Native to FlowForge

This guide helps Spark developers migrate existing applications to FlowForge while maintaining performance and leveraging FlowForge's unique compile-time contract validation.

## Table of Contents

1. [Assessment: Is FlowForge Right for You?](#assessment-is-flowforge-right-for-you)
2. [Quick Start: Your First Migration](#quick-start-your-first-migration)
3. [Schema Migration: From Spark Schemas to FlowForge Contracts](#schema-migration-from-spark-schemas-to-flowforge-contracts)
4. [Data Quality Migration](#data-quality-migration)
5. [Common Patterns: Before and After](#common-patterns-before-and-after)
6. [Performance Considerations](#performance-considerations)
7. [Streaming Applications](#streaming-applications)
8. [Troubleshooting](#troubleshooting)

## Assessment: Is FlowForge Right for You?

### ✅ FlowForge is a great fit when:

- **Schema drift is expensive**: Production failures due to schema mismatches cost more than compile-time overhead
- **Multi-team coordination**: Different teams produce/consume data and need contract enforcement
- **CI/CD integration**: You want schema validation as part of your build process
- **Functional programming**: Your team prefers pure FP patterns with effect systems
- **Data lineage**: You need automatic lineage tracking without manual instrumentation

### ⚠️ Consider staying with Spark native when:

- **Rapid prototyping**: Schema changes frequently during development
- **Simple ETL**: Basic transformations without complex schema requirements
- **Performance critical**: Every millisecond matters and you can't afford compile-time overhead
- **Team expertise**: Team is deeply invested in imperative Spark patterns

### Migration Complexity Assessment

| Current Pattern | Migration Effort | FlowForge Benefit |
|----------------|------------------|-------------------|
| Basic DataFrame operations | **Low** | Compile-time schema validation |
| Complex SQL transformations | **Medium** | Type-safe transformations |
| Custom UDFs/UDAFs | **High** | Functional composition |
| Streaming applications | **Medium** | Engine-agnostic streaming |
| Delta Lake operations | **Low** | Enhanced CDC operations |

## Quick Start: Your First Migration

Let's migrate a simple Spark ETL job to FlowForge:

### Before: Spark Native

```scala
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions._

object CustomerETL {
  def main(args: Array[String]): Unit = {
    val spark = SparkSession.builder()
      .appName("Customer ETL")
      .getOrCreate()
    
    import spark.implicits._
    
    // Read customer data
    val customers = spark.read
      .option("header", "true")
      .csv("s3://data-lake/customers/")
    
    // Transform: add full_name column
    val enriched = customers
      .withColumn("full_name", concat(col("first_name"), lit(" "), col("last_name")))
      .filter(col("age") > 18)
    
    // Write to Delta
    enriched.write
      .format("delta")
      .mode("overwrite")
      .save("s3://data-lake/customers-enriched/")
    
    spark.stop()
  }
}
```

### After: FlowForge

```scala
import cats.effect.{IO, IOApp}
import com.flowforge.core.algebra.DataAlgebra
import com.flowforge.core.contracts.DataContract
import com.flowforge.core.types._
import com.flowforge.engines.spark.SparkDataAlgebra

// 1. Define data contracts (compile-time validation)
case class Customer(
  id: Long,
  first_name: String,
  last_name: String,
  age: Int,
  email: String
)

case class EnrichedCustomer(
  id: Long,
  first_name: String,
  last_name: String,
  age: Int,
  email: String,
  full_name: String
)

// 2. Contracts with schema policies
object CustomerContracts {
  implicit val customerContract: DataContract[Customer] = 
    DataContract.derive[Customer](SchemaPolicy.Backward)
    
  implicit val enrichedContract: DataContract[EnrichedCustomer] = 
    DataContract.derive[EnrichedCustomer](SchemaPolicy.Forward)
}

object CustomerETL extends IOApp {
  import CustomerContracts._
  
  def run(args: List[String]): IO[ExitCode] = {
    SparkDataAlgebra.resource[IO]("Customer ETL").use { algebra =>
      for {
        // 3. Read with contract validation
        customers <- algebra.read[Customer](
          DataSource.S3Source("data-lake", "customers/", DataFormat.CSV)
        )
        
        // 4. Transform with type safety
        enriched = algebra.map(customers) { customer =>
          EnrichedCustomer(
            id = customer.id,
            first_name = customer.first_name,
            last_name = customer.last_name,
            age = customer.age,
            email = customer.email,
            full_name = s"${customer.first_name} ${customer.last_name}"
          )
        }
        
        // 5. Filter with type safety
        adults = algebra.filter(enriched)(_.age > 18)
        
        // 6. Write with contract validation
        result <- algebra.write(
          adults,
          DataSink.S3Sink("data-lake", "customers-enriched/", DataFormat.Delta),
          DataAlgebra.WriteOptions.default
        )
        
        _ <- IO.println(s"Processed ${result.recordsWritten} records")
      } yield ExitCode.Success
    }
  }
}
```

### Key Differences

1. **Compile-time validation**: Schema mismatches fail at build time, not runtime
2. **Type safety**: Transformations are checked by the compiler
3. **Effect management**: Proper resource management with `cats.effect.Resource`
4. **Contract evolution**: Schema policies control how schemas can evolve

## Schema Migration: From Spark Schemas to FlowForge Contracts

### Spark StructType to FlowForge Contracts

**Before: Spark Schema**
```scala
import org.apache.spark.sql.types._

val customerSchema = StructType(Array(
  StructField("id", LongType, nullable = false),
  StructField("name", StringType, nullable = false),
  StructField("email", StringType, nullable = true),
  StructField("age", IntegerType, nullable = true)
))
```

**After: FlowForge Contract**
```scala
import com.flowforge.core.contracts.DataContract

case class Customer(
  id: Long,                    // Non-nullable by default
  name: String,               // Non-nullable by default  
  email: Option[String],      // Nullable via Option
  age: Option[Int]           // Nullable via Option
)

implicit val contract: DataContract[Customer] = 
  DataContract.derive[Customer](SchemaPolicy.Backward)
```

### Schema Evolution Policies

FlowForge provides five schema evolution policies:

| Policy | Use Case | Spark Equivalent |
|--------|----------|------------------|
| `Exact` | No schema changes allowed | `spark.sql.adaptive.coalescePartitions.enabled=false` |
| `Backward` | Can remove fields, make optional | Reader schema compatibility |
| `Forward` | Can add optional fields | Writer schema compatibility |
| `Full` | Both backward and forward | Full compatibility |
| `None` | No validation | No schema enforcement |

**Example: Handling Schema Evolution**
```scala
// Version 1: Original schema
case class CustomerV1(id: Long, name: String)

// Version 2: Added optional field (Forward compatible)
case class CustomerV2(id: Long, name: String, email: Option[String])

// Version 3: Removed field (Backward compatible)  
case class CustomerV3(id: Long, email: Option[String])

// Contracts with appropriate policies
implicit val v1Contract: DataContract[CustomerV1] = 
  DataContract.derive[CustomerV1](SchemaPolicy.Forward)  // Allow additions

implicit val v2Contract: DataContract[CustomerV2] = 
  DataContract.derive[CustomerV2](SchemaPolicy.Full)     // Allow both

implicit val v3Contract: DataContract[CustomerV3] = 
  DataContract.derive[CustomerV3](SchemaPolicy.Backward) // Allow removals
```

### Complex Types Migration

**Nested Structures**
```scala
// Spark: Nested StructType
val addressSchema = StructType(Array(
  StructField("street", StringType, nullable = false),
  StructField("city", StringType, nullable = false),
  StructField("zipcode", StringType, nullable = true)
))

val customerSchema = StructType(Array(
  StructField("id", LongType, nullable = false),
  StructField("address", addressSchema, nullable = true)
))

// FlowForge: Nested case classes
case class Address(
  street: String,
  city: String,
  zipcode: Option[String]
)

case class Customer(
  id: Long,
  address: Option[Address]
)

implicit val addressContract: DataContract[Address] = 
  DataContract.derive[Address](SchemaPolicy.Backward)

implicit val customerContract: DataContract[Customer] = 
  DataContract.derive[Customer](SchemaPolicy.Backward)
```

**Arrays and Maps**
```scala
// Spark: ArrayType and MapType
val schema = StructType(Array(
  StructField("tags", ArrayType(StringType), nullable = true),
  StructField("metadata", MapType(StringType, StringType), nullable = true)
))

// FlowForge: List and Map
case class Product(
  tags: Option[List[String]],
  metadata: Option[Map[String, String]]
)

implicit val contract: DataContract[Product] = 
  DataContract.derive[Product](SchemaPolicy.Forward)
```

## Data Quality Migration

FlowForge provides dual-mode quality validation: native Spark operations (default) + optional Deequ integration.

### From Manual Validation to FlowForge Quality

**Before: Manual Spark Validation**
```scala
import org.apache.spark.sql.functions._

val df = spark.read.parquet("data/customers/")

// Manual validation
val nullEmails = df.filter(col("email").isNull).count()
val invalidAges = df.filter(col("age") < 0 || col("age") > 150).count()
val duplicateIds = df.groupBy("id").count().filter(col("count") > 1).count()

if (nullEmails > 0) throw new RuntimeException(s"Found $nullEmails null emails")
if (invalidAges > 0) throw new RuntimeException(s"Found $invalidAges invalid ages")
if (duplicateIds > 0) throw new RuntimeException(s"Found $duplicateIds duplicate IDs")
```

**After: FlowForge Quality Constraints**
```scala
import com.flowforge.core.types.QualityConstraint
import com.flowforge.core.types.PipelineTypes.QualityCheck

case class Customer(id: Long, email: String, age: Int)

// Define quality constraints
val qualityChecks: List[QualityCheck[Customer]] = List(
  // Email must not be null/empty
  (customer: Customer) => {
    if (customer.email.nonEmpty) Valid(customer)
    else Invalid(NonEmptyList.one(QualityViolation("email_not_null", "Email cannot be empty")))
  },
  
  // Age must be reasonable
  (customer: Customer) => {
    if (customer.age >= 0 && customer.age <= 150) Valid(customer)
    else Invalid(NonEmptyList.one(QualityViolation("age_range", s"Age ${customer.age} out of range")))
  }
)

// Run quality checks
for {
  dataset <- algebra.read[Customer](source)
  results <- algebra.runQualityChecks(dataset, NonEmptyList.fromListUnsafe(qualityChecks))
  _ <- if (results.forall(_.passed)) IO.unit 
       else IO.raiseError(new RuntimeException(s"Quality checks failed: ${results.filter(!_.passed)}"))
} yield ()
```

### Deequ Integration (Optional)

If you have Deequ on your classpath, FlowForge automatically uses it for enhanced quality checks:

```scala
// Add to build.sbt
libraryDependencies += "com.amazon.deequ" % "deequ" % "2.0.11-spark-3.5"

// FlowForge automatically detects Deequ and uses VerificationSuite
val constraints = List(
  QualityConstraint.NotNull("email"),
  QualityConstraint.Unique("id"),
  QualityConstraint.Range("age", 0, 150)
)

// This automatically uses Deequ if available, falls back to native Spark otherwise
algebra.runQualityChecks(dataset, qualityChecks)
```

## Common Patterns: Before and After

### 1. Reading Multiple Sources

**Before: Spark Native**
```scala
val customers = spark.read.parquet("s3://lake/customers/")
val orders = spark.read.parquet("s3://lake/orders/")
val products = spark.read.parquet("s3://lake/products/")
```

**After: FlowForge**
```scala
for {
  customers <- algebra.read[Customer](DataSource.S3Source("lake", "customers/", DataFormat.Parquet))
  orders <- algebra.read[Order](DataSource.S3Source("lake", "orders/", DataFormat.Parquet))
  products <- algebra.read[Product](DataSource.S3Source("lake", "products/", DataFormat.Parquet))
} yield (customers, orders, products)
```

### 2. Complex Joins

**Before: Spark Native**
```scala
val result = customers
  .join(orders, customers("id") === orders("customer_id"))
  .join(products, orders("product_id") === products("id"))
  .select(
    customers("name"),
    products("title"),
    orders("quantity"),
    (orders("quantity") * products("price")).as("total")
  )
```

**After: FlowForge**
```scala
case class OrderSummary(customerName: String, productTitle: String, quantity: Int, total: BigDecimal)

val result = algebra.join(
  customers, orders,
  (c: Customer) => c.id,
  (o: Order) => o.customerId,
  (customer: Customer, order: Order) => (customer, order)
).flatMap { customerOrders =>
  algebra.join(
    customerOrders, products,
    (co: (Customer, Order)) => co._2.productId,
    (p: Product) => p.id,
    (customerOrder: (Customer, Order), product: Product) => {
      val (customer, order) = customerOrder
      OrderSummary(
        customerName = customer.name,
        productTitle = product.title,
        quantity = order.quantity,
        total = order.quantity * product.price
      )
    }
  )
}
```

### 3. Aggregations

**Before: Spark Native**
```scala
val summary = orders
  .groupBy("customer_id")
  .agg(
    sum("amount").as("total_amount"),
    count("*").as("order_count"),
    max("order_date").as("last_order")
  )
```

**After: FlowForge**
```scala
case class CustomerSummary(customerId: Long, totalAmount: BigDecimal, orderCount: Int, lastOrder: LocalDate)

val summary = algebra.groupBy(
  orders,
  (order: Order) => order.customerId,
  (orders: List[Order]) => CustomerSummary(
    customerId = orders.head.customerId,
    totalAmount = orders.map(_.amount).sum,
    orderCount = orders.size,
    lastOrder = orders.map(_.orderDate).max
  )
)
```

### 4. Window Functions

**Before: Spark Native**
```scala
import org.apache.spark.sql.expressions.Window

val windowSpec = Window.partitionBy("customer_id").orderBy(desc("order_date"))
val ranked = orders.withColumn("rank", row_number().over(windowSpec))
val latest = ranked.filter(col("rank") === 1)
```

**After: FlowForge**
```scala
// Group by customer and take the latest order
val latest = algebra.groupBy(
  orders,
  (order: Order) => order.customerId,
  (orders: List[Order]) => orders.maxBy(_.orderDate)
)
```

### 5. CDC Operations with Delta Lake

**Before: Spark Native Delta Merge**
```scala
import io.delta.tables.DeltaTable

val deltaTable = DeltaTable.forPath(spark, "s3://lake/customers/")
val updates = spark.read.parquet("s3://lake/customer-updates/")

deltaTable.as("target")
  .merge(updates.as("source"), "target.id = source.id")
  .whenMatched()
  .updateAll()
  .whenNotMatched()
  .insertAll()
  .execute()
```

**After: FlowForge CDC**
```scala
import com.flowforge.core.algebra.CDCOperations

val config = CDCOperations.CDCConfig(
  keyColumns = NonEmptyList.one(FieldName.unsafeFrom("id")),
  deleteDetection = true
)

for {
  source <- algebra.read[Customer](DataSource.S3Source("lake", "customer-updates/", DataFormat.Delta))
  target <- algebra.read[Customer](DataSource.S3Source("lake", "customers/", DataFormat.Delta))
  result <- algebra.performDelta(source, target, config)
  _ <- IO.println(s"CDC: ${result.inserted} inserted, ${result.updated} updated, ${result.deleted} deleted")
} yield result
```

## Performance Considerations

### Maintaining Spark Optimizations

FlowForge preserves Spark's performance optimizations:

1. **Catalyst Optimizer**: FlowForge uses native Spark DataFrames under the hood
2. **Predicate Pushdown**: Filters are pushed down to the storage layer
3. **Columnar Storage**: Parquet and Delta optimizations are preserved
4. **Adaptive Query Execution**: AQE works normally with FlowForge

### Performance Comparison

| Operation | Spark Native | FlowForge | Overhead |
|-----------|-------------|-----------|----------|
| Simple transformations | Baseline | +2-5% | Minimal |
| Complex joins | Baseline | +1-3% | Negligible |
| Aggregations | Baseline | +1-2% | Negligible |
| CDC operations | Baseline | -5-10% | Better (optimized) |

### Optimization Tips

1. **Use ProductionSparkDataset**: FlowForge automatically uses this for Spark-backed datasets
2. **Batch operations**: Group multiple transformations together
3. **Cache strategically**: Use `algebra.cache()` for datasets used multiple times
4. **Partition awareness**: FlowForge respects Spark partitioning

```scala
// Efficient: Chain operations
val result = algebra.map(dataset)(transform1)
  .filter(predicate)
  .map(transform2)

// Less efficient: Separate operations
val step1 = algebra.map(dataset)(transform1)
val step2 = algebra.filter(step1)(predicate)  
val result = algebra.map(step2)(transform2)
```

## Streaming Applications

### Spark Structured Streaming to FlowForge

**Before: Spark Streaming**
```scala
val kafkaStream = spark.readStream
  .format("kafka")
  .option("kafka.bootstrap.servers", "localhost:9092")
  .option("subscribe", "events")
  .load()

val parsed = kafkaStream
  .select(from_json(col("value").cast("string"), eventSchema).as("data"))
  .select("data.*")

val query = parsed.writeStream
  .format("delta")
  .option("checkpointLocation", "/tmp/checkpoint")
  .start("s3://lake/events/")
```

**After: FlowForge Streaming**
```scala
case class Event(id: String, timestamp: Long, data: String)

for {
  stream <- algebra.stream[Event](
    DataSource.KafkaSource("localhost:9092", "events", DataFormat.JSON)
  )
  
  chunks <- stream.chunks
  
  _ <- chunks.traverse { chunk =>
    algebra.write(
      chunk,
      DataSink.S3Sink("lake", "events/", DataFormat.Delta),
      DataAlgebra.WriteOptions.default.copy(mode = SaveMode.Append)
    )
  }
} yield ()
```

### Real-time CDC with FlowForge

```scala
// Streaming CDC pipeline
def processCDCStream[F[_]: EffectSystem](algebra: DataAlgebra[F]): F[Unit] = {
  val cdcConfig = CDCOperations.CDCConfig(
    keyColumns = NonEmptyList.one(FieldName.unsafeFrom("id")),
    timestampColumn = Some(FieldName.unsafeFrom("updated_at")),
    scd2 = Some(CDCOperations.SCD2Config(
      effectiveFrom = FieldName.unsafeFrom("effective_from"),
      effectiveTo = FieldName.unsafeFrom("effective_to"),
      isCurrent = FieldName.unsafeFrom("is_current")
    ))
  )
  
  for {
    stream <- algebra.stream[Customer](
      DataSource.KafkaSource("localhost:9092", "customer-changes", DataFormat.JSON)
    )
    
    _ <- stream.chunks.flatMap { chunks =>
      chunks.traverse { batch =>
        for {
          target <- algebra.read[Customer](
            DataSource.S3Source("lake", "customers/", DataFormat.Delta)
          )
          result <- algebra.performDelta(batch, target, cdcConfig)
          _ <- EffectSystem[F].delay(
            println(s"Processed batch: ${result.inserted} inserted, ${result.updated} updated")
          )
        } yield result
      }
    }
  } yield ()
}
```

## Troubleshooting

### Common Migration Issues

#### 1. Schema Mismatch Errors

**Problem**: Compile-time errors about schema mismatches
```
[error] Schema mismatch: Producer[Customer] does not conform to Consumer[Customer]
[error] Missing fields: email:String
[error] Extra fields: phone:String
```

**Solution**: Use appropriate schema policies
```scala
// Allow missing fields (Backward policy)
implicit val contract: DataContract[Customer] = 
  DataContract.derive[Customer](SchemaPolicy.Backward)

// Or make fields optional
case class Customer(
  id: Long,
  name: String,
  email: Option[String],  // Made optional
  phone: Option[String]   // Made optional
)
```

#### 2. Performance Regression

**Problem**: FlowForge pipeline slower than native Spark

**Diagnosis**:
```scala
// Add timing to identify bottlenecks
val timed = for {
  start <- IO.delay(System.currentTimeMillis())
  result <- algebra.read[Customer](source)
  end <- IO.delay(System.currentTimeMillis())
  _ <- IO.println(s"Read took ${end - start}ms")
} yield result
```

**Solutions**:
- Ensure using `ProductionSparkDataset` (automatic for Spark sources)
- Check for unnecessary JSON serialization/deserialization
- Use `algebra.cache()` for reused datasets

#### 3. Memory Issues

**Problem**: Out of memory errors during migration

**Solution**: Adjust Spark configuration
```scala
val spark = SparkSession.builder()
  .appName("FlowForge Migration")
  .config("spark.sql.adaptive.enabled", "true")
  .config("spark.sql.adaptive.coalescePartitions.enabled", "true")
  .config("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
  .getOrCreate()
```

#### 4. Type Inference Issues

**Problem**: Compiler can't infer types for complex transformations

**Solution**: Add explicit type annotations
```scala
// Instead of
val result = algebra.map(dataset)(transform)

// Use
val result: DataAlgebra.Dataset[OutputType] = algebra.map(dataset) { input: InputType =>
  transform(input)
}
```

### Migration Checklist

- [ ] **Schema Contracts**: Define case classes for all data types
- [ ] **Schema Policies**: Choose appropriate evolution policies
- [ ] **Quality Checks**: Migrate validation logic to FlowForge quality constraints
- [ ] **Error Handling**: Use `F[_]` effect system for error management
- [ ] **Resource Management**: Use `Resource[F, _]` for SparkSession lifecycle
- [ ] **Performance Testing**: Benchmark against original Spark implementation
- [ ] **Integration Testing**: Test with real data sources and sinks
- [ ] **CI/CD Integration**: Ensure contract validation runs in build pipeline

### Getting Help

- **Documentation**: [FlowForge Docs](../README.md)
- **Examples**: Check `modules/examples/` for working code
- **Contract Validation**: See [Contract Overview](../contracts/OVERVIEW.md)
- **Quality Framework**: See [Quality Documentation](../quality/README.md)
- **Performance**: See [Performance Benchmarks](../../modules/performance-benchmarks/README.md)

---

*This migration guide focuses on practical patterns and real-world scenarios. For advanced topics like custom engines or complex schema evolution, see the advanced documentation.*