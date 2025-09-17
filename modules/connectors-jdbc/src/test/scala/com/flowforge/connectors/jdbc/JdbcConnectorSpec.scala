package com.flowforge.connectors.jdbc

import cats.effect.IO
import com.flowforge.core.algebra.{ DataAlgebra, EffectSystem }
import com.flowforge.core.instances.EffectInstances
import com.flowforge.core.instances.DefaultCodecs._
import com.flowforge.core.types.{ DataSink, DataSource }
import com.flowforge.core.types.RefinedTypes.TableName
import com.flowforge.engines.spark.SparkDataAlgebra
import org.apache.spark.sql.SparkSession
import org.scalatest.funsuite.AnyFunSuite
import cats.effect.unsafe.implicits.global
import org.scalatest.matchers.should.Matchers

class JdbcConnectorSpec extends AnyFunSuite with Matchers {

  implicit val F: EffectSystem[IO] = EffectInstances.catsEffectSystemInstance

  test("read and write via Spark JDBC (H2 in-memory)") {
    val spark = SparkSession.builder().appName("ff-jdbc-spec").master("local[2]").getOrCreate()
    try {
      val url    = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1"
      val driver = "org.h2.Driver"

      // Prepare table
      val conn = java.sql.DriverManager.getConnection(url)
      try {
        val st = conn.createStatement()
        st.execute("create table if not exists users(id int primary key, name varchar(64))")
        st.execute("insert into users(id,name) values (1,'Alice'), (2,'Bob')")
        st.close()
      } finally conn.close()

      val algebra = SparkDataAlgebra.createSparkDataAlgebra[IO](spark).algebra
      import com.flowforge.core.types._
      import com.flowforge.core.instances.DefaultCodecs._
      // Prepare small dataset to write via JDBC
      val data = List(
        Map("ID" -> "10", "NAME" -> "Zara"),
        Map("ID" -> "11", "NAME" -> "Mike"),
      )
      val schema = DataSchema.builder
        .addField("ID", DataType.Integer, required = true)
        .addField("NAME", DataType.String, required = true)
        .build
      val ds = com.flowforge.core.impl.SimpleDataset[Map[String, String]](
        data = data,
        schema = schema,
        metadata = DataAlgebra.DatasetMetadata(2L, schema, 1, java.time.Instant.now()),
      )
      val sink = new com.flowforge.core.types.JdbcSink(url, TableName("users_copy"), driver)
      val wr   = algebra.write(ds, sink, DataAlgebra.WriteOptions()).unsafeRunSync()
      wr.success shouldBe true

      val conn2 = java.sql.DriverManager.getConnection(url)
      try {
        val rs = conn2.createStatement().executeQuery("select count(*) from users_copy")
        rs.next()
        rs.getInt(1) shouldBe 2
        rs.close()
      } finally conn2.close()
    } finally spark.stop()
  }
}
