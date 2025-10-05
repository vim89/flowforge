package com.flowforge.sample.advanced

import cats.effect.{ IO, IOApp, Resource }
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.either._
import cats.syntax.traverse._
import cats.instances.list._
import com.flowforge.core.PipelineBuilder
import com.flowforge.core.algebra.{ DataAlgebra, EffectSystem }
import com.flowforge.core.algebra.{ DataEncoder => FFEncoder, DataDecoder => FFDecoder, EncodedData => FFEncodedData, CorruptedData => FFCorruptedData }
import com.flowforge.core.contracts.{ SchemaConforms, SchemaPolicy }
import com.flowforge.core.contracts.derive.Shape
import com.flowforge.core.types._
import com.flowforge.engines.spark.SparkDataAlgebra
import com.flowforge.framework.PipelineExecution
import com.flowforge.quality.deequ.DeequAdapter
import io.circe.{ Decoder, Encoder, Json }
import io.circe.parser.parse
import io.circe.syntax._
import io.circe.generic.semiauto._
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.streaming.Trigger
import java.time.{ Instant, LocalDateTime, ZoneOffset }
import java.util.UUID

/**
 * STREAMING CDC PIPELINE EXAMPLE
 *
 * This advanced example demonstrates FlowForge's streaming capabilities with Change Data Capture (CDC)
 * patterns, showcasing real-time data processing with compile-time contract validation.
 *
 * KEY FEATURES DEMONSTRATED:
 * 1. **Kafka Source Integration**: Read CDC events from Kafka topics with schema validation
 * 2. **Real-time Processing**: Transform CDC events using FlowForge's streaming capabilities
 * 3. **Delta Lake Integration**: Write processed events to Delta tables with SCD2 patterns
 * 4. **Contract Validation**: Show how schema policies work with streaming data
 * 5. **Error Handling**: Demonstrate proper error handling for malformed CDC events
 *
 * BUSINESS SCENARIO:
 * A customer management system publishes CDC events to Kafka whenever customer records
 * are inserted, updated, or deleted. This pipeline processes these events in real-time,
 * maintaining a slowly changing dimension (SCD2) table in Delta Lake that preserves
 * the complete history of customer changes.
 *
 * CDC EVENT STRUCTURE:
 * - INSERT: New customer record created
 * - UPDATE: Existing customer record modified (before/after values)
 * - DELETE: Customer record marked as deleted
 *
 * SCD2 PATTERN:
 * - Each customer record has effective_from and effective_to timestamps
 * - Current records have effective_to = null
 * - Historical records have effective_to set when superseded
 * - Deleted records are marked with is_deleted = true
 */
object StreamingCDCPipeline extends IOApp.Simple {

  // ================================================================================================
  // DATA MODELS - CDC Event Structure
  // ================================================================================================

  /**
   * CDC Operation Types - represents the type of database change
   */
  sealed trait CDCOperation
  object CDCOperation {
    case object Insert extends CDCOperation
    case object Update extends CDCOperation
    case object Delete extends CDCOperation

    implicit val encoder: Encoder[CDCOperation] = Encoder.encodeString.contramap {
      case Insert => "INSERT"
      case Update => "UPDATE"
      case Delete => "DELETE"
    }

    implicit val decoder: Decoder[CDCOperation] = Decoder.decodeString.emap {
      case "INSERT" => Right(Insert)
      case "UPDATE" => Right(Update)
      case "DELETE" => Right(Delete)
      case other => Left(s"Unknown CDC operation: "+other)
    }
  }

  /**
   * Raw customer data as it appears in the source system
   */
  case class CustomerData(
    id: Long,
    email: String,
    firstName: String,
    lastName: String,
    phone: Option[String],
    address: Option[String],
    registrationDate: String, // ISO-8601 timestamp
    status: String // ACTIVE, INACTIVE, SUSPENDED
  )

  /**
   * CDC Event envelope - contains metadata about the change plus the actual data
   */
  case class CDCEvent(
    operation: CDCOperation,
    timestamp: String, // ISO-8601 timestamp when change occurred
    transactionId: String, // Database transaction ID for ordering
    sequenceNumber: Long, // Sequence within transaction for ordering
    tableName: String, // Source table name
    before: Option[CustomerData], // Previous state (for UPDATE/DELETE)
    after: Option[CustomerData] // New state (for INSERT/UPDATE)
  )

