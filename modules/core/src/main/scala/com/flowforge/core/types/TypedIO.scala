package com.flowforge.core.types

import com.flowforge.core.contracts.derive.Shape

/**
 * Convenience constructors for typed sources and sinks. These require Shape evidence for compile-time schema
 * validation.
 */
object TypedIO {
  // Sources
  def localParquetSource[R](path: String)(implicit sc: Shape[R]): TypedSource[R] =
    TypedSource[R](LocalDataSource(path, DataFormat.Parquet))

  def gcsParquetSource[R](bucket: String, prefix: String)(implicit sc: Shape[R]): TypedSource[R] =
    TypedSource[R](DataSource.gcs(bucket, prefix, DataFormat.Parquet))

  def s3ParquetSource[R](bucket: String, prefix: String)(implicit sc: Shape[R]): TypedSource[R] =
    TypedSource[R](DataSource.s3(bucket, prefix, DataFormat.Parquet))

  def bigQuerySource[R](
    project: String,
    dataset: String,
    table: String,
  )(implicit sc: Shape[R],
  ): TypedSource[R] =
    TypedSource[R](DataSource.bigQuery(project, dataset, table))

  // Sinks
  def localParquetSink[R](path: String)(implicit sr: Shape[R]): TypedSink[R] =
    TypedSink[R](LocalDataSink(path, DataFormat.Parquet))

  def gcsParquetSink[R](bucket: String, prefix: String)(implicit sr: Shape[R]): TypedSink[R] =
    TypedSink[R](DataSink.gcs(bucket, prefix, DataFormat.Parquet))

  def s3ParquetSink[R](bucket: String, prefix: String)(implicit sr: Shape[R]): TypedSink[R] =
    TypedSink[R](DataSink.s3(bucket, prefix, DataFormat.Parquet))
}
