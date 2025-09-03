package com.flowforge.contracts.sample

import com.flowforge.core.types._
import shapeless.{ HList, LabelledGeneric }

/**
 * Sample generated-like contracts SDK to demonstrate compile-time schema gates without local
 * codegen. In real deployments, this lives in a separate repo and is published as an artifact.
 */
object sales {

  // Source contract type (e.g., published by upstream team)
  final case class SalesV1(
    invoiceNumber: String,
    customerId: String,
    amount: Double,
    eventTs: Long
  )

  // Curated target contract type
  final case class SalesCuratedV1(
    invoiceNumber: String,
    customerId: String,
    amount: Double,
    eventTs: Long
  )

  // Typed endpoints helpers (SDK surface)
  object Endpoints {
    // Infer the HList representation via LabelledGeneric.Aux and return a TypedSource for it
    def gcsParquetSourceFor[A, R <: HList](bucket: String, prefix: String)(implicit
      L: LabelledGeneric.Aux[A, R]
    ): TypedSource[R] = TypedIO.gcsParquetSource[R](bucket, prefix)

    def gcsParquetSourceFor[A](bucket: String, prefix: String)(implicit
      L: LabelledGeneric[A]
    ): TypedSource[L.Repr] = TypedIO.gcsParquetSource[L.Repr](bucket, prefix)

    def deltaSinkFor[A, R <: HList](path: String)(implicit
      L: LabelledGeneric.Aux[A, R]
    ): TypedSink[R] = TypedSink[R](LocalDataSink(path, DataFormat.Delta))

    def parquetSinkFor[A, R <: HList](path: String)(implicit
      L: LabelledGeneric.Aux[A, R]
    ): TypedSink[R] = TypedIO.localParquetSink[R](path)

    def parquetSinkFor[A](path: String)(implicit
      L: LabelledGeneric[A]
    ): TypedSink[L.Repr] = TypedIO.localParquetSink[L.Repr](path)
  }
}

object customers {
  // Customer contract types
  final case class CustomerV1(
    customerId: String,
    segment: String
  )

  object Endpoints {
    import com.flowforge.core.types.TypedIO
    import shapeless.{ HList, LabelledGeneric }

    def gcsParquetSourceFor[A, R <: HList](bucket: String, prefix: String)(implicit
      L: LabelledGeneric.Aux[A, R]
    ): TypedSource[R] = TypedIO.gcsParquetSource[R](bucket, prefix)

    def gcsParquetSourceFor[A](bucket: String, prefix: String)(implicit
      L: LabelledGeneric[A]
    ): TypedSource[L.Repr] = TypedIO.gcsParquetSource[L.Repr](bucket, prefix)
  }
}

object curated {
  // Joined curated type
  final case class SalesWithCustomerV1(
    invoiceNumber: String,
    customerId: String,
    segment: String,
    amount: Double,
    eventTs: Long
  )

  object Endpoints {
    import shapeless.{ HList, LabelledGeneric }

    def parquetSinkFor[A, R <: HList](path: String)(implicit
      L: LabelledGeneric.Aux[A, R]
    ): TypedSink[R] = TypedIO.localParquetSink[R](path)

    def parquetSinkFor[A](path: String)(implicit L: LabelledGeneric[A]): TypedSink[L.Repr] =
      TypedIO.localParquetSink[L.Repr](path)
  }
}
