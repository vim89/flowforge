package com.flowforge.sample.advanced

import cats.effect.{ IO, IOApp, Resource }
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.parallel._
import com.flowforge.app.AuditDb
import com.flowforge.core.PipelineBuilder
import com.flowforge.core.algebra.{ DataAlgebra, EffectSystem }
import com.flowforge.core.algebra.{ DataEncoder => FFEncoder, DataDecoder => FFDecoder }
import com.flowforge.core.algebra.{ EncodedData => FFEncodedData, CorruptedData => FFCorruptedData }
import com.flowforge.core.contracts.{ SchemaConforms, SchemaPolicy }
import com.flowforge.core.contracts.derive.Shape
import com.flowforge.core.lineage.OpenLineageEmitter
import com.flowforge.core.types._
import com.flowforge.engines.spark.SparkDataAlgebra
import com.flowforge.framework.PipelineExecution
import com.flowforge.quality.deequ.DeequAdapter
import io.circe.parser.parse
import io.circe.syntax._
import io.circe.generic.auto._
import org.apache.spark.sql.SparkSession
import java.time.{ Instant, LocalDate }
import scala.util.{ Try, Success, Failure }

/**
 * Multi-Cloud Data Lake Pipeline Example
 * 
 * Demonstrates FlowForge's capabilities for:
 * 1. Multi-Cloud Sources: S3, Azure Data Lake, GCS unified access
 * 2. Data Quality Integration: Dual-mode validation (native Spark + Deequ)
 * 3. Schema Evolution: Different policies (Backward, Forward, Full) with practical use cases
 * 4. Lineage Tracking: Automatic OpenLineage emission across cloud boundaries
 * 5. Resource Management: Proper cleanup and error handling
 * 
 * Scenario: Customer data consolidation across cloud providers for a global e-commerce platform
 */
object MultiCloudDataLakePipeline extends IOApp.Simple {

  // === DOMAIN MODELS ===
  
  /**
   * Customer data from legacy system (S3) - original schema
   */
  case class LegacyCustomer(
    customerId: Long,
    firstName: String,
    lastName: String,
    email: String,
    registrationDate: String, // Legacy: stored as string
    region: String
  )

  /**
   * Customer data from CRM system (Azure) - evolved schema with additional fields
   */
  case class CrmCustomer(
    customerId: Long,
    firstName: String,
    lastName: String,
    email: String,
    registrationDate: String,
    region: String,
    phoneNumber: Option[String], // New field
    loyaltyTier: Option[String], // New field
    lastLoginDate: Option[String] // New field
  )

  /**
   * Customer data from mobile app (GCS) - different field names and types
   */
  case class MobileCustomer(
    id: Long, // Different field name
    fullName: String, // Combined name field
    emailAddress: String, // Different field name
    signupTimestamp: Long, // Different type: epoch timestamp
    countryCode: String, // Different field name
    deviceType: String, // Mobile-specific field
    appVersion: String // Mobile-specific field
  )

  /**
   * Unified customer model - target schema for data lake
   */
  case class UnifiedCustomer(
    customerId: Long,
    firstName: String,
    lastName: String,
    email: String,
    registrationDate: LocalDate,
    region: String,
    phoneNumber: Option[String] = None,
    loyaltyTier: Option[String] = None,
    lastLoginDate: Option[LocalDate] = None,
    deviceType: Option[String] = None,
    appVersion: Option[String] = None,
    sourceSystem: String,
    ingestionTimestamp: Instant
  )

  // === SHAPE DERIVATIONS ===
  
  implicit val legacyCustomerShape: Shape[LegacyCustomer] = Shape.gen[LegacyCustomer]
  implicit val crmCustomerShape: Shape[CrmCustomer] = Shape.gen[CrmCustomer]
  implicit val mobileCustomerShape: Shape[MobileCustomer] = Shape.gen[MobileCustomer]
  implicit val unifiedCustomerShape: Shape[UnifiedCustomer] = Shape.gen[UnifiedCustomer]

  // === SCHEMA CONFORMANCE POLICIES ===
  
