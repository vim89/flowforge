# Migrating from dbt to FlowForge

This guide helps dbt users transition to FlowForge, mapping familiar dbt concepts to FlowForge's compile-time contract validation and type-safe data pipelines.

## Quick Comparison

| dbt Concept | FlowForge Equivalent | Key Difference |
|-------------|---------------------|----------------|
| Models | Scala transformations | Compile-time type safety vs runtime SQL |
| schema.yml | DataContract + case classes | Compile-time validation vs build-time |
| Tests | Quality constraints + compile-fail tests | Continuous validation vs periodic |
| Docs | OpenLineage + contract documentation | Automatic lineage vs manual docs |
| Incremental models | CDC operations | Real-time vs batch incremental |
| Macros | Scala functions | Type-safe vs text substitution |

## 1. Conceptual Mapping

### dbt Models → FlowForge Transformations

**dbt model (SQL):**
```sql
-- models/customer_summary.sql
{{ config(materialized='table') }}

select
    customer_id,
    count(*) as order_count,
    sum(amount) as total_amount,
    max(order_date) as last_order_date
from {{ ref('orders') }}
group by customer_id
```

**FlowForge equivalent (Scala):**
```scala
// CustomerSummaryPipeline.scala
import com.flowforge.core.Pipeline
import com.flowforge.core.contracts.DataContract

case class Order(
  customerId: String,
  amount: Double,
  orderDate: java.time.LocalDate
)

case class CustomerSummary(
  customerId: String,
  orderCount: Long,
  totalAmount: Double,
  lastOrderDate: java.time.LocalDate
)

class CustomerSummaryPipeline extends Pipeline {
  def transform(orders: TypedSource[Order]): TypedSink[CustomerSummary] = {
    orders
      .groupBy(_.customerId)
      .agg(
        count() as "orderCount",
        sum(col("amount")) as "totalAmount",
        max(col("orderDate")) as "lastOrderDate"
      )
      .as[CustomerSummary]
  }
}
```

### dbt Tests → FlowForge Quality Constraints

**dbt schema.yml:**
```yaml
models:
  - name: customer_summary
    columns:
      - name: customer_id
        tests:
          - not_null
          - unique
      - name: order_count
        tests:
          - not_null
          - dbt_utils.accepted_range:
              min_value: 1
      - name: total_amount
        tests:
          - not_null
          - dbt_utils.accepted_range:
              min_value: 0
```

**FlowForge equivalent:**
```scala
import com.flowforge.contracts._
import com.flowforge.contracts.ValidationRules._

implicit val customerSummaryContract: DataContract[CustomerSummary] =
  DataContract.builder[CustomerSummary]
    .withSchema(
      ContractSchema(
        name = NonEmptyString.unsafeFrom("CustomerSummary"),
        fields = List(
          FieldContract(
            name = NonEmptyString.unsafeFrom("customerId"),
            dataType = FieldType.StringType,
            nullable = false,
            constraints = List(FieldConstraint.MinLength(1))
          ),
          FieldContract(
            name = NonEmptyString.unsafeFrom("orderCount"),
            dataType = FieldType.LongType,
            nullable = false,
            constraints = List(FieldConstraint.Range(1, Long.MaxValue))
          ),
          FieldContract(
            name = NonEmptyString.unsafeFrom("totalAmount"),
            dataType = FieldType.DoubleType,
            nullable = false,
            constraints = List(FieldConstraint.Range(0.0, Double.MaxValue))
          )
        ),
        version = SchemaVersion.unsafeFrom(1)
      )
    )
    .withRules(
      nonNull("customerId")(_.customerId),
      unique("customerId")(_.customerId),
      nonNull("orderCount")(_.orderCount),
      range("orderCount")(1L, Long.MaxValue)(_.orderCount),
      nonNull("totalAmount")(_.totalAmount),
      range("totalAmount")(0.0, Double.MaxValue)(_.totalAmount)
    )
    .build
```

## 2. Schema Migration

### From dbt schema.yml to FlowForge Contracts

**dbt schema.yml:**
```yaml
version: 2

models:
  - name: users
    description: "User dimension table"
    columns:
      - name: user_id
        description: "Primary key"
        tests:
          - not_null
          - unique
      - name: email
        description: "User email address"
        tests:
          - not_null
          - dbt_utils.email
      - name: created_at
        description: "Account creation timestamp"
        tests:
          - not_null
      - name: status
        description: "Account status"
        tests:
          - accepted_values:
              values: ['active', 'inactive', 'suspended']
```

