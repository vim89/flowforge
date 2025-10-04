/**
 * FlowForge Core Module - Metrics & Observability Types
 *
 * File: modules/core/src/main/scala/com/flowforge/core/types/MetricTypes.scala Package:
 * com.flowforge.core.types
 *
 * This file defines comprehensive metrics and observability types for the FlowForge ecosystem. It provides a
 * foundation for monitoring, alerting, and performance optimization across data pipelines with rich metric
 * aggregation capabilities.
 *
 * Design Patterns Applied:
 *   - Value Object Pattern: Immutable metric data containers
 *   - Composite Pattern: Hierarchical metric aggregation and composition
 *   - Strategy Pattern: Different metric collection strategies per component
 *   - Observer Pattern: Metric collection and notification systems
 *   - Builder Pattern: Fluent API for metric construction and configuration
 *
 * Scala Features Showcased:
 *   - Case Classes: Immutable metric data structures
 *   - ADTs: Type-safe metric classification and categorization
 *   - Monoid: Composable metric aggregation with associative operations
 *   - Type Classes: Polymorphic metric collection across data types
 *   - Generic Programming: Reusable metric patterns and utilities
 *   - Duration Types: Precise timing and performance measurement
 *   - Option Types: Safe handling of optional metric values
 *
 * Innovation Highlights:
 *   - Composable metric aggregation using Monoid patterns
 *   - Type-safe metric dimensions and labels for filtering
 *   - Performance-optimized metric collection with minimal overhead
 *   - Integration with popular monitoring systems (Prometheus, etc.)
 *   - Real-time metric streaming and alerting capabilities
 *   - Data quality metrics with automated threshold detection
 *   - Pipeline performance metrics with bottleneck identification
 *
 * Usage Examples:
 * ```scala
 * // Basic metric creation and aggregation
 * val processingMetric = PipelineMetrics.processing(
 *   recordsProcessed = 1000,
 *   processingTime = 30.seconds,
 *   memoryUsed = 512.MB,
 * )
 *
 * // Composable metric aggregation
 * val totalMetrics = metrics.foldLeft(PipelineMetrics.empty)(_ combine _)
 *
 * // Quality metrics with thresholds
 * val qualityMetric = QualityMetrics.builder
 *   .completeness("customer_id", 0.99)
 *   .uniqueness("transaction_id", 1.0)
 *   .freshness(2.hours)
 *   .build
 *
 * // Performance monitoring
 * val perfMetric = PerformanceMetrics.timed("data_transformation") {
 *   // Your data processing code here
 * }
 * ```
 *
 * @author
 *   FlowForge Team
 * @version 1.0.0
 * @since 2024
 */
package com.flowforge.core.types

import cats.syntax.all._
import cats.{ Monoid, Show }

import java.time.{ Duration, Instant }
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

// ===============================
// METRIC VALUE TYPES
// ===============================

/**
 * Core metric value types for different kinds of measurements. These provide type-safe representations of
 * numeric metrics.
 */
sealed trait MetricValue extends Product with Serializable {
  def asDouble: Double
  def unit: MetricUnit
}

object MetricValue {

  /**
   * Counter metric - monotonically increasing value.
   */
  case class Counter(value: Long, unit: MetricUnit = MetricUnit.Count) extends MetricValue {
    def asDouble: Double                     = value.toDouble
    def increment(amount: Long = 1): Counter = copy(value = value + amount)
  }

  /**
   * Gauge metric - point-in-time value that can go up or down.
   */
  case class Gauge(value: Double, unit: MetricUnit = MetricUnit.Count) extends MetricValue {
    def asDouble: Double                = value
    def update(newValue: Double): Gauge = copy(value = newValue)
    def add(amount: Double): Gauge      = copy(value = value + amount)
  }

  /**
   * Histogram metric - distribution of values over time.
   */
  case class Histogram(
    buckets: Map[Double, Long],
    count: Long,
    sum: Double,
    unit: MetricUnit = MetricUnit.Count)
      extends MetricValue {
    def asDouble: Double = if (count > 0) sum / count else 0.0
    def mean: Double     = asDouble
    def observe(value: Double): Histogram = {
      val updatedBuckets = buckets.map {
        case (boundary, bucketCount) =>
          boundary -> (if (value <= boundary) bucketCount + 1 else bucketCount)
      }
      copy(
        buckets = updatedBuckets,
        count = count + 1,
        sum = sum + value,
      )
    }
  }

