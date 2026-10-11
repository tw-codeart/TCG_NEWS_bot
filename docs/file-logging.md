# File logging and Windows startup

The Java application writes UTF-8 `logs/bot.log` (INFO and above) and
`logs/errors.log` (WARN and above), relative to its working directory.
Override the directory with `--bot.logging.path=...` if necessary.
Each file rolls at 10 MB or daily. Archives are retained for seven days
with a 100 MB archive cap per appender. Active files are additional to this cap.
Logs are local runtime data and are excluded from Git.

Every API request gets a server-generated `X-Request-ID` response header.
Handled errors include the same ID as `errorId`, either at the response root
or in a notification result's `errors` list. Search the files for this ID.
Fetch summaries retain their existing structure; use the response header to
find source failure logs. Scheduled work has no HTTP request ID; use its
timestamp and source ID. Unexpected API failures return a generic message
while recording the exception trace locally.

The output layout masks known credential fields, Authorization/Cookie headers,
and process values of `QQ_ACCESS_TOKEN` and `TWITTER_AUTH_TOKEN` in messages
and exception traces. This is a defensive measure, not permission to log
credentials. Review logs before sharing: they can still contain local paths,
news content and other private operational information.

## Portable Windows installation

Tracked launcher templates are in `scripts/windows`. They belong beside the
portable bundle's `app.jar`, `runtime`, `rsshub`, `config.env` and `data` folders.
They are ASCII scripts, compatible with Windows PowerShell 5.1, and select
UTF-8 output without changing the system language. Window titles identify
Java on port 8080 and RSSHub on port 1200. Keep both service windows open.

For an update, stop Java, replace `app.jar` and the launcher scripts, preserve
`config.env` and `data`, then start `start-app.cmd`. Do not run two Java processes
against the same H2 database. NapCat still runs separately.

To check the installed version:

```powershell
Invoke-RestMethod 'http://127.0.0.1:8080/api/status'
```

`operationsRevision` should be `file-logging-v1`.

If a command fails and PowerShell displays no response body:

```powershell
curl.exe --noproxy "*" -i -X POST "http://127.0.0.1:8080/api/notifications/test"
```

This endpoint sends one QQ test message when QQ is enabled and configured.
Read `errorId` on failure and search `logs/errors.log` for that ID.

## Planned

Separate WS/OCG database files with preservation of existing records are now
available as an opt-in; see [separate storage](separated-game-storage.md).
Administrator-only group subscription commands are available as an opt-in;
see [group subscriptions](qq-group-subscriptions.md). Durable pending delivery is
described in [automatic delivery](automatic-qq-delivery.md). Single-entry startup,
local alerts, logon tasks and safe retention are in [Windows operations](windows-operations.md).
