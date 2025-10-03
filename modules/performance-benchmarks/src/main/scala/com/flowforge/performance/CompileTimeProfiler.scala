package com.flowforge.performance

import scala.collection.mutable
import scala.util.{Try, Success, Failure}
import scala.io.Source
import java.io.{File, PrintWriter}
import java.lang.management.{ManagementFactory, MemoryMXBean, GarbageCollectorMXBean}
import java.time.{Instant, Duration}
import scala.tools.nsc.{Global, Settings}
import scala.tools.nsc.reporters.ConsoleReporter
import scala.tools.nsc.io.VirtualDirectory
import scala.reflect.internal.util.BatchSourceFile
import scala.collection.JavaConverters._

/**
 * Compile-time performance measurement utilities for FlowForge schema validation macros.
 * Provides comprehensive tooling for measuring macro expansion performance, memory usage,
 * and analyzing compilation bottlenecks.
 */
object CompileTimeProfiler {

  /**
   * Compilation timer for measuring macro expansion performance.
   */
  object CompilationTimer {
    private val measurements = mutable.ListBuffer[CompilationMeasurement]()
    private val memoryBean: MemoryMXBean = ManagementFactory.getMemoryMXBean
    private val gcBeans: List[GarbageCollectorMXBean] = ManagementFactory.getGarbageCollectorMXBeans.asScala.toList

    case class CompilationMeasurement(
      testName: String,
      compilationTime: Duration,
      memoryUsed: Long,
      gcCount: Long,
      gcTime: Long,
      timestamp: Instant,
      schemaComplexity: SchemaComplexityMetrics
    )

    case class MemorySnapshot(
      heapUsed: Long,
      heapMax: Long,
      nonHeapUsed: Long,
      gcCount: Long,
      gcTime: Long
    )

    private def takeMemorySnapshot(): MemorySnapshot = {
      val heap = memoryBean.getHeapMemoryUsage
      val nonHeap = memoryBean.getNonHeapMemoryUsage
      val totalGcCount = gcBeans.map(_.getCollectionCount).sum
      val totalGcTime = gcBeans.map(_.getCollectionTime).sum
      
      MemorySnapshot(
        heapUsed = heap.getUsed,
        heapMax = heap.getMax,
        nonHeapUsed = nonHeap.getUsed,
        gcCount = totalGcCount,
        gcTime = totalGcTime
      )
    }

    /**
     * Measures compilation time and memory usage for a given code snippet.
     */
    def measureCompilation(
      testName: String,
      code: String,
      complexity: SchemaComplexityMetrics
    ): Try[CompilationMeasurement] = Try {
      // Force GC before measurement for consistent baseline
      System.gc()
      Thread.sleep(100)
      
      val beforeSnapshot = takeMemorySnapshot()
      val startTime = Instant.now()
      
      // Compile the code
      compileCode(code) match {
        case Success(_) =>
          val endTime = Instant.now()
          val afterSnapshot = takeMemorySnapshot()
          
          val measurement = CompilationMeasurement(
            testName = testName,
            compilationTime = Duration.between(startTime, endTime),
            memoryUsed = afterSnapshot.heapUsed - beforeSnapshot.heapUsed,
            gcCount = afterSnapshot.gcCount - beforeSnapshot.gcCount,
            gcTime = afterSnapshot.gcTime - beforeSnapshot.gcTime,
            timestamp = startTime,
            schemaComplexity = complexity
          )
          
          measurements += measurement
          measurement
          
        case Failure(ex) =>
          throw new RuntimeException(s"Compilation failed for test '$testName'", ex)
      }
    }

    /**
     * Compiles a code snippet using Scala compiler.
     */
    private def compileCode(code: String): Try[Unit] = Try {
      val settings = new Settings()
      settings.usejavacp.value = true
      settings.outputDirs.setSingleOutput(new VirtualDirectory("(memory)", None))
      
      val reporter = new ConsoleReporter(settings)
      val compiler = new Global(settings, reporter)
      
      val sourceFile = new BatchSourceFile("test.scala", code)
      val run = new compiler.Run()
      run.compileSources(List(sourceFile))
      
      if (reporter.hasErrors) {
        throw new RuntimeException("Compilation errors occurred")
      }
    }

