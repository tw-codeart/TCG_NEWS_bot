# TCG News Bot

A Java portfolio project for aggregating official trading card game news,
starting with Japanese Weiß Schwarz.

## Current status: Phase 7

The following source code and configuration have been added:

- A Java 17 / Spring Boot 3.5.16 Maven project.
- A JPA `NewsItem` entity and Spring Data repository.
- Categories for card reveals, product news, card list news, and general news.
- Fields for title, product name, reveal date, publication date, official image and source URLs,
  content hash, and an automatically assigned creation timestamp.
- A local file-based H2 database configuration.
- A `NewsSource` interface with a source identifier, game, and news-fetch contract.
- A `NewsSourceException` type for reporting retrieval and parsing failures with their causes.
- A Jsoup parser for the latest-products carousel and the first product-list page.
- A `WsProductDiscoveryService` that fetches the official products page and saves
  product names and canonical official detail URLs through `ProductRepository`.
- A separate `Product` entity whose detail URL is its catalog identity.
- Local HTML parser tests and isolated in-memory H2 persistence tests.
- A `WsCardRevealSource` that reads WS product URLs already stored by Phase 3.
- A separate product-page HTTP client and a fixture-testable card-reveal parser.
- Individual `CARD_REVEAL` records with product name, reveal date, official image
  and source URLs, a metadata SHA-256 hash, and a creation timestamp on persistence.
- Centralized exact duplicate checks in `NewsDeduplicationPolicy`, called by
  `NewsIngestionService`, with a database uniqueness constraint on `content_hash`.
- A `WsInformationSource` with a separate HTTP client and local-fixture parser
  for the first official information-list page.
- Information entries mapped to `PRODUCT_NEWS`, `CARD_LIST_NEWS`, or `NEWS`,
  retaining available metadata and leaving unavailable optional fields null.
- A REST controller exposing stored news and a synchronous manual fetch endpoint.
- A query service with newest-first ordering and an optional game filter.
- A fetch service that invokes configured `NewsSource` beans, isolates failures,
  and reports saved, duplicate, and unprocessed item counts.

**Phase 7 verification (2026-10-05):** Maven compilation and all 35 tests passed
using Java 17 and Maven 3.9.9. Seven new Spring Boot / MockMvc tests cover empty
and populated news responses, ordering and the game filter, invalid filters,
manual and repeated fetches, source failure isolation, all-source failure, and
storage errors. These tests use temporary in-memory H2 and mocked sources that
return freshly parsed local fixtures; they do not contact the official site.
The combined fixtures produced 10 items: the first request saved 9 and skipped
1 duplicate; the second saved 0 and skipped all 10. Two additional unit tests
check partial persistence counts and continuation after persistence failure.
One new source test checks product-discovery failure, and existing source tests
now verify discovery precedes product-page requests. No live-site fetch or
standalone HTTP-server check was performed for this phase.

**Phase 6 verification (2026-10-05):** Maven compilation and all 25 tests passed
using Java 17 and Maven 3.9.9. The 7 added in-memory H2 tests cover matching source
announcements despite changed thumbnails/hashes, matching card images/dates across
different pages, exact hashes, a freshly parsed repeated information fetch, similar
titles with shared URLs/dates/banners, different dates for the same card image,
and missing publication dates. The existing fixture test also confirms distinct
same-day card images are saved separately and repeated card fetches save nothing.
The information fixture saved 6 records on its first fetch and 0 on its second.
No live HTTP or image requests were made for Phase 6 validation.

**Phase 5 verification (2026-10-05):** Maven compilation and all 18 tests passed
using Java 17 and Maven 3.9.9. The 6 added tests cover information parsing, optional
fields, malformed entries, source delegation and HTTP errors, and publication-date
persistence. Parser tests use synthetic local HTML; source tests mock HTTP.
A separate live check fetched the official information page and saved 30 news
records into temporary in-memory H2: 18 `PRODUCT_NEWS`, 4 `CARD_LIST_NEWS`, and
8 `NEWS`. All 30 had publication dates and thumbnail URLs; 20 had product names
in the recognized title format. Required metadata and `createdAt` were checked
after persistence. No images were downloaded. This is a point-in-time check.

