/**
 * FlowForge Connectors Module - File System Example
 *
 * This example demonstrates practical usage of FlowForge file system connectors with real-world scenarios
 * including data ingestion, processing, and export.
 *
 * Features demonstrated:
 *   - Local file system operations
 *   - Batch and streaming I/O
 *   - Error handling and validation
 *   - Integration with FlowForge pipelines
 *   - Resource-safe operations
 *   - Multiple data formats
 */
package com.flowforge.connectors.filesystem.examples

import cats.effect.{ IO, IOApp }
import cats.implicits._
import com.flowforge.connectors.FileSystemResult._
import com.flowforge.connectors._
import com.flowforge.connectors.filesystem._
import com.flowforge.core.algebra.EffectSystem
import com.flowforge.core.types.{ LocalDataSink, LocalDataSource, _ }

import java.nio.file.{ Files, Paths }
import java.time.Instant

/**
 * Demonstrates file system connector usage with practical examples
 */
object FileSystemExample extends IOApp.Simple {

  // Use explicit EffectSystem instance to avoid ambiguity
  private implicit val effectSystemInstance: EffectSystem[IO] =
    com.flowforge.core.instances.EffectInstances.catsEffectSystemInstance

  def run: IO[Unit] =
    for {
      _ <- IO.println("🗂️ FlowForge File System Connector Demo")
      _ <- IO.println("")

      _ <- setupTestEnvironment
      _ <- runBasicFileOperations
      _ <- runBatchOperations
      _ <- runStreamingOperations
      _ <- runPipelineIntegration
      _ <- cleanup

      _ <- IO.println("✅ File system connector demo completed!")

    } yield ()

  /**
   * Setup test environment with sample files
   */
  def setupTestEnvironment: IO[Unit] =
    for {
      _ <- IO.println("🔧 Setting up test environment...")

      // Create test directories
      _ <- IO.delay(Files.createDirectories(Paths.get("/tmp/flowforge/input")))
      _ <- IO.delay(Files.createDirectories(Paths.get("/tmp/flowforge/output")))
      _ <- IO.delay(Files.createDirectories(Paths.get("/tmp/flowforge/processed")))

      // Create sample data files
      sampleData1 = """{"id":1,"name":"Alice","amount":100.50,"category":"sales"}
{"id":2,"name":"Bob","amount":75.25,"category":"marketing"}
{"id":3,"name":"Carol","amount":150.75,"category":"sales"}"""

      sampleData2 = """{"id":4,"name":"Dave","amount":200.00,"category":"support"}
{"id":5,"name":"Eve","amount":125.50,"category":"marketing"}
{"id":6,"name":"Frank","amount":300.25,"category":"sales"}"""

      _ <- IO.delay(Files.write(Paths.get("/tmp/flowforge/input/data1.json"), sampleData1.getBytes))
      _ <- IO.delay(Files.write(Paths.get("/tmp/flowforge/input/data2.json"), sampleData2.getBytes))

      _ <- IO.println("   ✅ Test environment ready")

    } yield ()

