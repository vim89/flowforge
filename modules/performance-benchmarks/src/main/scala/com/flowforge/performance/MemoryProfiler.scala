package com.flowforge.performance

import cats.effect.{ IO, Sync }
import cats.syntax.all._
import com.flowforge.infrastructure.MetricsCollector
import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Blackhole

import java.lang.management.{ GarbageCollectorMXBean, ManagementFactory, MemoryMXBean, MemoryUsage }
import java.util.concurrent.TimeUnit
import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.util.Random

/**
 * Memory profiler for schema validation performance monitoring.
 * Provides comprehensive memory usage tracking, GC analysis, and large schema testing.
 */
object MemoryProfiler {

  /**
   * Memory snapshot containing heap usage and GC statistics.
   */
  case class MemorySnapshot(
    heapUsed: Long,
    heapMax: Long,
    heapCommitted: Long,
    nonHeapUsed: Long,
    nonHeapMax: Long,
    nonHeapCommitted: Long,
    gcCollections: Map[String, Long],
    gcTime: Map[String, Long],
    timestamp: Long = System.currentTimeMillis()
  ) {
    def heapUtilization: Double = if (heapMax > 0) heapUsed.toDouble / heapMax else 0.0
    def nonHeapUtilization: Double = if (nonHeapMax > 0) nonHeapUsed.toDouble / nonHeapMax else 0.0
    
    def diff(other: MemorySnapshot): MemoryDiff = MemoryDiff(
      heapUsedDelta = this.heapUsed - other.heapUsed,
      heapCommittedDelta = this.heapCommitted - other.heapCommitted,
      nonHeapUsedDelta = this.nonHeapUsed - other.nonHeapUsed,
      gcCollectionsDelta = this.gcCollections.map { case (name, count) =>
        name -> (count - other.gcCollections.getOrElse(name, 0L))
      },
      gcTimeDelta = this.gcTime.map { case (name, time) =>
        name -> (time - other.gcTime.getOrElse(name, 0L))
      },
      durationMs = this.timestamp - other.timestamp
    )
  }

  case class MemoryDiff(
    heapUsedDelta: Long,
    heapCommittedDelta: Long,
    nonHeapUsedDelta: Long,
    gcCollectionsDelta: Map[String, Long],
    gcTimeDelta: Map[String, Long],
    durationMs: Long
  ) {
    def heapUsedMB: Double = heapUsedDelta / (1024.0 * 1024.0)
    def heapCommittedMB: Double = heapCommittedDelta / (1024.0 * 1024.0)
    def nonHeapUsedMB: Double = nonHeapUsedDelta / (1024.0 * 1024.0)
    def totalGcCollections: Long = gcCollectionsDelta.values.sum
    def totalGcTimeMs: Long = gcTimeDelta.values.sum
  }

  /**
   * Memory profiler with JVM monitoring capabilities.
   */
  class MemoryProfiler[F[_]: Sync](metricsCollector: MetricsCollector[F]) {
    private val memoryBean: MemoryMXBean = ManagementFactory.getMemoryMXBean
    private val gcBeans: List[GarbageCollectorMXBean] = ManagementFactory.getGarbageCollectorMXBeans.asScala.toList

    def takeSnapshot(): F[MemorySnapshot] = Sync[F].delay {
      val heapUsage = memoryBean.getHeapMemoryUsage
      val nonHeapUsage = memoryBean.getNonHeapMemoryUsage
      
      val gcCollections = gcBeans.map(bean => bean.getName -> bean.getCollectionCount).toMap
      val gcTime = gcBeans.map(bean => bean.getName -> bean.getCollectionTime).toMap

      MemorySnapshot(
        heapUsed = heapUsage.getUsed,
        heapMax = heapUsage.getMax,
        heapCommitted = heapUsage.getCommitted,
        nonHeapUsed = nonHeapUsage.getUsed,
        nonHeapMax = nonHeapUsage.getMax,
        nonHeapCommitted = nonHeapUsage.getCommitted,
        gcCollections = gcCollections,
        gcTime = gcTime
      )
    }

    def profileOperation[A](operationName: String)(operation: F[A]): F[(A, MemoryDiff)] = {
      for {
        before <- takeSnapshot()
        _      <- metricsCollector.recordGauge(s"memory_before_${operationName}_heap_mb", before.heapUsed / (1024.0 * 1024.0))
        result <- operation
        after  <- takeSnapshot()
        diff   = after.diff(before)
        _      <- recordMemoryMetrics(operationName, diff)
      } yield (result, diff)
    }

