package io.rf.crawler.application.data

import cats.effect.{Ref, Temporal}
import cats.implicits.*

import scala.concurrent.duration.{FiniteDuration, *}

trait WorkTracker[F[_]]:
  def track(): F[Unit]
  def track(tasks: Long): F[Unit]
  def completed(): F[Unit]
  def haltSignal: fs2.Stream[F, Boolean]

object WorkTracker:

  def make[F[_]: Temporal](haltingCheckInterval: FiniteDuration = 1.second): F[WorkTracker[F]] =
    Ref.of[F, Long](0L).map { ref =>
      new WorkTracker[F]:
        override def track(): F[Unit] = ref.update(_ + 1)

        override def track(tasks: Long): F[Unit] = ref.update(_ + tasks)

        override def completed(): F[Unit] = ref.update(_ - 1)

        override def haltSignal: fs2.Stream[F, Boolean] =
          fs2.Stream
            .repeatEval(ref.get)
            .metered(haltingCheckInterval)
            .dropWhile(_ != 0)
            .take(1)
            .as(true)
    }
