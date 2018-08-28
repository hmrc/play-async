resolvers ++= Seq(Resolver.url("hmrc-sbt-plugin-releases",
  url("https://dl.bintray.com/hmrc/sbt-plugin-releases"))(Resolver.ivyStylePatterns),
  "Typesafe Releases" at "http://repo.typesafe.com/typesafe/releases/",
  "HMRC Releases" at "https://dl.bintray.com/hmrc/releases")

addSbtPlugin("uk.gov.hmrc" % "sbt-auto-build" % "1.4.0")

addSbtPlugin("uk.gov.hmrc" % "sbt-git-versioning" % "1.9.0")

addSbtPlugin("uk.gov.hmrc" % "sbt-artifactory" % "0.12.0")

