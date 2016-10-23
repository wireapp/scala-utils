import SharedSettings._

name := "scalautils"
organization := "com.wire"
version := "0.0.1"

scalaVersion in ThisBuild := "2.11.8"

licenses in ThisBuild += ("GPL-3.0", url("https://opensource.org/licenses/GPL-3.0"))

lazy val licenseHeaders = HeaderPlugin.autoImport.headers := Set("scala", "java", "rs") .map { _ -> GPLv3("2016", "Wire Swiss GmbH") } (collection.breakOut)

lazy val scalautils = (project in file("."))
  .enablePlugins(AutomateHeaderPlugin).settings(licenseHeaders)
  .settings(
    compile := (compile in core in Compile).value,
    test := (test in core in Test).value
  )

lazy val core = (project in file("core"))
  .enablePlugins(AutomateHeaderPlugin).settings(licenseHeaders)
  .dependsOn(macrosupport)
  .settings(
    libraryDependencies ++= Seq(
      "ch.qos.logback" % "logback-classic" % "1.1.7",
      "com.typesafe.scala-logging" %% "scala-logging" % "3.4.0",
      "org.threeten" % "threetenbp" % "1.3" % Provided,

      //test dependencies
      "org.scalatest" %% "scalatest" % "2.2.6" % "test", //scalamock 3.2.2 is incompatible with scalatest 3.0.0
      "org.scalamock" %% "scalamock-scalatest-support" % "3.2.2" % "test"
    )
  )

lazy val macrosupport = (project in file("macrosupport"))
  .enablePlugins(AutomateHeaderPlugin).settings(licenseHeaders)
  .settings(
    libraryDependencies ++= Seq(
      "org.scala-lang" % "scala-reflect" % (scalaVersion in ThisBuild).value % Provided
    )
  )