    def detectMemoryLeak(snapshots: List[MemorySnapshot], thresholdMB: Double = 100.0): F[Option[String]] = Sync[F].delay {
      if (snapshots.length < 3) None
      else {
        val heapTrend = snapshots.sliding(2).map { case List(prev, curr) =>
          (curr.heapUsed - prev.heapUsed) / (1024.0 * 1024.0)
        }.toList

        val avgGrowth = heapTrend.sum / heapTrend.length
        if (avgGrowth > thresholdMB) {
          Some(s"Potential memory leak detected: average heap growth ${avgGrowth:.2f} MB per operation")
        } else None
      }
    }

    def forceGC(): F[Unit] = Sync[F].delay {
      System.gc()
      Thread.sleep(100) // Give GC time to complete
    }

    private def recordMemoryMetrics(operationName: String, diff: MemoryDiff): F[Unit] = {
      for {
        _ <- metricsCollector.recordGauge(s"memory_${operationName}_heap_delta_mb", diff.heapUsedMB)
        _ <- metricsCollector.recordGauge(s"memory_${operationName}_nonheap_delta_mb", diff.nonHeapUsedMB)
        _ <- metricsCollector.recordGauge(s"memory_${operationName}_gc_collections", diff.totalGcCollections.toDouble)
        _ <- metricsCollector.recordGauge(s"memory_${operationName}_gc_time_ms", diff.totalGcTimeMs.toDouble)
        _ <- metricsCollector.recordDuration(s"memory_${operationName}_duration", diff.durationMs.millis)
      } yield ()
    }
  }

  /**
   * Large schema test case generators for memory profiling.
   */
  object LargeSchemaGenerators {
    
    /**
     * Generates case class source code with specified number of fields.
     */
    def generateLargeRecord(className: String, fieldCount: Int): String = {
      val fields = (1 to fieldCount).map { i =>
        val fieldType = i % 7 match {
          case 0 => "String"
          case 1 => "Int"
          case 2 => "Long"
          case 3 => "Double"
          case 4 => "Boolean"
          case 5 => "Option[String]"
          case 6 => "List[Int]"
        }
        s"field$i: $fieldType"
      }.mkString(", ")

      s"case class $className($fields)"
    }

    /**
     * Generates deeply nested case class structures.
     */
    def generateNestedRecord(baseName: String, depth: Int): String = {
      def generateLevel(level: Int): String = {
        if (level == 0) {
          s"case class ${baseName}Leaf(value: String, count: Int, flag: Boolean)"
        } else {
          val nextLevel = generateLevel(level - 1)
          val className = s"$baseName$level"
          s"""$nextLevel
             |case class $className(
             |  id: String,
             |  nested: ${baseName}${level - 1},
             |  optionalNested: Option[${baseName}${level - 1}],
             |  listNested: List[${baseName}${level - 1}],
             |  mapNested: Map[String, ${baseName}${level - 1}]
             |)""".stripMargin
        }
      }
      generateLevel(depth)
    }

    /**
     * Generates complex collection-based schemas.
     */
    def generateComplexCollections(className: String): String = {
      s"""case class $className(
         |  simpleList: List[String],
         |  nestedList: List[List[Int]],
         |  deeplyNestedList: List[List[List[String]]],
         |  optionalList: Option[List[String]],
         |  listOfOptions: List[Option[String]],
         |  simpleMap: Map[String, Int],
         |  nestedMap: Map[String, Map[String, Int]],
         |  complexMap: Map[String, List[Option[Map[String, Int]]]],
         |  mixedCollections: List[Map[String, Option[List[Int]]]],
         |  extremeNesting: Option[List[Map[String, Option[List[Map[String, Int]]]]]]
         |)""".stripMargin
    }

