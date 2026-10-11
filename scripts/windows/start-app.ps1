. (Join-Path $PSScriptRoot 'common.ps1')
Assert-FreePort 8080
$env:RSSHUB_ENABLED = 'true'
$env:RSSHUB_BASE_URL = 'http://127.0.0.1:1200'
$env:RSSHUB_WS_ROUTE = '/twitter/user/wstcg'
$null = New-Item -ItemType Directory -Path (Join-Path $PSScriptRoot 'data') -Force
$null = New-Item -ItemType Directory -Path (Join-Path $PSScriptRoot 'logs') -Force
$env:SPRING_DATASOURCE_URL = 'jdbc:h2:file:./data/tcg-news'
$env:SERVER_ADDRESS = '127.0.0.1'
$env:SERVER_PORT = '8080'
if ([string]::IsNullOrWhiteSpace($env:BOT_RETENTION_ENABLED)) { $env:BOT_RETENTION_ENABLED = 'true' }
# The X login credential belongs only to RSSHub.
Remove-Item Env:TWITTER_AUTH_TOKEN -ErrorAction SilentlyContinue
Write-Host 'Java API: http://127.0.0.1:8080/api/news. Keep this window open.'
Write-Host 'Java logs: logs/bot.log and logs/errors.log. API errors include errorId.'
& (Join-Path $PSScriptRoot 'runtime\java\bin\java.exe') '-Dfile.encoding=UTF-8' '-jar' 'app.jar'
if ($LASTEXITCODE -ne 0) { throw "Java exited with code $LASTEXITCODE." }
