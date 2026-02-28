package io.rf.crawler.domain

import munit.FunSuite
import org.http4s.Uri

class UrlNormaliserSpec extends FunSuite:

  List(
    // format: off
    // already canonicalised
    ("http://rafaelfiume.blog/page",            "http://rafaelfiume.blog/page"),

    // query parameters preserved
    ("https://rafaelfiume.blog/page?x=1&y=2",   "https://rafaelfiume.blog/page?x=1&y=2"),

    // fragments removed
    ("https://rafaelfiume.blog/page#section",   "https://rafaelfiume.blog/page"),

    // collapse duplicate slashes
    ("https://rafaelfiume.blog//about//",       "https://rafaelfiume.blog/about"),
    ("https://rafaelfiume.blog//page//subpage", "https://rafaelfiume.blog/page/subpage"),

    // trailing slash removed from non-root path
    ("https://rafaelfiume.blog/about/",          "https://rafaelfiume.blog/about"),
    ("https://rafaelfiume.blog/",                "https://rafaelfiume.blog"),

    // path lowercase - schema & host are addressed by the deduplicator
    ("http://rafaelfiume.blog/PAGE",             "http://rafaelfiume.blog/page"),

    // default ports removed
    ("http://rafaelfiume.blog:80/page",          "http://rafaelfiume.blog/page"),
    ("https://rafaelfiume.blog:443/page",        "https://rafaelfiume.blog/page"),

    // non-default ports preserved
    ("https://rafaelfiume.blog:8443/page",       "https://rafaelfiume.blog:8443/page"),

    // trailing dot removed
    ("https://rafaelfiume.blog./page",           "https://rafaelfiume.blog/page")
    //format: on
  ).foreach { case (input, expected) =>
    test(s"normalises $input => $expected") {
      val url = Uri.unsafeFromString(input)
      val canonicalisedUrl = Uri.unsafeFromString(expected)
      assertEquals(UrlNormaliser.canonicalise(url), canonicalisedUrl)
    }
  }
