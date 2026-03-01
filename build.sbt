val scala3Version = "3.7.4"

ThisBuild / semanticdbEnabled := true
ThisBuild / semanticdbVersion := scalafixSemanticdb.revision
ThisBuild / scalacOptions ++= Seq(
  "-Wunused:imports"
)

Compile / run / fork := true
Compile / run / javaOptions ++= Seq(
  "-Xms1G",
  "-Xmx3G",
  "-XX:+UseG1GC",
  "-XX:MaxGCPauseMillis=200"
)

lazy val testDeps = Seq(
  "org.scalameta" %% "munit"                   % "1.0.0"      % Test,
  "org.scalameta" %% "munit-scalacheck"        % "1.1.0"      % Test,
  "org.typelevel" %% "munit-cats-effect"       % "2.1.0"      % Test,
  "org.typelevel" %% "scalacheck-effect-munit" % "1.0.4"      % Test
)

lazy val itTestDeps = Seq(
  "com.dimafeng"         %% "testcontainers-scala-munit"          % "0.43.0"      % Test,
  "com.dimafeng"         %% "testcontainers-scala-postgresql"     % "0.43.0"      % Test
)

lazy val It = config("it").extend(Test)

lazy val root = project
  .in(file("."))
  .configs(It)
  .settings(
    inConfig(It)(Defaults.testSettings ++ scalafixConfigSettings(It))
   )
  .settings(
    name := "site-crawler",
    version := "0.1.0-SNAPSHOT",

    scalaVersion := scala3Version,

    libraryDependencies ++= Seq(
      "is.cir"        %% "ciris"                               % "3.9.0",

      "co.fs2"        %% "fs2-core"                            % "3.12.0",
			"co.fs2"        %% "fs2-io"                              % "3.12.0",

      "org.jsoup"     %  "jsoup"                               % "1.2.1",

      "org.http4s"    %% "http4s-dsl"                          % "1.0.0-M44",
      "org.http4s"    %% "http4s-ember-client"                 % "1.0.0-M44",
      "org.http4s"    %% "http4s-ember-server"                 % "1.0.0-M44",

      "org.flywaydb"  %  "flyway-core"                         % "11.9.1",
      "org.flywaydb"  %  "flyway-database-postgresql"          % "11.9.1",

      "org.tpolecat"  %% "doobie-postgres-circe"               % "1.0.0-RC9",
      "org.tpolecat"  %% "doobie-core"                         % "1.0.0-RC9",
      "org.tpolecat"  %% "doobie-postgres"                     % "1.0.0-RC9",
      "org.tpolecat"  %% "doobie-hikari"                       % "1.0.0-RC9",

      "org.typelevel" %% "cats-effect"                         % "3.6.1",
      "org.typelevel" %% "cats-effect-std"                     % "3.5.3",

      "org.typelevel" %% "log4cats-slf4j"                      % "2.7.1",
      "org.slf4j"     %  "slf4j-simple"                        % "2.0.17",
    ) ++ testDeps ++ itTestDeps
  )