  /**
   * Demonstrate basic file operations
   */
  def runBasicFileOperations: IO[Unit] = {
    val connector = FileSystemConnector.local[IO]

    for {
      _ <- IO.println("📁 Basic File Operations:")

      // Test file existence
      exists <- connector.exists("/tmp/flowforge/input/data1.json")
      _      <- IO.println(s"   File exists: $exists")

      // Read file
      source = LocalDataSource(
        location = "/tmp/flowforge/input/data1.json",
        format = DataFormat.JSON,
        id = Some("data1"),
      )
      readResult <- connector.read(source)
      _ <- readResult match {
        case FileSystemResult.Success(bytes) =>
          IO.println(s"   Read ${bytes.length} bytes")
        case FileSystemResult.Failure(error) =>
          IO.println(s"   Read failed: ${error.message}")
      }

      // Get metadata
      metadataResult <- connector.getMetadata("/tmp/flowforge/input/data1.json")
      _ <- metadataResult match {
        case FileSystemResult.Success(metadata) =>
          IO.println(s"   File size: ${metadata.size} bytes, modified: ${metadata.lastModified}")
        case FileSystemResult.Failure(error) =>
          IO.println(s"   Metadata failed: ${error.message}")
      }

      // List files in directory
      listResult <- connector.listFiles("/tmp/flowforge/input")
      _ <- listResult match {
        case FileSystemResult.Success(files) =>
          for {
            _ <- IO.println(s"   Found ${files.length} files:")
            _ <- files.traverse(file => IO.println(s"     - ${file.name} (${file.size} bytes)"))
          } yield ()
        case FileSystemResult.Failure(error) =>
          IO.println(s"   List failed: ${error.message}")
      }

      // Write new file
      outputData = """{"id":100,"name":"Generated","amount":999.99,"category":"test"}"""
      sink = LocalDataSink(
        location = "/tmp/flowforge/output/generated.json",
        format = DataFormat.JSON,
        id = Some("test_output"),
      )
      writeResult <- connector.write(sink, outputData.getBytes)
      _ <- writeResult match {
        case FileSystemResult.Success(metadata) =>
          IO.println(s"   Wrote ${metadata.bytesWritten} bytes to ${metadata.path}")
        case FileSystemResult.Failure(error) =>
          IO.println(s"   Write failed: ${error.message}")
      }

      _ <- IO.println("   ✅ Basic operations completed")
      _ <- IO.println("")

    } yield ()
  }

  /**
   * Demonstrate batch operations
   */
  def runBatchOperations: IO[Unit] =
    for {
      _ <- IO.println("🔄 Batch Operations:")

      // Batch read multiple files
      sources = List(
        LocalDataSource("/tmp/flowforge/input/data1.json", DataFormat.JSON, id = Some("data1")),
        LocalDataSource("/tmp/flowforge/input/data2.json", DataFormat.JSON, id = Some("data2")),
      )

      batchResults <- FileSystemOps.batchRead[IO](sources)
      totalBytes = batchResults.collect {
        case FileSystemResult.Success(bytes) =>
          bytes.length
      }.sum
      _ <- IO.println(
        s"   Batch read: ${batchResults.count(_.isSuccess)} files, $totalBytes bytes total",
      )

      // Parallel read (faster for multiple files)
      parallelResults <- FileSystemOps.parallelRead[IO](sources)
      parallelTotalBytes = parallelResults.collect {
        case FileSystemResult.Success(bytes) =>
          bytes.length
      }.sum
      _ <- IO.println(
        s"   Parallel read: ${parallelResults.count(_.isSuccess)} files, $parallelTotalBytes bytes total",
      )

      _ <- IO.println("   ✅ Batch operations completed")
      _ <- IO.println("")

    } yield ()

  /**
   * Demonstrate streaming operations for large files
   */
  def runStreamingOperations: IO[Unit] = {
    val connector = FileSystemConnector.local[IO]

    for {
      _ <- IO.println("🌊 Streaming Operations:")

      // Create a larger test file
      largeData = (1 to 1000)
        .map(i =>
          s"""{"id":$i,"name":"User$i","amount":${i * 10.5},"category":"${if (i % 3 == 0) "sales"
            else if (i % 2 == 0) "marketing"
            else "support"}"}""",
        )
        .mkString("\n")

      _ <- IO.delay(
        Files.write(Paths.get("/tmp/flowforge/input/large_data.json"), largeData.getBytes),
      )

      // Stream read
      source = LocalDataSource(
        location = "/tmp/flowforge/input/large_data.json",
        format = DataFormat.JSON,
        id = Some("large_data"),
      )

      chunks <- connector.streamRead(source)
      totalStreamBytes = chunks.map(_.length).sum
      _ <- IO.println(s"   Streamed ${chunks.length} chunks, $totalStreamBytes bytes total")

      // Stream write
      sink = LocalDataSink(
        location = "/tmp/flowforge/output/streamed_data.json",
        format = DataFormat.JSON,
        id = Some("streamed_output"),
      )

      writeResult <- connector.streamWrite(sink, chunks)
      _           <- IO.println(s"   Streamed write completed: ${writeResult.bytesWritten} bytes")

      _ <- IO.println("   ✅ Streaming operations completed")
      _ <- IO.println("")

    } yield ()
  }

