import sbt.util
import scoverage.ScoverageKeys._
import sbtdynver.DynVerPlugin.autoImport._

import scala.collection.Seq
// ===== GLOBAL BUILD SETTINGS =====
ThisBuild / organization := "com.flowforge"

// Versioning: derive from git tags via sbt-dynver (v0.8.1 -> 0.8.1)
ThisBuild / versionScheme := Some("early-semver")
ThisBuild / dynverVTagPrefix := true
ThisBuild / dynverSeparator := "-"
ThisBuild / dynverSonatypeSnapshots := true
// Default Scala stays 2.13 for most modules; Spark/Deequ modules are handled pragmatically via deps.
ThisBuild / scalaVersion := Dependencies.Versions.scala213
// Global cross-build: keep 2.13 only for stability and speed. `core` additionally builds on Scala 3.
ThisBuild / crossScalaVersions := Seq(
  Dependencies.Versions.scala213
)

// ===== CODE COVERAGE THRESHOLDS =====
// Industry benchmarks: 70-80%, Data Engineering 75-85%
// FlowForge targets: Core/Contracts 90%+, Connectors/Engines 80%+, Infrastructure 75%+
//
// FLAG-BASED ENFORCEMENT:
// During development: Report only (no CI failures)
// Production ready: Set SCOVERAGE_ENFORCE_THRESHOLD=true to fail build on low coverage
//
// Usage:
//   Development: sbt coverage test coverageReport (reports but doesn't fail)
//   CI strict:   SCOVERAGE_ENFORCE_THRESHOLD=true sbt coverage test coverageReport (fails if below threshold)
//
val enforceCoverageThreshold = sys.env.get("SCOVERAGE_ENFORCE_THRESHOLD").contains("true")

ThisBuild / coverageMinimumStmtTotal := 75
ThisBuild / coverageMinimumBranchTotal := 70
ThisBuild / coverageFailOnMinimum := enforceCoverageThreshold
ThisBuild / coverageHighlighting := true

// ===== REPOSITORY RESOLVERS =====
resolvers ++= Resolver.sonatypeOssRepos("public") ++ Seq(
  Resolver.mavenCentral,
  "Confluent" at "https://packages.confluent.io/maven/",
  "Apache Releases" at "https://repository.apache.org/content/repositories/releases/",
  "Google Cloud" at "https://maven-central.storage-download.googleapis.com/maven2/",
  "AWS SDK" at "https://repo1.maven.org/maven2/software/amazon/awssdk/",
  "Spark Packages" at "https://repos.spark-packages.org/",
)

// Compiler settings for all projects

val scala3CompilerOptions = Seq(
  "-explain",
  "-explain-types",
  "-Wconf:cat=unused:s",      // suppress unused warnings
  "-Wconf:cat=deprecation:s", // suppress deprecation warnings
  "-Wunused:nowarn",
  "-source:3.3",
  "-Wsafe-init",
  "-deprecation",
  "-Wunused:all",
  "-language:higherKinds",
  "-language:implicitConversions",
)
val scala2CompilerOptions = Seq(
  // "-Xfatal-warnings", Commented to allow deprecation warnings
  // "-Wconf:deprecation:w", // suppress deprecation warnings - Commented to allow deprecation warnings
  "-feature",
  "-unchecked",
  "-deprecation",
  "-Ywarn-unused",
  "-language:higherKinds",
  "-language:implicitConversions",
  "-Xlint:_,-missing-interpolator",
  "-Ywarn-dead-code",
  "-Ywarn-value-discard",
)

def scalacOptionsForVersion(scalaVersion: String): Seq[String] =
  CrossVersion.partialVersion(scalaVersion) match {
    case Some((2, 13)) =>
      scala2CompilerOptions
    case Some((3, _)) =>
      scala3CompilerOptions
    case _ => Seq.empty
  }

