# Multi-Cloud Storage Recipes for FlowForge v1.0

## Overview

FlowForge v1.0 supports multi-cloud storage through **Spark's native drivers** instead of custom connectors. This approach provides production-ready reliability while keeping the API surface streamlined for 1.0.

All recipes use the **same FlowForge pipeline code** with different storage URIs and configuration blocks.

## Storage Driver Strategy

| Cloud Provider | Driver | URI Scheme | Hadoop Connector |
|---|---|---|---|
| **Amazon S3** | S3A | `s3a://` | `hadoop-aws` |
| **Azure Data Lake Gen2** | ABFS | `abfss://` | `hadoop-azure` |
| **Google Cloud Storage** | GCS | `gs://` | Google Cloud Dataproc connector |
| **Local/HDFS** | Standard | `file://` / `hdfs://` | Built-in |

## Amazon S3 Configuration

### Dependencies
```scala
// Add to build.sbt
libraryDependencies += "org.apache.hadoop" % "hadoop-aws" % "3.3.6"
```

### Spark Configuration
```scala
val sparkConfigS3 = Map(
  "spark.master" -> "local[*]",
  "spark.app.name" -> "FlowForge-S3-Pipeline",
  "spark.sql.extensions" -> "io.delta.sql.DeltaSparkSessionExtension",
  "spark.sql.catalog.spark_catalog" -> "org.apache.spark.sql.delta.catalog.DeltaCatalog",
  
  // S3A Configuration
  "spark.hadoop.fs.s3a.impl" -> "org.apache.hadoop.fs.s3a.S3AFileSystem",
  "spark.hadoop.fs.s3a.access.key" -> sys.env.getOrElse("AWS_ACCESS_KEY_ID", ""),
  "spark.hadoop.fs.s3a.secret.key" -> sys.env.getOrElse("AWS_SECRET_ACCESS_KEY", ""),
  "spark.hadoop.fs.s3a.aws.credentials.provider" -> "org.apache.hadoop.fs.s3a.SimpleAWSCredentialsProvider",
  
  // Performance optimizations
  "spark.hadoop.fs.s3a.block.size" -> "134217728", // 128MB
  "spark.hadoop.fs.s3a.buffer.dir" -> "/tmp",
  "spark.hadoop.fs.s3a.fast.upload" -> "true",
  "spark.hadoop.fs.s3a.fast.upload.buffer" -> "bytebuffer",
  
  // Optional: Path style access (for S3-compatible storage)
  "spark.hadoop.fs.s3a.path.style.access" -> "false",
  "spark.hadoop.fs.s3a.endpoint.region" -> "us-east-1"
)
```

### Pipeline Example
```scala
import com.flowforge.examples.spark.UsersPipelineUtils
import com.flowforge.engines.spark.ProductionSparkDataset
import cats.effect.IO

def runS3Pipeline(): IO[Unit] = {
  UsersPipelineUtils.createSparkResource(sparkConfigS3).use { spark =>
    for {
      // Read from S3
      inputPath = "s3a://my-bucket/raw-data/users.parquet"
      rawData <- IO.delay(ProductionSparkDataset.fromParquet[RawUser](spark, inputPath))
      
      // Transform (same logic regardless of storage)
      enrichedData <- UsersPipelineUtils.transformUsers(rawData)
      
      // Write to Delta on S3
      outputPath = "s3a://my-bucket/delta-tables/enriched-users"
      _ <- UsersPipelineUtils.writeToDelta(enrichedData, outputPath)
      
      _ <- IO.println(s"✅ S3 pipeline completed: $outputPath")
    } yield ()
  }
}
```

### Delta Lake Constraints on S3

FlowForge enforces data quality through Delta Lake constraints on S3. These constraints prevent invalid data from being written to your Delta tables.

```scala
// Create Delta table with comprehensive constraints
spark.sql(s"""
  CREATE TABLE IF NOT EXISTS delta.`s3a://my-bucket/delta-tables/users` (
    id STRING NOT NULL,
    email STRING NOT NULL,
    age INT,
    region STRING,
    CONSTRAINT valid_age CHECK (age >= 13 AND age <= 120),
    CONSTRAINT valid_email CHECK (email RLIKE '^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\\\.[A-Za-z]{2,}$$'),
    CONSTRAINT valid_region CHECK (region IN ('North America', 'Europe', 'Asia', 'Oceania', 'Other'))
  ) USING DELTA
  LOCATION 's3a://my-bucket/delta-tables/users'
