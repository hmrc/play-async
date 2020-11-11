resolvers ++= Seq(Resolver.url("hmrc-sbt-plugin-releases",
  url("https://dl.bintray.com/hmrc/sbt-plugin-releases"))(Resolver.ivyStylePatterns),
  "Typesafe Releases" at "https://repo.typesafe.com/typesafe/releases/",
  "HMRC Releases" at "https://dl.bintray.com/hmrc/releases")

addSbtPlugin("uk.gov.hmrc" % "sbt-auto-build" % "1.13.0")

addSbtPlugin("uk.gov.hmrc" % "sbt-git-versioning" % "1.15.0")

addSbtPlugin("uk.gov.hmrc" % "sbt-artifactory" % "0.14.0")

addSbtPlugin("com.timushev.sbt" % "sbt-updates" % "0.3.4")