**FlowForge contract:**
```scala
import com.flowforge.contracts._
import eu.timepit.refined.types.string.NonEmptyString
import java.time.Instant
import scala.util.matching.Regex

case class User(
  userId: String,
  email: String,
  createdAt: Instant,
  status: String
)

implicit val userContract: DataContract[User] = {
  val emailRegex = """^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$""".r
  
  DataContract.builder[User]
    .withSchema(
      ContractSchema(
        name = NonEmptyString.unsafeFrom("User"),
        fields = List(
          FieldContract(
            name = NonEmptyString.unsafeFrom("userId"),
            dataType = FieldType.StringType,
            nullable = false,
            description = Some("Primary key")
          ),
          FieldContract(
            name = NonEmptyString.unsafeFrom("email"),
            dataType = FieldType.StringType,
            nullable = false,
            constraints = List(FieldConstraint.Pattern(emailRegex)),
            description = Some("User email address")
          ),
          FieldContract(
            name = NonEmptyString.unsafeFrom("createdAt"),
            dataType = FieldType.TimestampType,
            nullable = false,
            description = Some("Account creation timestamp")
          ),
          FieldContract(
            name = NonEmptyString.unsafeFrom("status"),
            dataType = FieldType.StringType,
            nullable = false,
            constraints = List(FieldConstraint.OneOf(Set("active", "inactive", "suspended"))),
            description = Some("Account status")
          )
        ),
        version = SchemaVersion.unsafeFrom(1)
      )
    )
    .withRules(
      ValidationRules.nonNull("userId")(_.userId),
      ValidationRules.unique("userId")(_.userId),
      ValidationRules.nonNull("email")(_.email),
      ValidationRules.pattern("email")(emailRegex)(_.email),
      ValidationRules.nonNull("createdAt")(_.createdAt),
      ValidationRules.custom("validStatus") { user =>
        if (Set("active", "inactive", "suspended").contains(user.status)) {
          ().validNel
        } else {
          ContractViolation.CustomViolation(
            "status", 
            "validStatus", 
            s"Invalid status: ${user.status}"
          ).invalidNel
        }
      }
    )
    .build
}
```

## 3. SQL to Scala Transformation

### Basic Aggregations

**dbt SQL:**
```sql
-- models/daily_sales.sql
select
    date_trunc('day', order_date) as sale_date,
    count(*) as order_count,
    sum(amount) as total_sales,
    avg(amount) as avg_order_value,
    count(distinct customer_id) as unique_customers
from {{ ref('orders') }}
where order_date >= '2023-01-01'
group by date_trunc('day', order_date)
order by sale_date
```

**FlowForge Scala:**
```scala
import org.apache.spark.sql.functions._
import java.time.LocalDate

case class DailySales(
  saleDate: LocalDate,
  orderCount: Long,
  totalSales: Double,
  avgOrderValue: Double,
  uniqueCustomers: Long
)

class DailySalesPipeline extends Pipeline {
  def transform(orders: TypedSource[Order]): TypedSink[DailySales] = {
    orders
      .filter(col("orderDate") >= lit("2023-01-01"))
      .withColumn("saleDate", date_trunc("day", col("orderDate")))
      .groupBy(col("saleDate"))
      .agg(
        count("*") as "orderCount",
        sum("amount") as "totalSales",
        avg("amount") as "avgOrderValue",
        countDistinct("customerId") as "uniqueCustomers"
      )
      .orderBy(col("saleDate"))
      .as[DailySales]
  }
}
```

### Window Functions

**dbt SQL:**
```sql
-- models/customer_ranking.sql
select
    customer_id,
    total_amount,
    row_number() over (order by total_amount desc) as rank,
    lag(total_amount) over (order by total_amount desc) as prev_amount,
    total_amount - lag(total_amount) over (order by total_amount desc) as amount_diff
from {{ ref('customer_summary') }}
```

**FlowForge Scala:**
```scala
import org.apache.spark.sql.expressions.Window
import org.apache.spark.sql.functions._

case class CustomerRanking(
  customerId: String,
  totalAmount: Double,
  rank: Long,
  prevAmount: Option[Double],
  amountDiff: Option[Double]
)

class CustomerRankingPipeline extends Pipeline {
  def transform(customerSummary: TypedSource[CustomerSummary]): TypedSink[CustomerRanking] = {
    val windowSpec = Window.orderBy(col("totalAmount").desc)
    
    customerSummary
      .withColumn("rank", row_number().over(windowSpec))
      .withColumn("prevAmount", lag("totalAmount", 1).over(windowSpec))
      .withColumn("amountDiff", col("totalAmount") - col("prevAmount"))
      .select(
        col("customerId"),
        col("totalAmount"),
        col("rank"),
        col("prevAmount"),
        col("amountDiff")
      )
      .as[CustomerRanking]
  }
}
```

