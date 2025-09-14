package com.flowforge.maintenance

import com.flowforge.core.instances.EffectInstances
import com.flowforge.core.algebra.EffectSystem
import org.apache.spark.sql.SparkSession
import io.delta.tables.DeltaTable

case class VacuumCmd(path: String, retentionHours: Int)
case class CompactCmd(path: String, targetFiles: Int)

object MaintenanceCli {
  sealed trait Command
  case class Vacuum(c: VacuumCmd)  extends Command
  case class Compact(c: CompactCmd) extends Command

  def main(args: Array[String]): Unit = {
    val parser = new scopt.OptionParser[Command]("ff-maintenance") {
      head("FlowForge Maintenance CLI", "1.0")

      cmd("vacuum")
        .action((_, _) => Vacuum(VacuumCmd("", 168)))
        .text("Vacuum Delta table (OSS Delta)")
        .children(
          opt[String]("path").required().action { case (p, Vacuum(cmd)) => Vacuum(cmd.copy(path = p)) }
            .text("Delta table path"),
          opt[Int]("retention").optional().action { case (h, Vacuum(cmd)) => Vacuum(cmd.copy(retentionHours = h)) }
            .text("Retention hours (default 168)")
        )

      cmd("compact")
        .action((_, _) => Compact(CompactCmd("", 1)))
        .text("Best-effort file compaction by coalescing")
        .children(
          opt[String]("path").required().action { case (p, Compact(cmd)) => Compact(cmd.copy(path = p)) }
            .text("Delta/Parquet path"),
          opt[Int]("targetFiles").optional().action { case (n, Compact(cmd)) => Compact(cmd.copy(targetFiles = n)) }
            .text("Target number of files (default 1)")
        )
    }

    parser.parse(args, null).foreach(run)
  }

  def run(cmd: Command): Unit = {
    implicit val F: EffectSystem[cats.effect.IO] = EffectInstances.catsEffectSystemInstance
    val spark = SparkSession.builder().appName("ff-maintenance").master("local[*]")
      .config("spark.sql.extensions", "io.delta.sql.DeltaSparkSessionExtension")
      .config("spark.sql.catalog.spark_catalog", "org.apache.spark.sql.delta.catalog.DeltaCatalog")
      .getOrCreate()
    try cmd match {
      case Vacuum(VacuumCmd(path, hours)) =>
        val dt = DeltaTable.forPath(spark, path)
        dt.vacuum(hours)
        println(s"Vacuum completed for $path (retention=$hours h)")
      case Compact(CompactCmd(path, target)) =>
        val df = spark.read.format("delta").load(path)
        df.coalesce(target).write.format("delta").mode("overwrite").option("dataChange", "false").save(path)
        println(s"Compaction (coalesce=$target) completed for $path")
    } finally spark.stop()
  }
}

