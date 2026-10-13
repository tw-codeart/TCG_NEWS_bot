# LLBot / NapCat OneBot compatibility

The Java sender uses the same OneBot 11 HTTP actions for both adapters. Set
`QQ_ADAPTER=llbot` to identify the selected adapter in `/api/status` and Windows
operations. The default remains `napcat`; old configurations continue to work.
This setting does not install, log in to or switch a QQ client by itself.
Milky and Satori are not supported by this application.

## Migrate an existing Windows bundle

1. Use `stop-bot.cmd` and stop any separately started Java/RSSHub windows. Close
   NapCat before logging the same bot account into LLBot. Keep the entire `data`
   folder, `config.env`, the original `QQ_TEST_GROUP_ID`, separated-storage and
   group-subscription flags. Do not reset delivery stamps or reinitialize feeds.
2. Copy the incremental update contents into the existing bundle folder. Preserve
   `runtime`, `rsshub`, private configuration and databases. This update does not
   contain LLBot, QQ, credentials or databases.
3. Install LLBot using its [official instructions](https://luckylillia.com/guide/choice_install),
   and log in to the **same bot account**. In Bot configuration, enable **OneBot 11**
   and an **HTTP** connection. Use a local port such as 3000, set its Token, keep
   local-only listening enabled, and use array message format. Its WebUI (normally
   port 3080) is not the HTTP API address.
4. In the existing `config.env`, set `QQ_API_BASE_URL=http://127.0.0.1:3000` (or
   your actual HTTP port) and `QQ_ACCESS_TOKEN` to that HTTP connection's Token.
   Keep the other settings. Run `enable-llbot.cmd`. It checks `get_version_info`
   (`app_name=LLOneBot`), bot identity and actual `get_status.data.online` before
   changing configuration. Failed API/identity/online checks leave configuration
   and delivery data untouched. Success sets `QQ_ADAPTER=llbot` and
   `QQ_OCR_PROVIDER=onebot`, retaining a previously selected `tesseract` provider.
5. If group subscriptions are already enabled, the helper creates private
   `llbot-http-post.local.json` using the **existing** `QQ_COMMAND_SECRET`. In
   LLBot, add/enable a OneBot **HTTP POST** connection with those fields. This JSON
   is one connection entry, not a replacement for the entire LLBot configuration.
   The callback URL is `http://127.0.0.1:8080/api/qq/events`; message format is
   `array`, self/offline reporting is disabled, and its Token equals
   `QQ_COMMAND_SECRET`. The outgoing HTTP API Token and callback Token are separate.
   Do not disable callback signature verification. For a new group-mode setup,
   `start-group-subscriptions.cmd` generates the selected adapter's configuration.
6. Remove any old generic `qqLauncherPath` pointing to NapCat. Leave it empty for
   manual LLBot startup, or set it to the actual LLBot `.exe`/`.cmd`/`.bat` path in
   `operations.local.json`. Legacy `napcatLauncherPath` still works in NapCat mode
   and is ignored in LLBot mode. No QQ process is killed/restarted automatically.
7. Run `check-qq.cmd`, then `start-bot.cmd`, then `check-bot.cmd`. API status should
   show `qqAdapter=llbot`. This value identifies configuration, not proof of live
   delivery. Keep the supervisor open; do not start duplicate worker scripts.

Example optional supervisor settings (replace with an actual installed path):

```json
{
  "qqLauncherPath": "D:/LLBot/LLBot.exe",
  "preventSleep": true,
  "desktopAlerts": true
}
```

## Daily smoke test

After confirming LLBot is logged in, run `check-qq.cmd` and `check-bot.cmd`.
Preview one **stored** whole WS post with all its images:

```powershell
Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:8080/api/notifications/posts?game=WEISS_SCHWARZ&limit=1' | ConvertTo-Json -Depth 5
```

For OCG, replace `WEISS_SCHWARZ` with `YUGIOH_OCG`. Manual previews intentionally
allow resending and do not alter automatic delivery stamps. They do not scrape.
An owner/admin can use `/news status`, `/news subscribe ws` or
`/news subscribe ocg` in a group to check the callback/permission path.
Existing subscriptions need not be recreated when data and bot identity are retained.
Automatic polling remains the configured interval (default five minutes after
completion); successful per-group stamps suppress later automatic resends.

## Contract and verification limits

The official [HTTP/configuration documentation](https://luckylillia.com/guide/config)
and [OneBot protocol guide](https://luckylillia.com/guide/develop) were inspected.
Source revision `dc2b2b90092ef0f571b5546878288b83fff1e436` confirms:

- [HTTP and signed HTTP POST](https://github.com/LLOneBot/LuckyLilliaBot/blob/dc2b2b90092ef0f571b5546878288b83fff1e436/src/onebot11/connect/http.ts):
  API Bearer authentication and `X-Signature: sha1=<HMAC-SHA1>` over the JSON body,
  using the HTTP POST connection's Token.
- [Forward handling](https://github.com/LLOneBot/LuckyLilliaBot/blob/dc2b2b90092ef0f571b5546878288b83fff1e436/src/onebot11/transform/message/outgoing.ts):
  existing `user_id`/`nickname` nodes are supported; every original image URL is retained.
- [OCR](https://github.com/LLOneBot/LuckyLilliaBot/blob/dc2b2b90092ef0f571b5546878288b83fff1e436/src/onebot11/action/go-cqhttp/OCRImage.ts):
  `ocr_image` accepts `image` and returns `texts[].text`. `onebot` and the legacy
  `napcat` OCR provider setting share this parser. OCR failure still forwards the
  original image with an unverified/failure notice; Japanese accuracy is unverified.

Local mocked tests cover forwarding three images, repeated Japanese text, OCR,
failed acknowledgements, signed admin commands and durable group delivery.
Windows PowerShell 5.1 checks cover migration preservation, old launcher fallback,
adapter mismatch, offline/API failure and callback generation without real QQ calls.
No real LLBot login, group receipt, OCR or long-running stability test has been
performed. There is no measured performance or disconnection comparison with NapCat.
Changing adapters does not resolve RSSHub, site access, Java exits or laptop sleep.
LLBot's HTTP implementation can log API tokens; review/redact its logs before sharing.

To return to NapCat, stop LLBot and Java, restore the NapCat HTTP address/Token,
set `QQ_ADAPTER=napcat`, and restore its signed HTTP-client callback using the same
command secret. Keep the current JAR, data, group flags and original group ID.
`QQ_OCR_PROVIDER=onebot` also works with NapCat; `napcat` remains a supported alias.
