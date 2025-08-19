// ===== GLOBAL BUILD SETTINGS =====
ThisBuild / organization := "com.flowforge"
ThisBuild / version := "0.1.0"
ThisBuild / scalaVersion := Dependencies.Versions.scala213
ThisBuild / crossScalaVersions := Seq(Dependencies.Versions.scala213, Dependencies.Versions.scala3)

// Compiler settings for all projects
ThisBuild / scalacOptions ++= Seq(
  "-Xfatal-warnings",
  "-feature",
  "-deprecation", 
  "-unchecked",
  "-language:higherKinds",
  "-language:implicitConversions",
  "-Xlint:_,-missing-interpolator",
  "-Ywarn-dead-code",
  "-Ywarn-value-discard"
)

// Test settings
ThisBuild / Test / parallelExecution := false
ThisBuild / Test / testOptions += Tests.Argument("-oDF")

// ===== ROOT PROJECT =====
lazy val root = (project in file("."))
  .aggregate(
    core,
    safety,
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
    benchmarks
  )
  .settings(
    name := "flowforge",
    publish / skip := true,
    crossScalaVersions := Nil
  )

// ===== CORE MODULES =====
lazy val core = moduleProject("core")
  .settings(
    description := "Core abstractions and type system for FlowForge",
    libraryDependencies ++= Dependencies.forModule("core") ++ Seq(
      "com.chuusai" %% "shapeless" % Dependencies.Versions.shapeless % "provided"
    )
  )

lazy val safety = moduleProject("safety")
  .dependsOn(core)
  .settings(
    description := "Effect-safe operations and resource management",
    libraryDependencies ++= Dependencies.forModule("safety")
  )

// ===== CONNECTOR MODULES =====
lazy val connectors = moduleProject("connectors")
  .dependsOn(core, safety)
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
    description := "Azure Storage connector",
    libraryDependencies ++= Dependencies.forModule("connectors-azure")
  )

// ===== ENGINE MODULES =====
lazy val engines = moduleProject("engines")
  .dependsOn(core, safety)
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
  .dependsOn(core, safety)
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
  .dependsOn(core)
  .settings(
    description := "Giter8 templates and code generation",
    libraryDependencies ++= Dependencies.common
  )

lazy val monitoring = moduleProject("monitoring")
  .dependsOn(core, safety)
  .settings(
    description := "Monitoring and observability",
    libraryDependencies ++= Dependencies.forModule("monitoring")
  )

lazy val testing = moduleProject("testing")
  .dependsOn(core, safety)
  .settings(
    description := "Testing utilities and frameworks",
    libraryDependencies ++= Dependencies.forModule("testing")
  )

// ===== EXAMPLE & EXPERIMENTAL MODULES =====
lazy val examples = moduleProject("examples")
  .dependsOn(core, safety, enginesSpark, connectorsGcs, quality)
  .settings(
    description := "Example implementations and tutorials",
    libraryDependencies ++= Dependencies.forModule("examples"),
    publish / skip := true
  )

lazy val experimental = moduleProject("experimental")
  .dependsOn(core, safety)
  .settings(
    description := "Experimental features and prototypes",
    libraryDependencies ++= Dependencies.forModule("experimental"),
    publish / skip := true
  )

// ===== BENCHMARKS =====
lazy val benchmarks = (project in file("benchmarks"))
  .dependsOn(core, safety, examples)
  .enablePlugins(JmhPlugin)
  .settings(
    name := "flowforge-benchmarks",
    description := "Performance benchmarks",
    libraryDependencies ++= Dependencies.common,
    publish / skip := true
  )

// ===== HELPER FUNCTIONS =====
def moduleProject(name: String): Project = 
  Project(name.replace("-", ""), file(s"modules/$name"))
    .settings(
      moduleName := s"flowforge-$name",
      crossScalaVersions := (ThisBuild / crossScalaVersions).value
    )

// ===== SBT ALIASES =====
addCommandAlias("fmt", "all scalafmtSbt scalafmt test:scalafmt")
addCommandAlias("fmtCheck", "all scalafmtSbtCheck scalafmtCheck test:scalafmtCheck")
addCommandAlias("testAll", "all test")
addCommandAlias("testQuick", "testOnly * -- -l \"org.scalatest.tags.Slow\"")
addCommandAlias("compileAll", "all compile test:compile")
addCommandAlias("fullTest", "clean; compileAll; fmt; testAll")
addCommandAlias("fullCheck", "clean; compileAll; fmtCheck; testAll")
addCommandAlias("release", "fullCheck; publishSigned; sonatypeBundleRelease")

// Quick development cycle
addCommandAlias("dev", "~core/testQuick")
addCommandAlias("devAll", "~testQuick")

// Module-specific testing
addCommandAlias("testCore", "core/test")
addCommandAlias("testConnectors", "connectors*/test")
addCommandAlias("testEngines", "engines*/test")

// ===== GITHUB ACTIONS INTEGRATION =====
ThisBuild / githubWorkflowJavaVersions := Seq(JavaSpec.temurin("11"), JavaSpec.temurin("17"))
ThisBuild / githubWorkflowScalaVersions := Seq(Dependencies.Versions.scala213, Dependencies.Versions.scala3)

ThisBuild / githubWorkflowBuild := Seq(
  WorkflowStep.Sbt(List("fullCheck")),
  WorkflowStep.Sbt(
    List("benchmarks/Jmh/run"), 
    cond = Some(s"matrix.scala == '${Dependencies.Versions.scala213}'")
  )
)

ThisBuild / githubWorkflowTargetTags ++= Seq("v*")
ThisBuild / githubWorkflowPublishTargetBranches := Seq(RefPredicate.StartsWith(Ref.Tag("v")))

ThisBuild / githubWorkflowPublish := Seq(
  WorkflowStep.Sbt(
    List("ci-release"),
    env = Map(
      "PGP_PASSPHRASE" -> "${{ secrets.PGP_PASSPHRASE }}",
      "PGP_SECRET" -> "${{ secrets.PGP_SECRET }}",
      "SONATYPE_PASSWORD" -> "${{ secrets.SONATYPE_PASSWORD }}",
      "SONATYPE_USERNAME" -> "${{ secrets.SONATYPE_USERNAME }}"
    )
  )
)
