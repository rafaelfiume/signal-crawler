package io.rf.crawler.ingress

import cats.effect.Temporal
import cats.effect.std.Queue
import cats.implicits.*

trait Ingress[F[_], T]:
  def publish(events: T): F[Unit]
  def publish(events: Vector[T]): F[Unit]
  def stream: fs2.Stream[F, T]

object Ingress:
  def makeQueuedIngress[F[_]: Temporal, T](queue: Queue[F, T]): Ingress[F, T] =
    new Ingress[F, T]:
      override def publish(event: T): F[Unit] = queue.offer(event)
      override def publish(events: Vector[T]): F[Unit] = events.traverse_(queue.offer)
      override def stream: fs2.Stream[F, T] = fs2.Stream.fromQueueUnterminated(queue)
