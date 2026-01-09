package io.rf.crawler.emission

import cats.Applicative
import cats.implicits.*
import io.rf.crawler.domain.PageLinks
import org.typelevel.log4cats.SelfAwareStructuredLogger

trait Emitter[F[_]]:
  def emit(result: PageLinks): F[Unit]

object Emitter:
  def makeStdout[F[_]: Applicative]()(implicit logger: SelfAwareStructuredLogger[F]): Emitter[F] = new Emitter[F]:
    def emit(result: PageLinks): F[Unit] =
      logger.info(s"Page found: ${result.page}") *>
        // converts set to list and sort it for stable output (assumes performance is not an issue at least initially)
        result.links.toList.sorted.traverse_ { l => logger.info(s"  link: $l") }
