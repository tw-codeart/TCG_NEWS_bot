# Separate WS and OCG storage

This opt-in mode uses independent H2 files: `data/ws-news.mv.db` for WS and
`data/ocg-news.mv.db` for OCG. The old `data/tcg-news.mv.db` remains as the legacy
source. Default shared storage remains supported for existing installations and
tests; `GAME_STORAGE_SEPARATED_ENABLED=true` selects the separate mode.

Two JPA persistence units use the same entities and repository interfaces.
`GameStorage` chooses the game before starting its transaction. Ingestion,
deduplication, news queries and QQ delivery state all use this selection.
Each destination has database constraints rejecting the other game's news and
products. Fetching and QQ HTTP calls stay outside database transactions.
Both pools set `WRITE_DELAY=0` so a commit does not wait for the default delayed
log flush. This was added after a forced-process-stop check exposed lost recent
state; see the [H2 setting documentation](https://h2database.com/html/commands.html#set_write_delay).

`GET /api/news?game=...` reads that game's database. Unfiltered news merges both
games in newest-first order. Numeric IDs can coincide across databases: callers
must identify a record by **game and ID**, not ID alone. Whole-post preview still
requires a game and keeps each post's distinct image records.

## Migration and safety

Java creates a H2 backup ZIP in `data/backups` before copying existing legacy rows.
It preserves news IDs, hashes, URLs, dates, timestamps, product records, per-source
history baselines, delivery cursors, pending attempts and successful-send stamps.
Missing optional columns in early schemas remain null/default; nothing is invented.
The legacy database's rows and enum definitions are not rewritten or deleted.

Each destination's row copy and completion marker commit in one transaction.
If the other game's copy fails, restart can resume without recopying the completed
game. A migration marker prevents subsequent startups from overwriting successful
delivery state. Identity counters are advanced after copied IDs. An unmarked
nonempty destination or inconsistent legacy delivery/game state rejects startup
instead of silently merging data. The application blocks game storage operations
until both databases are initialized; this includes automatic notifications.

Do not start the old Java process while migrating. After switching, retain the
whole data directory and keep separate mode enabled. Turning it off, running an
older JAR, or retaining only the old legacy file would read stale data. Restore
from a complete stopped-process copy/backup if recovery is required; do not delete
database files or migration markers as a troubleshooting shortcut.

## Portable Windows update

1. Stop Java; preserve `config.env`, the complete `data`, `runtime` and `rsshub`.
2. Replace `app.jar` and scripts with the separate-storage update.
3. Run `start-separated.cmd` once. It sets only the public storage switch and
   delegates to the existing automatic startup. QQ and RSSHub prerequisites still
   apply; credentials/group IDs are retained.
4. After startup, `GET /api/status` should show `storageMode=SEPARATED` and
   `storageReady=true`. Both H2 files should be present; an existing legacy file
   also produces a backup. A fresh installation with no legacy file needs no legacy backup.
5. On later starts, use the existing `start-auto.cmd` with the storage flag retained.

Existing successful notifications are not deliberately replayed by migration.
Preexisting pending items remain eligible for the next automatic delivery attempt.
The migration itself does not send messages; the enabled poller runs normally
once initialization is complete. OCG collection remains separately optional.

## Scope

On 2026-10-11, `mvn test` and `mvn package` each passed 153 tests. Tests cover
row/state preservation, mismatched state rejection, destination collision
protection, interrupted migration recovery, early WS-only enum compatibility,
game-specific repository routing, deduplication and combined API reads.
Packaged Java was checked with a prior owned shared-file test database and local
RSS/QQ mocks. It retained old send stamps and delivered new WS/OCG image packets
from different physical files. After the immediate-flush fix, forced process
termination preserved the new rows and stamps; the following scheduled check
after restart saved zero new rows and sent zero additional packets.
Windows PowerShell 5.1 checked launcher parsing,
Unicode paths and configuration preservation. No real group or notebook migration
was performed for this phase.

Group subscription commands and multi-group routing are now available as a separate
opt-in; see [group subscriptions](qq-group-subscriptions.md). Windows boot autostart
and OCR improvements remain planned. Legacy/manual QQ delivery remains for the
configured test group. H2-specific migration supports this project's known tables;
unknown games/source IDs or unmarked destination data require investigation.
Multiple application processes and distributed transactions are not added.
