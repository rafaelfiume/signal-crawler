package io.rf.crawler.app

import cats.effect.{Async, Resource, Sync}
import io.rf.crawler.core.{Deduplicator, UrlFilter}
import io.rf.crawler.domain.HtmlPage
import io.rf.crawler.emission.Emitter
import io.rf.crawler.ingestion.{Fetcher, LinkExtractor}
import org.http4s.Uri
import org.http4s.ember.client.EmberClientBuilder
import org.typelevel.log4cats.{LoggerFactory, SelfAwareStructuredLogger}
import org.typelevel.log4cats.slf4j.Slf4jFactory

trait Components[F[_]]:
  def fetcher: Fetcher[F, HtmlPage]
  def extractor: LinkExtractor
  def emitter: Emitter[F]
  def urlFilter: UrlFilter
  def deduplicator: Deduplicator[F]

object Components:
  given [F[_]: Sync] => LoggerFactory[F] = Slf4jFactory.create[F]

  def make[F[_]: Async](seed: Uri)(implicit logger: SelfAwareStructuredLogger[F]): Resource[F, Components[F]] =
    for
      httpClient <- EmberClientBuilder.default[F].build
      deduplicator0 <- Resource.eval(Deduplicator.make())
    yield new Components[F]:
      override def fetcher: Fetcher[F, HtmlPage] = Fetcher.makeHtmlPageFetcher(httpClient)

      override def extractor: LinkExtractor = LinkExtractor.make()

      override def emitter: Emitter[F] = Emitter.makeStdout()

      override def urlFilter: UrlFilter = UrlFilter.makeForSeed(seed)

      override def deduplicator: Deduplicator[F] = deduplicator0