    /**
     * Returns all measurements collected so far.
     */
    def getMeasurements: List[CompilationMeasurement] = measurements.toList

    /**
     * Clears all collected measurements.
     */
    def clearMeasurements(): Unit = measurements.clear()

    /**
     * Exports measurements to CSV format for analysis.
     */
    def exportToCsv(filename: String): Unit = {
      val writer = new PrintWriter(new File(filename))
      try {
        writer.println("testName,compilationTimeMs,memoryUsedBytes,gcCount,gcTimeMs,timestamp,fieldCount,nestingDepth,collectionCount,optionalCount,complexityScore,complexityCategory")
        measurements.foreach { m =>
          writer.println(s"${m.testName},${m.compilationTime.toMillis},${m.memoryUsed},${m.gcCount},${m.gcTime},${m.timestamp},${m.schemaComplexity.fieldCount},${m.schemaComplexity.nestingDepth},${m.schemaComplexity.collectionCount},${m.schemaComplexity.optionalCount},${m.schemaComplexity.complexityScore},${m.schemaComplexity.category}")
        }
      } finally {
        writer.close()
      }
    }
  }

  /**
   * Schema complexity metrics for performance correlation analysis.
   */
  case class SchemaComplexityMetrics(
    fieldCount: Int,
    nestingDepth: Int,
    collectionCount: Int,
    optionalCount: Int,
    primitiveCount: Int,
    recordCount: Int
  ) {
    
    /**
     * Calculates a complexity score based on weighted metrics.
     */
    lazy val complexityScore: Double = {
      val fieldWeight = 1.0
      val nestingWeight = 3.0
      val collectionWeight = 2.0
      val optionalWeight = 1.5
      val recordWeight = 2.5
      
      (fieldCount * fieldWeight) +
      (nestingDepth * nestingWeight) +
      (collectionCount * collectionWeight) +
      (optionalCount * optionalWeight) +
      (recordCount * recordWeight)
    }
    
    /**
     * Categorizes complexity for easier analysis.
     */
    lazy val category: ComplexityCategory = {
      complexityScore match {
        case score if score <= 10 => ComplexityCategory.Simple
        case score if score <= 30 => ComplexityCategory.Medium
        case score if score <= 60 => ComplexityCategory.Complex
        case _ => ComplexityCategory.Extreme
      }
    }
  }

  sealed trait ComplexityCategory
  object ComplexityCategory {
    case object Simple extends ComplexityCategory
    case object Medium extends ComplexityCategory
    case object Complex extends ComplexityCategory
    case object Extreme extends ComplexityCategory
  }

  /**
   * Analyzes schema complexity from case class definitions.
   */
  object SchemaComplexityAnalyzer {
    
