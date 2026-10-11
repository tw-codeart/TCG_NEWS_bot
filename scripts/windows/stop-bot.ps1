$ErrorActionPreference='Stop'
$statusFile=Join-Path $PSScriptRoot 'operations-status.json'
if(-not (Test-Path -LiteralPath $statusFile)) { Write-Host 'No supervisor status found. Stop manually started services in their own windows.';exit }
$status=Get-Content -LiteralPath $statusFile -Raw|ConvertFrom-Json
[IO.File]::WriteAllText((Join-Path $PSScriptRoot 'operations.stop'),[string]$status.session,(New-Object Text.UTF8Encoding($false)))
Write-Host 'Stop requested. Wait up to one minute. Only supervised Java/RSSHub are stopped; NapCat is kept running.'
