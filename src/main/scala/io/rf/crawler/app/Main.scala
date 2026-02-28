package io.rf.crawler.app

import cats.effect.{ExitCode, IO, IOApp}
import io.rf.crawler.application.Orchestrator
import io.rf.crawler.domain.UrlValidator
import org.http4s.Uri
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

object Main extends IOApp:

  implicit val logger: SelfAwareStructuredLogger[IO] = Slf4jLogger.getLogger[IO]

  override def run(args: List[String]): IO[ExitCode] =
    validateArgs(args) match
      case Left(error) =>
        logger.error(
          Map("args" -> args.mkString(","), "error" -> error.getMessage)
        )("Invalid CLI argument; usage: `$ sbt \"run <seed-url>\"`") *>
          IO.pure(ExitCode.Error)

      case Right(seed) =>
        Components
          .make[IO](seed)
          .map { comps =>
            Orchestrator[IO](
              comps.fetcher,
              comps.extractor,
              comps.emitter,
              comps.urlFilter,
              comps.deduplicator
            ).run(seed)
          }
          .use { _.compile.drain }
          .as(ExitCode.Success)

  private def validateArgs(args: List[String]): Either[RuntimeException, Uri] =
    args match
      case uri :: Nil =>
        Uri
          .fromString(uri)
          .left
          .map(ex => RuntimeException(s"invalid seed URI: ${ex.getMessage()}"))
          .flatMap { uri =>
            Either.cond(UrlValidator.isValid(uri), uri, RuntimeException("seed URI is invalid for crawling"))
          }

      case _ =>
        Left(RuntimeException(s"expected a single seed URI parameter; got $args"))