""")

// Add constraints to existing tables
spark.sql(s"""
  ALTER TABLE delta.`s3a://my-bucket/delta-tables/users` 
  ALTER COLUMN id SET NOT NULL
""")

spark.sql(s"""
  ALTER TABLE delta.`s3a://my-bucket/delta-tables/users`
  ADD CONSTRAINT email_format CHECK (email LIKE '%@%.%')
""")
```

**Constraint Enforcement Example:**
```scala
// This INSERT will FAIL due to constraint violations
spark.sql(s"""
  INSERT INTO delta.`s3a://my-bucket/delta-tables/users`
  VALUES ('user1', 'invalid-email', 5, 'Unknown Region')
""")
// Error: CHECK constraint email_format violated by row with values: [user1, invalid-email, 5, Unknown Region]
```

## Azure Data Lake Gen2 Configuration  

### Dependencies
```scala
// Enable via environment or build configuration
// export HADOOP_OPTIONAL_TOOLS=hadoop-azure
libraryDependencies += "org.apache.hadoop" % "hadoop-azure" % "3.3.6"
```

### Spark Configuration
```scala
val sparkConfigAzure = Map(
  "spark.master" -> "local[*]",
  "spark.app.name" -> "FlowForge-Azure-Pipeline", 
  "spark.sql.extensions" -> "io.delta.sql.DeltaSparkSessionExtension",
  "spark.sql.catalog.spark_catalog" -> "org.apache.spark.sql.delta.catalog.DeltaCatalog",
  
  // ABFS Configuration
  "spark.hadoop.fs.azure.account.auth.type.mystorageaccount.dfs.core.windows.net" -> "OAuth",
  "spark.hadoop.fs.azure.account.oauth.provider.type.mystorageaccount.dfs.core.windows.net" -> "org.apache.hadoop.fs.azurebfs.oauth2.ClientCredsTokenProvider",
  "spark.hadoop.fs.azure.account.oauth2.client.id.mystorageaccount.dfs.core.windows.net" -> sys.env.getOrElse("AZURE_CLIENT_ID", ""),
  "spark.hadoop.fs.azure.account.oauth2.client.secret.mystorageaccount.dfs.core.windows.net" -> sys.env.getOrElse("AZURE_CLIENT_SECRET", ""),
  "spark.hadoop.fs.azure.account.oauth2.client.endpoint.mystorageaccount.dfs.core.windows.net" -> s"https://login.microsoftonline.com/${sys.env.getOrElse("AZURE_TENANT_ID", "")}/oauth2/token",
  
  // Performance optimizations
  "spark.hadoop.fs.azure.io.retry.max.retries" -> "3",
  "spark.hadoop.fs.azure.io.retry.backoff.interval" -> "30s"
)
```

### Pipeline Example
```scala
def runAzurePipeline(): IO[Unit] = {
  UsersPipelineUtils.createSparkResource(sparkConfigAzure).use { spark =>
    for {
      // Read from Azure Data Lake Gen2
      inputPath = "abfss://container@mystorageaccount.dfs.core.windows.net/raw-data/users.parquet"
      rawData <- IO.delay(ProductionSparkDataset.fromParquet[RawUser](spark, inputPath))
      
      // Transform (identical logic)
      enrichedData <- UsersPipelineUtils.transformUsers(rawData)
      
      // Write to Delta on Azure
      outputPath = "abfss://container@mystorageaccount.dfs.core.windows.net/delta-tables/enriched-users"  
      _ <- UsersPipelineUtils.writeToDelta(enrichedData, outputPath)
      
      _ <- IO.println(s"✅ Azure pipeline completed: $outputPath")
    } yield ()
  }
}
```

### Alternative: SAS Token Authentication
```scala
// For SAS token authentication instead of OAuth
val sparkConfigAzureSAS = Map(
  // ... other config ...
  "spark.hadoop.fs.azure.account.key.mystorageaccount.dfs.core.windows.net" -> sys.env.getOrElse("AZURE_STORAGE_SAS_TOKEN", "")
)
```

### Delta Lake Constraints on Azure Data Lake Gen2

Delta constraints work identically on Azure storage, providing the same data quality enforcement:

```scala
// Create Delta table with constraints on Azure
spark.sql(s"""
  CREATE TABLE IF NOT EXISTS delta.`abfss://container@account.dfs.core.windows.net/delta-tables/users` (
    id STRING NOT NULL,
    email STRING NOT NULL,
    age INT,
    region STRING,
    created_timestamp TIMESTAMP,
    CONSTRAINT valid_age CHECK (age >= 13 AND age <= 120),
    CONSTRAINT valid_email CHECK (email RLIKE '^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\\\.[A-Za-z]{2,}$$'),
    CONSTRAINT valid_region CHECK (region IN ('North America', 'Europe', 'Asia', 'Oceania', 'Other')),
    CONSTRAINT recent_data CHECK (created_timestamp >= '2020-01-01')
  ) USING DELTA
  LOCATION 'abfss://container@account.dfs.core.windows.net/delta-tables/users'
