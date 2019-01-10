import sbt._

object AppDependencies {

  private val play25Version = "2.5.19"
  private val play26Version = "2.6.20"

  val compile: Seq[ModuleID] =
    PlayCrossCompilation.dependencies(
      shared = Seq(
        "uk.gov.hmrc" %% "time" % "3.2.0",
        "org.pegdown" % "pegdown" % "1.6.0" % Test
      ),
      play25 = Seq(
        "com.typesafe.play" %% "play" % play25Version % Provided,
        "com.typesafe.play" %% "play-test" % play25Version,
        "uk.gov.hmrc" %% "http-verbs" % "9.0.0-play-25", // Note: Only the HeaderCarrier is used from this library.
        "org.scalatest" %% "scalatest" % "2.2.6" % Test
      ),
      play26 = Seq(
        "com.typesafe.play" %% "play" % play26Version % Provided,
        "com.typesafe.play" %% "play-test" % play26Version,
        "uk.gov.hmrc" %% "http-verbs" % "9.0.0-play-26", // Note: Only the HeaderCarrier is used from this library.
        "org.scalatest" %% "scalatest" % "3.0.5" % Test
      )
    )

  def apply(): Seq[ModuleID] = compile
}