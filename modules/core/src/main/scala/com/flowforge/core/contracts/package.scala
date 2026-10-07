package com.flowforge.core

/**
 * =Compile-time Contracts=
 *
 * Tools to prove, at compile time, that a producer type `Out` conforms to a declared `Contract` under a
 * policy `P`.
 *
 * ==Key types==
 *   - [[com.flowforge.core.contracts.SchemaConforms]] - evidence that `Out` conforms to `Contract` under `P`.
 *   - [[com.flowforge.core.contracts.SchemaPolicy]] - how conformance is checked (Exact, Backward, Forward,
 *     Ordered, By-position, etc.).
 *
 * ==Usage==
 * {{{
 * import com.flowforge.core.contracts._
 *
 * final case class V1(id: Long, email: String)
 * final case class V2(id: Long, email: String, age: Int)
 *
 * // Backward-compatible: the producer may add fields.
 * implicitly[SchemaConforms[V2, V1, SchemaPolicy.Backward]]
 * }}}
 *
 * Error messages include missing/extra/mismatch sections with path information; see docs/how-it-fails.md.
 *
 * ==Where the engine lives==
 * The comparison engine and its two macro front ends are the `ctdc-core` library
 * (github.com/vim89/compile-time-data-contracts), which FlowForge depends on rather than carrying its own
 * copy. The names below are aliases, not wrappers: they denote the same types, so implicit search reaches
 * `ctdc`'s materializer directly and the drift report is unchanged. They exist so that callers keep a single
 * FlowForge namespace and so the published FlowForge API does not move when the library is reorganized.
 */
package object contracts {

  type SchemaPolicy = ctdc.SchemaPolicy
  val SchemaPolicy: ctdc.SchemaPolicy.type = ctdc.SchemaPolicy

  type SchemaConforms[Out, Contract, P <: SchemaPolicy] = ctdc.SchemaConforms[Out, Contract, P]
  val SchemaConforms: ctdc.SchemaConforms.type = ctdc.SchemaConforms

  /**
   * Ask for contract evidence at a point of your choosing.
   *
   * Equivalent to `implicitly`, but named for what it proves, so a contract can be asserted where it is
   * declared rather than only where some method happens to require it.
   */
  def conforms[Out, Contract, P <: SchemaPolicy](
    implicit
    ev: SchemaConforms[Out, Contract, P],
  ): SchemaConforms[Out, Contract, P] = ev
}
