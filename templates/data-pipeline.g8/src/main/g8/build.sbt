ThisBuild / scalaVersion := "$scala_version$"

lazy val root = (project in file(".")).settings(
  name := "$name$",
  organization := "$organization$",
  Compile / run / fork := true,
  Compile / run / javaOptions ++= Seq(
    "-Dspark.ui.enabled=false",
    "-Dlog4j.configuration=log4j2.properties"
  ),
  libraryDependencies ++= Seq(
    "org.typelevel" %% "cats-effect" % "$cats_effect_version$",
    "org.apache.spark" %% "spark-sql" % "$spark_version$",
    "io.delta" %% "delta-spark" % "$delta_version$"
    // Add FlowForge modules when published to your repo:
    // "com.flowforge" %% "flowforge-core" % "$flowforge_version$",
    // "com.flowforge" %% "flowforge-framework" % "$flowforge_version$",
    // "com.flowforge" %% "flowforge-engines-spark" % "$flowforge_version$",
  )
)

Compile / mainClass := Some("example.Pipeline")
