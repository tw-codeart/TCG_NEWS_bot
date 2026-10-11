$ErrorActionPreference = 'Stop'
$utf8 = New-Object System.Text.UTF8Encoding($false)
[Console]::OutputEncoding = $utf8
$OutputEncoding = $utf8
Set-Location -LiteralPath $PSScriptRoot
$configFile = Join-Path $PSScriptRoot 'config.env'
if (-not (Test-Path -LiteralPath $configFile)) {
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'config.example.env') -Destination $configFile
    throw 'Edit config.env first, then start again. Do not share its secrets.'
}
$allowed = @('BOT_RETENTION_ENABLED', 'BOT_COMMAND_RETENTION_DAYS', 'QQ_OCR_PROVIDER', 'TESSERACT_EXECUTABLE', 'TESSERACT_DATA_DIRECTORY', 'QQ_GROUP_SUBSCRIPTIONS_ENABLED', 'QQ_COMMAND_SECRET', 'GAME_STORAGE_SEPARATED_ENABLED', 'RSSHUB_OCG_ENABLED', 'RSSHUB_OCG_ROUTE', 'QQ_IMAGE_FORWARD_ENABLED', 'QQ_OCR_ENABLED', 'TWITTER_AUTH_TOKEN', 'QQ_ENABLED', 'QQ_API_BASE_URL', 'QQ_TEST_GROUP_ID', 'QQ_ACCESS_TOKEN', 'RSSHUB_POLLING_ENABLED', 'RSSHUB_POLLING_INTERVAL', 'RSSHUB_NOTIFICATION_BATCH_LIMIT')
foreach ($line in [System.IO.File]::ReadAllLines($configFile)) {
    if ($line.Trim().Length -eq 0 -or $line.TrimStart().StartsWith('#')) { continue }
    $parts = $line -split '=', 2
    if ($parts.Length -ne 2 -or $allowed -notcontains $parts[0].Trim()) { throw 'Invalid setting in config.env.' }
    [Environment]::SetEnvironmentVariable($parts[0].Trim(), $parts[1].Trim(), 'Process')
}
function Assert-FreePort([int] $Port) {
    if (Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue) {
        throw "Port $Port is already occupied. Stop the old service before starting this one."
    }
}
