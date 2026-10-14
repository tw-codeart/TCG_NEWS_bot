# Windows operations and optional Japanese OCR

QQ operations now support both NapCat and LLBot over OneBot 11. See
[LLBot migration and daily checks](llbot-migration.md); `check-qq.cmd` checks the
selected adapter's API/online status. Existing NapCat entry points remain compatible.

These scripts target the existing portable Windows bundle, not Docker. They belong
beside `app.jar`, `runtime`, `rsshub`, `config.env` and `data`. No real account
credentials, NapCat binaries or database files are distributed in an update.

## One startup entry point

Stop the previously started Java and RSSHub windows before using `start-bot.cmd`.
Leave NapCat logged in. Automatic QQ delivery must already be configured/enabled
(`QQ_ENABLED=true` and `RSSHUB_POLLING_ENABLED=true`). The supervisor starts the
existing RSSHub and Java scripts hidden, checks health every 15 seconds, and
restarts its own exited workers with 60-second exponential backoff capped at
15 minutes. After ten minutes of uptime the failure backoff resets.

Only owned processes are restarted/stopped. If ports 8080/1200 were already
occupied, the supervisor reports an external process and monitors it without
taking ownership or starting a duplicate. It does not restart an alive but slow
Java process: a send might be in flight. A stale poll is reported instead.
The shared folder mutex rejects a second supervisor instance.

Supervisor v3 isolates diagnostic failures: an unwritable operations log/status
snapshot, an unreadable stop signal, or a failed popup launch is reported with a
fixed safe code and does not shut down Java/RSSHub. A failed worker launch gets
the same bounded restart backoff as an exited worker; the other worker continues.
Snapshots expose `JAVA_RESTART_PENDING`/`RSSHUB_RESTART_PENDING` when appropriate.
Invalid configuration still fails startup, and intentional shutdown still stops
only owned workers. This does not guarantee recovery from every OS/process fault.

- `start-bot.cmd`: start supervised RSSHub/Java and health monitoring.
- `stop-bot.cmd`: request shutdown of owned workers; NapCat is left running.
- `check-bot.cmd`: read supervisor status, API flags and pending/sent counts.
- `install-autostart.cmd`: install a current-user Windows **logon** task.
- `remove-autostart.cmd`: remove only this folder's logon task; keep all data.
- `check-ocr.cmd`: check provider configuration and recent OCR results, without sending.

The task uses an interactive limited principal, no stored Windows password and
single-instance execution. It runs after that user logs in, not before login.
Script failure can restart up to three times at one-minute intervals. Moving
the bundle requires removing the old folder's task and installing the new one.
No task is installed automatically during development/build or by unpacking.

## NapCat and alerts

Optional `operations.local.json` (private, ignored by Git):

```json
{
  "qqLauncherPath": "D:/NapCat/launcher.bat",
  "preventSleep": true,
  "desktopAlerts": true
}
```

Use your existing actual launcher path; the example is not an installer path.
For LLBot, use its actual launcher instead. An existing `napcatLauncherPath` is
supported only when `QQ_ADAPTER` is unset or `napcat`; LLBot ignores that old path.
Use `/` or escape backslashes in JSON. An empty path means start NapCat manually.
When configured and its API is unreachable, the supervisor requests one launcher
start. It never repeatedly spawns QQ or terminates another QQ session. Use the
WebUI to log in if necessary. A configured API that responds but reports offline
is detected through OneBot `get_status.data.online`, not a cached bot identity.

