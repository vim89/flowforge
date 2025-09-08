package com.flowforge.contracts

import com.flowforge.core.contracts.derive.Shape
import com.flowforge.core.types.{ DataFormat, DataSource }

/**
 * A TypedSource represents a data source with compile-time schema information. The phantom type C encodes the
 * schema structure for compile-time validation.
 */
final case class TypedSource[C](underlying: DataSource)(implicit val sc: Shape[C])

object TypedSource {
  def apply[C](ds: DataSource)(implicit sc: Shape[C]): TypedSource[C] =
    new TypedSource[C](ds)(sc)

  def apply[C](
    bucket: String,
    prefix: String,
    format: DataFormat,
  )(implicit sc: Shape[C],
  ): TypedSource[C] =
    new TypedSource[C](DataSource.gcs(bucket, prefix, format))(sc)
}
