package com.flowforge.core.lineage
import cats.implicits._
import com.flowforge.core.algebra.EffectSystem

import java.time.Instant
import java.util.UUID
import scala.util.Try

/**
 * OpenLineage event emitter for FlowForge v1.0
 *
 * Provides automatic lineage emission following OpenLineage specification. Designed to work "out of the box"
 * with Marquez and other OpenLineage-compatible systems.
 */
trait OpenLineageEmitter[F[_]] {
  def emitJobStart(
    namespace: String,
    jobName: String,
    runId: String,
  ): F[Either[LineageError, Unit]]
  def emitJobComplete(
    namespace: String,
    jobName: String,
    runId: String,
  ): F[Either[LineageError, Unit]]
  def emitJobFail(
    namespace: String,
    jobName: String,
    runId: String,
    error: String,
  ): F[Either[LineageError, Unit]]
}

case class LineageError(message: String, cause: Option[Throwable] = None)

/**
 * HTTP-based OpenLineage emitter that posts events to OpenLineage-compatible endpoints
 */
class HttpOpenLineageEmitter[F[_]: EffectSystem] extends OpenLineageEmitter[F] {

  private val F = EffectSystem[F]

  // Configuration from environment variables (zero-config approach)
  private val openLineageUrl = sys.env.getOrElse("OPENLINEAGE_URL", "http://localhost:5000/api/v1/lineage")
  sys.env.getOrElse("OPENLINEAGE_NAMESPACE", "flowforge")

  def emitJobStart(
    namespace: String,
    jobName: String,
    runId: String,
  ): F[Either[LineageError, Unit]] = {
    val eventTime = Instant.now().toString
    val event     = createStartEvent(namespace, jobName, runId, eventTime)
    emitEvent(event)
  }

  def emitJobComplete(
    namespace: String,
    jobName: String,
    runId: String,
  ): F[Either[LineageError, Unit]] = {
    val eventTime = Instant.now().toString
    val event     = createCompleteEvent(namespace, jobName, runId, eventTime)
    emitEvent(event)
  }

  def emitJobFail(
    namespace: String,
    jobName: String,
    runId: String,
    error: String,
  ): F[Either[LineageError, Unit]] = {
    val eventTime = Instant.now().toString
    val event     = createFailEvent(namespace, jobName, runId, eventTime, error)
    emitEvent(event)
  }

  private def createStartEvent(
    namespace: String,
    jobName: String,
    runId: String,
    eventTime: String,
  ): String =
    s"""{
      "eventType": "START",
      "eventTime": "$eventTime",
      "run": {
        "runId": "$runId"
      },
      "job": {
        "namespace": "$namespace",
        "name": "$jobName"
      },
      "inputs": [],
      "outputs": [],
      "producer": "https://github.com/flowforge/flowforge"
    }"""

  private def createCompleteEvent(
    namespace: String,
    jobName: String,
    runId: String,
    eventTime: String,
  ): String =
    s"""{
      "eventType": "COMPLETE", 
      "eventTime": "$eventTime",
      "run": {
        "runId": "$runId"
      },
      "job": {
        "namespace": "$namespace",
        "name": "$jobName"
      },
      "inputs": [],
      "outputs": [],
      "producer": "https://github.com/flowforge/flowforge"
    }"""

  private def createFailEvent(
    namespace: String,
    jobName: String,
    runId: String,
    eventTime: String,
    error: String,
  ): String =
    s"""{
      "eventType": "FAIL",
      "eventTime": "$eventTime", 
      "run": {
        "runId": "$runId"
      },
      "job": {
        "namespace": "$namespace",
        "name": "$jobName"
      },
      "inputs": [],
      "outputs": [],
      "producer": "https://github.com/flowforge/flowforge",
      "schemaURL": "https://openlineage.io/spec/1-0-5/OpenLineage.json#/definitions/RunEvent"
    }"""

