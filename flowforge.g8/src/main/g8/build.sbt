ThisBuild / organization := "$organization$"
ThisBuild / version := "$version$"
ThisBuild / scalaVersion := "$scalaVersion$"

lazy val root = (project in file("."))
  .settings(
    name := "$name$",
    Compile / run / fork := true,
    Compile / run / javaOptions ++= Seq(
      "-Xmx2g",
      "-XX:+UseG1GC",
      "--add-exports", "java.base/sun.nio.ch=ALL-UNNAMED"
    ),
    libraryDependencies ++= Seq(
      // FlowForge v1.0.0 core dependencies
      "com.flowforge" %% "flowforge-core" % "$flowforgeVersion$",
\$if(include_lineage.truthy)\$
      "com.flowforge" %% "flowforge-lineage" % "$flowforgeVersion$",
\$endif\$

      // Effect system dependencies (updated for v1.0.0)
\$if(effect_system == "cats-effect")\$
      "org.typelevel" %% "cats-effect" % "3.6.1",
      "org.typelevel" %% "cats-core" % "2.10.0",
\$endif\$

\$if(effect_system == "zio")\$
      "dev.zio" %% "zio" % "2.1.20",
      "dev.zio" %% "zio-interop-cats" % "23.1.0.2",
\$endif\$

      // Execution engine dependencies
\$if(execution_engine == "spark" || execution_engine == "both")\$
      "com.flowforge" %% "flowforge-engines-spark" % "$flowforgeVersion$",
      "org.apache.spark" %% "spark-sql" % "3.5.6",
      "io.delta" %% "delta-spark" % "3.3.2",
\$endif\$

\$if(execution_engine == "flink" || execution_engine == "both")\$
      "com.flowforge" %% "flowforge-engines-flink" % "$flowforgeVersion$",
      "org.apache.flink" % "flink-scala_2.12" % "1.18.0" % Provided,
      "org.apache.flink" % "flink-streaming-scala_2.12" % "1.18.0" % Provided,
      "org.apache.flink" % "flink-table-runtime" % "1.18.0" % Provided,
\$endif\$

      // Optional: Quality checks with Deequ (if enabled)
\$if(include_dq.truthy)\$
      "com.flowforge" %% "flowforge-quality-deequ" % "$flowforgeVersion$" % "optional",
\$endif\$

      // Test dependencies (updated)
      "org.scalatest" %% "scalatest" % "3.2.19" % Test,
      "org.scalatestplus" %% "scalacheck-1-17" % "3.2.17.0" % Test,
\$if(effect_system == "cats-effect")\$
      "org.typelevel" %% "cats-effect-testing-scalatest" % "1.5.0" % Test
\$endif\$

\$if(effect_system == "zio")\$
      "dev.zio" %% "zio-test" % "2.1.20" % Test,
      "dev.zio" %% "zio-test-sbt" % "2.1.20" % Test
\$endif\$
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
