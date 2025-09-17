package com.flowforge.app

import cats.effect.{ IO, Resource }
import cats.syntax.functor._
import java.sql.{ Connection, DriverManager }

/**
 * Minimal JDBC audit DB (H2) to demonstrate polymorphic effectful logging around the pipeline.
 */
object AuditDb {
  private val url  = "jdbc:h2:./target/auditdb;MODE=PostgreSQL;DATABASE_TO_UPPER=false"
  private val user = "sa"
  private val pass = ""

  private def connR: Resource[IO, Connection] =
    Resource.make(IO(DriverManager.getConnection(url, user, pass)))(c => IO(c.close()).void)

  def init(): IO[Unit] = connR.use { c =>
    IO {
      val st = c.createStatement()
      st.executeUpdate(
        "CREATE TABLE IF NOT EXISTS audit_log (ts TIMESTAMP DEFAULT CURRENT_TIMESTAMP, event VARCHAR(64))"
      )
      st.close()
    }.void
  }

  def log(event: String): IO[Unit] = connR.use { c =>
    IO {
      val ps = c.prepareStatement("INSERT INTO audit_log(event) VALUES (?)")
      ps.setString(1, event)
      ps.executeUpdate()
      ps.close()
    }.void
  }
}

