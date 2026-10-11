# Read-only regression checks. No task registration, real QQ calls or process termination.
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'operations-lib.ps1')
function Assert-Equal($Actual,$Expected,[string]$Name) { if($Actual -ne $Expected){throw ('FAILED: '+$Name)} }
foreach($file in (Get-ChildItem -LiteralPath $PSScriptRoot -Filter '*.ps1' -File)) {
    $tokens=$null;$errors=$null
    $null=[Management.Automation.Language.Parser]::ParseFile($file.FullName,[ref]$tokens,[ref]$errors)
    if($errors.Count){throw ('Syntax failure: '+$file.Name)}
}
function Invoke-RestMethod { return @{status='ok';retcode=0;data=@{online=$script:online}} }
$script:online=$true
Assert-Equal (Get-QqHealth ([Uri]'http://qq.example') @{}) 'QQ_OK' 'online'
$script:online=$false
Assert-Equal (Get-QqHealth ([Uri]'http://qq.example') @{}) 'QQ_OFFLINE_RELOGIN_REQUIRED' 'offline with cached identity'
function Invoke-RestMethod { throw 'synthetic connection failure; secret must not be returned' }
Assert-Equal (Get-QqHealth ([Uri]'http://qq.example') @{}) 'QQ_UNAVAILABLE' 'safe unavailable'
$now=[DateTimeOffset]::UtcNow
$snapshot=@{pollingEnabled=$true;polling=@{lastStartedAt=$now.AddSeconds(-300).ToString('o');lastFetchSuccessful=$true};sources=@()}
Assert-Equal (Get-PollingHealth $snapshot $now 300) 'POLLING_OK' 'normal quiet feed'
$snapshot.polling.lastStartedAt=$now.AddHours(-1).ToString('o')
Assert-Equal (Get-PollingHealth $snapshot $now 300) 'POLLING_STALE' 'stalled polling'
$snapshot.polling.lastFetchSuccessful=$false
Assert-Equal (Get-PollingHealth $snapshot $now 300) 'SOURCE_FETCH_FAILED' 'source failure'
$snapshot.pollingEnabled=$false
Assert-Equal (Get-PollingHealth $snapshot $now 300) 'POLLING_DISABLED' 'disabled polling'
$snapshot.pollingEnabled=$true;$snapshot.polling.lastStartedAt=$null
Assert-Equal (Get-PollingHealth $snapshot $now 300) 'POLLING_NOT_STARTED' 'no completed startup check'
Assert-Equal (Get-RestartDelay 1) 60 'first restart backoff'
Assert-Equal (Get-RestartDelay 100) 900 'bounded restart backoff'
Assert-Equal (Get-BotInstanceKey 'D:/TCG Bot') (Get-BotInstanceKey 'd:/tcg bot') 'stable folder identity'
Write-Host 'PASS: PowerShell syntax, QQ online/offline/error, polling health, restart backoff, folder identity.'
