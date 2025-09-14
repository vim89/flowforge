package com.flowforge.engines.spark

import com.flowforge.core.algebra.DataAlgebra
import com.flowforge.core.types.JdbcSink
import org.apache.spark.sql.{ DataFrame, DataFrameWriter, Dataset, Row, SaveMode }

/** Small helpers to keep Spark write configuration consistent and deduplicated. */
private[spark] object SparkWriteHelpers {

  /** Prefer repartition if set, else coalesce, otherwise return df unchanged. */
  def tuned(df: DataFrame, options: DataAlgebra.WriteOptions): DataFrame =
    options.repartition match {
      case Some(n) => df.repartition(n)
      case None    => options.coalesce.map(df.coalesce).getOrElse(df)
    }

  /** Map FlowForge write mode to Spark SaveMode. */
  def saveMode(mode: DataAlgebra.WriteMode): SaveMode = mode match {
    case DataAlgebra.WriteMode.Append        => SaveMode.Append
    case DataAlgebra.WriteMode.Overwrite     => SaveMode.Overwrite
    case DataAlgebra.WriteMode.ErrorIfExists => SaveMode.ErrorIfExists
    case DataAlgebra.WriteMode.Ignore        => SaveMode.Ignore
  }

  /** Create a base JDBC writer with URL/driver/table and optional auth. */
  def jdbcWriterBase(df: DataFrame, j: JdbcSink): DataFrameWriter[Row] = {
    val w0 = df.write
      .format("jdbc")
      .option("url", j.url)
      .option("driver", j.driver)
      .option("dbtable", j.table.value)
    val w1 = j.user.fold(w0)(u => w0.option("user", u))
    j.password.fold(w1)(p => w1.option("password", p))
  }

  /** Apply extra Spark JDBC options and mode to a writer. */
  def withExtrasAndMode(
    w: DataFrameWriter[Row],
    extra: Map[String, String],
    mode: DataAlgebra.WriteMode,
  ): DataFrameWriter[Row] =
    extra.foldLeft(w) { case (acc, (k, v)) => acc.option(k, v) }.mode(saveMode(mode))

  /**
   * Small helper for CSV/JSON style outputs where we generally want a single file by default. Uses provided
   * `coalesce` option if set, otherwise 1.
   */
  def singlePartition[A](ds: Dataset[A], coalesceOpt: Option[Int]): Dataset[A] =
    ds.coalesce(coalesceOpt.getOrElse(1))
}