Known NapCat versions support recovery attempts after silent disconnection;
failed quick login still requires scanning. This project cannot bypass login
expiry, account verification or guarantee an always-online QQ session. See the
[official NapCat release notes](https://github.com/NapNeko/NapCatQQ/releases).

Health changes are logged to `logs/operations.log`. Offline/auth/API problems,
source failures, stale polling and delivery failures produce a local desktop
popup; less than 512 MiB free on the bundle drive also raises a disk-space alert.
Alerts start after startup grace; unresolved issues remind at most every 30 minutes.
Popup windows close after ten seconds. Recovery is reported. Popups are local
to the logged-in user, not QQ messages. Errors remain available in logs even
when no interactive desktop is visible. `operations-status.json` is a local
snapshot, not proof that another machine or real group received a message.

If file logging is unavailable, the monitor console reports
`OPERATIONS_LOG_WRITE_FAILED`; do not expect that failed write to appear in the
log. Status failures report `OPERATIONS_STATUS_WRITE_FAILED`, popup launch failures
report `OPERATIONS_ALERT_START_FAILED`, and worker launch failures report
`JAVA_START_FAILED_RETRY_PENDING` or `RSSHUB_START_FAILED_RETRY_PENDING`.
`check-bot.cmd` warns about snapshots older than two minutes and still queries
Java when the local snapshot is malformed/unreadable. A stale snapshot alone is
not proof the service stopped. Fix folder permissions/free space rather than
deleting databases or delivery stamps.

Verification: `test-operations.ps1`, `test-onebot.ps1` and
`test-operations-resilience.ps1` passed on Windows PowerShell 5.1. The real-process
`test-supervisor-resilience.ps1` passed against local Node HTTP stubs, with actual
log/status-write and popup/start-launch failures injected. Observation cadence
was accelerated only in the temporary test copy; the 60-second restart delay was
unchanged. The test verified unrelated-worker continuity, status recovery,
duplicate rejection and scoped shutdown. It requires free ports 8080/1200/21837,
Node and permission to inspect Windows processes. It never sends real QQ messages.
Java was unchanged and Maven was not rerun in this script-only phase.

While active, `preventSleep=true` requests Windows keep the system awake; it
does not keep the display on or prevent manual sleep/shutdown. This request is
released on normal supervisor exit. The notebook still needs power/network.
The supervisor does not change system-wide power settings.

## Bounded logs and safe retention

Java logs retain their seven-day/100-MB-per-appender archive policy.
RSSHub output masks the configured X/API/callback secret values and rotates at
5 MiB with one previous file. Operations logs rotate at 1 MiB with one previous
file. Never share raw NapCat logs, QR codes or private settings without review.

Native Java startup enables `BOT_RETENTION_ENABLED=true` unless explicitly
configured otherwise. Generic Java deployment defaults it off. Once enabled,
cleanup runs after one minute and then daily, independently for WS and OCG.
`BOT_COMMAND_RETENTION_DAYS=30` (allowed 1-3650) removes old processed callback
markers; signed commands older than five minutes are already rejected.
Original-group binding markers are never deleted. Failures in one store are
logged and do not prevent cleanup in another.

News, pending deliveries, source cursors, subscriptions, success stamps and
migration backups are **retained**. Deleting these without a separate tombstone
strategy could resend historical news or lose recovery state. There is no
automatic news pruning or backup deletion. Monitor disk use; the daily cleanup
does not shrink H2's physical file immediately. `/api/status` exposes the last
cleanup summary without credentials.

## Optional local Japanese OCR

The existing NapCat provider remains default; installing this update does not
silently replace it or download an OCR application. An alternative runs locally
with Tesseract 5 and Japanese `jpn.traineddata`. Install a Windows build using the
links in [Tesseract's installation documentation](https://tesseract-ocr.github.io/tessdoc/Installation.html),
and install Japanese language data. Then configure absolute paths:

```dotenv
QQ_OCR_ENABLED=true
QQ_OCR_PROVIDER=tesseract
TESSERACT_EXECUTABLE=C:/Program Files/Tesseract-OCR/tesseract.exe
TESSERACT_DATA_DIRECTORY=C:/Program Files/Tesseract-OCR/tessdata
```

Run `check-ocr.cmd`, stop/restart the supervised bot to apply settings, then
manually preview one stored post. `QQ_OCR_PROVIDER=napcat` restores the old
provider; `QQ_OCR_ENABLED=false` skips OCR and still forwards originals.

The optional provider fetches only allowed official HTTPS image URLs, follows
no redirects, limits input to 10 MiB, applies a 15-second download deadline and
a 20-second OCR process deadline, and removes temporary input/output files on
success/failure. It passes original bytes with `-l jpn --oem 1 --psm 11` using
separate process arguments. No shell command interpolation, resizing, cropping,
watermark removal, permanent mirror or OCR output database is introduced.
An OS/process crash may leave temporary files for OS cleanup.

OCR output is unverified source text, **not** a reliable effect-only extraction
or translation. Empty output/configuration/timeouts/API failures are counted in
`/api/status.ocr`; a failed OCR still sends the original image with the existing
fallback. Complex layouts, small print and stylized card text may be misread.
Runtime counts reset when Java restarts. Actual Japanese card accuracy and
notebook Tesseract installation still require live validation.

## Verification boundary

On 2026-10-12, `mvn test` and `mvn package` each passed 184 tests. Tests cover
temporary-image cleanup, bounded image input, Japanese text preservation,
OCR failure fallback, retention in both game stores and preservation of bindings,
news and delivery stamps. Windows PowerShell 5.1 verified real synthetic worker
restart/shutdown, QQ offline/recovery detection, singleton locking, Unicode paths
and real Node output redaction/rotation. Task APIs were mocked; no real task was
registered on the development PC. Packaged Java with local RSS/QQ mocks verified
per-group restart retries without replay of successful groups with retention enabled.

Java regression tests use fixed fixtures/mocks. Windows script tests use synthetic
services, never real QQ credentials. Real notebook logon tasks, NapCat relogin,
real group commands and prolonged live WS/OCG delivery require user-side checks.
Neither a successful test nor an API acknowledgment proves visual group receipt.

Delivery remains at least once: acknowledgment loss or a stop before recording
success can still cause a repeat. Keeping durable stamps prevents ordinary
duplicate fetches from producing another send.
