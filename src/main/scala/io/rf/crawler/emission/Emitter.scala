package io.rf.crawler.emission

import cats.Monad
import cats.implicits.*
import io.rf.crawler.domain.PageLinks
import org.typelevel.log4cats.SelfAwareStructuredLogger

trait Emitter[F[_]]:
  def emit(result: PageLinks): F[Unit]

object Emitter:
  def makeStdout[F[_]: Monad]()(implicit logger: SelfAwareStructuredLogger[F]): Emitter[F] = new Emitter[F]:
    def emit(result: PageLinks): F[Unit] = {
      val sb = StringBuilder()
      sb.append(s"Page found: ${result.page}\n")
      result.links.toList.sorted.foreach(l => sb.append(s"  link: $l\n"))
      sb.toString()
    }.pure[F].flatMap(msg => logger.info(msg))
