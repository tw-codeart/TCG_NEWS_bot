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

The parser selects consecutive image blocks after `新規カード` or `新カード`,
stopping at the next text block so reprints and product art stay excluded.
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

On 2026-10-18 the supplied [Movie Box article](https://yu-gi-oh.jp/news/4pai9s7t1s/)
was inspected in the in-app browser. Its three new-card images occupy two grids
(one plus two); the previous parser stopped after the first grid. Both grids
are now parsed. Original article paragraphs and line breaks are stored in the
nullable `articleText` field and included by the shared notification formatter.
The text includes clearly labelled reprint lists and product details; selected
images still come only from explicit new-card sections. This is HTML text
extraction, not OCR or translation. No screenshot service is added.
Identical visible metadata/text is displayed once per QQ packet. QQ still removes
visible URLs and marks text truncated over 6000 code points; full extracted text
remains in H2. Scripts, hidden elements, dialogs and banners are excluded.

`CARDニュース(OCG)` notices mentioning a V-Jump article are also inspected when
they contain an expanded official article link; an OCG-tagged article with an
explicit new-card section is still required. The ordinary OCG account continues
to recognize `収録カード公開` and retain every attachment separately.
Existing post/image identities and successful delivery stamps are unchanged.
Previously stored rows are not automatically rewritten with article text or
resent. Newly recovered missing images still follow history suppression and
explicit backfill rules.

The supplied X examples were published on October 15 and October 14 respectively.
On October 18 the panel's updated weekly range covers October 11–17, including
both examples when RSSHub or H2 still contains them. An explicit narrower range
is also available:

```powershell
$preview = Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:8080/api/notifications/backfill/preview?game=YUGIOH_OCG&from=2026-10-14&through=2026-10-17'
$preview | ConvertTo-Json -Depth 5
# Confirm only after reviewing the returned selection:
Invoke-RestMethod -Method Post -Uri ('http://127.0.0.1:8080/api/notifications/backfill/confirm?previewId=' + $preview.previewId)
```

Direct HTTP and isolated Edge were blocked again on the development PC on
October 18, while the in-app browser displayed the official article. This does
not establish unattended article retrieval on the notebook. Run the packaged
read-only article check before enabling the second account.

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
