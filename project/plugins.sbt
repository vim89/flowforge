// ===== PLUGINS =====

// Code formatting
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.5.2")

// Linting & refactoring
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.11.1")

// Test coverage
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "2.3.0")

// Fat JAR assembly
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "2.0.0")
