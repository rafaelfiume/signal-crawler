package io.rf.crawler.domain

import munit.FunSuite
import org.http4s.Uri

class UrlNormaliserSpec extends FunSuite:

  List(
    // format: off

    // ── Already canonical ──────────────────────────────────────────────────
    ("http://rafaelfiume.blog/page",                "http://rafaelfiume.blog/page"),

    // ── Query parameters preserved (non-tracking) ──────────────────────────
    ("https://rafaelfiume.blog/page?x=1&y=2",      "https://rafaelfiume.blog/page?x=1&y=2"),

    // ── Fragments removed ──────────────────────────────────────────────────
    ("https://rafaelfiume.blog/page#section",       "https://rafaelfiume.blog/page"),

    // ── Duplicate slashes collapsed ────────────────────────────────────────
    ("https://rafaelfiume.blog//about//",           "https://rafaelfiume.blog/about"),
    ("https://rafaelfiume.blog//page//subpage",     "https://rafaelfiume.blog/page/subpage"),

    // ── Trailing slash removed from non-root path ──────────────────────────
    ("https://rafaelfiume.blog/about/",             "https://rafaelfiume.blog/about"),
    ("https://rafaelfiume.blog/",                   "https://rafaelfiume.blog"),

    // ── Path case preserved ────────────────────────────────────────────────
    // Paths are case-sensitive per RFC 3986: /wiki/DDoS ≠ /wiki/ddos.
    // Only the host component is case-folded (handled in normalisedAuth via http4s).
    ("http://rafaelfiume.blog/PAGE",                "http://rafaelfiume.blog/PAGE"),

    // ── Default ports removed ──────────────────────────────────────────────
    ("http://rafaelfiume.blog:80/page",             "http://rafaelfiume.blog/page"),
    ("https://rafaelfiume.blog:443/page",           "https://rafaelfiume.blog/page"),

    // ── Non-default ports preserved ────────────────────────────────────────
    ("https://rafaelfiume.blog:8443/page",          "https://rafaelfiume.blog:8443/page"),

    // ── Trailing dot removed from host ─────────────────────────────────────
    ("https://rafaelfiume.blog./page",              "https://rafaelfiume.blog/page"),

    // ── Percent-encoding: decode unreserved characters (RFC 3986 §6.2.2.1) ─
    // Unreserved = A-Z a-z 0-9 - . _ ~
    // %41='A', %62='b', %6F='o', %75='u', %74='t' are all unreserved → decoded.
    ("https://rafaelfiume.blog/%41%62%6F%75%74",   "https://rafaelfiume.blog/About"),
    // %7E='~' is unreserved → decoded; %2F='/' is reserved → kept, uppercased.
    ("https://rafaelfiume.blog/tilde%7Etest",       "https://rafaelfiume.blog/tilde~test"),

    // ── Percent-encoding: uppercase hex digits for non-unreserved chars ─────
    // Non-ASCII bytes (e.g. Thai, Tamil) must stay encoded; only hex case normalised.
    ("https://th.wikipedia.org/wiki/%e0%b8%81",    "https://th.wikipedia.org/wiki/%E0%B8%81"),
    ("https://ta.wikipedia.org/wiki/%e0%ae%95%e0%ae%a3%e0%ae%bf", "https://ta.wikipedia.org/wiki/%E0%AE%95%E0%AE%A3%E0%AE%BF"),
    // Mixed case hex normalised to uppercase.
    ("https://rafaelfiume.blog/%c3%A9t%C3%a9",     "https://rafaelfiume.blog/%C3%A9t%C3%A9"),

    // ── Query: tracking parameters stripped ───────────────────────────────
    // utm_* family.
    ("https://rafaelfiume.blog/page?utm_source=twitter&content=true",  "https://rafaelfiume.blog/page?content=true"),
    ("https://rafaelfiume.blog/page?x=1&utm_campaign=spring&y=2",      "https://rafaelfiume.blog/page?x=1&y=2"),
    ("https://rafaelfiume.blog/page?utm_source=a&utm_medium=b&utm_campaign=c", "https://rafaelfiume.blog/page"),
    // Social tracking params.
    ("https://rafaelfiume.blog/page?fbclid=abc123",                     "https://rafaelfiume.blog/page"),
    ("https://rafaelfiume.blog/page?gclid=xyz&q=scala",                 "https://rafaelfiume.blog/page?q=scala"),
    // Miscellaneous tracking params.
    ("https://rafaelfiume.blog/page?ref=homepage&id=42",                "https://rafaelfiume.blog/page?id=42"),
    ("https://rafaelfiume.blog/page?icid=promo",                        "https://rafaelfiume.blog/page"),
    // Locale selectors — select a rendering variant, not a distinct resource.
    // e.g. Apple App Store country/locale params: ?l=ru, ?l=pt-BR.
    ("https://apps.apple.com/us/iphone/today?l=ru",                     "https://apps.apple.com/us/iphone/today"),
    ("https://apps.apple.com/us/iphone/today?l=pt-BR",                  "https://apps.apple.com/us/iphone/today"),
    ("https://rafaelfiume.blog/page?lang=en&id=42",                     "https://rafaelfiume.blog/page?id=42"),
    ("https://rafaelfiume.blog/page?language=fr",                       "https://rafaelfiume.blog/page"),
    ("https://rafaelfiume.blog/page?locale=en-US&q=ddos",               "https://rafaelfiume.blog/page?q=ddos"),

    // ── Query: parameters sorted by key then value ─────────────────────────
    // Ensures ?a=1&b=2 and ?b=2&a=1 deduplicate to the same canonical form.
    ("https://rafaelfiume.blog/page?z=3&a=1&m=2",  "https://rafaelfiume.blog/page?a=1&m=2&z=3"),
    ("https://rafaelfiume.blog/page?b=2&a=1",       "https://rafaelfiume.blog/page?a=1&b=2"),

    // ── Query: tracking stripped then sorted ──────────────────────────────
    ("https://rafaelfiume.blog/page?utm_source=tw&z=3&a=1", "https://rafaelfiume.blog/page?a=1&z=3")

    // format: on
  ).foreach { case (input, expected) =>
    test(s"normalises $input => $expected") {
      val url = Uri.unsafeFromString(input)
      val canonicalisedUrl = Uri.unsafeFromString(expected)
      assertEquals(UrlNormaliser.canonicalise(url), canonicalisedUrl)
    }
  }
