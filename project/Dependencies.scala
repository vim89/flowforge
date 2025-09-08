import sbt.*
import sbt.Keys.scalaVersion

object Dependencies {

  // ===== VERSION CATALOG =====
  object Versions {
    // Scala ecosystem
    val scala212 = "2.12.20"
    val scala213 = "2.13.16"
    val scala3   = "3.3.3"

    // Core functional libraries
    val cats           = "2.10.0"
    val catsEffect     = "3.6.1"    // Performance upgrade: was 3.5.4
    val fs2            = "3.9.4"
    val zio            = "2.1.20"   // Major improvements: was 2.0.19 (binary compatible)
    val zioInteropCats = "23.1.0.2" // Updated: was 23.1.0.0
    val refined        = "0.11.3"
    val kittens        = "3.1.0"

    // JSON & Config
    val circe      = "0.14.14" // Security update: was 0.14.6
    val pureconfig = "0.17.9"

    // Big Data engines
    val spark    = "3.5.6" // Updated to latest 3.5 LTS per v100-plan
    val delta    = "3.3.2"
    val flink    = "1.18.0"
    val kafka    = "3.6.1"
    val fs2Kafka = "3.9.0"

    // Cloud providers
    val gcpStorage = "2.37.0"
    val bigquery   = "2.54.1"
    val aws        = "2.21.29"
    val azure      = "12.9.0"

    // Data quality
    val deequ             = "2.0.12-spark-3.5" // Per v1.0-2 plan: use latest Deequ
    val greatExpectations = "0.16.0"

    // Monitoring & Observability
    val prometheus = "0.16.0"
    val otel       = "1.53.0" // Latest stable: was 1.32.0
    val micrometer = "1.12.1"

    // Logging
    val scalaLogging = "3.9.5"
    val logback      = "1.5.18" // Already latest available
    val log4cats     = "2.7.1"

    // Experimental libraries
    val kyo = "0.8.5"

    // Testing
    val scalaTest      = "3.2.19" // Updated: was 3.2.17
    val scalaCheck     = "1.18.1" // Updated: was 1.17.0
    val testContainers = "0.40.17"
    val wiremock       = "3.0.1"
    val mockito        = "5.13.0"

    // Build tools & plugins
    val scalafix = "0.11.1"
    val scalafmt = "3.7.17"
    val mdoc     = "2.5.1"
    val unidoc   = "0.5.0"
    val assembly = "2.1.4"
    // Choose a widely available artifact for generic frameless-dataset
    val frameless = "0.15.0"
  }

  val validation = Seq(
    "io.github.jmcardon" %% "tsec-common" % "0.4.0",
    "org.scalactic"      %% "scalactic"   % Versions.scalaTest,
  )

  // ===== CORE DEPENDENCIES =====
  object Core {
    val functional = Seq(
      "org.typelevel" %% "cats-core"   % Versions.cats,
      "org.typelevel" %% "cats-effect" % Versions.catsEffect,
      "org.typelevel" %% "cats-free"   % Versions.cats,
      "co.fs2"        %% "fs2-core"    % Versions.fs2,
      "co.fs2"        %% "fs2-io"      % Versions.fs2,
      "org.typelevel" %% "kittens"     % Versions.kittens,
    )

    def typeSafety(scalaVersion: String): Seq[ModuleID] = {
      val common = Seq(
        "eu.timepit" %% "refined" % Versions.refined,
      )

      val versionSpecific = CrossVersion.partialVersion(scalaVersion) match {
        case Some((2, _)) =>
          Seq(
            "com.softwaremill.magnolia1_2" %% "magnolia"      % "1.1.10",
            "org.scala-lang"                % "scala-reflect" % scalaVersion,
          )
        case Some((3, _)) =>
          Seq(
            // Scala 3 uses built-in Mirrors, no external dependencies needed
          )
        case _ => Seq.empty
      }

      common ++ versionSpecific
    }

    val json = Seq(
      "io.circe" %% "circe-core"    % Versions.circe,
      "io.circe" %% "circe-generic" % Versions.circe,
      "io.circe" %% "circe-parser"  % Versions.circe,
    )

    val config = Seq.empty[ModuleID]