    /**
     * Generates realistic data engineering schemas.
     */
    def generateUserProfileSchema(): String = {
      """case class UserProfile(
        |  userId: String,
        |  email: String,
        |  firstName: Option[String],
        |  lastName: Option[String],
        |  dateOfBirth: Option[java.time.Instant],
        |  phoneNumbers: List[String],
        |  addresses: List[Address],
        |  preferences: Map[String, String],
        |  metadata: Map[String, Any],
        |  createdAt: java.time.Instant,
        |  updatedAt: java.time.Instant,
        |  isActive: Boolean,
        |  tags: List[String],
        |  customFields: Map[String, Option[String]]
        |)
        |
        |case class Address(
        |  street: String,
        |  city: String,
        |  state: Option[String],
        |  zipCode: String,
        |  country: String,
        |  isPrimary: Boolean
        |)""".stripMargin
    }

    def generateEventDataSchema(): String = {
      """case class EventData(
        |  eventId: String,
        |  eventType: String,
        |  timestamp: java.time.Instant,
        |  userId: Option[String],
        |  sessionId: Option[String],
        |  properties: Map[String, Any],
        |  context: EventContext,
        |  metrics: List[Metric],
        |  dimensions: Map[String, String],
        |  nested: Option[NestedEventData]
        |)
        |
        |case class EventContext(
        |  userAgent: Option[String],
        |  ipAddress: Option[String],
        |  referrer: Option[String],
        |  page: Option[PageInfo],
        |  device: Option[DeviceInfo],
        |  location: Option[LocationInfo]
        |)
        |
        |case class PageInfo(
        |  url: String,
        |  title: Option[String],
        |  path: String,
        |  queryParams: Map[String, String]
        |)
        |
        |case class DeviceInfo(
        |  deviceType: String,
        |  os: Option[String],
        |  browser: Option[String],
        |  screenResolution: Option[String]
        |)
        |
        |case class LocationInfo(
        |  country: Option[String],
        |  region: Option[String],
        |  city: Option[String],
        |  timezone: Option[String]
        |)
        |
        |case class Metric(
        |  name: String,
        |  value: Double,
        |  unit: Option[String],
        |  tags: Map[String, String]
        |)
        |
        |case class NestedEventData(
        |  level1: Option[Level1],
        |  metadata: Map[String, Any]
        |)
        |
        |case class Level1(
        |  level2: Option[Level2],
        |  data: List[String]
        |)
        |
        |case class Level2(
        |  level3: Option[Level3],
        |  values: Map[String, Int]
        |)
        |
        |case class Level3(
        |  finalData: String,
        |  count: Int
        |)""".stripMargin
    }

    def generateMetricsSchema(): String = {
      """case class MetricsData(
        |  metricName: String,
        |  timestamp: java.time.Instant,
        |  value: Double,
        |  tags: Map[String, String],
        |  dimensions: List[Dimension],
        |  aggregations: Map[String, AggregationResult],
        |  timeSeries: List[TimeSeriesPoint],
        |  metadata: MetricsMetadata
        |)
        |
        |case class Dimension(
        |  name: String,
        |  value: String,
        |  cardinality: Option[Long]
        |)
        |
        |case class AggregationResult(
        |  aggregationType: String,
        |  value: Double,
        |  count: Long,
        |  min: Option[Double],
        |  max: Option[Double],
        |  percentiles: Map[String, Double]
        |)
        |
        |case class TimeSeriesPoint(
        |  timestamp: java.time.Instant,
        |  value: Double,
        |  interpolated: Boolean
        |)
        |
        |case class MetricsMetadata(
        |  source: String,
        |  version: String,
        |  quality: Option[Double],
        |  annotations: Map[String, String],
        |  relatedMetrics: List[String]
        |)""".stripMargin
    }
  }

  /**
   * JMH benchmarks for memory allocation measurement.
   */
  @BenchmarkMode(Array(Mode.AverageTime))
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  @State(Scope.Benchmark)
  @Fork(value = 1, jvmArgs = Array("-Xmx4g", "-XX:+UseG1GC"))
  @Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
  @Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
  class MemoryAllocationBenchmarks {

    private val profiler = new MemoryProfiler[IO](MetricsCollector.noOpCollector[IO])
    private val random = new Random(42)

    @Setup
    def setup(): Unit = {
      // Force GC before benchmarks
      System.gc()
      Thread.sleep(100)
    }

    @Benchmark
    def benchmarkSmallSchemaMemory(bh: Blackhole): Unit = {
      val result = profiler.profileOperation("small_schema") {
        IO {
          // Simulate small schema validation
          val data = (1 to 10).map(i => s"field$i" -> random.nextInt()).toMap
          bh.consume(data)
          data
        }
      }.unsafeRunSync()
      bh.consume(result)
    }

