package io.rf.crawler.domain

import org.http4s.Uri

final case class PageLinks(page: Uri, links: Set[Uri])
