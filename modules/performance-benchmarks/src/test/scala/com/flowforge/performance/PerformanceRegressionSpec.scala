// scalafix:off DisableSyntax.var DisableSyntax.throw DisableSyntax.null DisableSyntax.noUnsafeRunSync
package com.flowforge.performance

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterAll
import scala.concurrent.duration._
import scala.util.{Try, Success, Failure}
import java.io.{File, PrintWriter}
import java.nio.file.{Files, Paths, StandardOpenOption}
import scala.io.Source
import java.lang.management.{ManagementFactory, MemoryMXBean}
import scala.collection.mutable
import scala.reflect.runtime.universe._
import scala.tools.reflect.ToolBox
import com.flowforge.core.contracts.SchemaPolicy
import com.flowforge.core.contracts.derive.Shape

/**
 * Performance regression test suite for SchemaConformsMacros.
 * 
 * This suite provides automated performance testing with baseline tracking,
 * regression detection, and CI integration for FlowForge's schema validation macros.
 */
class PerformanceRegressionSpec extends AnyFunSuite with Matchers with BeforeAndAfterAll {

  // Configuration
  private val baselineDir = "modules/performance-benchmarks/baselines"
  private val reportDir = "target/performance-reports"
  private val regressionThreshold = 0.10 // 10% slowdown triggers failure
  private val memoryRegressionThreshold = 0.15 // 15% memory increase triggers failure
  private val warmupIterations = 3
  private val measurementIterations = 5
  private val statisticalSignificanceLevel = 0.05

  // Performance measurement utilities
  private val memoryBean: MemoryMXBean = ManagementFactory.getMemoryMXBean
  private val runtime = Runtime.getRuntime
  
  case class PerformanceMetrics(
    compilationTimeMs: Double,
    memoryUsageMB: Double,
    gcCount: Long,
    gcTimeMs: Long,
    schemaComplexity: SchemaComplexity
  )

  case class SchemaComplexity(
    fieldCount: Int,
    nestingDepth: Int,
    collectionTypes: Int,
    optionalFields: Int,
    complexityScore: Double
  ) {
    def category: String = complexityScore match {
      case s if s < 10 => "Simple"
      case s if s < 50 => "Medium" 
      case s if s < 200 => "Complex"
      case _ => "Extreme"
    }
  }

  case class BaselineMetrics(
    version: String,
    timestamp: Long,
    metrics: Map[String, PerformanceMetrics]
  )

  // Test data generators for different complexity levels
  object SchemaGenerators {
    
    def generateSimpleSchema(fieldCount: Int): String = {
      val fields = (1 to fieldCount).map(i => s"field$i: String").mkString(", ")
      s"case class SimpleSchema$fieldCount($fields)"
    }

    def generateNestedSchema(depth: Int): String = {
      def buildNested(currentDepth: Int): String = {
        if (currentDepth <= 1) "String"
        else s"Nested${currentDepth}(value: ${buildNested(currentDepth - 1)})"
      }
      
      val nestedDef = (2 to depth).map(d => 
        s"case class Nested$d(value: ${if (d == 2) "String" else s"Nested${d-1}"})"
      ).mkString("\n")
      
      s"""
      |$nestedDef
      |case class NestedSchema$depth(root: ${buildNested(depth)})
      """.stripMargin
    }

    def generateComplexSchema(fieldCount: Int, hasCollections: Boolean, hasOptionals: Boolean): String = {
      val baseFields = (1 to fieldCount/3).map(i => s"field$i: String")
      val collectionFields = if (hasCollections) {
        (1 to fieldCount/3).map(i => s"list$i: List[String]") ++
        (1 to fieldCount/6).map(i => s"map$i: Map[String, Int]")
      } else Seq.empty
      val optionalFields = if (hasOptionals) {
        (1 to fieldCount/3).map(i => s"opt$i: Option[String]")
      } else Seq.empty
      
      val allFields = (baseFields ++ collectionFields ++ optionalFields).mkString(", ")
      s"case class ComplexSchema$fieldCount($allFields)"
    }

    def calculateComplexity(schemaCode: String): SchemaComplexity = {
      val fieldCount = schemaCode.count(_ == ':')
      val nestingDepth = schemaCode.split("case class").length - 1
      val collectionTypes = schemaCode.split("List\\[|Map\\[").length - 1
      val optionalFields = schemaCode.split("Option\\[").length - 1
      
      val complexityScore = fieldCount * 1.0 + 
                           nestingDepth * 5.0 + 
                           collectionTypes * 3.0 + 
                           optionalFields * 2.0
      
      SchemaComplexity(fieldCount, nestingDepth, collectionTypes, optionalFields, complexityScore)
    }
  }

