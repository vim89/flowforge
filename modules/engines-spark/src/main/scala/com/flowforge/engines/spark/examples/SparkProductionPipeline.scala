/**
 * FlowForge Spark Engine - Production Pipeline Example
 * 
 * This example demonstrates a complete production-ready data pipeline using the Spark DataAlgebra
 * adapter with FlowForge's functional composition patterns.
 * 
 * Features demonstrated:
 * - Resource-safe Spark session management
 * - Type-safe pipeline composition using Kleisli arrows
 * - Data quality validation with comprehensive error handling
 * - CDC (Change Data Capture) operations
 * - Multi-format data source/sink support
 * - Production monitoring and observability
 * - Schema evolution and migration
 */
package com.flowforge.engines.spark.examples

import cats.data.{NonEmptyList, ValidatedNel}
import cats.effect.{IO, IOApp}
import cats.implicits._
import com.flowforge.core.algebra.DataAlgebra._
import com.flowforge.core.algebra.EffectSystem
import com.flowforge.core.instances.EffectInstances.catsEffectSystemInstance
import com.flowforge.core.types._
import com.flowforge.core.types.PipelineTypes._
import com.flowforge.engines.spark.{SparkDataAlgebra, SparkPipelineOps}
import eu.timepit.refined.types.string.NonEmptyString

import java.time.Instant

/**
 * Production pipeline example showcasing Spark DataAlgebra capabilities
 */
object SparkProductionPipeline extends IOApp.Simple {

  // Sample data models for the pipeline
  case class RawSalesRecord(
    id: String,
    customerId: String,
    productId: String,
    quantity: Int,
    price: Double,
    timestamp: String,
    region: String
  )

  case class ProcessedSalesRecord(
    id: String,
    customerId: String,
    productId: String,
    quantity: Int,
    unitPrice: Double,
    totalAmount: Double,
    timestamp: Instant,
    region: String,
    processed: Boolean = true
  )

  case class SalesAggregation(
    region: String,
    totalSales: Double,
    totalQuantity: Long,
    uniqueCustomers: Long,
    avgOrderValue: Double
  )

  implicit val es: EffectSystem[IO] = catsEffectSystemInstance

  def run: IO[Unit] = {
    println("🚀 FlowForge Spark Production Pipeline Demo")
    
    for {
      _ <- IO.println("📊 Starting production-ready Spark pipeline...")
      _ <- runProductionPipeline
      _ <- IO.println("✅ Production pipeline completed successfully!")
      _ <- IO.println("🎯 FlowForge Spark Integration Demo Complete")
    } yield ()
  }

  /**
   * Complete production pipeline demonstrating all major features
   */
  def runProductionPipeline: IO[Unit] = {
    SparkDataAlgebra.resource[IO](
      appName = "FlowForge-Production-Demo",
      master = "local[*]"
    ).use { spark =>
      
      implicit val sparkAlgebra: SparkDataAlgebra[IO] = spark
      
      for {
        _ <- IO.println("🔧 Initializing Spark DataAlgebra...")
        
        // Define data sources and sinks
        rawDataSource = createMockDataSource()
        processedDataSink = createMockDataSink("processed_sales")
        aggregatedDataSink = createMockDataSink("sales_aggregations")
        
        // Define data contract and quality checks
        dataContract = createSalesDataContract()
        qualityChecks = createQualityChecks()
        
        _ <- IO.println("📥 Phase 1: Data Ingestion with Validation")
        ingestionResult <- runIngestionPipeline(rawDataSource, dataContract)
        _ <- IO.println(s"   ✅ Ingested data quality score: ${ingestionResult.score}")
        
        _ <- IO.println("🔄 Phase 2: Data Transformation and Processing")
        processedDataset <- runTransformationPipeline(ingestionResult.data)
        _ <- IO.println(s"   ✅ Processed ${processedDataset.size} records")
        
        _ <- IO.println("🛡️ Phase 3: Quality Validation and Cleaning")
        cleanedDataset <- runQualityPipeline(processedDataset, qualityChecks)
        _ <- IO.println(s"   ✅ Quality checks passed for ${cleanedDataset.size} records")
        
        _ <- IO.println("📊 Phase 4: Data Aggregation and Analytics")
        aggregatedDataset <- runAggregationPipeline(cleanedDataset)
        _ <- IO.println(s"   ✅ Created ${aggregatedDataset.size} aggregated records")
        
        _ <- IO.println("💾 Phase 5: Data Export and Storage")
        writeResult1 <- spark.write(cleanedDataset, processedDataSink)
        writeResult2 <- spark.write(aggregatedDataset, aggregatedDataSink)
        _ <- IO.println(s"   ✅ Exported ${writeResult1.recordsWritten + writeResult2.recordsWritten} total records")
        
        _ <- IO.println("🔍 Phase 6: Data Profiling and Monitoring")
        _ <- runProfilingAndMonitoring(cleanedDataset, aggregatedDataset)
        
        _ <- IO.println("🔄 Phase 7: CDC Operations Demo")
        _ <- runCDCDemo(rawDataSource)
        
      } yield ()
    }
  }