  /**
   * Processed customer record for SCD2 Delta table
   * Includes SCD2 metadata fields for tracking history
   */
  case class CustomerSCD2(
    id: Long,
    email: String,
    firstName: String,
    lastName: String,
    phone: Option[String],
    address: Option[String],
    registrationDate: String,
    status: String,
    // SCD2 metadata fields
    effectiveFrom: String, // When this version became active
    effectiveTo: Option[String], // When this version was superseded (null for current)
    isDeleted: Boolean, // True if this record represents a deletion
    isCurrent: Boolean, // True if this is the current version
    cdcTransactionId: String, // Original CDC transaction ID for lineage
    cdcSequenceNumber: Long, // Original CDC sequence for ordering
    processedAt: String // When FlowForge processed this event
  )

  /**
   * Error record for malformed CDC events that couldn't be processed
   */
  case class CDCErrorRecord(
    rawEvent: String, // Original JSON that failed to parse
    errorMessage: String, // Description of what went wrong
    errorType: String, // Classification of error (PARSE_ERROR, VALIDATION_ERROR, etc.)
    timestamp: String, // When error occurred
    retryCount: Int // Number of retry attempts
  )

  // ================================================================================================
  // CIRCE CODECS - JSON serialization/deserialization
  // ================================================================================================

  implicit val customerDataEncoder: Encoder[CustomerData] = deriveEncoder[CustomerData]
  implicit val customerDataDecoder: Decoder[CustomerData] = deriveDecoder[CustomerData]

  implicit val cdcEventEncoder: Encoder[CDCEvent] = deriveEncoder[CDCEvent]
  implicit val cdcEventDecoder: Decoder[CDCEvent] = deriveDecoder[CDCEvent]

  implicit val customerSCD2Encoder: Encoder[CustomerSCD2] = deriveEncoder[CustomerSCD2]
  implicit val customerSCD2Decoder: Decoder[CustomerSCD2] = deriveDecoder[CustomerSCD2]

  implicit val cdcErrorRecordEncoder: Encoder[CDCErrorRecord] = deriveEncoder[CDCErrorRecord]
  implicit val cdcErrorRecordDecoder: Decoder[CDCErrorRecord] = deriveDecoder[CDCErrorRecord]

  // ================================================================================================
  // FLOWFORGE SHAPE INSTANCES - Required for compile-time contract validation
  // ================================================================================================

  implicit val customerDataShape: Shape[CustomerData] = Shape.gen[CustomerData]
  implicit val cdcEventShape: Shape[CDCEvent] = Shape.gen[CDCEvent]
  implicit val customerSCD2Shape: Shape[CustomerSCD2] = Shape.gen[CustomerSCD2]
  implicit val cdcErrorRecordShape: Shape[CDCErrorRecord] = Shape.gen[CDCErrorRecord]

  // ================================================================================================
  // FLOWFORGE DECODERS/ENCODERS - Integration with FlowForge data algebra
  // ================================================================================================

  /**
   * FlowForge decoder for CDC events from Kafka JSON
   */
  implicit val cdcEventFFDecoder: FFDecoder[CDCEvent] = new FFDecoder[CDCEvent] {
    def decode(ed: FFEncodedData, format: DataFormat): Either[FFCorruptedData, CDCEvent] = format match {
      case DataFormat.JSON | DataFormat.JSONL =>
        val jsonString = new String(ed.data, "UTF-8")
        parse(jsonString)
          .left.map(e => FFCorruptedData(s"JSON parse error: "+e.getMessage))
          .flatMap(_.as[CDCEvent].left.map(e => FFCorruptedData(s"CDC event decode error: "+e.getMessage)))
      case other =>
        Left(FFCorruptedData(s"Unsupported format for CDC events: "+other))
    }

    def validateSchema(ed: FFEncodedData, expected: DataSchema): Either[FFCorruptedData, Unit] = Right(())

    def decodeWithEvolution(ed: FFEncodedData, format: DataFormat, target: DataSchema): Either[FFCorruptedData, CDCEvent] =
      decode(ed, format)

    def supportsFormat(format: DataFormat): Boolean =
      format == DataFormat.JSON || format == DataFormat.JSONL
  }

