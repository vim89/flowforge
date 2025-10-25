# Appendix – Engine Portability Proof (Concept Reference)

> Use only if an organizer explicitly asks for multi-engine details. Keep this out of the main decks so the WHY narrative stays focused.

## Purpose
- Document the minimal algebra + interpreter seam that lets one pipeline run on Spark or Flink.
- Provide one slide-worth of content in case Q&A turns toward portability.

## Core Message
- Pipelines depend on an algebra of typed stages (`DataAlgebra[F]`) rather than a concrete engine.
- Interpreters (`SparkRunner`, `FlinkRunner`) live behind the same trait; swapping interpreters changes the engine without touching user jobs.
- Effect polymorphism keeps orchestration logic identical (`F[_]: Async`), matching the ScalaIO organizer pitch about fiber-safe execution.

## Sample Slide Copy
**TITLE:** Engine Portability in One Diagram  
**Bullets:**
- Algebra first: `Read`, `Transform`, `Write` defined against `F[_]`.
- Spark interpreter wires the algebra into Dataset ops; Flink interpreter targets DataStream.
- Pipeline code stays the same; choose the interpreter at wiring time.

## Optional Diagram
```
Pipeline[F, In, Out]
        |
        v
  DataAlgebra[F]
   /          \
SparkRunner  FlinkRunner
```

## Demo Snippet (keep off main deck)
```scala
trait EngineRunner[F[_]] {
  def run[A](pipeline: Pipeline[F, Unit, A]): F[A]
}

final class SparkRunner[F[_]: Async](session: SparkSession) extends EngineRunner[F] { /* ... */ }
final class FlinkRunner[F[_]: Async](env: StreamExecutionEnvironment) extends EngineRunner[F] { /* ... */ }

// Pipeline code stays unchanged
val job: Pipeline[F, Unit, Output] = customerPipeline.build

// Choose engine at the edge
val sparkResult = new SparkRunner[F](spark).run(job)
val flinkResult = new FlinkRunner[F](env).run(job)
```

## When to Use
- Q&A question: “Does this only work on Spark?” → swap to this appendix slide.
- Organizer request to highlight portability → add as optional slide after “Remember Why.”
- Blog follow-up or reference material for attendees who want deeper technical proof.
