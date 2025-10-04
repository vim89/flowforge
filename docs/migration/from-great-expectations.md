# Migrating from Great Expectations to FlowForge

This guide helps Great Expectations users transition to FlowForge's compile-time contract validation and dual-mode data quality framework.

## Why Migrate from Great Expectations?

Great Expectations provides excellent runtime data validation, but FlowForge offers unique advantages:

- **Compile-time validation**: Catch schema issues before deployment
- **Zero-dependency default**: Native Spark checks without external libraries
- **Engine agnostic**: Same quality logic across Spark, Flink, and other engines
- **Integrated lineage**: Built-in OpenLineage emission
- **Type safety**: Scala's type system prevents many data quality issues at compile time

## Quality Framework Comparison

| Great Expectations | FlowForge | Key Difference |
|-------------------|-----------|----------------|
| Runtime validation | Compile-time + Runtime | Prevents issues before deployment |
| Python-based | Scala-based | Type safety and functional programming |
| Expectation suites | Quality constraints + Contracts | Schema validation integrated |
| Data docs | Lineage + Quality reports | Unified observability |
| Checkpoints | CI/CD with contract validation | Build-time failure prevention |

## Expectation Translation Guide

### Basic Expectations

| Great Expectations | FlowForge Equivalent | Example |
|-------------------|---------------------|---------|
| `expect_column_values_to_not_be_null` | `NotNull` | `NotNull(Field("email"))` |
| `expect_column_values_to_be_unique` | `Unique` | `Unique(Field("user_id"))` |
| `expect_column_values_to_be_between` | `Range` | `Range(Field("age"), Some(0), Some(120))` |
| `expect_column_values_to_match_regex` | `Pattern` | `Pattern(Field("email"), "^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}$")` |
| `expect_column_values_to_be_in_set` | `Compliance` | `Compliance("status_check", "status IN ('active', 'inactive')")` |

### Advanced Expectations

| Great Expectations | FlowForge Equivalent | Notes |
|-------------------|---------------------|-------|
| `expect_column_distinct_values_to_be_in_set` | `Distinctness` + `Compliance` | Combine constraints |
| `expect_column_proportion_of_unique_values_to_be_between` | `Distinctness` | `Distinctness(Field("id"), 0.95)` |
| `expect_column_values_to_not_match_regex` | `Pattern` with negation | Use SQL NOT in Compliance |
| `expect_table_row_count_to_be_between` | Custom `Compliance` | `Compliance("row_count", "COUNT(*) BETWEEN 1000 AND 10000")` |

## Migration Examples

### Example 1: Basic User Data Validation

**Great Expectations Suite:**
```python
# great_expectations/expectations/user_data_suite.json
{
  "expectation_suite_name": "user_data_suite",
  "expectations": [
    {
      "expectation_type": "expect_column_values_to_not_be_null",
      "kwargs": {"column": "user_id"}
    },
    {
      "expectation_type": "expect_column_values_to_be_unique",
      "kwargs": {"column": "email"}
    },
    {
      "expectation_type": "expect_column_values_to_be_between",
      "kwargs": {"column": "age", "min_value": 13, "max_value": 120}
    },
    {
      "expectation_type": "expect_column_values_to_match_regex",
      "kwargs": {"column": "email", "regex": "^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}$"}
    }
  ]
}
```

**FlowForge Equivalent:**
```scala
import com.flowforge.core.types._
import com.flowforge.core.contracts._

// 1. Define the contract (compile-time validation)
case class User(
  user_id: String,
  email: String,
  age: Int,
  created_at: java.time.Instant
)

object UserContract extends DataContract[User] {
  override def schemaPolicy: SchemaPolicy = SchemaPolicy.Strict
  
  // 2. Define quality constraints (runtime validation)
  override def qualityConstraints: List[QualityConstraint] = List(
    QualityConstraint.NotNull(Field("user_id")),
    QualityConstraint.Unique(Field("email")),
    QualityConstraint.Range(Field("age"), Some(13), Some(120)),
    QualityConstraint.Pattern(Field("email"), "^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}$")
  )
}

// 3. Use in pipeline with automatic validation
class UserPipeline[F[_]: DataAlgebra] {
  def processUsers(rawData: Dataset[RawUser]): F[Dataset[User]] = {
    for {
      // Contract validation happens at compile time
      validated <- rawData.validateContract(UserContract)
      // Quality constraints run at execution time
      qualityChecked <- validated.runQualityChecks(UserContract.qualityConstraints)
    } yield qualityChecked
  }
}
```

