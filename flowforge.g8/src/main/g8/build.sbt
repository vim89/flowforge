ThisBuild / organization := "$organization$"
ThisBuild / scalaVersion := "2.13.16"

// Scalafix needs semanticdb files and the unused warnings to run the rules in .scalafix.conf.
ThisBuild / semanticdbEnabled := true
ThisBuild / semanticdbVersion := "4.10.1" // The version sbt-scalafix defaults to does not build for 2.13.16.
ThisBuild / scalacOptions ++= Seq("-Wunused:imports", "-Wunused:locals", "-Wunused:privates")
// ExplicitResultTypes reads the compiled classes, so it has to run on the same binary version as they do.
ThisBuild / scalafixScalaBinaryVersion := "2.13"

// Repos
resolvers += Resolver.mavenCentral

// FlowForge version (set during template generation)
lazy val flowforgeVersion = "$flowforgeVersion$"

// Core deps
lazy val ffDeps = Seq(
  "com.flowforge" %% "flowforge-core"          % flowforgeVersion,
  "com.flowforge" %% "flowforge-contracts"     % flowforgeVersion,
  "com.flowforge" %% "flowforge-engines-spark" % flowforgeVersion,
  "com.flowforge" %% "flowforge-quality-deequ" % flowforgeVersion,
  "com.flowforge" %% "flowforge-connectors"    % flowforgeVersion
)

// Spark runtime
lazy val sparkDeps = Seq(
  "org.apache.spark" %% "spark-sql" % "3.5.1"
)

// Logging & Metrics quickstart
lazy val loggingDeps = Seq(
  "com.typesafe.scala-logging" %% "scala-logging"           % "3.9.5",
  "ch.qos.logback"              % "logback-classic"         % "1.5.6",
  "io.prometheus"               % "simpleclient"            % "0.16.0",
  "io.prometheus"               % "simpleclient_hotspot"    % "0.16.0",
  "io.prometheus"               % "simpleclient_httpserver" % "0.16.0"
)

// JDBC (H2) for audit demo
lazy val jdbcDeps = Seq(
  "com.h2database" % "h2" % "2.2.224"
)

// ZIO (to demonstrate alternative effect system)
lazy val zioDeps = Seq(
  "dev.zio" %% "zio"              % "2.1.20",
  "dev.zio" %% "zio-interop-cats" % "23.1.0.2"
)

// Test
lazy val testDeps = Seq(
  "org.scalatest" %% "scalatest" % "3.2.19" % Test
)

lazy val root = (project in file("."))
  .settings(
    name := "$name$",
    // Several demo objects have a main method, so `sbt run` has to be told which one the quickstart means.
    Compile / run / mainClass := Some("com.flowforge.app.PipelineApp"),
    publish / skip            := true,
    Test / parallelExecution  := false,
    fork                      := true,
    javaOptions ++= Seq(
      "-Duser.timezone=UTC",
      "-Dnet.bytebuddy.experimental=true",
      "--add-exports=java.base/sun.nio.ch=ALL-UNNAMED"
    ),
    libraryDependencies ++= ffDeps ++ sparkDeps ++ loggingDeps ++ jdbcDeps ++ zioDeps ++ testDeps
  )
