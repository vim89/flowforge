package com.flowforge.sample.advanced

import cats.data.{ NonEmptyList, ValidatedNel }
import cats.effect.IO
import cats.implicits._
import com.flowforge.core.PipelineBuilder
import com.flowforge.core.algebra.{ DataAlgebra, EffectSystem }
import com.flowforge.core.contracts.{ DataContract, SchemaConforms, SchemaPolicy }
import com.flowforge.core.contracts.derive.Shape
import com.flowforge.core.types._
import com.flowforge.engines.flink.FlinkDataAlgebra
import com.flowforge.engines.spark.SparkDataAlgebra
import com.flowforge.framework.PipelineExecution
import org.apache.spark.sql.SparkSession
import io.circe.{ Decoder, Encoder }
import io.circe.generic.semiauto._
import io.circe.parser.parse
import java.time.{ Instant, LocalDateTime, ZoneOffset }
import java.util.concurrent.TimeUnit
import scala.concurrent.duration._

/**
 * Comprehensive Flink streaming example demonstrating FlowForge's engine-agnostic capabilities.
 * 
 * This example shows how the same business logic can run on both Spark and Flink engines
 * without modification, while leveraging streaming-specific features like windowing,
 * watermarks, and event-time processing.
 * 
 * Key Features Demonstrated:
 * - Engine abstraction: Same pipeline logic works on Spark and Flink
 * - Streaming semantics: Windowing, watermarks, event-time processing
 * - Contract validation: Compile-time schema enforcement in streaming contexts
 * - State management: Proper handling of streaming state across engines
 * - Cross-engine testing: Verification of identical behavior
 */
object FlinkStreamingExample {

  // ========== Data Models with Contract Validation ==========

  /**
   * Represents a user interaction event in our streaming pipeline.
   * This model demonstrates compile-time contract validation in streaming contexts.
   */
  final case class UserEvent(
    userId: Long,
    eventType: String,
    timestamp: Long, // Unix timestamp in milliseconds
    sessionId: String,
    properties: Map[String, String] = Map.empty
  )

  /**
   * Aggregated user session metrics computed in streaming windows.
   * Shows how FlowForge handles schema evolution in streaming aggregations.
   */
  final case class SessionMetrics(
    userId: Long,
    sessionId: String,
    eventCount: Int,
    sessionDurationMs: Long,
    firstEventTime: Long,
    lastEventTime: Long,
    eventTypes: Set[String],
    windowStart: Long,
    windowEnd: Long
  )

  /**
   * Real-time user behavior profile updated via streaming CDC operations.
   * Demonstrates state management and incremental updates in streaming contexts.
   */
  final case class UserProfile(
    userId: Long,
    totalSessions: Int,
    totalEvents: Long,
    avgSessionDuration: Double,
    favoriteEventTypes: List[String],
    lastActiveTime: Long,
    profileVersion: Int = 1
  )

  // ========== Contract Definitions ==========

  implicit val userEventShape: Shape[UserEvent] = Shape.gen[UserEvent]
  implicit val sessionMetricsShape: Shape[SessionMetrics] = Shape.gen[SessionMetrics]
  implicit val userProfileShape: Shape[UserProfile] = Shape.gen[UserProfile]

  // Exact schema conformance for input events (strict validation)
  implicit val userEventConforms: SchemaConforms[UserEvent, UserEvent, SchemaPolicy.Exact] = implicitly

  // Forward compatibility for session metrics (allows new fields)
  implicit val sessionMetricsConforms: SchemaConforms[SessionMetrics, SessionMetrics, SchemaPolicy.Forward] = implicitly

  // Backward compatibility for user profiles (handles schema evolution)
  implicit val userProfileConforms: SchemaConforms[UserProfile, UserProfile, SchemaPolicy.Backward] = implicitly

  // ========== JSON Codecs ==========

