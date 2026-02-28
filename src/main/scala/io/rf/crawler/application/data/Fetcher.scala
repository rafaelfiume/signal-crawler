package io.rf.crawler.application.data

import cats.effect.Async
import cats.implicits.*
import io.rf.crawler.domain.HtmlPage
import org.http4s.Uri
import org.http4s.client.Client
import org.http4s.client.dsl.Http4sClientDsl

trait Fetcher[F[_], A]:
  def fetch(url: Uri): F[A]

object Fetcher:
  def makeHtmlPageFetcher[F[_]: Async](httpClient: Client[F]): Fetcher[F, HtmlPage] =
    new Fetcher[F, HtmlPage] with Http4sClientDsl[F]:
      override def fetch(url: Uri): F[HtmlPage] =
        httpClient.expect[String](url).map(HtmlPage(url, _))