    val logging = Seq(
      "com.typesafe.scala-logging" %% "scala-logging"   % Versions.scalaLogging,
      "ch.qos.logback"              % "logback-classic" % Versions.logback % Runtime,
      "org.typelevel"              %% "log4cats-slf4j"  % Versions.log4cats,
    )

    val all: Seq[ModuleID] = functional ++ Seq(
      "eu.timepit" %% "refined" % Versions.refined,
      // Note: Magnolia now conditionally added in build.sbt for Scala 2 only
    ) ++ json ++ logging ++ validation
  }

  object TypedSpark {
    val frameless = Seq(
      "org.typelevel" %% "frameless-dataset" % Versions.frameless,
    )
  }

  // Effect systems (both available for developer choice)
  val effectSystems = Seq(
    "org.typelevel" %% "cats-effect"      % Versions.catsEffect,
    "dev.zio"       %% "zio"              % Versions.zio,
    "dev.zio"       %% "zio-interop-cats" % Versions.zioInteropCats,
  )

  // Engines
  object Engines {
    val spark = Seq(
      "org.apache.spark" %% "spark-core"     % Versions.spark % "provided",
      "org.apache.spark" %% "spark-sql"      % Versions.spark % "provided",
      "org.apache.spark" %% "spark-catalyst" % Versions.spark % "provided",
      "io.delta"         %% "delta-spark"    % Versions.delta,
    )

    val sparkCompile = Seq(
      "org.apache.spark" %% "spark-core"     % Versions.spark,
      "org.apache.spark" %% "spark-sql"      % Versions.spark,
      "org.apache.spark" %% "spark-catalyst" % Versions.spark,
      "io.delta"         %% "delta-spark"    % Versions.delta,
    )

    val flink = Seq(
      // NOTE: Flink Scala API only available for 2.12, documented constraint
      "org.apache.flink" % "flink-scala_2.12"           % Versions.flink % "provided",
      "org.apache.flink" % "flink-streaming-scala_2.12" % Versions.flink % "provided",
      "org.apache.flink" % "flink-table-runtime"        % Versions.flink % "provided",
    )

    val all: Seq[ModuleID] = spark ++ flink
  }

  // ===== CONNECTOR DEPENDENCIES =====
  object Connectors {
    val hadoop = Seq(
      "org.apache.hadoop" % "hadoop-client-api"     % "3.3.6",
      "org.apache.hadoop" % "hadoop-client-runtime" % "3.3.6",
      "org.apache.hadoop" % "hadoop-hdfs-client"    % "3.3.6"
        exclude ("org.slf4j", "slf4j-log4j12")
        exclude ("log4j", "log4j"),
    )

    val gcs = Seq(
      "com.google.cloud" % "google-cloud-storage"       % Versions.gcpStorage,
      "com.google.cloud" % "google-cloud-secretmanager" % Versions.gcpStorage,
    )

    val all: Seq[ModuleID] = hadoop ++ gcs
  }

  // ===== QUALITY DEPENDENCIES =====
  object Quality {
    // Migrated away from Deequ (Scala 2.12-only). Provide only validation libs here.
    val all: Seq[ModuleID] = validation
  }

  // ===== MONITORING DEPENDENCIES =====
  object Monitoring {
    val prometheus = Seq(
      "io.prometheus" % "simpleclient"         % Versions.prometheus,
      "io.prometheus" % "simpleclient_hotspot" % Versions.prometheus,
    )

    val openTelemetry = Seq(
      "io.opentelemetry" % "opentelemetry-api" % Versions.otel,
      "io.opentelemetry" % "opentelemetry-sdk" % Versions.otel,
    )

    val micrometer = Seq(
      "io.micrometer" % "micrometer-core" % Versions.micrometer,
    )

    val all: Seq[ModuleID] = prometheus ++ openTelemetry ++ micrometer
  }

  // ===== TESTING DEPENDENCIES =====
  object Testing {
    val unit = Seq(
      "org.scalatest"     %% "scalatest"                     % Versions.scalaTest  % Test,
      "org.scalatestplus" %% "scalacheck-1-17"               % "3.2.17.0"          % Test,
      "org.scalacheck"    %% "scalacheck"                    % Versions.scalaCheck % Test,
      "org.typelevel"     %% "cats-effect-testing-scalatest" % "1.5.0"             % Test,
      "dev.zio"           %% "zio-test"                      % Versions.zio        % Test,
      "dev.zio"           %% "zio-test-sbt"                  % Versions.zio        % Test,
      "org.mockito"        % "mockito-core"                  % Versions.mockito    % Test,
    )