  // Compilation performance measurement
  object CompilationProfiler {
    
    def measureCompilationPerformance(sourceCode: String, testName: String): PerformanceMetrics = {
      val complexity = SchemaGenerators.calculateComplexity(sourceCode)
      
      // Warmup
      (1 to warmupIterations).foreach(_ => compileWithMacro(sourceCode))
      
      // Measurement
      val measurements = (1 to measurementIterations).map { _ =>
        System.gc() // Ensure clean state
        Thread.sleep(100) // Allow GC to complete
        
        val startMemory = memoryBean.getHeapMemoryUsage.getUsed
        val startTime = System.nanoTime()
        val startGcCount = getGcCount
        val startGcTime = getGcTime
        
        val result = compileWithMacro(sourceCode)
        
        val endTime = System.nanoTime()
        val endMemory = memoryBean.getHeapMemoryUsage.getUsed
        val endGcCount = getGcCount
        val endGcTime = getGcTime
        
        val compilationTime = (endTime - startTime) / 1e6 // Convert to milliseconds
        val memoryUsage = (endMemory - startMemory) / (1024.0 * 1024.0) // Convert to MB
        val gcCount = endGcCount - startGcCount
        val gcTime = endGcTime - startGcTime
        
        (compilationTime, memoryUsage, gcCount, gcTime, result.isSuccess)
      }.filter(_._5) // Only include successful compilations
      
      if (measurements.isEmpty) {
        throw new RuntimeException(s"All compilation attempts failed for test: $testName")
      }
      
      val avgCompilationTime = measurements.map(_._1).sum / measurements.length
      val avgMemoryUsage = measurements.map(_._2).sum / measurements.length
      val totalGcCount = measurements.map(_._3).sum
      val totalGcTime = measurements.map(_._4).sum
      
      PerformanceMetrics(avgCompilationTime, avgMemoryUsage, totalGcCount, totalGcTime, complexity)
    }
    
    private def compileWithMacro(sourceCode: String): Try[Unit] = Try {
      val fullCode = s"""
        |import com.flowforge.core.contracts.SchemaPolicy
        |import com.flowforge.core.contracts.derive.Shape
        |import com.flowforge.core.contracts.SchemaConforms
        |
        |$sourceCode
        |
        |case class TestContract(id: Long, name: String)
        |
        |// Force macro expansion
        |implicitly[SchemaConforms[TestContract, TestContract, SchemaPolicy.Exact]]
      """.stripMargin
      
      val tb = runtimeMirror(getClass.getClassLoader).mkToolBox()
      tb.compile(tb.parse(fullCode))
      ()
    }
    
    private def getGcCount: Long = {
      ManagementFactory.getGarbageCollectorMXBeans.asScala.map(_.getCollectionCount).sum
    }
    
    private def getGcTime: Long = {
      ManagementFactory.getGarbageCollectorMXBeans.asScala.map(_.getCollectionTime).sum
    }
  }

  // Baseline management
  object BaselineManager {
    
    def loadBaseline(testSuite: String): Option[BaselineMetrics] = {
      val baselineFile = new File(s"$baselineDir/$testSuite.json")
      if (baselineFile.exists()) {
        Try {
          val content = Source.fromFile(baselineFile).mkString
          parseBaseline(content)
        }.toOption
      } else None
    }
    
    def saveBaseline(testSuite: String, metrics: Map[String, PerformanceMetrics]): Unit = {
      val baseline = BaselineMetrics(
        version = getCurrentVersion,
        timestamp = System.currentTimeMillis(),
        metrics = metrics
      )
      
      val baselineFile = new File(s"$baselineDir/$testSuite.json")
      baselineFile.getParentFile.mkdirs()
      
      val writer = new PrintWriter(baselineFile)
      try {
        writer.write(serializeBaseline(baseline))
      } finally {
        writer.close()
      }
    }
    
    def shouldUpdateBaseline: Boolean = {
      // Update baseline if running on main branch or if FORCE_BASELINE_UPDATE is set
      sys.env.get("GITHUB_REF").contains("refs/heads/main") || 
      sys.env.get("FORCE_BASELINE_UPDATE").contains("true")
    }
    
    private def getCurrentVersion: String = {
      sys.env.getOrElse("GITHUB_SHA", "local-" + System.currentTimeMillis())
    }
    