**Phase 4 verification (2026-10-05):** Maven compilation and all 12 tests passed
using the same Java 17 / Maven 3.9.9 toolchain. The 7 new tests cover dated reveals,
multiple same-day images, pages without reveals, malformed records, duplicate
occurrences and fresh repeated fetches, and product-page HTTP failure isolation.
All unit tests use local fixtures or mocked HTTP; none depend on the live website.
A separate manual check ran the real Phase 3 discovery and Phase 4 source in a
non-web Spring context. It visited 14 discovered product URLs and saved 170
card-reveal records in temporary in-memory H2. All 170 came from
<https://ws-tcg.com/products/anm_bp/>, with dates from 2026-09-07 to 2026-09-29.
The other 13 product pages returned no dated card reveals. Required fields,
including `createdAt`, were checked after persistence. No image requests were made.

**Phase 3 verification (2026-10-04):** A prepared working copy was compiled and tested with
Temurin JDK 17.0.20.1 and Maven 3.9.9. `mvn test` executed 5 tests with 0 failures,
0 errors, and 0 skipped tests. The suite includes the existing news persistence test,
three product parser tests, and one product persistence test.
One separate manual check started a non-web Spring context, fetched the real
products page, and saved 19 entries into 14 distinct product rows in a temporary
in-memory H2 database. Repeated carousel/list appearances share a detail URL.
This is a point-in-time check, not a guarantee that the official HTML will remain
unchanged. Web-server startup and file-based database restart persistence have not
been separately verified. The portable validation tools are outside this repository
and do not change system Java or Maven configuration.

The source contract does not depend on WS page structure. Future games can add
game enum values and source implementations without changing this interface.
Product discovery is a catalog prerequisite, not a `NewsSource` implementation:
catalog entries are not product-news announcements. `WsCardRevealSource` is the
first concrete news source; `WsInformationSource` is the second. `NewsFetchService`
invokes the configured sources through this interface.

Use `POST /api/fetch` to trigger collection manually. The WS card-reveal source
refreshes its product catalog with the existing discovery service before reading
stored product URLs. From Spring/Java code, the single-source
`NewsIngestionService.fetchAndSave(source)` method remains available.
Startup/scheduled fetches and notifications are not implemented.

## Product discovery scope

The parser was based on a direct HTTP 200 inspection of
<https://ws-tcg.com/products/> on 2026-10-04. It reads
`.products__latestSwiper a.products__link` and `.products__lists a.products__link`,
using the first non-empty `.products__name`. The live carousel contains an empty
name paragraph before the actual name. Relative links resolve against the page URL;
only HTTPS detail links on `ws-tcg.com` are accepted. Query strings and fragments
are removed, and detail paths use a trailing slash for a stable catalog key.

Malformed individual entries are skipped with warnings. Missing expected sections
or a page containing only invalid entries raise an error. Network and parsing
failures are logged and rethrown as `NewsSourceException` with the original cause.
Repeated discovery updates the same product row by its URL; different URLs can
share a product name. This catalog identity does not implement news deduplication.

Only the latest carousel and first list page are covered. Historical pagination
and release-date parsing/filtering are not implemented. The JUnit tests use a reduced synthetic local fixture with the observed
HTML structure and no official images; they make no live network requests.

## Card reveal scope

The parser follows the dated `.article__accordionTitle` headings and their
immediate `.article__accordionContent` siblings observed on the anemoi product
page. It reads images from `ul.img6Lists-card` within the matching dated accordion.
The undated archive wrapper and unrelated promotional sections are excluded.
The reveal date comes from the full Japanese date in the heading, never from an
image filename, upload directory, or the current date. Missing reveal sections
return an empty list. Invalid dates, missing content, and missing/invalid image
URLs are logged and skipped without discarding other valid records.

