import sbt.util

import scala.collection.Seq
// ===== GLOBAL BUILD SETTINGS =====
ThisBuild / organization := "com.flowforge"

ThisBuild / version      := "0.1.0"
ThisBuild / scalaVersion := Dependencies.Versions.scala213
ThisBuild / crossScalaVersions := Seq(
  Dependencies.Versions.scala212,
  Dependencies.Versions.scala213
  // Dependencies.Versions.scala3
)

// ===== REPOSITORY RESOLVERS =====
resolvers ++= Resolver.sonatypeOssRepos("public") ++ Seq(
  Resolver.mavenCentral,
  "Confluent" at "https://packages.confluent.io/maven/",
  "Apache Releases" at "https://repository.apache.org/content/repositories/releases/",
  "Google Cloud" at "https://maven-central.storage-download.googleapis.com/maven2/",
  "AWS SDK" at "https://repo1.maven.org/maven2/software/amazon/awssdk/",
  "Spark Packages" at "https://repos.spark-packages.org/"
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
  "-Ywarn-value-discard"
)
val scala2CompilerOptions = Seq(
  // "-Xfatal-warnings", Commented to allow deprecation warnings
  // "-Wconf:deprecation:w", // suppress deprecation warnings - Commented to allow deprecation warnings
  "-feature",
  "-unchecked",
  "-deprecation",
  "-language:higherKinds",
  "-language:implicitConversions",
  "-Xlint:_,-missing-interpolator",
  "-Ywarn-dead-code",
  "-Ywarn-value-discard"
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

// Test settings
ThisBuild / Test / parallelExecution := false
ThisBuild / Test / testOptions += Tests.Argument("-oDF")

// Helper function for module projects
def moduleProject(name: String): Project =
  Project(name, file(s"modules/$name"))
    .settings(
      moduleName := s"flowforge-$name",
      libraryDependencies ++= Dependencies.common
    )

// ===== ROOT PROJECT =====
lazy val root = (project in file("."))
  .aggregate(
    // Infrastructure Layer (NEW)
    safety,
    config,
    logging,
    infrastructure,
    // Existing modules
    core,
    framework,
    contracts,
    connectors,
    connectorsGcs,
    connectorsS3,
    connectorsBigQuery,
    connectorsKafka,
    connectorsAzure,
    engines,
    enginesSpark,
    enginesFlink,
    quality,
    qualityDeequ,
    templates,
    monitoring,
    testing,
    examples,
    experimental,
    benchmarks,
    it
  )
  .settings(
    name               := "flowforge",
    publish / skip     := true,
    crossScalaVersions := Nil
  )

// ===== INFRASTRUCTURE LAYER (NEW) =====
lazy val safety = moduleProject("safety")
  .dependsOn(core)
  .settings(
    description := "Resource safety and bracket patterns",
    libraryDependencies ++= Dependencies.forModule("safety")
  )

lazy val config = moduleProject("config")
  .dependsOn(core, safety)
  .settings(
    description := "Type-safe configuration management (CCM replacement)",
    libraryDependencies ++= Dependencies.forModule("config")
  )

lazy val logging = moduleProject("logging")
  .dependsOn(core, config)
  .settings(
    description := "Structured logging and observability framework",
    libraryDependencies ++= Dependencies.forModule("logging")
  )

lazy val infrastructure = moduleProject("infrastructure")
  .dependsOn(safety, config, logging)
  .settings(
    description := "Complete infrastructure layer with testing framework",
    libraryDependencies ++= Dependencies.forModule("infrastructure")
  )

// ===== CORE MODULES =====
lazy val core = moduleProject("core")
  .settings(
    description := "Core abstractions and custom type system",
    libraryDependencies ++= Dependencies.forModule("core")
  )

lazy val framework = moduleProject("framework")
  .dependsOn(core, contracts)
  .settings(
    description := "Advanced pipeline combinators and orchestration",
    libraryDependencies ++= Dependencies.forModule("framework")
  )

lazy val contracts = moduleProject("contracts")
  .dependsOn(core)
  .settings(
    description := "Compile-time and runtime data contracts",
    libraryDependencies ++= Dependencies.forModule("contracts")
  )

// ===== CONNECTOR MODULES =====
lazy val connectors = moduleProject("connectors")
  .dependsOn(core, contracts)
  .settings(
    description := "Base connector abstractions",
    libraryDependencies ++= Dependencies.forModule("connectors")
  )

lazy val connectorsGcs = moduleProject("connectors-gcs")
  .dependsOn(connectors)
  .settings(
    description := "Google Cloud Storage connector",
    libraryDependencies ++= Dependencies.forModule("connectors-gcs")
  )

lazy val connectorsS3 = moduleProject("connectors-s3")
  .dependsOn(connectors)
  .settings(
    description := "Amazon S3 connector",
    libraryDependencies ++= Dependencies.forModule("connectors-s3")
  )

lazy val connectorsBigQuery = moduleProject("connectors-bigquery")
  .dependsOn(connectors)
  .settings(
    description := "Google BigQuery connector",
    libraryDependencies ++= Dependencies.forModule("connectors-bigquery")
  )

lazy val connectorsKafka = moduleProject("connectors-kafka")
  .dependsOn(connectors)
  .settings(
    description := "Apache Kafka connector",
    libraryDependencies ++= Dependencies.forModule("connectors-kafka")
  )

lazy val connectorsAzure = moduleProject("connectors-azure")
  .dependsOn(connectors)
  .settings(
    description := "Azure connector",
    libraryDependencies ++= Dependencies.forModule("connectors-azure")
  )

// ===== ENGINE MODULES =====
lazy val engines = moduleProject("engines")
  .dependsOn(core, contracts, framework)
  .settings(
    description := "Base execution engine abstractions",
    libraryDependencies ++= Dependencies.forModule("engines")
  )

lazy val enginesSpark = moduleProject("engines-spark")
  .dependsOn(engines, connectors)
  .settings(
    description := "Apache Spark execution engine",
    libraryDependencies ++= Dependencies.forModule("engines-spark")
  )

lazy val enginesFlink = moduleProject("engines-flink")
  .dependsOn(engines, connectors)
  .settings(
    description := "Apache Flink execution engine",
    libraryDependencies ++= Dependencies.forModule("engines-flink")
  )

// ===== QUALITY MODULES =====
lazy val quality = moduleProject("quality")
  .dependsOn(core, contracts, framework)
  .settings(
    description := "Data quality framework",
    libraryDependencies ++= Dependencies.forModule("quality")
  )

lazy val qualityDeequ = moduleProject("quality-deequ")
  .dependsOn(quality, enginesSpark)
  .settings(
    description := "Amazon Deequ integration for data quality",
    libraryDependencies ++= Dependencies.forModule("quality-deequ")
  )

// ===== SUPPORT MODULES =====
lazy val templates = moduleProject("templates")
  .dependsOn(core, contracts, framework, quality)
  .settings(
    description := "Pipeline Giter8 templates and code generation",
    libraryDependencies ++= Dependencies.forModule("templates")
  )

lazy val monitoring = moduleProject("monitoring")
  .dependsOn(core, framework)
  .settings(
    description := "Monitoring and observability",
    libraryDependencies ++= Dependencies.forModule("monitoring")
  )

lazy val testing = moduleProject("testing")
  .dependsOn(core, contracts, framework, quality)
  .settings(
    description := "Testing utilities and frameworks",
    libraryDependencies ++= Dependencies.forModule("testing")
  )

// ===== EXAMPLE & EXPERIMENTAL MODULES =====
lazy val examples = moduleProject("examples")
  .dependsOn(core, contracts, framework, connectors, connectorsGcs, engines, enginesSpark, quality)
  .settings(
    description := "Example implementations",
    libraryDependencies ++= Dependencies.forModule("examples"),
    publish / skip := true
  )

lazy val experimental = moduleProject("experimental")
  .dependsOn(core, framework)
  .settings(
    description := "Experimental features & prototypes: ML, distributed computing, Kyo, Caprese",
    crossScalaVersions := Seq(Dependencies.Versions.scala213),
    libraryDependencies ++= Dependencies.forModule("experimental"),
    publish / skip := true
  )

// ===== ADDITIONAL MODULES =====
lazy val benchmarks = (project in file("benchmarks"))
  .dependsOn(core, framework, examples)
  .settings(
    name        := "flowforge-benchmarks",
    description := "Performance benchmarks",
    libraryDependencies ++= Dependencies.common,
    publish / skip := true
  )

lazy val it = (project in file("integration-tests"))
  .dependsOn(examples, testing, connectorsGcs, connectorsBigQuery, enginesSpark, qualityDeequ)
  .settings(
    name           := "flowforge-integration-tests",
    description    := "Integration tests",
    publish / skip := true,
    Test / fork    := true
  )

// ===== SBT ALIASES =====
addCommandAlias("fmt", "all scalafmtSbt scalafmt test:scalafmt")
addCommandAlias("fmtCheck", "all scalafmtSbtCheck scalafmtCheck test:scalafmtCheck")
addCommandAlias("fix", "all compile:scalafix test:scalafix")
addCommandAlias("fixCheck", "compile:scalafix --check ; test:scalafix --check")
addCommandAlias("testAll", "all test")
addCommandAlias("testQuick", "testOnly * -- -l \"org.scalatest.tags.Slow\"")
addCommandAlias("compileAll", "all compile test:compile")
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
