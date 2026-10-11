# Compact QQ forwards

The existing QQ image transport builds a `messages` array for NapCat's
[`send_group_forward_msg`](https://napneko.github.io/onebot/api) action.
Exactly identical post text **after** the existing QQ link-removal adapter is
emitted once per packet. Different visible bodies remain. This affects display
only: no news rows, source URLs, hashes, image records or delivery stamps are
merged or deleted. Every image retains its original URL and its own OCR
result/fallback; no image or watermark is modified.

Automatic forwarding collects pending posts for each game. WS and OCG remain in
separate forward packets and databases. Posts that fit the internal capacity can
share one packet. Capacity is now 50 news/image records (at most 100 nodes);
this is an application bound, not an official QQ maximum or a promise that the
live account/platform accepts every large packet. Larger batches split without
dropping images, and the existing three-attempt default/five-attempt hard cap
applies. Excess stays pending for later checks.

Manual whole-post preview now uses the same `NewsForwardPackets` planner instead
of sending each selected post separately:

```powershell
Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:8080/api/notifications/posts?game=WEISS_SCHWARZ&limit=3' |
    ConvertTo-Json -Depth 6
```

`limit` still counts selected posts (1–5); `sent` counts forward packets. Up to
five packets may be sent by one manual invocation; larger selections are rejected
before sending. Manual previews do not scrape or change automatic delivery state.
Legacy `/notifications/news` previews still inspect individual records.

`GET /api/status` reports `forwardRevision=compact-forward-v1` and
`forwardItemLimit=50`. Updating the JAR requires no new database migration or
configuration setting. Keep the existing separated-storage flag, data and secrets.

OCR can make a larger batch slow because each image may require a separate request.
OCR settings remain unchanged. Real large-packet delivery and the notebook update
need live verification; local mocks do not establish platform limits.

Verification on 2026-10-11: `mvn test` and `mvn package` each passed 157 tests.
Tests include 13/50-image body deduplication, preservation of distinct bodies,
multi-post manual packing and overflow handling. Packaged Java with local RSS/QQ
mocks sent two WS posts/four images in one packet and two OCG posts/five images
in another, with one repeated visible body per game. A two-post manual preview
sent one packet; a subsequent restart did not automatically repeat successful sends.
