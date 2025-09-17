ThisBuild / organization := "$organization$"
ThisBuild / name         := "$name$"
ThisBuild / scalaVersion := "2.13.16"

// ——— Repos
resolvers ++= Resolver.sonatypeOssRepos("public") ++ Seq(
  Resolver.mavenCentral,
)

// ——— Dependencies
lazy val flowforgeVersion = "$flowforgeVersion$" // set to published version or SNAPSHOT

lazy val ffDeps = Seq(
  // Core FlowForge modules (uncomment once artifacts are available)
  // "com.flowforge" %% "flowforge-core"           % flowforgeVersion,
  // "com.flowforge" %% "flowforge-contracts"      % flowforgeVersion,
  // "com.flowforge" %% "flowforge-engines-spark"  % flowforgeVersion,
  // "com.flowforge" %% "flowforge-connectors"     % flowforgeVersion,
)

// Logging & Metrics quickstart
lazy val loggingDeps = Seq(
  "com.typesafe.scala-logging" %% "scala-logging"   % "3.9.5",
  "ch.qos.logback"              %  "logback-classic" % "1.5.6"
)

lazy val metricsDeps = Seq(
  "io.prometheus" % "simpleclient"          % "0.16.0",
  "io.prometheus" % "simpleclient_hotspot"  % "0.16.0",
  "io.prometheus" % "simpleclient_httpserver" % "0.16.0",
)

lazy val root = (project in file("."))
  .settings(
    name := "$name$",
    publish / skip := true,
    libraryDependencies ++= ffDeps ++ loggingDeps ++ metricsDeps,
  )