    @Benchmark
    def benchmarkMediumSchemaMemory(bh: Blackhole): Unit = {
      val result = profiler.profileOperation("medium_schema") {
        IO {
          // Simulate medium schema validation (50 fields)
          val data = (1 to 50).map { i =>
            val value = i % 5 match {
              case 0 => random.nextString(10)
              case 1 => random.nextInt()
              case 2 => random.nextLong()
              case 3 => random.nextDouble()
              case 4 => random.nextBoolean()
            }
            s"field$i" -> value
          }.toMap
          bh.consume(data)
          data
        }
      }.unsafeRunSync()
      bh.consume(result)
    }

    @Benchmark
    def benchmarkLargeSchemaMemory(bh: Blackhole): Unit = {
      val result = profiler.profileOperation("large_schema") {
        IO {
          // Simulate large schema validation (200 fields)
          val data = (1 to 200).map { i =>
            val value = i % 7 match {
              case 0 => random.nextString(20)
              case 1 => random.nextInt()
              case 2 => random.nextLong()
              case 3 => random.nextDouble()
              case 4 => random.nextBoolean()
              case 5 => Some(random.nextString(10))
              case 6 => (1 to 5).map(_ => random.nextInt()).toList
            }
            s"field$i" -> value
          }.toMap
          bh.consume(data)
          data
        }
      }.unsafeRunSync()
      bh.consume(result)
    }

    @Benchmark
    def benchmarkExtremeSchemaMemory(bh: Blackhole): Unit = {
      val result = profiler.profileOperation("extreme_schema") {
        IO {
          // Simulate extreme schema validation (500+ fields)
          val data = (1 to 500).map { i =>
            val value = i % 10 match {
              case 0 => random.nextString(50)
              case 1 => random.nextInt()
              case 2 => random.nextLong()
              case 3 => random.nextDouble()
              case 4 => random.nextBoolean()
              case 5 => Some(random.nextString(20))
              case 6 => (1 to 10).map(_ => random.nextInt()).toList
              case 7 => (1 to 5).map(j => s"key$j" -> random.nextString(10)).toMap
              case 8 => Some((1 to 3).map(_ => random.nextDouble()).toList)
              case 9 => (1 to 2).map(_ => (1 to 3).map(_ => random.nextInt()).toList).toList
            }
            s"field$i" -> value
          }.toMap
          bh.consume(data)
          data
        }
      }.unsafeRunSync()
      bh.consume(result)
    }

    @Benchmark
    def benchmarkNestedSchemaMemory(bh: Blackhole): Unit = {
      val result = profiler.profileOperation("nested_schema") {
        IO {
          // Simulate deeply nested schema validation
          def createNestedData(depth: Int): Any = {
            if (depth <= 0) random.nextString(10)
            else Map(
              "id" -> random.nextString(10),
              "value" -> random.nextInt(),
              "nested" -> createNestedData(depth - 1),
              "optionalNested" -> Some(createNestedData(depth - 1)),
              "listNested" -> (1 to 3).map(_ => createNestedData(depth - 1)).toList
            )
          }
          
          val data = createNestedData(10)
          bh.consume(data)
          data
        }
      }.unsafeRunSync()
      bh.consume(result)
    }

    @Benchmark
    def benchmarkCollectionSchemaMemory(bh: Blackhole): Unit = {
      val result = profiler.profileOperation("collection_schema") {
        IO {
          // Simulate complex collection schema validation
          val data = Map(
            "simpleList" -> (1 to 100).map(_.toString).toList,
            "nestedList" -> (1 to 10).map(i => (1 to 10).map(_ => i).toList).toList,
            "deeplyNestedList" -> (1 to 5).map(i => 
              (1 to 5).map(j => 
                (1 to 5).map(_ => s"$i-$j").toList
              ).toList
            ).toList,
            "optionalList" -> Some((1 to 50).map(_.toString).toList),
            "listOfOptions" -> (1 to 50).map(i => if (i % 2 == 0) Some(i.toString) else None).toList,
            "simpleMap" -> (1 to 50).map(i => s"key$i" -> i).toMap,
            "nestedMap" -> (1 to 10).map(i => 
              s"outer$i" -> (1 to 10).map(j => s"inner$j" -> (i * j)).toMap
            ).toMap,
            "complexMap" -> (1 to 5).map(i =>
              s"complex$i" -> (1 to 5).map(j =>
                if (j % 2 == 0) Some((1 to 3).map(k => s"nested$k" -> (i * j * k)).toMap)
                else None
              ).toList
            ).toMap
          )
          bh.consume(data)
          data
        }
      }.unsafeRunSync()
      bh.consume(result)
    }
  }