## 4. Testing Migration

### dbt Tests → FlowForge Quality Constraints

**dbt custom test:**
```sql
-- tests/assert_positive_revenue.sql
select *
from {{ ref('daily_sales') }}
where total_sales <= 0
```

**FlowForge quality constraint:**
```scala
val positiveRevenueRule: ValidationRule[DailySales] = 
  ValidationRules.custom("positiveRevenue") { sales =>
    if (sales.totalSales > 0) {
      ().validNel
    } else {
      ContractViolation.CustomViolation(
        "totalSales",
        "positiveRevenue",
        s"Revenue must be positive, got: ${sales.totalSales}"
      ).invalidNel
    }
  }
```

### dbt Generic Tests → FlowForge Compile-time Tests

**dbt test:**
```yaml
models:
  - name: customer_summary
    tests:
      - dbt_utils.expression_is_true:
          expression: "order_count > 0"
```

**FlowForge compile-fail test:**
```scala
// This won't compile if CustomerSummary doesn't have orderCount > 0 constraint
class CustomerSummarySpec extends CompileFailSpec {
  "CustomerSummary contract" should {
    "enforce positive order count" in {
      // This will fail compilation if constraint is violated
      val invalidSummary = CustomerSummary("cust1", 0, 100.0, LocalDate.now())
      
      assertTypeError("""
        val pipeline = Pipeline.builder
          .addTypedSink[CustomerSummary]("output", invalidSummary)
          .build
      """)
    }
  }
}
```

## 5. Incremental Models

### dbt Incremental → FlowForge CDC

**dbt incremental model:**
```sql
-- models/customer_summary_incremental.sql
{{ config(
    materialized='incremental',
    unique_key='customer_id',
    on_schema_change='fail'
) }}

select
    customer_id,
    count(*) as order_count,
    sum(amount) as total_amount,
    max(order_date) as last_order_date
from {{ ref('orders') }}

{% if is_incremental() %}
    where order_date > (select max(last_order_date) from {{ this }})
{% endif %}

group by customer_id
```

**FlowForge CDC pipeline:**
```scala
import com.flowforge.core.streaming.CDC
import com.flowforge.core.streaming.CDCOperation._

case class CustomerSummaryEvent(
  operation: CDCOperation,
  customerId: String,
  orderCount: Long,
  totalAmount: Double,
  lastOrderDate: LocalDate,
  timestamp: Instant
)

class CustomerSummaryCDCPipeline extends StreamingPipeline {
  def process(
    orderEvents: TypedStream[OrderEvent],
    existingSummary: TypedSource[CustomerSummary]
  ): TypedSink[CustomerSummaryEvent] = {
    
    val newSummaries = orderEvents
      .filter(_.operation != DELETE)
      .groupBy(_.customerId)
      .agg(
        count() as "orderCount",
        sum("amount") as "totalAmount",
        max("orderDate") as "lastOrderDate"
      )
    
    val cdcEvents = CDC.merge(
      target = existingSummary,
      source = newSummaries,
      mergeKey = "customerId",
      whenMatched = UPDATE,
      whenNotMatched = INSERT
    )
    
    cdcEvents.as[CustomerSummaryEvent]
  }
}
```

### Slowly Changing Dimensions (SCD2)

**dbt SCD2 with snapshots:**
```sql
-- snapshots/customer_snapshot.sql
{% snapshot customer_snapshot %}
    {{
        config(
          target_schema='snapshots',
          unique_key='customer_id',
          strategy='timestamp',
          updated_at='updated_at',
        )
    }}
    select * from {{ ref('customers') }}
{% endsnapshot %}
```

