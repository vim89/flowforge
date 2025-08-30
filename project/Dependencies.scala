import sbt._

object Dependencies {

  // ===== VERSION CATALOG =====
  object Versions {
    // Scala ecosystem
    val scala212 = "2.12.20"
    val scala213 = "2.13.16"
    val scala3   = "3.3.3"

    // Core functional libraries
    val cats           = "2.10.0"
    val catsEffect     = "3.5.4"
    val fs2            = "3.9.4"
    val zio            = "2.0.19"
    val zioInteropCats = "23.1.0.0"
    val refined        = "0.11.3"
    val kittens        = "3.1.0"
    val shapeless      = "2.3.13"

    // JSON & Config
    val circe      = "0.14.6"
    val pureconfig = "0.17.9"

    // Big Data engines
    val spark    = "3.5.0"
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
    val deequ             = "2.0.11-spark-3.5"
    val greatExpectations = "0.16.0"

    // Monitoring & Observability
    val prometheus = "0.16.0"
    val otel       = "1.32.0"
    val micrometer = "1.12.1"

    // Logging
    val scalaLogging = "3.9.5"
    val logback      = "1.5.18"
    val log4cats     = "2.7.1"

    // Experimental libraries
    val kyo = "0.8.5"

    // Testing
    val scalaTest      = "3.2.17"
    val scalaCheck     = "1.17.0"
    val testContainers = "0.40.17"
    val wiremock       = "3.0.1"
    val mockito        = "5.8.0"

    // Build tools & plugins
    val sbtGiter8 = "0.16.2"
    val scalafix  = "0.11.1"
    val scalafmt  = "3.7.17"
    val mdoc      = "2.5.1"
    val unidoc    = "0.5.0"
    val assembly  = "2.1.4"
  }

  val validation = Seq(
    "io.github.jmcardon" %% "tsec-common" % "0.4.0",
    "org.scalactic"      %% "scalactic"   % Versions.scalaTest
  )

  // ===== CORE DEPENDENCIES =====
  object Core {
    val functional = Seq(
      "org.typelevel" %% "cats-core"   % Versions.cats,
      "org.typelevel" %% "cats-effect" % Versions.catsEffect,
      "org.typelevel" %% "cats-free"   % Versions.cats,
      "co.fs2"        %% "fs2-core"    % Versions.fs2,
      "co.fs2"        %% "fs2-io"      % Versions.fs2,
      "org.typelevel" %% "kittens"     % Versions.kittens
    )

    val typeSafety = Seq(
      "eu.timepit" %% "refined" % Versions.refined
    )

    val json = Seq(
      "io.circe" %% "circe-core"    % Versions.circe,
      "io.circe" %% "circe-generic" % Versions.circe,
      "io.circe" %% "circe-parser"  % Versions.circe
    )

    val config = Seq.empty[ModuleID]

    val logging = Seq(
      "com.typesafe.scala-logging" %% "scala-logging"   % Versions.scalaLogging,
      "ch.qos.logback"              % "logback-classic" % Versions.logback % Runtime,
      "org.typelevel"              %% "log4cats-slf4j"  % Versions.log4cats
    )

    val all: Seq[ModuleID] = functional ++ typeSafety ++ json ++ logging ++ validation
  }

  // Effect systems (provided dependencies)
  val effectSystems = Seq(
    "org.typelevel" %% "cats-effect"      % Versions.catsEffect     % "provided",
    "dev.zio"       %% "zio"              % Versions.zio            % "provided",
    "dev.zio"       %% "zio-interop-cats" % Versions.zioInteropCats % "provided"
  )

  // Engines
  object Engines {
    val spark = Seq(
      "org.apache.spark" %% "spark-core"     % Versions.spark % "provided",
      "org.apache.spark" %% "spark-sql"      % Versions.spark % "provided",
      "org.apache.spark" %% "spark-catalyst" % Versions.spark % "provided",
      "io.delta"         %% "delta-spark"    % Versions.delta
    )

    val flink = Seq(
      "org.apache.flink" % "flink-scala_2.12"           % Versions.flink % "provided",
      "org.apache.flink" % "flink-streaming-scala_2.12" % Versions.flink % "provided",
      "org.apache.flink" % "flink-table-runtime"        % Versions.flink % "provided"
    )

    val all: Seq[ModuleID] = spark ++ flink
  }

  // ===== CONNECTOR DEPENDENCIES =====
  object Connectors {
    val gcs = Seq(
      "com.google.cloud" % "google-cloud-storage"       % Versions.gcpStorage,
      "com.google.cloud" % "google-cloud-secretmanager" % Versions.gcpStorage
    )

