package io.rf.crawler.core

import io.rf.crawler.domain.UrlValidator
import org.http4s.Uri

trait UrlFilter:
  def accept(url: Uri): Boolean

object UrlFilter:
  def makeForSeed(seed: Uri): UrlFilter =
    // Initialising UrlFilter with an invalid seed is considered a bug (irrecoverable error), thus exception is thrown.
    if !UrlValidator.isValid(seed) then throw IllegalArgumentException("UrlFilter must be initialised with a valid seed")
    else
      new UrlFilter:
        override def accept(url: Uri): Boolean =
          if UrlValidator.hasAllowedSchemes(url) then seed.host == url.host
          else false