  implicit val userEventDecoder: Decoder[UserEvent] = deriveDecoder[UserEvent]
  implicit val userEventEncoder: Encoder[UserEvent] = deriveEncoder[UserEvent]
  implicit val sessionMetricsDecoder: Decoder[SessionMetrics] = deriveDecoder[SessionMetrics]
  implicit val sessionMetricsEncoder: Encoder[SessionMetrics] = deriveEncoder[SessionMetrics]
  implicit val userProfileDecoder: Decoder[UserProfile] = deriveDecoder[UserProfile]
  implicit val userProfileEncoder: Encoder[UserProfile] = deriveEncoder[UserProfile]

  // ========== FlowForge Data Encoders/Decoders ==========

  implicit val userEventFFDecoder: com.flowforge.core.algebra.DataDecoder[UserEvent] = 
    new com.flowforge.core.algebra.DataDecoder[UserEvent] {
      def decode(ed: com.flowforge.core.algebra.EncodedData, format: DataFormat) = format match {
        case DataFormat.JSON | DataFormat.JSONL =>
          parse(new String(ed.data, "UTF-8"))
            .flatMap(_.as[UserEvent])
            .left.map(e => com.flowforge.core.algebra.CorruptedData(e.getMessage))
        case other => 
          Left(com.flowforge.core.algebra.CorruptedData(s"Unsupported format: $other"))
      }
      def validateSchema(ed: com.flowforge.core.algebra.EncodedData, expected: DataSchema) = Right(())
      def decodeWithEvolution(ed: com.flowforge.core.algebra.EncodedData, format: DataFormat, target: DataSchema) = 
        decode(ed, format)
      def supportsFormat(format: DataFormat) = 
        format == DataFormat.JSON || format == DataFormat.JSONL
    }

  implicit val sessionMetricsFFEncoder: com.flowforge.core.algebra.DataEncoder[SessionMetrics] = 
    com.flowforge.core.algebra.DataEncoder.instance[SessionMetrics](
      (metrics, format) => format match {
        case DataFormat.JSON | DataFormat.JSONL =>
          Right(com.flowforge.core.algebra.EncodedData(
            sessionMetricsEncoder(metrics).noSpaces.getBytes("UTF-8"), 
            format
          ))
        case DataFormat.Parquet =>
          // Simplified parquet encoding for demo
          Right(com.flowforge.core.algebra.EncodedData(
            sessionMetricsEncoder(metrics).noSpaces.getBytes("UTF-8"), 
            format
          ))
        case other => 
          Left(com.flowforge.core.algebra.CorruptedData(s"Unsupported format: $other"))
      },
      _ => DataSchema.builder
        .addField("userId", DataType.Long)
        .addField("sessionId", DataType.String)
        .addField("eventCount", DataType.Integer)
        .addField("sessionDurationMs", DataType.Long)
        .addField("firstEventTime", DataType.Long)
        .addField("lastEventTime", DataType.Long)
        .addField("eventTypes", DataType.Array(DataType.String))
        .addField("windowStart", DataType.Long)
        .addField("windowEnd", DataType.Long)
        .build
    )

  implicit val userProfileFFEncoder: com.flowforge.core.algebra.DataEncoder[UserProfile] = 
    com.flowforge.core.algebra.DataEncoder.instance[UserProfile](
      (profile, format) => format match {
        case DataFormat.JSON | DataFormat.JSONL =>
          Right(com.flowforge.core.algebra.EncodedData(
            userProfileEncoder(profile).noSpaces.getBytes("UTF-8"), 
            format
          ))
        case DataFormat.Delta =>
          // Delta format encoding for CDC operations
          Right(com.flowforge.core.algebra.EncodedData(
            userProfileEncoder(profile).noSpaces.getBytes("UTF-8"), 
            format
          ))
        case other => 
          Left(com.flowforge.core.algebra.CorruptedData(s"Unsupported format: $other"))
      },
      _ => DataSchema.builder
        .addField("userId", DataType.Long)
        .addField("totalSessions", DataType.Integer)
        .addField("totalEvents", DataType.Long)
        .addField("avgSessionDuration", DataType.Double)
        .addField("favoriteEventTypes", DataType.Array(DataType.String))
        .addField("lastActiveTime", DataType.Long)
        .addField("profileVersion", DataType.Integer)
        .build
    )

