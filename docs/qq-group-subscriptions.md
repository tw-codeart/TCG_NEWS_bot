# QQ group subscriptions

This opt-in mode adds signed group commands and per-group automatic delivery.
It requires the existing separated WS/OCG storage, configured QQ API/test group,
and a callback signing secret. QQ and group mode remain disabled by default.
No Spring Security, frontend or additional messaging framework is introduced.

## Commands

```text
/news subscribe ws
/news subscribe ocg
/news unsubscribe ws
/news unsubscribe ocg
/news status
/news help
```

Only an owner/admin may subscribe or cancel. Status/help are available to group
members. The backend checks the current bot identity and calls
[`get_group_member_info`](https://github.com/botuniverse/onebot-11/blob/master/api/public.md#get_group_member_info-获取群成员信息)
with `no_cache=true`; the event's claimed sender role is ignored.
Only current group text events (optionally mentioning the bot) are handled.
Private, anonymous, self-sent, unrelated, stale and unsupported-segment events
do not trigger commands. Negative OneBot message IDs are supported.

## NapCat setup on the laptop

LLBot uses the same signed Java callback and subscriptions. See
[LLBot migration](llbot-migration.md) for its OneBot HTTP POST settings. With
`QQ_ADAPTER=llbot`, the setup helper generates `llbot-http-post.local.json`
instead of `napcat-http-client.local.json`; existing secrets/data are preserved.

1. Stop Java, update the JAR/scripts, preserve the complete `data` and `config.env`.
2. Run `start-group-subscriptions.cmd`. It requires separate storage, creates a
   random callback secret only if absent, preserves existing settings, creates
   private `napcat-http-client.local.json`, and starts the existing automatic flow.
3. Keep the current NapCat **HTTP server** for outgoing API calls. Add a separate
   **HTTP client** in NapCat network configuration for incoming events:
   URL `http://127.0.0.1:8080/api/qq/events`, message format `array`, self-message
   reporting disabled, enabled on save. Copy its Token from the private local JSON
   (`token`, same value as `QQ_COMMAND_SECRET`). Do not share or commit this file.
4. A group owner/admin can send `/news subscribe ws` or `/news subscribe ocg`.
   The bot must be in that group, and the corresponding RSSHub source must be enabled.
5. Later launches use `start-auto.cmd`. Keep group mode enabled and the original
   `QQ_TEST_GROUP_ID`; do not run an older JAR against the activated data directory.

NapCat's [HTTP client implementation](https://github.com/NapNeko/NapCatQQ/blob/main/packages/napcat-onebot/network/http-client.ts)
uses its Token to compute `X-Signature: sha1=<HMAC-SHA1>` over the exact JSON body,
matching the [OneBot HTTP POST signature](https://github.com/botuniverse/onebot-11/blob/master/communication/http-post.md#签名).
This callback secret is separate from the outgoing HTTP server's API token.
The Java route rejects invalid signatures before parsing/handling events, bounds
bodies at 64 KiB, and accepts events within a five-minute clock window. It returns
204 without a quick-action reply, avoiding a second reply from NapCat.

## Persistence and delivery

`qq_group_subscriptions` stores a game/group preference and news cursor, with an
optimistic-lock version. `qq_group_deliveries` stores each game's group/news
pending state and success stamp. `processed_qq_commands` prevents duplicate
callback changes/replies and binds the original test group for legacy import.
Command marker and preference change commit together. A failed confirmation
message does not roll back the subscription; use `/news status` to check.

The original configured test group's historical stamps and pending rows are
copied once to that destination only. It stays subscribed for enabled sources
until an administrator cancels that game's subscription. Legacy rows are kept.
New groups start at the currently stored official X news position and do not
receive already stored history. Source first-run suppression still applies.
Cancellation deletes only that group's pending delivery entries, not news or
successful stamps. Resubscription starts from now; cancelled backlog is not replayed.

Every group gets separate WS/OCG forward packets using the existing formatter,
50-record internal capacity and repeated-body removal. A failed group remains
pending while successful groups are stamped. The existing default three/hard-five
attempt budget is shared across games/groups per fetch. Eligible destinations
rotate through the budget; larger queues continue in later polling rounds.
Each game permits at most ten active subscribed groups (an application limit).

`GET /api/notifications/status` adds `groupSubscriptionsEnabled` and per-source
active group counts without listing group IDs or credentials. When group mode
is active, source pending/sent counts are **group delivery records**, so a card
sent to two groups counts twice. Manual REST smoke/preview endpoints still send
only to the configured test group and do not accept caller-selected group IDs.

## Verification and remaining limits

On 2026-10-12, `mvn test` and `mvn package` each passed 172 tests. Checks cover
owner/admin permissions, spoofed event roles, invalid signatures, callback
deduplication, ignored/malformed events, history suppression, cancellation,
resubscription, independent games, reply failure, role-response identity matching,
and per-group send/retry behavior. Windows PowerShell 5.1 verified setup in a
Unicode path, stable generated secrets, and preservation of existing settings.

Packaged Java was checked against local RSS/QQ mocks with file H2: signed admin
subscription, ordinary-member rejection despite a forged owner field, one reply
for duplicate callbacks, original-group success/subscribed-group failure, restart
retry of only the failed group, and administrator cancellation all passed.
No live QQ command/multi-group delivery or notebook setup was performed.

HTTP confirmation loss or a stop before saving an acknowledgement can still
duplicate a retry. A cancellation may not stop a send already in flight.
Optional retention cleans old command replay markers while preserving original-group
bindings and all delivery stamps. Windows logon startup, worker supervision, local
health alerts and optional Tesseract Japanese OCR are described in
[Windows operations](windows-operations.md). Live notebook/group verification and
OCR accuracy remain unverified. Java, RSSHub and NapCat must remain available.