  /**
   * Timer metric - specialized histogram for duration measurements.
   */
  case class Timer(
    durations: List[FiniteDuration],
    unit: MetricUnit = MetricUnit.Milliseconds)
      extends MetricValue {
    def asDouble: Double = meanDuration.toMillis.toDouble
    def count: Int       = durations.length
    def totalDuration: FiniteDuration =
      durations.foldLeft(FiniteDuration(0, TimeUnit.MILLISECONDS))(_ + _)
    def meanDuration: FiniteDuration =
      if (durations.nonEmpty)
        FiniteDuration(totalDuration.toMillis / durations.length, TimeUnit.MILLISECONDS)
      else FiniteDuration(0, TimeUnit.MILLISECONDS)
    def maxDuration: FiniteDuration =
      if (durations.isEmpty) FiniteDuration(0, TimeUnit.MILLISECONDS) else durations.max
    def minDuration: FiniteDuration =
      if (durations.isEmpty) FiniteDuration(0, TimeUnit.MILLISECONDS) else durations.min

    def record(duration: FiniteDuration): Timer = copy(durations = durations :+ duration)
  }

  /**
   * Rate metric - value per unit time.
   */
  case class Rate(
    events: Long,
    timeWindow: FiniteDuration,
    unit: MetricUnit = MetricUnit.EventsPerSecond)
      extends MetricValue {
    def asDouble: Double        = events.toDouble / timeWindow.toSeconds
    def eventsPerSecond: Double = asDouble
    def eventsPerMinute: Double = asDouble * 60
    def eventsPerHour: Double   = asDouble * 3600
  }

  // Convenience constructors
  def counter(value: Long): Counter                    = Counter(value)
  def gauge(value: Double): Gauge                      = Gauge(value)
  def timer(durations: List[FiniteDuration]): Timer    = Timer(durations)
  def rate(events: Long, window: FiniteDuration): Rate = Rate(events, window)
}

/**
 * Metric units for proper scaling and display.
 */
sealed trait MetricUnit extends Product with Serializable {
  def symbol: String
  def description: String
}

object MetricUnit {

  // Count-based units
  case object Count extends MetricUnit {
    val symbol      = "count"
    val description = "Number of items"
  }

  case object Percentage extends MetricUnit {
    val symbol      = "%"
    val description = "Percentage (0-100)"
  }

  case object Ratio extends MetricUnit {
    val symbol      = "ratio"
    val description = "Ratio (0.0-1.0)"
  }

  // Time-based units
  case object Nanoseconds extends MetricUnit {
    val symbol      = "ns"
    val description = "Nanoseconds"
  }

  case object Microseconds extends MetricUnit {
    val symbol      = "μs"
    val description = "Microseconds"
  }

  case object Milliseconds extends MetricUnit {
    val symbol      = "ms"
    val description = "Milliseconds"
  }

  case object Seconds extends MetricUnit {
    val symbol      = "s"
    val description = "Seconds"
  }

  case object Minutes extends MetricUnit {
    val symbol      = "min"
    val description = "Minutes"
  }

  case object Hours extends MetricUnit {
    val symbol      = "h"
    val description = "Hours"
  }

  // Size-based units
  case object Bytes extends MetricUnit {
    val symbol      = "B"
    val description = "Bytes"
  }

  case object Kilobytes extends MetricUnit {
    val symbol      = "KB"
    val description = "Kilobytes"
  }

  case object Megabytes extends MetricUnit {
    val symbol      = "MB"
    val description = "Megabytes"
  }

  case object Gigabytes extends MetricUnit {
    val symbol      = "GB"
    val description = "Gigabytes"
  }

  case object Terabytes extends MetricUnit {
    val symbol      = "TB"
    val description = "Terabytes"
  }

  // Rate-based units
  case object EventsPerSecond extends MetricUnit {
    val symbol      = "events/s"
    val description = "Events per second"
  }

  case object RecordsPerSecond extends MetricUnit {
    val symbol      = "records/s"
    val description = "Records per second"
  }

