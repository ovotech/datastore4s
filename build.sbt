name := "datastore4s"
organization := "org.datastore4s"

version := "0.1"

scalaVersion := "2.12.4"

libraryDependencies ++= Seq(
  "com.google.cloud" % "google-cloud-datastore" % "1.14.0",
  "org.scala-lang" % "scala-reflect" % "2.12.4",
  "org.scalatest" %% "scalatest" % "3.0.4" % Test,
  "org.scalacheck" %% "scalacheck" % "1.13.4" % Test
)