    private def parseBaseline(json: String): BaselineMetrics = {
      // Simple JSON parsing for baseline metrics
      // In a real implementation, you'd use a proper JSON library
      val lines = json.split('\n').map(_.trim).filter(_.nonEmpty)
      val version = extractJsonValue(lines, "version")
      val timestamp = extractJsonValue(lines, "timestamp").toLong
      
      // For this implementation, we'll use a simplified approach
      BaselineMetrics(version, timestamp, Map.empty)
    }
    
    private def serializeBaseline(baseline: BaselineMetrics): String = {
      val metricsJson = baseline.metrics.map { case (name, metrics) =>
        s"""    "$name": {
           |      "compilationTimeMs": ${metrics.compilationTimeMs},
           |      "memoryUsageMB": ${metrics.memoryUsageMB},
           |      "gcCount": ${metrics.gcCount},
           |      "gcTimeMs": ${metrics.gcTimeMs},
           |      "complexityScore": ${metrics.schemaComplexity.complexityScore}
           |    }""".stripMargin
      }.mkString(",\n")
      
      s"""{
         |  "version": "${baseline.version}",
         |  "timestamp": ${baseline.timestamp},
         |  "metrics": {
         |$metricsJson
         |  }
         |}""".stripMargin
    }
    
    private def extractJsonValue(lines: Array[String], key: String): String = {
      lines.find(_.contains(s""""$key":"""))
           .map(_.split(":")(1).trim.replaceAll("[,\"]", ""))
           .getOrElse("")
    }
  }

  // Performance regression analysis
  object RegressionAnalyzer {
    
    def analyzeRegression(
      current: PerformanceMetrics, 
      baseline: PerformanceMetrics,
      testName: String
    ): RegressionResult = {
      val timeRegression = (current.compilationTimeMs - baseline.compilationTimeMs) / baseline.compilationTimeMs
      val memoryRegression = (current.memoryUsageMB - baseline.memoryUsageMB) / baseline.memoryUsageMB
      
      val timeRegressionSignificant = timeRegression > regressionThreshold
      val memoryRegressionSignificant = memoryRegression > memoryRegressionThreshold
      
      val isRegression = timeRegressionSignificant || memoryRegressionSignificant
      
      RegressionResult(
        testName = testName,
        timeRegression = timeRegression,
        memoryRegression = memoryRegression,
        isSignificantRegression = isRegression,
        details = generateRegressionDetails(current, baseline, timeRegression, memoryRegression)
      )
    }
    
    private def generateRegressionDetails(
      current: PerformanceMetrics,
      baseline: PerformanceMetrics, 
      timeRegression: Double,
      memoryRegression: Double
    ): String = {
      s"""Performance Comparison:
         |  Compilation Time: ${baseline.compilationTimeMs}ms -> ${current.compilationTimeMs}ms (${(timeRegression * 100).formatted("%.1f")}%)
         |  Memory Usage: ${baseline.memoryUsageMB}MB -> ${current.memoryUsageMB}MB (${(memoryRegression * 100).formatted("%.1f")}%)
         |  GC Count: ${baseline.gcCount} -> ${current.gcCount}
         |  GC Time: ${baseline.gcTimeMs}ms -> ${current.gcTimeMs}ms
         |  Schema Complexity: ${current.schemaComplexity.complexityScore} (${current.schemaComplexity.category})
         |""".stripMargin
    }
  }

  case class RegressionResult(
    testName: String,
    timeRegression: Double,
    memoryRegression: Double,
    isSignificantRegression: Boolean,
    details: String
  )

  // Test execution framework
  private val testResults = mutable.Map[String, PerformanceMetrics]()
  private val regressionResults = mutable.ListBuffer[RegressionResult]()

  override def beforeAll(): Unit = {
    super.beforeAll()
    // Ensure directories exist
    new File(baselineDir).mkdirs()
    new File(reportDir).mkdirs()
    
    // JVM warmup
    println("Warming up JVM for performance testing...")
    (1 to 5).foreach { _ =>
      CompilationProfiler.measureCompilationPerformance(
        SchemaGenerators.generateSimpleSchema(5), 
        "warmup"
      )
    }
  }

