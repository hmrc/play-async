import sbt.Keys._
import sbt._

object HmrcBuild extends Build {

  import uk.gov.hmrc._
  import uk.gov.hmrc.versioning.SbtGitVersioning

  val appName = "play-async"

  lazy val library = (project in file("."))
    .enablePlugins(SbtAutoBuildPlugin, SbtGitVersioning)
    .settings(
      name := appName,
      scalaVersion := "2.11.7",
      crossScalaVersions := Seq("2.11.7"),
      libraryDependencies ++= AppDependencies(),
      resolvers := Seq(
        Resolver.bintrayRepo("hmrc", "releases"),
        "typesafe-releases" at "http://repo.typesafe.com/typesafe/releases/"
      )
    )
}

private object AppDependencies {

  val compile = Seq(
    "com.typesafe.play" %% "play" % "2.5.12" % "provided",
    "uk.gov.hmrc" %% "http-core" % "0.5.0" // Note: Only the HeaderCarrier is used from this library.
  )

  val testScope: String = "test"

  val test = Seq(
    "org.scalatest" %% "scalatest" % "2.2.6" % testScope,
    "uk.gov.hmrc" %% "hmrctest" % "2.3.0" % testScope
  )

  def apply(): Seq[ModuleID] = compile ++ test
}


