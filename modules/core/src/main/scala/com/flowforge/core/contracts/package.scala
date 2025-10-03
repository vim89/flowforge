package com.flowforge.core

/**
  * =Compile‑time Contracts=
  *
  * Tools to prove, at compile time, that a producer type `Out` conforms to a declared `Contract` under a
  * policy `P`.
  *
  * ==Key types==
  *  - [[com.flowforge.core.contracts.SchemaConforms]] — evidence that `Out` conforms to `Contract` under `P`.
  *  - [[com.flowforge.core.contracts.SchemaPolicy]] — how conformance is checked (Exact, Backward, Forward,
  *    Ordered, By‑position, etc.).
  *
  * ==Usage==
  * {{@example
  * import com.flowforge.core.contracts._
  * final case class V1(id: Long, email: String)
  * final case class V2(id: Long, email: String, age: Int)
  *
  * // Backward‑compatible: producer may add fields
  * implicitly[SchemaConforms[V2, V1, SchemaPolicy.Backward]]
  * }}
  *
  * Error messages include missing/extra/mismatch sections with path information; see docs/how‑it‑fails.md.
  */
package object contracts