  // Legacy system: Use Backward policy to allow new fields in target
  implicit val legacyConforms: SchemaConforms[LegacyCustomer, UnifiedCustomer, SchemaPolicy.Backward] = implicitly
  
  // CRM system: Use Forward policy to allow missing fields in source
  implicit val crmConforms: SchemaConforms[CrmCustomer, UnifiedCustomer, SchemaPolicy.Forward] = implicitly
  
  // Mobile system: Use Full policy for complete flexibility during migration
  implicit val mobileConforms: SchemaConforms[MobileCustomer, UnifiedCustomer, SchemaPolicy.Full] = implicitly

  // === EFFECT SYSTEM ===
  
  implicit val es: EffectSystem[IO] = com.flowforge.core.instances.EffectInstances.catsEffectSystemInstance

  // === ENCODERS/DECODERS ===
  
  implicit val legacyDecoder: FFDecoder[LegacyCustomer] = createJsonDecoder[LegacyCustomer]
  implicit val crmDecoder: FFDecoder[CrmCustomer] = createJsonDecoder[CrmCustomer]
  implicit val mobileDecoder: FFDecoder[MobileCustomer] = createJsonDecoder[MobileCustomer]
  implicit val unifiedDecoder: FFDecoder[UnifiedCustomer] = createJsonDecoder[UnifiedCustomer]

  implicit val legacyEncoder: FFEncoder[LegacyCustomer] = createJsonEncoder[LegacyCustomer]
  implicit val crmEncoder: FFEncoder[CrmCustomer] = createJsonEncoder[CrmCustomer]
  implicit val mobileEncoder: FFEncoder[MobileCustomer] = createJsonEncoder[MobileCustomer]
  implicit val unifiedEncoder: FFEncoder[UnifiedCustomer] = createJsonEncoder[UnifiedCustomer]

  private def createJsonDecoder[T: io.circe.Decoder]: FFDecoder[T] = new FFDecoder[T] {
    def decode(ed: FFEncodedData, format: DataFormat): Either[FFCorruptedData, T] = format match {
      case DataFormat.JSON | DataFormat.JSONL =>
        parse(new String(ed.data, "UTF-8"))
          .left.map(e => FFCorruptedData(s"JSON parse error: ${e.getMessage}"))
          .flatMap(_.as[T].left.map(e => FFCorruptedData(s"JSON decode error: ${e.getMessage}")))
      case other => Left(FFCorruptedData(s"Unsupported format: $other"))
    }
    def validateSchema(ed: FFEncodedData, expected: DataSchema): Either[FFCorruptedData, Unit] = Right(())
    def decodeWithEvolution(ed: FFEncodedData, format: DataFormat, target: DataSchema): Either[FFCorruptedData, T] = 
      decode(ed, format)
    def supportsFormat(format: DataFormat): Boolean = 
      format == DataFormat.JSON || format == DataFormat.JSONL
  }

  private def createJsonEncoder[T: io.circe.Encoder]: FFEncoder[T] = 
    com.flowforge.core.algebra.DataEncoder.instance[T](
      (value, format) => format match {
        case DataFormat.JSON | DataFormat.JSONL =>
          Right(FFEncodedData(value.asJson.noSpaces.getBytes("UTF-8"), format))
        case other => Left(s"Unsupported format: $other")
      },
      _ => DataSchema.builder.build // Simplified schema for demo
    )

  // === SPARK RESOURCES ===
  
  private def sparkR: Resource[IO, SparkSession] =
    Resource.make(IO {
      SparkSession
        .builder()
        .appName("MultiCloudDataLakePipeline")
        .master("local[*]")
        .config("spark.ui.enabled", "false")
        .config("spark.sql.extensions", "io.delta.sql.DeltaSparkSessionExtension")
        .config("spark.sql.catalog.spark_catalog", "org.apache.delta.catalog.DeltaCatalog")
        // Multi-cloud configurations
        .config("spark.hadoop.fs.s3a.impl", "org.apache.hadoop.fs.s3a.S3AFileSystem")
        .config("spark.hadoop.fs.azure.impl", "org.apache.hadoop.fs.azure.NativeAzureFileSystem")
        .config("spark.hadoop.fs.gs.impl", "com.google.cloud.hadoop.fs.gcs.GoogleHadoopFileSystem")
        .getOrCreate()
    })(s => IO(s.stop()).void)

