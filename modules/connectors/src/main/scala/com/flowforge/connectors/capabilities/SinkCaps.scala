package com.flowforge.connectors.capabilities
sealed trait SupportsMerge
sealed trait SupportsBatch
final case class Sink[R, Caps](impl: Any)
object Writes {
  import org.apache.spark.sql.Dataset
  def writeMerge[R](s: Sink[R, SupportsMerge], ds: Dataset[R]): Unit = ()
  def writeBatch[R](s: Sink[R, SupportsBatch], ds: Dataset[R]): Unit = ()
}
