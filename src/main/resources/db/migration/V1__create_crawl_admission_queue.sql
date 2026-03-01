CREATE SCHEMA IF NOT EXISTS crawler;

CREATE TABLE crawler.crawl_admission_queue (
    id BIGSERIAL PRIMARY KEY,     -- 8 bytes, sequential (better index locality), naturally ordered fits FIFO semantics
    url TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_crawl_admission_queue_url
    ON crawler.crawl_admission_queue(url);