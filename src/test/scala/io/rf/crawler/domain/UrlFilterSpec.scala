package io.rf.crawler.domain

import munit.FunSuite
import org.http4s.Uri

class UrlFilterSpec extends FunSuite:

  test("accepts any valid http/https host"):
    assert(UrlFilter.accept(Uri.unsafeFromString("https://en.wikipedia.org/wiki/DDoS_attack")))
    assert(UrlFilter.accept(Uri.unsafeFromString("http://arstechnica.com/security/2023/ddos")))

  test("rejects relative URLs (no host)"):
    assert(!UrlFilter.accept(Uri.unsafeFromString("/page")))

  test("rejects invalid schemes"):
    assert(!UrlFilter.accept(Uri.unsafeFromString("mailto:test@example.com")))
    assert(!UrlFilter.accept(Uri.unsafeFromString("ftp://example.com/file")))

  test("accepts sharing-intent URLs (domain policy belongs to DomainBlocklist)"):
    val uri = Uri.unsafeFromString("https://twitter.com/intent/tweet?url=https://example.com")
    assert(UrlFilter.accept(uri))

  test("accepts donation URLs (domain policy belongs to DomainBlocklist)"):
    val uri = Uri.unsafeFromString("https://donate.wikimedia.org?wmf_source=donate")
    assert(UrlFilter.accept(uri))

  List(
    // format: off
    // ── Crawlable — no extension or HTML ──────────────────────────────────
    ("https://rafaelfiume.blog/about",           true),
    ("https://rafaelfiume.blog/page.html",       true),
    ("https://rafaelfiume.blog/page.htm",        true),

    // ── Code & styles ─────────────────────────────────────────────────────
    ("https://example.com/app.js",               false),
    ("https://example.com/app.jsx",              false),
    ("https://example.com/main.ts",              false),
    ("https://example.com/main.tsx",             false),
    ("https://example.com/style.css",            false),

    // ── Data ──────────────────────────────────────────────────────────────
    ("https://example.com/data.json",            false),
    ("https://example.com/feed.xml",             false),
    ("https://example.com/export.csv",           false),
    ("https://example.com/config.yaml",          false),
    ("https://example.com/config.yml",           false),

    // ── Documents & archives ──────────────────────────────────────────────
    ("https://example.com/report.pdf",           false),
    ("https://example.com/archive.zip",          false),
    ("https://example.com/archive.tar",          false),
    ("https://example.com/archive.gz",           false),
    ("https://example.com/archive.7z",           false),

    // ── Images ────────────────────────────────────────────────────────────
    ("https://example.com/image.png",            false),
    ("https://example.com/image.jpg",            false),
    ("https://example.com/image.jpeg",           false),
    ("https://example.com/image.gif",            false),
    ("https://example.com/image.webp",           false),
    ("https://example.com/icon.svg",             false),
    ("https://example.com/favicon.ico",          false),

    // ── Audio & video ─────────────────────────────────────────────────────
    ("https://example.com/audio.mp3",            false),
    ("https://example.com/video.mp4",            false),
    ("https://example.com/audio.wav",            false),
    ("https://example.com/audio.ogg",            false),
    ("https://example.com/video.avi",            false),
    ("https://example.com/video.mov",            false),
    ("https://example.com/video.webm",           false),

    // ── Fonts ─────────────────────────────────────────────────────────────
    ("https://example.com/font.woff",            false),
    ("https://example.com/font.woff2",           false),
    ("https://example.com/font.ttf",             false),
    ("https://example.com/font.eot",             false),

    // ── Edge cases ────────────────────────────────────────────────────────
    // Extension check is case-insensitive
    ("https://example.com/app.JS",               false),
    ("https://example.com/image.PNG",            false),
    // Extension in path segment, not final — not matched
    ("https://example.com/js/utils",             true)

    // format: on
  ).foreach { case (uriStr, expected) =>
    test(s"hasAssetExtension: $uriStr => $expected") {
      val uri = Uri.unsafeFromString(uriStr)
      assertEquals(UrlFilter.accept(uri), expected)
    }
  }

  // ─── MediaWiki non-content action params ──────────────────────────────────
  // These params appear on any MediaWiki installation, not just Wikipedia.

  List(
    // format: off
    ("https://en.wikipedia.org/wiki/DDoS_attack",                                       true),   // content page — accepted
    ("https://en.wikipedia.org/w/index.php?title=DDoS&action=edit",                     false),  // action=edit
    ("https://en.wikipedia.org/w/index.php?title=DDoS&action=history",                  false),  // action=history
    ("https://en.wikipedia.org/w/index.php?title=DDoS&action=info",                     false),  // action=info
    ("https://en.wikipedia.org/w/index.php?title=DDoS&veaction=edit",                   false),  // visual editor
    ("https://en.wikipedia.org/w/index.php?title=DDoS&mobileaction=toggle_view_mobile", false),  // mobile toggle
    ("https://en.wikipedia.org/w/index.php?title=DDoS&diff=123",                        false),  // diff view
    ("https://en.wikipedia.org/w/index.php?title=DDoS&oldid=123",                       false),  // historical revision
    ("https://en.wikipedia.org/w/index.php?title=DDoS&printable=yes",                   false),  // print view
    ("https://en.wikipedia.org/w/index.php?title=DDoS&useparsoid=0",                    false),  // parsoid toggle
    ("https://en.wikipedia.org/w/index.php?title=DDoS&redlink=1",                       false),  // red-link
    ("https://en.wikipedia.org/w/index.php?title=DDoS&returnto=Main_Page",              false),  // login returnto
    ("https://en.wikipedia.org/w/index.php?title=DDoS&bookcmd=book_creator",            false),  // book creator
    // Also applies to non-Wikipedia MediaWiki installations
    ("https://wiki.example.org/w/index.php?title=SomePage&action=edit",                 false),
    ("https://wiki.example.org/w/index.php?title=SomePage&diff=456",                    false)
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
    // Content articles — no colon in first segment after /wiki/
    ("https://en.wikipedia.org/wiki/DDoS_attack",                                   true),
    ("https://en.wikipedia.org/wiki/Denial-of-service_attack",                      true),
    ("https://ta.wikipedia.org/wiki/%e0%ae%95%e0%ae%a3%e0%ae%bf%e0%ae%a9%e0%ae%bf", true),
    ("https://wiki.example.org/wiki/SomePage",                                      true),

    // Non-/wiki/ paths — isNamespacedWikiPath is a no-op
    ("https://en.wikipedia.org/w/index.php?title=DDoS",                             true),
    ("https://en.wikipedia.org/wiki/Special:Search",                                false),
    ("https://en.wikipedia.org/wiki/Special:Random",                                false),
    ("https://en.wikipedia.org/wiki/Special:RecentChanges",                         false),
    ("https://en.wikipedia.org/wiki/Special:UserLogin",                             false),
    ("https://en.wikipedia.org/wiki/Special:CreateAccount",                         false),
    ("https://en.wikipedia.org/wiki/Special:DownloadAsPdf",                         false),
    ("https://en.wikipedia.org/wiki/Special:WhatLinksHere/DDoS",                    false),
    ("https://en.wikipedia.org/wiki/File:Stachledraht_ddos_attack.svg",             false),
    ("https://en.wikipedia.org/wiki/Talk:DDoS_attack",                              false),
    ("https://en.wikipedia.org/wiki/Wikipedia:About",                               false),

    // Tamil namespaces — namespace name is encoded, colon is literal
    ("https://ta.wikipedia.org/wiki/%e0%ae%9a%e0%ae%bf%e0%ae%b1%e0%ae%aa%e0%af%8d%e0%ae%aa%e0%af%81:search", false),
    ("https://ta.wikipedia.org/wiki/%e0%ae%b5%e0%ae%bf%e0%ae%95%e0%af%8d%e0%ae%95%e0%ae%bf%e0%ae%aa%e0%af%8d%e0%ae%aa%e0%af%80%e0%ae%9f%e0%ae%bf%e0%ae%af%e0%ae%be:%e0%ae%ae%e0%af%81%e0%ae%a4%e0%ae%b1%e0%af%8d%e0%ae%aa%e0%ae%95%e0%af%8d%e0%ae%95%e0%ae%95%e0%af%8d_%e0%ae%95%e0%ae%9f%e0%af%8d%e0%ae%9f%e0%af%81%e0%ae%b0%e0%af%88%e0%ae%95%e0%ae%b3%e0%af%8d", false),

    // Non-Wikipedia MediaWiki installation
    ("https://wiki.example.org/wiki/Special:RecentChanges",                         false),
    ("https://wiki.example.org/wiki/File:Logo.png",                                 false)
    // format: on
  ).foreach { case (uriStr, expected) =>
    test(s"MediaWiki namespace path filter: $uriStr → $expected") {
      val uri = Uri.unsafeFromString(uriStr)
      assertEquals(UrlFilter.accept(uri), expected)
    }
  }