### Example 2: Advanced Statistical Validation

**Great Expectations with Statistical Expectations:**
```python
# Advanced GE suite with statistical validation
{
  "expectations": [
    {
      "expectation_type": "expect_column_mean_to_be_between",
      "kwargs": {"column": "transaction_amount", "min_value": 50, "max_value": 500}
    },
    {
      "expectation_type": "expect_column_stdev_to_be_between", 
      "kwargs": {"column": "transaction_amount", "min_value": 10, "max_value": 200}
    },
    {
      "expectation_type": "expect_column_proportion_of_unique_values_to_be_between",
      "kwargs": {"column": "customer_id", "min_value": 0.8, "max_value": 1.0}
    }
  ]
}
```

**FlowForge with Deequ Integration:**
```scala
import com.flowforge.quality.deequ.DeequAdapter

case class Transaction(
  transaction_id: String,
  customer_id: String,
  transaction_amount: Double,
  transaction_date: java.time.LocalDate
)

object TransactionContract extends DataContract[Transaction] {
  override def schemaPolicy: SchemaPolicy = SchemaPolicy.Backward
  
  override def qualityConstraints: List[QualityConstraint] = List(
    // Basic constraints (native Spark)
    QualityConstraint.NotNull(Field("transaction_id")),
    QualityConstraint.Range(Field("transaction_amount"), Some(0.01), Some(10000.0)),
    
    // Advanced statistical constraints (Deequ when available)
    QualityConstraint.Compliance(
      "mean_amount_check",
      "AVG(transaction_amount) BETWEEN 50 AND 500"
    ),
    QualityConstraint.Distinctness(Field("customer_id"), 0.8),
    
    // Custom business rules
    QualityConstraint.Compliance(
      "weekend_transaction_limit",
      "CASE WHEN DAYOFWEEK(transaction_date) IN (1,7) THEN transaction_amount <= 1000 ELSE true END"
    )
  )
}

// Enable Deequ mode for advanced statistical validation
// System property: -Dff.quality.mode=deequ
class TransactionPipeline[F[_]: DataAlgebra](implicit spark: SparkSession) {
  def validateTransactions(data: Dataset[Transaction]): F[QualityResult[Dataset[Transaction]]] = {
    // FlowForge automatically uses Deequ if available and enabled
    DeequAdapter.runChecks(spark, data, TransactionContract.qualityConstraints)
  }
}
```

## Data Profiling Migration

### Great Expectations Profiling
```python
# GE profiling workflow
import great_expectations as ge

# 1. Create datasource
context = ge.get_context()
datasource = context.sources.add_pandas(name="my_datasource")

# 2. Profile data
profiler = UserConfigurableProfiler(
    profile_dataset=df,
    excluded_expectations=["expect_table_columns_to_match_ordered_list"]
)
suite = profiler.build_suite()
```

### FlowForge Profiling with Deequ
```scala
import com.flowforge.quality.deequ.DeequAdapter
import com.amazon.deequ.profiles.ColumnProfilerRunner

// 1. Automatic profiling during pipeline execution
class DataProfilingPipeline[F[_]: DataAlgebra](implicit spark: SparkSession) {
  
  def profileAndValidate[A](
    data: Dataset[A], 
    contract: DataContract[A]
  ): F[QualityResult[Dataset[A]]] = {
    
    // Profile data (when Deequ available)
    val profile = if (DeequAdapter.deequAvailable) {
      ColumnProfilerRunner()
        .onData(data.asInstanceOf[ProductionSparkDataset[A]].sparkDataFrame)
        .run()
    } else {
      // Fallback: basic Spark statistics
      data.asInstanceOf[ProductionSparkDataset[A]].sparkDataFrame.describe()
    }
    
    // Run quality checks with profiling context
    DeequAdapter.runChecks(spark, data, contract.qualityConstraints)
  }
}

// 2. Generate constraint suggestions from profiling
object ConstraintSuggestionEngine {
  def suggestConstraints[A](data: Dataset[A]): List[QualityConstraint] = {
    // Use Deequ's ConstraintSuggestionRunner when available
    // Fallback to basic heuristics with native Spark
    List(
      QualityConstraint.NotNull(Field("id")), // if null rate < 0.01
      QualityConstraint.Unique(Field("id")),  // if uniqueness > 0.99
      // ... more suggestions based on profiling
    )
  }
}
```

