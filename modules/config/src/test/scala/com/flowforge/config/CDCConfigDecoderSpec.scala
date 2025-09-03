package com.flowforge.config

import org.scalatest.funsuite.AnyFunSuite
import com.typesafe.config.ConfigFactory
import com.flowforge.core.algebra.CDCOperations

class CDCConfigDecoderSpec extends AnyFunSuite {
  test("CDCConfig decoder parses keys, timestamp, scd2, partition, hash, zorder") {
    val hocon = """
      cdc {
        keyColumns = ["id", "cat"]
        timestampColumn = "ts"
        deleteDetection = true
        batchSize = 5000
        scd2 { effectiveFrom = "effective_from", effectiveTo = "effective_to", isCurrent = "is_current" }
        partition { partitionBy = ["id", "cat"] }
        hashColumns = ["v"]
        optimizeAfterMerge = false
        zOrderBy = ["id"]
      }
    """.stripMargin

    val cfg     = ConfigFactory.parseString(hocon)
    val decoded = ConfigDecoder[CDCOperations.CDCConfig].decode(cfg, "cdc").toEither
    assert(
      decoded.isRight,
      decoded.left.toOption.map(_.toList.map(_.message).mkString(", ")).getOrElse("")
    )
    val c = decoded.toOption.get
    assert(c.keyColumns.toList.map(_.value) == List("id", "cat"))
    assert(c.timestampColumn.exists(_.value == "ts"))
    assert(c.scd2.exists(_.effectiveFrom.value == "effective_from"))
    assert(c.partition.exists(_.partitionBy.map(_.value) == List("id", "cat")))
    assert(c.hashColumns.exists(_.toList.map(_.value) == List("v")))
    assert(c.zOrderBy.exists(_.toList.map(_.value) == List("id")))
  }
}
