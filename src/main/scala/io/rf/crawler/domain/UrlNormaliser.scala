package io.rf.crawler.domain

import org.http4s.Uri

/*
 * A minimalist URL normaliser necessary for effective deduplication.
 *
 * See UrlNormaliserSpec and DeduplicatorSpec for more details.
 *
 * Note:
 *   - no 'http' -> 'https' canonicalisation (might lead to duplicates, but business rule dependent)
 *   - no tracking-related query parameters removal (might lead to duplicates; could be added in further iterations)
 *
 * Changes to the normalisation rules may affect deduplication and crawl cardinality.
 */
object UrlNormaliser:
  def canonicalise(url: Uri): Uri = url.normalisedAuth.normalisePath.normaliseFragment

  extension (url: Uri)
    def normalisedAuth: Uri =
      val normalisedAuth = url.authority.map { auth =>
        val normalisedHost = Uri.Host.fromString(auth.host.value.stripSuffix(".")).getOrElse(auth.host)
        val normalisedPort = (url.scheme, auth.port) match
          case ((Some(Uri.Scheme.http), Some(80)))   => None
          case ((Some(Uri.Scheme.https), Some(443))) => None
          case _                                     => auth.port
        auth.copy(host = normalisedHost, port = normalisedPort)
      }
      url.copy(authority = normalisedAuth)

    def normalisePath: Uri =
      val path = url.path.renderString
      val collapsed = path.split("/").filterNot(_.isEmpty).mkString("/")
      val lowercase = collapsed.toLowerCase
      val normalised = Uri.Path.unsafeFromString(if lowercase.isEmpty then "" else s"/$lowercase")
      url.copy(path = normalised)

    def normaliseFragment: Uri = url.copy(fragment = None)
