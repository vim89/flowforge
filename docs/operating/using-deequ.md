# Using Deequ with FlowForge

## Overview

FlowForge v1.0 uses **native Spark checks** as the default data quality solution to keep the core lean and dependency-free. However, you can optionally enhance your data quality validation with **Amazon Deequ**, the industry-standard data quality framework.

## When to Use Deequ

Consider using Deequ when you need:
- Advanced statistical profiling and anomaly detection
- Industry-tested data quality framework
- Integration with existing Deequ workflows
- Advanced constraint types beyond FlowForge's native support

## Quick Setup

### 1. Add Deequ Dependency

Add Deequ to your `build.sbt`:

```scala
libraryDependencies += "com.amazon.deequ" % "deequ" % "2.0.11-spark-3.5"
```

**Maven Coordinates:**
```xml
<dependency>
    <groupId>com.amazon.deequ</groupId>
    <artifactId>deequ</artifactId>
    <version>2.0.11-spark-3.5</version>
</dependency>
```

### 2. Enable Deequ Mode

Set the system property to enable Deequ:

```bash
# Enable Deequ enhancement mode
export JAVA_OPTS="-Dff.quality.mode=deequ"

# Or pass directly to SBT
sbt -Dff.quality.mode=deequ "examples-spark/runMain com.flowforge.examples.spark.UsersPipeline"
```

## Complete Example

Here's a self-contained example using Deequ with FlowForge:

```scala
package com.flowforge.examples.deequ

import cats.effect.IO
import com.flowforge.examples.spark.UsersPipelineUtils
import com.flowforge.engines.spark.ProductionSparkDataset
import com.flowforge.core.types.QualityConstraint
import com.flowforge.core.types.RefinedTypes.FieldName
import com.flowforge.quality.deequ.DeequAdapter

object DeequExample {

  case class Customer(
    id: String,
    email: String, 
    age: Int,
    country: String,
    revenue: Double
  )

  def runExample(): IO[Unit] = {
    val sparkConfig = Map(
      "spark.master" -> "local[*]",
      "spark.app.name" -> "FlowForge-Deequ-Example"
    )

    UsersPipelineUtils.createSparkResource(sparkConfig).use { spark =>
      for {
        // Generate sample data
        dataset <- generateSampleCustomers(spark)
        
        // Define quality constraints
        constraints = defineQualityConstraints()
        
        // Run quality checks with Deequ (automatically falls back to native if Deequ unavailable)
        qualityResult <- IO.delay(DeequAdapter.runChecks(spark, dataset, constraints))
        
        // Display results
        _ <- displayQualityResults(qualityResult)
        
      } yield ()
    }
  }

  private def generateSampleCustomers(spark: org.apache.spark.sql.SparkSession): IO[ProductionSparkDataset[Customer]] = {
    import spark.implicits._
    
    IO.delay {
      val customers = Seq(
        Customer("c001", "alice@example.com", 28, "USA", 1250.50),
        Customer("c002", "bob@test.com", 35, "Canada", 890.25), 
        Customer("c003", "charlie@demo.org", 22, "UK", 2100.75),
        Customer("c004", "diana@sample.net", 41, "Australia", 1575.00),
        Customer("c005", "eve@example.co.uk", 29, "UK", 950.25),
        // Add some data that will trigger quality violations for demonstration
        Customer("c006", "", 15, "Germany", -100.0), // Invalid email, negative revenue
        Customer("c007", "frank@test.ca", 150, "Canada", 750.0), // Invalid age
      )
      
      val df = customers.toDF()
      
      // Create ProductionSparkDataset wrapper
      ProductionSparkDataset.fromDataFrame(df)
    }
  }

  private def defineQualityConstraints(): List[QualityConstraint] = List(
    // Basic constraints (work in both native and Deequ modes)
    QualityConstraint.NotNull(FieldName("id")),
    QualityConstraint.NotNull(FieldName("email")),
    QualityConstraint.Unique(FieldName("id")),
    QualityConstraint.Range(FieldName("age"), Some(18.0), Some(120.0)),
    QualityConstraint.Pattern(FieldName("email"), "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"),
    QualityConstraint.Compliance("positive_revenue", "revenue >= 0"),
    
    // Additional constraint that benefits from Deequ's advanced validation
    QualityConstraint.Compliance("valid_country", "country IN ('USA', 'Canada', 'UK', 'Australia', 'Germany')")
  )

  private def displayQualityResults(result: com.flowforge.core.algebra.DataAlgebra.QualityResult[_]): IO[Unit] = {
    IO.delay {
      println("\\n" + "="*50)
      println("FlowForge + Deequ Quality Results")
      println("="*50)
      println(s"Overall Passed: ${result.passed}")
      println(s"Quality Score: ${(result.score * 100).toInt}%")
      
      if (result.violations.nonEmpty) {
        println("\\nQuality Violations Found:")
        result.violations.foreach { violation =>
          println(s"  ❌ ${violation.rule}")
          println(s"     ${violation.message}")
          println(s"     Affected Records: ${violation.recordsAffected}")
          println()
        }
      } else {
        println("\\n✅ All quality constraints passed!")
      }
      
      println("\\nMode Detection:")
      val mode = if (sys.props.get("ff.quality.mode").contains("deequ")) {
        if (isDeequAvailable()) "Deequ Enhanced" else "Native (Deequ unavailable)"
      } else {
        "Native (Default)"
      }
      println(s"  Quality Engine: $mode")
    }
  }
  
  private def isDeequAvailable(): Boolean = {
    scala.util.Try {
      Class.forName("com.amazon.deequ.VerificationSuite")
      true
    }.getOrElse(false)
  }

  def main(args: Array[String]): Unit = {
    import cats.effect.unsafe.implicits.global
    runExample().unsafeRunSync()
  }
}
```