    val integration = Seq(
      "com.dimafeng"          %% "testcontainers-scala-scalatest"  % Versions.testContainers % Test,
      "com.dimafeng"          %% "testcontainers-scala-postgresql" % Versions.testContainers % Test,
      "com.dimafeng"          %% "testcontainers-scala-kafka"      % Versions.testContainers % Test,
      "org.testcontainers"     % "gcloud"                          % "1.19.3"                % Test,
      "com.github.tomakehurst" % "wiremock-jre8"                   % Versions.wiremock       % Test,
    )

    val all: Seq[ModuleID] = unit ++ integration
  }

  // Common dependencies with all essentials
  val common: Seq[ModuleID] = Core.all ++ effectSystems ++ Testing.unit

  def withProvided(deps: Seq[ModuleID], providedLibs: Seq[ModuleID]): Seq[ModuleID] =
    deps ++ providedLibs.map(_ % "provided")

  def forModule(moduleName: String): Seq[ModuleID] = moduleName match {
    case "core" => Core.all ++ Monitoring.prometheus ++ Testing.unit
    // Infrastructure Layer modules
    case "infrastructure" =>
      Core.all ++ effectSystems ++ Testing.integration ++ Monitoring.all ++ Seq(
        "com.typesafe" % "config" % "1.4.3",
      )
    case "contracts" =>
      Core.all ++ Testing.unit ++ Seq(
        "org.apache.spark" %% "spark-sql" % Versions.spark % "provided",
      )
    case "connectors"     => Core.functional ++ Testing.unit ++ Connectors.all
    case "connectors-s3"  => common ++ Connectors.hadoop
    case "connectors-gcs" => common ++ Connectors.gcs
    case "engines-spark"  => common ++ Engines.spark ++ TypedSpark.frameless
    case "engines-flink"  => common ++ Engines.flink
    case "quality"        => Core.functional ++ Testing.unit
    case "quality-deequ" =>
      common ++ Quality.all ++ Seq(
        // Native Spark checks by default (no extra dependencies)
        // Optional Deequ integration when available on classpath
        "org.apache.spark" %% "spark-sql" % Versions.spark,
      )
    case "lineage" => common
    case "examples" =>
      common ++ Engines.sparkCompile ++ Seq(
        "io.delta" %% "delta-spark" % Versions.delta,
      )
    case "examples-spark" =>
      common ++ Engines.sparkCompile ++ Seq(
        "io.delta" %% "delta-spark" % Versions.delta,
      )
    case _ => common
  }

  // ===== DEPENDENCY OVERRIDES =====
  val overrides: Seq[ModuleID] = Seq(
    "org.typelevel"          %% "cats-core"   % Versions.cats,
    "org.typelevel"          %% "cats-kernel" % Versions.cats,
    "org.scala-lang.modules" %% "scala-xml"   % "2.2.0",
    // Ensure Byte Buddy supports newer JDKs (Mockito/others)
    "net.bytebuddy" % "byte-buddy"       % "1.15.11",
    "net.bytebuddy" % "byte-buddy-agent" % "1.15.11",
    // Align Netty across AWS/GCP/Hadoop stacks
    "io.netty" % "netty-common"      % "4.1.110.Final",
    "io.netty" % "netty-buffer"      % "4.1.110.Final",
    "io.netty" % "netty-transport"   % "4.1.110.Final",
    "io.netty" % "netty-resolver"    % "4.1.110.Final",
    "io.netty" % "netty-handler"     % "4.1.110.Final",
    "io.netty" % "netty-codec"       % "4.1.110.Final",
    "io.netty" % "netty-codec-http"  % "4.1.110.Final",
    "io.netty" % "netty-codec-http2" % "4.1.110.Final",
    // Align Google stack
    "com.google.guava"  % "guava"              % "33.4.0-jre",
    "com.google.j2objc" % "j2objc-annotations" % "3.0.0",
    // Align SLF4J
    "org.slf4j" % "slf4j-api" % "2.0.16", // Security update: was 2.0.13
  )
}
