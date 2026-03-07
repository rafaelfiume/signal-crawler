package io.rf.crawler.infra.storage.postgres.ingress

import cats.effect.IO
import cats.syntax.all.*
import doobie.*
import doobie.implicits.*
import io.rf.crawler.infra.storage.postgres.PostgresTransactionManager
import io.rf.crawler.infra.storage.postgres.ingress.PostgresIngress
import io.rf.crawler.infra.storage.postgres.testkit.DockerPostgresSuite
import munit.{CatsEffectSuite, ScalaCheckEffectSuite}
import org.http4s.Uri
import org.scalacheck.{Arbitrary, Gen, ShrinkLowPriority}
import org.scalacheck.effect.PropF.forAllF

class PostgresIngressSpec
    extends CatsEffectSuite
    with ScalaCheckEffectSuite
    with DockerPostgresSuite
    with ShrinkLowPriority
    with PostgresIngressSpecContext:

  override def scalaCheckTestParameters =
    super.scalaCheckTestParameters.withMinSuccessfulTests(1)

  test("publishes and dequeues a single url"):
    forAllF { (uri: Uri) =>
      will(cleanStorage) {
        (
          PostgresIngress.make(),
          PostgresTransactionManager.make[IO](transactor())
        ).tupled.use { case (ingress, tx) =>
          for
            _ <- tx.commit { ingress.publish(uri) }

            dequeued <- tx.commit {
              ingress.stream.take(1).compile.lastOrError
            }
          yield assertEquals(dequeued, uri)
        }
      }
    }

  test("publish is idempotent"):
    forAllF { (uri: Uri) =>
      will(cleanStorage) {
        (
          PostgresIngress.make(),
          PostgresTransactionManager.make[IO](transactor())
        ).tupled.use { case (ingress, tx) =>
          for
            _ <- tx.commit { ingress.publish(uri) }
            _ <- tx.commit { ingress.publish(uri) }

            count <- tx.commit {
              sql"""
                SELECT COUNT(*)
                FROM crawler.crawl_admission_queue
              """.query[Long].unique
            }
          yield assertEquals(count, 1L)
        }
      }
    }

  test("dequeue removes url from queue"):
    forAllF { (uri: Uri) =>
      will(cleanStorage) {
        (
          PostgresIngress.make(),
          PostgresTransactionManager.make[IO](transactor())
        ).tupled.use { case (ingress, tx) =>
          for
            _ <- tx.commit { ingress.publish(uri) }

            _ <- tx.commit {
              ingress.stream.take(1).compile.drain
            }

            remaining <- tx.commit { queueSize() }
          yield assertEquals(remaining, 0L)
        }
      }
    }

trait PostgresIngressSpecContext:

  def cleanStorage: ConnectionIO[Unit] =
    sql"TRUNCATE TABLE crawler.crawl_admission_queue".update.run.void

  def queueSize(): ConnectionIO[Long] =
    sql"""
      SELECT COUNT(*)
      FROM crawler.crawl_admission_queue
    """.query[Long].unique

  given Arbitrary[Uri] = Arbitrary {
    def boundedStr(chars: Gen[Char]) = Gen.choose(1, 12).flatMap(Gen.stringOfN(_, chars))

    for
      scheme <- Gen.oneOf("http", "https")
      sub <- boundedStr(Gen.alphaLowerChar)
      tld <- Gen.oneOf("com", "org", "net", "io")
      segs <- Gen.chooseNum(1, 4).flatMap(Gen.listOfN(_, boundedStr(Gen.alphaNumChar)))
    yield Uri(
      scheme = Some(Uri.Scheme.unsafeFromString(scheme)),
      authority = Some(Uri.Authority(host = Uri.RegName(s"$sub.$tld"))),
      path = segs.foldLeft(Uri.Path.empty)(_ addSegment _)
    )
  }