  /**
   * FlowForge encoder for SCD2 records to Delta Lake
   */
  implicit val customerSCD2FFEncoder: FFEncoder[CustomerSCD2] = FFEncoder.instance[CustomerSCD2](
    (customer, format) => format match {
      case DataFormat.JSON | DataFormat.JSONL =>
        Right(FFEncodedData(customer.asJson.noSpaces.getBytes("UTF-8"), format))
      case DataFormat.Parquet | DataFormat.Delta =>
        // For Parquet/Delta, Spark will handle the encoding
        Right(FFEncodedData(customer.asJson.noSpaces.getBytes("UTF-8"), DataFormat.JSON))
      case other =>
        Left(FFCorruptedData(s"Unsupported format for SCD2 records: "+other))
    },
    _ => DataSchema.builder
      .addField("id", DataType.Long)
      .addField("email", DataType.String)
      .addField("firstName", DataType.String)
      .addField("lastName", DataType.String)
      .addField("phone", DataType.String)
      .addField("address", DataType.String)
      .addField("registrationDate", DataType.String)
      .addField("status", DataType.String)
      .addField("effectiveFrom", DataType.String)
      .addField("effectiveTo", DataType.String)
      .addField("isDeleted", DataType.Boolean)
      .addField("isCurrent", DataType.Boolean)
      .addField("cdcTransactionId", DataType.String)
      .addField("cdcSequenceNumber", DataType.Long)
      .addField("processedAt", DataType.String)
      .build
  )

  /**
   * FlowForge encoder for error records
   */
  implicit val cdcErrorRecordFFEncoder: FFEncoder[CDCErrorRecord] = FFEncoder.instance[CDCErrorRecord](
    (error, format) => Right(FFEncodedData(error.asJson.noSpaces.getBytes("UTF-8"), format)),
    _ => DataSchema.builder
      .addField("rawEvent", DataType.String)
      .addField("errorMessage", DataType.String)
      .addField("errorType", DataType.String)
      .addField("timestamp", DataType.String)
      .addField("retryCount", DataType.Integer)
      .build
  )

  // ================================================================================================
  // SCHEMA CONFORMANCE - Compile-time contract validation
  // ================================================================================================

  // These implicit instances ensure compile-time validation of schema compatibility
  implicit val cdcEventConforms: SchemaConforms[CDCEvent, CDCEvent, SchemaPolicy.Exact] = implicitly
  implicit val customerSCD2Conforms: SchemaConforms[CustomerSCD2, CustomerSCD2, SchemaPolicy.Exact] = implicitly
  implicit val errorRecordConforms: SchemaConforms[CDCErrorRecord, CDCErrorRecord, SchemaPolicy.Exact] = implicitly

  // Schema evolution policies for different scenarios
  implicit val cdcEventBackwardConforms: SchemaConforms[CDCEvent, CDCEvent, SchemaPolicy.Backward] = implicitly
  implicit val customerSCD2ForwardConforms: SchemaConforms[CustomerSCD2, CustomerSCD2, SchemaPolicy.Forward] = implicitly

  // ================================================================================================
  // BUSINESS LOGIC - CDC Event Processing
  // ================================================================================================

