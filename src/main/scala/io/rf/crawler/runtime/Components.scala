package io.rf.crawler.runtime

import cats.effect.{Async, Resource, Sync}
import cats.effect.std.Queue
import io.rf.crawler.application.control.{FifoScheduler, Scheduler}
import io.rf.crawler.application.data.{Deduplicator, Fetcher, LinkExtractor, WorkTracker}
import io.rf.crawler.domain.{HtmlPage, UrlFilter}
import io.rf.crawler.emission.Emitter
import io.rf.crawler.ingress.Ingress
import org.http4s.Uri
import org.http4s.client.middleware.FollowRedirect
import org.http4s.ember.client.EmberClientBuilder
import org.typelevel.log4cats.{LoggerFactory, SelfAwareStructuredLogger}
import org.typelevel.log4cats.slf4j.Slf4jFactory

import scala.concurrent.duration.*

trait Components[F[_]]:
  def queue: Queue[F, Uri]
  def ingress: Ingress[F, Uri]
  def scheduler: Scheduler[F]
  def workTracker: WorkTracker[F]
  def fetcher: Fetcher[F, HtmlPage]
  def extractor: LinkExtractor
  def emitter: Emitter[F]
  def urlFilter: UrlFilter
  def deduplicator: Deduplicator[F]

object Components:
  given [F[_]: Sync] => LoggerFactory[F] = Slf4jFactory.create[F]

  def make[F[_]: Async](seed: Uri)(implicit logger: SelfAwareStructuredLogger[F]): Resource[F, Components[F]] =
    val maxRedirects = 3
    for
      queue0 <- Resource.eval(Queue.unbounded[F, Uri])
      workTracker0 <- Resource.eval(WorkTracker.make[F]())
      httpClient <- EmberClientBuilder.default[F].build.map(FollowRedirect(maxRedirects)(_))
      deduplicator0 <- Resource.eval(Deduplicator.make())
    yield new Components[F]:
      override def queue: Queue[F, Uri] = queue0

      override def ingress: Ingress[F, Uri] = Ingress.makeQueuedIngress(queue)

      override def scheduler: Scheduler[F] = FifoScheduler.make(ingress.stream, dispatchInterval = 100.millis)

      override def workTracker: WorkTracker[F] = workTracker0

      override def fetcher: Fetcher[F, HtmlPage] = Fetcher.makeHtmlPageFetcher(httpClient)

      override def extractor: LinkExtractor = LinkExtractor.make()

      override def emitter: Emitter[F] = Emitter.makeStdout()

      override def urlFilter: UrlFilter = UrlFilter.makeForSeed(seed)

      override def deduplicator: Deduplicator[F] = deduplicator0
