$ErrorActionPreference='Stop'
$path=Join-Path $PSScriptRoot 'operations-status.json'
if(Test-Path -LiteralPath $path) { [IO.File]::ReadAllText($path)|Write-Host }
else { Write-Host 'No supervisor status. Manual services may still be running.' }
foreach($uri in @('http://127.0.0.1:8080/api/status','http://127.0.0.1:8080/api/notifications/status')) {
    try { Invoke-RestMethod $uri -TimeoutSec 5|ConvertTo-Json -Depth 6 }
    catch { Write-Host 'JAVA_STATUS_UNAVAILABLE: check logs/operations.log and logs/errors.log.' }
}
