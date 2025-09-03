# Typed Contract Pipelines — Compile-Time Gates in Action

Updated: 2025-09-03

This doc shows how FlowForge enforces compile-time schema gates using typed endpoints and SchemaEq evidence.

## What to look at
- Example pipeline: `modules/examples/src/main/scala/com/flowforge/examples/TypedContractPipeline.scala`
- Sample contract SDK: `modules/contracts-sample-sdk/src/main/scala/com/flowforge/contracts/sample/SalesContracts.scala`
- Core evidence: `modules/core/src/main/scala/com/flowforge/core/types/SchemaEvidence.scala`
- Typed-only builder: `modules/core/src/main/scala/com/flowforge/core/types/PipelineBuilder2.scala`

## Key Idea
- `TypedSource[R]` and `TypedSink[R]` carry the contract schema at the type level (as an HList representation R).
- Builder methods accept only typed endpoints and require `SchemaEq[A, R]` evidence. If your pipeline type A doesn’t align with the contract schema R, compilation fails.

## Minimal example
```scala
val source = Endpoints.gcsParquetSourceFor[SalesV1]("demo-bucket", "raw/sales/")
val sink   = Endpoints.parquetSinkFor[SalesCuratedV1]("/tmp/curated/sales")

val builder = PipelineBuilder2[IO]("sales-typed")
  .addTypedSource(source, _ => F.pure(SalesV1("INV-1", "C-1", 100.0, System.currentTimeMillis())))
  .addTransform[SalesCuratedV1](v => F.pure(SalesCuratedV1(v.invoiceNumber, v.customerId, v.amount, v.eventTs)))
  .addTypedSink(sink, (out, ds) => F.delay(println(s"Would write record $out to $ds")))
```

If you change the transformation to produce a type that does not match `SalesCuratedV1`’s schema (e.g., missing `eventTs`), the call to `addTypedSink` fails to compile with a clear message from `SchemaEq`.

## Evolving toward SDKs per domain
In real teams, contracts are authored via UI and published as versioned SDK JARs. This repo includes `contracts-sample-sdk` for demonstration, but production builds should depend on SDK artifacts instead of defining schema types locally.

## Next examples to add
- Projection-safe pipelines: allow strict subset/superset policies.
- Join pipelines with two sources and a curated sink.
- Streaming CDC example with typed records and quality rules.
- Scala 3 variant using Mirrors and inline errors (no shapeless).