  case object BytesPerSecond extends MetricUnit {
    val symbol      = "B/s"
    val description = "Bytes per second"
  }

  implicit val showMetricUnit: Show[MetricUnit] = Show.show(_.symbol)
}

// ===============================
// METRIC LABELS & DIMENSIONS
// ===============================

/**
 * Metric labels for dimensional filtering and grouping. Enables rich metric queries and aggregation patterns.
 */
case class MetricLabels(labels: Map[String, String] = Map.empty) {

  def add(key: String, value: String): MetricLabels =
    copy(labels = labels + (key -> value))

  def addAll(newLabels: Map[String, String]): MetricLabels =
    copy(labels = labels ++ newLabels)

  def remove(key: String): MetricLabels =
    copy(labels = labels - key)

  def get(key: String): Option[String] = labels.get(key)

  def contains(key: String): Boolean = labels.contains(key)

  def isEmpty: Boolean = labels.isEmpty

  def nonEmpty: Boolean = labels.nonEmpty

  def keys: Set[String] = labels.keySet

  def values: Iterable[String] = labels.values

  def size: Int = labels.size

  /**
   * Match against label patterns.
   */
  def matches(patterns: Map[String, String]): Boolean =
    patterns.forall {
      case (key, pattern) =>
        labels.get(key).exists(_.matches(pattern))
    }

  /**
   * Create a subset with only specified keys.
   */
  def subset(keys: Set[String]): MetricLabels =
    MetricLabels(labels.filter { case (k, _) => keys.contains(k) })

  /**
   * Merge with other labels, giving precedence to other.
   */
  def merge(other: MetricLabels): MetricLabels =
    MetricLabels(labels ++ other.labels)
}

object MetricLabels {

  val empty: MetricLabels = MetricLabels()

  def apply(pairs: (String, String)*): MetricLabels =
    MetricLabels(Map(pairs: _*))

  // Common label creators
  def pipeline(pipelineName: String): MetricLabels =
    MetricLabels("pipeline" -> pipelineName)

  def component(componentName: String): MetricLabels =
    MetricLabels("component" -> componentName)

  def environment(env: String): MetricLabels =
    MetricLabels("environment" -> env)

  def dataSource(source: String): MetricLabels =
    MetricLabels("data_source" -> source)

  def stage(stageName: String): MetricLabels =
    MetricLabels("stage" -> stageName)

  implicit val showMetricLabels: Show[MetricLabels] = Show.show { labels =>
    if (labels.isEmpty) "{}"
    else labels.labels.map { case (k, v) => s"$k=$v" }.mkString("{", ",", "}")
  }
}

// ===============================
// CORE METRIC TYPES
// ===============================

/**
 * Core metric data structure with metadata.
 */
case class Metric(
  name: String,
  value: MetricValue,
  labels: MetricLabels = MetricLabels.empty,
  timestamp: Instant = Instant.now(),
  description: Option[String] = None,
  metadata: Map[String, String] = Map.empty) {

  def withLabel(key: String, value: String): Metric =
    copy(labels = labels.add(key, value))

  def withLabels(newLabels: MetricLabels): Metric =
    copy(labels = labels.merge(newLabels))

  def withDescription(desc: String): Metric =
    copy(description = Some(desc))

  def withMetadata(key: String, value: String): Metric =
    copy(metadata = metadata + (key -> value))

  def withTimestamp(ts: Instant): Metric =
    copy(timestamp = ts)

  /**
   * Check if this metric matches given label filters.
   */
  def matches(labelFilters: Map[String, String]): Boolean =
    labels.matches(labelFilters)

  /**
   * Get the numeric value of this metric.
   */
  def numericValue: Double = value.asDouble

  /**
   * Get the unit of this metric.
   */
  def unit: MetricUnit = value.unit
}

object Metric {

  /**
   * Create a counter metric.
   */
  def counter(
    name: String,
    value: Long,
    labels: MetricLabels = MetricLabels.empty,
  ): Metric =
    Metric(name, MetricValue.counter(value), labels)

  /**
   * Create a gauge metric.
   */
  def gauge(
    name: String,
    value: Double,
    labels: MetricLabels = MetricLabels.empty,
  ): Metric =
    Metric(name, MetricValue.gauge(value), labels)

