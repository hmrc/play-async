import uk.gov.hmrc.SbtArtifactory
import uk.gov.hmrc.versioning.SbtGitVersioning

name := "play-async"

lazy val library = (project in file("."))
  .enablePlugins(SbtAutoBuildPlugin, SbtGitVersioning, SbtArtifactory)
  .settings(
    majorVersion := 2,
    scalaVersion := "2.11.11",
    makePublicallyAvailableOnBintray := true,
    crossScalaVersions := Seq("2.11.11"),
    libraryDependencies ++= AppDependencies(),
    resolvers := Seq(
      Resolver.bintrayRepo("hmrc", "releases"),
      "typesafe-releases" at "http://repo.typesafe.com/typesafe/releases/"
    )
  )