package com.flowforge.contracts

import com.flowforge.core.types.{ DataFormat, DataSink }
import com.flowforge.core.contracts.derive.Shape

/**
 * A TypedSink represents a data sink with compile-time schema information. The phantom type R encodes the
 * schema structure for compile-time validation.
 */
final case class TypedSink[R](underlying: DataSink)(implicit val sr: Shape[R])

object TypedSink {
  def apply[R](ds: DataSink)(implicit sr: Shape[R]): TypedSink[R] =
    TypedSink[R](ds)

  def apply[R](
    bucket: String,
    prefix: String,
    format: DataFormat,
  )(implicit sr: Shape[R],
  ): TypedSink[R] =
    TypedSink[R](DataSink.gcs(bucket, prefix, format))
}
