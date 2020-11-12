import sbt._

object AppDependencies {

  private val play25Version = "2.5.19"
  private val play26Version = "2.6.20"

  val compile: Seq[ModuleID] =
    PlayCrossCompilation.dependencies(
      shared = Seq(
        "org.pegdown" % "pegdown" % "1.6.0" % Test
      ),
      play25 = Seq(
        "uk.gov.hmrc" %% "time" % "3.2.0",
        "com.typesafe.play" %% "play" % play25Version % Provided,
        "com.typesafe.play" %% "play-test" % play25Version,
        "uk.gov.hmrc" %% "http-verbs" % "9.0.0-play-25", // Note: Only the HeaderCarrier is used from this library.
        "org.scalatest" %% "scalatest" % "2.2.6" % Test,
        "uk.gov.hmrc" %% "hmrctest" % "3.4.0-play-25" % Test
      ),
      play26 = Seq(
        "uk.gov.hmrc" %% "time" % "3.9.0",
        "com.typesafe.play" %% "play" % play26Version % Provided,
        "com.typesafe.play" %% "play-test" % play26Version,
        "uk.gov.hmrc" %% "http-verbs-play-26" % "12.0.0", // Note: Only the HeaderCarrier is used from this library.
        "org.scalatest" %% "scalatest" % "3.0.5" % Test,
        "uk.gov.hmrc" %% "hmrctest" % "3.9.0-play-26" % Test
      )
    )

  def apply(): Seq[ModuleID] = compile
}