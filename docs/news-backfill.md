# Manual news backfill after data loss or downtime

Use a preview and explicit confirmation to recover RSSHub card reveals into the
configured **test group's** existing durable delivery queue. This is useful after
the old `data` folder was deleted and the first feed snapshot became suppressed
history. It does not reconstruct deleted send stamps or guarantee a complete X
archive.

## Windows panel

The current panel adds **补发WS（前7天）** and **补发OCG（前7天）**. Start supervised
collection, then click the relevant button. It refreshes that game's configured
RSSHub sources, reports posts/records already sent/pending and available to queue,
and asks before enqueueing. The default window is the previous seven **complete
calendar dates in Asia/Tokyo**, excluding today. For example, on October 18 it
covers October 11 00:00 through October 18 00:00 (exclusive), including October
11 through 17. The preview shows the actual dates before confirmation.

Keep polling enabled. The next normal fetch drains confirmed records using the
existing formatter, per-game forward packets and batch budget (default three,
maximum five attempted packets per fetch; at most fifty records per image packet).
QQ can be temporarily offline while preparing the backfill. A queue acknowledgement
is not proof of real group receipt. Confirmed records persist across restart;
preview tokens expire after ten minutes and are lost on restart.

The existing **测试推送** buttons remain explicit repeat previews and do not mark
automatic delivery success. Use **补发** for durable, deduplicated recovery.

## REST API

Preview without invoking a notification sender:

```powershell
$preview = Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:8080/api/notifications/backfill/preview?game=WEISS_SCHWARZ&days=7'
$preview | ConvertTo-Json -Depth 8
```

Inspect the result, then enqueue the server-issued selection:

```powershell
Invoke-RestMethod -Method Post -Uri ("http://127.0.0.1:8080/api/notifications/backfill/confirm?previewId=" + $preview.previewId) | ConvertTo-Json
```

Use `game=YUGIOH_OCG` for the separately configured OCG source(s). For calendar
dates, replace `days` with `from=2026-10-16&through=2026-10-17`; both dates are
inclusive in Asia/Tokyo. Omitted `days` defaults to seven. `days=2` remains available
for the previous two complete Japan dates;
use explicit `from`/`through` dates to include today if desired. Both forms are
limited to seven days, with at most 1,000 records per preview. Oversized selections
must be narrowed rather than silently truncating multi-image posts. Future
calendar dates are rejected. Undated records are not guessed into a date range.

Neither endpoint accepts a request body, URLs or group overrides. Confirmation
only uses the configured test group; when subscription mode is enabled that group
must still be actively subscribed. Other subscribed groups do not receive the
explicit historical backfill. A changed target/mode or expired preview requires
a new preview. Repeated confirmations skip existing pending and sent stamps.

## Scope and recovery semantics

The refresh uses configured official sources and the existing central ingestion
and deduplication policy. It shares the normal fetch lock and preserves initial
history suppression. It invokes no sender. Ordinary polling remains independent:
new regular news discovered after an existing baseline can still enter and be
sent through its usual queue while an operator is reviewing a backfill.

Candidates combine stored news with the current RSSHub response. Source reports
include fetch/save counts, the oldest/newest **returned** timestamps and error IDs;
they are not proof that the requested interval is complete. A failed source does
not block other sources, and previously stored eligible history can still be
selected if that source already has a baseline. An empty/failed first response
does not establish one.

Confirmation creates only missing queue rows. It never fabricates successful-send
stamps, rewinds cursors or resets sent records. Existing delivery services perform
sending later; failures keep rows pending and do not roll back saved news.
News and delivery state continue to use the selected game's storage transaction.

X posts no longer returned by RSSHub and absent from the database cannot be
recovered by this feature. Pagination/archive retrieval and arbitrary post-URL
imports are not implemented. If old success records were deleted, previously
delivered messages may appear again when explicitly backfilled. Success tracking
only prevents repeats from the retained/new database onward.

Tests use local source fixtures, H2 and mocked QQ. Real QQ delivery and recovery
of the user's missing two-day history must be checked on the deployed machine.

Verification on 2026-10-18: `mvn test` and `mvn package` each passed 218 tests.
The packaged JAR on the lite Java runtime was also exercised with local RSS/QQ
HTTP stubs and separate file H2 databases: preview sent nothing, WS three-image
and OCG two-image selections were queued, pending rows survived a process restart,
and scheduled delivery confirmed two packets containing all five images. A later
preview/confirmation skipped the retained success stamps. The calendar update
also verified Japan midnight boundaries and excluded today's feed entries.
No real QQ was used.

The subsequent weekly default update retained 225 passing tests for both Maven
commands. A fresh lite-runtime smoke check using local RSS/QQ stubs included a
five-day-old post per game, excluded today and nine-day-old posts, preserved all
seven pending images over restart and confirmed two forward packets. Repeated
confirmation skipped retained success stamps. This does not verify real QQ receipt.

The calendar revision is `calendar-history-v2` in `/api/status`. Older rolling-hour
JARs do not enable the revised panel controls. The panel also disables OCG backfill
when that source is disabled and shows `RSSHUB_OCG_ENABLED=true` as the required
configuration change; stop/restart collection after editing `config.env`.
