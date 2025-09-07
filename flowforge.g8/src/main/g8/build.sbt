ThisBuild / organization := "$organization$"
ThisBuild / version := "$version$"
ThisBuild / scalaVersion := "$scalaVersion$"

lazy val root = (project in file("."))
  .settings(
    name := "$name;format="norm"$",
    libraryDependencies ++= Seq(
      "com.flowforge" %% "flowforge-core" % "$flowforgeVersion$",
      "org.typelevel" %% "cats-effect" % "3.5.1",
      "org.scalatest" %% "scalatest" % "3.2.15" % Test
    )
  )