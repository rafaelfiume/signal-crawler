package io.rf.crawler.domain

import munit.FunSuite
import org.http4s.Uri

class UrlFilterSpec extends FunSuite:

  private val seed = Uri.unsafeFromString("https://rafaelfiume.blog")
  private val filter = UrlFilter.makeForSeed(seed)

  test("accepts same host"):
    val uri = Uri.unsafeFromString("https://rafaelfiume.blog/about")
    assert(filter.accept(uri))

  test("filtering accepts different schemes"):
    val uri = Uri.unsafeFromString("http://rafaelfiume.blog/about")
    assert(filter.accept(uri))

  test("filtering is case-insensitive"):
    val uri = Uri.unsafeFromString("http://RAFAelfiume.blog/about")
    assert(filter.accept(uri))

  test("accepts ports when host matches"):
    val uri = Uri.unsafeFromString("https://rafaelfiume.blog:80/page")
    assert(filter.accept(uri))

  test("rejects subdomain"):
    val uri = Uri.unsafeFromString("https://www.rafaelfiume.blog")
    assert(!filter.accept(uri))

  test("rejects other domains"):
    val uri = Uri.unsafeFromString("https://www.others.blog")
    assert(!filter.accept(uri))

  test("rejects relative paths"):
    val uri = Uri.unsafeFromString("/about")
    assert(!filter.accept(uri))

  test("rejects relative URI even if path matches host"):
    val uri = Uri.unsafeFromString("/rafaelfiume.blog/page")
    assert(!filter.accept(uri))

  test("rejects trailing dot in subdomain"):
    val uri = Uri.unsafeFromString("https://www.rafaelfiume.blog./page")
    assert(!filter.accept(uri))

  test("accepts IPv4 host if same as seed (rare)"):
    val seed = Uri.unsafeFromString("https://127.0.0.1")
    val filter = UrlFilter.makeForSeed(seed)
    val uri = Uri.unsafeFromString("https://127.0.0.1/page")
    assert(filter.accept(uri))

  test("rejects different IPv4 host"):
    val seed = Uri.unsafeFromString("https://127.0.0.1")
    val filter = UrlFilter.makeForSeed(seed)
    val uri = Uri.unsafeFromString("https://127.0.0.2/page")
    assert(!filter.accept(uri))

  List(
    // format: off
    ("http://rafaelfiume.blog/page",     true),  // http allowed
    ("https://rafaelfiume.blog/page",    true),  // https allowed
    ("ftp://rafaelfiume.blog/file",      false), // ftp rejected
    ("mailto:someone@example.com",       false), // mailto rejected
    ("tel:+123456789",                   false), // tel rejected
    ("javascript:void(0)",               false), // javascript rejected
    ("file:///local/path",               false)  // file rejected
    // format: on
  ).foreach { case (uriStr, expected) =>
    test(s"accepts scheme $uriStr: expected $expected") {
      val uri = Uri.unsafeFromString(uriStr)
      assertEquals(filter.accept(uri), expected)
    }
  }
