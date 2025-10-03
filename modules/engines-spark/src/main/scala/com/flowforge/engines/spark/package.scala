package com.flowforge.engines

/**
  * =Spark Engine=
  *
  * Production‑ready Spark integration implementing the core [[com.flowforge.core.algebra.DataAlgebra]] with
  * native quality checks and optional Deequ enhancement.
  *
  * ==Highlights==
  *  - Dataset façade with helpers (see [[com.flowforge.engines.spark.ProductionSparkDataset]]).
  *  - Native quality checks; Deequ via reflection if present on classpath.
  *  - Idempotent edge operations recommended for sinks and side‑effects.
  *
  * ==Quick start==
  * {{@example
  * import cats.effect.IO
  * import org.apache.spark.sql.SparkSession
  * import com.flowforge.engines.spark.SparkDataAlgebra
  *
  * val spark = SparkSession.builder().master("local[*]").appName("ff").getOrCreate()
  * val alg   = SparkDataAlgebra.createSparkDataAlgebra[IO](spark).algebra
  * }}
  */
package object spark