  /**
   * Memory leak detection utilities.
   */
  object MemoryLeakDetector {
    
    def runLeakDetectionTest[F[_]: Sync](
      profiler: MemoryProfiler[F],
      operation: F[Unit],
      iterations: Int = 100,
      samplingInterval: Int = 10
    ): F[Option[String]] = {
      def runIterations(remaining: Int, snapshots: List[MemorySnapshot]): F[List[MemorySnapshot]] = {
        if (remaining <= 0) Sync[F].pure(snapshots)
        else {
          for {
            _        <- operation
            snapshot <- if (remaining % samplingInterval == 0) profiler.takeSnapshot().map(Some(_)) else Sync[F].pure(None)
            newSnapshots = snapshot.fold(snapshots)(_ :: snapshots)
            result   <- runIterations(remaining - 1, newSnapshots)
          } yield result
        }
      }

      for {
        _         <- profiler.forceGC()
        initial   <- profiler.takeSnapshot()
        snapshots <- runIterations(iterations, List(initial))
        leak      <- profiler.detectMemoryLeak(snapshots.reverse)
      } yield leak
    }
  }

  /**
   * Performance regression detection based on memory patterns.
   */
  object MemoryRegressionDetector {
    
    case class MemoryBaseline(
      operationName: String,
      avgHeapUsageMB: Double,
      maxHeapUsageMB: Double,
      avgGcCollections: Double,
      avgGcTimeMs: Double,
      sampleCount: Int
    )

    case class RegressionResult(
      operationName: String,
      heapUsageRegression: Option[Double],
      gcCollectionRegression: Option[Double],
      gcTimeRegression: Option[Double],
      isRegression: Boolean
    )

    def detectRegression(
      baseline: MemoryBaseline,
      current: MemoryDiff,
      thresholdPercent: Double = 20.0
    ): RegressionResult = {
      val currentHeapMB = current.heapUsedMB
      val currentGcCollections = current.totalGcCollections.toDouble
      val currentGcTimeMs = current.totalGcTimeMs.toDouble

      val heapRegression = if (baseline.avgHeapUsageMB > 0) {
        val change = ((currentHeapMB - baseline.avgHeapUsageMB) / baseline.avgHeapUsageMB) * 100
        if (change > thresholdPercent) Some(change) else None
      } else None

      val gcCollectionRegression = if (baseline.avgGcCollections > 0) {
        val change = ((currentGcCollections - baseline.avgGcCollections) / baseline.avgGcCollections) * 100
        if (change > thresholdPercent) Some(change) else None
      } else None

      val gcTimeRegression = if (baseline.avgGcTimeMs > 0) {
        val change = ((currentGcTimeMs - baseline.avgGcTimeMs) / baseline.avgGcTimeMs) * 100
        if (change > thresholdPercent) Some(change) else None
      } else None

      val isRegression = heapRegression.isDefined || gcCollectionRegression.isDefined || gcTimeRegression.isDefined

      RegressionResult(
        operationName = baseline.operationName,
        heapUsageRegression = heapRegression,
        gcCollectionRegression = gcCollectionRegression,
        gcTimeRegression = gcTimeRegression,
        isRegression = isRegression
      )
    }

    def updateBaseline(baseline: MemoryBaseline, newMeasurement: MemoryDiff): MemoryBaseline = {
      val newCount = baseline.sampleCount + 1
      val weight = 1.0 / newCount
      val oldWeight = 1.0 - weight

      baseline.copy(
        avgHeapUsageMB = baseline.avgHeapUsageMB * oldWeight + newMeasurement.heapUsedMB * weight,
        maxHeapUsageMB = math.max(baseline.maxHeapUsageMB, newMeasurement.heapUsedMB),
        avgGcCollections = baseline.avgGcCollections * oldWeight + newMeasurement.totalGcCollections * weight,
        avgGcTimeMs = baseline.avgGcTimeMs * oldWeight + newMeasurement.totalGcTimeMs * weight,
        sampleCount = newCount
      )
    }
  }
}