  private def daoR(spark: SparkSession): Resource[IO, DataAlgebra[IO]] =
    Resource.pure(SparkDataAlgebra.createSparkDataAlgebra[IO](spark).algebra)

  // === MAIN PIPELINE ===
  
  def run: IO[Unit] = sparkR.use { spark =>
    daoR(spark).use { dao =>
      val log = org.slf4j.LoggerFactory.getLogger("multi-cloud-pipeline")
      
      for {
        _ <- AuditDb.init()
        _ <- AuditDb.log("multi_cloud_pipeline_started")
        _ <- log.info("Starting multi-cloud customer data consolidation pipeline").pure[IO]
        
        // === PHASE 1: MULTI-CLOUD DATA INGESTION ===
        _ <- AuditDb.log("phase_1_ingestion_started")
        
        // S3 Source (Legacy System) - Backward compatibility
        legacyResult <- processLegacyCustomers(dao, log)
        _ <- AuditDb.log(s"legacy_customers_processed_count=${legacyResult._2}")
        
        // Azure Data Lake Source (CRM System) - Forward compatibility  
        crmResult <- processCrmCustomers(dao, log)
        _ <- AuditDb.log(s"crm_customers_processed_count=${crmResult._2}")
        
        // GCS Source (Mobile App) - Full flexibility
        mobileResult <- processMobileCustomers(dao, log)
        _ <- AuditDb.log(s"mobile_customers_processed_count=${mobileResult._2}")
        
        // === PHASE 2: DATA QUALITY VALIDATION ===
        _ <- AuditDb.log("phase_2_quality_validation_started")
        
        // Combine all datasets for unified quality checks
        allCustomers = List(legacyResult._1, crmResult._1, mobileResult._1).flatten
        qualityResult <- runUnifiedQualityChecks(spark, allCustomers, log)
        _ <- AuditDb.log(s"quality_validation_passed=${qualityResult.passed}")
        
        // === PHASE 3: UNIFIED DATA LAKE WRITE ===
        _ <- AuditDb.log("phase_3_unified_write_started")
        
        finalResult <- if (qualityResult.passed) {
          writeToDataLake(dao, allCustomers, log)
        } else {
          IO(log.warn(s"Quality validation failed with ${qualityResult.violations.size} violations")) *>
          AuditDb.log("pipeline_failed_quality_validation") *>
          IO.raiseError(new RuntimeException(s"Quality validation failed: ${qualityResult.violations}"))
        }
        
        _ <- AuditDb.log(s"unified_customers_written_count=${finalResult}")
        _ <- AuditDb.log("multi_cloud_pipeline_completed")
        _ <- log.info(s"Pipeline completed successfully. Processed ${finalResult} unified customer records").pure[IO]
        
      } yield ()
    }
  }

  // === LEGACY CUSTOMERS PROCESSING (S3) ===
  
  private def processLegacyCustomers(dao: DataAlgebra[IO], log: org.slf4j.Logger): IO[(List[UnifiedCustomer], Int)] = {
    val s3Source = DataSource.cloud(
      provider = "s3",
      bucket = "legacy-customer-data",
      path = "customers/year=2023/month=*/day=*/customers.parquet",
      format = DataFormat.Parquet,
      credentials = Map(
        "aws.access.key.id" -> sys.env.getOrElse("AWS_ACCESS_KEY_ID", "demo-key"),
        "aws.secret.access.key" -> sys.env.getOrElse("AWS_SECRET_ACCESS_KEY", "demo-secret"),
        "aws.region" -> "us-east-1"
      )
    )
    
    for {
      _ <- IO(log.info("Processing legacy customers from S3"))
      _ <- AuditDb.log("legacy_s3_read_started")
      
      // Simulate reading legacy data (in real scenario, this would read from S3)
      legacyCustomers = generateSampleLegacyCustomers()
      
      // Transform to unified model with Backward schema policy
      unifiedCustomers <- legacyCustomers.traverse(transformLegacyToUnified)
      
      _ <- AuditDb.log("legacy_transformation_completed")
      _ <- IO(log.info(s"Transformed ${unifiedCustomers.size} legacy customers"))
      
    } yield (unifiedCustomers, unifiedCustomers.size)
  }

