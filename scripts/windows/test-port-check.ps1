# Synthetic Windows query results only. No sockets are opened or processes launched.
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'operations-lib.ps1')
function Assert-True($Value,[string]$Name) { if(-not $Value){throw ('FAILED: '+$Name)} }
$global:tcgPortQueryMode='empty'
function Get-NetTCPConnection {
    [CmdletBinding()]
    param([int]$LocalPort,[string]$State)
    if($global:tcgPortQueryMode -eq 'empty') { return }
    if($global:tcgPortQueryMode -eq 'null') { return $null }
    if($global:tcgPortQueryMode -eq 'occupied') { return @{LocalPort=$LocalPort;State=$State} }
    $category=if($global:tcgPortQueryMode -eq 'not-found'){[Management.Automation.ErrorCategory]::ObjectNotFound}else{[Management.Automation.ErrorCategory]::PermissionDenied}
    $id=if($global:tcgPortQueryMode -eq 'not-found'){'CmdletizationQuery_NotFound'}else{'Synthetic_AccessDenied'}
    $errorRecord=New-Object Management.Automation.ErrorRecord ((New-Object Exception 'private upstream details'),$id,$category,$null)
    $PSCmdlet.ThrowTerminatingError($errorRecord)
}
Assert-True (-not (Test-BotPortOccupied 8080)) 'empty result is free'
$global:tcgPortQueryMode='null'
Assert-True (-not (Test-BotPortOccupied 8080)) 'null result is free'
$global:tcgPortQueryMode='not-found'
Assert-True (-not (Test-BotPortOccupied 8080)) 'Windows no-match result is free'
$global:tcgPortQueryMode='occupied'
Assert-True (Test-BotPortOccupied 8080) 'listener detected'
$global:tcgPortQueryMode='denied'
$failed=$false
try { $null=Test-BotPortOccupied 8080 } catch {
    $failed=$true
    Assert-True ($_.Exception.Message -like 'PORT_CHECK_UNAVAILABLE:*' -and $_.Exception.Message -notmatch 'private upstream') 'safe query error'
}
Assert-True $failed 'unknown query failure never means free'
$global:tcgPortStarts=0
function Start-BotWorker { $global:tcgPortStarts++;return @{HasExited=$false} }
function Write-OperationEvent([string]$Code) { $global:tcgPortLastCode=$Code }
$now=[DateTimeOffset]::UtcNow
$worker=@{definition=@{name='JAVA';port=8080;script='start-app.ps1'};external=$false;process=$null;failures=0;nextStart=[DateTimeOffset]::MinValue;startedAt=$null}
Update-BotWorker $worker $now
Assert-True ($global:tcgPortStarts -eq 0 -and $worker.nextStart -eq $now.AddSeconds(60)) 'query denial schedules retry without starting a duplicate'
Assert-True ($global:tcgPortLastCode -eq 'JAVA_PORT_CHECK_FAILED_RETRY_PENDING') 'port query failure has a specific safe log code'
$global:tcgPortQueryMode='empty'
Update-BotWorker $worker $now.AddSeconds(60)
Assert-True ($global:tcgPortStarts -eq 1) 'query recovery allows normal launch'
Write-Host 'PASS: empty/no-match/listening/denied port queries, safe failure, no duplicate launch and recovery.'