    val s3 = Seq(
      "software.amazon.awssdk" % "s3"             % Versions.aws,
      "software.amazon.awssdk" % "secretsmanager" % Versions.aws
    )

    val bigquery = Seq(
      "com.google.cloud" % "google-cloud-bigquery" % Versions.bigquery
    )

    val kafka = Seq(
      "org.apache.kafka" % "kafka-clients" % Versions.kafka,
      "com.github.fd4s" %% "fs2-kafka"     % Versions.fs2Kafka
        exclude ("org.apache.kafka", "kafka-clients")
        exclude ("org.typelevel", "cats-effect")
        exclude ("org.scala-lang", "scala3-library")
    )

    val azure = Seq(
      "com.azure" % "azure-storage-blob" % Versions.azure,
      "com.azure" % "azure-identity"     % "1.10.4"
    )

    val all: Seq[ModuleID] = gcs ++ s3 ++ bigquery ++ kafka ++ azure
  }

  // ===== QUALITY DEPENDENCIES =====
  object Quality {
    val deequ = Seq(
      "com.amazon.deequ" % "deequ" % Versions.deequ
        exclude ("org.typelevel", "cats-core_2.12")
        exclude ("org.typelevel", "cats-kernel_2.12")
        exclude ("org.scala-lang.modules", "scala-xml_2.12")
    )

    val all: Seq[ModuleID] = deequ ++ validation
  }

  // ===== MONITORING DEPENDENCIES =====
  object Monitoring {
    val prometheus = Seq(
      "io.prometheus" % "simpleclient"         % Versions.prometheus,
      "io.prometheus" % "simpleclient_hotspot" % Versions.prometheus
    )

    val openTelemetry = Seq(
      "io.opentelemetry" % "opentelemetry-api" % Versions.otel,
      "io.opentelemetry" % "opentelemetry-sdk" % Versions.otel
    )

    val micrometer = Seq(
      "io.micrometer" % "micrometer-core" % Versions.micrometer
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
      "org.mockito"        % "mockito-core"                  % Versions.mockito    % Test
    )

    val integration = Seq(
      "com.dimafeng"          %% "testcontainers-scala-scalatest"  % Versions.testContainers % Test,
      "com.dimafeng"          %% "testcontainers-scala-postgresql" % Versions.testContainers % Test,
      "com.dimafeng"          %% "testcontainers-scala-kafka"      % Versions.testContainers % Test,
      "org.testcontainers"     % "gcloud"                          % "1.19.3"                % Test,
      "com.github.tomakehurst" % "wiremock-jre8"                   % Versions.wiremock       % Test
    )

    val all: Seq[ModuleID] = unit ++ integration
  }

  // Common dependencies with all essentials
  val common: Seq[ModuleID] = Core.all ++ effectSystems ++ Testing.unit

  def withProvided(deps: Seq[ModuleID], providedLibs: Seq[ModuleID]): Seq[ModuleID] =
    deps ++ providedLibs.map(_ % "provided")

  def forModule(moduleName: String): Seq[ModuleID] = moduleName match {
    case "core"   => Core.all ++ Testing.unit
    case "safety" => Core.functional ++ effectSystems ++ Testing.unit
    case "contracts" =>
      Core.all ++ Testing.unit ++ Seq(
        "org.apache.spark" %% "spark-sql" % Versions.spark % "provided"
      )
    case "connectors"          => Core.functional ++ Testing.unit
    case "connectors-gcs"      => common ++ Connectors.gcs
    case "connectors-s3"       => common ++ Connectors.s3
    case "connectors-bigquery" => common ++ Connectors.bigquery
    case "connectors-kafka"    => common ++ Connectors.kafka
    case "connectors-azure"    => common ++ Connectors.azure
    case "engines"             => Core.functional ++ Testing.unit
    case "engines-spark"       => common ++ Engines.spark
    case "engines-flink"       => common ++ Engines.flink
    case "quality"             => Core.functional ++ Testing.unit
    case "quality-deequ"       => common ++ Quality.deequ
    case "templates" =>
      common ++ Seq(
        "org.foundweekends.giter8" %% "giter8-lib" % Versions.sbtGiter8
      )
    case "monitoring" => common ++ Monitoring.all
    case "testing"    => common ++ Testing.integration
    case "examples"   => common
    case "experimental" =>
      common ++ Seq(
        "io.getkyo" %% "kyo-core" % Versions.kyo
      )
    case _ => common
  }

  // ===== DEPENDENCY OVERRIDES =====
  val overrides: Seq[ModuleID] = Seq(
    "org.typelevel"          %% "cats-core"   % Versions.cats,
    "org.typelevel"          %% "cats-kernel" % Versions.cats,
    "org.scala-lang.modules" %% "scala-xml"   % "2.2.0"
  )
}
