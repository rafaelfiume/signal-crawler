package io.rf.crawler.application.control

import org.http4s.Uri

trait Scheduler[F[_]]:
  def dispatch: fs2.Stream[F, Uri]