## Reporting Migration

### Great Expectations Data Docs
```python
# GE generates HTML reports
context.build_data_docs()
# Creates: great_expectations/uncommitted/data_docs/local_site/index.html
```

### FlowForge Quality Reports + Lineage
```scala
import com.flowforge.core.lineage.OpenLineageEmitter

class QualityReportingPipeline[F[_]: DataAlgebra] {
  
  def runWithReporting[A](
    data: Dataset[A],
    contract: DataContract[A]
  ): F[Dataset[A]] = {
    for {
      // 1. Run quality checks
      qualityResult <- data.runQualityChecks(contract.qualityConstraints)
      
      // 2. Emit lineage with quality metadata
      _ <- OpenLineageEmitter.emitDataQuality(
        dataset = "user_data",
        qualityScore = qualityResult.score,
        violations = qualityResult.violations,
        constraints = contract.qualityConstraints
      )
      
      // 3. Log quality results (structured logging)
      _ <- logQualityResults(qualityResult)
      
    } yield qualityResult.dataset
  }
  
  private def logQualityResults[A](result: QualityResult[Dataset[A]]): F[Unit] = {
    val report = QualityReport(
      timestamp = java.time.Instant.now(),
      passed = result.passed,
      score = result.score,
      violations = result.violations.map(v => 
        ViolationSummary(v.rule, v.severity, v.recordsAffected)
      )
    )
    
    // Emit to your observability stack (Datadog, Prometheus, etc.)
    DataAlgebra[F].pure(println(s"Quality Report: $report"))
  }
}

case class QualityReport(
  timestamp: java.time.Instant,
  passed: Boolean,
  score: Double,
  violations: List[ViolationSummary]
)

case class ViolationSummary(
  rule: String,
  severity: String,
  recordsAffected: Long
)
```

## CI/CD Integration

### Great Expectations Checkpoints
```yaml
# .github/workflows/data-validation.yml (GE)
name: Data Validation
on: [push]
jobs:
  validate:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      - name: Run Great Expectations
        run: |
          great_expectations checkpoint run user_data_checkpoint
```

### FlowForge Contract Validation
```yaml
# .github/workflows/flowforge-validation.yml
name: FlowForge Validation
on: [push]
jobs:
  validate:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      - name: Setup Scala
        uses: olafurpg/setup-scala@v10
      - name: Compile-time Contract Validation
        run: |
          # Contracts are validated at compile time
          sbt compile
      - name: Runtime Quality Tests
        run: |
          # Run quality constraint tests
          sbt "testOnly *QualitySpec"
      - name: Integration Tests
        run: |
          # Full pipeline tests with quality validation
          sbt "testOnly *PipelineIntegrationSpec"
```

**Key Advantage**: FlowForge catches schema issues at compile time, preventing deployment of broken pipelines.

## Performance Considerations

### Great Expectations Performance
- **Runtime overhead**: All validation happens during data processing
- **Memory usage**: Stores expectation results in memory
- **Scalability**: Can become bottleneck for large datasets

### FlowForge Performance Advantages

1. **Compile-time validation**: Zero runtime cost for schema validation
2. **Dual-mode quality**: Choose between fast native Spark or comprehensive Deequ
3. **Lazy evaluation**: Quality checks only run when explicitly requested
4. **Engine optimization**: Leverages Spark's Catalyst optimizer

