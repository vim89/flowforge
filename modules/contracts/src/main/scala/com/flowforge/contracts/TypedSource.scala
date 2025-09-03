package com.flowforge.contracts

import com.flowforge.core.types.{ DataFormat, DataSource }

/**
 * A TypedSource represents a data source with compile-time schema information. The phantom type R
 * encodes the schema structure for compile-time validation.
 */
final case class TypedSource[R](
  identifier: String,
  underlying: DataSource
)

object TypedSource {
  def apply[R](id: String, ds: DataSource): TypedSource[R] = TypedSource[R](id, ds)
  def apply[R](id: String): TypedSource[R] =
    TypedSource[R](id, DataSource.gcs("generated", s"$id", DataFormat.Parquet))
}
