package io.rf.crawler.domain

import munit.FunSuite
import org.http4s.Uri

class UrlFilterSpec extends FunSuite:

  // ─── MediaWiki non-content action params ──────────────────────────────────
  // These params appear on any MediaWiki installation, not just Wikipedia.

  List(
    // format: off
    ("https://en.wikipedia.org/wiki/DDoS_attack",                             true),   // content page — accepted
    ("https://en.wikipedia.org/w/index.php?title=DDoS&action=edit",           false),  // action=edit
    ("https://en.wikipedia.org/w/index.php?title=DDoS&action=history",        false),  // action=history
    ("https://en.wikipedia.org/w/index.php?title=DDoS&action=info",           false),  // action=info
    ("https://en.wikipedia.org/w/index.php?title=DDoS&veaction=edit",         false),  // visual editor
    ("https://en.wikipedia.org/w/index.php?title=DDoS&mobileaction=toggle_view_mobile", false), // mobile toggle
    ("https://en.wikipedia.org/w/index.php?title=DDoS&diff=123",              false),  // diff view
    ("https://en.wikipedia.org/w/index.php?title=DDoS&oldid=123",             false),  // historical revision
    ("https://en.wikipedia.org/w/index.php?title=DDoS&printable=yes",         false),  // print view
    ("https://en.wikipedia.org/w/index.php?title=DDoS&useparsoid=0",         false),  // parsoid toggle
    ("https://en.wikipedia.org/w/index.php?title=DDoS&redlink=1",             false),  // red-link
    ("https://en.wikipedia.org/w/index.php?title=DDoS&returnto=Main_Page",    false),  // login returnto
    ("https://en.wikipedia.org/w/index.php?title=DDoS&bookcmd=book_creator",  false),  // book creator
    // Also applies to non-Wikipedia MediaWiki installations
    ("https://wiki.example.org/w/index.php?title=SomePage&action=edit",       false),
    ("https://wiki.example.org/w/index.php?title=SomePage&diff=456",          false)
    // format: on
  ).foreach { case (uriStr, expected) =>
    test(s"MediaWiki action param filter: $uriStr → $expected") {
      val uri = Uri.unsafeFromString(uriStr)
      assertEquals(UrlFilter.accept(uri), expected)
    }
  }

  // ─── MediaWiki namespace path filtering ───────────────────────────────────
  // A colon in the first path segment after /wiki/ identifies a namespace.
  // Applies to any MediaWiki installation; encoding-agnostic.

  List(
    // format: off
    // English namespaces
    ("https://en.wikipedia.org/wiki/Special:Search",                    false),
    ("https://en.wikipedia.org/wiki/Special:Random",                    false),
    ("https://en.wikipedia.org/wiki/Special:RecentChanges",             false),
    ("https://en.wikipedia.org/wiki/Special:UserLogin",                 false),
    ("https://en.wikipedia.org/wiki/Special:CreateAccount",             false),
    ("https://en.wikipedia.org/wiki/Special:DownloadAsPdf",             false),
    ("https://en.wikipedia.org/wiki/Special:WhatLinksHere/DDoS",        false),
    ("https://en.wikipedia.org/wiki/File:Stachledraht_ddos_attack.svg", false),
    ("https://en.wikipedia.org/wiki/Talk:DDoS_attack",                  false),
    ("https://en.wikipedia.org/wiki/Wikipedia:About",                   false),
    // Tamil namespaces — namespace name is encoded, colon is literal
    ("https://ta.wikipedia.org/wiki/%e0%ae%9a%e0%ae%bf%e0%ae%b1%e0%ae%aa%e0%af%8d%e0%ae%aa%e0%af%81:search",       false),
    ("https://ta.wikipedia.org/wiki/%e0%ae%b5%e0%ae%bf%e0%ae%95%e0%af%8d%e0%ae%95%e0%ae%bf%e0%ae%aa%e0%af%8d%e0%ae%aa%e0%af%80%e0%ae%9f%e0%ae%bf%e0%ae%af%e0%ae%be:%e0%ae%ae%e0%af%81%e0%ae%a4%e0%ae%b1%e0%af%8d%e0%ae%aa%e0%ae%95%e0%af%8d%e0%ae%95%e0%ae%95%e0%af%8d_%e0%ae%95%e0%ae%9f%e0%af%8d%e0%ae%9f%e0%af%81%e0%ae%b0%e0%af%88%e0%ae%95%e0%ae%b3%e0%af%8d", false),
    // Non-Wikipedia MediaWiki installation
    ("https://wiki.example.org/wiki/Special:RecentChanges",             false),
    ("https://wiki.example.org/wiki/File:Logo.png",                     false),
    // Content articles — no colon in first segment after /wiki/
    ("https://en.wikipedia.org/wiki/DDoS_attack",                       true),
    ("https://en.wikipedia.org/wiki/Denial-of-service_attack",          true),
    ("https://ta.wikipedia.org/wiki/%e0%ae%95%e0%ae%a3%e0%ae%bf%e0%ae%a9%e0%ae%bf", true),
    ("https://wiki.example.org/wiki/SomePage",                          true),
    // Non-/wiki/ paths — isNamespacedWikiPath is a no-op
    ("https://en.wikipedia.org/w/index.php?title=DDoS",                 true)
    // format: on
  ).foreach { case (uriStr, expected) =>
    test(s"MediaWiki namespace path filter: $uriStr → $expected") {
      val uri = Uri.unsafeFromString(uriStr)
      assertEquals(UrlFilter.accept(uri), expected)
    }
  }

  test("accepts any valid http/https host"):
    assert(UrlFilter.accept(Uri.unsafeFromString("https://en.wikipedia.org/wiki/DDoS_attack")))
    assert(UrlFilter.accept(Uri.unsafeFromString("http://arstechnica.com/security/2023/ddos")))

  test("rejects relative URLs (no host)"):
    assert(!UrlFilter.accept(Uri.unsafeFromString("/page")))

  test("rejects invalid schemes"):
    assert(!UrlFilter.accept(Uri.unsafeFromString("mailto:test@example.com")))
    assert(!UrlFilter.accept(Uri.unsafeFromString("ftp://example.com/file")))

  test("rejects MediaWiki action params"):
    val uri = Uri.unsafeFromString("https://en.wikipedia.org/w/index.php?title=DDoS&action=edit")
    assert(!UrlFilter.accept(uri))

  test("rejects MediaWiki namespaced paths"):
    val uri = Uri.unsafeFromString("https://en.wikipedia.org/wiki/Special:Search")
    assert(!UrlFilter.accept(uri))

  // ─── Domain-level exclusions are NOT the filter's responsibility ──────────
  // Sharing intents, donation pages, and blocked domains are handled by
  // DomainBlocklist. UrlFilter does not duplicate those rules.

  test("accepts sharing-intent URLs (domain policy belongs to DomainBlocklist)"):
    val uri = Uri.unsafeFromString("https://twitter.com/intent/tweet?url=https://example.com")
    assert(UrlFilter.accept(uri))

  test("accepts donation URLs (domain policy belongs to DomainBlocklist)"):
    val uri = Uri.unsafeFromString("https://donate.wikimedia.org?wmf_source=donate")
    assert(UrlFilter.accept(uri))
