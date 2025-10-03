package com.flowforge.examples.demo

import cats.effect.{ IO, IOApp }
import com.flowforge.core.PipelineBuilder
import com.flowforge.core.contracts.SchemaPolicy
import com.flowforge.core.contracts.derive.Shape
import com.flowforge.core.instances.EffectInstances.catsEffectSystemInstance
import com.flowforge.core.types.TypedIO._
import com.flowforge.core.types._

/**
 * Typestate (phantom types) demo: show that an incomplete pipeline REFUSES to build.
 *
 *   - GREEN section: complete pipeline compiles and runs (safe)
 *   - RED section: uncomment to PROVOKE a compile-time error for a pipeline missing the sink
 *
 * This example is brand-agnostic and relies only on the public FlowForge builder surface: PipelineBuilder[IO]
 * → addTypedSource → addTransform → addTypedSink → build()
 *
 * References on typestate/phantom types & compile-safe builders:
 *   - Xebia: Compile‑safe Builder Pattern Using Phantom Types (Scala) — prevents `.build()` until complete
 *   - Typelevel Cats‑Effect IOApp — entry point to run IO programs cleanly
 *   - ZIO/Cats fibers docs (background on structured concurrency)
 */
object TypestateRefusal extends IOApp.Simple {

  // --- Domain types: Producer (extra field) vs Consumer (contract)
  final case class Producer(
    id: Long,
    email: String,
    age: Int)
  final case class Consumer(id: Long, email: String)

  // Contract shape evidence for compile‑time validation + typed endpoints
  implicit val producerShape: Shape[Producer] = Shape.gen[Producer]
  implicit val consumerShape: Shape[Consumer] = Shape.gen[Consumer]

  // Typed endpoints (paths are placeholders; the demo reader/writer are pure/side‑effect‑free)
  private val source: TypedSource[Consumer] = localParquetSource[Consumer]("examples-data/users.parquet")
  private val sink: TypedSink[Consumer]     = localParquetSink[Consumer]("examples-data/out/users.parquet")

  // =========================
  // GREEN: complete pipeline
  // =========================
  // Compiles and runs. Demonstrates:
  //  - compile‑time contract check on source (Producer conforms to Consumer under Backward)
  //  - pure transform boundary (Producer => Consumer)
  //  - compile‑time contract check on sink (Exact)
  val pipeline =
    PipelineBuilder[IO]("typestate-refusal-green")
      .withDescription("Typestate demo: complete pipeline compiles; missing sink refuses.")
      .addTypedSource[Producer, Consumer, SchemaPolicy.Backward](
        source,
        _ => IO.pure(Producer(1L, "alice@example.com", 25)), // demo reader: provide a value without real IO
      )
      .addTransform[Consumer] { p =>
        // pure transform separated from effects: Out=Producer ⇒ C=Consumer
        IO.pure(Consumer(p.id, p.email))
      }
      .addTypedSink[Consumer, SchemaPolicy.Exact](
        sink,
        (c, _) => IO(println(s"✅ Would write: $c")), // demo writer
      )
      .build()

  // Run the GREEN pipeline once with monitoring hooks.
  override def run: IO[Unit] =
    for {
      _ <- IO.println("✅ Building complete pipeline (GREEN)…")
      _ <- pipeline.executeWithMonitoring(()) // runs the Kleisli with Unit input
      _ <- IO.println("✅ Done.")

      _ <- IO.println("\n🧪 To demonstrate compile‑time refusal (RED):")
      _ <- IO.println("   1) Open TypestateRefusal.scala")
      _ <- IO.println("   2) Uncomment the RED block below")
      _ <- IO.println("   3) Run `sbt \"project examples\" compile` → compiler refuses `.build` (no sink)")
    } yield ()

  /**
   * Compile Failures
   */
  object TypestateRefusal_RED {
    // This block deliberately omits the sink stage and attempts to call `.build`.
    // Expected: ❌ Compile-time error because the builder state is not `Complete`.
    /*val broken: Pipeline[IO, Unit, Producer] =
      PipelineBuilder[IO]("typestate-refusal-red")
        .addTypedSource[Producer, Consumer, SchemaPolicy.Backward](
          source,
          _ => IO.pure(Producer(42L, "bob@example.com", 30)),
        )
        .addTransform[Producer](p => IO.pure(p)) // now state = WithTransform (no sink)
        .build // ❌ should NOT compile: required S <:< BuilderState.Complete*/
  }
}