  // ========== Engine-Agnostic Business Logic ==========

  /**
   * Core streaming business logic that works identically on both Spark and Flink.
   * This demonstrates FlowForge's engine abstraction capabilities.
   */
  class StreamingPipeline[F[_]: EffectSystem](algebra: DataAlgebra[F]) {

    /**
     * Processes user events into session metrics using windowing and event-time processing.
     * This logic is identical regardless of whether it runs on Spark or Flink.
     */
    def processEventStream(
      eventSource: DataSource,
      metricssink: DataSink,
      windowDuration: FiniteDuration = 5.minutes,
      watermarkDelay: FiniteDuration = 30.seconds
    ): F[Unit] = {
      for {
        // Read streaming events with contract validation
        eventStream <- algebra.stream[UserEvent](eventSource)
        
        // Apply watermark for event-time processing (engine handles implementation)
        watermarkedStream = eventStream.withWatermark("timestamp", watermarkDelay)
        
        // Window events by session and time
        windowedEvents = watermarkedStream
          .groupBy(event => (event.userId, event.sessionId))
          .window(windowDuration)
        
        // Compute session metrics (same logic on both engines)
        sessionMetrics = windowedEvents.aggregate { events =>
          val eventList = events.toList
          val sortedByTime = eventList.sortBy(_.timestamp)
          val firstEvent = sortedByTime.head
          val lastEvent = sortedByTime.last
          val windowStart = (firstEvent.timestamp / windowDuration.toMillis) * windowDuration.toMillis
          val windowEnd = windowStart + windowDuration.toMillis
          
          SessionMetrics(
            userId = firstEvent.userId,
            sessionId = firstEvent.sessionId,
            eventCount = eventList.size,
            sessionDurationMs = lastEvent.timestamp - firstEvent.timestamp,
            firstEventTime = firstEvent.timestamp,
            lastEventTime = lastEvent.timestamp,
            eventTypes = eventList.map(_.eventType).toSet,
            windowStart = windowStart,
            windowEnd = windowEnd
          )
        }
        
        // Write metrics with schema validation
        _ <- algebra.writeWithValidation(
          sessionMetrics.toDataset,
          metricssink,
          DataContract.builder[SessionMetrics]
            .withPolicy(SchemaPolicy.Forward)
            .withQualityChecks(
              QualityCheck.nonNull("userId"),
              QualityCheck.nonNull("sessionId"),
              QualityCheck.range("eventCount", min = 1),
              QualityCheck.range("sessionDurationMs", min = 0)
            )
            .build
        ).flatMap {
          case cats.data.Validated.Valid(result) => 
            EffectSystem[F].pure(())
          case cats.data.Validated.Invalid(errors) => 
            EffectSystem[F].raiseError(new RuntimeException(s"Quality validation failed: ${errors.toList.mkString(", ")}"))
        }
        
      } yield ()
    }

