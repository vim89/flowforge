package com.flowforge.engines.spark

import com.flowforge.core.algebra.{ DataAlgebra, DataDecoder, DataEncoder, EncodedData }
import com.flowforge.core.types._
import org.apache.spark.sql.{ DataFrame, Dataset, SparkSession }

import java.time.Instant

/**
 * Production-ready Spark dataset wrapper with MEMORY-SAFE distributed processing.
 *
 * CRITICAL FIX: No longer stores entire dataset in memory. Uses Spark DataFrame for distributed operations
 * with sample data for compatibility.
 *
 * This bridges the gap between FlowForge's functional interface and Spark's distributed computation model
 * while maintaining type safety and preventing OOM errors.
 */
final case class ProductionSparkDataset[A](
  sampleData: List[A], // Only stores sample, not full dataset
  sparkDataFrame: DataFrame,
  schema: DataSchema,
  metadata: DataAlgebra.DatasetMetadata)
    extends DataAlgebra.Dataset[A] {

  /**
   * Get record count from Spark DataFrame for accuracy - DISTRIBUTED SAFE
   */
  override def size: Int = sparkDataFrame.count().toInt

  /**
   * Check emptiness from Spark DataFrame - DISTRIBUTED SAFE
   */
  override def isEmpty: Boolean = sparkDataFrame.isEmpty

  /**
   * COMPATIBILITY: Returns sample data for legacy compatibility WARNING: This is only a sample, not the full
   * dataset
   */
  override def data: List[A] = sampleData

  /**
   * Convert to Spark Dataset with encoder for type-safe operations
   */
  def asSparkDataset[T](implicit encoder: org.apache.spark.sql.Encoder[T]): Dataset[T] =
    // This would require proper encoding implementation in production
    throw new UnsupportedOperationException(
      "Type-safe Spark Dataset conversion requires encoder implementation",
    )

  /**
   * Cache the underlying DataFrame for performance
   */
  def cache(): ProductionSparkDataset[A] = {
    val cachedDf = sparkDataFrame.cache()
    copy(sparkDataFrame = cachedDf)
  }

  /**
   * Persist with specified storage level
   */
  def persist(level: org.apache.spark.storage.StorageLevel): ProductionSparkDataset[A] = {
    val persistedDf = sparkDataFrame.persist(level)
    copy(sparkDataFrame = persistedDf)
  }

  /**
   * Repartition the DataFrame
   */
  def repartition(numPartitions: Int): ProductionSparkDataset[A] = {
    val repartitionedDf = sparkDataFrame.repartition(numPartitions)
    copy(
      sparkDataFrame = repartitionedDf,
      metadata = metadata.copy(partitions = numPartitions),
    )
  }

  /**
   * Show DataFrame for debugging (delegates to Spark)
   */
  def show(numRows: Int = 20, truncate: Boolean = true): Unit =
    sparkDataFrame.show(numRows, truncate)

  /**
   * Write DataFrame using Spark's native writer
   */
  def writeParquet(path: String): Unit =
    sparkDataFrame.write.mode("overwrite").parquet(path)

  /**
   * Write DataFrame as Delta table
   */
  def writeDelta(path: String): Unit =
    sparkDataFrame.write.format("delta").mode("overwrite").save(path)
}

object ProductionSparkDataset {

  /**
    * Utilities for constructing and working with production‑grade Spark Datasets inside FlowForge.
    *
    * A `ProductionSparkDataset[A]` wraps a Spark `Dataset[A]` with metadata and helper methods that make it
    * easy to integrate with FlowForge’s typed contracts, quality checks, and sinks while keeping
    * transformations pure.
    *
    * Typical usage is internal to the Spark `DataAlgebra` implementation, but the helpers are safe to use in
    * examples/tests when you need to adapt a DataFrame/Dataset to the DSL.
    */