    /**
     * Analyzes complexity of a case class definition from source code.
     */
    def analyzeFromSource(caseClassCode: String): SchemaComplexityMetrics = {
      val lines = caseClassCode.split('\n').map(_.trim).filter(_.nonEmpty)
      
      var fieldCount = 0
      var nestingDepth = 0
      var collectionCount = 0
      var optionalCount = 0
      var primitiveCount = 0
      var recordCount = 0
      var currentDepth = 0
      var maxDepth = 0
      
      lines.foreach { line =>
        // Track nesting depth
        val openBraces = line.count(_ == '{')
        val closeBraces = line.count(_ == '}')
        currentDepth += openBraces - closeBraces
        maxDepth = math.max(maxDepth, currentDepth)
        
        // Count fields (lines with colons that aren't case class declarations)
        if (line.contains(":") && !line.startsWith("case class") && !line.startsWith("sealed")) {
          fieldCount += 1
          
          // Count collections
          if (line.contains("List[") || line.contains("Array[") || line.contains("Seq[") || 
              line.contains("Vector[") || line.contains("Set[")) {
            collectionCount += 1
          }
          
          // Count maps
          if (line.contains("Map[")) {
            collectionCount += 1
          }
          
          // Count optionals
          if (line.contains("Option[")) {
            optionalCount += 1
          }
          
          // Count primitives
          if (line.matches(".*:\\s*(String|Int|Long|Double|Float|Boolean|Instant).*")) {
            primitiveCount += 1
          }
          
          // Count nested records (custom types)
          if (!line.matches(".*:\\s*(String|Int|Long|Double|Float|Boolean|Instant|Option|List|Array|Seq|Vector|Set|Map).*")) {
            recordCount += 1
          }
        }
      }
      
      SchemaComplexityMetrics(
        fieldCount = fieldCount,
        nestingDepth = maxDepth,
        collectionCount = collectionCount,
        optionalCount = optionalCount,
        primitiveCount = primitiveCount,
        recordCount = recordCount
      )
    }
    
    /**
     * Generates test schemas of varying complexity.
     */
    def generateTestSchemas(): Map[String, (String, SchemaComplexityMetrics)] = {
      val schemas = mutable.Map[String, (String, SchemaComplexityMetrics)]()
      
      // Simple schema
      val simpleSchema = 
        """case class SimpleUser(
          |  id: Long,
          |  name: String,
          |  email: String
          |)""".stripMargin
      schemas("simple") = (simpleSchema, analyzeFromSource(simpleSchema))
      
      // Medium complexity schema
      val mediumSchema = 
        """case class MediumUser(
          |  id: Long,
          |  name: String,
          |  email: Option[String],
          |  tags: List[String],
          |  metadata: Map[String, String],
          |  profile: UserProfile,
          |  createdAt: java.time.Instant
          |)
          |
          |case class UserProfile(
          |  firstName: String,
          |  lastName: String,
          |  age: Option[Int]
          |)""".stripMargin
      schemas("medium") = (mediumSchema, analyzeFromSource(mediumSchema))
      
      // Complex schema
      val complexSchema = 
        """case class ComplexEvent(
          |  id: String,
          |  timestamp: java.time.Instant,
          |  userId: Long,
          |  sessionId: Option[String],
          |  eventType: String,
          |  properties: Map[String, String],
          |  metrics: List[Metric],
          |  context: EventContext,
          |  experiments: List[Experiment],
          |  deviceInfo: Option[DeviceInfo]
          |)
          |
          |case class Metric(
          |  name: String,
          |  value: Double,
          |  unit: String,
          |  tags: Map[String, String]
          |)
          |
          |case class EventContext(
          |  page: Option[PageContext],
          |  user: UserContext,
          |  session: SessionContext
          |)
          |
          |case class PageContext(
          |  url: String,
          |  title: Option[String],
          |  referrer: Option[String]
          |)
          |
          |case class UserContext(
          |  id: Long,
          |  segments: List[String],
          |  properties: Map[String, String]
          |)
          |
          |case class SessionContext(
          |  id: String,
          |  startTime: java.time.Instant,
          |  duration: Option[Long]
          |)
          |
          |case class Experiment(
          |  id: String,
          |  variant: String,
          |  enrolled: Boolean
          |)
          |
          |case class DeviceInfo(
          |  userAgent: String,
          |  platform: String,
          |  version: Option[String],
          |  capabilities: List[String]
          |)""".stripMargin
      schemas("complex") = (complexSchema, analyzeFromSource(complexSchema))
      
      // Extreme complexity schema (large number of fields)
      val extremeFields = (1 to 100).map(i => s"  field$i: String").mkString(",\n")
      val extremeSchema = s"case class ExtremeSchema(\n$extremeFields\n)"
      schemas("extreme") = (extremeSchema, analyzeFromSource(extremeSchema))
      
      schemas.toMap
    }
  }

