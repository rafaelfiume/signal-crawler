# signal-crawler

Start project with:

```
sbt "run <seed-url>"
```

## Design

The crawler is composed by a small set of key components coordinated by the [Orchestrator](/src/main/scala/io/rf/crawler/core/Orchestrator.scala).

The overall data flow is illustrated below:

```
-- (seed) --> | Queue  | --> | Fetcher | --> | Extractor | --> | Emitter |
                    ^                              |
                    |                              |
                    |                              |--> | Url Filter | --> | Url Normaliser | --> | Deduplication |
                    |                                                                                   |
                    |                                                                                   |
                    |-----------------------------------------------------------------------------------|
```

 * Queue: holds the initial seed URL and all the newly discovered URLs pending processing
 * Fetcher: downloads the HTML content
 * UrlExtractor: extracts well-formed URLs from the fetched HTML page
 * Emitter: emits (prints) the crawled page URL along with the well-formed URLs discovered in that page
 * UrlFilter: filters URIs with unsupported schemes (non-HTTP/HTTPS) and URLs pointing to external domains or subdomains
 * UrlNormaliser: canonicalises URLs to support effective deduplication
 * Deduplicator: tracks URLs that have already been seen to prevent reprocessing and cycles
 * Orchestrator: coordinates the workflow, manages concurrency, applies backpressure and ensure proper termination.

 ### Concurrency

* Each crawled page is processed sequentially according to the pipeline described above
* At most `maxConcurrency` pages are processed concurrently (via `parEvalMap`)
   - This limits fetch operations (finite, scarce, and protected resources)
* Deduplication is atomic in order to support safe-concurrency.


### Termination

The following invariant must be preserved when determining termination:

> Crawler terminate when there is **no queued work and no in-flight work**.

### URL Filter

See [spec](/src/test/scala/io/rf/crawler/core/UrlFilterSpec.scala) for the URL filtering rules.

### URL Normalisation

See component [doc](/src/main/scala/io/rf/crawler/core/UrlNormaliser.scala) and [spec](/src/test/scala/io/rf/crawler/core/UrlNormaliserSpec.scala) for details on URL normalisation semantics and tradeoffs.

### Deduplication

See component [doc](/src/main/scala/io/rf/crawler/core/Deduplicator.scala) and [spec](/src/test/scala/io/rf/crawler/core/DeduplicatorSpec.scala) for deduplication guarantees and limitations.

### Error Handling

The application distinguishes between different types of errors:
  - Infrastructure: raised errors not reflected in the API (e.g. I/O errors in the Fetcher)
  - Domain: error represented explicitly in components API (e.g. URL extraction errors)
  - Bugs: exceptions are thrown to indicate invariants are not preserved (e.g. misconfiguration when instantiating UrlFilter).

Errors are currently handled in minimal but functional way:
  - Infrastructure errors are logged at `warn` or `error` level
  - Domain errors are logged at `info` level and represented as strings (no ADT yet).

Beyond logging, no additional observability (metrics, tracing) is in place.

## Limitations

The current solution uses a breadth-first-like strategy implemented via a single queue, to decide which URLs are crawled next.

This approach limits the possibility to:
  - implement politeness
  - ensure fairness across domains
  - prioritise URLs
  - avoid bombarding a single web-site with requests (politeness)
  - progressing across domains at similar, relatively slow rates (fairness)
  - scale the application horizontally.