  /**
   * Create a timer metric.
   */
  def timer(
    name: String,
    durations: List[FiniteDuration],
    labels: MetricLabels = MetricLabels.empty,
  ): Metric =
    Metric(name, MetricValue.timer(durations), labels)

  /**
   * Create a rate metric.
   */
  def rate(
    name: String,
    events: Long,
    window: FiniteDuration,
    labels: MetricLabels = MetricLabels.empty,
  ): Metric =
    Metric(name, MetricValue.rate(events, window), labels)

  implicit val showMetric: Show[Metric] = Show.show { metric =>
    s"${metric.name}${metric.labels.show}=${metric.numericValue}${metric.unit.show}"
  }
}

/**
 * Collection of related metrics.
 */
case class MetricCollection(
  metrics: List[Metric] = List.empty,
  name: String = "metrics",
  timestamp: Instant = Instant.now(),
  metadata: Map[String, String] = Map.empty) {

  def add(metric: Metric): MetricCollection =
    copy(metrics = metrics :+ metric)

  def addAll(newMetrics: List[Metric]): MetricCollection =
    copy(metrics = metrics ++ newMetrics)

  def filter(predicate: Metric => Boolean): MetricCollection =
    copy(metrics = metrics.filter(predicate))

  def filterByName(name: String): MetricCollection =
    filter(_.name == name)

  def filterByLabels(labelFilters: Map[String, String]): MetricCollection =
    filter(_.matches(labelFilters))

  def groupByName: Map[String, List[Metric]] =
    metrics.groupBy(_.name)

  def groupByLabel(labelKey: String): Map[String, List[Metric]] =
    metrics.groupBy(_.labels.get(labelKey).getOrElse("unknown"))

  def size: Int = metrics.size

  def isEmpty: Boolean = metrics.isEmpty

  def nonEmpty: Boolean = metrics.nonEmpty

  /**
   * Find metrics by name.
   */
  def find(name: String): Option[Metric] =
    metrics.find(_.name == name)

  /**
   * Get all metric names.
   */
  def metricNames: Set[String] =
    metrics.map(_.name).toSet

  /**
   * Combine with another metric collection.
   */
  def combine(other: MetricCollection): MetricCollection =
    copy(
      metrics = metrics ++ other.metrics,
      name = s"$name+${other.name}",
      timestamp = if (timestamp.isAfter(other.timestamp)) timestamp else other.timestamp,
    )

  /**
   * Convert to map for easy access.
   */
  def toMap: Map[String, Metric] =
    metrics.map(m => m.name -> m).toMap
}

object MetricCollection {

  val empty: MetricCollection = MetricCollection()

  def single(metric: Metric): MetricCollection =
    MetricCollection(List(metric))

  def from(metrics: Metric*): MetricCollection =
    MetricCollection(metrics.toList)

  def named(name: String): MetricCollection =
    MetricCollection(name = name)

  // Monoid instance for metric collection combination
  implicit val metricCollectionMonoid: Monoid[MetricCollection] = new Monoid[MetricCollection] {
    def empty: MetricCollection                                             = MetricCollection.empty
    def combine(x: MetricCollection, y: MetricCollection): MetricCollection = x.combine(y)
  }

  implicit val showMetricCollection: Show[MetricCollection] = Show.show { collection =>
    s"MetricCollection(${collection.name}, ${collection.size} metrics)"
  }
}

// ===============================
// PIPELINE METRICS
// ===============================

/**
 * Specialized metrics for data pipeline operations.
 */
