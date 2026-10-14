$ErrorActionPreference='Stop'
$path=Join-Path $PSScriptRoot 'operations-status.json'
if(Test-Path -LiteralPath $path) {
    try {
        $snapshot=[IO.File]::ReadAllText($path)|ConvertFrom-Json
        $age=([DateTimeOffset]::UtcNow-[DateTimeOffset]::Parse($snapshot.updatedAt)).TotalSeconds
        if($age -gt 120 -or $age -lt -120) { Write-Host 'SUPERVISOR_STATUS_STALE: check the monitor window; this snapshot may be outdated.' }
        $snapshot|ConvertTo-Json
    } catch { Write-Host 'SUPERVISOR_STATUS_UNAVAILABLE: local snapshot unreadable; checking Java directly.' }
}
else { Write-Host 'No supervisor status. Manual services may still be running.' }
foreach($uri in @('http://127.0.0.1:8080/api/status','http://127.0.0.1:8080/api/notifications/status')) {
    try { Invoke-RestMethod $uri -TimeoutSec 5|ConvertTo-Json -Depth 6 }
    catch { Write-Host 'JAVA_STATUS_UNAVAILABLE: check logs/operations.log and logs/errors.log.' }
}
