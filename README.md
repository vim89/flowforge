# flowforge
Let's be honest -
1. Most data pipeline frameworks treat types as suggestions.
2. Config files are strings.
3. Schemas are "validated" at runtime.
4. Data quality is an afterthought.

Here's what we do differently:

_🛑 This won't even compile if your schema doesn't match_
```scala
// This won't even compile if your schema doesn't match
val pipeline = DataPipelineFactory[IO]
  .source(blob"gs://raw-data/sales/*.parquet")
  .contract(SalesDataContract.strict)  // Compile-time contract validation
  .transform(_.filter(_.amount >= 999))    // Type-safe transformations
  .quality(nonNull("invoice_number") and unique("customer_id"))  // Built-in quality checks
  .sink(BigQuerySink("analytics.customers"))
  .build

// Run it with automatic retry, monitoring, and error handling
pipeline.run.unsafeRunSync()
```