// Scoped per project, not per build: `ThisBuild / scalacOptions` would be computed once against
// `ThisBuild / scalaVersion`, so a module that pins a different Scala version got the wrong flags.

// Enable SemanticDB for Scalafix semantic rules with version compatibility
inThisBuild(
  List(
    semanticdbEnabled := true,
    semanticdbVersion := "4.10.1", // Compatible with Scala 2.13.16
  ),
)

// Ensure your app runs in a separate JVM (so sbt memory != app memory)
fork := true

// Give Spark jobs headroom when you `run` from sbt
javaOptions ++= Seq(
  "-Xms2g",
  "-Xmx6g",
  "-Duser.timezone=UTC",
  "-Dnet.bytebuddy.experimental=true",
  "--add-exports=java.base/sun.nio.ch=ALL-UNNAMED",
)

// Test settings
ThisBuild / Test / parallelExecution := false
ThisBuild / Test / testOptions += Tests.Argument("-oDF")
ThisBuild / Test / fork := true
ThisBuild / Test / javaOptions += "--add-exports=java.base/sun.nio.ch=ALL-UNNAMED"

// Scaladoc settings (visible in scaladoc.yml job)
ThisBuild / Compile / doc / scalacOptions ++= Seq(
  "-groups",
  "-doc-title",
  "FlowForge API",
  "-doc-version",
  version.value,
)

// Helper function for module projects
def moduleProject(name: String): Project =
  Project(name, file(s"modules/$name"))
    .settings(
      moduleName := s"flowforge-$name",
      scalacOptions ++= scalacOptionsForVersion(scalaVersion.value),
      libraryDependencies ++= Dependencies.common,
    )

// Demos and CLI entry points print to stdout and run effects at their `main`. See the header of
// .scalafix-demo.conf for which rules that exempts them from.
// The Test config inherits from Compile, so the Test setting has to be repeated here or the demo
// config would also apply to these modules' test sources.
lazy val demoScalafixSettings: Seq[Setting[_]] =
  Seq(
    Compile / scalafixConfig := Some(file(".scalafix-demo.conf")),
    Test / scalafixConfig := Some(file(".scalafix-test.conf")),
  )

// Binary compatibility: previous version can be supplied via env MIMA_PREVIOUS_VERSION
def mimaSettings(module: String): Seq[Setting[_]] =
  Seq(
    mimaPreviousArtifacts := sys.env
      .get("MIMA_PREVIOUS_VERSION")
      .map(v => Set(organization.value %% s"flowforge-$module" % v))
      .getOrElse(Set.empty),
  )

// ===== ROOT PROJECT =====
lazy val root = (project in file("."))
  .aggregate(
    // Infrastructure Layer
    infrastructure,
    // CLIs
    validationCli,
    maintenanceCli,
    contractsExtractorCli,
    contractsSdk,
    // Core modules
    core,
    contracts,
    connectors,
    connectorsGcs,
    connectorsJdbc,
    enginesSpark,
    enginesFlink,
    qualityDeequ, // Removed empty quality module per v1.0-2 plan
    examples,
    compileFailTests,
    experimental
  )
  .settings(
    name               := "flowforge",
    publish / skip     := true,
    crossScalaVersions := Nil,
  )

// ===== INFRASTRUCTURE LAYER (NEW) =====

lazy val infrastructure = moduleProject("infrastructure")
  .dependsOn(core)
  .settings(
    description := "Complete infrastructure layer with testing framework",
    libraryDependencies ++= Dependencies.forModule("infrastructure"),
    coverageExcludedPackages := Seq(
      "com.flowforge.infrastructure.DistributedTracing",
    ).mkString(";"),
  )
  .settings(mimaSettings("infrastructure"): _*)

