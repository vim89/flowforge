package com.flowforge.maintenance

import cats.effect.{ ExitCode, IO, IOApp, Resource }
import com.flowforge.core.algebra.EffectSystem
import com.flowforge.core.instances.EffectInstances
import com.flowforge.core.logging.CoreLogger
import io.delta.tables.DeltaTable
import org.apache.spark.sql.SparkSession

case class VacuumCmd(path: String, retentionHours: Int)
case class CompactCmd(path: String, targetFiles: Int)

object MaintenanceCli extends IOApp {
  sealed trait Command
  case class Vacuum(c: VacuumCmd)   extends Command
  case class Compact(c: CompactCmd) extends Command

  def run(args: List[String]): IO[ExitCode] = {
    implicit val F: EffectSystem[IO] = EffectInstances.catsEffectSystemInstance
    implicit val L: CoreLogger[IO]   = com.flowforge.core.logging.CoreLogger.noOp[IO]

    val parser = new scopt.OptionParser[Command]("ff-maintenance") {
      head("FlowForge Maintenance CLI", "1.0")

      cmd("vacuum")
        .action((_, _) => Vacuum(VacuumCmd("", 168)))
        .text("Vacuum Delta table (OSS Delta)")
        .children(
          opt[String]("path")
            .required().action {
              case (p, Vacuum(cmd)) => Vacuum(cmd.copy(path = p))
              case (_, other)       => other
            }
            .text("Delta table path"),
          opt[Int]("retention")
            .optional().action {
              case (h, Vacuum(cmd)) => Vacuum(cmd.copy(retentionHours = h))
              case (_, other)       => other
            }
            .text("Retention hours (default 168)"),
        )

      cmd("compact")
        .action((_, _) => Compact(CompactCmd("", 1)))
        .text("Best-effort file compaction by coalescing")
        .children(
          opt[String]("path")
            .required().action {
              case (p, Compact(cmd)) => Compact(cmd.copy(path = p))
              case (_, other)        => other
            }
            .text("Delta/Parquet path"),
          opt[Int]("targetFiles")
            .optional().action {
              case (n, Compact(cmd)) => Compact(cmd.copy(targetFiles = n))
              case (_, other)        => other
            }
            .text("Target number of files (default 1)"),
        )
    }

    parser.parse(args, null) match {
      case None => IO.pure(ExitCode.Error)
      case Some(cmd) =>
        val sparkR: Resource[IO, SparkSession] = Resource.make {
          IO {
            val b = SparkSession
              .builder()
              .appName("ff-maintenance")
              // Do not hardcode master; allow spark-submit or SPARK_MASTER to control it
              .config("spark.ui.enabled", "false")
              .config("spark.sql.extensions", "io.delta.sql.DeltaSparkSessionExtension")
              .config("spark.sql.catalog.spark_catalog", "org.apache.spark.sql.delta.catalog.DeltaCatalog")
            sys.env.get("SPARK_MASTER").foreach(b.master)
            b.getOrCreate()
          }
        }(s => IO(s.stop()))

        sparkR.use { spark =>
          cmd match {
            case Vacuum(VacuumCmd(path, hours)) =>
              IO(DeltaTable.forPath(spark, path).vacuum(hours)) *>
                L.info(s"Vacuum completed for $path (retention=$hours h)")
            case Compact(CompactCmd(path, target)) =>
              IO(
                spark.read
                  .format("delta").load(path).coalesce(target).write
                  .format("delta").mode("overwrite").option("dataChange", "false").save(path),
              ) *> L.info(s"Compaction (coalesce=$target) completed for $path")
          }
        }
          .as(ExitCode.Success)
    }
  }
}
