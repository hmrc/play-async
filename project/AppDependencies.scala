import sbt._

object AppDependencies {

  private val play26Version = "2.6.20"

  val compile: Seq[ModuleID] =
    Seq(
        "uk.gov.hmrc" %% "time" % "3.9.0",
        "org.pegdown" % "pegdown" % "1.6.0" % Test,
        "com.typesafe.play" %% "play" % play26Version % Provided,
        "com.typesafe.play" %% "play-test" % play26Version,
        "uk.gov.hmrc" %% "http-verbs-play-26" % "11.6.0", // Note: Only the HeaderCarrier is used from this library.
        "org.scalatest" %% "scalatest" % "3.0.5" % Test,
        "uk.gov.hmrc" %% "hmrctest" % "3.9.0-play-26" % Test
      )

  def apply(): Seq[ModuleID] = compile
}