  /**
   * Base class for compile-time performance tests.
   */
  abstract class CompileTimePerformanceSpec {
    
    case class PerformanceThreshold(
      maxCompilationTimeMs: Long,
      maxMemoryUsageMB: Long,
      maxComplexityScore: Double
    )
    
    private val defaultThresholds = Map(
      ComplexityCategory.Simple -> PerformanceThreshold(1000, 50, 10),
      ComplexityCategory.Medium -> PerformanceThreshold(3000, 100, 30),
      ComplexityCategory.Complex -> PerformanceThreshold(8000, 200, 60),
      ComplexityCategory.Extreme -> PerformanceThreshold(20000, 500, 200)
    )
    
    /**
     * Runs a performance test for schema validation macro.
     */
    def runPerformanceTest(
      testName: String,
      schemaCode: String,
      validationCode: String,
      customThresholds: Option[Map[ComplexityCategory, PerformanceThreshold]] = None
    ): PerformanceTestResult = {
      
      val complexity = SchemaComplexityAnalyzer.analyzeFromSource(schemaCode)
      val thresholds = customThresholds.getOrElse(defaultThresholds)
      val threshold = thresholds(complexity.category)
      
      val fullCode = s"""
        |import com.flowforge.core.contracts._
        |import com.flowforge.core.contracts.derive._
        |
        |$schemaCode
        |
        |$validationCode
        |""".stripMargin
      
      CompilationTimer.measureCompilation(testName, fullCode, complexity) match {
        case Success(measurement) =>
          val compilationTimeMs = measurement.compilationTime.toMillis
          val memoryUsageMB = measurement.memoryUsed / (1024 * 1024)
          
          val violations = mutable.ListBuffer[String]()
          
          if (compilationTimeMs > threshold.maxCompilationTimeMs) {
            violations += s"Compilation time ${compilationTimeMs}ms exceeds threshold ${threshold.maxCompilationTimeMs}ms"
          }
          
          if (memoryUsageMB > threshold.maxMemoryUsageMB) {
            violations += s"Memory usage ${memoryUsageMB}MB exceeds threshold ${threshold.maxMemoryUsageMB}MB"
          }
          
          if (complexity.complexityScore > threshold.maxComplexityScore) {
            violations += s"Complexity score ${complexity.complexityScore} exceeds threshold ${threshold.maxComplexityScore}"
          }
          
          PerformanceTestResult(
            testName = testName,
            measurement = measurement,
            threshold = threshold,
            violations = violations.toList,
            passed = violations.isEmpty
          )
          
        case Failure(ex) =>
          PerformanceTestResult(
            testName = testName,
            measurement = null,
            threshold = threshold,
            violations = List(s"Test failed with exception: ${ex.getMessage}"),
            passed = false
          )
      }
    }
    
    /**
     * Runs regression tests against baseline measurements.
     */
    def runRegressionTest(
      testName: String,
      current: CompilationTimer.CompilationMeasurement,
      baseline: CompilationTimer.CompilationMeasurement,
      regressionThresholdPercent: Double = 10.0
    ): RegressionTestResult = {
      
      val timeRegression = ((current.compilationTime.toMillis - baseline.compilationTime.toMillis).toDouble / baseline.compilationTime.toMillis) * 100
      val memoryRegression = ((current.memoryUsed - baseline.memoryUsed).toDouble / baseline.memoryUsed) * 100
      
      val violations = mutable.ListBuffer[String]()
      
      if (timeRegression > regressionThresholdPercent) {
        violations += f"Compilation time regression: ${timeRegression}%.1f%% (threshold: ${regressionThresholdPercent}%.1f%%)"
      }
      
      if (memoryRegression > regressionThresholdPercent) {
        violations += f"Memory usage regression: ${memoryRegression}%.1f%% (threshold: ${regressionThresholdPercent}%.1f%%)"
      }
      
      RegressionTestResult(
        testName = testName,
        current = current,
        baseline = baseline,
        timeRegressionPercent = timeRegression,
        memoryRegressionPercent = memoryRegression,
        violations = violations.toList,
        passed = violations.isEmpty
      )
    }
  }

