# NapCat test control panel

The native Windows bundle includes `control-panel.cmd`, a Chinese WinForms
operator panel for the existing NapCat/OneBot pipeline. It does not replace
NapCat's own WebUI or change Java persistence, deduplication or delivery stamps.

## Fresh setup

1. Fully extract the complete native bundle into a local folder such as
   `D:\TCG_bot`. The incremental update alone does not include Java, Node or RSSHub.
2. Install/start NapCat separately and log in with the existing test QQ account.
   Open its actual WebUI address from the launcher log; the usual address is
   `http://127.0.0.1:6099/webui/`. Ports may differ. Keep the launcher running.
3. In NapCat, enable a OneBot HTTP server on `127.0.0.1:3000`, use Array message
   format, and configure a private access token. Save/enable the server.
4. Open `control-panel.cmd`, click the configuration button and fill the local
   `config.env`. Set `QQ_ADAPTER=napcat`, `QQ_ENABLED=true`,
   `QQ_API_BASE_URL=http://127.0.0.1:3000`, `QQ_TEST_GROUP_ID`,
   `QQ_ACCESS_TOKEN`, `TWITTER_AUTH_TOKEN`, `RSSHUB_OCG_ENABLED=true`,
   `RSSHUB_POLLING_ENABLED=true` and `GAME_STORAGE_SEPARATED_ENABLED=true`.
   Keep `QQ_GROUP_SUBSCRIPTIONS_ENABLED=false` for the initial single test group.
   Keep the linked OCG website source disabled until its access is verified.
5. Start collection from the panel. This starts the existing supervised
   Java/RSSHub workers. The bundled runtimes require no Docker or Maven setup.
   Config edits apply after stopping and restarting collection.
6. After status becomes healthy and news has been stored, explicitly preview
   one WS or OCG post. All its stored image records use the existing backend
   packet rules. Preview is a manual resend and does not modify automatic stamps.

The local OneBot HTTP access token is separate from a QQ developer-platform bot
token and separate from NapCat's WebUI login token. A platform application's
approval does not configure this local connection. QQ login/verification still
has to succeed normally; the panel cannot bypass it.

No real group IDs, tokens, QQ/NapCat installers, logged-in account data or news
databases are included. Obtain NapCat from its
[official project](https://github.com/NapNeko/NapCatQQ/releases) and consult its
[WebUI guide](https://napneko.github.io/config/starter.shell).

## Visible health and actions

The panel polls OneBot `get_status.data.online`, rather than assuming a reachable
port or cached login identity means online. It distinguishes offline, unreachable,
authentication failure, API failure, disabled and missing configuration. Status
requests run in a background runspace so HTTP waits do not block the window.
The next panel check starts five seconds after the previous check completes;
requests have timeouts, so this is not an exact five-second detection guarantee.

The supervisor independently records `qqLastCheckedAt`, `qqLastHealthyAt` and
`qqUnhealthySince` in its v5 local status snapshot. These are observation times
within the current supervisor session, not the exact server-side logout time.
Error changes retain the first observed anomaly time; recovery clears it.
Existing rotating operations logs retain health transitions. A sound plays when
the open panel detects a new QQ failure state and its sound checkbox is enabled.

The panel also shows Java reachability, polling health, the last fetch counts,
and pending/confirmed record counts for each known source. Counts are records,
not numbers of forward packets. A healthy API is not proof of real group receipt.
Use a preview and inspect the test group to confirm end-to-end behavior.

Start/stop use the existing folder mutex and session-scoped stop signal. They do
not terminate arbitrary Java/QQ processes. Preview is enabled only while this
folder owns a running supervisor with a fresh snapshot and managed Java, and
the QQ/API checks succeed. It targets the backend's configured test group;
the panel accepts no group override and triggers no scraping from preview.

Buttons open the local config, logs folder and NapCat WebUI. An optional private
`operations.local.json` may override `qqWebUiUrl` with a loopback HTTP(S) URL;
credentials and token/query parameters are rejected. Use NapCat's WebUI login
screen for its token. Closing the panel leaves collection running. Stop it
explicitly before moving/updating the bundle. Do not delete `data` to fix login.

## One active account for this test build

Use the old test account as the single active sender. A second account may be
prepared as a manual backup, but automatic account failover, simultaneous
broadcasting and synchronization between two senders are not implemented.
Running two independent copies against the same groups can duplicate messages.
This panel improves failure visibility; it does not guarantee continuous QQ login.

## Optional managed recovery

With collection stopped, the **自动恢复设置** button configures the existing
NapCat Node Shell directory and bot QQ account. It can restart an owned failed
process and attempt saved-ticket quick login, with a grace period, cooldown and
three-attempt cap. External instances and required human login challenges are not
overridden. In this mode the stop button also stops the monitor-owned NapCat.
See [managed recovery](napcat-auto-recovery.md) for setup, timing and limitations.
The WS/OCG backfill buttons now preview the previous seven complete Japan dates.

## Verification

`test-control-panel.ps1` covers online/offline/authentication/unavailable/disabled
states, observation timestamps, safe snapshot fields, ownership guards, scoped
start/stop, fixed WS/OCG previews, asynchronous status collection and WinForms
construction/rendering using local mocks. No real QQ account is required.
Existing supervisor tests use local HTTP/process stubs, including timestamp
publication on offline/recovery. No real group receipt or laptop soak test was
performed for this panel phase. Java is unchanged; Maven was not rerun.
