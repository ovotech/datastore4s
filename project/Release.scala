import sbt.Keys._
import sbt._

import scala.util.Try

object Release extends AutoPlugin {

  object Version {

    val VersionR = """([0-9]+).([0-9]+).([0-9]+)([\.\-0-9a-zA-Z]*)?""".r

    def apply(s: String): Version = {
      val VersionR(maj, minor, patch, qual) = s
      Version(maj.toInt, minor.toInt, patch.toInt, Option(qual).filterNot(_.isEmpty))
    }
  }

  case class Version(major: Int, minor: Int, patch: Int, qualifier: Option[String]) {

    def bump: Version = qualifier match {
      case Some(q) if q startsWith "-MAJOR" => copy(major = major + 1, minor = 0, patch = 0, qualifier = None)
      case Some(q) if q startsWith "-MINOR" => copy(minor = minor + 1, patch = 0, qualifier = None)
      case _                                => copy(patch = patch + 1, qualifier = None)
    }

    def withoutQualifier = copy(qualifier = None)

    def string = s"$major.$minor.$patch${qualifier.getOrElse("")}"
  }

  override def trigger: PluginTrigger = AllRequirements

  override def requires: Plugins = plugins.JvmPlugin

  lazy val releaseNextVersion: TaskKey[String] = TaskKey[String]("release-next-version")
  lazy val releaseWriteNextVersion: TaskKey[File] = TaskKey[File]("release-write-next-version")

  private def newVersion(version: Version) = version.bump.string

  override def projectSettings =
    Seq(releaseNextVersion := {
      println(s"Previous version: ${version.value}")
      val nextVersion = newVersion(Version(version.value))
      println(s"Next Version: $nextVersion")
      nextVersion
    }, releaseWriteNextVersion := {
      val file = new File(target.value, "next_release_version")
      IO.write(file, releaseNextVersion.value)
      file
    })
}