// ===== CORE MODULES =====
lazy val core = moduleProject("core")
  .settings(
    description := "Core abstractions and custom type system",
    // Core is the only module published for both.
    crossScalaVersions := Seq(Dependencies.Versions.scala213, Dependencies.Versions.scala3),
    libraryDependencies ++= Dependencies.forModule("core"),
    // Minimal, justified excludes only
    coverageExcludedPackages := Seq(
      "com.flowforge.core.examples.*",
    ).mkString(";"),
    // Core module requires 90% coverage (foundational code)
    coverageMinimumStmtTotal := 90,
    coverageMinimumBranchTotal := 85,
    coverageFailOnMinimum := enforceCoverageThreshold,
    // Magnolia, scala-reflect and the scala-2/scala-3 source directories used to be wired here, because the
    // contract macro is spelled differently on each Scala version. That macro now ships in ctdc-core, which
    // carries its own per-version sources and dependencies, so everything left in this module is ordinary
    // version-agnostic Scala.
  )
  .settings(mimaSettings("core"): _*)

lazy val contracts = moduleProject("contracts")
  .dependsOn(core)
  .settings(
    description := "Compile-time and runtime data contracts",
    libraryDependencies ++= Dependencies.forModule("contracts"),
    // Contracts module requires 90% coverage (KILLER FEATURE - must be bulletproof)
    coverageMinimumStmtTotal := 90,
    coverageMinimumBranchTotal := 85,
    coverageFailOnMinimum := enforceCoverageThreshold,
  )
  .settings(mimaSettings("contracts"): _*)

// Sample "contract SDK" to demonstrate typed endpoints without local codegen

// ===== CONNECTOR MODULES =====
lazy val connectors = moduleProject("connectors")
  .dependsOn(core, contracts)
  .settings(
    description := "Base connector abstractions",
    libraryDependencies ++= Dependencies.forModule("connectors"),
    coverageExcludedPackages := Seq(
      "com.flowforge.connectors.filesystem.examples.*",
    ).mkString(";"),
    coverageExcludedFiles := Seq(
      ".*HDFSFileSystemConnector.scala",
      ".*CloudStorageConnector.scala",
    ).mkString(";"),
  )
  .settings(mimaSettings("connectors"): _*)

lazy val connectorsGcs = moduleProject("connectors-gcs")
  .dependsOn(connectors)
  .settings(
    description := "Google Cloud Storage connector",
    libraryDependencies ++= Dependencies.forModule("connectors-gcs"),
  )
  .settings(mimaSettings("connectors-gcs"): _*)

lazy val connectorsJdbc = moduleProject("connectors-jdbc")
  .dependsOn(core, connectors, enginesSpark % "test->compile")
  .settings(
    description := "JDBC connectors and helpers (Spark JDBC + effect-safe helpers)",
    libraryDependencies ++= Dependencies.forModule("connectors-jdbc"),
    Test / fork := true,
  )
  .settings(mimaSettings("connectors-jdbc"): _*)

// ===== ENGINE MODULES =====

lazy val enginesSpark = moduleProject("engines-spark")
  .dependsOn(core, connectors)
  .settings(
    description := "Apache Spark execution engine",
    libraryDependencies ++= Dependencies.forModule("engines-spark"),
  )
  .settings(mimaSettings("engines-spark"): _*)

// typed-spark merged into engines-spark under com.flowforge.engines.spark.typed

lazy val enginesFlink = moduleProject("engines-flink")
  .dependsOn(core, connectors, enginesSpark % "test->compile")
  .settings(
    description := "Apache Flink execution engine",
    libraryDependencies ++= Dependencies.forModule("engines-flink"),
    // Flink serializes records with Kryo, which reflects into `java.base`. On Java 17 and later that is
    // closed by default, and the failure arrives as an `InaccessibleObjectException` from inside a running
    // job rather than at startup. These are the opens Flink's own scripts set for the same reason.
    Test / javaOptions ++= Seq(
      "--add-opens=java.base/java.lang=ALL-UNNAMED",
      "--add-opens=java.base/java.lang.invoke=ALL-UNNAMED",
      "--add-opens=java.base/java.io=ALL-UNNAMED",
      "--add-opens=java.base/java.net=ALL-UNNAMED",
      "--add-opens=java.base/java.nio=ALL-UNNAMED",
      "--add-opens=java.base/java.text=ALL-UNNAMED",
      "--add-opens=java.base/java.time=ALL-UNNAMED",
      "--add-opens=java.base/java.util=ALL-UNNAMED",
      "--add-opens=java.base/java.util.concurrent=ALL-UNNAMED",
      "--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED",
      "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED",
    ),
    Test / fork := true,
  )
  .settings(mimaSettings("engines-flink"): _*)

