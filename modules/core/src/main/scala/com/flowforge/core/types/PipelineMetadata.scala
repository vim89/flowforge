package com.flowforge.core.types

import java.time.Instant

/**
 * Pipeline metadata for tracking and management.
 */
case class PipelineMetadata(
  version: String = "1.0.0",
  author: String = "system",
  createdAt: Instant = Instant.now(),
  updatedAt: Instant = Instant.now(),
  tags: Set[String] = Set.empty,
  properties: Map[String, String] = Map.empty)
