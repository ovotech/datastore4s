import sbt.Keys._
import sbt._

import scala.util.Try

object Release extends AutoPlugin {

  object Version {

    val VersionR = """([0-9]+)((?:\.[0-9]+)+)?([\.\-0-9a-zA-Z]*)?""".r
    val PreReleaseQualifierR = """[\.-](?i:rc|m|alpha|beta)[\.-]?[0-9]*""".r

    def apply(s: String): Try[Version] =
      Try {
        val VersionR(maj, subs, qual) = s
        // parse the subversions (if any) to a Seq[Int]
        val subSeq: Seq[Int] = Option(subs) map { str =>
          // split on . and remove empty strings
          str.split('.').filterNot(_.trim.isEmpty).map(_.toInt).toSeq
        } getOrElse Nil
        Version(maj.toInt, subSeq, Option(qual).filterNot(_.isEmpty))
      }
  }

  case class Version(major: Int, subversions: Seq[Int], qualifier: Option[String]) {
    val SnapshotQualifier = "-SNAPSHOT"
    def bumpOrSnapshot = {
      val maybeBumpedPrerelease = qualifier.collect {
        case Version.PreReleaseQualifierR() => withoutQualifier
      }

      maybeBumpedPrerelease
        .orElse(maybeBumpedLastSubversion)
        .getOrElse(bumpMajor)
    }

    private def bumpMajor = copy(major = major + 1, subversions = Seq.fill(subversions.length)(0))

    private def maybeBumpedLastSubversion = bumpSubversionOpt(subversions.length - 1)

    private def bumpSubversionOpt(idx: Int) = {
      val bumped = subversions.drop(idx)
      val reset = bumped.drop(1).length
      bumped.headOption map { head =>
        val patch = (head + 1) +: Seq.fill(reset)(0)
        copy(subversions = subversions.patch(idx, patch, patch.length))
      }
    }

    def withoutQualifier = copy(qualifier = None)

    def asSnapshot = copy(qualifier = Some(SnapshotQualifier))

    def string = "" + major + mkString(subversions) + qualifier.getOrElse("")

    private def mkString(parts: Seq[Int]) = parts.map("." + _).mkString
  }

  override def trigger: PluginTrigger = AllRequirements
  override def requires: Plugins = plugins.JvmPlugin

  lazy val releaseNextVersion: TaskKey[String] = TaskKey[String]("release-next-version")
  lazy val releaseWriteNextVersion: TaskKey[File] = TaskKey[File]("release-write-next-version")

  override def projectSettings =
    Seq(releaseNextVersion := {
      Version(version.value).map(_.bumpOrSnapshot.string).get
    }, releaseWriteNextVersion := {
      val file = new File(target.value, "next_release_version")
      IO.write(file, releaseNextVersion.value)
      file
    })
}