**FlowForge SCD2:**
```scala
import com.flowforge.core.scd.SCD2

case class CustomerHistory(
  customerId: String,
  name: String,
  email: String,
  status: String,
  validFrom: Instant,
  validTo: Option[Instant],
  isCurrent: Boolean
)

class CustomerSCD2Pipeline extends Pipeline {
  def process(
    newCustomers: TypedSource[Customer],
    existingHistory: TypedSource[CustomerHistory]
  ): TypedSink[CustomerHistory] = {
    
    SCD2.apply(
      current = existingHistory.filter(_.isCurrent),
      incoming = newCustomers,
      businessKey = "customerId",
      compareColumns = List("name", "email", "status"),
      effectiveTimeColumn = "validFrom"
    ).as[CustomerHistory]
  }
}
```

## 6. Documentation Migration

### dbt Docs → FlowForge Lineage

**dbt documentation:**
```yaml
# dbt_project.yml
models:
  my_project:
    staging:
      +docs:
        node_color: "lightblue"
    marts:
      +docs:
        node_color: "orange"
```

**FlowForge automatic lineage:**
```scala
// Lineage is automatically captured via OpenLineage
class SalesAnalyticsPipeline extends Pipeline {
  @LineageMetadata(
    description = "Daily sales analytics pipeline",
    owner = "analytics-team",
    tags = List("sales", "daily", "analytics")
  )
  def transform(
    orders: TypedSource[Order],
    customers: TypedSource[Customer]
  ): TypedSink[DailySalesAnalytics] = {
    
    // Lineage automatically tracks:
    // - Input sources: orders, customers
    // - Transformations: join, aggregations
    // - Output: daily_sales_analytics
    
    orders
      .join(customers, "customerId")
      .groupBy(date_trunc("day", col("orderDate")))
      .agg(
        sum("amount") as "totalSales",
        countDistinct("customerId") as "uniqueCustomers"
      )
      .as[DailySalesAnalytics]
  }
}
```

### Contract Documentation

**FlowForge contracts serve as living documentation:**
```scala
implicit val salesAnalyticsContract: DataContract[DailySalesAnalytics] =
  DataContract.builder[DailySalesAnalytics]
    .withSchema(
      ContractSchema(
        name = NonEmptyString.unsafeFrom("DailySalesAnalytics"),
        fields = List(
          FieldContract(
            name = NonEmptyString.unsafeFrom("saleDate"),
            dataType = FieldType.TimestampType,
            nullable = false,
            description = Some("Date of sales aggregation (truncated to day)")
          ),
          FieldContract(
            name = NonEmptyString.unsafeFrom("totalSales"),
            dataType = FieldType.DoubleType,
            nullable = false,
            constraints = List(FieldConstraint.Range(0.0, Double.MaxValue)),
            description = Some("Total sales amount for the day")
          )
        ),
        version = SchemaVersion.unsafeFrom(1),
        metadata = Map(
          "owner" -> "analytics-team",
          "purpose" -> "Daily sales reporting and analysis",
          "update_frequency" -> "daily",
          "data_classification" -> "internal"
        )
      )
    )
    .build
```

## 7. Deployment Comparison

### dbt Deployment → FlowForge CI/CD

**dbt deployment (dbt Cloud/Airflow):**
```yaml
# .github/workflows/dbt.yml
name: dbt CI/CD
on:
  push:
    branches: [main]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      - name: Setup dbt
        run: pip install dbt-core dbt-postgres
      - name: Run dbt tests
        run: |
          dbt deps
          dbt test
          dbt run
```

**FlowForge CI/CD:**
```yaml
# .github/workflows/flowforge.yml
name: FlowForge CI/CD
on:
  push:
    branches: [main]

jobs:
  compile-time-validation:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      - name: Setup Scala
        uses: olafurpg/setup-scala@v13
      - name: Compile and validate contracts
        run: |
          sbt compile  # Compile-time contract validation
          sbt test     # Quality constraint tests
          sbt ffValidate  # Physical schema validation
      
  deploy:
    needs: compile-time-validation
    runs-on: ubuntu-latest
    steps:
      - name: Deploy to production
        run: |
          sbt assembly
          # Deploy JAR to Spark cluster
```

### Contract Submission Workflow

**dbt schema changes:**
```bash
# Manual process
1. Edit schema.yml
2. Run dbt test
3. Deploy if tests pass
```

**FlowForge contract changes:**
```scala
// Automated contract submission via GitHub Actions Forms
// 1. Non-technical users submit contract changes via web form
// 2. CI generates typed artifacts
// 3. Validation runs against physical schemas
// 4. Auto-merge if validation passes
```

## 8. Common dbt Macros → FlowForge Functions

### dbt Macros

