package io.rf.crawler.application.data

import io.rf.crawler.domain.*
import org.http4s.Uri
import org.jsoup.Jsoup

import scala.jdk.CollectionConverters.*
import scala.util.Try

// A sealed ADT to model errors should be considered, specially if failure modes evolve.
type Error = String

trait LinkExtractor:
  def extract(page: HtmlPage): Either[Error, PageLinks]

object LinkExtractor:
  def make(): LinkExtractor = new LinkExtractor:
    override def extract(page: HtmlPage): Either[Error, PageLinks] =
      Try {
        val doc = Jsoup.parse(page.content, page.url.renderString)

        // see https://jsoup.org/cookbook/extracting-data/example-list-links
        val links = doc
          .select("a[href]")
          .asScala
          .iterator
          .map(_.attr("abs:href"))
          .flatMap(href => Uri.fromString(href).toOption) // silently drops invalid uri's
          .toSet
        PageLinks(page.url, links)
      }.toEither.left.map(ex => s"${page.url}; ${ex.getMessage()}")
