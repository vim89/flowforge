package com.flowforge.examples.connectors

import com.flowforge.core.algebra.{ DataDecoder, DataEncoder, EffectSystem }
import com.flowforge.core.algebra.{ EncodedData => FFEncodedData }
import com.flowforge.core.types.DataFormat

import java.nio.file.{ Files, Paths, StandardOpenOption }

/**
  * Minimal file-backed Kafka facade.
  *
  * Simulates a Kafka topic as a JSONL file at `<baseDir>/<topic>.jsonl`.
  * Each publish appends a JSON line. consumeAll reads and decodes all lines.
  *
  * This is for runnable examples without adding heavy dependencies. The real
  * wiring for Kafka (clients, Spark Structured Streaming, or Flink) would live
  * under connectors/engines.
  */
object KafkaFacade {

  trait KafkaTopic[F[_], A] {
    def publish(a: A): F[Unit]
    def publishAll(as: List[A]): F[Unit]
    def consumeAll: F[List[A]]
  }

  def topic[F[_], A](baseDir: String, topic: String)(implicit
    F: EffectSystem[F],
    enc: DataEncoder[A],
    dec: DataDecoder[A],
  ): KafkaTopic[F, A] = new KafkaTopic[F, A] {

    private val path = Paths.get(baseDir, s"$topic.jsonl")

    private def ensureDir(): F[Unit] = {
      val mkParent: F[Unit] = F.map(F.blocking {
        if (!Files.exists(path.getParent)) Files.createDirectories(path.getParent)
      })(_ => ())
      val mkFile: F[Unit] = F.map(F.blocking {
        if (!Files.exists(path)) Files.createFile(path) else path
      })(_ => ())
      F.flatMap(mkParent)(_ => mkFile)
    }

    def publish(a: A): F[Unit] =
      F.flatMap(ensureDir()) { _ =>
        F.map(F.blocking {
          val json = enc.encode(a, DataFormat.JSON).fold(e => throw new RuntimeException(e.message), _.data)
          Files.write(path, json ++ "\n".getBytes("UTF-8"), StandardOpenOption.APPEND)
        })(_ => ())
      }

    def publishAll(as: List[A]): F[Unit] = as.foldLeft(F.pure(())) { (acc, a) => F.flatMap(acc)(_ => publish(a)) }

    def consumeAll: F[List[A]] = F.blocking {
      if (!Files.exists(path)) List.empty[A]
      else {
        import scala.jdk.CollectionConverters._
        val lines = Files.readAllLines(path).asScala.toList
        lines.flatMap { line =>
          dec.decode(FFEncodedData(line.getBytes("UTF-8"), DataFormat.JSON), DataFormat.JSON).toOption
        }
      }
    }
  }
}
