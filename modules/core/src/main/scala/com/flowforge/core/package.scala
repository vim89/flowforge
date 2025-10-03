package com.flowforge

/**
 * =FlowForge Core=
 *
 * Core concepts for building compile‑time contract‑aware data pipelines in Scala.
 *
 * ==What is guaranteed==
 *   - '''Contracts at compile‑time''': code will not compile if an output type does not conform to a declared
 *     contract under a chosen [[com.flowforge.core.contracts.SchemaPolicy policy]]. This is proven via
 *     [[com.flowforge.core.contracts.SchemaConforms]] evidence.
 *   - '''Typestate builder''': incomplete pipelines (missing source/transform/sink) cannot be built – the
 *     type system enforces the sequence. See [[com.flowforge.core.PipelineBuilder]].
 *   - '''Effect‑safe edges''': I/O is explicit through an effect type `F[_]` with a minimal
 *     [[com.flowforge.core.algebra.EffectSystem]]; transforms remain pure.
 *
 * ==How to use==
 * {{@example import cats.effect.IO import com.flowforge.core.PipelineBuilder import
 * com.flowforge.core.contracts.{SchemaConforms, SchemaPolicy}
 *
 * final case class User(id: Long, email: String) object Contracts { // compile‑time evidence (fails to
 * compile if drift exists) implicit val ev: SchemaConforms[User, User, SchemaPolicy.Exact] = implicitly }
 *
 * val builder = PipelineBuilder[IO]("users") .addTypedSource[User, User, SchemaPolicy.Exact]( /* source */
 * null, _ => IO.pure(User(1,"a@b"))) .noTransform .addTypedSink[User, SchemaPolicy.Exact]( /* sink */ null,
 * (_, _) => IO.unit)
 *
 * val pipeline = builder.build() }}
 *
 * ==Where to go next==
 *   - Contracts and policies: [[com.flowforge.core.contracts]]
 *   - Engines (Spark): [[com.flowforge.engines.spark]]
 *   - Quality (native + Deequ): [[com.flowforge.quality.deequ]]
 */
package object core
