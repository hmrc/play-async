import sbt.Keys._
import sbt._

object HmrcBuild extends Build {

  import uk.gov.hmrc.DefaultBuildSettings._
  import uk.gov.hmrc.SbtAutoBuildPlugin
  import uk.gov.hmrc.versioning.SbtGitVersioning

  val nameApp = "play-async"

  lazy val library = Project(nameApp, file("."))
    .enablePlugins(play.PlayScala, SbtAutoBuildPlugin, SbtGitVersioning)
    .settings(scalaSettings: _*)
    .settings(defaultSettings(): _*)
    .settings(
      libraryDependencies ++= AppDependencies(),
      crossScalaVersions := Seq("2.11.7")
    )
    .settings(resolvers += Resolver.bintrayRepo("hmrc", "releases"))
}

private object AppDependencies {

  import play.PlayImport._
  import play.core.PlayVersion

  val compile = Seq(
    "com.typesafe.play" %% "play" % PlayVersion.current % "provided",
    ws % "provided",
    "uk.gov.hmrc" %% "play-json-logger" % "2.1.1",
    "uk.gov.hmrc" %% "http-verbs" % "3.3.0"
  )

  val testScope: String = "test"

  val test = Seq(
    "org.scalatest" %% "scalatest" % "2.2.1" % testScope,
    "org.pegdown" % "pegdown" % "1.4.2" % testScope,
    "uk.gov.hmrc" %% "hmrctest" % "1.7.0" % testScope,
    "uk.gov.hmrc" %% "frontend-bootstrap" % "6.5.0" % testScope,
    "uk.gov.hmrc" %% "play-authorised-frontend" % "5.1.0" % testScope,
    "uk.gov.hmrc" %% "play-config" % "2.0.1" % testScope,
    "uk.gov.hmrc" %% "http-caching-client" % "5.3.0" % testScope,
    "uk.gov.hmrc" %% "play-ui" % "4.14.0" % testScope
  )

  def apply() = compile ++ test
}


