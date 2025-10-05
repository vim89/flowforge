package com.flowforge.examples.demo

import cats.effect.{ IO, IOApp }

import scala.concurrent.duration._

object CatsFibers extends IOApp.Simple {
  def work(name: String): IO[Int] =
    IO.println(s"[$name] start") *>
      IO.sleep(750.millis) *>
      IO.println(s"[$name] done") *> IO.pure(name.length)

  val run: IO[Unit] =
    for {
      f1 <- work("alpha").start
      f2 <- work("beta").start
      r1 <- f1.joinWithNever
      r2 <- f2.joinWithNever
      _  <- IO.println(s"results: $r1 + $r2 = ${r1 + r2}")
    } yield ()

  implicit val sessionMetricsFFEncoder: com.flowforge.core.algebra.DataEncoder[SessionMetrics] =
    com.flowforge.core.algebra.DataEncoder.instance[SessionMetrics](
      (metrics, format) =>
        format match {
          case DataFormat.JSON | DataFormat.JSONL =>
            Right(
              com.flowforge.core.algebra.EncodedData(
                sessionMetricsEncoder(metrics).noSpaces.getBytes("UTF-8"),
                format,
              ),
            )
          case DataFormat.Parquet =>
            // Simplified parquet encoding for demo
            Right(
              com.flowforge.core.algebra.EncodedData(
                sessionMetricsEncoder(metrics).noSpaces.getBytes("UTF-8"),
                format,
              ),
            )
          case other =>
            Left(com.flowforge.core.algebra.CorruptedData(s"Unsupported format: " + other))
        },
      _ =>
        DataSchema.builder
          .addField("userId", DataType.Long)
          .addField("sessionId", DataType.String)
          .addField("eventCount", DataType.Integer)
          .addField("sessionDurationMs", DataType.Long)
          .addField("firstEventTime", DataType.Long)
          .addField("lastEventTime", DataType.Long)
          .addField("eventTypes", DataType.Array(DataType.String))
          .addField("windowStart", DataType.Long)
          .addField("windowEnd", DataType.Long)
          .build,
    )
}