""")

// Modify existing table constraints
spark.sql(s"""
  ALTER TABLE delta.`abfss://container@account.dfs.core.windows.net/delta-tables/users`
  ADD CONSTRAINT business_hours CHECK (HOUR(created_timestamp) BETWEEN 8 AND 18)
""")
```

**Azure-Specific Enforcement:**
```scala
// Constraint violations on Azure storage behave identically to other clouds
try {
  spark.sql(s"""
    INSERT INTO delta.`abfss://container@account.dfs.core.windows.net/delta-tables/users`
    VALUES ('user2', 'bad-email', 150, 'Invalid Region', '2019-01-01 00:00:00')
  """)
} catch {
  case e: DeltaInvariantViolationException =>
    println(s"Constraint violation caught: ${e.getMessage}")
}
```

## Google Cloud Storage Configuration

### Dependencies
```scala
// Add GCS Hadoop connector
libraryDependencies += "com.google.cloud.bigdataoss" % "gcs-connector" % "hadoop3-2.2.11"
```

### Spark Configuration
```scala
val sparkConfigGCS = Map(
  "spark.master" -> "local[*]",
  "spark.app.name" -> "FlowForge-GCS-Pipeline",
  "spark.sql.extensions" -> "io.delta.sql.DeltaSparkSessionExtension", 
  "spark.sql.catalog.spark_catalog" -> "org.apache.spark.sql.delta.catalog.DeltaCatalog",
  
  // GCS Configuration
  "spark.hadoop.fs.gs.impl" -> "com.google.cloud.hadoop.fs.gcs.GoogleHadoopFileSystem",
  "spark.hadoop.fs.AbstractFileSystem.gs.impl" -> "com.google.cloud.hadoop.fs.gcs.GoogleHadoopFS",
  "spark.hadoop.google.cloud.auth.service.account.enable" -> "true",
  "spark.hadoop.google.cloud.auth.service.account.json.keyfile" -> sys.env.getOrElse("GOOGLE_APPLICATION_CREDENTIALS", ""),
  
  // Performance optimizations  
  "spark.hadoop.fs.gs.block.size" -> "134217728", // 128MB
  "spark.hadoop.fs.gs.outputstream.buffer.size" -> "8388608" // 8MB
)
```

### Pipeline Example
```scala
def runGCSPipeline(): IO[Unit] = {
  UsersPipelineUtils.createSparkResource(sparkConfigGCS).use { spark =>
    for {
      // Read from Google Cloud Storage
      inputPath = "gs://my-gcs-bucket/raw-data/users.parquet"
      rawData <- IO.delay(ProductionSparkDataset.fromParquet[RawUser](spark, inputPath))
      
      // Transform (same business logic)
      enrichedData <- UsersPipelineUtils.transformUsers(rawData)
      
      // Write to Delta on GCS
      outputPath = "gs://my-gcs-bucket/delta-tables/enriched-users"
      _ <- UsersPipelineUtils.writeToDelta(enrichedData, outputPath)
      
      _ <- IO.println(s"✅ GCS pipeline completed: $outputPath")
    } yield ()
  }
}
```

### Alternative: Workload Identity (GKE)
```scala
// For GKE workload identity (no service account key needed)
val sparkConfigGCSWorkloadIdentity = Map(
  // ... other config ...
  "spark.hadoop.google.cloud.auth.service.account.enable" -> "false",
  // Workload identity will be used automatically
)
```

### Delta Lake Constraints on Google Cloud Storage

GCS provides the same level of constraint enforcement as other cloud providers:

```scala
// Create Delta table with constraints on GCS
spark.sql(s"""
  CREATE TABLE IF NOT EXISTS delta.`gs://my-gcs-bucket/delta-tables/users` (
    id STRING NOT NULL,
    email STRING NOT NULL,
    age INT,
    region STRING,
    signup_date DATE,
    revenue DECIMAL(10,2),
    CONSTRAINT valid_age CHECK (age >= 13 AND age <= 120),
    CONSTRAINT valid_email CHECK (email RLIKE '^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\\\.[A-Za-z]{2,}$$'),
    CONSTRAINT valid_region CHECK (region IN ('North America', 'Europe', 'Asia', 'Oceania', 'Other')),
    CONSTRAINT positive_revenue CHECK (revenue IS NULL OR revenue >= 0),
    CONSTRAINT valid_signup CHECK (signup_date >= '2020-01-01')
  ) USING DELTA
  LOCATION 'gs://my-gcs-bucket/delta-tables/users'
