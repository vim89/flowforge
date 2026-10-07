package com.flowforge.core.contracts

/**
 * Field metadata for a case class, derived at compile time.
 *
 * Aliases for `ctdc.derive`, for the same reason as the aliases in [[com.flowforge.core.contracts]]: the
 * engine is a library, but FlowForge callers should only need one namespace. Because these are aliases and
 * not wrappers, `Shape.gen[A]` and implicit search behave exactly as they do in `ctdc`.
 */
package object derive {

  type Field = ctdc.derive.Field
  val Field: ctdc.derive.Field.type = ctdc.derive.Field

  type Shape[T] = ctdc.derive.Shape[T]
  val Shape: ctdc.derive.Shape.type = ctdc.derive.Shape
}
