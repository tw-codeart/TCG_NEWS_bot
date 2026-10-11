# Whole-post previews and WS image packets

The existing `POST /api/notifications/news?limit=1` previews one **image record**,
which can be only one image from a multi-image X post. It remains compatible.

Use `POST /api/notifications/posts?game=WEISS_SCHWARZ&limit=1` or the same endpoint
with `game=YUGIOH_OCG` to preview one **whole stored official X post**. All its saved
images are selected and forwarded together (split only above 50 records per packet).
Website news is excluded. Publication instant determines the latest post, with
ingestion time/ID as a fallback; a late import of an old post does not displace a
newer dated post. Image-free placeholders are omitted once real images exist.

The new endpoint accepts 1–5 posts and at most 5 total forward packets. Oversized
selections are rejected before any send, rather than quietly truncating images.
It uses stored data and the shared formatter, and never triggers scraping or
allows a caller-selected group. The configured single test group remains the
destination. Response fields: posts, images, attempted, sent and errors. Attempted
and sent count API-confirmed packets, not individual images or visual receipt.

Automatic WS image forwarding now uses the same bounded packet path even when
OCG is disabled. Normal post image groups (at most 50) are kept intact at packet
boundaries. Eligible new/pending records, persistent history suppression and existing
message-budget limits remain. Legacy plain-text mode is unchanged.

`GET /api/status` reports `apiRevision=post-preview-v2`, supported games, registered
source IDs, effective polling/OCG/QQ/image/OCR flags and the polling interval.
It omits group IDs, tokens and base URL settings. A 404 indicates this endpoint is
absent in the running build; confirm the directory/process used for Java startup.

The optional portable `diagnose-bot.cmd` reads only public flags, stored news,
status and the two local RSSHub feeds. It does not call a fetch/save endpoint or
send a QQ message. Raw feed counts are diagnostics, not a replacement for parsing.

Unit/integration checks cover complete WS/OCG image groups, date ordering,
website exclusion, bounds, source isolation, persistence and duplicate handling.
All QQ HTTP uses mocks. The laptop's current WS outage, current live feed format,
and actual multi-image group receipt have not been verified. A Java status/fetch
result and RSSHub diagnostic are still needed to diagnose upstream/runtime issues.
Japanese OCR failure and group subscription commands remain separate pending work.
