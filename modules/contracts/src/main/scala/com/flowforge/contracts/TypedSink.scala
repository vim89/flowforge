package com.flowforge.contracts

import com.flowforge.core.types.{ DataFormat, DataSink }

/**
 * A TypedSink represents a data sink with compile-time schema information. The phantom type R encodes the
 * schema structure for compile-time validation.
 */
final case class TypedSink[R](
  identifier: String,
  underlying: DataSink)

object TypedSink {
  def apply[R](id: String, ds: DataSink): TypedSink[R] = TypedSink[R](id, ds)
  def apply[R](id: String): TypedSink[R] =
    TypedSink[R](id, DataSink.gcs("generated", s"$id", DataFormat.Parquet))
}