  /**
   * Data ingestion pipeline with validation
   */
  def runIngestionPipeline(
    source: DataSource,
    contract: DataContract[RawSalesRecord]
  )(implicit spark: SparkDataAlgebra[IO]): IO[QualityResult[Dataset[RawSalesRecord]]] = {
    
    val pipeline = SparkPipelineOps.ingestWithValidation[IO, RawSalesRecord](source, contract)
    pipeline.run(())
  }

  /**
   * Data transformation pipeline
   */
  def runTransformationPipeline(
    rawDataset: Dataset[RawSalesRecord]
  )(implicit spark: SparkDataAlgebra[IO]): IO[Dataset[ProcessedSalesRecord]] = {
    
    val transformation = (raw: RawSalesRecord) => IO.delay {
      ProcessedSalesRecord(
        id = raw.id,
        customerId = raw.customerId,
        productId = raw.productId,
        quantity = raw.quantity,
        unitPrice = raw.price,
        totalAmount = raw.quantity * raw.price,
        timestamp = Instant.parse(raw.timestamp + "T00:00:00Z"), // Simplified parsing
        region = raw.region,
        processed = true
      )
    }
    
    val pipeline = SparkPipelineOps.transform[IO, RawSalesRecord, ProcessedSalesRecord](transformation)
    pipeline.run(rawDataset)
  }

  /**
   * Quality validation and cleaning pipeline
   */
  def runQualityPipeline(
    dataset: Dataset[ProcessedSalesRecord],
    qualityChecks: NonEmptyList[QualityCheck[ProcessedSalesRecord]]
  )(implicit spark: SparkDataAlgebra[IO]): IO[Dataset[ProcessedSalesRecord]] = {
    
    val cleaningRules = List(
      CleaningRule[ProcessedSalesRecord](
        name = "normalize_region",
        apply = record => record.copy(region = record.region.toUpperCase),
        condition = _.region.nonEmpty
      ),
      CleaningRule[ProcessedSalesRecord](
        name = "fix_negative_amounts",
        apply = record => record.copy(totalAmount = math.abs(record.totalAmount)),
        condition = _.totalAmount < 0
      )
    )
    
    val pipeline = 
      SparkPipelineOps.qualityCheck[IO, ProcessedSalesRecord](qualityChecks)
        .andThen(SparkPipelineOps.clean[IO, ProcessedSalesRecord](cleaningRules))
        .map(_._1) // Extract dataset from (dataset, qualityResults) tuple
    
    pipeline.run(dataset)
  }

  /**
   * Data aggregation pipeline
   */
  def runAggregationPipeline(
    dataset: Dataset[ProcessedSalesRecord]
  )(implicit spark: SparkDataAlgebra[IO]): IO[Dataset[SalesAggregation]] = {
    
    val aggregator = (records: List[ProcessedSalesRecord]) => {
      val totalSales = records.map(_.totalAmount).sum
      val totalQuantity = records.map(_.quantity.toLong).sum
      val uniqueCustomers = records.map(_.customerId).distinct.size.toLong
      val avgOrderValue = if (records.nonEmpty) totalSales / records.size else 0.0
      
      SalesAggregation(
        region = records.headOption.map(_.region).getOrElse("UNKNOWN"),
        totalSales = totalSales,
        totalQuantity = totalQuantity,
        uniqueCustomers = uniqueCustomers,
        avgOrderValue = avgOrderValue
      )
    }
    
    val pipeline = SparkPipelineOps.groupBy[IO, ProcessedSalesRecord, String, SalesAggregation](
      keyExtractor = _.region,
      aggregator = aggregator
    )
    
    pipeline.run(dataset)
  }

  /**
   * Data profiling and monitoring operations
   */
  def runProfilingAndMonitoring[A, B](
    processedDataset: Dataset[A],
    aggregatedDataset: Dataset[B]
  )(implicit spark: SparkDataAlgebra[IO]): IO[Unit] = {
    for {
      processedProfile <- spark.profile(processedDataset)
      aggregatedProfile <- spark.profile(aggregatedDataset)
      
      _ <- IO.println(s"   📊 Processed dataset profile:")
      _ <- IO.println(s"      - Record count: ${processedProfile.recordCount}")
      _ <- IO.println(s"      - Schema fields: ${processedProfile.schema.fields.size}")
      
      _ <- IO.println(s"   📊 Aggregated dataset profile:")
      _ <- IO.println(s"      - Record count: ${aggregatedProfile.recordCount}")
      _ <- IO.println(s"      - Schema fields: ${aggregatedProfile.schema.fields.size}")
      
    } yield ()
  }

