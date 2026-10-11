. (Join-Path $PSScriptRoot 'common.ps1')
Assert-FreePort 1200
if ([string]::IsNullOrWhiteSpace($env:TWITTER_AUTH_TOKEN)) { throw 'Set TWITTER_AUTH_TOKEN in config.env.' }
$rssRoot = Join-Path $PSScriptRoot 'rsshub'
$manifest = Get-Content -LiteralPath (Join-Path $rssRoot 'dependency-links.json') -Raw | ConvertFrom-Json
foreach ($link in $manifest) {
    $linkPath = Join-Path $rssRoot $link.path
    if (-not (Test-Path -LiteralPath $linkPath)) {
        $null = New-Item -ItemType Directory -Path (Split-Path -Parent $linkPath) -Force
        $null = New-Item -ItemType Junction -Path $linkPath -Target (Join-Path $rssRoot $link.target)
    }
}
$env:NODE_ENV = 'production'
$env:CACHE_TYPE = 'memory'
$env:CACHE_EXPIRE = '300'
$env:LISTEN_INADDR_ANY = '0'
$env:DISABLE_IPV6 = '1'
$env:PORT = '1200'
$env:ENABLE_CLUSTER = 'false'
$env:LOGFILES = 'false'
$env:LOGGER_LEVEL = 'info'
Set-Location -LiteralPath $rssRoot
Write-Host 'RSSHub: http://127.0.0.1:1200/twitter/user/wstcg. Keep this window open.'
$null=New-Item -ItemType Directory -Path (Join-Path $PSScriptRoot 'logs') -Force
$rssLog=Join-Path $PSScriptRoot 'logs/rsshub.log'
$rssSecrets=@($env:TWITTER_AUTH_TOKEN,$env:QQ_ACCESS_TOKEN,$env:QQ_COMMAND_SECRET) | Where-Object { $_ -and $_.Length -gt 0 }
# Native stderr should be logged, not terminate Windows PowerShell 5.1's pipeline.
$ErrorActionPreference='Continue'
& (Join-Path $PSScriptRoot 'runtime\node\node.exe') '--max-http-header-size=32768' 'dist/index.mjs' 2>&1 | ForEach-Object {
    $safe=[string]$_
    foreach($secret in $rssSecrets) { $safe=$safe.Replace($secret,'[REDACTED]') }
    if((Test-Path -LiteralPath $rssLog) -and (Get-Item -LiteralPath $rssLog).Length -gt 5MB) { Move-Item -LiteralPath $rssLog -Destination ($rssLog+'.1') -Force }
    [IO.File]::AppendAllText($rssLog,$safe+[Environment]::NewLine,(New-Object Text.UTF8Encoding($false)))
    Write-Host $safe
}
$ErrorActionPreference='Stop'
if ($LASTEXITCODE -ne 0) { throw "RSSHub exited with code $LASTEXITCODE." }
