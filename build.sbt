import com.typesafe.sbt.git.ConsoleGitRunner

name := "datastore4s"
organization := "com.ovoenergy"

scalaVersion := "2.12.4"

libraryDependencies ++= Seq(
  "org.scala-lang" % "scala-reflect" % "2.12.4",
  "com.google.cloud" % "google-cloud-datastore" % "1.25.0",
  // Test Dependencies
  "org.scalatest" %% "scalatest" % "3.0.4" % "test,it",
  "org.mockito" % "mockito-core" % "2.18.0" % Test,
  "org.scalacheck" %% "scalacheck" % "1.13.4" % Test
)

configs(IntegrationTest)
Defaults.itSettings
testFrameworks in IntegrationTest := Seq(TestFrameworks.ScalaTest)

val datastoreVariables = Map("DATASTORE_PROJECT_ID" -> "datastore4s", "DATASTORE_NAMESPACE" -> "datastore4s-namespace")

envVars in Test := datastoreVariables + ("DATASTORE_EMULATOR_HOST" -> "https://localhost")
fork in Test := true
envVars in IntegrationTest := datastoreVariables
fork in IntegrationTest := true

bintrayOrganization := Some("ovotech")
bintrayRepository := "maven-private"
bintrayPackage := "datastore4s"
bintrayPackageLabels := Seq("gcp", "datastore")

licenses := Seq(("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0")))

// sbt-git plugin for versioning
enablePlugins(GitVersioning)
git.remoteRepo := "origin"
git.runner := ConsoleGitRunner
git.baseVersion := "0.0"
git.useGitDescribe := true