  /**
   * Demonstrate integration with FlowForge pipelines
   */
  def runPipelineIntegration: IO[Unit] =
    for {
      _ <- IO.println("🔗 Pipeline Integration:")

      // Create a simple data processing pipeline using file connectors
      result <- dataPipelineWithFileSystem
      _      <- IO.println(s"   Pipeline processed ${result.recordsProcessed} records")
      _      <- IO.println(s"   Output files: ${result.outputFiles.mkString(", ")}")

      _ <- IO.println("   ✅ Pipeline integration completed")
      _ <- IO.println("")

    } yield ()

  /**
   * Data processing pipeline that uses file system connectors
   */
  def dataPipelineWithFileSystem: IO[PipelineResult] = {
    val connector = FileSystemConnector.local[IO]

    for {
      // Stage 1: Read input files
      inputFiles <- connector.listFiles("/tmp/flowforge/input")
      jsonFiles = inputFiles match {
        case FileSystemResult.Success(files) =>
          files.filter(_.format == DataFormat.JSON).filter(!_.name.contains("large"))
        case _ => List.empty
      }

      // Stage 2: Process each file
      processedData <- jsonFiles.traverse { file =>
        for {
          readResult <- connector.read(
            LocalDataSource(file.path, DataFormat.JSON, id = Some(file.name)),
          )
          processedContent = readResult match {
            case FileSystemResult.Success(bytes) =>
              // Simple transformation: add processing timestamp
              val originalContent = new String(bytes)
              val lines           = originalContent.split("\n")
              val processedLines = lines.map { line =>
                if (line.trim.nonEmpty) {
                  val trimmed = line.trim.dropRight(1) // Remove closing }
                  s"""$trimmed,"processed_at":"${Instant.now()}"}"""
                } else line
              }
              processedLines.mkString("\n")
            case FileSystemResult.Failure(_) => ""
          }

          // Stage 3: Write processed data
          outputPath = s"/tmp/flowforge/processed/processed_${file.name}"
          sink       = LocalDataSink(outputPath, DataFormat.JSON, id = Some(s"processed_${file.name}"))
          _ <- connector.write(sink, processedContent.getBytes)
        } yield outputPath
      }

      // Stage 4: Create summary report
      summaryData = s"""{"pipeline_run":"${Instant
          .now()}","files_processed":${jsonFiles.length},"output_files":${processedData.length}}"""
      summarySink = LocalDataSink(
        "/tmp/flowforge/output/pipeline_summary.json",
        DataFormat.JSON,
        id = Some("pipeline_summary"),
      )
      _ <- connector.write(summarySink, summaryData.getBytes)

    } yield PipelineResult(
      pipelineId = "file-system-demo",
      executionId = java.util.UUID.randomUUID().toString,
      status = ExecutionStatus.Success,
      recordsProcessed = jsonFiles.length.toLong,
      outputFiles = processedData :+ "/tmp/flowforge/output/pipeline_summary.json",
      startTime = Instant.now(),
      endTime = Instant.now(),
      duration = scala.concurrent.duration.Duration.Zero,
      metrics = List.empty,
      errors = List.empty,
    )
  }

  /**
   * Clean up test environment
   */
  def cleanup: IO[Unit] = {
    val connector = FileSystemConnector.local[IO]

    for {
      _ <- IO.println("🧹 Cleaning up...")

      // Clean up test directories (in production, you might want to keep outputs)
      _ <- connector.delete("/tmp/flowforge", recursive = true)

      _ <- IO.println("   ✅ Cleanup completed")
      _ <- IO.println("")

    } yield ()
  }

  // ===============================
  // SUPPORTING TYPES
  // ===============================

  case class PipelineResult(
    pipelineId: String,
    executionId: String,
    status: ExecutionStatus,
    recordsProcessed: Long,
    outputFiles: List[String],
    startTime: Instant,
    endTime: Instant,
    duration: scala.concurrent.duration.Duration,
    metrics: List[String],
    errors: List[FlowForgeError])
}
