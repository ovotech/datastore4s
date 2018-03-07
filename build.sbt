name := "datastore4s"
organization := "com.ovoenergy"

version := "0.1-SNAPSHOT"

scalaVersion := "2.12.4"

libraryDependencies ++= Seq(
  "com.google.cloud" % "google-cloud-datastore" % "1.14.0",
  "org.scala-lang" % "scala-reflect" % "2.12.4",
  "org.scalatest" %% "scalatest" % "3.0.4" % "test,it",
  "org.scalacheck" %% "scalacheck" % "1.13.4" % Test
)

configs(IntegrationTest)
Defaults.itSettings
testFrameworks in IntegrationTest := Seq(TestFrameworks.ScalaTest)

bintrayOrganization := Some("ovotech")
bintrayRepository := "maven-private"
bintrayPackage := "datastore4s"
bintrayPackageLabels := Seq("gcp", "datastore")

licenses := Seq(("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0")))
