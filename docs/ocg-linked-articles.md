# OCG official posts with linked articles

Two official accounts are supported independently:

- `@YuGiOh_OCG_INFO`: the existing source reads new-card images in posts.
- `@yu_gi_oh_jp`: a new opt-in RSSHub source follows official article links and
  reads explicitly labelled new-card images, without substituting promotional art.

Both use `YUGIOH_OCG`, the same OCG database and `/news subscribe ocg` preference.
Each account has its own history baseline/cursor. Group mode combines their
eligible records per game using the existing three/hard-five attempt budget and
50-record packet limit. Extra packets remain pending for later polling.

## Enable in the native bundle

Stop the supervisor, replace the JAR/scripts while preserving private settings,
data and runtimes, then run `check-yugioh-jp.cmd`. Only after `ARTICLE_READ_OK`
should you run `enable-ocg-jp.cmd` and `start-bot.cmd`.
When RSSHub is already running the helper validates its new feed first; otherwise
feed validation is deferred to polling. Article readability is checked before
writing configuration. It preserves the original account route,
credentials, group preferences and storage mode, and sends no QQ message.

```dotenv
RSSHUB_OCG_ENABLED=true
RSSHUB_OCG_ROUTE=/twitter/user/YuGiOh_OCG_INFO
RSSHUB_OCG_JP_ENABLED=true
RSSHUB_OCG_JP_ROUTE=/twitter/user/yu_gi_oh_jp
YUGIOH_JP_BROWSER_ENABLED=true
YUGIOH_JP_BROWSER_EXECUTABLE=C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe
```

The extra account defaults disabled and uses the existing RSSHub instance.
No X follow action or extra credentials are requested. The instance must actually
return this feed. `/api/status` exposes `ocgJpEnabled`; notification status adds
independent `baselines`, while group-mode `sources` still aggregates by game.

First JP history is saved without automatic forwarding, even when OCG was already
active. Old posts whose articles become readable later are suppressed using the
baseline time and original post publication time. Undated JP posts are stored
for manual preview but not automatically delivered. Existing pending rows and
successful stamps are retained.

`preview-yugioh-jp.cmd` manually sends one stored JP post to the configured test
group, without scraping. Manual previews may repeat and do not update automatic
stamps. `/posts?game=YUGIOH_OCG&limit=1` now selects across both accounts; `postId`
can select a specific stored post from either one.

## Verified HTML and data

On 2026-10-12 the supplied [official article](https://yu-gi-oh.jp/news/w0cgkf8x-usk/)
was inspected in the browser. It contains `article > header h1`,
`header time[datetime]`, an explicit `OCG` tag, `[data-news-body] > div` blocks,
`[data-news-rich-text]` labels and image buttons with
`data-news-image-open`/`data-image-src`. Its script only binds enlargement events.

The parser selects the immediate image block after `新規カード` or `新カード`.
The supplied page yields five new cards, excluding one reprint, one package
image, banners and dialog placeholders. It does not infer novelty from artwork,
dimensions or decoded filenames. Unmarked/changed layouts are skipped or logged;
additional layouts need their own verified examples.

Each image becomes a separate `CARD_REVEAL` NewsItem with original post title/text,
X `sourceUrl`, original publication date/time, official `imageUrl`, `articleUrl`,
existing post/image hash and JPA creation time. `productName` is extracted only
from an explicit quoted product followed by `に収録` in the article heading.
Missing values remain absent; no card names/effects or translations are guessed.
The existing formatter removes visible URLs in QQ image mode while retaining
them in data/console output. Official images/watermarks remain unchanged.

Only HTTPS `yu-gi-oh.jp/news/<slug>/` articles and `/media/cms/v1/` images are
accepted. RSSHub must expand article links; arbitrary sites and `t.co` redirects
are not followed. HTTP and parsing remain separate. HTML requests have a
15-second timeout, 2-MiB boundary and no redirects. No image mirror is created.
An optional installed Edge/Chrome reader uses normal headless rendering,
`--dump-dom`, a fresh temporary profile, a 45-second deadline and the same HTML
limit. It does not read normal profiles, expose debugging ports, disable
certificate checks/sandboxing or process CAPTCHAs. Browser caches are temporary;
a crash/locked profile may require OS temp cleanup. Browser navigation may
redirect normally; returned DOM still needs the expected article structure and
official image scope. QQ/X credentials are removed from its process environment.
Malformed entries/images and individual article failures do not block valid ones.
If all matching articles fail, the source reports an error; other sources continue.
Partial failures are recorded in logs. QQ failure retains saved news/pending state.

## Delivery eligibility

Group mode records per-source eligibility in the existing `qq_news_deliveries`
ledger before capturing per-game group queues. This prevents a new account's
history from entering an active subscription. `qq_group_deliveries` still owns
per-group success stamps. The source ledger's legacy `pending` flag is not a
group-mode delivery count; group status uses group records. Existing cursors,
preferences and successful stamps are preserved.

## Verification and live limit

`mvn test` and `mvn package` each passed 201 tests on 2026-10-12. Local fixtures
and mocks cover both accounts, all new-card images, excluded images, malformed
records, dates, independent history, late history, merged game packets,
deduplication, source isolation, QQ failure recovery, browser argument/profile
isolation, verification-page rejection and temporary cleanup.

A minimal DOM sample captured from the live browser page was replayed through
Java and returned five new-card URLs. Direct PowerShell and Java HTTP on the
development PC received Cloudflare HTTP 403, while the in-app browser displayed it.
Isolated headless Edge/Chrome also returned Cloudflare rejection on this PC.
Automatic live article retrieval, notebook feed availability and real QQ receipt
are therefore **not verified**. No official image bytes were downloaded in this
check. The source logs failures and does not bypass challenges.

The user reported notebook HTTP 403 too; its browser reader is not verified.
If its `check-yugioh-jp.cmd` also fails, keep JP disabled until a supported
retrieval approach is verified. The enable helper refuses configuration writes
after failed article checks. Existing WS/OCG sources continue independently.
Japanese OCR and actual QQ WebP receipt remain unverified.
