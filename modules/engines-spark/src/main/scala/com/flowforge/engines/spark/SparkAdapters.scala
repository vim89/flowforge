package com.flowforge.engines.spark

import com.flowforge.connectors.capabilities.{ CanWrite, Sink, SupportsBatch, SupportsMerge }
import org.apache.spark.sql.Dataset

/**
 * Spark engine adapters implementing the specification from docs/plan/End-to-End-Compile-time.md Section 3.3
 *
 * Lines 175-187:
 *   - type DS[A] = org.apache.spark.sql.Dataset[A]
 *   - final case class SparkHandle[R, Caps](...)
 *   - implicit writers: CanWrite[DS[R], R, SupportsMerge, SparkHandle[R, Caps]], etc.
 */

// Type alias for Spark datasets as specified in plan
object SparkTypes {
  type DS[A] = Dataset[A]
}

// Spark-specific handle for sinks with capabilities
final case class SparkHandle[R, Caps](
  path: String,
  format: String = "parquet",
  mode: String = "append",
  options: Map[String, String] = Map.empty)

// Implicit CanWrite instances for Spark engine integration
object SparkWriters {
  import SparkTypes.DS

  // Merge-capable Spark writer
  implicit def sparkMergeWriter[R]: CanWrite[DS[R], R, SupportsMerge, SparkHandle[R, SupportsMerge]] =
    new CanWrite[DS[R], R, SupportsMerge, SparkHandle[R, SupportsMerge]] {
      def write(s: Sink[R, SupportsMerge, SparkHandle[R, SupportsMerge]], ds: DS[R]): Unit = {
        val handle = s.handle
        ds.write
          .format(handle.format)
          .mode("append") // Merge-like behavior
          .options(handle.options)
          .save(handle.path)
      }
    }

  // Batch-capable Spark writer
  implicit def sparkBatchWriter[R]: CanWrite[DS[R], R, SupportsBatch, SparkHandle[R, SupportsBatch]] =
    new CanWrite[DS[R], R, SupportsBatch, SparkHandle[R, SupportsBatch]] {
      def write(s: Sink[R, SupportsBatch, SparkHandle[R, SupportsBatch]], ds: DS[R]): Unit = {
        val handle = s.handle
        ds.write
          .format(handle.format)
          .mode(handle.mode)
          .options(handle.options)
          .save(handle.path)
      }
    }
}

// Factory methods for creating Spark sinks
object SparkSinks {

  def mergeSink[R](path: String, format: String = "parquet")
    : Sink[R, SupportsMerge, SparkHandle[R, SupportsMerge]] =
    Sink(SparkHandle[R, SupportsMerge](path, format))

  def batchSink[R](
    path: String,
    format: String = "parquet",
    mode: String = "overwrite",
  ): Sink[R, SupportsBatch, SparkHandle[R, SupportsBatch]] =
    Sink(SparkHandle[R, SupportsBatch](path, format, mode))
}