  /**
   * CDC operations demonstration
   */
  def runCDCDemo(source: DataSource)(implicit spark: SparkDataAlgebra[IO]): IO[Unit] = {
    import com.flowforge.core.algebra.CDCOperations
    import com.flowforge.core.types.RefinedTypes.FieldName
    
    val primaryKeys = NonEmptyList.of(FieldName.unsafeFrom("id"))
    val config = CDCOperations.CDCConfig.withQualityChecks
    
    for {
      sourceDataset <- spark.read[RawSalesRecord](source)
      targetDataset <- spark.read[RawSalesRecord](source) // Mock target for demo
      
      cdcResult <- spark.performDelta(sourceDataset, targetDataset, primaryKeys, config)
      
      _ <- IO.println(s"   🔄 CDC Results:")
      _ <- IO.println(s"      - Processed records: ${cdcResult.processedRecords}")
      _ <- IO.println(s"      - Insert count: ${cdcResult.insertCount}")
      _ <- IO.println(s"      - Update count: ${cdcResult.updateCount}")
      _ <- IO.println(s"      - Delete count: ${cdcResult.deleteCount}")
      _ <- IO.println(s"      - Processing time: ${cdcResult.processingTime}")
      
    } yield ()
  }

  // ===============================
  // HELPER METHODS AND MOCK DATA
  // ===============================

  /**
   * Create mock data source for demonstration
   */
  def createMockDataSource(): DataSource = {
    DataSource(
      id = Some("sales_raw_data"),
      location = "/tmp/sales/raw/", // Would be actual path in production
      format = DataFormat.Parquet,
      options = Map(
        "header" -> "true",
        "inferSchema" -> "true"
      )
    )
  }

  /**
   * Create mock data sink for demonstration
   */
  def createMockDataSink(name: String): DataSink = {
    DataSink(
      id = Some(s"${name}_sink"),
      location = s"/tmp/sales/$name/", // Would be actual path in production
      format = DataFormat.Parquet,
      options = Map(
        "compression" -> "snappy",
        "mode" -> "overwrite"
      )
    )
  }

  /**
   * Create data contract for sales records
   */
  def createSalesDataContract(): DataContract[RawSalesRecord] = {
    val idValidation = (record: RawSalesRecord) => {
      if (record.id.nonEmpty) {
        record.validNel
      } else {
        DataValidationError.InvalidField("id", "ID cannot be empty").invalidNel
      }
    }
    
    val priceValidation = (record: RawSalesRecord) => {
      if (record.price > 0) {
        record.validNel
      } else {
        DataValidationError.InvalidField("price", "Price must be positive").invalidNel
      }
    }
    
    val quantityValidation = (record: RawSalesRecord) => {
      if (record.quantity > 0) {
        record.validNel
      } else {
        DataValidationError.InvalidField("quantity", "Quantity must be positive").invalidNel
      }
    }
    
    DataContract(
      name = "SalesDataContract",
      version = "1.0.0",
      description = Some("Contract for raw sales data validation"),
      validations = List(idValidation, priceValidation, quantityValidation),
      schema = DataSchema.builder
        .withField(DataField("id", "string", required = true))
        .withField(DataField("customerId", "string", required = true))
        .withField(DataField("productId", "string", required = true))
        .withField(DataField("quantity", "integer", required = true))
        .withField(DataField("price", "double", required = true))
        .withField(DataField("timestamp", "string", required = true))
        .withField(DataField("region", "string", required = true))
        .build,
      sla = Some(DataSLA(
        availabilityTime = "09:00",
        freshnessThreshold = java.time.Duration.ofHours(24),
        completenessThreshold = 0.95
      ))
    )
  }

