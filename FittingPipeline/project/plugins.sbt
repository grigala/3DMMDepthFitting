resolvers += "twitter-repo" at "https://maven.twttr.com"

addSbtPlugin("com.twitter" % "scrooge-sbt-plugin" % "18.4.0")
addSbtPlugin("com.typesafe.sbt" % "sbt-git" % "0.9.3")
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.2.1")
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.10")
