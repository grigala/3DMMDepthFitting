name := "3dmm-depth-fitting"

version := "1.0"

scalaVersion := "2.12.8"

organization := "ch.unibas.cs.gravis"

scroogeThriftOutputFolder in Compile := (baseDirectory in Compile) (_ / "src/main/thriftgenerated").value

managedSourceDirectories in Compile += (scroogeThriftOutputFolder in Compile).value

resolvers += Resolver.jcenterRepo

resolvers += Resolver.bintrayRepo("unibas-gravis", "maven")

libraryDependencies ++= Seq(
    "ch.unibas.cs.gravis" %% "scalismo-ui" % "0.13.0",
    "ch.unibas.cs.gravis" %% "scalismo-faces" % "0.10.1",
    "com.twitter" %% "scrooge-core" % "18.4.0",
    "com.twitter" %% "finagle-thrift" % "18.4.0",
    "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2",
    "org.scalatest" %% "scalatest" % "3.0.5" % "test",
    "io.spray" %% "spray-json" % "1.3.4",
    "org.scalanlp" %% "breeze-viz" % "0.13",
)