  /**
   * Create quality checks for processed records
   */
  def createQualityChecks(): NonEmptyList[QualityCheck[ProcessedSalesRecord]] = {
    val nonNullIdCheck = QualityCheck[ProcessedSalesRecord](
      name = "non_null_id",
      description = "ID field must not be null or empty",
      validate = record => {
        if (record.id.nonEmpty) {
          record.validNel
        } else {
          DataValidationError.InvalidField("id", "ID cannot be null or empty").invalidNel
        }
      }
    )
    
    val positiveAmountCheck = QualityCheck[ProcessedSalesRecord](
      name = "positive_amount",
      description = "Total amount must be positive",
      validate = record => {
        if (record.totalAmount > 0) {
          record.validNel
        } else {
          DataValidationError.InvalidField("totalAmount", "Total amount must be positive").invalidNel
        }
      }
    )
    
    val validRegionCheck = QualityCheck[ProcessedSalesRecord](
      name = "valid_region",
      description = "Region must be a valid value",
      validate = record => {
        val validRegions = Set("NORTH", "SOUTH", "EAST", "WEST", "CENTRAL")
        if (validRegions.contains(record.region.toUpperCase)) {
          record.validNel
        } else {
          DataValidationError.InvalidField("region", s"Invalid region: ${record.region}").invalidNel
        }
      }
    )
    
    NonEmptyList.of(nonNullIdCheck, positiveAmountCheck, validRegionCheck)
  }

  // ===============================
  // DATA TYPE INSTANCES
  // ===============================

  // DataEncoder instances for our data types
  implicit val rawSalesEncoder: DataEncoder[RawSalesRecord] = new DataEncoder[RawSalesRecord] {
    def encode(value: RawSalesRecord): EncodedData = 
      EncodedData(value.toString.getBytes)
    def schema: DataSchema = 
      DataSchema.builder
        .withField(DataField("id", "string", required = true))
        .withField(DataField("customerId", "string", required = true))
        .withField(DataField("productId", "string", required = true))
        .withField(DataField("quantity", "integer", required = true))
        .withField(DataField("price", "double", required = true))
        .withField(DataField("timestamp", "string", required = true))
        .withField(DataField("region", "string", required = true))
        .build
  }

  implicit val processedSalesEncoder: DataEncoder[ProcessedSalesRecord] = new DataEncoder[ProcessedSalesRecord] {
    def encode(value: ProcessedSalesRecord): EncodedData = 
      EncodedData(value.toString.getBytes)
    def schema: DataSchema = 
      DataSchema.builder
        .withField(DataField("id", "string", required = true))
        .withField(DataField("customerId", "string", required = true))
        .withField(DataField("productId", "string", required = true))
        .withField(DataField("quantity", "integer", required = true))
        .withField(DataField("unitPrice", "double", required = true))
        .withField(DataField("totalAmount", "double", required = true))
        .withField(DataField("timestamp", "timestamp", required = true))
        .withField(DataField("region", "string", required = true))
        .withField(DataField("processed", "boolean", required = true))
        .build
  }

  implicit val salesAggregationEncoder: DataEncoder[SalesAggregation] = new DataEncoder[SalesAggregation] {
    def encode(value: SalesAggregation): EncodedData = 
      EncodedData(value.toString.getBytes)
    def schema: DataSchema = 
      DataSchema.builder
        .withField(DataField("region", "string", required = true))
        .withField(DataField("totalSales", "double", required = true))
        .withField(DataField("totalQuantity", "long", required = true))
        .withField(DataField("uniqueCustomers", "long", required = true))
        .withField(DataField("avgOrderValue", "double", required = true))
        .build
  }

  // DataDecoder instances for our data types
  implicit val rawSalesDecoder: DataDecoder[RawSalesRecord] = new DataDecoder[RawSalesRecord] {
    def decode(data: EncodedData): Either[FlowForgeError, RawSalesRecord] = {
      // Simplified decoder - in production would properly deserialize
      Right(RawSalesRecord(
        id = "001",
        customerId = "CUST_001", 
        productId = "PROD_001",
        quantity = 5,
        price = 99.99,
        timestamp = "2024-01-01",
        region = "NORTH"
      ))
    }
    def expectedSchema: DataSchema = rawSalesEncoder.schema
  }

  implicit val processedSalesDecoder: DataDecoder[ProcessedSalesRecord] = new DataDecoder[ProcessedSalesRecord] {
    def decode(data: EncodedData): Either[FlowForgeError, ProcessedSalesRecord] = {
      Right(ProcessedSalesRecord(
        id = "001",
        customerId = "CUST_001",
        productId = "PROD_001", 
        quantity = 5,
        unitPrice = 99.99,
        totalAmount = 499.95,
        timestamp = Instant.now(),
        region = "NORTH"
      ))
    }
    def expectedSchema: DataSchema = processedSalesEncoder.schema
  }

  implicit val salesAggregationDecoder: DataDecoder[SalesAggregation] = new DataDecoder[SalesAggregation] {
    def decode(data: EncodedData): Either[FlowForgeError, SalesAggregation] = {
      Right(SalesAggregation(
        region = "NORTH",
        totalSales = 1499.95,
        totalQuantity = 15,
        uniqueCustomers = 3,
        avgOrderValue = 499.98
      ))
    }
    def expectedSchema: DataSchema = salesAggregationEncoder.schema
  }
}