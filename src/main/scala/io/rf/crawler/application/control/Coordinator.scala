package io.rf.crawler.application.control

import cats.effect.{Async, Ref}
import cats.implicits.*
import org.http4s.Uri

import scala.collection.immutable.{Queue, TreeSet}
import scala.concurrent.duration.*

class Coordinator[F[_]: Async] private (
  state: Ref[F, Coordinator.State],
  cooldown: Long
) extends Scheduler[F]:

  def dispatch: fs2.Stream[F, Uri] = fs2.Stream.repeatEval(emit()).unNone

  private[control] def admit(url: Uri): F[Unit] =
    for
      now <- Async[F].monotonic.map(_.toMillis)
      _ <- state.update { st =>
        val urlDomain = domain(url)
        st.domainIndex.get(urlDomain) match
          case None =>
            val domainQueue = Coordinator.DomainQueue(now, urlDomain, Queue(url))
            st.copy(
              schedule = st.schedule + domainQueue,
              domainIndex = st.domainIndex + (urlDomain -> domainQueue)
            )
          case Some(domainQueue) =>
            val updatedDomainQueue = domainQueue.copy(urls = domainQueue.urls :+ url)
            st.copy(
              schedule = st.schedule - domainQueue + updatedDomainQueue,
              domainIndex = st.domainIndex + (urlDomain -> updatedDomainQueue)
            )
      }
      _ <- Async[F].delay(println(s"[ADMIT]    ${fmt(now)} $url"))
    yield ()

  private def emit(): F[Option[Uri]] =
    Async[F].monotonic.flatMap { now =>
      val nowMs = now.toMillis
      state.get.flatMap { st =>
        st.schedule.headOption match
          case None =>
            Async[F].delay(println(s"[IDLE]     ${fmt(nowMs)} no candidates, sleeping 1s")) >>
              Async[F].sleep(1.second).as(None)

          case Some(dq) =>
            val wait = dq.nextEligibleAt - nowMs
            if wait > 0 then
              Async[F].delay(println(s"[WAIT]     ${fmt(nowMs)} next=${dq.domain} eligible in ${wait}ms")) >>
                Async[F].sleep(wait.millis).as(None)
            else dequeue(dq, nowMs)
      }
    }

  private def dequeue(dq: Coordinator.DomainQueue, now: Long): F[Option[Uri]] =
    state
      .modify { st =>
        val (url, remaining) = dq.urls.dequeue
        val scheduleWithout = st.schedule - dq
        val indexWithout = st.domainIndex - dq.domain

        if remaining.isEmpty then
          val exhausted = dq.copy(nextEligibleAt = now + cooldown, urls = Queue.empty)
          st.copy(
            schedule = scheduleWithout,
            st.domainIndex + (dq.domain -> exhausted)
          ) -> url
        else
          val next = dq.copy(nextEligibleAt = now + cooldown, urls = remaining)
          st.copy(
            schedule = scheduleWithout + next,
            domainIndex = indexWithout + (dq.domain -> next)
          ) -> url
      }
      .flatMap { url =>
        Async[F]
          .delay(println(s"[EMIT]     ${fmt(now)} $url (next ${dq.domain} eligible at ${fmt(now + cooldown)})"))
          .as(Some(url))
      }

  private def domain(url: Uri): String =
    url.authority.map(_.host.value).getOrElse("")

  private def fmt(ms: Long): String =
    val s = ms / 1000
    val m = s / 60
    val h = m / 60
    f"${h % 24}%02d:${m % 60}%02d:${s % 60}%02d.${ms % 1000}%03d"

object Coordinator:

  def make[F[_]: Async](
    source: fs2.Stream[F, Uri],
    cooldown: FiniteDuration
  ): F[Scheduler[F]] =
    for
      state <- Ref.of[F, State](State(TreeSet.empty, Map.empty))
      coord = Coordinator(state, cooldown.toMillis)
    yield new Scheduler[F]:
      def dispatch: fs2.Stream[F, Uri] =
        source.evalMap(coord.admit).drain.merge(coord.dispatch)

  private[Coordinator] case class DomainQueue(
    nextEligibleAt: Long,
    domain: String,
    urls: Queue[Uri]
  )

  private[Coordinator] case class State(
    schedule: TreeSet[DomainQueue],
    domainIndex: Map[String, DomainQueue]
  )

  given Ordering[DomainQueue] =
    Ordering.by(dq => (dq.nextEligibleAt, dq.domain))