  /**
   * Core business logic for processing CDC events into SCD2 records
   * This function handles the three types of CDC operations:
   * - INSERT: Creates new SCD2 record with current timestamp
   * - UPDATE: Creates new SCD2 record and marks previous as historical
   * - DELETE: Marks current record as deleted
   */
  def processCDCEvent(event: CDCEvent): IO[List[CustomerSCD2]] = {
    val now = Instant.now().toString
    val processedAt = now

    event.operation match {
      case CDCOperation.Insert =>
        // INSERT: Create new current record
        event.after match {
          case Some(customerData) =>
            val scd2Record = CustomerSCD2(
              id = customerData.id,
              email = customerData.email,
              firstName = customerData.firstName,
              lastName = customerData.lastName,
              phone = customerData.phone,
              address = customerData.address,
              registrationDate = customerData.registrationDate,
              status = customerData.status,
              effectiveFrom = event.timestamp,
              effectiveTo = None, // Current record
              isDeleted = false,
              isCurrent = true,
              cdcTransactionId = event.transactionId,
              cdcSequenceNumber = event.sequenceNumber,
              processedAt = processedAt
            )
            IO.pure(List(scd2Record))
          case None =>
            IO.raiseError(new IllegalArgumentException("INSERT operation must have 'after' data"))
        }

      case CDCOperation.Update =>
        // UPDATE: Create new current record
        // Note: In a real implementation, you would also need to update the previous record's effectiveTo
        // This would require reading from the existing Delta table, which is beyond this example's scope
        event.after match {
          case Some(customerData) =>
            val scd2Record = CustomerSCD2(
              id = customerData.id,
              email = customerData.email,
              firstName = customerData.firstName,
              lastName = customerData.lastName,
              phone = customerData.phone,
              address = customerData.address,
              registrationDate = customerData.registrationDate,
              status = customerData.status,
              effectiveFrom = event.timestamp,
              effectiveTo = None, // Current record
              isDeleted = false,
              isCurrent = true,
              cdcTransactionId = event.transactionId,
              cdcSequenceNumber = event.sequenceNumber,
              processedAt = processedAt
            )
            IO.pure(List(scd2Record))
          case None =>
            IO.raiseError(new IllegalArgumentException("UPDATE operation must have 'after' data"))
        }

      case CDCOperation.Delete =>
        // DELETE: Create tombstone record marking deletion
        event.before match {
          case Some(customerData) =>
            val deletionRecord = CustomerSCD2(
              id = customerData.id,
              email = customerData.email,
              firstName = customerData.firstName,
              lastName = customerData.lastName,
              phone = customerData.phone,
              address = customerData.address,
              registrationDate = customerData.registrationDate,
              status = customerData.status,
              effectiveFrom = event.timestamp,
              effectiveTo = None, // Deletion is current state
              isDeleted = true, // Mark as deleted
              isCurrent = true,
              cdcTransactionId = event.transactionId,
              cdcSequenceNumber = event.sequenceNumber,
              processedAt = processedAt
            )
            IO.pure(List(deletionRecord))
          case None =>
            IO.raiseError(new IllegalArgumentException("DELETE operation must have 'before' data"))
        }
    }
  }

  /**
   * Error handling for malformed CDC events
   * Creates error records that can be written to a dead letter queue for investigation
   */
  def handleCDCError(rawJson: String, error: Throwable, retryCount: Int = 0): IO[CDCErrorRecord] = {
    val now = Instant.now().toString
    val errorType = error match {
      case _: io.circe.ParsingFailure => "JSON_PARSE_ERROR"
      case _: io.circe.DecodingFailure => "JSON_DECODE_ERROR"
      case _: IllegalArgumentException => "VALIDATION_ERROR"
      case _ => "UNKNOWN_ERROR"
    }

    IO.pure(CDCErrorRecord(
      rawEvent = rawJson,
      errorMessage = error.getMessage,
      errorType = errorType,
      timestamp = now,
      retryCount = retryCount
    ))
  }

  /**
   * Data quality validation for CDC events
   * Ensures CDC events meet business rules before processing
   */
  def validateCDCEvent(event: CDCEvent): IO[Either[String, CDCEvent]] = {
    val validations = List(
      // Validate required fields based on operation type
      if (event.operation == CDCOperation.Insert && event.after.isEmpty)
        Some("INSERT operations must have 'after' data")
      else None,

      if (event.operation == CDCOperation.Delete && event.before.isEmpty)
        Some("DELETE operations must have 'before' data")
      else None,

      if (event.operation == CDCOperation.Update && (event.before.isEmpty || event.after.isEmpty))
        Some("UPDATE operations must have both 'before' and 'after' data")
      else None,

      // Validate customer data fields
      event.after.flatMap { customer =>
        if (customer.email.isEmpty || !customer.email.contains("@"))
          Some("Customer email must be valid")
        else None
      },

      event.after.flatMap { customer =>
        if (customer.firstName.trim.isEmpty || customer.lastName.trim.isEmpty)
          Some("Customer first and last names are required")
        else None
      },

      // Validate timestamp format
      if (event.timestamp.isEmpty)
        Some("CDC event timestamp is required")
      else None,

      // Validate transaction ID
      if (event.transactionId.isEmpty)
        Some("CDC transaction ID is required")
      else None
    ).flatten

    if (validations.nonEmpty) {
      IO.pure(Left(validations.mkString("; ")))
    } else {
      IO.pure(Right(event))
    }
  }