```scala
// Performance comparison example
class PerformanceOptimizedPipeline[F[_]: DataAlgebra] {
  
  def processLargeDataset(data: Dataset[LargeRecord]): F[Dataset[ProcessedRecord]] = {
    for {
      // 1. Contract validation: ZERO runtime cost (compile-time only)
      validated <- data.validateContract(LargeRecordContract)
      
      // 2. Selective quality checks: Only critical constraints
      criticalChecks = List(
        QualityConstraint.NotNull(Field("id")),
        QualityConstraint.Unique(Field("id"))
      )
      qualityChecked <- validated.runQualityChecks(criticalChecks)
      
      // 3. Business logic with type safety
      processed <- qualityChecked.map(transformRecord)
      
    } yield processed
  }
  
  // Optional: Full quality suite for smaller datasets or sampling
  def runFullQualitySuite(data: Dataset[LargeRecord]): F[QualityResult[Dataset[LargeRecord]]] = {
    // Sample data for comprehensive quality checks
    val sample = data.sample(0.1) // 10% sample
    sample.runQualityChecks(LargeRecordContract.qualityConstraints)
  }
}
```

## Migration Checklist

### Phase 1: Assessment
- [ ] Inventory existing Great Expectations suites
- [ ] Map expectations to FlowForge constraints (use tables above)
- [ ] Identify custom expectations requiring Compliance constraints
- [ ] Plan schema contract definitions

### Phase 2: Implementation
- [ ] Define FlowForge contracts for each data model
- [ ] Convert expectation suites to quality constraints
- [ ] Set up Deequ integration (optional)
- [ ] Implement quality reporting pipeline

### Phase 3: Testing
- [ ] Run parallel validation (GE + FlowForge) during transition
- [ ] Compare quality results and performance
- [ ] Update CI/CD pipelines
- [ ] Train team on FlowForge concepts

### Phase 4: Migration
- [ ] Switch to FlowForge-only validation
- [ ] Remove Great Expectations dependencies
- [ ] Update documentation and runbooks
- [ ] Monitor quality metrics post-migration

## Common Migration Patterns

### Pattern 1: Gradual Migration
```scala
// Run both GE and FlowForge during transition
class HybridValidationPipeline[F[_]: DataAlgebra] {
  def validateWithBoth[A](
    data: Dataset[A],
    contract: DataContract[A],
    geCheckpoint: String
  ): F[Dataset[A]] = {
    for {
      // FlowForge validation
      ffResult <- data.runQualityChecks(contract.qualityConstraints)
      
      // Great Expectations validation (external call)
      geResult <- runGreatExpectations(data, geCheckpoint)
      
      // Compare results and log differences
      _ <- compareValidationResults(ffResult, geResult)
      
    } yield ffResult.dataset
  }
}
```

### Pattern 2: Custom Expectation Migration
```scala
// For complex GE expectations, use Compliance constraints
object CustomExpectationMigration {
  
  // GE: expect_column_values_to_be_dateutil_parseable
  val dateParseableConstraint = QualityConstraint.Compliance(
    "date_parseable",
    "TRY_CAST(date_column AS DATE) IS NOT NULL"
  )
  
  // GE: expect_multicolumn_sum_to_equal
  val multiColumnSumConstraint = QualityConstraint.Compliance(
    "sum_validation",
    "ABS((col1 + col2 + col3) - total_col) < 0.01"
  )
  
  // GE: expect_column_pair_values_A_to_be_greater_than_B
  val columnComparisonConstraint = QualityConstraint.Compliance(
    "start_before_end",
    "start_date <= end_date"
  )
}
```

## Getting Help

- **Documentation**: [FlowForge Quality Guide](/docs/quality/README.md)
- **Examples**: Check the `examples/` module for working code
- **Community**: Join our Slack for migration support
- **Professional Services**: Contact us for enterprise migration assistance

---

**Next Steps**: Once you've migrated from Great Expectations, explore FlowForge's advanced features like multi-engine support and streaming contract validation in our [Advanced Tutorials](/docs/tutorials/advanced-patterns.mdoc).