case class PipelineMetrics(
  pipelineName: String,
  recordsProcessed: Long = 0,
  recordsFailed: Long = 0,
  bytesProcessed: Long = 0,
  processingTime: FiniteDuration = FiniteDuration(0, TimeUnit.MILLISECONDS),
  memoryUsed: Long = 0,
  cpuUsage: Double = 0.0,
  customMetrics: Map[String, Double] = Map.empty,
  timestamp: Instant = Instant.now(),
  labels: MetricLabels = MetricLabels.empty) {

  def recordsPerSecond: Double =
    if (processingTime.toSeconds > 0) recordsProcessed.toDouble / processingTime.toSeconds else 0.0

  def bytesPerSecond: Double =
    if (processingTime.toSeconds > 0) bytesProcessed.toDouble / processingTime.toSeconds else 0.0

  def errorRate: Double =
    if (recordsProcessed > 0) recordsFailed.toDouble / recordsProcessed else 0.0

  def successRate: Double = 1.0 - errorRate

  def throughput: Double = recordsPerSecond

  def withCustomMetric(name: String, value: Double): PipelineMetrics =
    copy(customMetrics = customMetrics + (name -> value))

  def withLabel(key: String, value: String): PipelineMetrics =
    copy(labels = labels.add(key, value))

  def combine(other: PipelineMetrics): PipelineMetrics =
    copy(
      recordsProcessed = recordsProcessed + other.recordsProcessed,
      recordsFailed = recordsFailed + other.recordsFailed,
      bytesProcessed = bytesProcessed + other.bytesProcessed,
      processingTime = processingTime + other.processingTime,
      memoryUsed = math.max(memoryUsed, other.memoryUsed), // Take max memory
      cpuUsage = (cpuUsage + other.cpuUsage) / 2,          // Average CPU usage
      customMetrics = customMetrics ++ other.customMetrics,
      timestamp = if (timestamp.isAfter(other.timestamp)) timestamp else other.timestamp,
    )

  /**
   * Convert to metric collection.
   */
  def toMetrics: MetricCollection = {
    val baseLabels = labels.add("pipeline", pipelineName)

    val coreMetrics = List(
      Metric.counter("records_processed", recordsProcessed, baseLabels),
      Metric.counter("records_failed", recordsFailed, baseLabels),
      Metric.counter("bytes_processed", bytesProcessed, baseLabels.add("unit", "bytes")),
      Metric.gauge("processing_time_ms", processingTime.toMillis.toDouble, baseLabels),
      Metric.gauge("memory_used_bytes", memoryUsed.toDouble, baseLabels),
      Metric.gauge("cpu_usage_percent", cpuUsage, baseLabels),
      Metric.gauge("records_per_second", recordsPerSecond, baseLabels),
      Metric.gauge("bytes_per_second", bytesPerSecond, baseLabels),
      Metric.gauge("error_rate", errorRate, baseLabels),
      Metric.gauge("success_rate", successRate, baseLabels),
    )

    val customMetricsList = customMetrics.map {
      case (name, value) =>
        Metric.gauge(name, value, baseLabels)
    }.toList

    MetricCollection
      .from(coreMetrics ++ customMetricsList: _*)
      .copy(name = s"pipeline_metrics_$pipelineName")
  }
}

object PipelineMetrics {

  def empty(pipelineName: String): PipelineMetrics =
    PipelineMetrics(pipelineName)

  def processing(
    pipelineName: String,
    recordsProcessed: Long,
    processingTime: FiniteDuration,
    memoryUsed: Long = 0,
  ): PipelineMetrics =
    PipelineMetrics(
      pipelineName = pipelineName,
      recordsProcessed = recordsProcessed,
      processingTime = processingTime,
      memoryUsed = memoryUsed,
    )

  // Monoid instance for pipeline metrics combination
  implicit val pipelineMetricsMonoid: Monoid[PipelineMetrics] = new Monoid[PipelineMetrics] {
    def empty: PipelineMetrics                                           = PipelineMetrics("unknown")
    def combine(x: PipelineMetrics, y: PipelineMetrics): PipelineMetrics = x.combine(y)
  }

  implicit val showPipelineMetrics: Show[PipelineMetrics] = Show.show { metrics =>
    s"PipelineMetrics(${metrics.pipelineName}: ${metrics.recordsProcessed} records, ${metrics.processingTime}, ${metrics.errorRate}% errors)"
  }
}

// ===============================
// QUALITY METRICS
// ===============================

/**
 * Data quality metrics for monitoring data integrity and completeness.
 */