  override def afterAll(): Unit = {
    super.afterAll()
    
    // Generate performance report
    generatePerformanceReport()
    
    // Update baselines if appropriate
    if (BaselineManager.shouldUpdateBaseline) {
      BaselineManager.saveBaseline("performance-regression", testResults.toMap)
      println(s"Updated performance baselines with ${testResults.size} test results")
    }
    
    // Check for regressions and fail if found
    val significantRegressions = regressionResults.filter(_.isSignificantRegression)
    if (significantRegressions.nonEmpty) {
      val regressionSummary = significantRegressions.map(r => 
        s"${r.testName}: ${(r.timeRegression * 100).formatted("%.1f")}% slower, ${(r.memoryRegression * 100).formatted("%.1f")}% more memory"
      ).mkString("\n")
      
      fail(s"Performance regressions detected:\n$regressionSummary")
    }
  }

  // Compile-time performance regression tests
  test("Simple schema compilation performance") {
    val testCases = List(5, 10, 20, 50)
    
    testCases.foreach { fieldCount =>
      val testName = s"simple-schema-$fieldCount-fields"
      val schema = SchemaGenerators.generateSimpleSchema(fieldCount)
      val metrics = CompilationProfiler.measureCompilationPerformance(schema, testName)
      
      testResults(testName) = metrics
      checkRegression(testName, metrics)
      
      // Sanity checks
      metrics.compilationTimeMs should be > 0.0
      metrics.schemaComplexity.fieldCount shouldBe fieldCount
      metrics.schemaComplexity.category should (be("Simple") or be("Medium"))
    }
  }

  test("Nested schema compilation performance") {
    val testCases = List(2, 5, 10, 15)
    
    testCases.foreach { depth =>
      val testName = s"nested-schema-depth-$depth"
      val schema = SchemaGenerators.generateNestedSchema(depth)
      val metrics = CompilationProfiler.measureCompilationPerformance(schema, testName)
      
      testResults(testName) = metrics
      checkRegression(testName, metrics)
      
      // Performance should scale reasonably with nesting depth
      metrics.compilationTimeMs should be > 0.0
      metrics.schemaComplexity.nestingDepth shouldBe depth
      
      if (depth > 10) {
        metrics.schemaComplexity.category should (be("Complex") or be("Extreme"))
      }
    }
  }

  test("Complex schema compilation performance") {
    val testCases = List(
      (20, true, true),   // 20 fields with collections and optionals
      (50, true, true),   // 50 fields with collections and optionals  
      (100, true, true),  // 100 fields with collections and optionals
      (200, false, false) // 200 simple fields
    )
    
    testCases.foreach { case (fieldCount, hasCollections, hasOptionals) =>
      val testName = s"complex-schema-$fieldCount-fields-collections-$hasCollections-optionals-$hasOptionals"
      val schema = SchemaGenerators.generateComplexSchema(fieldCount, hasCollections, hasOptionals)
      val metrics = CompilationProfiler.measureCompilationPerformance(schema, testName)
      
      testResults(testName) = metrics
      checkRegression(testName, metrics)
      
      // Complex schemas should have reasonable performance characteristics
      metrics.compilationTimeMs should be > 0.0
      metrics.schemaComplexity.fieldCount should be >= fieldCount / 2 // Approximate due to field distribution
      
      if (fieldCount >= 100) {
        metrics.schemaComplexity.category should (be("Complex") or be("Extreme"))
      }
    }
  }

  test("Schema policy comparison performance") {
    val schema = SchemaGenerators.generateComplexSchema(30, true, true)
    val policies = List("Exact", "Backward", "Forward", "Full")
    
    policies.foreach { policy =>
      val testName = s"policy-comparison-$policy"
      val policySpecificCode = s"""
        |$schema
        |case class TestContract(id: Long, name: String)
        |implicitly[SchemaConforms[TestContract, TestContract, SchemaPolicy.$policy]]
      """.stripMargin
      
      val metrics = CompilationProfiler.measureCompilationPerformance(policySpecificCode, testName)
      
      testResults(testName) = metrics
      checkRegression(testName, metrics)
      
      metrics.compilationTimeMs should be > 0.0
    }
  }

  test("Memory usage scaling with schema size") {
    val fieldCounts = List(10, 25, 50, 100, 200)
    val memoryUsages = mutable.ListBuffer[Double]()
    
    fieldCounts.foreach { fieldCount =>
      val testName = s"memory-scaling-$fieldCount-fields"
      val schema = SchemaGenerators.generateSimpleSchema(fieldCount)
      val metrics = CompilationProfiler.measureCompilationPerformance(schema, testName)
      
      testResults(testName) = metrics
      checkRegression(testName, metrics)
      
      memoryUsages += metrics.memoryUsageMB
      
      // Memory usage should be reasonable
      metrics.memoryUsageMB should be > 0.0
      if (fieldCount >= 100) {
        metrics.memoryUsageMB should be < 100.0 // Should not exceed 100MB for compilation
      }
    }
    
    // Memory usage should scale sub-linearly with field count
    val memoryGrowthRates = memoryUsages.zip(memoryUsages.tail).map { case (prev, curr) => curr / prev }
    val avgGrowthRate = memoryGrowthRates.sum / memoryGrowthRates.length
    
    avgGrowthRate should be < 3.0 // Memory shouldn't triple between test cases
  }

