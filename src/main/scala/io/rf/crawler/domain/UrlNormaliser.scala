package io.rf.crawler.domain

import org.http4s.{Query, Uri}

/*
 * URL normaliser for effective deduplication.
 *
 * Normalisation steps (in order):
 *   1. Auth    — strip trailing dot from host, remove default ports (:80 for http, :443 for https)
 *   2. Path    — collapse duplicate slashes, strip trailing slash
 *   3. Encoding— decode unreserved chars (%41 → A), uppercase hex digits (%e0 → %E0) per RFC 3986 §6.2.2.1
 *   4. Query   — strip tracking parameters, sort remaining params by key then value
 *   5. Fragment— remove (never sent to server; no canonical form)
 *
 * Intentionally omitted (see inline notes):
 *   - http → https scheme upgrade (server-dependent; defer to observed redirect behaviour)
 *   - www. prefix normalisation (different subdomain; may genuinely differ in practice)
 *   - Path case-folding (paths are case-sensitive per RFC 3986; /wiki/DDoS != /wiki/ddos)
 *
 * Changes to the normalisation rules affect deduplication and crawl cardinality.
 */
object UrlNormaliser:

  // RFC 3986 §2.3 — characters that must never be percent-encoded.
  private val Unreserved: Set[Char] =
    (('A' to 'Z') ++ ('a' to 'z') ++ ('0' to '9') ++ "-._~").toSet

  /*
   * Query parameters that carry tracking/analytics metadata but do not identify a distinct resource.
   * Strip before deduplication to avoid re-fetching the same page under different campaign tags.
   */
  private val TrackingParams: Set[String] = Set(
    "utm_source",
    "utm_medium",
    "utm_campaign",
    "utm_term",
    "utm_content",
    "ref",
    "source",
    "icid",
    "ocid",
    "wt.mc_id",
    "s_cid",
    "cid",
    "fbclid",
    "gclid",
    "msclkid",
    "mc_cid",
    "mc_eid",
    // Locale selectors — select a rendering variant, not a distinct resource.
    // e.g. ?l=ru and ?l=pt-BR on apps.apple.com; ?lang=en on various sites.
    "l",
    "lang",
    "language",
    "locale"
  )

  def canonicalise(url: Uri): Uri =
    url.normalisedAuth.normalisePath.normalisePercentEncoding.normaliseQuery.normaliseFragment

  extension (url: Uri)
    def normalisedAuth: Uri =
      val norm = url.authority.map { auth =>
        val host = Uri.Host.fromString(auth.host.value.stripSuffix(".")).getOrElse(auth.host)
        val port = (url.scheme, auth.port) match
          case (Some(Uri.Scheme.http), Some(80))   => None
          case (Some(Uri.Scheme.https), Some(443)) => None
          case _                                   => auth.port
        auth.copy(host = host, port = port)
      }
      url.copy(authority = norm)

    def normalisePath: Uri =
      val collapsed = url.path.renderString.split("/").filterNot(_.isEmpty).mkString("/")
      val normalised = Uri.Path.unsafeFromString(if collapsed.isEmpty then "" else s"/$collapsed")
      url.copy(path = normalised)

    /*
     * Operates on the encoded path string so that:
     *   - unreserved chars unnecessarily encoded as %XX are decoded (e.g. %41 → A)
     *   - hex digits in non-unreserved sequences are uppercased (%e0 → %E0)
     * This is applied after normalisePath so the collapsed structure is already stable.
     */
    def normalisePercentEncoding: Uri =
      val normPath = Uri.Path.unsafeFromString(normaliseEncoding(url.path.renderString))
      url.copy(path = normPath)

    def normaliseQuery: Uri =
      val stripped = url.query.pairs.filterNot { case (k, _) => TrackingParams.contains(k) }
      val sorted = stripped.sortBy { case (k, v) => (k, v.getOrElse("")) }
      url.copy(query = Query(sorted*))

    def normaliseFragment: Uri = url.copy(fragment = None)

  // RFC 3986 §6.2.2.1 percent-encoding normalisation over an already-encoded string.
  private def normaliseEncoding(s: String): String =
    val sb = new StringBuilder(s.length)
    var i = 0
    while i < s.length do
      if s(i) == '%' && i + 2 < s.length then
        val hex = s.substring(i + 1, i + 3)
        scala.util.Try(Integer.parseInt(hex, 16)).toOption match
          case Some(code) if code <= 127 && Unreserved.contains(code.toChar) =>
            // Decode unnecessarily encoded unreserved character.
            sb.append(code.toChar)
            i += 3
          case Some(_) =>
            // Keep encoded but normalise hex case to uppercase.
            sb.append('%').append(hex.toUpperCase)
            i += 3
          case None =>
            // Malformed %XX — leave as-is.
            sb.append(s(i))
            i += 1
      else
        sb.append(s(i))
        i += 1
    sb.toString