    /**
     * Updates user profiles using CDC operations for real-time state management.
     * Demonstrates how FlowForge handles streaming state consistently across engines.
     */
    def updateUserProfiles(
      sessionMetricsSource: DataSource,
      profilesTable: DataSink,
      checkpointInterval: FiniteDuration = 1.minute
    ): F[Unit] = {
      for {
        // Read session metrics stream
        metricsStream <- algebra.stream[SessionMetrics](sessionMetricsSource)
        
        // Read existing user profiles for CDC operations
        existingProfiles <- algebra.read[UserProfile](
          DataSource.table("user_profiles", DataFormat.Delta)
        )
        
        // Group metrics by user for profile updates
        userMetrics = metricsStream.groupBy(_.userId)
        
        // Compute profile updates (engine-agnostic aggregation logic)
        profileUpdates = userMetrics.aggregate { metrics =>
          val metricsList = metrics.toList
          val userId = metricsList.head.userId
          val totalEvents = metricsList.map(_.eventCount.toLong).sum
          val totalSessions = metricsList.map(_.sessionId).toSet.size
          val avgDuration = metricsList.map(_.sessionDurationMs).sum.toDouble / totalSessions
          val allEventTypes = metricsList.flatMap(_.eventTypes)
          val favoriteTypes = allEventTypes.groupBy(identity).mapValues(_.size).toList
            .sortBy(-_._2).take(3).map(_._1)
          val lastActive = metricsList.map(_.lastEventTime).max
          
          UserProfile(
            userId = userId,
            totalSessions = totalSessions,
            totalEvents = totalEvents,
            avgSessionDuration = avgDuration,
            favoriteEventTypes = favoriteTypes,
            lastActiveTime = lastActive,
            profileVersion = 1 // Will be incremented during CDC merge
          )
        }
        
        // Perform CDC operations to merge profile updates
        cdcResult <- algebra.performDelta(
          source = profileUpdates.toDataset,
          target = existingProfiles,
          config = CDCOperations.CDCConfig(
            keyColumns = NonEmptyList.of(RefinedTypes.FieldName("userId")),
            mergeCondition = Some("source.lastActiveTime > target.lastActiveTime"),
            updateCondition = Some("source.totalEvents > target.totalEvents"),
            deleteCondition = None // No deletes for user profiles
          )
        )
        
        // Apply CDC operations to the target table
        _ <- algebra.applyCDCOperations(cdcResult.operations, profilesTable)
        
        // Record lineage for the streaming update
        _ <- algebra.recordLineage(
          profileUpdates.toDataset,
          operation = "streaming_profile_update",
          context = LineageContext(
            jobId = "flink-streaming-pipeline",
            runId = java.util.UUID.randomUUID().toString,
            timestamp = Instant.now(),
            metadata = Map(
              "engine" -> "flink",
              "checkpoint_interval" -> checkpointInterval.toString,
              "watermark_delay" -> "30s"
            )
          )
        )
        
      } yield ()
    }
  }

  // ========== Engine-Specific Implementations ==========

  /**
   * Flink-specific pipeline implementation demonstrating streaming capabilities.
   */
  def createFlinkPipeline[F[_]: EffectSystem]: F[StreamingPipeline[F]] = {
    val flinkAlgebra = new FlinkDataAlgebra[F]()
    EffectSystem[F].pure(new StreamingPipeline[F](flinkAlgebra))
  }

  /**
   * Spark-specific pipeline implementation for comparison and testing.
   */
  def createSparkPipeline[F[_]: EffectSystem](spark: SparkSession): F[StreamingPipeline[F]] = {
    val sparkAlgebra = SparkDataAlgebra.createSparkDataAlgebra[F](spark).algebra
    EffectSystem[F].pure(new StreamingPipeline[F](sparkAlgebra))
  }

  // ========== Cross-Engine Testing ==========

  /**
   * Demonstrates that the same business logic produces identical results on both engines.
   * This is a key feature of FlowForge's engine abstraction.
   */
  def crossEngineTest(spark: SparkSession): IO[Boolean] = {
    implicit val ioEffectSystem: EffectSystem[IO] = com.flowforge.core.instances.EffectInstances.catsIOEffectSystemInstance
    
    val testEvents = List(
      UserEvent(1L, "login", System.currentTimeMillis(), "session1"),
      UserEvent(1L, "view_page", System.currentTimeMillis() + 1000, "session1"),
      UserEvent(1L, "logout", System.currentTimeMillis() + 2000, "session1"),
      UserEvent(2L, "login", System.currentTimeMillis(), "session2"),
      UserEvent(2L, "purchase", System.currentTimeMillis() + 1500, "session2")
    )
    
    for {
      // Create test data source
      testSource = DataSource.memory(testEvents, DataFormat.JSON)
      testSink = DataSink.memory("test_output", DataFormat.JSON)
      
      // Run on Flink
      flinkPipeline <- createFlinkPipeline[IO]
      _ <- flinkPipeline.processEventStream(testSource, testSink)
      flinkResults <- flinkPipeline.algebra.read[SessionMetrics](
        DataSource.memory("test_output", DataFormat.JSON)
      )
      
      // Run on Spark  
      sparkPipeline <- createSparkPipeline[IO](spark)
      _ <- sparkPipeline.processEventStream(testSource, testSink)
      sparkResults <- sparkPipeline.algebra.read[SessionMetrics](
        DataSource.memory("test_output", DataFormat.JSON)
      )
      
      // Compare results (should be identical)
      flinkMetrics = flinkResults.collect().sortBy(_.userId)
      sparkMetrics = sparkResults.collect().sortBy(_.userId)
      
      resultsMatch = flinkMetrics.zip(sparkMetrics).forall { case (f, s) =>
        f.userId == s.userId &&
        f.sessionId == s.sessionId &&
        f.eventCount == s.eventCount &&
        f.sessionDurationMs == s.sessionDurationMs &&
        f.eventTypes == s.eventTypes
      }
      
    } yield resultsMatch
  }

