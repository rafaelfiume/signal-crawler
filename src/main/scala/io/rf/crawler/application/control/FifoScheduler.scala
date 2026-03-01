package io.rf.crawler.application.control

import cats.effect.Temporal
import org.http4s.Uri

import scala.concurrent.duration.FiniteDuration

class FifoScheduler[F[_]: Temporal] private (source: fs2.Stream[F, Uri], dispatchInterval: FiniteDuration) extends Scheduler[F]:
  def dispatch: fs2.Stream[F, Uri] = source.metered(dispatchInterval)

object FifoScheduler:
  def make[F[_]: Temporal](
    source: fs2.Stream[F, Uri],
    dispatchInterval: FiniteDuration
  ): Scheduler[F] = FifoScheduler(source, dispatchInterval)
