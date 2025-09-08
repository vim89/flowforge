# FlowForge Examples-Spark Module

## Overview

The `examples-spark` module provides polished, production-ready examples demonstrating FlowForge v1.0 capabilities with Apache Spark.

## Features Demonstrated

### ✨ UsersPipeline.scala - Complete ETL Example

A comprehensive end-to-end data pipeline showcasing:

- **Type-safe dataset transformations** with `ProductionSparkDataset`
- **Data quality validation** using dual-mode support (native Spark / optional Deequ)
- **Resource-safe Spark operations** with cats-effect `Resource`
- **Error handling** with `Either` monads (CLAUDE.md compliance)
- **Delta Lake integration** for ACID transactions
- **Lineage tracking** (when OpenLineage is configured)

## Running the Examples

### Prerequisites

```bash
# Local development
spark-shell --version  # Spark 3.5.0+ required
```

### UsersPipeline Example

```bash
# Run the complete users pipeline
sbt "examples-spark/runMain com.flowforge.examples.spark.UsersPipeline"
```

This will:
1. 📊 Generate sample user data (10 records with quality issues)
2. 🧹 Clean invalid data (emails, age ranges)
3. ✅ Validate data quality (5 constraint checks)
4. 🚀 Enrich with business logic (age groups, regions)
5. 📈 Display results and statistics
6. 💾 Save to `/tmp/flowforge/users-pipeline-output`

### Example Output

```
🚀 Starting FlowForge UsersPipeline example
📊 Generated 10 raw user records

🔍 Quality Check Results:
   Passed: true
   Score: 100%

📈 Pipeline Results:
===================
+----+------------------+---+----------+---------------+--------+--------+--------------+
|  id|             email|age|   country|signupTimestamp|isActive|ageGroup|        region|
+----+------------------+---+----------+---------------+--------+--------+--------------+
|u001|alice@example.com| 28|       USA|     1673740800|    true|  middle|North America|
|u002|    bob@test.com  | 35|    Canada|     1676419200|    true|  middle|North America|
+----+------------------+---+----------+---------------+--------+--------+--------------+

📊 Summary Statistics:
+--------------+--------+-----+
|        region|ageGroup|count|
+--------------+--------+-----+
|Europe        |middle  |    2|
|North America |middle  |    2|
|North America |young   |    1|
+--------------+--------+-----+

💾 Saving results to: /tmp/flowforge/users-pipeline-output
✅ Results saved successfully
✅ UsersPipeline completed successfully
```

## Code Structure

### Domain Models

```scala
case class RawUser(id: String, email: String, age: Option[Int], ...)
case class CleanedUser(id: String, email: String, age: Int, ...)
case class EnrichedUser(id: String, ..., ageGroup: String, region: String)
```

### Pipeline Stages

1. **Data Generation**: Creates sample dataset with realistic data
2. **Cleaning**: Filters invalid emails/ages using pure Spark functions
3. **Quality Validation**: Runs 6 quality constraints (NotNull, Unique, Range, Pattern, Compliance)
4. **Enrichment**: Adds age groups and geographic regions
5. **Output**: Displays results and saves to filesystem

### Quality Constraints Demonstrated

```scala
List(
  FFConstraint.NotNull(FieldName("id")),
  FFConstraint.NotNull(FieldName("email")),
  FFConstraint.Unique(FieldName("id")),
  FFConstraint.Range(FieldName("age"), Some(13.0), Some(100.0)),
  FFConstraint.Pattern(FieldName("email"), "^[A-Za-z0-9+_.-]+@(.+)$"),
  FFConstraint.Compliance("active-user-check", "isActive = true OR signupTimestamp > unix_timestamp('2023-01-01', 'yyyy-MM-dd')")
)
```

## Utility Functions

### UsersPipelineUtils

- **Quality Presets**: `userDataQualityConstraints()` - Common user data validations
- **Transformations**: Email normalization, age calculation, user segmentation
- **Spark Configurations**: Local development settings with Delta Lake support

### Common Transformations

```scala
// Email normalization
Transformations.normalizeEmail("email_column")

// Age classification
Transformations.classifyUserSegment("age", "is_active")

// Date calculations
Transformations.calculateAge("birth_date")
```

## Integration Points

### Data Quality

- **Native Mode** (default): Uses Spark DataFrame operations, zero dependencies
- **Deequ Mode** (optional): Enable with `-Dff.quality.mode=deequ` system property
- **Graceful Fallback**: Automatically falls back if Deequ unavailable

### Lineage Tracking

When OpenLineage is configured:
```bash
export OPENLINEAGE_URL="http://localhost:5000/api/v1/lineage"
```
Pipeline automatically emits START/COMPLETE/FAIL events for lineage tracking.

### Delta Lake

Pipeline demonstrates:
- Schema evolution compatibility
- ACID transaction guarantees  
- Time travel queries support
- Optimized file layouts

## Best Practices Demonstrated

### ✅ CLAUDE.md Compliance

- **Pure Functions**: All transformations return new DataFrames
- **Either Monads**: Error handling without try-catch-finally
- **Resource Safety**: Spark sessions managed with cats-effect Resource
- **Immutable Data**: Domain models are immutable case classes

### ✅ Production Readiness

- **Error Handling**: Graceful degradation and meaningful error messages
- **Performance**: Adaptive query execution and partition coalescing
- **Monitoring**: Quality scores, execution metrics, and lineage tracking
- **Configurability**: Spark settings optimized for different environments

### ✅ Type Safety

- **Compile-time Guarantees**: Schema compatibility checking
- **Phantom Types**: Type-level validation where applicable
- **Dataset Safety**: Memory-safe operations avoiding driver OOM

## Development

### Adding New Examples

1. Create new object in `com.flowforge.examples.spark` package
2. Follow UsersPipeline pattern for consistency
3. Include comprehensive documentation and error handling
4. Add to examples-spark module README

### Testing

```bash
# Compile examples
sbt examples-spark/compile

# Run tests (when added)
sbt examples-spark/test

# Format code
sbt examples-spark/fmt
```

### Integration with Main Pipeline

Examples can be integrated into larger FlowForge applications:

```scala
import com.flowforge.examples.spark.UsersPipelineUtils

// Use predefined quality constraints
val constraints = UsersPipelineUtils.userDataQualityConstraints()

// Apply common transformations
val cleanedEmail = Transformations.normalizeEmail("email")
```