import SharedSettings._

name := "scalautils"
organization := "com.wire"
version := "0.0.1"

logLevel := Level.Debug

scalaVersion in ThisBuild := "2.11.8"

licenses in ThisBuild += ("GPL-3.0", url("https://opensource.org/licenses/GPL-3.0"))

scalacOptions += "-target:jvm-1.7"
javacOptions ++= Seq("-source", "1.7", "-target", "1.7")

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
      "org.json" % "json" % "20160810",
      "commons-codec" % "commons-codec" % "1.10",
      "com.wire" % "generic-message-proto" % "1.18.0",
      "org.threeten" % "threetenbp" % "1.3" % Provided,
//      "org.java-websocket" % "Java-WebSocket" % "1.3.0",
      "javax.websocket" % "javax.websocket-api" % "1.1",
      "org.glassfish.tyrus" % "tyrus-client" % "1.13",
      "org.glassfish.tyrus" % "tyrus-container-grizzly-client" % "1.13",

      "org.apache.httpcomponents" % "httpclient" % "4.5.3",
      "org.apache.httpcomponents" % "fluent-hc" % "4.5.3",

      //twitter utils
      "com.twitter" %% "util-cache" % "6.39.0",

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
