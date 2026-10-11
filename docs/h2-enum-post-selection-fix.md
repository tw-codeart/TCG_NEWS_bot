# H2 native enums and exact stored-post selection

## Storage failure reproduced

The supplied OCG HTTP response was `503` with `News storage is unavailable`.
A populated H2 file from the earlier WS-only build reproduces that exact response:
its `NEWS_ITEMS.GAME` column is a native `ENUM('WEISS_SCHWARZ')`. Filtering by
`YUGIOH_OCG` raises H2 SQLState 22030 even before inserting an OCG record.
The earlier updater handled only varchar CHECK constraints and missed native enums.

On application startup the updater now inspects that specific GAME column,
retains existing enum labels/order, appends supported games and preserves its
default/non-null constraints. It runs independently of RSSHub/OCG enablement so
read-only game filters work even with those sources disabled. Existing CHECK-style
upgrade logic remains. No news table, rows or bootstrap markers are deleted.

The built JAR was run against a **copy** of a legacy file with three synthetic WS
image records. Before the fix the OCG preview returned the exact 503; afterward
the empty OCG query returned 200, old WS rows remained, and a WS three-image request
reached a local mock OneBot server with all three image segments. The laptop's own
database was not accessed and its exact SQL exception has not been collected.
Stop Java and back up its small data directory before replacing the JAR.

## What the supplied WS XML actually contains

The diagnostic XML contains 13 feed entries. The current Java parser reads eight
matching card posts and **20 images**, including all images in the three-image post:

- `2108389486552117588`: 2026-10-09 02:50 UTC / 11:50 JST, **one image**.
- `2108386969822846981`: 2026-10-09 02:40 UTC / 11:40 JST, **three images**.

The default latest-post preview correctly selects the newer one-image post. That
does not prove the older three-image post was truncated. The real XML was parsed
read-only: no images were downloaded, no records saved and no group messages sent.
A reduced local regression fixture keeps the escaped HTML/double-escaped URL query
structure with synthetic image names, rather than adding real card image binaries.

## Select that exact stored post

```powershell
Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:8080/api/notifications/posts?game=WEISS_SCHWARZ&postId=2108386969822846981' | ConvertTo-Json -Depth 5
```

The optional `postId` accepts only 1–20 positive numeric digits and requires
`limit=1`. The account prefix is derived from the game. It selects only an already
stored post; no caller URL, group override, scraping or arbitrary message is allowed.
Unknown IDs return zero posts; if needed first trigger the existing `/api/fetch`.
The existing latest-post behavior remains when postId is omitted.

`GET /api/status` now returns `apiRevision=post-preview-v2`.
Verification: mvn test and mvn package pass **134 tests**. Native enum row/default/
constraint preservation, repeated startup, exact post selection and invalid IDs are
covered. The copied-file/runtime HTTP check uses a fake group/API only. Actual
multi-image QQ receipt, the laptop's post-upgrade result and Japanese OCR accuracy
still need confirmation. Group command subscriptions are unchanged/planned.
