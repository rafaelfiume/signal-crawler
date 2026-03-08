package io.rf.crawler.domain

import org.http4s.Uri

object UrlFilter:

  /**
   * Multi-domain crawl: any valid URL on any host is accepted. Structural content-type rules still apply; domain-level exclusions
   * are the responsibility of DomainBlocklist.
   */
  def accept(url: Uri): Boolean =
    UrlValidator.hasAllowedSchemes(url)
      && url.host.isDefined
      && !hasNonContentActionParam(url)
      && !isNamespacedWikiPath(url)

  // MediaWiki action/UI query params — indicate editing or metadata surfaces, not content.
  // Applies to any MediaWiki installation (Wikipedia, corporate wikis, fan wikis, etc.).
  private val NonContentActionParams: Set[String] = Set(
    "action",
    "veaction",
    "mobileaction",
    "diff",
    "oldid",
    "printable",
    "useparsoid",
    "redlink",
    "bookcmd",
    "returnto"
  )

  // Use pairs rather than params to catch bare keys (e.g. ?oldid without =).
  private[domain] def hasNonContentActionParam(url: Uri): Boolean =
    url.query.pairs.exists { case (k, _) => NonContentActionParams.contains(k) }

  // A colon in the first path segment after /wiki/ identifies a MediaWiki namespace:
  // Special:, File:, Talk:, Help:, and all their multilingual equivalents.
  // MediaWiki never percent-encodes the colon separator itself, so a literal ':'
  // check is encoding-agnostic — it works even when the namespace name is fully
  // encoded (e.g. Tamil's %e0%ae%9a...%e0%ae%bf:search).
  private[domain] def isNamespacedWikiPath(url: Uri): Boolean =
    val path = url.path.renderString
    if !path.startsWith("/wiki/") then false
    else path.stripPrefix("/wiki/").takeWhile(_ != '/').contains(':')
