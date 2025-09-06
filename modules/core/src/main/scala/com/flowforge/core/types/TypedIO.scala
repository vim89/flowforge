package com.flowforge.core.types

import com.flowforge.core.contracts.derive.Shape

/**
 * Convenience constructors for typed sources and sinks. These simply wrap existing untyped
 * DataSource/DataSink values with a type-level schema marker R.
 */
object TypedIO {
  // Sources
  def localParquetSource[R: Shape](path: String): TypedSource[R] =
    TypedSource[R](LocalDataSource(path, DataFormat.Parquet))

  def gcsParquetSource[R: Shape](bucket: String, prefix: String): TypedSource[R] =
    TypedSource[R](DataSource.gcs(bucket, prefix, DataFormat.Parquet))

  def s3ParquetSource[R: Shape](bucket: String, prefix: String): TypedSource[R] =
    TypedSource[R](DataSource.s3(bucket, prefix, DataFormat.Parquet))

  def bigQuerySource[R: Shape](
    project: String,
    dataset: String,
    table: String,
  ): TypedSource[R] =
    TypedSource[R](DataSource.bigQuery(project, dataset, table))

  // Sinks
  def localParquetSink[R: Shape](path: String): TypedSink[R] =
    TypedSink[R](LocalDataSink(path, DataFormat.Parquet))

  def gcsParquetSink[R: Shape](bucket: String, prefix: String): TypedSink[R] =
    TypedSink[R](DataSink.gcs(bucket, prefix, DataFormat.Parquet))

  def s3ParquetSink[R: Shape](bucket: String, prefix: String): TypedSink[R] =
    TypedSink[R](DataSink.s3(bucket, prefix, DataFormat.Parquet))
}
