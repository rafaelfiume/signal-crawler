package io.rf.crawler.core

import cats.effect.{Async, Ref}
import cats.implicits.*
import org.http4s.Uri

trait Deduplicator[F[_]]:
  def hasSeen(url: Uri): F[Boolean]

object Deduplicator:
  def make[F[_]: Async](): F[Deduplicator[F]] = Ref.of(Set.empty[Uri]).map { state =>
    new Deduplicator[F]:
      override def hasSeen(url: Uri): F[Boolean] =
        state.modify { set =>
          if set.contains(url) then set -> true
          else (set + url) -> false
        }
  }
