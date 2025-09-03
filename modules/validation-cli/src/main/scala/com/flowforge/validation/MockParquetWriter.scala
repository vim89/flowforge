package com.flowforge.validation

import cats.effect.{ IO, IOApp }
import com.flowforge.core.algebra.EffectSystem
import com.flowforge.core.instances.EffectInstances._
import org.apache.spark.sql.SparkSession

/** Writes a tiny Parquet dataset with a fixed schema for CI demos. */
object MockParquetWriter extends IOApp.Simple {
  private val F = EffectSystem[IO]
  final case class rec(invoiceNumber: String, customerId: String, amount: Double, eventTs: Long)

  def run: IO[Unit] = F.blocking {
    val spark = SparkSession
      .builder()
      .appName("mock-parquet-writer")
      .master("local[2]")
      .config("spark.ui.enabled", "false")
      .getOrCreate()
    import spark.implicits._
    Seq(rec("INV-9", "C-9", 99.0, System.currentTimeMillis()))
      .toDS()
      .write
      .mode("overwrite")
      .parquet(sys.props.getOrElse("ff.mock.output", "/tmp/mock.parquet"))
    spark.stop()
  }
}