// ===== QUALITY MODULES =====
// Removed empty quality module shell per v1.0-2 plan requirements
// Use quality-deequ module for data quality functionality

lazy val qualityDeequ = moduleProject("quality-deequ")
  .dependsOn(core, contracts, enginesSpark)
  .settings(
    description := "Amazon Deequ integration for data quality with native Spark fallback",
    libraryDependencies ++= Dependencies.forModule("quality-deequ"),
    // Run Spark tests in a forked JVM and open JDK internals Spark needs on modern JDKs
    Test / javaOptions ++= Seq(
      "-Dnet.bytebuddy.experimental=true",
      "--add-exports=java.base/sun.nio.ch=ALL-UNNAMED",
      "--add-opens=java.base/java.nio=ALL-UNNAMED",
      "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED",
    ),
    Test / fork              := true,
    Test / parallelExecution := false,
  )
  .settings(mimaSettings("quality-deequ"): _*)

// ===== SUPPORT MODULES =====

// ===== EXAMPLE & EXPERIMENTAL MODULES =====
lazy val examples = moduleProject("examples")
  .dependsOn(core, contracts, contractsSdk, enginesSpark, enginesFlink, qualityDeequ)
  .settings(
    description := "Example implementations",
    libraryDependencies ++= Dependencies.forModule("examples"),
    publish / skip := true,
    // Examples are for demonstration - exclude from coverage requirements
    coverageEnabled := false,
  )
  .settings(demoScalafixSettings)

// examples-spark merged into examples; module removed to avoid duplication

// CLI for physical schema validation (Delta/Hive/Parquet) for CI usage
lazy val validationCli = moduleProject("validation-cli")
  .dependsOn(core, enginesSpark)
  .settings(
    description := "FlowForge Schema Validation CLI",
    libraryDependencies ++= Dependencies.forModule("examples") ++ Seq(
      "com.github.scopt" %% "scopt"        % "4.1.0",
      "io.circe"         %% "circe-core"   % Dependencies.Versions.circe,
      "io.circe"         %% "circe-parser" % Dependencies.Versions.circe,
      // Bring Spark runtime for standalone CLI jar; keep only spark-sql for Parquet mode
      "org.apache.spark" %% "spark-sql" % Dependencies.Versions.spark,
    ),
    Compile / mainClass := Some("com.flowforge.validation.SchemaValidateCli"),
    publish / skip      := true,
  )
  .settings(demoScalafixSettings)

// CLI to infer contracts from physical sources and emit .avsc + dq/metadata YAML
lazy val contractsExtractorCli = moduleProject("contracts-extractor-cli")
  .dependsOn(core, enginesSpark)
  .settings(
    description := "FlowForge Contracts Extractor CLI",
    libraryDependencies ++= Seq(
      "com.github.scopt" %% "scopt"         % "4.1.0",
      "io.circe"         %% "circe-core"    % Dependencies.Versions.circe,
      "io.circe"         %% "circe-generic" % Dependencies.Versions.circe,
      "io.circe"         %% "circe-parser"  % Dependencies.Versions.circe,
      "org.apache.spark" %% "spark-sql"     % Dependencies.Versions.spark,
    ),
    Compile / mainClass := Some("com.flowforge.contracts.extractor.ContractsExtractorCli"),
    publish / skip      := true,
  )
  .settings(demoScalafixSettings)