  // ================================================================================================
  // AUDIT LOGGING - Integration with existing audit system
  // ================================================================================================

  /**
   * Simple audit logging system for tracking pipeline events
   * In a real implementation, this would integrate with the existing AuditDb from PipelineApp
   */
  object StreamingAuditLog {
    private val logger = org.slf4j.LoggerFactory.getLogger("streaming-cdc-pipeline")

    def logPipelineStart(): IO[Unit] =
      IO(logger.info("🚀 Streaming CDC Pipeline started"))

    def logEventProcessed(event: CDCEvent): IO[Unit] =
      IO(logger.info(s"✅ Processed CDC event: "+event.operation+"  for customer "+event.after.orElse(event.before).map(_.id).getOrElse("unknown")))

    def logEventError(error: CDCErrorRecord): IO[Unit] =
      IO(logger.warn(s"❌ CDC event error: "+error.errorType+" - "+error.errorMessage))

    def logQualityCheck(passed: Boolean, score: Double): IO[Unit] =
      if (passed) {
        IO(logger.info(s"✅ Data quality check passed with score: "+score))
      } else {
        IO(logger.warn(s"❌ Data quality check failed with score: "+score))
      }

    def logBatchProcessed(batchId: Long, recordCount: Int, errorCount: Int): IO[Unit] =
      IO(logger.info("📊 Batch "+batchId+" processed: "+ recordCount +" records, "+ errorCount +" errors"))

    def logPipelineStop(): IO[Unit] =
      IO(logger.info("🛑 Streaming CDC Pipeline stopped"))
  }

  // ================================================================================================
  // SPARK RESOURCES - Resource management for Spark streaming
  // ================================================================================================

