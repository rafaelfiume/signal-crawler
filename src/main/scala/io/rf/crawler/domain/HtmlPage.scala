package io.rf.crawler.domain

import org.http4s.Uri

final case class HtmlPage(url: Uri, content: String)
