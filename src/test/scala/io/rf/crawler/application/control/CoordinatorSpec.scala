package io.rf.crawler.application.control

import cats.effect.IO
import cats.effect.testkit.TestControl
import fs2.Stream
import munit.CatsEffectSuite
import org.http4s.Uri
import org.http4s.syntax.literals.*

import scala.concurrent.duration.*

class CoordinatorSpec extends CatsEffectSuite:

  test("cooldown enforces spacing between same-domain URLs"):
    val url1 = uri"https://a.com/1"
    val url2 = uri"https://a.com/2"
    val ingress = Stream(url1, url2)
    val cooldown = 1.hour

    val program =
      for
        scheduler <- Coordinator.make[IO](ingress, cooldown)
        result <- scheduler.dispatch
          .evalMap(url => IO.monotonic.map(t => (url, t)))
          .take(2)
          .compile
          .toList
      yield result

    TestControl.executeEmbed(program).map { emissions =>
      val List((firstUrl, t1), (secondUrl, t2)) = emissions
      assertEquals(firstUrl, url1)
      assertEquals(secondUrl, url2)
      assert(t2 - t1 >= cooldown)
    }

  test("cooldown on one domain does not affect eligibility of another"):
    val a1 = uri"https://a.com/1"
    val a2 = uri"https://a.com/2"
    val b1 = uri"https://b.com/1"
    val cooldown = 1.hour
    val ingress = Stream(a1, b1, a2)

    val program =
      for
        scheduler <- Coordinator.make[IO](ingress, cooldown)
        result <- scheduler.dispatch
          .evalMap(url => IO.monotonic.map(t => (url, t)))
          .take(3)
          .compile
          .toList
      yield result

    TestControl.executeEmbed(program).map { emissions =>
      val List((_, tA1), (_, tB1), (_, tA2)) = emissions
      // b.com must not wait for a.com's cooldown
      assert(tB1 - tA1 < cooldown, "b.com was incorrectly held back by a.com's cooldown")
      // a.com/2 must respect a.com's cooldown
      assert(tA2 - tA1 >= cooldown, "a.com/2 was emitted before cooldown elapsed")
    }

  test("domains with equal eligibility interleave fairly"):
    val a1 = uri"https://a.com/1"
    val a2 = uri"https://a.com/2"
    val b1 = uri"https://b.com/1"
    val b2 = uri"https://b.com/2"
    val ingress = Stream(a1, b1, a2, b2)

    val program =
      for
        scheduler <- Coordinator.make[IO](ingress, 1.hour)
        result <- scheduler.dispatch.take(4).compile.toList
      yield result

    TestControl.executeEmbed(program).map { result =>
      // Both domains must appear before either domain repeats
      val first = result.take(2).map(_.host.get.value).toSet
      val second = result.drop(2).map(_.host.get.value).toSet
      assertEquals(first, Set("a.com", "b.com"))
      assertEquals(second, Set("a.com", "b.com"))
    }

  test("URLs within a domain are emitted in FIFO order"):
    val urls = List(
      uri"https://a.com/1",
      uri"https://a.com/2",
      uri"https://a.com/3"
    )
    val ingress = Stream.emits(urls)

    val program =
      for
        scheduler <- Coordinator.make[IO](ingress, 1.hour)
        result <- scheduler.dispatch.take(urls.size.toLong).compile.toList
      yield result

    TestControl.executeEmbed(program).map { result =>
      assertEquals(result, urls)
    }

  test("empty source emits nothing"):
    val program =
      for
        scheduler <- Coordinator.make[IO](Stream.empty, 1.second)
        result <- scheduler.dispatch.interruptAfter(1.minutes).compile.toList
      yield result

    TestControl.executeEmbed(program).map { result =>
      assertEquals(result, Nil)
    }
