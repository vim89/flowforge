ThisBuild / organization := "$organization$"
ThisBuild / version := "$version$"
ThisBuild / scalaVersion := "$scalaVersion$"

lazy val root = (project in file("."))
  .settings(
    name := "$name;format="norm"$",
    Compile / run / fork := true,
    Compile / run / javaOptions ++= Seq(
      "-Xmx2g",
      "-XX:+UseG1GC"
    ),
    libraryDependencies ++= Seq(
      // Note: Using local FlowForge artifacts - run `sbt publishLocal` from FlowForge repo
      "com.flowforge" %% "flowforge-core" % "$flowforgeVersion$",
      
      // Effect system dependencies (Cats Effect default)
      "org.typelevel" %% "cats-effect" % "3.5.4",
      "org.typelevel" %% "cats-core" % "2.10.0",
      
      // Engine dependencies (Spark default)
      "com.flowforge" %% "flowforge-engines-spark" % "$flowforgeVersion$",
      "org.apache.spark" %% "spark-sql" % "3.5.0",
      "io.delta" %% "delta-spark" % "3.3.0",
      
      // Test dependencies
      "org.scalatest" %% "scalatest" % "3.2.17" % Test,
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