  private def emitEvent(eventJson: String): F[Either[LineageError, Unit]] =
    F.handleError {
      for {
        _      <- F.delay(println(s"[OpenLineage] Emitting to $openLineageUrl: $eventJson"))
        result <- postEventToEndpoint(openLineageUrl, eventJson)
      } yield result
    } { error =>
      Left(LineageError(s"Failed to emit lineage event: ${error.getMessage}", Some(error)))
    }

  private def postEventToEndpoint(endpoint: String, eventJson: String): F[Either[LineageError, Unit]] =
    F.delay {
      Try {
        // Use simple HTTP client for zero-dependency approach
        val url        = new java.net.URL(endpoint)
        val connection = url.openConnection().asInstanceOf[java.net.HttpURLConnection]

        connection.setRequestMethod("POST")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("User-Agent", "FlowForge/1.0.0")
        connection.setDoOutput(true)

        // Write JSON payload
        val outputStream = connection.getOutputStream
        outputStream.write(eventJson.getBytes("UTF-8"))
        outputStream.flush()
        outputStream.close()

        // Check response
        val responseCode = connection.getResponseCode
        if (responseCode >= 200 && responseCode < 300) {
          ()
        } else {
          throw new RuntimeException(s"HTTP error $responseCode from OpenLineage endpoint")
        }
      }.toEither.left.map(error => LineageError(error.getMessage, Some(error)))
    }
}

/**
 * No-op OpenLineage emitter for disabled lineage scenarios
 *
 * Per v10-6.md plan: provides NoopEmitter[F] for scenarios where lineage emission should be disabled
 */
class NoopOpenLineageEmitter[F[_]: EffectSystem] extends OpenLineageEmitter[F] {

  private val F = EffectSystem[F]

  def emitJobStart(
    namespace: String,
    jobName: String,
    runId: String,
  ): F[Either[LineageError, Unit]] =
    F.pure(Right(()))

  def emitJobComplete(
    namespace: String,
    jobName: String,
    runId: String,
  ): F[Either[LineageError, Unit]] =
    F.pure(Right(()))

  def emitJobFail(
    namespace: String,
    jobName: String,
    runId: String,
    error: String,
  ): F[Either[LineageError, Unit]] =
    F.pure(Right(()))
}

object OpenLineageEmitter {

  def http[F[_]: EffectSystem]: OpenLineageEmitter[F] = new HttpOpenLineageEmitter[F]

  def noop[F[_]: EffectSystem]: OpenLineageEmitter[F] = new NoopOpenLineageEmitter[F]

  def asyncHttp[F[_]: EffectSystem](capacity: Int = 1024): OpenLineageEmitter[F] =
    new AsyncOpenLineageEmitter[F](http[F], capacity)

  // Generate a unique run ID for each pipeline execution
  def generateRunId(): String = UUID.randomUUID().toString

  // Helper for pipeline-level events
  def emitPipelineStart[F[_]: EffectSystem](
    emitter: OpenLineageEmitter[F],
    pipelineName: String,
    runId: String,
  ): F[Either[LineageError, Unit]] = {
    val namespace = sys.env.getOrElse("OPENLINEAGE_NAMESPACE", "flowforge")
    emitter.emitJobStart(namespace, pipelineName, runId)
  }

  def emitPipelineComplete[F[_]: EffectSystem](
    emitter: OpenLineageEmitter[F],
    pipelineName: String,
    runId: String,
  ): F[Either[LineageError, Unit]] = {
    val namespace = sys.env.getOrElse("OPENLINEAGE_NAMESPACE", "flowforge")
    emitter.emitJobComplete(namespace, pipelineName, runId)
  }

  def emitPipelineFail[F[_]: EffectSystem](
    emitter: OpenLineageEmitter[F],
    pipelineName: String,
    runId: String,
    error: String,
  ): F[Either[LineageError, Unit]] = {
    val namespace = sys.env.getOrElse("OPENLINEAGE_NAMESPACE", "flowforge")
    emitter.emitJobFail(namespace, pipelineName, runId, error)
  }
}
