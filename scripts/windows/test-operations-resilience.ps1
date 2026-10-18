param([switch]$Fixture)
# Windows PowerShell 5.1, synthetic failures only; never calls real QQ or kills processes.
$ErrorActionPreference='Stop'
if(-not $Fixture) {
    $directory=Join-Path ([IO.Path]::GetTempPath()) ('TCG-Resilience-Test-'+[Guid]::NewGuid().ToString('N'))
    $null=New-Item -ItemType Directory -Path $directory
    foreach($name in @('operations-lib.ps1','port-check.ps1','test-operations-resilience.ps1','check-bot.ps1')) { Copy-Item -LiteralPath (Join-Path $PSScriptRoot $name) -Destination $directory }
    try { & (Join-Path $directory 'test-operations-resilience.ps1') -Fixture }
    finally {
        $resolved=[IO.Path]::GetFullPath($directory)
        if((Split-Path -Parent $resolved) -eq ([IO.Path]::GetTempPath().TrimEnd('\')) -and (Split-Path -Leaf $resolved) -match '^TCG-Resilience-Test-[a-f0-9]{32}$') { Remove-Item -LiteralPath $resolved -Recurse -Force }
    }
    return
}
. (Join-Path $PSScriptRoot 'operations-lib.ps1')
function Assert-True($Condition,[string]$Name) { if(-not $Condition){throw ('FAILED: '+$Name)} }
$null=New-Item -ItemType Directory -Path (Join-Path $PSScriptRoot 'logs/operations.log') -Force
$warnings=@(Write-OperationEvent 'SYNTHETIC_EVENT' 3>&1)
Assert-True ($warnings -match 'OPERATIONS_LOG_WRITE_FAILED') 'log failure reported safely'
$script:operationsSettings=@{desktopAlerts=$true}
$global:tcgResilienceAlertStarts=0
function Start-Process { $global:tcgResilienceAlertStarts++;throw 'synthetic secret-bearing process launch failure' }
Show-OperationAlert 'QQ_UNAVAILABLE'
Assert-True ($global:tcgResilienceAlertStarts -eq 1) 'failed alert launch isolated'
Assert-True (-not (Start-QqAdapterOnce 'relative-launcher.cmd')) 'invalid QQ launcher isolated'
Assert-True ($global:tcgResilienceAlertStarts -eq 1) 'invalid path starts no process'
$dummyLauncher=Join-Path $PSScriptRoot 'synthetic-launcher.cmd'
[IO.File]::WriteAllText($dummyLauncher,'@echo off')
Assert-True (-not (Start-QqAdapterOnce $dummyLauncher)) 'QQ launcher start failure isolated'
Assert-True ($global:tcgResilienceAlertStarts -eq 2) 'QQ launch failure actually injected'
$statusPath=Join-Path $PSScriptRoot 'operations-status.json'
$null=New-Item -ItemType Directory -Path ($statusPath+'.tmp')
Assert-True (-not (Write-OperationStatus $statusPath @{health='test'})) 'status failure returns false'
Remove-Item -LiteralPath ($statusPath+'.tmp')
Assert-True (Write-OperationStatus $statusPath @{health='recovered';updatedAt=[DateTimeOffset]::UtcNow.ToString('o')}) 'status writing recovers'
Assert-True (([IO.File]::ReadAllText($statusPath)|ConvertFrom-Json).health -eq 'recovered') 'atomic status snapshot readable'
$stop=Join-Path $PSScriptRoot 'operations.stop'
$null=New-Item -ItemType Directory -Path $stop
Assert-True (-not (Test-BotStopRequest $stop 'session')) 'unreadable stop does not stop workers'
Remove-Item -LiteralPath $stop
[IO.File]::WriteAllText($stop,'wrong-session')
Assert-True (-not (Test-BotStopRequest $stop 'session')) 'stale stop rejected'
[IO.File]::WriteAllText($stop,'session')
Assert-True (Test-BotStopRequest $stop 'session') 'current stop accepted'

$global:tcgResilienceStarts=0
function Get-NetTCPConnection { return $null }
function Start-BotWorker {
    $global:tcgResilienceStarts++
    if($global:tcgResilienceStarts -eq 1) { throw 'synthetic start failure' }
    return @{HasExited=$false}
}
$now=[DateTimeOffset]::UtcNow
$worker=@{definition=@{name='JAVA';port=8080;script='start-app.ps1'};process=$null;external=$false;failures=0;nextStart=[DateTimeOffset]::MinValue;startedAt=$null}
Update-BotWorker $worker $now
Assert-True ($worker.process -eq $null -and $worker.failures -eq 1 -and $worker.nextStart -eq $now.AddSeconds(60)) 'failed start schedules bounded retry'
Update-BotWorker $worker $now.AddSeconds(59)
Assert-True ($global:tcgResilienceStarts -eq 1) 'no retry before backoff'
Update-BotWorker $worker $now.AddSeconds(60)
Assert-True ($worker.process -ne $null -and $global:tcgResilienceStarts -eq 2) 'retry succeeds on deadline'
$external=@{definition=@{name='RSSHUB'};external=$true}
Update-BotWorker $external $now
Assert-True ($global:tcgResilienceStarts -eq 2) 'external workers untouched'
function Get-NetTCPConnection { return @{State='Listen'} }
$occupied=@{definition=@{name='JAVA';port=8080};process=$null;external=$false;nextStart=[DateTimeOffset]::MinValue}
Update-BotWorker $occupied $now
Assert-True ($occupied.external -and $global:tcgResilienceStarts -eq 2) 'occupied port never starts duplicate'

$global:tcgResilienceStatusQueries=0
function Invoke-RestMethod { $global:tcgResilienceStatusQueries++;return @{storageReady=$true} }
[IO.File]::WriteAllText($statusPath,'malformed-json')
$output=(& (Join-Path $PSScriptRoot 'check-bot.ps1') 6>&1|Out-String)
Assert-True ($global:tcgResilienceStatusQueries -eq 2) 'bad supervisor snapshot does not block Java checks'
Assert-True ($output -match 'SUPERVISOR_STATUS_UNAVAILABLE') 'bad snapshot reported'
[IO.File]::WriteAllText($statusPath,(@{updatedAt=$now.AddMinutes(-10).ToString('o')}|ConvertTo-Json))
$output=(& (Join-Path $PSScriptRoot 'check-bot.ps1') 6>&1|Out-String)
Assert-True ($output -match 'SUPERVISOR_STATUS_STALE') 'stale heartbeat reported'
Write-Host 'PASS: log/alert/status/stop failure isolation, status recovery, worker backoff, external ownership, stale snapshots and direct Java checks.'