  case class PerformanceTestResult(
    testName: String,
    measurement: CompilationTimer.CompilationMeasurement,
    threshold: CompileTimePerformanceSpec#PerformanceThreshold,
    violations: List[String],
    passed: Boolean
  )

  case class RegressionTestResult(
    testName: String,
    current: CompilationTimer.CompilationMeasurement,
    baseline: CompilationTimer.CompilationMeasurement,
    timeRegressionPercent: Double,
    memoryRegressionPercent: Double,
    violations: List[String],
    passed: Boolean
  )

  /**
   * Profiling data analysis utilities.
   */
  object ProfilingDataAnalyzer {
    
    case class MacroExpansionProfile(
      macroName: String,
      expansionCount: Int,
      totalTimeMs: Long,
      averageTimeMs: Double,
      maxTimeMs: Long,
      memoryAllocatedBytes: Long
    )
    
    case class CompilationProfile(
      totalCompilationTimeMs: Long,
      macroExpansions: List[MacroExpansionProfile],
      phaseTimings: Map[String, Long],
      memoryUsage: Map[String, Long]
    )
    
    /**
     * Parses scalac-profiling flamegraph data.
     */
    def parseProfilingData(profilingOutputFile: String): Try[CompilationProfile] = Try {
      val source = Source.fromFile(profilingOutputFile)
      try {
        val lines = source.getLines().toList
        
        var totalTime = 0L
        val macroProfiles = mutable.ListBuffer[MacroExpansionProfile]()
        val phaseTimings = mutable.Map[String, Long]()
        val memoryUsage = mutable.Map[String, Long]()
        
        lines.foreach { line =>
          // Parse macro expansion data
          if (line.contains("macro expansion")) {
            val parts = line.split("\\s+")
            if (parts.length >= 4) {
              val macroName = parts(0)
              val timeMs = parts(2).toLong
              val count = parts(3).toInt
              
              macroProfiles += MacroExpansionProfile(
                macroName = macroName,
                expansionCount = count,
                totalTimeMs = timeMs,
                averageTimeMs = timeMs.toDouble / count,
                maxTimeMs = timeMs, // Simplified - would need more detailed parsing
                memoryAllocatedBytes = 0L // Would need memory profiling data
              )
            }
          }
          
          // Parse phase timings
          if (line.contains("phase:")) {
            val parts = line.split("\\s+")
            if (parts.length >= 3) {
              val phaseName = parts(1)
              val timeMs = parts(2).toLong
              phaseTimings(phaseName) = timeMs
              totalTime += timeMs
            }
          }
          
          // Parse memory usage
          if (line.contains("memory:")) {
            val parts = line.split("\\s+")
            if (parts.length >= 3) {
              val component = parts(1)
              val bytes = parts(2).toLong
              memoryUsage(component) = bytes
            }
          }
        }
        
        CompilationProfile(
          totalCompilationTimeMs = totalTime,
          macroExpansions = macroProfiles.toList,
          phaseTimings = phaseTimings.toMap,
          memoryUsage = memoryUsage.toMap
        )
        
      } finally {
        source.close()
      }
    }
    
