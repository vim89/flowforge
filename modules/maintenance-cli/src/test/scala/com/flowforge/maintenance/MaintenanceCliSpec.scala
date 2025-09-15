package com.flowforge.maintenance

import org.scalatest.funsuite.AnyFunSuite

class MaintenanceCliSpec extends AnyFunSuite {
  test("vacuum subcommand parses path and retention") {
    val args = List("vacuum", "--path", "/tables/users", "--retention", "24")
    val parsed = new scopt.OptionParser[MaintenanceCli.Command]("ff-maintenance") {
      head("FlowForge Maintenance CLI", "test")
      cmd("vacuum")
        .action((_, _) => MaintenanceCli.Vacuum(VacuumCmd("", 168))).children(
          opt[String]("path").required().action {
            case (p, MaintenanceCli.Vacuum(cmd)) => MaintenanceCli.Vacuum(cmd.copy(path = p))
            case (_, other)                      => other
          },
          opt[Int]("retention").optional().action {
            case (h, MaintenanceCli.Vacuum(cmd)) => MaintenanceCli.Vacuum(cmd.copy(retentionHours = h))
            case (_, other)                      => other
          },
        )
      cmd("compact")
        .action((_, _) => MaintenanceCli.Compact(CompactCmd("", 1))).children(
          opt[String]("path").required().action {
            case (p, MaintenanceCli.Compact(cmd)) => MaintenanceCli.Compact(cmd.copy(path = p))
            case (_, other)                       => other
          },
          opt[Int]("targetFiles").optional().action {
            case (n, MaintenanceCli.Compact(cmd)) => MaintenanceCli.Compact(cmd.copy(targetFiles = n))
            case (_, other)                       => other
          },
        )
    }.parse(args, null)

    assert(parsed.contains(MaintenanceCli.Vacuum(VacuumCmd("/tables/users", 24))))
  }

  test("compact subcommand parses path and targetFiles") {
    val args = List("compact", "--path", "/tables/users", "--targetFiles", "2")
    val parsed = new scopt.OptionParser[MaintenanceCli.Command]("ff-maintenance") {
      head("FlowForge Maintenance CLI", "test")
      cmd("vacuum")
        .action((_, _) => MaintenanceCli.Vacuum(VacuumCmd("", 168))).children(
          opt[String]("path").required().action {
            case (p, MaintenanceCli.Vacuum(cmd)) => MaintenanceCli.Vacuum(cmd.copy(path = p))
            case (_, other)                      => other
          },
          opt[Int]("retention").optional().action {
            case (h, MaintenanceCli.Vacuum(cmd)) => MaintenanceCli.Vacuum(cmd.copy(retentionHours = h))
            case (_, other)                      => other
          },
        )
      cmd("compact")
        .action((_, _) => MaintenanceCli.Compact(CompactCmd("", 1))).children(
          opt[String]("path").required().action {
            case (p, MaintenanceCli.Compact(cmd)) => MaintenanceCli.Compact(cmd.copy(path = p))
            case (_, other)                       => other
          },
          opt[Int]("targetFiles").optional().action {
            case (n, MaintenanceCli.Compact(cmd)) => MaintenanceCli.Compact(cmd.copy(targetFiles = n))
            case (_, other)                       => other
          },
        )
    }.parse(args, null)

    assert(parsed.contains(MaintenanceCli.Compact(CompactCmd("/tables/users", 2))))
  }
}