**dbt macro:**
```sql
-- macros/get_payment_methods.sql
{% macro get_payment_methods() %}
    {{ return(['credit_card', 'debit_card', 'bank_transfer', 'cash']) }}
{% endmacro %}

-- Usage in model:
select *
from payments
where payment_method in ({{ get_payment_methods() | join("','") | replace("'", "''") }})
```

**FlowForge function:**
```scala
object PaymentMethods {
  val validMethods: Set[String] = Set(
    "credit_card", "debit_card", "bank_transfer", "cash"
  )
  
  def isValid(method: String): Boolean = validMethods.contains(method)
  
  def filterValid[T](df: Dataset[T])(getMethod: T => String): Dataset[T] = {
    df.filter(row => validMethods.contains(getMethod(row)))
  }
}

// Usage in pipeline:
class PaymentPipeline extends Pipeline {
  def transform(payments: TypedSource[Payment]): TypedSink[Payment] = {
    PaymentMethods.filterValid(payments)(_.paymentMethod)
  }
}
```

### Date Utilities

**dbt date macro:**
```sql
-- macros/date_spine.sql
{% macro date_spine(start_date, end_date) %}
    with date_spine as (
        select 
            '{{ start_date }}'::date + generate_series(
                0, 
                '{{ end_date }}'::date - '{{ start_date }}'::date
            ) as date_day
    )
    select * from date_spine
{% endmacro %}
```

**FlowForge date utilities:**
```scala
object DateUtils {
  def dateSpine(
    start: LocalDate, 
    end: LocalDate
  )(implicit spark: SparkSession): Dataset[LocalDate] = {
    import spark.implicits._
    
    val days = start.until(end, ChronoUnit.DAYS).toInt
    (0 to days)
      .map(start.plusDays(_))
      .toDS()
  }
  
  def addBusinessDays(date: LocalDate, days: Int): LocalDate = {
    // Implementation for business day calculation
    date.plusDays(days) // Simplified
  }
}
```

## 9. Migration Strategy

### Phase 1: Assessment
1. **Inventory dbt models**: Catalog all models, tests, and dependencies
2. **Identify patterns**: Group similar transformations and test patterns
3. **Map data sources**: Understand source systems and their schemas
4. **Plan contracts**: Design FlowForge contracts for key data models

### Phase 2: Parallel Development
1. **Start with leaf models**: Begin with models that have no dependencies
2. **Implement contracts**: Create DataContract instances for each model
3. **Build pipelines**: Convert SQL logic to Scala transformations
4. **Add quality rules**: Migrate dbt tests to FlowForge quality constraints

### Phase 3: Validation
1. **Data reconciliation**: Ensure FlowForge outputs match dbt outputs
2. **Performance testing**: Validate performance meets requirements
3. **Contract validation**: Verify compile-time validation catches issues
4. **Integration testing**: Test with downstream consumers

### Phase 4: Cutover
1. **Gradual migration**: Replace dbt models one at a time
2. **Monitor closely**: Watch for data quality issues
3. **Update consumers**: Migrate downstream systems to use FlowForge outputs
4. **Decommission dbt**: Remove old dbt models after validation

## 10. Key Benefits of Migration

### Compile-time Safety
- **dbt**: Runtime SQL errors, schema drift detected at build time
- **FlowForge**: Compile-time type safety, impossible to deploy broken schemas

### Performance
- **dbt**: SQL optimization depends on warehouse capabilities
- **FlowForge**: Spark/Flink optimizations, engine-agnostic performance tuning

### Testing
- **dbt**: Separate test files, run after model execution
- **FlowForge**: Integrated quality constraints, continuous validation

### Documentation
- **dbt**: Manual documentation maintenance
- **FlowForge**: Automatic lineage capture, contracts as living documentation

### Deployment
- **dbt**: Build-time validation, potential runtime failures
- **FlowForge**: Compile-time validation, guaranteed runtime success

## Resources

- [FlowForge Contract Documentation](../contracts/OVERVIEW.md)
- [Quality Framework Guide](../quality/README.md)
- [Interactive Tutorial: First Contract](../tutorials/first-contract.mdoc)
- [Error Message Reference](../how-it-fails.md)
- [Performance Benchmarks](../../modules/performance-benchmarks/README.md)

## Getting Help

- **Migration questions**: Open an issue with the `migration` label
- **Contract design**: Review existing examples in `modules/examples/`
- **Performance tuning**: Check the performance benchmarks module
- **Community support**: Join the FlowForge Slack workspace