// Maintenance CLI for non-SLA operations (VACUUM, compact)
lazy val maintenanceCli = moduleProject("maintenance-cli")
  .dependsOn(core, enginesSpark)
  .settings(
    description := "FlowForge Maintenance CLI (Delta VACUUM, compaction)",
    libraryDependencies ++= Dependencies.forModule("maintenance-cli"),
    Compile / mainClass := Some("com.flowforge.maintenance.MaintenanceCli"),
    publish / skip      := true,
  )
  .settings(demoScalafixSettings)

// ===== ADDITIONAL MODULES =====

// ===== SBT ALIASES =====
addCommandAlias("fmt", "all scalafmtSbt scalafmt test:scalafmt")
addCommandAlias("fmtCheck", "all scalafmtSbtCheck scalafmtCheck test:scalafmtCheck")
// scalafixAll covers every project and both configurations in one task; the per-configuration form it
// replaced only reached the project sbt happened to have loaded.
addCommandAlias("fix", "scalafixAll")
addCommandAlias("fixCheck", "scalafixAll --check")
addCommandAlias("testAll", "all test")
addCommandAlias("testQuick", "testOnly * -- -l \"org.scalatest.tags.Slow\"")
// Better compileAll: use aggregation-aware sequence, not `all`
// Avoid clean here to preserve incremental compilation speed
addCommandAlias("compileAll", ";compile; test:compile")
addCommandAlias("coverage", "clean; coverage; testAll; coverageReport")
addCommandAlias("fullTest", "clean; compileAll; fmt; fix; testAll")
addCommandAlias("fullCheck", "clean; compileAll; fmtCheck; fixCheck; testAll")
addCommandAlias("assembly", "core/assembly")

// Quick development cycle
addCommandAlias("dev", "~core/testQuick")
addCommandAlias("devAll", "~testQuick")

// Module-specific testing
addCommandAlias("testCore", "core/test")
addCommandAlias("testConnectors", "connectors*/test")
addCommandAlias("testEngines", "engines*/test")

// Assembly for different modules
addCommandAlias("assemblyCore", "core/assembly")
addCommandAlias("assemblySpark", "enginesSpark/assembly")
addCommandAlias("assemblyExamples", "examples/assembly")

// Assembly merge strategy
ThisBuild / assemblyMergeStrategy := {
  case PathList("META-INF", xs @ _*)  => MergeStrategy.discard
  case x if x.endsWith(".conf")       => MergeStrategy.concat
  case x if x.endsWith(".properties") => MergeStrategy.concat
  case x if x.endsWith(".xml")        => MergeStrategy.first
  case x                              => MergeStrategy.first
}

// MVR convenience alias: compile + unit tests only (no opt-in ITs)
addCommandAlias("mvr", "compileAll; testQuick")

// FlowForge DX commands per end-to-end plan
// FlowForge aliases required by end-to-end plan section 8
addCommandAlias(
  "ffCheck",
  "compile-fail-tests/test; quality-deequ/test",
)                                                  // compile-fail + quality integration checks
addCommandAlias("ffDev", "compileAll; testQuick")  // local run with fixtures in <3s
addCommandAlias("ffRunSpark", "engines-spark/run") // Spark local[*], DQ + Delta sink

// Legacy aliases removed - using canonical ffCheck, ffDev, ffRunSpark above

// Ensure examples compiles after contracts-sdk (belt-and-suspenders ordering)
examples / Compile / compile := (examples / Compile / compile)
  .dependsOn(contractsSdk / Compile / compile, core / Compile / compile)
  .value

