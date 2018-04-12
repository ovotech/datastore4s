name := "datastore4s"
organization := "com.ovoenergy"

version := "0.1-SNAPSHOT"

scalaVersion := "2.12.4"

libraryDependencies ++= Seq(
  "org.scala-lang" % "scala-reflect" % "2.12.4",
  "com.google.cloud" % "google-cloud-datastore" % "1.14.0",
  // Test Dependencies
  "org.scalatest" %% "scalatest" % "3.0.4" % "test,it",
  "org.mockito" % "mockito-core" % "2.18.0" % Test,
  "org.scalacheck" %% "scalacheck" % "1.13.4" % Test
)

configs(IntegrationTest)
Defaults.itSettings
testFrameworks in IntegrationTest := Seq(TestFrameworks.ScalaTest)

val datastoreVariables = Map("DATASTORE_PROJECT_ID" -> "datastore4s", "DATASTORE_NAMESPACE" -> "datastore4s-namespace")

envVars in Test := datastoreVariables
fork in Test := true
envVars in IntegrationTest := datastoreVariables
fork in IntegrationTest := true

bintrayOrganization := Some("ovotech")
bintrayRepository := "maven-private"
bintrayPackage := "datastore4s"
bintrayPackageLabels := Seq("gcp", "datastore")

licenses := Seq(("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0")))
