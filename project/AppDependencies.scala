import sbt.Keys._
import sbt._

object AppDependencies {
  import play.core.PlayVersion

  val compile = Seq(
    "com.typesafe.play" %% "play" % PlayVersion.current % "provided",
    "uk.gov.hmrc" %% "http-verbs" % "6.2.0" // Note: Only the HeaderCarrier is used from this library.
  )

  val testScope: String = "test"

  val test = Seq(
    "org.scalatest" %% "scalatest" % "2.2.6" % testScope,
    "uk.gov.hmrc" %% "hmrctest" % "2.0.0" % testScope
  )

  def apply(): Seq[ModuleID] = compile ++ test
}