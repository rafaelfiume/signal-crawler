package io.rf.crawler.domain

import munit.FunSuite
import org.http4s.Uri

class DomainBlocklistSpec extends FunSuite:

  // helper
  private def isBlocked(uriStr: String): Boolean =
    DomainBlocklist.isBlocked(Uri.unsafeFromString(uriStr))

  // ─── Not blocked ───────────────────────────────────────────────────────────

  test("does not block an ordinary content domain"):
    assert(!isBlocked("https://rafaelfiume.blog/about"))

  test("does not block an unknown domain"):
    assert(!isBlocked("https://arstechnica.com/security/ddos"))

  // ─── Encyclopaedias & wikis ────────────────────────────────────────────────

  test("blocks english wikipedia"):
    assert(isBlocked("https://en.wikipedia.org/wiki/DDoS_attack"))

  test("blocks non-english wikipedia subdomain"):
    assert(isBlocked("https://ta.wikipedia.org/wiki/%e0%ae%95%e0%ae%a3%e0%ae%bf%e0%ae%a9%e0%ae%bf"))

  test("blocks wikidata"):
    assert(isBlocked("https://www.wikidata.org/wiki/Q131172"))

  test("blocks wikimedia foundation"):
    assert(isBlocked("https://foundation.wikimedia.org/wiki/Policy:Privacy_policy"))

  test("blocks donate.wikimedia.org"):
    assert(isBlocked("https://donate.wikimedia.org?wmf_source=donate"))

  // ─── Social networks ──────────────────────────────────────────────────────

  test("blocks twitter/X"):
    assert(isBlocked("https://twitter.com/intent/tweet?url=https://example.com"))
    assert(isBlocked("https://x.com/home"))

  test("blocks facebook"):
    assert(isBlocked("https://www.facebook.com/sharer/sharer.php"))

  test("blocks linkedin"):
    assert(isBlocked("https://www.linkedin.com/in/someprofile"))

  test("blocks reddit"):
    assert(isBlocked("https://www.reddit.com/r/netsec"))

  test("blocks bluesky"):
    assert(isBlocked("https://bsky.app/intent/compose?text=hello"))

  // ─── Job boards ───────────────────────────────────────────────────────────

  test("blocks indeed"):
    assert(isBlocked("https://indeed.com/jobs?q=scala"))

  test("blocks glassdoor"):
    assert(isBlocked("https://www.glassdoor.com/Reviews/reviews.htm"))

  test("blocks greenhouse job listings"):
    assert(isBlocked("https://boards.greenhouse.io/somecompany/jobs/123"))

  // ─── Video & streaming ────────────────────────────────────────────────────

  test("blocks youtube"):
    assert(isBlocked("https://www.youtube.com/watch?v=abc"))

  test("blocks youtube short link"):
    assert(isBlocked("https://youtu.be/abc"))

  test("blocks vimeo"):
    assert(isBlocked("https://vimeo.com/123456"))

  // ─── App stores ───────────────────────────────────────────────────────────

  test("blocks Apple App Store"):
    assert(isBlocked("https://apps.apple.com/us/iphone/today"))

  test("blocks Apple App Store with locale query param"):
    assert(isBlocked("https://apps.apple.com/us/iphone/today?l=ru"))

  test("blocks Apple App Store country-sharded subdirectory"):
    assert(isBlocked("https://apps.apple.com/lk/iphone/today"))

  test("blocks Google Play Store"):
    assert(isBlocked("https://play.google.com/store/apps/details?id=com.example"))

  test("blocks Huawei AppGallery"):
    assert(isBlocked("https://appgallery.huawei.com/app/C12345"))

  // ─── Code hosting ─────────────────────────────────────────────────────────

  test("blocks github"):
    assert(isBlocked("https://github.com/scala/scala"))

  test("blocks gitlab"):
    assert(isBlocked("https://gitlab.com/someone/project"))

  // ─── Ad networks ──────────────────────────────────────────────────────────

  test("blocks doubleclick"):
    assert(isBlocked("https://ad.doubleclick.net/ddm/trackclk"))

  test("blocks google ad services"):
    assert(isBlocked("https://www.googleadservices.com/pagead/aclk"))

  // ─── CDN & infrastructure ─────────────────────────────────────────────────

  test("blocks googleapis"):
    assert(isBlocked("https://fonts.googleapis.com/css?family=Roboto"))

  test("blocks cloudfront CDN"):
    assert(isBlocked("https://d1234abcd.cloudfront.net/assets/main.js"))

  // ─── Search engines ───────────────────────────────────────────────────────

  test("blocks google search"):
    assert(isBlocked("https://www.google.com/search?q=ddos"))

  test("blocks bing"):
    assert(isBlocked("https://www.bing.com/search?q=ddos"))

  // ─── URL shorteners ───────────────────────────────────────────────────────

  test("blocks bit.ly"):
    assert(isBlocked("https://bit.ly/3xYzAbc"))

  test("blocks t.co (twitter shortener)"):
    assert(isBlocked("https://t.co/AbCdEf"))

  // ─── TLD blocking ─────────────────────────────────────────────────────────

  test("blocks .gov domain"):
    assert(isBlocked("https://cisa.gov/topics/cyber-threats"))

  test("blocks subdomain of .gov"):
    assert(isBlocked("https://nvd.nist.gov/vuln/detail/CVE-2023-1234"))

  test("blocks .mil domain"):
    assert(isBlocked("https://www.defense.mil/News/"))

  test("blocks .onion domain"):
    assert(isBlocked("http://somesite.onion/page"))

  test("does not block .edu (high-quality academic signal)"):
    assert(!isBlocked("https://www.cs.princeton.edu/research"))

  test("blocks .gov.uk (gov appears as a label)"):
    assert(isBlocked("https://www.ncsc.gov.uk/guidance/ddos"))

  test("blocks .gov.au"):
    assert(isBlocked("https://www.cyber.gov.au/threats"))

  test("blocks .gov.in"):
    assert(isBlocked("https://cert-in.gov.in/s2cMainServlet?pageid=PUBVLNOTES"))

  // ─── Web archive & citation services ──────────────────────────────────────

  List(
    // format: off
    ("https://web.archive.org/web/20161120190412/http://atlas.arbor.net/summary/dos",    true),
    ("https://web.archive.org/web/20140822045227/http://www.scmagazineuk.com/article",   true),
    ("https://www.webcitation.org/5ws9rppxi?url=http://cyber.law.harvard.edu/file.pdf",  true),
    ("https://archive.ph/abc123",                                                        true),
    ("https://archive.today/newest/https://example.com",                                 true),
    ("https://cachedview.nl/",                                                           true)
    // format: on
  ).foreach { case (uriStr, expected) =>
    test(s"archive service filter: $uriStr => $expected") {
      assert(isBlocked(uriStr) == expected)
    }
  }

  // ─── Edge cases ───────────────────────────────────────────────────────────

  test("blocklist is case-insensitive on host"):
    assert(isBlocked("https://EN.WIKIPEDIA.ORG/wiki/DDoS"))

  test("blocks host with trailing dot"):
    assert(isBlocked("https://en.wikipedia.org./wiki/DDoS"))

  test("does not block a domain that merely contains a blocked name as substring"):
    assert(!isBlocked("https://notgithub.com/page"))
    assert(!isBlocked("https://myyoutube.com/video"))
