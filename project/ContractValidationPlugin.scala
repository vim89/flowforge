import sbt._
import sbt.Keys._

/**
 * Minimal sbt AutoPlugin defining FlowForge schema verification tasks.
 *
 * NOTE: This is a lightweight scaffold that can later delegate to
 * modules/validation-cli Canonical model. For now it validates that
 * expected schema file exists and the target path is non-empty.
 */
object ContractValidationPlugin extends AutoPlugin {
  object autoImport {
    val ffVerifySourcePhysical = taskKey[Unit](
      "Verify source physical schema against expected JSON (minimal stub)",
    )
    val ffVerifyTargetPhysical = taskKey[Unit](
      "Verify target physical schema against expected JSON (minimal stub)",
    )
    val ffExpectedSchema = settingKey[File]("Path to expected schema JSON file")
    val ffInputPath      = settingKey[String]("Path or table to verify")
  }

  import autoImport._

  override def projectSettings: Seq[Def.Setting[_]] = Seq(
    ffExpectedSchema := baseDirectory.value / "expected-schema.json",
    ffInputPath      := "",
    ffVerifySourcePhysical := {
      val log      = streams.value.log
      val expected = ffExpectedSchema.value
      val input    = ffInputPath.value
      if (!expected.exists()) sys.error(s"Expected schema file not found: ${expected.getAbsolutePath}")
      if (input.trim.isEmpty) sys.error("ffInputPath not set; please set in ThisBuild or project settings")
      log.info(s"[ffVerifySourcePhysical] OK (stub): expected=${expected.getName} input=${input}")
    },
    ffVerifyTargetPhysical := {
      val log      = streams.value.log
      val expected = ffExpectedSchema.value
      val input    = ffInputPath.value
      if (!expected.exists()) sys.error(s"Expected schema file not found: ${expected.getAbsolutePath}")
      if (input.trim.isEmpty) sys.error("ffInputPath not set; please set in ThisBuild or project settings")
      log.info(s"[ffVerifyTargetPhysical] OK (stub): expected=${expected.getName} input=${input}")
    },
  )
}