case class QualityMetrics(
  datasetName: String,
  completeness: Map[String, Double] = Map.empty,
  uniqueness: Map[String, Double] = Map.empty,
  validity: Map[String, Double] = Map.empty,
  accuracy: Map[String, Double] = Map.empty,
  consistency: Map[String, Double] = Map.empty,
  timeliness: Option[FiniteDuration] = None,
  freshness: Option[FiniteDuration] = None,
  overallScore: Option[Double] = None,
  timestamp: Instant = Instant.now(),
  labels: MetricLabels = MetricLabels.empty) {

  def averageCompleteness: Double =
    if (completeness.nonEmpty) completeness.values.sum / completeness.size else 1.0

  def averageUniqueness: Double =
    if (uniqueness.nonEmpty) uniqueness.values.sum / uniqueness.size else 1.0

  def averageValidity: Double =
    if (validity.nonEmpty) validity.values.sum / validity.size else 1.0

  def averageAccuracy: Double =
    if (accuracy.nonEmpty) accuracy.values.sum / accuracy.size else 1.0

  def averageConsistency: Double =
    if (consistency.nonEmpty) consistency.values.sum / consistency.size else 1.0

  def calculateOverallScore: Double = {
    val scores = List(
      averageCompleteness,
      averageUniqueness,
      averageValidity,
      averageAccuracy,
      averageConsistency,
    )
    scores.sum / scores.length
  }

  def withOverallScore: QualityMetrics =
    copy(overallScore = Some(calculateOverallScore))

  def withLabel(key: String, value: String): QualityMetrics =
    copy(labels = labels.add(key, value))

  /**
   * Check if quality meets minimum thresholds.
   */
  def meetsThreshold(threshold: Double): Boolean =
    overallScore.getOrElse(calculateOverallScore) >= threshold

  /**
   * Convert to metric collection.
   */
  def toMetrics: MetricCollection = {
    val baseLabels = labels.add("dataset", datasetName)

    val completenessMetrics = completeness.map {
      case (field, score) =>
        Metric.gauge("completeness", score, baseLabels.add("field", field))
    }.toList

    val uniquenessMetrics = uniqueness.map {
      case (field, score) =>
        Metric.gauge("uniqueness", score, baseLabels.add("field", field))
    }.toList

    val validityMetrics = validity.map {
      case (field, score) =>
        Metric.gauge("validity", score, baseLabels.add("field", field))
    }.toList

    val aggregateMetrics = List(
      Metric.gauge("average_completeness", averageCompleteness, baseLabels),
      Metric.gauge("average_uniqueness", averageUniqueness, baseLabels),
      Metric.gauge("average_validity", averageValidity, baseLabels),
      Metric.gauge(
        "overall_quality_score",
        overallScore.getOrElse(calculateOverallScore),
        baseLabels,
      ),
    )

    val timelinessList = timeliness.map { duration =>
      Metric.gauge("timeliness_ms", duration.toMillis.toDouble, baseLabels)
    }.toList

    val freshnessList = freshness.map { duration =>
      Metric.gauge("freshness_ms", duration.toMillis.toDouble, baseLabels)
    }.toList

    MetricCollection
      .from(
        (completenessMetrics ++ uniquenessMetrics ++ validityMetrics ++
          aggregateMetrics ++ timelinessList ++ freshnessList): _*,
      )
      .copy(name = s"quality_metrics_$datasetName")
  }
}

object QualityMetrics {

  def empty(datasetName: String): QualityMetrics =
    QualityMetrics(datasetName)

  /**
   * Builder for quality metrics.
   */
  case class QualityMetricsBuilder(
    datasetName: String,
    completeness: Map[String, Double] = Map.empty,
    uniqueness: Map[String, Double] = Map.empty,
    validity: Map[String, Double] = Map.empty,
    accuracy: Map[String, Double] = Map.empty,
    consistency: Map[String, Double] = Map.empty,
    timeliness: Option[FiniteDuration] = None,
    freshness: Option[FiniteDuration] = None,
    labels: MetricLabels = MetricLabels.empty) {

    def completeness(field: String, score: Double): QualityMetricsBuilder =
      copy(completeness = completeness + (field -> score))

    def uniqueness(field: String, score: Double): QualityMetricsBuilder =
      copy(uniqueness = uniqueness + (field -> score))

    def validity(field: String, score: Double): QualityMetricsBuilder =
      copy(validity = validity + (field -> score))

    def accuracy(field: String, score: Double): QualityMetricsBuilder =
      copy(accuracy = accuracy + (field -> score))

    def consistency(field: String, score: Double): QualityMetricsBuilder =
      copy(consistency = consistency + (field -> score))

    def timeliness(duration: FiniteDuration): QualityMetricsBuilder =
      copy(timeliness = Some(duration))

    def freshness(duration: FiniteDuration): QualityMetricsBuilder =
      copy(freshness = Some(duration))

    def withLabel(key: String, value: String): QualityMetricsBuilder =
      copy(labels = labels.add(key, value))

    def build: QualityMetrics =
      QualityMetrics(
        datasetName = datasetName,
        completeness = completeness,
        uniqueness = uniqueness,
        validity = validity,
        accuracy = accuracy,
        consistency = consistency,
        timeliness = timeliness,
        freshness = freshness,
        labels = labels,
      ).withOverallScore
  }