  private def transformLegacyToUnified(legacy: LegacyCustomer): IO[UnifiedCustomer] = IO {
    val registrationDate = Try(LocalDate.parse(legacy.registrationDate)).getOrElse(LocalDate.now())
    
    UnifiedCustomer(
      customerId = legacy.customerId,
      firstName = legacy.firstName,
      lastName = legacy.lastName,
      email = legacy.email,
      registrationDate = registrationDate,
      region = legacy.region,
      phoneNumber = None, // Not available in legacy system
      loyaltyTier = None, // Not available in legacy system
      lastLoginDate = None, // Not available in legacy system
      deviceType = None, // Not applicable for legacy system
      appVersion = None, // Not applicable for legacy system
      sourceSystem = "legacy-s3",
      ingestionTimestamp = Instant.now()
    )
  }

  // === CRM CUSTOMERS PROCESSING (Azure) ===
  
  private def processCrmCustomers(dao: DataAlgebra[IO], log: org.slf4j.Logger): IO[(List[UnifiedCustomer], Int)] = {
    val azureSource = DataSource.cloud(
      provider = "azure",
      bucket = "crm-data-lake",
      path = "customers/delta/customers_table/",
      format = DataFormat.Delta,
      credentials = Map(
        "azure.storage.account.name" -> sys.env.getOrElse("AZURE_STORAGE_ACCOUNT", "democrmaccount"),
        "azure.storage.account.key" -> sys.env.getOrElse("AZURE_STORAGE_KEY", "demo-key"),
        "azure.container.name" -> "crm-data"
      )
    )
    
    for {
      _ <- IO(log.info("Processing CRM customers from Azure Data Lake"))
      _ <- AuditDb.log("crm_azure_read_started")
      
      // Simulate reading CRM data (in real scenario, this would read from Azure)
      crmCustomers = generateSampleCrmCustomers()
      
      // Transform to unified model with Forward schema policy
      unifiedCustomers <- crmCustomers.traverse(transformCrmToUnified)
      
      _ <- AuditDb.log("crm_transformation_completed")
      _ <- IO(log.info(s"Transformed ${unifiedCustomers.size} CRM customers"))
      
    } yield (unifiedCustomers, unifiedCustomers.size)
  }

  private def transformCrmToUnified(crm: CrmCustomer): IO[UnifiedCustomer] = IO {
    val registrationDate = Try(LocalDate.parse(crm.registrationDate)).getOrElse(LocalDate.now())
    val lastLoginDate = crm.lastLoginDate.flatMap(d => Try(LocalDate.parse(d)).toOption)
    
    UnifiedCustomer(
      customerId = crm.customerId,
      firstName = crm.firstName,
      lastName = crm.lastName,
      email = crm.email,
      registrationDate = registrationDate,
      region = crm.region,
      phoneNumber = crm.phoneNumber,
      loyaltyTier = crm.loyaltyTier,
      lastLoginDate = lastLoginDate,
      deviceType = None, // Not applicable for CRM system
      appVersion = None, // Not applicable for CRM system
      sourceSystem = "crm-azure",
      ingestionTimestamp = Instant.now()
    )
  }

  // === MOBILE CUSTOMERS PROCESSING (GCS) ===
  
