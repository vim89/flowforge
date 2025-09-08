package com.flowforge.examples

import cats.effect.{ IO, IOApp }
import com.flowforge.core.PipelineBuilder
import com.flowforge.core.contracts.SchemaPolicy
import com.flowforge.core.instances.EffectInstances._
import com.flowforge.core.types.TypedIO._
import com.flowforge.core.types._
import com.flowforge.core.contracts.derive.Shape

/**
 * CANONICAL TYPED CONTRACTS DEMO
 *
 * This is the canonical example referenced by ADR-019 and docs/evidence/typed-example.md. Demonstrates
 * FlowForge's unique compile-time contract validation.
 *
 * KEY FEATURES:
 *   1. TypedSource and TypedSink with explicit contract schemas
 *   2. SchemaConforms evidence enforced at compile time
 *   3. Pipeline build() only available when all stages are complete
 *   4. Clear error messages when contracts don't align
 */
object TypedContractsDemo extends IOApp.Simple {

  // Domain models with explicit Shape instances
  case class SalesV1(
    invoiceNumber: String,
    customerId: String,
    amount: Double,
    eventTs: Long)
  case class SalesCuratedV1(
    invoiceNumber: String,
    customerId: String,
    amount: Double,
    eventTs: Long)

  implicit val salesV1Shape: Shape[SalesV1]               = Shape.gen[SalesV1]
  implicit val salesCuratedV1Shape: Shape[SalesCuratedV1] = Shape.gen[SalesCuratedV1]

  def run: IO[Unit] =
    for {
      _ <- IO.println("🔧 FlowForge Canonical Typed Contracts Demo")
      _ <- IO.println("=" * 50)
      _ <- IO.println("")

      _ <- IO.println("✅ DEMONSTRATING: Typed contract pipeline with SchemaConforms evidence")
      _ <- demonstrateTypedContractPipeline
      _ <- IO.println("")

      _ <- IO.println("🎯 This pipeline demonstrates:")
      _ <- IO.println("   - TypedSource[R] carries contract schema at type level")
      _ <- IO.println("   - TypedSink[R] enforces output schema alignment")
      _ <- IO.println("   - SchemaConforms[A, R, P] evidence prevents compilation if schemas don't align")
      _ <- IO.println("   - Clear error messages guide schema fixes")

    } yield ()

  private def demonstrateTypedContractPipeline: IO[Unit] = {
    // Mock data functions
    def readSalesData(source: DataSource): IO[SalesV1] =
      IO.pure(SalesV1("INV-001", "C-001", 100.0, System.currentTimeMillis()))

    def writeCuratedData(data: SalesCuratedV1, sink: DataSink): IO[Unit] =
      IO.println(s"   💾 Writing curated sales data: $data")

    for {
      _ <- IO.println("🚀 Building typed contract pipeline...")

      // This pipeline COMPILES because schemas align under Exact policy
      pipeline <- IO.pure {
        PipelineBuilder[IO]("canonical-typed-pipeline")
          .withDescription("Canonical typed contracts demonstration")
          .addTypedSource[SalesV1, SalesV1, SchemaPolicy.Exact](
            gcsParquetSource[SalesV1]("demo-bucket", "raw/sales/"),
            readSalesData,
          )
          .addTransform[SalesCuratedV1] { sales =>
            // Transform maintains schema compatibility
            IO.pure(SalesCuratedV1(sales.invoiceNumber, sales.customerId, sales.amount, sales.eventTs))
          }
          .addTypedSink[SalesCuratedV1, SchemaPolicy.Exact](
            gcsParquetSink[SalesCuratedV1]("demo-bucket", "curated/sales/"),
            writeCuratedData,
          )
          .build()
      }

      _ <- IO.println(s"   ✓ Typed pipeline built successfully: ${pipeline.name}")
      _ <- IO.println(s"   ✓ Pipeline has ${pipeline.stages.length} contract-validated stages")

      _ <- IO.println("📝 COMPILE-TIME FAILURES prevented by SchemaConforms evidence:")
      _ <- IO.println("   • Change SalesV1 fields -> Source SchemaConforms[SalesV1, SalesV1, Exact] missing")
      _ <- IO.println("   • Change transform output type -> Sink SchemaConforms mismatch")
      _ <- IO.println("   • Remove .addTransform() -> Missing HasTransform evidence for build()")
      _ <- IO.println("   • Remove .addTypedSink() -> Missing HasSink evidence for build()")

      _ <- IO.println("🎯 This is the exact pattern that prevents runtime schema failures!")

    } yield ()
  }
}
