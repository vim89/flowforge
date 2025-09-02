package com.flowforge.core.impl

import com.flowforge.core.algebra.DataAlgebra
import com.flowforge.core.types.DataSchema
import java.time.Instant

/**
 * Minimal immutable implementation of DataAlgebra.Dataset[A].
 */
final case class SimpleDataset[A](
  data: List[A],
  schema: DataSchema,
  metadata: DataAlgebra.DatasetMetadata
) extends DataAlgebra.Dataset[A]

object SimpleDataset {
  def empty[A](schema: DataSchema): SimpleDataset[A] =
    SimpleDataset(Nil, schema, DataAlgebra.DatasetMetadata(0L, schema, 1, Instant.now(), None))
}
