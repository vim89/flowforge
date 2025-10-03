# Quick start

The smallest possible pipeline in FlowForge.

```scala
import cats.effect.IO
import com.flowforge.core.pipeline._

object QuickStart extends IOApp.Simple {
  def run: IO[Unit] =
    DataPipelineFactory[IO]
      .source(blob"gs://raw/sales.csv")
      .contract(SalesContract.strict)
      .transform(_.filter(_.amount >= 0))
      .quality(nonNull("id") and unique("id"))
      .sink(BigQuerySink("analytics.sales"))
      .build
      .run
}
```

See the [architecture overview](diagrams/overview.svg) for how components fit together.

## 3-step fast feedback loop (DX)

1) Run `sbt ffDev` to compile + run focused tests (target <3s for pure code).

2) Introduce a contract drift (e.g., change a field in a case class) and run `sbt compile` — it should fail with a readable, path‑aware diff from `SchemaConforms`.

3) Fix the type or relax the policy (Exact → Backward) and re‑run `sbt compile` — it should turn green.
