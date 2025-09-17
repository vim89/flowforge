ThisBuild / organization := "$organization$"
ThisBuild / name         := "$name$"
ThisBuild / scalaVersion := "2.13.16"

// Repos
resolvers ++= Resolver.sonatypeOssRepos("public") ++ Seq(
  Resolver.mavenCentral,
)

// FlowForge version (set during template generation)
lazy val flowforgeVersion = "$flowforgeVersion$"

// Core deps
lazy val ffDeps = Seq(
  "com.flowforge" %% "flowforge-core"          % flowforgeVersion,
  "com.flowforge" %% "flowforge-contracts"     % flowforgeVersion,
  "com.flowforge" %% "flowforge-engines-spark" % flowforgeVersion,
  "com.flowforge" %% "flowforge-quality-deequ" % flowforgeVersion,
  "com.flowforge" %% "flowforge-connectors"    % flowforgeVersion,
)

// Spark runtime
lazy val sparkDeps = Seq(
  "org.apache.spark" %% "spark-sql" % "3.5.1"
)

// Logging & Metrics quickstart
lazy val loggingDeps = Seq(
  "com.typesafe.scala-logging" %% "scala-logging"    % "3.9.5",
  "ch.qos.logback"              %  "logback-classic"  % "1.5.6",
  "io.prometheus"               %  "simpleclient"     % "0.16.0",
  "io.prometheus"               %  "simpleclient_hotspot" % "0.16.0",
  "io.prometheus"               %  "simpleclient_httpserver" % "0.16.0",
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
    publish / skip := true,
    Test / parallelExecution := false,
    fork := true,
    javaOptions ++= Seq(
      "-Duser.timezone=UTC",
      "-Dnet.bytebuddy.experimental=true",
      "--add-exports=java.base/sun.nio.ch=ALL-UNNAMED",
    ),
    libraryDependencies ++= ffDeps ++ sparkDeps ++ loggingDeps ++ jdbcDeps ++ zioDeps ++ testDeps,
  )
