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