""")

// Add constraints to existing GCS tables
spark.sql(s"""
  ALTER TABLE delta.`gs://my-gcs-bucket/delta-tables/users`
  ALTER COLUMN email SET NOT NULL
""")

spark.sql(s"""
  ALTER TABLE delta.`gs://my-gcs-bucket/delta-tables/users`
  ADD CONSTRAINT customer_lifecycle CHECK (
    (revenue IS NULL AND signup_date >= CURRENT_DATE - INTERVAL 30 DAY) OR
    (revenue IS NOT NULL AND revenue > 0)
  )
""")
```

**GCS Constraint Validation Example:**
```scala
// Test constraint enforcement on GCS
import org.apache.spark.sql.delta.DeltaInvariantViolationException

val invalidData = Seq(
  ("user3", "invalid.email", 12, "Invalid Region", java.sql.Date.valueOf("2019-01-01"), -50.0),
  ("user4", "valid@email.com", 25, "Europe", java.sql.Date.valueOf("2023-01-01"), null)
).toDF("id", "email", "age", "region", "signup_date", "revenue")

try {
  invalidData.write
    .format("delta")
    .mode("append")
    .save("gs://my-gcs-bucket/delta-tables/users")
} catch {
  case e: DeltaInvariantViolationException =>
    println("✅ Constraint enforcement working on GCS:")
    println(s"   ${e.getMessage}")
}
```

## Universal FlowForge Pipeline Template

This template works across **all storage backends** - just change the configuration and URIs:

```scala
package com.flowforge.examples.multicloud

import cats.effect.{ IO, Resource }
import com.flowforge.examples.spark.UsersPipelineUtils
import com.flowforge.engines.spark.ProductionSparkDataset
import org.apache.spark.sql.SparkSession

object UniversalStoragePipeline {

  def runPipeline(
    storageConfig: Map[String, String],
    inputPath: String,
    outputPath: String,
    pipelineName: String
  ): IO[Unit] = {
    
    UsersPipelineUtils.createSparkResource(storageConfig).use { spark =>
      for {
        _ <- IO.println(s"🚀 Starting $pipelineName pipeline")
        _ <- IO.println(s"📊 Input: $inputPath")
        _ <- IO.println(s"💾 Output: $outputPath")
        
        // 1. Read data (storage-agnostic)
        rawData <- IO.delay(ProductionSparkDataset.fromParquet[RawUser](spark, inputPath))
        _ <- IO.println(s"✅ Loaded ${rawData.count} raw records")
        
        // 2. Apply data quality validation
        qualityResult <- UsersPipelineUtils.validateDataQuality(rawData)
        _ <- IO.println(s"📈 Data quality score: ${qualityResult.score * 100}%")
        
        // 3. Transform data (business logic independent of storage)
        enrichedData <- UsersPipelineUtils.transformUsers(rawData)  
        _ <- IO.println(s"🔄 Transformed to ${enrichedData.count} enriched records")
        
        // 4. Write to Delta with constraints (works on all clouds)
        _ <- writeDeltaWithConstraints(spark, enrichedData, outputPath)
        _ <- IO.println(s"✅ $pipelineName pipeline completed successfully")
        
      } yield ()
    }
  }

  private def writeDeltaWithConstraints(
    spark: SparkSession, 
    data: ProductionSparkDataset[EnrichedUser], 
    outputPath: String
  ): IO[Unit] = {
    IO.delay {
      // Write data first
      data.writeDelta(outputPath)
      
      // Add universal constraints that work on all storage backends
      spark.sql(s"""
        ALTER TABLE delta.`$outputPath` 
        ALTER COLUMN id SET NOT NULL
      """)
      
      spark.sql(s"""
        ALTER TABLE delta.`$outputPath` 
        ALTER COLUMN email SET NOT NULL  
      """)
      
      spark.sql(s"""
        ALTER TABLE delta.`$outputPath`
        ADD CONSTRAINT valid_age CHECK (age >= 13 AND age <= 120)
      """)
      
      spark.sql(s"""
        ALTER TABLE delta.`$outputPath`
        ADD CONSTRAINT valid_region CHECK (region IN ('North America', 'Europe', 'Asia', 'Oceania', 'Other'))
      """)
    }
  }

  // Usage examples
  def main(args: Array[String]): Unit = {
    val program = args.headOption match {
      case Some("s3") => runPipeline(
        sparkConfigS3,
        "s3a://my-bucket/input/users.parquet",
        "s3a://my-bucket/output/enriched-users",
        "S3"
      )
      case Some("azure") => runPipeline(
        sparkConfigAzure, 
        "abfss://container@account.dfs.core.windows.net/input/users.parquet",
        "abfss://container@account.dfs.core.windows.net/output/enriched-users",
        "Azure"
      )
      case Some("gcs") => runPipeline(
        sparkConfigGCS,
        "gs://my-bucket/input/users.parquet", 
        "gs://my-bucket/output/enriched-users",
        "GCS"
      )
      case _ => IO.println("Usage: sbt 'runMain UniversalStoragePipeline [s3|azure|gcs]'")
    }
    
    program.unsafeRunSync()
  }
}
```

## Delta Lake Multi-Cloud Constraints

### Schema Evolution Policies
All FlowForge schema evolution policies work identically across storage backends:

```scala
import com.flowforge.core.types.SchemaEvolutionPolicy

// These policies work on S3A, ABFS, and GCS
val policies = List(
  SchemaEvolutionPolicy.Exact,      // No schema changes allowed
  SchemaEvolutionPolicy.Backward,   // Can remove columns, relax constraints  
  SchemaEvolutionPolicy.Forward,    // Can add optional columns
  SchemaEvolutionPolicy.Full        // Bidirectional compatibility
)
```

### Cross-Cloud Table Constraints
```sql
-- Works identically on s3a://, abfss://, and gs:// paths
ALTER TABLE delta.`{storage-uri}/my-table`
ADD CONSTRAINT business_rule CHECK (
  amount > 0 
  AND status IN ('active', 'inactive', 'pending')
  AND created_date >= '2020-01-01'
);
```

## Production Considerations

### Security Best Practices
- **S3**: Use IAM roles instead of access keys when possible
- **Azure**: Prefer managed identity over client credentials
- **GCS**: Use workload identity on GKE, service account keys otherwise
- **All**: Enable encryption in transit and at rest

### Performance Optimization
- **Partitioning**: Use same partition strategies across all clouds
- **Delta optimization**: `OPTIMIZE` and `VACUUM` commands work universally  
- **Caching**: Spark's adaptive query execution benefits all storage backends
- **Parallelism**: Configure `spark.sql.shuffle.partitions` based on data size

### Monitoring & Observability
- **OpenLineage**: Automatic lineage emission works regardless of storage backend
- **Quality metrics**: FlowForge quality scores are storage-independent
- **Performance**: Spark metrics capture I/O performance across all systems

### Cost Optimization
- **S3**: Use intelligent tiering, lifecycle policies
- **Azure**: Leverage hot/cool/archive tiers appropriately  
- **GCS**: Use nearline/coldline storage classes for older data
- **All**: Enable Delta Lake's data skipping and Z-ordering

## Migration Between Cloud Providers

The universal pipeline design makes cloud migration straightforward:

```scala
// Step 1: Copy data between clouds using same pipeline
def migrateData(): IO[Unit] = {
  for {
    // Read from source cloud
    _ <- runPipeline(sparkConfigS3, "s3a://old-bucket/data", "/tmp/migration", "Migration-Read")
    
    // Write to destination cloud  
    _ <- runPipeline(sparkConfigGCS, "/tmp/migration", "gs://new-bucket/data", "Migration-Write")
    
    // Verify constraints are maintained
    _ <- verifyConstraints("gs://new-bucket/data")
  } yield ()
}
```

This approach ensures that FlowForge pipelines are **truly cloud-agnostic** while leveraging battle-tested, production-ready storage drivers from the Spark ecosystem.

---

**References:**
- [Hadoop AWS Documentation](https://hadoop.apache.org/docs/stable/hadoop-aws/tools/hadoop-aws/index.html)
- [Hadoop Azure Documentation](https://hadoop.apache.org/docs/stable/hadoop-azure/index.html)  
- [Google Cloud Dataproc Connectors](https://cloud.google.com/dataproc/docs/concepts/connectors/cloud-storage)
- [Delta Lake Multi-Cloud](https://docs.delta.io/latest/delta-storage.html)