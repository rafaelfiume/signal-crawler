package io.rf.crawler.application

import cats.effect.{Async, Ref}
import cats.effect.implicits.*
import cats.effect.std.Queue
import cats.implicits.*
import io.rf.crawler.application.data.{Deduplicator, Fetcher, LinkExtractor}
import io.rf.crawler.domain.{HtmlPage, UrlFilter, UrlNormaliser}
import io.rf.crawler.emission.Emitter
import org.http4s.Uri
import org.typelevel.log4cats.SelfAwareStructuredLogger

import scala.concurrent.duration.*

class Orchestrator[F[_]: Async](
  fetcher: Fetcher[F, HtmlPage],
  linkExtractor: LinkExtractor,
  emitter: Emitter[F],
  urlFilter: UrlFilter,
  deduplicator: Deduplicator[F],
  queueMaxCapacity: Int =
    16 * 50 * 2, // use heuristics to decide the queue capacity, for example `maxConcurrency * avg. page links * buffering factor`
  maxConcurrency: Int = 16, // may need tuning: set relatively high since orchestrator is I/O bounded (not cpu)
  haltingCheckInterval: FiniteDuration = 1.second
)(using logger: SelfAwareStructuredLogger[F]):
  def run(seed: Uri): fs2.Stream[F, Unit] =
    for
      queue <- fs2.Stream.eval(Queue.bounded[F, Uri](queueMaxCapacity))
      inflightWork <- fs2.Stream.eval(Ref.of(0L))
      _ <- fs2.Stream.eval(inflightWork.update(_ + 1) *> queue.offer(seed))
      _ <- fs2.Stream.eval(deduplicator.hasSeen(seed).void) // Track seed as "seen"
      stream <- fs2.Stream
        .fromQueueUnterminated(queue) // Queue
        .parEvalMap(maxConcurrency)(process(queue, inflightWork))
        .interruptWhen(haltSignal(inflightWork))
    yield stream

  private def process(queue: Queue[F, Uri], inflightWork: Ref[F, Long])(url: Uri): F[Unit] =
    def enqueue(url: Uri) = inflightWork.update(_ + 1) *> queue.offer(url)

    (for
      htmlPage <- fetcher.fetch(url)
      pageLinks <- linkExtractor.extract(htmlPage) match
        case Right(links) => links.pure
        case Left(err)    => Async[F].raiseError(DomainError(err))
      _ <- emitter.emit(pageLinks)
      acceptedLinks = pageLinks.links.filter(urlFilter.accept)
      canonicalisedLinks = acceptedLinks.map(UrlNormaliser.canonicalise)
      deduplicatedLinks <- canonicalisedLinks.toVector.filterA(link => deduplicator.hasSeen(link).map(!_))
      _ <- deduplicatedLinks.traverse_(
        enqueue // liveness issue right here!
      )
    yield ())
      .handleErrorWith {
        case DomainError(err) => logger.info(Map("url" -> url.renderString))(err)
        case infraError       => logger.warn(Map("url" -> url.renderString))(infraError.getMessage())
      }
      .guarantee {
        inflightWork.update(_ - 1)
      }

  private def haltSignal(inflightWork: Ref[F, Long]): fs2.Stream[F, Boolean] =
    fs2.Stream
      .repeatEval(inflightWork.get)
      .metered(haltingCheckInterval)
      .dropWhile(_ != 0)
      .take(1)
      .as(true)

  private case class DomainError(err: String) extends Throwable
