package io.rf.crawler.domain

import org.http4s.Uri
import org.http4s.Uri.Scheme

object UrlValidator:

  def hasHost(uri: Uri): Boolean = uri.host.nonEmpty

  def hasAllowedSchemes(uri: Uri): Boolean =
    Set(Scheme.http, Scheme.https).exists(uri.scheme.contains(_))

  def isValid(uri: Uri): Boolean = hasHost(uri) && hasAllowedSchemes(uri)
