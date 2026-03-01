package io.rf.crawler.infra.storage.postgres.ingress

import cats.effect.Resource
import cats.implicits.*
import doobie.*
import doobie.free.connection.ConnectionIO
import doobie.implicits.*
import io.rf.crawler.ingress.Ingress
import org.http4s.Uri

object PostgresIngress:
  def make[F[_]](): Resource[F, Ingress[ConnectionIO, Uri]] =
    Resource.pure(new PostgresIngress())

private class PostgresIngress private () extends Ingress[ConnectionIO, Uri]:

  override def publish(url: Uri): ConnectionIO[Unit] =
    Statements.insertUrl(url)

  override def publish(urls: Vector[Uri]): ConnectionIO[Unit] =
    Statements.insertUrls(urls)

  override def stream: fs2.Stream[ConnectionIO, Uri] =
    fs2.Stream.repeatEval(Statements.dequeueUrl).unNone

private object Statements:

  def insertUrl(url: Uri): ConnectionIO[Unit] =
    // DO NOTHING fits Set semantics
    sql"""
      INSERT INTO crawler.crawl_admission_queue (url)
      VALUES (${url.renderString})
      ON CONFLICT (url) DO NOTHING
    """.update.run.void

  def insertUrls(urls: Vector[Uri]): ConnectionIO[Unit] =
    Update[String](
      """
        INSERT INTO crawler.crawl_admission_queue (url)
        VALUES (?)
        ON CONFLICT (url) DO NOTHING
      """
    ).updateMany(urls.map(_.renderString)).void

  def dequeueUrl: ConnectionIO[Option[Uri]] =
    sql"""
      WITH cte AS (
        SELECT id, url
        FROM crawler.crawl_admission_queue
        ORDER BY id
        LIMIT 1
        FOR UPDATE SKIP LOCKED
      )
      DELETE FROM crawler.crawl_admission_queue
      USING cte
      WHERE crawler.crawl_admission_queue.id = cte.id
      RETURNING cte.url
    """
      .query[String]
      .option
      .map(_.flatMap(s => Uri.fromString(s).toOption))
