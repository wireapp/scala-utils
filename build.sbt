name := "scalautils"
organization := "com.wire"
version := "0.0.1"

scalaVersion in ThisBuild := "2.11.8"

lazy val scalautils = (project in file("."))
  .settings(
    compile := (compile in core in Compile).value,
    test := (test in core in Test).value
  )

lazy val core = (project in file("core"))
  .dependsOn(macrosupport)
  .settings(
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % "3.0.0" % "test",
      "ch.qos.logback" % "logback-classic" % "1.1.7",
      "com.typesafe.scala-logging" %% "scala-logging" % "3.4.0",
      "org.threeten" % "threetenbp" % "1.3" % Provided
    )
  )

lazy val macrosupport = (project in file("macrosupport"))
  .settings(
    libraryDependencies ++= Seq(
      "org.scala-lang" % "scala-reflect" % (scalaVersion in ThisBuild).value % Provided
    )
  )

