name := "stomp-server"

version := "0.1.7"

scalaVersion := "2.12.10"

scalacOptions ++= Seq( "-deprecation", "-feature", "-unchecked", "-language:postfixOps", "-language:implicitConversions", "-language:existentials",
  "-P:scalajs:sjsDefinedByDefault" )

organization := "xyz.hyperreal"

Global / onChangedBuildSource := ReloadOnSourceChanges

resolvers += "Typesafe Repository" at "https://repo.typesafe.com/typesafe/releases/"

resolvers += "Hyperreal Repository" at "https://dl.bintray.com/edadma/maven"

enablePlugins(ScalaJSPlugin)

enablePlugins(ScalaJSBundlerPlugin)

scalaJSUseMainModuleInitializer := true

jsEnv := new org.scalajs.jsenv.nodejs.NodeJSEnv()

npmDependencies in Compile ++=
  Seq(
    "uuid" -> "3.3.3",
    "sockjs" -> "^0.3.19",
    // testing
//    "sockjs-client" -> "1.4.0",
//    "stompjs" -> "2.3.3"
)

libraryDependencies ++= Seq(
  ScalablyTyped.U.uuid,
  ScalablyTyped.S.sockjs,
//  ScalablyTyped.S.`sockjs-client`,
//  ScalablyTyped.S.stompjs
)

libraryDependencies ++= Seq(
  "org.scalatest" %%% "scalatest" % "3.0.8" % "test",
  "org.scalacheck" %%% "scalacheck" % "1.14.1" % "test"
)

coverageExcludedPackages := ".*Main"

mainClass in (Compile, run) := Some( "xyz.hyperreal." + name.value.replace('-', '_') + ".Main" )

mainClass in assembly := Some( "xyz.hyperreal." + name.value.replace('-', '_') + ".Main" )

assemblyJarName in assembly := name.value + "-" + version.value + ".jar"

publishMavenStyle := true

publishArtifact in Test := false

pomIncludeRepository := { _ => false }

licenses := Seq("ISC" -> url("https://opensource.org/licenses/ISC"))

homepage := Some(url("https://github.com/edadma/" + name.value))

pomExtra :=
  <scm>
    <url>git@github.com:edadma/{name.value}.git</url>
    <connection>scm:git:git@github.com:edadma/{name.value}.git</connection>
  </scm>
  <developers>
    <developer>
      <id>edadma</id>
      <name>Edward A. Maxedon, Sr.</name>
      <url>https://github.com/edadma</url>
    </developer>
  </developers>
