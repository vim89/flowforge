import sbt.util

import scala.collection.Seq
// ===== GLOBAL BUILD SETTINGS =====
ThisBuild / organization := "com.flowforge"

ThisBuild / version := "0.1.0-SNAPSHOT"
// Default Scala stays 2.13 for most modules; Spark/Deequ modules are handled pragmatically via deps.
ThisBuild / scalaVersion := Dependencies.Versions.scala213
ThisBuild / crossScalaVersions := Seq(
  Dependencies.Versions.scala212,
  Dependencies.Versions.scala213,
  // Dependencies.Versions.scala3  // TODO: Enable when Scala 3 implementation for compile-time contracts is ready
)

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
  "-Xlint:_,-missing-interpolator",
  "-Ywarn-dead-code",
  "-Ywarn-value-discard",
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

ThisBuild / scalacOptions ++= scalacOptionsForVersion(scalaVersion.value)

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

// Helper function for module projects
def moduleProject(name: String): Project =
  Project(name, file(s"modules/$name"))
    .settings(
      moduleName := s"flowforge-$name",
      libraryDependencies ++= Dependencies.common,
    )

// ===== ROOT PROJECT =====
lazy val root = (project in file("."))
  .aggregate(
    // Infrastructure Layer
    infrastructure,
    // CLIs
    validationCli,
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
    it,
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
  )

// ===== CORE MODULES =====
lazy val core = moduleProject("core")
  .settings(
    description := "Core abstractions and custom type system",
    libraryDependencies ++= Dependencies.forModule("core"),
    // Section 13.3 - Version-specific dependencies for Scala 2/3 cross-build
    libraryDependencies ++= {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, _)) =>
          Seq(
            "com.softwaremill.magnolia1_2" %% "magnolia"      % "1.1.10",
            "org.scala-lang"                % "scala-reflect" % scalaVersion.value,
          )
        case Some((3, _)) =>
          Seq(
            // Scala 3 uses built-in Mirrors, no external dependencies needed
          )
        case _ => Seq.empty
      }
    },
    // Section 13.3 - Version-specific source directories for Scala 2/3 cross-build
    Compile / unmanagedSourceDirectories ++= {
      val base = (Compile / sourceDirectory).value
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, _)) => Seq(base / "scala-2")
        case Some((3, _)) => Seq(base / "scala-3")
        case _            => Nil
      }
    },
    Test / unmanagedSourceDirectories ++= {
      val base = (Test / sourceDirectory).value
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, _)) => Seq(base / "scala-2")
        case Some((3, _)) => Seq(base / "scala-3")
        case _            => Nil
      }
    },
  )

lazy val contracts = moduleProject("contracts")
  .dependsOn(core)
  .settings(
    description := "Compile-time and runtime data contracts",
    libraryDependencies ++= Dependencies.forModule("contracts"),
  )

// Sample "contract SDK" to demonstrate typed endpoints without local codegen

// ===== CONNECTOR MODULES =====
lazy val connectors = moduleProject("connectors")
  .dependsOn(core, contracts)
  .settings(
    description := "Base connector abstractions",
    libraryDependencies ++= Dependencies.forModule("connectors"),
  )

lazy val connectorsGcs = moduleProject("connectors-gcs")
  .dependsOn(connectors)
  .settings(
    description := "Google Cloud Storage connector",
    libraryDependencies ++= Dependencies.forModule("connectors-gcs"),
  )

lazy val connectorsJdbc = moduleProject("connectors-jdbc")
  .dependsOn(core, connectors, enginesSpark % "test->compile")
  .settings(
    description := "JDBC connectors and helpers (Spark JDBC + effect-safe helpers)",
    libraryDependencies ++= Dependencies.forModule("connectors-jdbc"),
    Test / fork := true,
  )

// ===== ENGINE MODULES =====

lazy val enginesSpark = moduleProject("engines-spark")
  .dependsOn(core, connectors)
  .settings(
    description := "Apache Spark execution engine",
    libraryDependencies ++= Dependencies.forModule("engines-spark"),
  )

// typed-spark merged into engines-spark under com.flowforge.engines.spark.typed

lazy val enginesFlink = moduleProject("engines-flink")
  .dependsOn(core, connectors, enginesSpark % "test->compile")
  .settings(
    description := "Apache Flink execution engine",
    crossScalaVersions := Seq(Dependencies.Versions.scala212, Dependencies.Versions.scala213),
    libraryDependencies ++= Dependencies.forModule("engines-flink"),
  )

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
    // Make it runnable for ffCheck command
    Compile / mainClass := Some("com.flowforge.quality.deequ.ContractToDeltaExample"),
  )

// ===== SUPPORT MODULES =====

// ===== EXAMPLE & EXPERIMENTAL MODULES =====
lazy val examples = moduleProject("examples")
  .dependsOn(core, contracts, contractsSdk, enginesSpark, qualityDeequ)
  .settings(
    description := "Example implementations",
    libraryDependencies ++= Dependencies.forModule("examples"),
    publish / skip := true,
  )

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

// Maintenance CLI for non-SLA operations (VACUUM, compact)
lazy val maintenanceCli = moduleProject("maintenance-cli")
  .dependsOn(core, enginesSpark)
  .settings(
    description := "FlowForge Maintenance CLI (Delta VACUUM, compaction)",
    libraryDependencies ++= Dependencies.forModule("maintenance-cli"),
    Compile / mainClass := Some("com.flowforge.maintenance.MaintenanceCli"),
    publish / skip      := true,
  )

// ===== ADDITIONAL MODULES =====

lazy val it = (project in file("integration-tests"))
  .dependsOn(examples, connectorsGcs, enginesSpark)
  .settings(
    name           := "integration-tests",
    description    := "Flowforge Integration tests",
    publish / skip := true,
    Test / fork    := true,
    Test / skip    := !sys.props.get("withSparkIT").contains("true"),
  )

// ===== SBT ALIASES =====
addCommandAlias("fmt", "all scalafmtSbt scalafmt test:scalafmt")
addCommandAlias("fmtCheck", "all scalafmtSbtCheck scalafmtCheck test:scalafmtCheck")
addCommandAlias("fix", "all compile:scalafix test:scalafix")
addCommandAlias("fixCheck", "compile:scalafix --check ; test:scalafix --check")
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
  "compile-fail-tests/test; quality-deequ/run",
)                                                  // compile-fail + contract diff check
addCommandAlias("ffDev", "compileAll; testQuick")  // local run with fixtures in <3s
addCommandAlias("ffRunSpark", "engines-spark/run") // Spark local[*], DQ + Delta sink

// Legacy aliases removed - using canonical ffCheck, ffDev, ffRunSpark above

// Ensure examples compiles after contracts-sdk (belt-and-suspenders ordering)
examples / Compile / compile := (examples / Compile / compile)
  .dependsOn(contractsSdk / Compile / compile)
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
