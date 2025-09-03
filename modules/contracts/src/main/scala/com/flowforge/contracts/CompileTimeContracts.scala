package com.flowforge.contracts

import shapeless.{ HList, LabelledGeneric }

/**
 * Compile-time contract utilities. These helpers let you tag a DataContract[A] with a type-level
 * schema `R` and require (at compile-time) that A’s labelled-generic representation equals `R`.
 *
 * This does not introspect the runtime DataContract fields; instead, it enforces that the type A
 * (source/target record) matches the type-level schema `R`. Use together with TypedSink to ensure
 * end-to-end compile-time consistency between pipeline Out and sink expectations.
 */
object CompileTimeContracts {

  /**
   * Requires compile-time evidence that A’s labelled-generic representation equals R. If the
   * evidence cannot be found, this method cannot be called (compilation fails).
   */
  def requireMatches[A, R <: HList](dc: DataContract[A])(implicit
    ev: LabelledGeneric.Aux[A, R]
  ): DataContract[A] = dc
}

