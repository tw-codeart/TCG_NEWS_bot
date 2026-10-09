# Windows notebook deployment

This bundle runs Java and RSSHub in Linux containers on Docker Desktop.
NapCat/QQ runs natively on the Windows laptop. Java connects to its OneBot v11
HTTP server through `host.docker.internal:3000`. No account, login data, token,
group ID, existing database, or image binary is included in the archive.

## Verification status

The included JAR passed all 96 Maven tests and `mvn package` on the development
computer. Java application code is unchanged by this deployment setup. The
Compose configuration is checked with the official standalone Docker Compose
tool. Both pinned image digests were resolved from the public registry.
Docker image building, container startup, container-to-NapCat connectivity,
named-volume permissions/persistence, and laptop deployment have **not** been
verified: Docker Engine is not installed on the preparation computer.
The archive is an online-build deployment bundle, not an offline image tarball.
The laptop must download the base and RSSHub images on first use.

## 1. Install Docker Desktop on the laptop

Use the [official Windows installer](https://docs.docker.com/desktop/setup/install/windows-install/)
for the laptop's CPU architecture, enable its WSL 2 backend, complete any required
Windows restart, and start Docker Desktop. Use Linux containers.
In a new PowerShell window, check:

```powershell
docker version
docker compose version
```

`docker version` must show a working Server section. Extract the deployment ZIP
to a short local path such as `D:\TCG-bot`; avoid nesting the bundle inside a
different Compose project. Change into the extracted folder before later commands.

## 2. Prepare NapCat on the laptop

Start and log in to NapCat on the laptop with the account that belongs to the
single test group. Configure an enabled OneBot **HTTP server**, port `3000`, and
an API access token. The laptop's container must be able to reach this server.
For the bridge-network setup, use a reachable host binding such as `0.0.0.0`
rather than relying on host-only `127.0.0.1`; require the API token for this
reachable binding. Firewall rules must permit the Docker Desktop backend to
connect. Keep the WebUI local where possible.

The API token is separate from NapCat's WebUI password and X's `auth_token`.
Native NapCat installation/configuration is not automated by this bundle.
See [NapCat startup](https://napneko.github.io/guide/boot/Shell) and
[network configuration](https://napneko.github.io/config/basic).

## 3. Fill in the laptop's local settings

```powershell
Copy-Item .env.example .env
notepad .env
```

Set `TWITTER_AUTH_TOKEN` from your own X browser login, the numeric
`QQ_TEST_GROUP_ID`, and the matching `QQ_ACCESS_TOKEN`. Do not share this file.
Leave `QQ_ENABLED=false` and `RSSHUB_POLLING_ENABLED=false` for the first startup.
`QQ_API_BASE_URL=http://host.docker.internal:3000` reaches native NapCat from the
Java container. Java reaches the RSSHub container at `http://rsshub:1200`.
Inside a container, `localhost` refers to that container, not Windows.

## 4. Validate and start

```powershell
docker compose --env-file .env config --quiet
docker compose --env-file .env up -d --build
docker compose ps
docker compose logs --tail 80 app
```

Avoid printing the fully rendered Compose configuration: it contains the local
credential values. The first startup builds the small Java runtime image using
the included prebuilt JAR; Maven and Java do not need to be installed on the laptop.
The RSSHub health check verifies only server readiness, not X login/feed validity.
Verify the [RSSHub feed](http://127.0.0.1:1200/twitter/user/wstcg) separately.
Both published ports are bound to Windows loopback, not the LAN.

## 5. Initialize history, then test QQ

```powershell
Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:8080/api/fetch' | ConvertTo-Json -Depth 5
```

This archive starts with a fresh H2 volume. The first successful nonempty RSSHub
batch records history without automatic QQ messages. Check fetch errors and the
`RSSHub QQ bootstrap` log. Existing website sources are also fetched by this API.

Set `QQ_ENABLED=true` in `.env`, then apply it:

```powershell
docker compose --env-file .env up -d
Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:8080/api/notifications/test' | ConvertTo-Json -Depth 5
```

Confirm the test group received the fixed message. An API failure returns safe
details; do not assume group receipt from container startup alone. If necessary,
use `POST /api/notifications/news?limit=1` for one stored-message preview.

After both the feed and group test succeed, set `RSSHUB_POLLING_ENABLED=true`
and keep `RSSHUB_POLLING_INTERVAL=PT5M` (or `PT15M`), then run `up -d` again.
After the initial interval, checks select only the RSSHub WS source. Only newly
saved records qualify for automatic notification. The existing 3-message default,
5-message maximum, history protection, and skip-without-replay behavior remain.
Once laptop deployment is verified, stop the desktop's corresponding polling
service to avoid two independent databases sending the same newly found post.

## Operations and data

```powershell
docker compose logs -f --tail 50 app
docker compose stop
docker compose start
docker compose down
```

`down` keeps the named H2 volume. `down -v` deletes stored news and initialization
markers and should not be used for an ordinary restart. Docker Desktop, native
NapCat, and the containers must remain running; a sleeping/off laptop cannot poll.
The restart policy restores containers after the Docker Engine restarts unless
they were explicitly stopped. It does not start Docker Desktop or log in to QQ.
No retry queue, database server, or additional game is added.

This bundle intentionally omits the desktop database. The existing data remain
on the desktop. Database migration/backup restoration is a separate step; do not
copy a live H2 file into the laptop volume while either application is running.