  def builder(datasetName: String): QualityMetricsBuilder =
    QualityMetricsBuilder(datasetName)

  implicit val showQualityMetrics: Show[QualityMetrics] = Show.show { metrics =>
    s"QualityMetrics(${metrics.datasetName}: ${metrics.overallScore.getOrElse(metrics.calculateOverallScore) * 100}% quality)"
  }
}

// ===============================
// PERFORMANCE METRICS
// ===============================

/**
 * Performance metrics for system resource monitoring.
 */
case class PerformanceMetrics(
  componentName: String,
  cpuUsage: Double = 0.0,
  memoryUsage: Long = 0,
  diskUsage: Long = 0,
  networkBytesIn: Long = 0,
  networkBytesOut: Long = 0,
  threadCount: Int = 0,
  gcTime: FiniteDuration = FiniteDuration(0, TimeUnit.MILLISECONDS),
  latency: FiniteDuration = FiniteDuration(0, TimeUnit.MILLISECONDS),
  timestamp: Instant = Instant.now(),
  labels: MetricLabels = MetricLabels.empty) {

  def memoryUsageMB: Double   = memoryUsage.toDouble / (1024 * 1024)
  def diskUsageGB: Double     = diskUsage.toDouble / (1024 * 1024 * 1024)
  def networkTotalBytes: Long = networkBytesIn + networkBytesOut

  def withLabel(key: String, value: String): PerformanceMetrics =
    copy(labels = labels.add(key, value))

  /**
   * Convert to metric collection.
   */
  def toMetrics: MetricCollection = {
    val baseLabels = labels.add("component", componentName)

    val metrics = List(
      Metric.gauge("cpu_usage_percent", cpuUsage, baseLabels),
      Metric.gauge("memory_usage_bytes", memoryUsage.toDouble, baseLabels),
      Metric.gauge("memory_usage_mb", memoryUsageMB, baseLabels),
      Metric.gauge("disk_usage_bytes", diskUsage.toDouble, baseLabels),
      Metric.gauge("disk_usage_gb", diskUsageGB, baseLabels),
      Metric.counter("network_bytes_in", networkBytesIn, baseLabels),
      Metric.counter("network_bytes_out", networkBytesOut, baseLabels),
      Metric.gauge("thread_count", threadCount.toDouble, baseLabels),
      Metric.gauge("gc_time_ms", gcTime.toMillis.toDouble, baseLabels),
      Metric.gauge("latency_ms", latency.toMillis.toDouble, baseLabels),
    )

    MetricCollection
      .from(metrics: _*)
      .copy(name = s"performance_metrics_$componentName")
  }
}

object PerformanceMetrics {

  def empty(componentName: String): PerformanceMetrics =
    PerformanceMetrics(componentName)

  /**
   * Time a block of code and return performance metrics.
   */
  def timed[T](componentName: String)(block: => T): (T, PerformanceMetrics) = {
    val start    = Instant.now()
    val result   = block
    val end      = Instant.now()
    val duration = FiniteDuration(Duration.between(start, end).toMillis, TimeUnit.MILLISECONDS)

    val metrics = PerformanceMetrics(
      componentName = componentName,
      latency = duration,
      timestamp = end,
    )

    (result, metrics)
  }

  implicit val showPerformanceMetrics: Show[PerformanceMetrics] = Show.show { metrics =>
    s"PerformanceMetrics(${metrics.componentName}: CPU=${metrics.cpuUsage}%, Memory=${metrics.memoryUsageMB}MB, Latency=${metrics.latency})"
  }
}