// ===== COMPILE-FAIL TESTS MODULE =====
lazy val compileFailTests = moduleProject("compile-fail-tests")
  .dependsOn(core, contracts, examples)
  .settings(
    description := "Compile-fail tests proving FlowForge's core USP: pipelines become unbuildable on schema drift",
    libraryDependencies ++= Dependencies.forModule("core"),
    // These tests are designed to fail compilation when uncommented
    publish / skip := true,
  )

// ===== LOCAL CONTRACT VALIDATION (delegates to validation-cli) =====
// Usage:
//   sbt ffValidate --mode parquet --input "/path/to/table" --expected-json contracts/avro/sales/Entity.v1.0.0.avsc --expected-format spark
lazy val ffValidate =
  inputKey[Unit]("Validate physical schema vs contract using validation-cli (CI-first parity)")

ThisBuild / ffValidate := Def.inputTaskDyn {
  import sbt.complete.DefaultParsers._
  val args = spaceDelimited("").parsed
  (validationCli / Compile / run).toTask(" " + args.mkString(" "))
}.evaluated
// ===== CONTRACTS SDK (generated from contracts/avro) =====
lazy val contractsSdk = moduleProject("contracts-sdk")
  .dependsOn(core)
  .settings(
    description := "Generated typed contracts SDK (from contracts/avro)",
    Compile / sourceGenerators += Def.task {
      val out    = (Compile / sourceManaged).value / "contractsSdk"
      val base   = (ThisBuild / baseDirectory).value / "contracts" / "avro"
      val logger = streams.value.log
      val files  = (base ** "*.avsc").get
      IO.createDirectory(out)
      val generated = files.flatMap { f =>
        val rel     = IO.relativize(base, f).getOrElse(f.getName)
        val content = IO.read(f)
        ContractsCodegen.generateScala(rel, content) match {
          case Right(codegen) =>
            val file = out / codegen.relativePath
            IO.createDirectory(file.getParentFile)
            IO.write(file, codegen.contents)
            logger.info(s"[contracts-sdk] generated: ${file.getAbsolutePath}")
            Some(file)
          case Left(err) =>
            logger.warn(s"[contracts-sdk] skipped $rel: $err")
            None
        }
      }
      generated
    }.taskValue,
  )
  .settings(mimaSettings("contracts-sdk"): _*)
// Experimental Scala 3 module for capture checking demos (opt-in)
lazy val experimental = moduleProject("experimental")
  .settings(
    description        := "Experimental Scala 3 POCs",
    scalaVersion       := Dependencies.Versions.scala3,
    crossScalaVersions := Seq(Dependencies.Versions.scala3),
    scalacOptions ++= Seq(
      "-explain",
      "-source:3.3"
    ),
    Compile / mainClass := Some("com.flowforge.experimental.caprese.Main"),
    publish / skip      := true,
    // The Scala 2 only rules are dropped here so this module still goes through the same DisableSyntax gate.
    scalafixConfig := Some(file(".scalafix-scala3.conf")),
  )
// ===== UNIDOC (optional unified API) =====
import sbtunidoc.ScalaUnidocPlugin
import sbtunidoc.ScalaUnidocPlugin.autoImport._

// Only aggregate the Scala 2.13 modules. This keeps unidoc stable.
lazy val unidocProjects = Seq(
  core,
  contracts,
  connectors,
  connectorsGcs,
  connectorsJdbc,
  enginesSpark,
  enginesFlink,
  qualityDeequ,
  infrastructure,
  examples,
  validationCli,
  contractsExtractorCli,
  maintenanceCli,
)

ThisBuild / ScalaUnidoc / unidocProjectFilter := inProjects(unidocProjects.map(_.project): _*)

// Scalafix: Disable auto-run on compile (run explicitly in CI)
ThisBuild / scalafixOnCompile := false

// Test sources use a looser rule set. See the header of .scalafix-test.conf for which rules are
// dropped and why. Scalafix has no per-file excludes, so the split has to be made here.
ThisBuild / Test / scalafixConfig := Some(file(".scalafix-test.conf"))
