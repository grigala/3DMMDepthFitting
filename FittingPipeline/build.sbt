import sbt.Resolver

name := "3DMMDepthFitting"

version := "1.0"

scalaVersion := "2.12.8"

organization := "io.github.grigala"

scroogeThriftOutputFolder in Compile := (baseDirectory in Compile) (_ / "src/main/thriftgenerated").value

managedSourceDirectories in Compile += (scroogeThriftOutputFolder in Compile).value

resolvers += Resolver.jcenterRepo

resolvers += Resolver.bintrayRepo("unibas-gravis", "maven")

libraryDependencies ++= Seq(
    "ch.unibas.cs.gravis" %% "scalismo-ui" % "0.90.0",
    "ch.unibas.cs.gravis" %% "scalismo-faces" % "0.90.0",
    "com.twitter" %% "scrooge-core" % "21.8.0",
    "com.twitter" %% "finagle-thrift" % "21.8.0",
    "com.typesafe.scala-logging" %% "scala-logging" % "3.9.4",
    "org.scalatest" %% "scalatest" % "3.2.9" % "test",
    "io.spray" %% "spray-json" % "1.3.6",
    "org.scalanlp" %% "breeze-viz" % "1.3"
)