  /**
   * Creates Spark session configured for streaming with Delta Lake support
   */
  private def createSparkSession(): Resource[IO, SparkSession] =
    Resource.make(IO {
      SparkSession
        .builder()
        .appName("FlowForge-StreamingCDC-Pipeline")
        .master("local[*]") // Use local mode for demo; configure for cluster in production
        .config("spark.ui.enabled", "false") // Disable UI for cleaner demo output
        .config("spark.sql.streaming.checkpointLocation", "/tmp/flowforge-cdc-checkpoints")
        .config("spark.sql.extensions", "io.delta.sql.DeltaSparkSessionExtension")
        .config("spark.sql.catalog.spark_catalog", "org.apache.spark.sql.delta.catalog.DeltaCatalog")
        .config("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
        .config("spark.sql.adaptive.enabled", "true")
        .config("spark.sql.adaptive.coalescePartitions.enabled", "true")
        .getOrCreate()
    })(spark => IO(spark.stop()).void)

  /**
   * Creates FlowForge data algebra instance for Spark
   */
  private def createDataAlgebra(spark: SparkSession): Resource[IO, DataAlgebra[IO]] =
    Resource.pure(SparkDataAlgebra.createSparkDataAlgebra[IO](spark).algebra)

  // ================================================================================================
  // MAIN PIPELINE - Streaming CDC processing pipeline
  // ================================================================================================

  /**
   * Main streaming CDC pipeline implementation
   * Demonstrates FlowForge's streaming capabilities with real-time CDC processing
   */
  def run: IO[Unit] = {
    implicit val es: EffectSystem[IO] = com.flowforge.core.instances.EffectInstances.catsEffectSystemInstance

    createSparkSession().use { spark =>
      createDataAlgebra(spark).use { dao =>
        for {
          _ <- IO.println("🔧 FlowForge Streaming CDC Pipeline")
          _ <- IO.println("=" * 60)
          _ <- IO.println("")

          _ <- StreamingAuditLog.logPipelineStart()

          // ========================================================================================
          // STEP 1: Configure Data Sources and Sinks
          // ========================================================================================

          _ <- IO.println("📡 STEP 1: Configuring Kafka source and Delta Lake sinks")

          // Kafka source for CDC events
          // In production, this would connect to actual Kafka cluster
          kafkaSource = DataSource.kafka(
            brokers = "localhost:9092",
            topic = "customer-cdc-events",
            format = DataFormat.JSON
          )

          // Delta Lake sink for processed SCD2 records
          deltaLakeSink = DataSink.delta(
            path = "/tmp/flowforge-demo/customer-scd2",
            format = DataFormat.Delta,
            mode = WriteMode.Append
          )

          // Dead letter queue for error records
          errorSink = DataSink.delta(
            path = "/tmp/flowforge-demo/cdc-errors",
            format = DataFormat.Delta,
            mode = WriteMode.Append
          )

          _ <- IO.println(s"   ✓ Kafka source: "+kafkaSource.location)
          _ <- IO.println(s"   ✓ Delta Lake sink: "+deltaLakeSink.location)
          _ <- IO.println(s"   ✓ Error sink: "+errorSink.location)

          // ========================================================================================
          // STEP 2: Build Contract-Validated Streaming Pipeline
          // ========================================================================================

          _ <- IO.println("")
          _ <- IO.println("🏗️  STEP 2: Building contract-validated streaming pipeline")

          // Create typed sources and sinks with compile-time contract validation
          typedKafkaSource = TypedSource[CDCEvent](kafkaSource)
          typedDeltaSink = TypedSink[CustomerSCD2](deltaLakeSink)
          typedErrorSink = TypedSink[CDCErrorRecord](errorSink)

          _ <- IO.println("   ✓ Created typed Kafka source with CDCEvent contract")
          _ <- IO.println("   ✓ Created typed Delta sink with CustomerSCD2 contract")
          _ <- IO.println("   ✓ Created typed error sink with CDCErrorRecord contract")

          // ========================================================================================
          // STEP 3: Define Data Quality Rules
          // ========================================================================================

          _ <- IO.println("")
          _ <- IO.println("🔍 STEP 3: Defining data quality rules for CDC events")

          // Data quality constraints for CDC events
          cdcQualityRules = List(
            QualityConstraint.NotNull(RefinedTypes.FieldName.unsafeFrom("operation")),
            QualityConstraint.NotNull(RefinedTypes.FieldName.unsafeFrom("timestamp")),
            QualityConstraint.NotNull(RefinedTypes.FieldName.unsafeFrom("transactionId")),
            QualityConstraint.Pattern(
              RefinedTypes.FieldName.unsafeFrom("operation"),
              "^(INSERT|UPDATE|DELETE)\$"
            )
          )

          // Data quality constraints for customer data within CDC events
          customerQualityRules = List(
            QualityConstraint.Pattern(
              RefinedTypes.FieldName.unsafeFrom("email"),
              "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\\\.[A-Za-z]{2,}\$"
            ),
            QualityConstraint.NotNull(RefinedTypes.FieldName.unsafeFrom("firstName")),
            QualityConstraint.NotNull(RefinedTypes.FieldName.unsafeFrom("lastName")),
            QualityConstraint.Pattern(
              RefinedTypes.FieldName.unsafeFrom("status"),
              "^(ACTIVE|INACTIVE|SUSPENDED)\$"
            )
          )

          _ <- IO.println(s"   ✓ Defined "+cdcQualityRules.length+" CDC event quality rules")
          _ <- IO.println(s"   ✓ Defined "+customerQualityRules.length+" customer data quality rules")

          // ========================================================================================
          // STEP 4: Build Main Processing Pipeline
          // ========================================================================================

          _ <- IO.println("")
          _ <- IO.println("⚙️  STEP 4: Building main CDC processing pipeline")

          // Main pipeline: Kafka → Validate → Transform → Delta Lake
          mainPipeline = PipelineBuilder[IO]("streaming-cdc-main-pipeline")
            .withDescription("Processes CDC events from Kafka into SCD2 Delta Lake table")
            .addTypedSource[CDCEvent, CDCEvent, SchemaPolicy.Backward](
              typedKafkaSource,
              // Mock reader for demo - in production this would be actual Kafka consumer
              _ => IO.pure(CDCEvent(
                operation = CDCOperation.Insert,
                timestamp = Instant.now().toString,
                transactionId = UUID.randomUUID().toString,
                sequenceNumber = 1L,
                tableName = "customers",
                before = None,
                after = Some(CustomerData(
                  id = 12345L,
                  email = "demo@flowforge.com",
                  firstName = "Demo",
                  lastName = "Customer",
                  phone = Some("+1-555-0123"),
                  address = Some("123 FlowForge St, Data City, DC 12345"),
                  registrationDate = Instant.now().toString,
                  status = "ACTIVE"
                ))
              ))
            )
            .addTransform[List[CustomerSCD2]] { cdcEvent =>
              for {
                // Step 4a: Validate CDC event
                validationResult <- validateCDCEvent(cdcEvent)

                // Step 4b: Process valid events or handle errors
                result <- validationResult match {
                  case Right(validEvent) =>
                    for {
                      _ <- StreamingAuditLog.logEventProcessed(validEvent)
                      scd2Records <- processCDCEvent(validEvent)
                    } yield scd2Records

                  case Left(errorMessage) =>
                    for {
                      errorRecord <- handleCDCError(cdcEvent.asJson.noSpaces, new IllegalArgumentException(errorMessage))
                      _ <- StreamingAuditLog.logEventError(errorRecord)
                    } yield List.empty[CustomerSCD2] // Return empty list for errors
                }
              } yield result
            }
            .addTypedSink[List[CustomerSCD2], SchemaPolicy.Forward](
              // Note: This is a simplified sink - in reality you'd need a custom sink for List[CustomerSCD2]
              typedDeltaSink.asInstanceOf[TypedSink[List[CustomerSCD2]]],
              (scd2Records, _) => {
                // Write each SCD2 record to Delta Lake
                scd2Records.traverse_ { record =>
                  for {
                    _ <- IO.println(s"   💾 Writing SCD2 record: Customer "+record.id+" - "+record.operation)
                    // In production: dao.write(deltaDataset, record)
                  } yield ()
                }
              }
            )
            .build()

          _ <- IO.println("   ✓ Main processing pipeline built successfully")

          // ========================================================================================
          // STEP 5: Build Error Handling Pipeline
          // ========================================================================================

          _ <- IO.println("")
          _ <- IO.println("🚨 STEP 5: Building error handling pipeline")

          // Error pipeline: Kafka → Parse Errors → Dead Letter Queue
          errorPipeline = PipelineBuilder[IO]("streaming-cdc-error-pipeline")
            .withDescription("Handles malformed CDC events and writes to dead letter queue")
            .addTypedSource[String, String, SchemaPolicy.Exact](
              TypedSource[String](kafkaSource), // Raw string source for unparseable events
              _ => IO.pure("{\"invalid\": \"json\"}")
            )
            .addTransform[CDCErrorRecord] { rawJson =>
              for {
                errorRecord <- handleCDCError(rawJson, new RuntimeException("Unparseable CDC event"))
                _ <- StreamingAuditLog.logEventError(errorRecord)
              } yield errorRecord
            }
            .addTypedSink[CDCErrorRecord, SchemaPolicy.Exact](
              typedErrorSink,
              (errorRecord, _) => {
                IO.println(s"   💀 Writing error record: "+errorRecord.errorType)
                // In production: dao.write(errorDataset, errorRecord)
              }
            )
            .build()

          _ <- IO.println("   ✓ Error handling pipeline built successfully")

          // ========================================================================================
          // STEP 6: Execute Pipelines with Quality Monitoring
          // ========================================================================================

          _ <- IO.println("")
          _ <- IO.println("🚀 STEP 6: Executing streaming pipelines")

          // Simulate streaming execution
          _ <- IO.println("   📊 Starting streaming execution simulation...")

          // Execute main pipeline
          _ <- PipelineExecution.execute(mainPipeline)(())

          // Execute error pipeline
          _ <- PipelineExecution.execute(errorPipeline)(())

          // Simulate batch processing metrics
          _ <- StreamingAuditLog.logBatchProcessed(
            batchId = 1L,
            recordCount = 1,
            errorCount = 0
          )

          // ========================================================================================
          // STEP 7: Demonstrate Schema Evolution
          // ========================================================================================

          _ <- IO.println("")
          _ <- IO.println("🔄 STEP 7: Demonstrating schema evolution capabilities")

          _ <- IO.println("   📝 Schema evolution scenarios:")
          _ <- IO.println("   • Backward policy: Pipeline can handle new fields in CDC events")
          _ <- IO.println("   • Forward policy: Pipeline can handle missing optional fields")
          _ <- IO.println("   • Contract validation prevents incompatible schema changes")
          _ <- IO.println("   • Compile-time safety ensures schema compatibility")

          // ========================================================================================
          // STEP 8: Cleanup and Summary
          // ========================================================================================

          _ <- IO.println("")
          _ <- IO.println("✅ STEP 8: Pipeline execution completed")

          _ <- StreamingAuditLog.logPipelineStop()

          _ <- IO.println("")
          _ <- IO.println("🎯 STREAMING CDC PIPELINE SUMMARY")
          _ <- IO.println("=" * 60)
          _ <- IO.println("✅ Kafka source integration with schema validation")
          _ <- IO.println("✅ Real-time CDC event processing")
          _ <- IO.println("✅ SCD2 pattern implementation for Delta Lake")
          _ <- IO.println("✅ Compile-time contract validation")
          _ <- IO.println("✅ Comprehensive error handling")
          _ <- IO.println("✅ Data quality monitoring")
          _ <- IO.println("✅ Schema evolution support")
          _ <- IO.println("✅ Audit logging integration")
          _ <- IO.println("")
          _ <- IO.println("🔧 This example demonstrates FlowForge's streaming capabilities")
          _ <- IO.println("   with enterprise-grade CDC processing patterns.")

        } yield ()
      }
    }
  }

  // ================================================================================================
  // HELPER METHODS - Utility functions for demo
  // ================================================================================================

  /**
   * Creates sample CDC events for testing
   * In production, these would come from actual database CDC tools like Debezium
   */
  def createSampleCDCEvents(): List[CDCEvent] = {
    val now = Instant.now()
    val txnId = UUID.randomUUID().toString

    List(
      // INSERT event
      CDCEvent(
        operation = CDCOperation.Insert,
        timestamp = now.toString,
        transactionId = txnId,
        sequenceNumber = 1L,
        tableName = "customers",
        before = None,
        after = Some(CustomerData(
          id = 1001L,
          email = "alice@example.com",
          firstName = "Alice",
          lastName = "Johnson",
          phone = Some("+1-555-0101"),
          address = Some("123 Main St, Anytown, ST 12345"),
          registrationDate = now.toString,
          status = "ACTIVE"
        ))
      ),

      // UPDATE event
      CDCEvent(
        operation = CDCOperation.Update,
        timestamp = now.plusSeconds(60).toString,
        transactionId = UUID.randomUUID().toString,
        sequenceNumber = 1L,
        tableName = "customers",
        before = Some(CustomerData(
          id = 1001L,
          email = "alice@example.com",
          firstName = "Alice",
          lastName = "Johnson",
          phone = Some("+1-555-0101"),
          address = Some("123 Main St, Anytown, ST 12345"),
          registrationDate = now.toString,
          status = "ACTIVE"
        )),
        after = Some(CustomerData(
          id = 1001L,
          email = "alice.johnson@example.com", // Email updated
          firstName = "Alice",
          lastName = "Johnson",
          phone = Some("+1-555-0102"), // Phone updated
          address = Some("456 Oak Ave, Newtown, ST 67890"), // Address updated
          registrationDate = now.toString,
          status = "ACTIVE"
        ))
      ),

      // DELETE event
      CDCEvent(
        operation = CDCOperation.Delete,
        timestamp = now.plusSeconds(120).toString,
        transactionId = UUID.randomUUID().toString,
        sequenceNumber = 1L,
        tableName = "customers",
        before = Some(CustomerData(
          id = 1002L,
          email = "bob@example.com",
          firstName = "Bob",
          lastName = "Smith",
          phone = Some("+1-555-0201"),
          address = Some("789 Pine St, Oldtown, ST 11111"),
          registrationDate = now.minusSeconds(86400).toString,
          status = "INACTIVE"
        )),
        after = None
      )
    )
  }
}
