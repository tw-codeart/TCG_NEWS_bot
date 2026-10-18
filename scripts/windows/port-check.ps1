# A failed Windows query is not evidence that a port is free.
function Test-BotPortOccupied([int]$Port) {
    try { return @(Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction Stop | Where-Object {$null -ne $_}).Count -gt 0 }
    catch {
        if($_.CategoryInfo.Category -eq [Management.Automation.ErrorCategory]::ObjectNotFound -and
            $_.FullyQualifiedErrorId -like 'CmdletizationQuery_NotFound*') { return $false }
        throw 'PORT_CHECK_UNAVAILABLE: Cannot verify local listening ports. Check Windows process/network query permissions before starting services.'
    }
}