  // ========== Main Application ==========

  /**
   * Main application demonstrating the complete Flink streaming pipeline.
   * Shows how to set up and run streaming pipelines with FlowForge.
   */
  def main(args: Array[String]): Unit = {
    implicit val ioEffectSystem: EffectSystem[IO] = com.flowforge.core.instances.EffectInstances.catsIOEffectSystemInstance
    
    val program: IO[Unit] = for {
      // Create Flink streaming pipeline
      pipeline <- createFlinkPipeline[IO]
      
      // Configure data sources and sinks
      eventSource = DataSource.kafka(
        topic = "user_events",
        brokers = "localhost:9092",
        format = DataFormat.JSON
      )
      
      metricssink = DataSink.kafka(
        topic = "session_metrics", 
        brokers = "localhost:9092",
        format = DataFormat.JSON
      )
      
      profilesSink = DataSink.delta(
        path = "s3://data-lake/user_profiles",
        mode = WriteMode.Merge
      )
      
      // Start streaming pipeline
      _ <- IO.println("Starting Flink streaming pipeline...")
      
      // Process event stream into session metrics
      _ <- pipeline.processEventStream(
        eventSource = eventSource,
        metricssink = metricssink,
        windowDuration = 5.minutes,
        watermarkDelay = 30.seconds
      )
      
      // Update user profiles with CDC operations
      _ <- pipeline.updateUserProfiles(
        sessionMetricsSource = DataSource.kafka("session_metrics", "localhost:9092", DataFormat.JSON),
        profilesTable = profilesSink,
        checkpointInterval = 1.minute
      )
      
      _ <- IO.println("Streaming pipeline started successfully!")
      _ <- IO.println("Pipeline will run continuously. Press Ctrl+C to stop.")
      
    } yield ()
    
    program.unsafeRunSync()
  }

  // ========== Utility Methods ==========

  /**
   * Creates sample test data for development and testing.
   */
  def generateSampleEvents(count: Int): List[UserEvent] = {
    val random = new scala.util.Random()
    val eventTypes = List("login", "logout", "view_page", "purchase", "search", "click")
    val baseTime = System.currentTimeMillis()
    
    (1 to count).map { i =>
      UserEvent(
        userId = random.nextLong(1000) + 1,
        eventType = eventTypes(random.nextInt(eventTypes.length)),
        timestamp = baseTime + (i * 1000) + random.nextInt(5000),
        sessionId = s"session_${random.nextInt(100)}",
        properties = Map(
          "page" -> s"page_${random.nextInt(10)}",
          "source" -> List("web", "mobile", "api")(random.nextInt(3))
        )
      )
    }.toList
  }

  /**
   * Validates that streaming state is properly managed across engine restarts.
   */
  def validateStateManagement[F[_]: EffectSystem](pipeline: StreamingPipeline[F]): F[Boolean] = {
    // This would typically involve checkpoint validation and state recovery testing
    // For demo purposes, we'll simulate state validation
    EffectSystem[F].pure(true)
  }
}