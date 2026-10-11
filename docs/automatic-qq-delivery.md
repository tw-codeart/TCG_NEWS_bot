# Automatic QQ delivery

With RSSHub and polling enabled, Java first checks the configured official X
feeds after ten seconds, then uses a fixed delay (default five minutes after
each completed check). WS accepts `今日のカード`; OCG, when enabled, retains
its existing new-card announcement filter. This is polling, not instant X push.

## Safe, durable sending

The first complete nonempty feed snapshot is still a suppressed historical
baseline. On upgrading an existing installation, the new per-source cursor
starts at the largest already stored official X news ID **before** fetching.
Old saved rows are not automatically replayed. Use the existing manual whole-post
preview endpoint to test an already saved post.

After initialization, newly saved official X records enter `qq_news_deliveries`.
The news itself stays in `news_items`. `qq_delivery_cursors` and queued rows
commit together. If Java stops between saving news and queue capture, the cursor
allows a later check to recover those new records. Source URL and game restrict
each queue; website records do not enter the QQ queue.

Only a successfully confirmed send marks a packet sent. Disabled QQ, transport
failure, or the batch limit leaves records pending. A later manual or scheduled
fetch drains pending records even if its source fails or it saves zero new rows.
Successful sends are not repeated just because the RSS feed repeats them.

The default shared limit is three attempted packets per fetch, with a hard maximum
of five. Image mode keeps normal multi-image posts together, up to fifty records
per packet. Multiple posts from the same game share a packet when they fit.
Exactly repeated visible post bodies appear once; images and per-record delivery
stamps remain distinct. Text mode sends one record per attempt. A source stops after its first
failed attempt in a cycle; other sources may use the remaining budget. There is
no tight retry loop; retries follow the configured polling interval.

## Windows portable setup

1. Preserve `config.env` and `data`; stop only the old Java window.
2. Install the update beside `app.jar` and the existing runtimes.
3. Keep RSSHub running on port 1200 and NapCat logged in with its HTTP API active.
4. Run `start-auto.cmd`. It checks NapCat login and the WS RSS feed, enables QQ and
   five-minute polling in the existing config, then starts Java. It does not alter
   tokens/group IDs or send a test message during these preflight checks.
5. Keep all three processes running and the laptop awake.

QQ and polling remain disabled in repository defaults; the launcher is an explicit
opt-in. OCR settings and the optional OCG switch are preserved. OCR may delay a
cycle substantially; set `QQ_OCR_ENABLED=false` if original images alone are enough.
The scripts use ASCII source and UTF-8 output for Windows PowerShell 5.1.

## Diagnose fetching versus sending

```powershell
Invoke-RestMethod 'http://127.0.0.1:8080/api/notifications/status' |
    ConvertTo-Json -Depth 6
```

`deliveryRevision` is `durable-qq-v1`. Status includes public configuration flags,
last automatic fetch result, and per-source baseline, pending/sent record counts,
last attempt/success timestamps and failure reason/error ID. It never includes
credentials or group IDs. `qqConfigured=true` validates configuration only, not
live connectivity. Sent counts are image/news **records**, not QQ packet counts.
Match error IDs to `logs/errors.log`. Fetch success does not imply delivery success.

For one already stored WS post, manually test with:

```powershell
Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:8080/api/notifications/posts?game=WEISS_SCHWARZ&limit=1' |
    ConvertTo-Json -Depth 6
```

Manual previews are explicit repeats and do not change automatic delivery state.

## Limitations

Verification on 2026-10-11: `mvn test` and `mvn package` each passed 146 tests.
A packaged-JAR test with local RSS/QQ mocks and file H2 exercised real scheduled
checks, three new images, a failed transport attempt, process restart, and delivery
recovery despite a duplicate-only feed. Windows PowerShell 5.1 checked ASCII
launchers, Unicode paths, failed preflight safety and preserved credentials.

The old notebook's exact reason for missing delivery needs its status/log output.
This change is tested with local fixtures/mocked QQ, not proof of notebook or real
group operation. Restarted Java resets the in-memory last-poll snapshot; queue
state remains persisted. Boot autostart and exponential retry backoff remain planned.
Multi-group delivery is now available in [subscription mode](qq-group-subscriptions.md).
Separate game files are available as an opt-in;
see [separate game storage](separated-game-storage.md).

This is at-least-once delivery: if QQ accepts a packet but its response is lost,
or Java stops before storing the success acknowledgement, a retry can duplicate
that packet. One running Java process per database is supported. Queue/sent rows
currently have no retention cleanup. Do not delete news/queue/cursor/bootstrap
tables to troubleshoot. Feeds can be stale or omit posts that appeared while
the laptop was off; polling cannot recover items no longer present in a feed.
