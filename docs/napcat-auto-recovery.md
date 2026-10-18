# Weekly backfill and managed NapCat recovery

The panel and backfill API default to the seven previous complete dates in
Asia/Tokyo, excluding today. On October 18 the window is October 11–17. This
changes history selection, not RSSHub feed retention. Existing success stamps,
test-group restrictions, packet budgets and the 1000-record preview limit remain.
History missing from both RSSHub and H2 cannot be reconstructed by this feature.

## One-time setup

1. Update the application/scripts while collection is stopped. Preserve private
   configuration, NapCat installation/data and the bot's `data` folder.
2. Open `control-panel.cmd`. While the supervisor is stopped, choose
   **自动恢复设置**. Enable recovery, select the existing Windows **Node Shell**
   folder containing `node.exe` and `index.js`, and enter the **bot QQ account**,
   not the destination group number. No password is collected.
3. Save. Exit the old manually launched NapCat, then start collection from the
   panel. The monitor starts an owned worker with `node.exe index.js -q <account>`
   and `NAPCAT_QUICK_ACCOUNT`, reusing NapCat's saved login ticket. Complete the
   first login in NapCat WebUI if requested; keep the same account/configuration.
4. The panel shows recovery state and attempt count. An external-instance status
   means a manually launched instance is not owned; stop that instance yourself
   before transferring operation to the monitor.
5. Optionally run `install-autostart.cmd` once for the current user's Windows
   logon task. This does not log Windows in or start before user login. Keep the
   PC powered, connected and awake. Locking the session is different from logging
   off. The application does not change lid-close/hibernate policy.

Private settings go to `operations.local.json`; existing keys and `config.env`
are preserved. Recovery is disabled by default and applies only to a local
NapCat Node Shell API. LLBot behavior is unchanged. Other launcher formats need
separate validation and are not accepted by this setup.

## Recovery behavior

Checks normally occur about every 15 seconds plus HTTP request time. A missing
process may start immediately. A managed instance observed continuously offline
gets a 120-second grace period before restart is considered; attempts are at
least 300 seconds apart. Only the exact owned process tree may be stopped.
Manually started instances are not killed or duplicated. API token errors and
nonlocal APIs do not trigger restarts.

At most three starts/restarts are attempted during an outage. The last running
login page then remains available and the panel requests manual login. Ten
continuous observed minutes online reset the budget; brief online flaps do not.
A deliberate supervisor restart also starts a new recovery session. Intentional
collection stop terminates Java/RSSHub and NapCat workers created by that monitor;
manually started QQ remains untouched.

NapCat's official [Windows launcher source](https://github.com/NapNeko/NapCatQQ/blob/main/packages/napcat-shell-loader/launcher-win10.bat)
documents quick-login parameters. Its [WebUI initialization](https://github.com/NapNeko/NapCatQQ/blob/main/packages/napcat-webui-backend/index.ts)
uses `NAPCAT_QUICK_ACCOUNT`. A ticket may expire or QQ may request QR, device,
phone or CAPTCHA verification. This bot does not bypass those checks, store QQ
passwords or guarantee unattended authentication in those circumstances.

Collection and durable pending delivery continue independently of QQ downtime.
After login recovers, polling retries pending records within the normal budget
and stamps only confirmed success. Already queued news needs no manual replay.

## Diagnostics and verification

`operations-status.json` exposes state/counts without the QQ account/credentials.
Fixed transition codes go to `logs/operations.log`. NapCat stdout/stderr may
include WebUI tokens and QR data; the managed wrapper discards those streams
instead of copying them to shared bot logs. Use the local NapCat WebUI and its
own configured diagnostics. Inherited X/API/command/password environment variables
are removed from the child.

Windows PowerShell tests cover timing, retry caps, stable recovery, disabled/LLBot
modes, wrong-token/remote/external-process guards, settings preservation and panel
rendering. A real process test uses a local Node HTTP stub in a Chinese/space
path to check quick-login arguments, secret removal, offline/crash recovery and
external-instance protection. These tests do not run NapCat or authenticate QQ.
Real overnight stability and real automatic QQ relogin remain unverified.
