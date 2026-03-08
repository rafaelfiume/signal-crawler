package io.rf.crawler.domain

import org.http4s.Uri

object DomainBlocklist:

  /**
   * A hardcoded blocklist covering domains that are either:
   *   - high-volume but low-signal for general crawls (Wikipedia, social networks)
   *   - not crawlable in any meaningful way (login walls, tracking, ad networks)
   *   - actively hostile to crawlers (rate-limiting, CAPTCHAs, legal risk)
   *
   * Matching rules:
   *   - Registered domain match: blocking "wikipedia.org" covers all subdomains (en.wikipedia.org, ta.wikipedia.org,
   *     donate.wikimedia.org, etc.)
   *   - TLD match: blocking "gov" covers all government domains regardless of country (cisa.gov, nist.gov, gov.uk, etc.)
   *
   * Future: load from config file (HOCON/YAML) so operators can extend at runtime.
   */

  def isBlocked(url: Uri): Boolean =
    url.host.map(_.value.toLowerCase.stripSuffix(".")).exists { host =>
      BlockedDomains.exists(blocked => host == blocked || host.endsWith(s".$blocked"))
      || hasBlockedTld(host)
    }

  private lazy val BlockedDomains: Set[String] =
    Encyclopaedias ++
      SocialNetworks ++
      JobBoards ++
      VideoStreaming ++
      AppStores ++
      CodeHosting ++
      Ecommerce ++
      AdNetworks ++
      Infrastructure ++
      SearchEngines ++
      UrlShorteners ++
      ArchiveServices

  // ─── TLD blocking ─────────────────────────────────────────────────────────
  // Two distinct matching strategies:
  //
  // BlockedTldLabels — blocks any host containing the label anywhere in its
  //   hierarchy. "gov" blocks "cisa.gov", "nvd.nist.gov", AND "ncsc.gov.uk"
  //   because "gov" appears as a label in all three regardless of position.
  //
  // BlockedFinalTlds — blocks only when the label is the final component.
  //   "onion" must not match "polygonal.com" (contains "on" but unrelated);
  //   checking as the final label is precise enough.
  //
  // .edu is intentionally excluded: academic and university CS sites are
  // often high-quality signal. Operators can add it if desired.

  private def hasBlockedTld(host: String): Boolean =
    val labels = host.split('.').toSet
    BlockedTldLabels.exists(labels.contains) ||
    BlockedFinalTlds.exists(tld => host == tld || host.endsWith(s".$tld"))

  private lazy val BlockedTldLabels: Set[String] = Set(
    "gov", // government — blocks cisa.gov, nvd.nist.gov, ncsc.gov.uk, etc.
    "mil" // military — same label-presence reasoning
  )

  private lazy val BlockedFinalTlds: Set[String] = Set(
    "onion" // Tor hidden services — unreachable from the open web
  )

  // ─── Encyclopaedias & wikis ────────────────────────────────────────────────
  // High link-density but mostly internal cross-references; crawling multiplies
  // queue size rapidly without adding much topical signal.
  private val Encyclopaedias = Set(
    "wikipedia.org",
    "wikidata.org",
    "wikimedia.org",
    "wikisource.org",
    "wikibooks.org",
    "wikinews.org",
    "wikiquote.org",
    "wikivoyage.org",
    "wiktionary.org",
    "wikiversity.org",
    "mediawiki.org",
    "wmflabs.org",
    "wmcloud.org"
  )

  // ─── Social networks & UGC platforms ──────────────────────────────────────
  // Require login for most content; sharing-intent URLs pollute the queue;
  // robots.txt typically disallows crawlers anyway.
  private val SocialNetworks = Set(
    "twitter.com",
    "x.com",
    "facebook.com",
    "instagram.com",
    "linkedin.com",
    "reddit.com",
    "pinterest.com",
    "tumblr.com",
    "tiktok.com",
    "snapchat.com",
    "threads.net",
    "bsky.app",
    "mastodon.social",
    "vk.com",
    "weibo.com",
    "discord.com",
    "discord.gg",
    "slack.com",
    "telegram.org",
    "t.me"
  )

  // ─── Job boards & recruitment ──────────────────────────────────────────────
  // Listings are ephemeral, login-walled, or JavaScript-rendered.
  private val JobBoards = Set(
    "linkedin.com", // also in SocialNetworks; listed here for intent clarity
    "indeed.com",
    "glassdoor.com",
    "monster.com",
    "ziprecruiter.com",
    "simplyhired.com",
    "careerbuilder.com",
    "dice.com",
    "lever.co",
    "greenhouse.io",
    "workday.com",
    "jobs.apple.com",
    "careers.google.com"
  )

  // ─── Video & streaming platforms ──────────────────────────────────────────
  // Binary content; no useful text to index; often behind sign-in.
  private val VideoStreaming = Set(
    "youtube.com",
    "youtu.be",
    "vimeo.com",
    "twitch.tv",
    "dailymotion.com",
    "netflix.com",
    "primevideo.com",
    "disneyplus.com",
    "hulu.com",
    "spotify.com",
    "soundcloud.com"
  )

  // ─── App stores ───────────────────────────────────────────────────────────
  // Storefront pages are locale-sharded (e.g. /us/, /lk/, /nr/), JavaScript-heavy,
  // and contain no crawlable content beyond app metadata.
  private val AppStores = Set(
    "apps.apple.com",
    "play.google.com",
    "appgallery.huawei.com",
    "microsoft.com/store",
    "galaxy.store"
  )

  // ─── Code hosting & developer tooling ─────────────────────────────────────
  // Issue trackers, PR pages, and CI dashboards generate enormous link graphs
  // with low information density relative to crawl cost.
  private val CodeHosting = Set(
    "github.com",
    "gitlab.com",
    "bitbucket.org",
    "sourceforge.net",
    "codepen.io",
    "jsfiddle.net",
    "replit.com"
  )

  // ─── E-commerce & retail ──────────────────────────────────────────────────
  // Session-dependent URLs; personalised content; aggressive bot-detection.
  private val Ecommerce = Set(
    "amazon.com",
    "amazon.co.uk",
    "ebay.com",
    "etsy.com",
    "shopify.com",
    "alibaba.com",
    "aliexpress.com",
    "walmart.com"
  )

  // ─── Ad networks & tracking infrastructure ────────────────────────────────
  // No content; exist solely to track users; fetching causes legal/privacy risk.
  private val AdNetworks = Set(
    "doubleclick.net",
    "googlesyndication.com",
    "googleadservices.com",
    "adnxs.com",
    "outbrain.com",
    "taboola.com",
    "criteo.com",
    "scorecardresearch.com",
    "quantserve.com",
    "moatads.com"
  )

  // ─── CDN, infrastructure & utility endpoints ──────────────────────────────
  // Serve assets (JS, CSS, fonts, images), not documents.
  private val Infrastructure = Set(
    "cloudflare.com",
    "fastly.net",
    "akamaihd.net",
    "akamai.net",
    "googleusercontent.com",
    "gstatic.com",
    "googleapis.com",
    "bootstrapcdn.com",
    "cloudfront.net",
    "cdn.jsdelivr.net",
    "unpkg.com"
  )

  // ─── Search engines ───────────────────────────────────────────────────────
  // Result pages are query-dependent and have no stable canonical form.
  private val SearchEngines = Set(
    "google.com",
    "google.co.uk",
    "bing.com",
    "yahoo.com",
    "duckduckgo.com",
    "baidu.com",
    "yandex.com",
    "yandex.ru"
  )

  // ─── URL shorteners ───────────────────────────────────────────────────────
  // Redirect to another domain; the target is what matters. A redirect-following
  // fetcher will eventually land on the real URL, so no need to pre-admit these.
  private val UrlShorteners = Set(
    "bit.ly",
    "tinyurl.com",
    "t.co",
    "ow.ly",
    "buff.ly",
    "rebrand.ly",
    "short.io",
    "is.gd",
    "cutt.ly"
  )

  // ─── Web archive & citation services ──────────────────────────────────────
  // Archived pages exist at unique timestamped URLs, generating millions of
  // distinct entries that exhaust the seen-set without adding topical signal.
  private val ArchiveServices = Set(
    "web.archive.org",
    "webcitation.org",
    "archive.ph",
    "archive.today",
    "cachedview.nl"
  )
