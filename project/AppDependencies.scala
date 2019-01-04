import sbt._

object AppDependencies {

  val compile = Seq(
    "com.typesafe.play" %% "play" % "2.5.19" % Provided,
    "uk.gov.hmrc" %% "http-verbs" % "9.0.0-play-25", // Note: Only the HeaderCarrier is used from this library.
    "uk.gov.hmrc" %% "time" % "2.0.0" // Note: Only the HeaderCarrier is used from this library.
  )

  val testScope: String = "test"

  val test = Seq(
    "org.scalatest" %% "scalatest" % "2.2.6" % testScope,
    "uk.gov.hmrc" %% "hmrctest" % "3.3.0" % testScope
  )

  def apply(): Seq[ModuleID] = compile ++ test
}