Each image is a separate record, including different images on the same day.
The title remains unset; card names and effects are not inferred. The content hash
is SHA-256 over UTF-8 metadata: game, category, source URL, reveal date, and image
URL, separated by newlines. It does not hash downloaded image bytes. Duplicate
occurrences in one batch and previously saved hashes are filtered by the ingestion
service. A unique hash constraint also prevents duplicate inserts. The same image
on a different dated reveal remains a distinct record.

The source refreshes the WS catalog, then uses its stored rows and logs individual product-page failures
while continuing other pages. If every attempted page fails, it throws
`NewsSourceException` with the first cause. Pages that successfully load without
dated reveals are successful empty results. Parsing supports the observed accordion
and image-list format; other HTML formats and sources have not been implemented.
If catalog discovery fails, this source fails before detail requests; the API
orchestrator still attempts other sources.

## Information news scope

The parser follows `ul.news__lists > li.news__item`, `a.news__itemLink`,
`.news__itemTitle`, `time.news__itemDate`, `.news__itemCatItem`, and
`.news__itemThumb img`, inspected directly at <https://ws-tcg.com/information/>
(HTTP 200) on 2026-10-05. Links can lead directly to products, card lists, or other
official pages. Query parameters are preserved, including card-list expansion IDs.
Only HTTPS URLs on `ws-tcg.com` are accepted.

The official labels `商品情報` and `カードリスト` select the corresponding
categories. Other or missing labels produce `NEWS`. Publication dates come from
the `datetime` attribute, falling back to a valid displayed `yyyy.MM.dd` date.
Missing or invalid dates and thumbnails remain null. `revealDate` stays null.
Product names are extracted only from recognized full product-announcement titles
with a bracketed name; irregular or ambiguous titles leave `productName` null.
The original news title is retained without translation or inferred card text.

Malformed individual entries are logged and skipped; valid entries continue.
A missing expected news list raises a parsing error. An empty list, or a list
containing only invalid individual entries, returns no records. Retrieval and
page-level parsing failures are logged and wrapped in `NewsSourceException`.

The information content hash uses UTF-8 metadata separated by newlines: game,
category, source URL, title, publication date, and image URL. Null date/image values
use empty hash components. The ingestion service delegates duplicate checks to the shared policy;
the parser does not filter duplicates. Different titles or dates at the same
destination URL can represent separate announcements; the exact identity rules
below preserve them.

Only the first list page is fetched. Pagination and linked-page content extraction
are not implemented. No detail-page requests or image downloads are made by this source.

## News deduplication scope

`NewsDeduplicationPolicy` checks persisted records in this order:

1. An exact `contentHash` match is a duplicate for any category.
2. For card reveals, the same game, category, reveal date, and non-empty image URL
   identify the same reveal, even if its source page or metadata hash changes.
   A different image on the same day, or the same image on another date, stays separate.
   If the date or image is missing, only the hash rule applies.
3. For other news, the same game, category, source URL, publication date, and
   complete non-empty title identify an announcement. Thumbnail changes do not
   create another record. Missing publication dates match other missing dates.

Source URLs alone are not globally unique: several announcements can point to
one product, card-list, or rules page. News thumbnails can be reused banners.
Neither shared URLs, shared news thumbnails, dates, product names, nor similar
titles alone cause a merge. Exact titles are used only with the news URL/date
context; no title similarity matching is performed.

The service calls `saveAndFlush` before checking the next item. Subsequent items
in the same batch and later fetches can therefore find the saved record. Sources
and parsers do not perform duplicate checks. The shared policy uses model fields,
not WS selectors or individual source IDs, so future matching rules can be added
there without changing source implementations. No fuzzy matching, RSSHub, or X
integration is implemented.

