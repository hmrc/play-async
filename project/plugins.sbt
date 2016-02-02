resolvers += Resolver.url("hmrc-sbt-plugin-releases",
  url("https://dl.bintray.com/hmrc/sbt-plugin-releases"))(Resolver.ivyStylePatterns)

resolvers += "Typesafe Releases" at "http://repo.typesafe.com/typesafe/releases/"

addSbtPlugin("uk.gov.hmrc" % "sbt-auto-build" % "1.0.0")

addSbtPlugin("uk.gov.hmrc" % "sbt-git-versioning" % "0.8.0")

addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.3.9")



////credentials += Credentials(Path.userHome / ".sbt" / ".credentials")
//
////val hmrcRepoHost = java.lang.System.getProperty("hmrc.repo.host", "https://nexus-preview.tax.service.gov.uk")
//
////resolvers ++= Seq("hmrc-snapshots" at hmrcRepoHost + "/content/repositories/hmrc-snapshots",
////  "hmrc-releases" at hmrcRepoHost + "/content/repositories/hmrc-releases",
////  "typesafe-releases" at hmrcRepoHost + "/content/repositories/typesafe-releases",
////  Resolver.url("hmrc-sbt-plugin-releases",
////    url("https://dl.bintray.com/hmrc/sbt-plugin-releases"))(Resolver.ivyStylePatterns))
//
//resolvers += Resolver.url("hmrc-sbt-plugin-releases",
//  url("https://dl.bintray.com/hmrc/sbt-plugin-releases"))(Resolver.ivyStylePatterns)
//
//resolvers += "Typesafe Releases" at "http://repo.typesafe.com/typesafe/releases/"
//
//
////addSbtPlugin("uk.gov.hmrc" % "sbt-settings" % "3.2.0")
//
//addSbtPlugin("com.github.gseitz" % "sbt-release" % "0.8.5")
//
////addSbtPlugin("uk.gov.hmrc" % "hmrc-resolvers" % "0.3.0")
//
//addSbtPlugin("uk.gov.hmrc" % "sbt-distributables" % "0.10.0")
//
//addSbtPlugin("uk.gov.hmrc" % "sbt-auto-build" % "1.0.0")
//
//addSbtPlugin("uk.gov.hmrc" % "sbt-git-versioning" % "0.8.0")
//
//addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.3.9")
//
