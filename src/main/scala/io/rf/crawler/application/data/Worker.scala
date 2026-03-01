package io.rf.crawler.application.data

import cats.effect.Async
import cats.effect.implicits.*
import cats.implicits.*
import io.rf.crawler.application.data.{Deduplicator, Fetcher, LinkExtractor}
import io.rf.crawler.domain.{HtmlPage, UrlFilter, UrlNormaliser}
import io.rf.crawler.emission.Emitter
import io.rf.crawler.ingress.Ingress
import org.http4s.Uri
import org.typelevel.log4cats.SelfAwareStructuredLogger

class Worker[F[_]: Async](
  targets: fs2.Stream[F, Uri],
  ingress: Ingress[F, Uri],
  tracker: WorkTracker[F],
  fetcher: Fetcher[F, HtmlPage],
  linkExtractor: LinkExtractor,
  emitter: Emitter[F],
  urlFilter: UrlFilter,
  deduplicator: Deduplicator[F],
  maxConcurrency: Int = 16 // may need tuning: set relatively high since orchestrator is I/O bounded (not cpu)
)(using logger: SelfAwareStructuredLogger[F]):
  def run: fs2.Stream[F, Unit] = targets.parEvalMap(maxConcurrency)(process)

  private def process(url: Uri): F[Unit] =
    def publish(newlyDiscovered: Vector[Uri]) = tracker.track(newlyDiscovered.size) *> ingress.publish(newlyDiscovered)

    (for
      htmlPage <- fetcher.fetch(url)
      pageLinks <- linkExtractor.extract(htmlPage) match
        case Right(links) => links.pure
        case Left(err)    => Async[F].raiseError(DomainError(err))
      _ <- emitter.emit(pageLinks)
      acceptedLinks = pageLinks.links.filter(urlFilter.accept)
      canonicalisedLinks = acceptedLinks.map(UrlNormaliser.canonicalise)
      deduplicatedLinks <- canonicalisedLinks.toVector.filterA(link => deduplicator.hasSeen(link).map(!_))
      _ <- publish(deduplicatedLinks)
    yield ())
      .handleErrorWith {
        case DomainError(err) => logger.info(Map("url" -> url.renderString))(err)
        case infraError       => logger.warn(Map("url" -> url.renderString))(infraError.getMessage())
      }
      .guarantee {
        tracker.completed()
      }

private case class DomainError(err: String) extends Throwable
