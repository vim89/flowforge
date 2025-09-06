package com.flowforge.lineage
import java.time.OffsetDateTime
import java.util.UUID

object DemoEmit {
  def main(args: Array[String]): Unit = {
    val endpoint  = sys.env.getOrElse("OPENLINEAGE_ENDPOINT", "http://localhost:5000/api/v1/lineage")
    val namespace = "flowforge-demo"
    val job       = "typed-quickstart"
    val runId     = UUID.randomUUID.toString
    val producer  = "https://github.com/vim89/flowforge"
    val when      = OffsetDateTime.now.toString
    val cfg       = OpenLineageConfig(endpoint, namespace, job)
    val emitter   = LineageEmitter.http(cfg)
    val payload   = LineageEmitter.minimalStart(namespace, job, runId, producer, when)
    emitter.emit(payload)
  }
}
