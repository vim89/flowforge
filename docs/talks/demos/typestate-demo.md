# Demo runbook - Type indexing / Typestate Builder pattern

## Goal
Show that build() is unavailable until all required parts are present.

## Steps
1) Construct a pipeline/job without a sink → attempt build() → compile‑time error.
2) Add the sink → build() typechecks.

## Example (pseudocode)
```scala
val builder = PipelineBuilder("demo")
  .addSource(typedSource)        // has contract evidence
  .noTransform                   // or addTransform(...)

builder.build // ❌ type error: requires Complete

val ok = builder
  .addSink(typedSink)
  .build // ✅
```

Notes
- Keep the error snippet short; show the required state (Complete) in the message.
- Tie back to “illegal states unrepresentable” and safer migrations.

