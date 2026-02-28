package io.rf.crawler.application.data

import cats.effect.IO
import cats.implicits.*
import munit.CatsEffectSuite
import org.http4s.Uri

class DeduplicatorSpec extends CatsEffectSuite:

  private val url1 = Uri.unsafeFromString("https://rafaelfiume.blog")
  private val url2 = Uri.unsafeFromString("https://rafaelfiume.blog/page")

  List(
    (
      "first occurrence it not a duplicate",
      List(url1),
      List(false)
    ),
    (
      "second occurrence is a duplicate",
      List(url1, url1),
      List(false, true)
    ),
    (
      "different urls are tracked independently",
      List(url1, url2),
      List(false, false)
    ),
    (
      "mixed duplicates behave correctly",
      List(url1, url2, url1, url2),
      List(false, false, true, true)
    )
  ).foreach { case (name, urls, expected) =>
    test(name) {
      for
        dedup <- Deduplicator.make[IO]()
        results <- urls.traverse(dedup.hasSeen)
      yield assertEquals(results, expected)
    }
  }

  List(
    // format: off
    (
      "scheme casing is ignored",
      List(
        "https://rafaelfiume.blog/page",
        "HTTPS://rafaelfiume.blog/page"
      ),
      List(false, true)
    ),
    (
      "host casing is ignored",
      List(
        "https://rafaelfiume.blog/page",
        "https://RAFAELFIUME.BLOG/page"
      ),
      List(false, true)
    ),
    (
      "path casing is preserved (must be normalised)",
      List(
        "https://rafaelfiume.blog/page",
        "https://rafaelfiume.blog/Page"
      ),
      List(false, false)
    )
    // format: on
  ).foreach { case (name, rawUrls, expected) =>
    test(name) {
      for
        dedup <- Deduplicator.make[IO]()
        urls = rawUrls.map(Uri.unsafeFromString)
        result <- urls.traverse(dedup.hasSeen)
      yield assertEquals(result, expected)
    }
  }
