package com.flowforge.lineage
import java.net.{ HttpURLConnection, URL }
import java.nio.charset.StandardCharsets

final case class OpenLineageConfig(
  endpoint: String,
  namespace: String,
  job: String)
trait LineageEmitter { def emit(json: String): Unit }

object LineageEmitter {
  def http(cfg: OpenLineageConfig): LineageEmitter =
    new LineageEmitter {
      def emit(json: String): Unit = {
        val url  = new URL(cfg.endpoint)
        val conn = url.openConnection().asInstanceOf[HttpURLConnection]
        conn.setRequestMethod("POST")
        conn.setDoOutput(true)
        conn.setRequestProperty("Content-Type", "application/json")
        val bytes = json.getBytes(StandardCharsets.UTF_8)
        conn.getOutputStream.write(bytes)
        val code = conn.getResponseCode
        if (code / 100 != 2) sys.error(s"OpenLineage POST failed: HTTP $code")
      }
    }

  def minimalStart(
    namespace: String,
    job: String,
    runId: String,
    producer: String,
    eventTime: String,
  ): String =
    s"""{
       |  "eventType":"START",
       |  "eventTime":"$eventTime",
       |  "run":{"runId":"$runId"},
       |  "job":{"namespace":"$namespace","name":"$job"},
       |  "producer":"$producer"
       |}""".stripMargin
}
