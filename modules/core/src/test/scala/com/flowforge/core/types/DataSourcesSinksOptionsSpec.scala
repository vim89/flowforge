package com.flowforge.core.types

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import com.flowforge.core.testing.What
import com.flowforge.core.types.DataSink.WriteMode

class DataSourcesSinksOptionsSpec extends AnyFunSuite with Matchers {

  test("GCS sink setters: compression, partitioning, write mode", What) {
    val sink = DataSink.gcs("bucket", "prefix", DataFormat.Parquet)
      .withCompression(CompressionType.Snappy)
      .partitionedBy("dt", "id")
      .withWriteMode(WriteMode.Overwrite)

    sink.compression shouldBe CompressionType.Snappy
    sink.partitionBy shouldBe List("dt", "id")
    sink.writeMode shouldBe WriteMode.Overwrite
  }

  test("S3 sink setters: compression, partitioning, write mode", What) {
    val sink = DataSink.s3("bucket", "out", DataFormat.JSON)
      .withCompression(CompressionType.Gzip)
      .partitionedBy("p1")
      .withWriteMode(WriteMode.Append)

    sink.compression shouldBe CompressionType.Gzip
    sink.partitionBy shouldBe List("p1")
    sink.writeMode shouldBe WriteMode.Append
  }

  test("Local data source/sink carry format and path", What) {
    val src  = DataSource.local("/in", DataFormat.CSV)
    val sink = DataSink.local("/out", DataFormat.CSV)
    src.format shouldBe DataFormat.CSV
    sink.format shouldBe DataFormat.CSV
  }

  test("JDBC and BigQuery sources hold optional attributes", What) {
    val jdbc = DataSource.jdbc("jdbc:postgresql://h/db", "users", "org.postgresql.Driver")
      .withQuery("select 1")
    jdbc.query shouldBe Some("select 1")

    val bq = DataSource.bigQuery("my-project-123", "my_dataset", "my_table").withFilter("dt='2025-01-01'")
    bq.filter shouldBe Some("dt='2025-01-01'")
  }
}