    /**
     * Identifies performance bottlenecks from profiling data.
     */
    def identifyBottlenecks(profile: CompilationProfile): List[PerformanceBottleneck] = {
      val bottlenecks = mutable.ListBuffer[PerformanceBottleneck]()
      
      // Identify slow macro expansions
      profile.macroExpansions.foreach { macro =>
        if (macro.averageTimeMs > 100) {
          bottlenecks += PerformanceBottleneck(
            category = "macro",
            description = s"Slow macro expansion: ${macro.macroName}",
            impact = macro.totalTimeMs,
            recommendation = "Consider optimizing macro implementation or reducing usage"
          )
        }
      }
      
      // Identify slow compilation phases
      profile.phaseTimings.foreach { case (phase, timeMs) =>
        val percentage = (timeMs.toDouble / profile.totalCompilationTimeMs) * 100
        if (percentage > 20) {
          bottlenecks += PerformanceBottleneck(
            category = "phase",
            description = s"Slow compilation phase: $phase (${percentage.toInt}% of total time)",
            impact = timeMs,
            recommendation = "Investigate phase-specific optimizations"
          )
        }
      }
      
      // Identify high memory usage
      profile.memoryUsage.foreach { case (component, bytes) =>
        val mb = bytes / (1024 * 1024)
        if (mb > 100) {
          bottlenecks += PerformanceBottleneck(
            category = "memory",
            description = s"High memory usage in $component: ${mb}MB",
            impact = bytes,
            recommendation = "Consider memory optimization strategies"
          )
        }
      }
      
      bottlenecks.toList.sortBy(-_.impact)
    }
    
    /**
     * Generates performance report for CI artifacts.
     */
    def generateReport(
      profile: CompilationProfile,
      measurements: List[CompilationTimer.CompilationMeasurement],
      outputFile: String
    ): Unit = {
      val writer = new PrintWriter(new File(outputFile))
      try {
        writer.println("# FlowForge Compilation Performance Report")
        writer.println(s"Generated at: ${Instant.now()}")
        writer.println()
        
        writer.println("## Overall Statistics")
        writer.println(s"Total compilation time: ${profile.totalCompilationTimeMs}ms")
        writer.println(s"Number of macro expansions: ${profile.macroExpansions.map(_.expansionCount).sum}")
        writer.println(s"Average test compilation time: ${measurements.map(_.compilationTime.toMillis).sum / measurements.length}ms")
        writer.println()
        
        writer.println("## Macro Performance")
        profile.macroExpansions.sortBy(-_.totalTimeMs).foreach { macro =>
          writer.println(s"- ${macro.macroName}: ${macro.totalTimeMs}ms (${macro.expansionCount} expansions, avg: ${macro.averageTimeMs}ms)")
        }
        writer.println()
        
        writer.println("## Phase Timings")
        profile.phaseTimings.toList.sortBy(-_._2).foreach { case (phase, timeMs) =>
          val percentage = (timeMs.toDouble / profile.totalCompilationTimeMs) * 100
          writer.println(f"- $phase: ${timeMs}ms (${percentage}%.1f%%)")
        }
        writer.println()
        
        writer.println("## Performance Bottlenecks")
        val bottlenecks = identifyBottlenecks(profile)
        if (bottlenecks.nonEmpty) {
          bottlenecks.foreach { bottleneck =>
            writer.println(s"- **${bottleneck.category.toUpperCase()}**: ${bottleneck.description}")
            writer.println(s"  Impact: ${bottleneck.impact}")
            writer.println(s"  Recommendation: ${bottleneck.recommendation}")
            writer.println()
          }
        } else {
          writer.println("No significant performance bottlenecks identified.")
        }
        
        writer.println("## Test Results by Complexity")
        val groupedMeasurements = measurements.groupBy(_.schemaComplexity.category)
        ComplexityCategory.Simple :: ComplexityCategory.Medium :: ComplexityCategory.Complex :: ComplexityCategory.Extreme :: Nil foreach { category =>
          groupedMeasurements.get(category).foreach { categoryMeasurements =>
            writer.println(s"### ${category.toString} Schemas")
            categoryMeasurements.foreach { m =>
              writer.println(s"- ${m.testName}: ${m.compilationTime.toMillis}ms, ${m.memoryUsed / (1024 * 1024)}MB")
            }
            writer.println()
          }
        }
        
      } finally {
        writer.close()
      }
    }
  }

  case class PerformanceBottleneck(
    category: String,
    description: String,
    impact: Long,
    recommendation: String
  )
}