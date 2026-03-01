package io.rf.crawler.runtime

import cats.effect.{ExitCode, IO, IOApp, Resource}
import io.rf.crawler.application.data.Worker
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
          .flatMap { comps =>
            for _ <- Resource.eval(plant(seed, comps))
            yield Worker[IO](
              comps.scheduler.dispatch,
              comps.ingress,
              comps.workTracker,
              comps.fetcher,
              comps.extractor,
              comps.emitter,
              comps.urlFilter,
              comps.deduplicator,
              maxConcurrency = 4 // reduced concurrency since I'm mostly running it in very slow machines
            ).run.interruptWhen(comps.workTracker.haltSignal)
          }
          .use { _.compile.drain }
          .as(ExitCode.Success)

  private def plant(seed: Uri, comps: Components[IO]): IO[Unit] =
    for
      _ <- comps.ingress.publish(seed)
      _ <- comps.deduplicator.hasSeen(seed).void
      _ <- comps.workTracker.track()
    yield ()

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