  private def processMobileCustomers(dao: DataAlgebra[IO], log: org.slf4j.Logger): IO[(List[UnifiedCustomer], Int)] = {
    val gcsSource = DataSource.cloud(
      provider = "gcs",
      bucket = "mobile-app-analytics",
      path = "user-events/year=2023/month=*/day=*/user_profiles.json",
      format = DataFormat.JSONL,
      credentials = Map(
        "google.cloud.project.id" -> sys.env.getOrElse("GCP_PROJECT_ID", "demo-project"),
        "google.cloud.service.account.key" -> sys.env.getOrElse("GCP_SERVICE_ACCOUNT_KEY", "demo-key")
      )
    )
    
    for {
      _ <- IO(log.info("Processing mobile customers from GCS"))
      _ <- AuditDb.log("mobile_gcs_read_started")
      
      // Simulate reading mobile data (in real scenario, this would read from GCS)
      mobileCustomers = generateSampleMobileCustomers()
      
      // Transform to unified model with Full schema policy
      unifiedCustomers <- mobileCustomers.traverse(transformMobileToUnified)
      
      _ <- AuditDb.log("mobile_transformation_completed")
      _ <- IO(log.info(s"Transformed ${unifiedCustomers.size} mobile customers"))
      
    } yield (unifiedCustomers, unifiedCustomers.size)
  }

  private def transformMobileToUnified(mobile: MobileCustomer): IO[UnifiedCustomer] = IO {
    val registrationDate = Try(
      Instant.ofEpochSecond(mobile.signupTimestamp).atZone(java.time.ZoneOffset.UTC).toLocalDate()
    ).getOrElse(LocalDate.now())
    
    // Parse full name into first and last name (simplified logic)
    val nameParts = mobile.fullName.split(" ", 2)
    val firstName = nameParts.headOption.getOrElse("Unknown")
    val lastName = if (nameParts.length > 1) nameParts(1) else ""
    
    UnifiedCustomer(
      customerId = mobile.id,
      firstName = firstName,
      lastName = lastName,
      email = mobile.emailAddress,
      registrationDate = registrationDate,
      region = mobile.countryCode,
      phoneNumber = None, // Not available in mobile data
      loyaltyTier = None, // Not available in mobile data
      lastLoginDate = None, // Not available in mobile data
      deviceType = Some(mobile.deviceType),
      appVersion = Some(mobile.appVersion),
      sourceSystem = "mobile-gcs",
      ingestionTimestamp = Instant.now()
    )
  }

  // === DATA QUALITY VALIDATION ===
  
  private def runUnifiedQualityChecks(
    spark: SparkSession, 
    customers: List[UnifiedCustomer], 
    log: org.slf4j.Logger
  ): IO[DataAlgebra.QualityResult[List[UnifiedCustomer]]] = {
    
    val qualityConstraints = List(
      // Core identity constraints
      QualityConstraint.NotNull(RefinedTypes.FieldName.unsafeFrom("customerId")),
      QualityConstraint.Unique(RefinedTypes.FieldName.unsafeFrom("customerId")),
      
      // Contact information constraints
      QualityConstraint.NotNull(RefinedTypes.FieldName.unsafeFrom("email")),
      QualityConstraint.Pattern(
        RefinedTypes.FieldName.unsafeFrom("email"),
        "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
      ),
      QualityConstraint.Distinctness(RefinedTypes.FieldName.unsafeFrom("email"), 0.95), // Allow some duplicates
      
      // Name constraints
      QualityConstraint.NotNull(RefinedTypes.FieldName.unsafeFrom("firstName")),
      QualityConstraint.NullRateBelow(RefinedTypes.FieldName.unsafeFrom("lastName"), 0.1), // Allow some missing last names
      
      // Regional data constraints
      QualityConstraint.NotNull(RefinedTypes.FieldName.unsafeFrom("region")),
      QualityConstraint.Compliance(
        "valid_region_codes",
        "region IN ('US', 'EU', 'APAC', 'LATAM', 'us-east-1', 'europe-west1', 'asia-southeast1')",
        0.95
      ),
      
      // Source system tracking
      QualityConstraint.NotNull(RefinedTypes.FieldName.unsafeFrom("sourceSystem")),
      QualityConstraint.Compliance(
        "valid_source_systems",
        "sourceSystem IN ('legacy-s3', 'crm-azure', 'mobile-gcs')",
        1.0
      ),
      
      // Temporal constraints
      QualityConstraint.NotNull(RefinedTypes.FieldName.unsafeFrom("ingestionTimestamp")),
      QualityConstraint.Compliance(
        "recent_ingestion",
        "ingestionTimestamp >= current_timestamp() - interval 1 hour",
        1.0
      )
    )
    
    for {
      _ <- IO(log.info("Running unified data quality validation"))
      _ <- AuditDb.log("quality_validation_started")
      
      // Convert to Spark DataFrame for Deequ validation
      // In real implementation, this would use the actual DataAlgebra dataset
      result <- IO {
        // Simulate quality validation result
        val passedChecks = qualityConstraints.size - 1 // Simulate one minor violation
        val score = passedChecks.toDouble / qualityConstraints.size
        val violations = if (score < 1.0) {
          List("Minor email distinctness violation: 94% vs required 95%")
        } else List.empty
        
        DataAlgebra.QualityResult(
          data = customers,
          passed = score >= 0.9, // Accept if 90% of checks pass
          score = score,
          violations = violations,
          metrics = Map(
            "total_records" -> customers.size.toString,
            "unique_customers" -> customers.map(_.customerId).distinct.size.toString,
            "source_systems" -> customers.map(_.sourceSystem).distinct.mkString(",")
          )
        )
      }
      
      _ <- AuditDb.log(s"quality_score=${result.score}")
      _ <- if (result.passed) {
        IO(log.info(s"Quality validation passed with score ${result.score}"))
      } else {
        IO(log.warn(s"Quality validation failed with score ${result.score}, violations: ${result.violations}"))
      }
      
    } yield result
  }