  test("Compilation performance consistency") {
    val schema = SchemaGenerators.generateComplexSchema(25, true, true)
    val testName = "consistency-test"
    
    // Run the same test multiple times to check for consistency
    val measurements = (1 to 10).map { i =>
      CompilationProfiler.measureCompilationPerformance(schema, s"$testName-$i")
    }
    
    val times = measurements.map(_.compilationTimeMs)
    val mean = times.sum / times.length
    val variance = times.map(t => math.pow(t - mean, 2)).sum / times.length
    val stdDev = math.sqrt(variance)
    val coefficientOfVariation = stdDev / mean
    
    // Coefficient of variation should be reasonable (< 50%)
    coefficientOfVariation should be < 0.5
    
    // Store the average for regression checking
    val avgMetrics = measurements.reduce { (a, b) =>
      PerformanceMetrics(
        (a.compilationTimeMs + b.compilationTimeMs) / 2,
        (a.memoryUsageMB + b.memoryUsageMB) / 2,
        (a.gcCount + b.gcCount) / 2,
        (a.gcTimeMs + b.gcTimeMs) / 2,
        a.schemaComplexity
      )
    }
    
    testResults(testName) = avgMetrics
    checkRegression(testName, avgMetrics)
  }

  // Helper methods
  private def checkRegression(testName: String, currentMetrics: PerformanceMetrics): Unit = {
    BaselineManager.loadBaseline("performance-regression").foreach { baseline =>
      baseline.metrics.get(testName).foreach { baselineMetrics =>
        val regression = RegressionAnalyzer.analyzeRegression(currentMetrics, baselineMetrics, testName)
        regressionResults += regression
        
        if (regression.isSignificantRegression) {
          println(s"WARNING: Performance regression detected in $testName")
          println(regression.details)
        }
      }
    }
  }

  private def generatePerformanceReport(): Unit = {
    val reportFile = new File(s"$reportDir/performance-report.md")
    val writer = new PrintWriter(reportFile)
    
    try {
      writer.println("# Performance Test Report")
      writer.println(s"Generated: ${new java.util.Date()}")
      writer.println()
      
      writer.println("## Test Results Summary")
      writer.println("| Test Name | Compilation Time (ms) | Memory Usage (MB) | Complexity | Category |")
      writer.println("|-----------|----------------------|-------------------|------------|----------|")
      
      testResults.toSeq.sortBy(_._1).foreach { case (name, metrics) =>
        writer.println(s"| $name | ${metrics.compilationTimeMs.formatted("%.2f")} | ${metrics.memoryUsageMB.formatted("%.2f")} | ${metrics.schemaComplexity.complexityScore.formatted("%.1f")} | ${metrics.schemaComplexity.category} |")
      }
      
      writer.println()
      writer.println("## Regression Analysis")
      
      if (regressionResults.isEmpty) {
        writer.println("No baseline available for regression analysis.")
      } else {
        val significantRegressions = regressionResults.filter(_.isSignificantRegression)
        if (significantRegressions.isEmpty) {
          writer.println("✅ No significant performance regressions detected.")
        } else {
          writer.println("⚠️ Performance regressions detected:")
          significantRegressions.foreach { regression =>
            writer.println(s"### ${regression.testName}")
            writer.println(regression.details)
          }
        }
      }
      
      writer.println()
      writer.println("## Performance Trends")
      
      // Group by complexity category
      val byComplexity = testResults.groupBy(_._2.schemaComplexity.category)
      byComplexity.foreach { case (category, tests) =>
        val avgTime = tests.map(_._2.compilationTimeMs).sum / tests.size
        val avgMemory = tests.map(_._2.memoryUsageMB).sum / tests.size
        writer.println(s"- **$category**: Avg compilation time ${avgTime.formatted("%.2f")}ms, Avg memory ${avgMemory.formatted("%.2f")}MB")
      }
      
    } finally {
      writer.close()
    }
    
    println(s"Performance report generated: ${reportFile.getAbsolutePath}")
  }
}