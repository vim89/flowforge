// ===== PLUGINS =====

// Code formatting
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.5.2")

// Linting & refactoring
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.11.1")

// Test coverage
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "2.3.0")


// Fat JAR assembly
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "2.0.0")

// Unified Scaladoc (optional): generate a single aggregated API across modules
// Note: org changed to com.github.sbt in 0.5.0
addSbtPlugin("com.github.sbt" % "sbt-unidoc" % "0.5.0")