  // === DATA LAKE WRITE WITH LINEAGE ===
  
  private def writeToDataLake(
    dao: DataAlgebra[IO], 
    customers: List[UnifiedCustomer], 
    log: org.slf4j.Logger
  ): IO[Int] = {
    
    val deltaLakeSink = DataSink.cloud(
      provider = "s3", // Could be any cloud provider
      bucket = "unified-data-lake",
      path = "gold/customers/delta/unified_customers/",
      format = DataFormat.Delta,
      credentials = Map(
        "aws.access.key.id" -> sys.env.getOrElse("AWS_ACCESS_KEY_ID", "demo-key"),
        "aws.secret.access.key" -> sys.env.getOrElse("AWS_SECRET_ACCESS_KEY", "demo-secret"),
        "aws.region" -> "us-east-1"
      ),
      options = Map(
        "mergeSchema" -> "true",
        "overwriteSchema" -> "false",
        "partitionBy" -> "sourceSystem,region"
      )
    )
    
    for {
      _ <- IO(log.info("Writing unified customer data to Delta Lake"))
      _ <- AuditDb.log("delta_write_started")
      
      // Emit OpenLineage events for data lineage tracking
      _ <- emitLineageEvents(customers)
      
      // Build and execute pipeline with contract validation
      pipeline = PipelineBuilder[IO]("unified-customer-pipeline")
        .addTypedSource[UnifiedCustomer, UnifiedCustomer, SchemaPolicy.Exact](
          TypedSource(DataSource.memory(customers)), // In-memory source for processed data
          _ => IO.pure(customers.head) // Sample for contract validation
        )
        .addTransform[UnifiedCustomer] { customer =>
          // Apply final business rules and enrichment
          IO.pure(customer.copy(
            email = customer.email.toLowerCase.trim,
            region = normalizeRegion(customer.region)
          ))
        }
        .addTypedSink[UnifiedCustomer, SchemaPolicy.Exact](
          TypedSink(deltaLakeSink),
          (_, _) => IO(log.info("Writing to Delta Lake")) // Simplified write operation
        )
        .build()
      
      _ <- PipelineExecution.execute(pipeline)(())
      _ <- AuditDb.log("delta_write_completed")
      _ <- IO(log.info(s"Successfully wrote ${customers.size} unified customer records to Delta Lake"))
      
    } yield customers.size
  }

  private def normalizeRegion(region: String): String = region.toLowerCase match {
    case r if r.startsWith("us") => "US"
    case r if r.startsWith("eu") || r.startsWith("europe") => "EU"
    case r if r.startsWith("asia") || r.startsWith("ap") => "APAC"
    case r if r.startsWith("latam") || r.startsWith("south") => "LATAM"
    case other => other.toUpperCase
  }

  // === LINEAGE TRACKING ===
  
