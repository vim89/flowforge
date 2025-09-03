package com.flowforge.engines.spark

import org.apache.spark.sql.SparkSession

/**
 * Optional Delta Lake integration via reflection to avoid hard dependency. If io.delta:delta-core is on the
 * classpath, performs SCD1-style MERGE.
 */
object DeltaSupport {

  final case class MergeConfig(keys: List[String])

  def isAvailable: Boolean =
    scala.util.Try(Class.forName("io.delta.tables.DeltaTable")).isSuccess

  def scd1Merge(
    spark: SparkSession,
    sourcePath: String,
    targetPath: String,
    keys: List[String],
  ): Either[String, Unit] = {
    if (!isAvailable) return Left("Delta Lake not available on classpath")
    scala.util.Try {
      val deltaTableClz = Class.forName("io.delta.tables.DeltaTable")
      val forPath       = deltaTableClz.getMethod("forPath", classOf[SparkSession], classOf[String])
      val target        = forPath.invoke(null, spark, targetPath)

      val srcDf   = spark.read.format("delta").load(sourcePath)
      val keyExpr = keys.map(k => s"target.$k = source.$k").mkString(" AND ")

      val mergeMethod = deltaTableClz.getMethods
        .find(m => m.getName == "alias" || m.getName == "as")
        .map(_ => ())

      // NOTE: Full reflection for merge/update/insert chain is verbose; for now, document required Delta ops
      // and return Left with instruction if not implemented.
      Left(
        "Delta MERGE reflection path stub: wire actual merge/update/insert via reflection or add delta-core dependency",
      )
    }.fold(t => Left(s"Delta MERGE failed: ${t.getMessage}"), identity)
  }
}
