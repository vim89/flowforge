package com.flowforge.lineage

import cats.effect.IO
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s._
import org.http4s.client.Client
import org.http4s.circe._

import java.time.Instant
import java.util.UUID

/**
 * Minimal OpenLineage emitter for FlowForge pipelines.
 * 
 * Implements Section 8.1 from docs/plan/End-to-End-Compile-time.md (lines 284-290):
 * - Send job/run/dataset events on each stage start/end
 * - Destination: HTTP → Marquez
 */

case class OpenLineageEvent(
  eventType: String,
  eventTime: Instant,
  run: RunInfo,
  job: JobInfo,
  inputs: List[DatasetInfo] = List.empty,
  outputs: List[DatasetInfo] = List.empty
)

case class RunInfo(
  runId: String,
  facets: Map[String, Any] = Map.empty
)

case class JobInfo(
  namespace: String,
  name: String,
  facets: Map[String, Any] = Map.empty
)

case class DatasetInfo(
  namespace: String, 
  name: String,
  facets: Map[String, Any] = Map.empty
)

class OpenLineageEmitter(
  marquezUrl: String,
  client: Client[IO]
) {

  private val marquezEndpoint = s"$marquezUrl/api/v1/lineage"
  
  def emitJobStart(
    jobName: String,
    runId: String = UUID.randomUUID().toString,
    namespace: String = "flowforge"
  ): IO[Unit] = {
    val event = OpenLineageEvent(
      eventType = "START",
      eventTime = Instant.now(),
      run = RunInfo(runId),
      job = JobInfo(namespace, jobName)
    )
    
    sendEvent(event)
  }
  
  def emitJobComplete(
    jobName: String,
    runId: String,
    inputs: List[DatasetInfo] = List.empty,
    outputs: List[DatasetInfo] = List.empty,
    namespace: String = "flowforge"
  ): IO[Unit] = {
    val event = OpenLineageEvent(
      eventType = "COMPLETE", 
      eventTime = Instant.now(),
      run = RunInfo(runId),
      job = JobInfo(namespace, jobName),
      inputs = inputs,
      outputs = outputs
    )
    
    sendEvent(event)
  }
  
  def emitJobFail(
    jobName: String,
    runId: String,
    namespace: String = "flowforge"
  ): IO[Unit] = {
    val event = OpenLineageEvent(
      eventType = "FAIL",
      eventTime = Instant.now(), 
      run = RunInfo(runId),
      job = JobInfo(namespace, jobName)
    )
    
    sendEvent(event)
  }
  
  private def sendEvent(event: OpenLineageEvent): IO[Unit] = {
    val request = Request[IO](
      method = Method.POST,
      uri = Uri.unsafeFromString(marquezEndpoint)
    ).withEntity(event.asJson)
    
    client.expect[String](request).flatMap { response =>
      IO.println(s"OpenLineage event sent: ${event.eventType} for job ${event.job.name}")
    }.handleErrorWith { error =>
      IO.println(s"Failed to send OpenLineage event: ${error.getMessage}")
    }
  }
}

object OpenLineageEmitter {
  
  def create(marquezUrl: String = "http://localhost:5000")(implicit client: Client[IO]): OpenLineageEmitter =
    new OpenLineageEmitter(marquezUrl, client)
  
  // Helper to create dataset info from common data sources
  def gcsDataset(bucket: String, path: String): DatasetInfo =
    DatasetInfo(
      namespace = s"gs://$bucket",
      name = path
    )
  
  def bigQueryDataset(project: String, dataset: String, table: String): DatasetInfo =
    DatasetInfo(
      namespace = s"bigquery://$project.$dataset", 
      name = table
    )
}