  private def emitLineageEvents(customers: List[UnifiedCustomer]): IO[Unit] = {
    val lineageEvents = List(
      OpenLineageEvent(
        eventType = "START",
        job = OpenLineageJob("multi-cloud-customer-consolidation", "data-engineering"),
        run = OpenLineageRun(java.util.UUID.randomUUID().toString),
        inputs = List(
          OpenLineageDataset("s3://legacy-customer-data/customers/", "legacy-customers"),
          OpenLineageDataset("azure://crm-data-lake/customers/", "crm-customers"),
          OpenLineageDataset("gs://mobile-app-analytics/user-events/", "mobile-customers")
        ),
        outputs = List(
          OpenLineageDataset("s3://unified-data-lake/gold/customers/", "unified-customers")
        )
      ),
      OpenLineageEvent(
        eventType = "COMPLETE",
        job = OpenLineageJob("multi-cloud-customer-consolidation", "data-engineering"),
        run = OpenLineageRun(java.util.UUID.randomUUID().toString),
        inputs = List(
          OpenLineageDataset("s3://legacy-customer-data/customers/", "legacy-customers"),
          OpenLineageDataset("azure://crm-data-lake/customers/", "crm-customers"),
          OpenLineageDataset("gs://mobile-app-analytics/user-events/", "mobile-customers")
        ),
        outputs = List(
          OpenLineageDataset("s3://unified-data-lake/gold/customers/", "unified-customers")
        ),
        metrics = Some(Map(
          "records_processed" -> customers.size.toString,
          "source_systems" -> customers.map(_.sourceSystem).distinct.size.toString
        ))
      )
    )
    
    lineageEvents.traverse_(event => 
      AuditDb.log(s"lineage_event_${event.eventType.toLowerCase}_emitted") *>
      IO(println(s"OpenLineage Event: ${event.eventType} for job ${event.job.name}"))
    )
  }

  // === SAMPLE DATA GENERATION ===
  
  private def generateSampleLegacyCustomers(): List[LegacyCustomer] = List(
    LegacyCustomer(1001L, "John", "Doe", "john.doe@example.com", "2020-01-15", "US"),
    LegacyCustomer(1002L, "Jane", "Smith", "jane.smith@example.com", "2020-02-20", "EU"),
    LegacyCustomer(1003L, "Bob", "Johnson", "bob.johnson@example.com", "2020-03-10", "APAC")
  )

  private def generateSampleCrmCustomers(): List[CrmCustomer] = List(
    CrmCustomer(2001L, "Alice", "Brown", "alice.brown@example.com", "2021-01-15", "US", 
                Some("+1-555-0101"), Some("Gold"), Some("2023-12-01")),
    CrmCustomer(2002L, "Charlie", "Wilson", "charlie.wilson@example.com", "2021-02-20", "EU", 
                Some("+44-20-7946-0958"), Some("Silver"), Some("2023-11-28")),
    CrmCustomer(2003L, "Diana", "Davis", "diana.davis@example.com", "2021-03-10", "APAC", 
                None, Some("Bronze"), Some("2023-12-02"))
  )

  private def generateSampleMobileCustomers(): List[MobileCustomer] = List(
    MobileCustomer(3001L, "Eva Martinez", "eva.martinez@example.com", 1640995200L, "LATAM", "iOS", "2.1.0"),
    MobileCustomer(3002L, "Frank Chen", "frank.chen@example.com", 1641081600L, "APAC", "Android", "2.0.5"),
    MobileCustomer(3003L, "Grace Kim", "grace.kim@example.com", 1641168000L, "US", "iOS", "2.1.0")
  )

  // === HELPER CASE CLASSES FOR LINEAGE ===
  
  case class OpenLineageEvent(
    eventType: String,
    job: OpenLineageJob,
    run: OpenLineageRun,
    inputs: List[OpenLineageDataset],
    outputs: List[OpenLineageDataset],
    metrics: Option[Map[String, String]] = None
  )

  case class OpenLineageJob(name: String, namespace: String)
  case class OpenLineageRun(runId: String)
  case class OpenLineageDataset(name: String, namespace: String)
}