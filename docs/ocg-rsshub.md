# Optional official Yu-Gi-Oh! OCG announcements

The optional source reads `@YuGiOh_OCG_INFO` through the existing RSSHub instance.
KONAMI identifies this as its official OCG X account:
https://www.konami.com/yugioh/campaign/tgs2026.
No X follow action, paid API, OCG website scraper, TCG or Rush Duel source is added.

## Enable

```properties
rsshub.enabled=true
rsshub.base-url=http://127.0.0.1:1200
rsshub.ocg-enabled=true
rsshub.ocg-route=/twitter/user/YuGiOh_OCG_INFO
```

Environment: `RSSHUB_OCG_ENABLED` defaults to false; `RSSHUB_OCG_ROUTE` uses the
route above. The portable Java launcher already configures the shared base URL.
`RSSHUB_POLLING_ENABLED=true` enables the existing fixed-delay poller (five minutes
by default), now selecting WS and OCG in one fetch. Feed caching and fetch duration
can delay detection. The source reuses HTTP fetching and official-post/media
validation from WS while retaining the existing WS hashes and fixtures.

## Conservative new-card filter

The supplied example contains `収録カード公開`. The filter accepts that heading
or an explicit `新カード` / `新規カード` followed nearby by `公開`, `発表`, `収録`
or `登場`. Unicode compatibility normalization is used for matching only; stored
post text retains its original characters. Hashtags are not required.

Posts explicitly mentioning reprints (`再録`, `再収録`), reissues (`復刻`), alternative
art (`新規イラスト`, `イラスト違い`), campaigns or giveaways are excluded. Ordinary
`カード紹介` / `今日のカード` and generic product announcements do not qualify.
Images must be original HTTPS `pbs.twimg.com/media/` URLs. Text-only/video-only
posts and other accounts/media hosts are skipped.

This is a textual announcement filter, not card-database verification. An unmarked
reprint using the same heading can still qualify; genuine new-card posts with
different wording can be missed. The real OCG feed was not accessible during
implementation. Local fixtures use synthetic image URLs and the supplied heading.
No live-feed success or newest actual card is claimed.

Items use `game=YUGIOH_OCG`, `category=CARD_REVEAL`, original text, original X URL,
publication instant/Japan date, image URL, stable per-game/post/image hash and the
existing persistence timestamp. Product names and reveal dates are not inferred.
Each image remains its own record; multiple cards in a single collage are not split.

## Persistence and notifications

OCG uses the same centralized ingestion/deduplication as WS. Its source ID is
`ocg-jp-rsshub-card-reveals`, with a separate persistent first-run marker. The first
successful nonempty complete matching snapshot stores history without automatic
QQ notifications. Empty/partial snapshots wait for a complete nonempty baseline.
An existing WS marker does not initialize OCG. Subsequent duplicates send nothing.

When OCG is enabled, automatic WS and OCG RSSHub notices are grouped into forward
packets of at most **20 image records**, using the existing formatter and QQ sender.
The configured batch limit counts packets, not images: default three, hard maximum
five **across the whole fetch**. Thirteen new images fit in one packet; forty-five
fit in three. Packets have at most forty nodes. Failed attempts consume the same
budget. Records beyond the budget remain saved and are logged as skipped; there
is no retry queue or later replay. A full default budget can cover sixty images,
shared between the enabled RSSHub sources.

QQ text omits source URLs and bare homepage links like the supplied
`yugioh-card.com/japan/products…`; the database retains them. Images are unchanged.
OCR is unverified raw text with an original-image fallback on failure. Japanese OCR
accuracy and live merged-forward receipt remain unverified. Group subscription
commands restricted to owners/admins are still Planned.

Old H2 databases can retain a check allowing only WEISS_SCHWARZ. On OCG-enabled
startup, a narrowly scoped updater recognizes the generated news-game enum check,
adds an expanded valid check first, then removes the legacy check. News rows,
unrelated checks and bootstrap markers are retained. Idempotence and old-row
preservation are tested with H2; the laptop database itself was not accessed.
Stop Java and back up its small `data` directory before upgrading.

## Inspect and preview without scraping

```powershell
Invoke-RestMethod 'http://127.0.0.1:8080/api/news?game=YUGIOH_OCG'
Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:8080/api/notifications/news?game=YUGIOH_OCG&limit=1' | ConvertTo-Json -Depth 5
```

Manual previews still accept only one to five stored records, can resend an old
record on explicit request, and cannot change the configured destination group.
The game parameter does not enable an unconfigured source or trigger scraping.

Verification: `mvn test` and `mvn package` pass 123 tests (no failures/errors/skips),
using local fixtures, in-memory H2 and mocked QQ/RSSHub HTTP. Real OCG RSSHub fetch,
automatic OCG group delivery and laptop deployment have not been verified.
