import sbt._

object AppDependencies {

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