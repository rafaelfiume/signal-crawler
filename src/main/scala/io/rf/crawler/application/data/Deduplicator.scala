package io.rf.crawler.application.data

import cats.effect.{Async, Ref}
import cats.implicits.*
import org.http4s.Uri

trait Deduplicator[F[_]]:
  def hasSeen(url: Uri): F[Boolean]

object Deduplicator:

  /*
   * A simple, in-memory, atomic deduplicator.
   *
   * Tradeoffs:
   *
   * This is a well-suited component for:
   *   - Controlled domains
   *   - Short-lived tasks
   *   - Prototype / unit-testing
   *
   * It trades scalability for speed and simplicity.
   *
   * It maintains visited web-page URLs in-memory:
   *   - Crawler is constrained to a single-node solution limiting parallelism.
   *   - Number of total visited pages is bounded by the available memory.
   *      - Deduplication state is unbounded (leads to OOM).
   *   - Crucially, the deduplication state is reset when the service restarts.
   *
   * There is no support for expiring or removing deduplicated URLs.
   */
  def make[F[_]: Async](): F[Deduplicator[F]] = Ref.of(Set.empty[Uri]).map { state =>
    new Deduplicator[F]:
      override def hasSeen(url: Uri): F[Boolean] =
        state.modify { set =>
          if set.contains(url) then set -> true
          else (set + url) -> false
        }
  }
