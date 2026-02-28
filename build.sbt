val scala3Version = "3.7.4"

ThisBuild / semanticdbEnabled := true
ThisBuild / semanticdbVersion := scalafixSemanticdb.revision
ThisBuild / fork := true

scalacOptions ++= Seq(
  "-Wunused:imports"
)

lazy val root = project
  .in(file("."))
  .settings(
    name := "site-crawler",
    version := "0.1.0-SNAPSHOT",

    scalaVersion := scala3Version,

    libraryDependencies ++= Seq(
      "co.fs2"        %% "fs2-core"            % "3.12.0",
			"co.fs2"        %% "fs2-io"              % "3.12.0",
      "org.jsoup"     %  "jsoup"               % "1.2.1",
      "org.http4s"    %% "http4s-dsl"          % "1.0.0-M44",
      "org.http4s"    %% "http4s-ember-client" % "1.0.0-M44",
      "org.http4s"    %% "http4s-ember-server" % "1.0.0-M44",
      "org.typelevel" %% "cats-effect"         % "3.6.1",
      "org.typelevel" %% "cats-effect-std"     % "3.5.3",
      "org.typelevel" %% "log4cats-slf4j"      % "2.7.1",
      "org.slf4j"     %  "slf4j-simple"        % "2.0.17",
      "org.typelevel" %% "munit-cats-effect"   % "2.1.0" % Test)
  )
