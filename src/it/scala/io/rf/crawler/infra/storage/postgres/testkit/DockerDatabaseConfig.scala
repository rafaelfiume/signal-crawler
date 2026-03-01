package io.rf.crawler.infra.storage.postgres.testkit

object DockerDatabaseConfig:
  val postgreSQLVersion = "16.2-bullseye"
  val driver = "org.postgresql.Driver"
  val database = "crawler"
  val user = "crawler"
  val password = "crawler"
