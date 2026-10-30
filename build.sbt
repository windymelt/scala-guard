ThisBuild / scalaVersion := "3.9.0"
ThisBuild / organization := "dev.windymelt"

lazy val root = project
  .in(file("."))
  .settings(
    name := "scala-guard",
    scalacOptions ++= Seq("-deprecation", "-feature"),
    libraryDependencies += "org.scalameta" %% "munit" % "1.2.1" % Test
  )