Comparisons use the stored URL strings exactly; URL aliases, rewritten image URLs,
and semantically equivalent announcements are not matched. An edited news title
or date can remain a separate announcement. Existing duplicate rows are not
automatically cleaned up, and incoming metadata does not update a matched row.
The database enforces unique hashes only. Concurrent fetches with different hashes
but the same composite identity can still race; composite database constraints or
coordination are not implemented. No schema change is required in this phase.

## REST API

- `GET /api/news` returns stored `NewsItem` records as a JSON array, ordered by
  `createdAt` descending, then `id` descending as a tie-breaker. An empty database
  returns `[]`. This minimal API returns all matching rows without pagination.
- `GET /api/news?game=WEISS_SCHWARZ` applies an exact enum filter. Unknown game
  values return HTTP 400 with `{"message":"Invalid game filter"}`.
- `POST /api/fetch` takes no request body and runs the configured sources
  sequentially. The controller delegates to services; it contains no scraping,
  parsing, or deduplication rules. The WS card source performs product discovery,
  and both WS sources use the existing ingestion and deduplication policy.

With the application running on the default local port:

```shell
curl "http://localhost:8080/api/news"
curl "http://localhost:8080/api/news?game=WEISS_SCHWARZ"
curl -X POST "http://localhost:8080/api/fetch"
```

Example POST response from the local-fixture MockMvc test (not live-site counts):

```json
{
  "fetched": 10,
  "saved": 9,
  "duplicates": 1,
  "unprocessed": 0,
  "successfulSources": 2,
  "errors": []
}
```

`fetched` counts items returned by sources, `saved` counts successful new saves,
and `duplicates` counts actual duplicate decisions. `unprocessed` counts returned
items left unsaved/unclassified when persistence stops mid-batch. Counts include
known partial persistence results; a source that fails before returning items
has no known fetched count and is reported in `errors`. No batch transaction is
started, so earlier successful saves remain when a later item fails.

HTTP 200 means at least one source completed (an empty result counts as success),
or no sources were configured. Partial failures appear in `errors`, each containing
`sourceId` and a short message; callers must check this list. If every configured
source fails, the same summary is returned with HTTP 502. Full causes are logged
on the server rather than exposed in JSON. News-query storage failures return
HTTP 503 with `{"message":"News storage is unavailable"}`.

Fetch requests are synchronous and can take time because product pages are visited
sequentially. Per-product failures retain the existing source logging behavior;
the API reports source-level failures, not every skipped page or parser entry.
Concurrency limitations from Phase 6 still apply. Authentication, background jobs,
and frontend interfaces are not implemented.

## Prerequisites and commands

Install JDK 17 and Maven 3.6.3 or later, and ensure Maven uses JDK 17.
Run these commands from the project root:

```shell
java -version
mvn -version
mvn test
mvn spring-boot:run
```

The web starter provides the REST API runtime described above.
The H2 configuration uses `./data/tcg-news` relative to the working directory.
Database files and build output are excluded from Git. The H2 console is disabled.
Automatic schema updates and the local database credentials are development settings.
Tests use an embedded database instead of the application's data files.
`revealDate` represents the card-reveal date; `publishedDate` represents the news
publication date. `createdAt` is an instant assigned when a record is first persisted.
Non-card news can omit image and reveal fields.
Card reveals and information news use metadata hashes and the centralized identity
checks described above.

## Planned / Roadmap

1. Phase 8: Add `NotificationSender` and `ConsoleNotificationSender`.
2. Phase 9: Extend parser fixture coverage and news deduplication tests.

Future extension points: `CardTextExtractor` and `TranslationService` interfaces.
Chinese translation, QQ / Discord delivery, and Yu-Gi-Oh! OCG / TCG support are
future work and are not implemented. No OCR, paid APIs, social media APIs, or
frontend framework are included.

## Images and attribution

The data model stores official image URLs and source URLs only. Official card
images are not downloaded into this repository, mirrored, or modified. This is an
independent portfolio project and does not claim official authorization.
