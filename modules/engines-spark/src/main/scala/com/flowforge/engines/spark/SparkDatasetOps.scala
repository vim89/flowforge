package com.flowforge.engines.spark

import com.flowforge.core.algebra.{ DataAlgebra, DataDecoder }

/**
 * Spark-specific helpers for working with ProductionSparkDataset while keeping the core algebra pure.
 */
object SparkDatasetOps {

  /**
   * Filter a dataset using a Spark Column expression when the dataset is backed by Spark.
   * Falls back to no-op for non-Spark datasets.
   */
  def filterByColumn[A: DataDecoder](
    dataset: DataAlgebra.Dataset[A],
  )(cond: org.apache.spark.sql.Column): DataAlgebra.Dataset[A] = dataset match {
    case pds: ProductionSparkDataset[A] =>
      val spark = pds.sparkDataFrame.sparkSession
      val df    = pds.sparkDataFrame.filter(cond)
      ProductionSparkDataset.fromDataFrame[A](df, spark)
    case other => other
  }

  /**
   * Order a Spark-backed dataset by a column (ascending/descending). Falls back to input for non-Spark.
   */
  def sortByColumn[A: DataDecoder](
    dataset: DataAlgebra.Dataset[A],
  )(col: org.apache.spark.sql.Column, ascending: Boolean = true,
  ): DataAlgebra.Dataset[A] = dataset match {
    case pds: ProductionSparkDataset[A] =>
      val spark = pds.sparkDataFrame.sparkSession
      val df    = if (ascending) pds.sparkDataFrame.orderBy(col.asc) else pds.sparkDataFrame.orderBy(col.desc)
      ProductionSparkDataset.fromDataFrame[A](df, spark)
    case other => other
  }

  /**
   * Limit a Spark-backed dataset to the first n rows. Falls back for non-Spark.
   */
  def limitRows[A: DataDecoder](dataset: DataAlgebra.Dataset[A])(n: Int): DataAlgebra.Dataset[A] = dataset match {
    case pds: ProductionSparkDataset[A] if n >= 0 =>
      val spark = pds.sparkDataFrame.sparkSession
      val df    = pds.sparkDataFrame.limit(n)
      ProductionSparkDataset.fromDataFrame[A](df, spark)
    case other => other
  }

  /**
   * Drop the first n rows of a Spark-backed dataset using exceptAll(limit(n)).
   * Note: Ordering is undefined unless the dataset is ordered already.
   */
  def dropRows[A: DataDecoder](dataset: DataAlgebra.Dataset[A])(n: Int): DataAlgebra.Dataset[A] = dataset match {
    case pds: ProductionSparkDataset[A] if n > 0 =>
      val spark  = pds.sparkDataFrame.sparkSession
      val prefix = pds.sparkDataFrame.limit(n)
      val rest   = pds.sparkDataFrame.exceptAll(prefix)
      ProductionSparkDataset.fromDataFrame[A](rest, spark)
    case other => other
  }
}
