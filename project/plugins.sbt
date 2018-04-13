addSbtPlugin("com.geirsson" % "sbt-scalafmt" % "1.4.0")
addSbtPlugin("org.foundweekends" % "sbt-bintray" % "0.5.2")
addSbtPlugin("com.typesafe.sbt" % "sbt-git" % "0.9.3")

// TO mute sbt-git
libraryDependencies += "org.slf4j" % "slf4j-nop" % "1.7.22"
