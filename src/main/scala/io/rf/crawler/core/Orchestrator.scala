package io.rf.crawler.core

import cats.effect.Async
import cats.effect.std.Queue
import cats.implicits.*
import io.rf.crawler.domain.HtmlPage
import io.rf.crawler.emission.Emitter
import io.rf.crawler.ingestion.{Fetcher, LinkExtractor}
import org.http4s.Uri
import org.typelevel.log4cats.SelfAwareStructuredLogger

class Orchestrator[F[_]: Async](
  fetcher: Fetcher[F, HtmlPage],
  extractor: LinkExtractor,
  emitter: Emitter[F],
  urlFilter: UrlFilter,
  deduplicator: Deduplicator[F],
  maxConcurrent: Int = 16 // may need tuning: set relatively hight since orchestrator is I/O bounded (not cpu)
)(implicit logger: SelfAwareStructuredLogger[F]):
  def run(seed: Uri): fs2.Stream[F, Unit] =
    for
      queue <- fs2.Stream.eval(Queue.unbounded[F, Option[Uri]])
      _ <- fs2.Stream.eval(queue.offer(seed.some))
      stream <- fs2.Stream
        .fromQueueNoneTerminated(queue) // Queue
        .parEvalMap(maxConcurrent) { uri =>
          fetcher.fetch(uri).attempt.map { // Fetches web page corresponding to the next uri in the queue
            _.flatMap(extractor.extract(_)) // Extracts well-formed links
          }
        }
        .evalMap { // drops and logs fetcher or extractor errors
          case Right(pageLinks) => pageLinks.some.pure
          case Left(err)        => logger.info(s"failed to extract links: $err").as(none)
        }
        .unNone
        .evalTap(emitter.emit(_)) // Prints page being processed and its valid links
        .flatMap { pageLinks =>
          fs2.Stream.emits(pageLinks.links.toVector) // Emit each valid link found in the page being processed
        }
        .evalMap { link => // Filters links pointing to external domains or subdomains
          if urlFilter.accept(link) then link.some.pure
          else logger.debug(s"filtered link $link").as(none)
        }
        .unNone
        .evalMap { link => // Deduplicates links
          deduplicator
            .hasSeen(link)
            .ifM(
              logger.debug(s"deduplicated link $link").as(none),
              link.some.pure
            )
        }
        .unNone // stop None instances due to duplication from being queued
        .evalMap { link =>
          queue.offer(link.some) // queue links
        }
    yield stream
