package example

import cats.effect.{ IO, IOApp }
import org.apache.spark.sql.SparkSession

object Pipeline extends IOApp.Simple {
  def run: IO[Unit] = IO.blocking {
    val spark = SparkSession.builder().appName("$name$").master("local[*]").getOrCreate()
    try {
      import spark.implicits._

      val data = Seq(
        ("a", 10.0),
        ("b", 20.5),
        ("c", 30.2)
      ).toDF("id", "amount")

      val out = "target/tmp/delta/$name$/curated"
      // Write as Delta for a minimal, local demo
      data.write.format("delta").mode("overwrite").save(out)

      val readBack = spark.read.format("delta").load(out)
      println("=== Read Back Schema ===")
      readBack.printSchema()
      println("=== Sample Rows ===")
      readBack.show(false)
    } finally {
      spark.stop()
    }
  }
}