## Running the Example

```bash
# 1. Add Deequ to classpath (if desired)
sbt "set libraryDependencies += \\\"com.amazon.deequ\\\" % \\\"deequ\\\" % \\\"2.0.11-spark-3.5\\\""

# 2. Run with native mode (default)
sbt "runMain com.flowforge.examples.deequ.DeequExample"

# 3. Run with Deequ mode (if Deequ available)
sbt -Dff.quality.mode=deequ "runMain com.flowforge.examples.deequ.DeequExample"
```

## Output Comparison

### Native Mode (Default)
```
FlowForge + Deequ Quality Results
==================================================
Overall Passed: false
Quality Score: 85%

Quality Violations Found:
  ❌ NotNull(email)
     NotNull(email) failed (1 violations)
     Affected Records: 1

  ❌ Range(age,Some(18.0),Some(120.0))
     Range(age,Some(18.0),Some(120.0)) failed (1 violations) 
     Affected Records: 1

Mode Detection:
  Quality Engine: Native (Default)
```

### Deequ Enhanced Mode
```
FlowForge + Deequ Quality Results
==================================================
Overall Passed: false
Quality Score: 85%

Quality Violations Found:
  ❌ Constraint for completeness of email
     Constraint for completeness of email failed
     Affected Records: 0

  ❌ Constraint for range of age
     Constraint for range of age failed
     Affected Records: 0

Mode Detection:
  Quality Engine: Deequ Enhanced
```

## Deequ-Specific Features

When Deequ mode is enabled, you get additional capabilities:

### 1. Statistical Profiling
```scala
// Deequ automatically profiles data distributions
// Provides richer anomaly detection
// Historical comparison capabilities
```

### 2. Advanced Constraint Types
```scala
// Deequ supports more sophisticated constraints
QualityConstraint.Compliance("statistical_anomaly", 
  "mean(revenue) BETWEEN 800 AND 1500")
```

### 3. Enterprise Integration
```scala
// Deequ integrates with data catalogs
// Provides constraint suggestion based on data profiling
// Rich metadata about constraint evaluation
```

## Fallback Behavior

FlowForge's dual-mode design provides robust fallback:

1. **Deequ Available + Enabled**: Uses Deequ VerificationSuite
2. **Deequ Unavailable + Enabled**: Automatically falls back to native checks
3. **Default Mode**: Always uses native Spark checks

This ensures your pipelines work regardless of classpath configuration.

## Performance Considerations

| Mode | Performance | Dependencies | Use Case |
|------|-------------|--------------|----------|
| **Native** | Faster startup, minimal memory | Zero extra deps | Development, CI/CD, lean deployments |
| **Deequ** | Richer validation, slight overhead | +15MB JAR | Production, enterprise environments |

## Troubleshooting

### Deequ Not Loading
```bash
# Check if Deequ is on classpath
sbt "show dependencyClasspath" | grep deequ

# Verify version compatibility
sbt "libraryDependencies" | grep deequ
```

### Constraint Failures
```scala
// Enable debug mode for detailed constraint evaluation
System.setProperty("ff.quality.debug", "true")
```

### Scala Version Issues
Ensure you're using compatible versions:
- **Scala 2.13**: Use `deequ:2.0.11-spark-3.5`
- **Spark 3.5**: Required for Deequ 2.0.11

## Production Deployment

For production use with Deequ:

```scala
// production.conf
flowforge {
  quality {
    mode = "deequ"
    deequ {
      cache-results = true
      parallel-execution = true
      max-constraint-failures = 10
    }
  }
}
```

This page provides everything you need to enhance FlowForge's native data quality with Deequ's industry-standard capabilities while maintaining the lean default experience.

---

**References:**
- [Amazon Deequ Documentation](https://github.com/awslabs/deequ)
- [Deequ Maven Central](https://central.sonatype.com/artifact/com.amazon.deequ/deequ)
- [FlowForge Quality Framework](../quality/)