package com.flowforge.core.contracts
import scala.annotation.implicitNotFound

/**
 * Evidence that an output type `Out` conforms to a declared data contract `Contract` under a schema‑evolution
 * policy `P` (e.g., Exact, Backward, Forward).
 *
 * This evidence is materialized at compile time by a macro that deeply compares normalized shapes of `Out`
 * and `Contract` and aborts compilation with a precise, path‑aware message on drift.
 *
 * Typical use: {{@example import com.flowforge.core.contracts._ import
 * com.flowforge.core.contracts.SchemaPolicy._ final case class User(id: Long, email: String) // Contract
 * types are generated in modules/contracts-sdk from Avro (or authored directly) type UserV1 = User //
 * Requires compile‑time evidence; fails to compile on mismatch implicitly[SchemaConforms[User, UserV1,
 * Exact]] }}
 */
@implicitNotFound("""
FlowForge: Contract drift (policy: ${P})
Out: ${Out} vs Contract: ${Contract}
Missing: <> | Extra: <> | Mismatched: <>
""")
trait SchemaConforms[Out, Contract, P <: SchemaPolicy]

/**
 * The evidence itself has no members, so everything here is the materializer, and the materializer is the one
 * part of contract checking that cannot be written once: it is a macro, and the two Scala versions spell
 * macros differently. [[SchemaConformsMaterializer]] is supplied per version from its own source directory.
 * Both spellings end up calling the same comparison in `internal.ShapeDiff`.
 */
object SchemaConforms extends SchemaConformsMaterializer
