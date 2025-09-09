ThisBuild / organization := "$organization$"
ThisBuild / version := "$version$"
ThisBuild / scalaVersion := "$scalaVersion$"

lazy val root = (project in file("."))
  .settings(
    name := "$name;format="norm"$",
    Compile / run / fork := true,
    Compile / run / javaOptions ++= Seq(
      "-Xmx2g",
      "-XX:+UseG1GC",
      "--add-exports", "java.base/sun.nio.ch=ALL-UNNAMED"
    ),
    libraryDependencies ++= Seq(
      // FlowForge v1.0.0 core dependencies
      "com.flowforge" %% "flowforge-core" % "$flowforgeVersion$",
      "com.flowforge" %% "flowforge-lineage" % "$flowforgeVersion$",
      
      // Effect system dependencies (updated for v1.0.0)
      "org.typelevel" %% "cats-effect" % "3.6.1",
      "org.typelevel" %% "cats-core" % "2.10.0",
      
      // Spark 3.5.6 LTS (per v1.0.0 plan)
      "com.flowforge" %% "flowforge-engines-spark" % "$flowforgeVersion$",
      "org.apache.spark" %% "spark-sql" % "3.5.6",
      "io.delta" %% "delta-spark" % "3.3.2",
      
      // Optional: Quality checks with Deequ (if enabled)
      "com.flowforge" %% "flowforge-quality-deequ" % "$flowforgeVersion$" % "optional",
      
      // Test dependencies (updated)
      "org.scalatest" %% "scalatest" % "3.2.19" % Test,
      "org.scalatestplus" %% "scalacheck-1-17" % "3.2.17.0" % Test,
      "org.typelevel" %% "cats-effect-testing-scalatest" % "1.5.0" % Test
    )
  )

// Compiler options for better safety
scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-unchecked",
  "-Xlint",
  "-Ywarn-dead-code",
  "-Ywarn-numeric-widen",
  "-Ywarn-value-discard"
)