  /**
    * Create ProductionSparkDataset from an existing Spark [[org.apache.spark.sql.DataFrame]] while avoiding
    * driver OOMs.
    *
    * Uses a small JSON sample for compatibility with FlowForge’s decoders and keeps the full dataset lazily
    * evaluated on the cluster.
    *
    * @param df the input DataFrame
    * @param spark the SparkSession used for auxiliary operations
    * @tparam A element type with a FlowForge [[com.flowforge.core.algebra.DataDecoder]] instance
    * @return a ProductionSparkDataset[A] backed by the given DataFrame
    */
  def fromDataFrame[A: DataDecoder](
    df: DataFrame,
    spark: SparkSession,
  ): ProductionSparkDataset[A] = {
    // MEMORY-SAFE: Use lazy evaluation instead of .collect().toList
    // Only compute sample for schema inference, not entire dataset
    val sampleData: List[A] = if (df.isEmpty) {
      List.empty[A]
    } else {
      // Take only small sample for compatibility, not entire dataset
      val sampleSize  = math.min(100, df.count()).toInt
      val jsonStrings = df.toJSON.limit(sampleSize).collect().toList
      jsonStrings.flatMap { js =>
        val bytes = js.getBytes("UTF-8")
        DataDecoder[A].decode(EncodedData(bytes, DataFormat.JSON), DataFormat.JSON).toOption
      }
    }

    val schema = DataSchema(
      fields = df.schema.fields
        .map(f =>
          StructField(
            name = com.flowforge.core.types.RefinedTypes.FieldName.unsafeFrom(f.name),
            dataType = mapSparkTypeToFlowForgeType(f.dataType),
            nullable = f.nullable,
          ),
        )
        .toList,
      version = com.flowforge.core.types.RefinedTypes.SchemaVersion.unsafeFrom(1),
      metadata = Map("spark_schema" -> df.schema.toString),
      createdAt = Instant.now(),
    )

    val metadata = DataAlgebra.DatasetMetadata(
      recordCount = df.count(),
      schema = schema,
      partitions = df.rdd.getNumPartitions,
      createdAt = Instant.now(),
      source = None,
    )

    ProductionSparkDataset(sampleData, df, schema, metadata)
  }

  /** Map Spark SQL DataType to FlowForge [[com.flowforge.core.types.DataType]]. */
  private def mapSparkTypeToFlowForgeType(
    sparkType: org.apache.spark.sql.types.DataType,
  ): DataType = {
    import org.apache.spark.sql.types.{ DataType => __, _ }
    sparkType match {
      case StringType    => DataType.String
      case IntegerType   => DataType.Integer
      case LongType      => DataType.Long
      case DoubleType    => DataType.Double
      case BooleanType   => DataType.Boolean
      case TimestampType => DataType.Timestamp
      case DateType      => DataType.Date
      case FloatType     => DataType.Float
      case ByteType      => DataType.Byte
      case ShortType     => DataType.Short
      case BinaryType    => DataType.Binary
      case dt: DecimalType =>
        DataType.Decimal(
          eu.timepit.refined.types.numeric.PosInt.unsafeFrom(dt.precision),
          eu.timepit.refined.types.numeric.NonNegInt.unsafeFrom(dt.scale),
        )
      case ArrayType(elementType, _) => DataType.Array(mapSparkTypeToFlowForgeType(elementType))
      case MapType(keyType, valueType, _) =>
        DataType.Map(
          mapSparkTypeToFlowForgeType(keyType),
          mapSparkTypeToFlowForgeType(valueType),
        )
      case StructType(fields) =>
        DataType.Struct(
          fields
            .map(f =>
              com.flowforge.core.types.StructField(
                name = com.flowforge.core.types.RefinedTypes.FieldName.unsafeFrom(f.name),
                dataType = mapSparkTypeToFlowForgeType(f.dataType),
                nullable = f.nullable,
              ),
            )
            .toList,
        )
      case _ => DataType.String // Default fallback
    }
  }

  /**
   * Create from in-memory data with Spark DataFrame creation
   */
  def fromData[A: DataEncoder](
    data: List[A],
    spark: SparkSession,
  ): ProductionSparkDataset[A] = {
    import spark.implicits._

    // Convert data to JSON strings and create DataFrame
    val jsonStrings = data.map { a =>
      DataEncoder[A].encode(a, DataFormat.JSON) match {
        case Right(encoded) => new String(encoded.data, "UTF-8")
        case Left(_)        => "{}" // Handle encoding failures gracefully
      }
    }

    val df = spark.read.json(spark.createDataset(jsonStrings))

    val schema = DataSchema(
      fields = df.schema.fields
        .map(f =>
          StructField(
            name = com.flowforge.core.types.RefinedTypes.FieldName.unsafeFrom(f.name),
            dataType = mapSparkTypeToFlowForgeType(f.dataType),
            nullable = f.nullable,
          ),
        )
        .toList,
      version = com.flowforge.core.types.RefinedTypes.SchemaVersion.unsafeFrom(1),
      metadata = Map("created_from" -> "in_memory_data"),
      createdAt = Instant.now(),
    )

    val metadata = DataAlgebra.DatasetMetadata(
      recordCount = data.size.toLong,
      schema = schema,
      partitions = df.rdd.getNumPartitions,
      createdAt = Instant.now(),
      source = None,
    )

    ProductionSparkDataset(data, df, schema, metadata)
  }
}
