# Local mocks only: no real QQ API, login, task registration or message delivery.
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'onebot-config.ps1')
. (Join-Path $PSScriptRoot 'onebot-preflight.ps1')
function Assert-Equal($Actual,$Expected,[string]$Name) { if($Actual -ne $Expected){throw ('FAILED: '+$Name)} }
function Assert-Failure([scriptblock]$Action,[string]$Code) {
    $failed=$false
    try { & $Action } catch { $failed=$true;if($_.Exception.Message -notlike ($Code+'*')) { throw } }
    if(-not $failed) { throw ('Expected failure: '+$Code) }
}
$keys=@('QQ_ADAPTER','QQ_OCR_PROVIDER','QQ_API_BASE_URL','QQ_ACCESS_TOKEN','QQ_COMMAND_SECRET','QQ_GROUP_SUBSCRIPTIONS_ENABLED','GAME_STORAGE_SEPARATED_ENABLED','QQ_TEST_GROUP_ID','QQ_ENABLED','RSSHUB_POLLING_ENABLED')
$original=@{};foreach($key in $keys){$original[$key]=[Environment]::GetEnvironmentVariable($key,'Process')}
$originalDirectory=(Get-Location).Path
$testRoot=Join-Path ([IO.Path]::GetTempPath()) ('TCG-OneBot-Test-'+[Guid]::NewGuid().ToString('N'))
try {
    $env:QQ_ADAPTER=''
    Assert-Equal (Get-QqAdapterName) 'napcat' 'legacy adapter default'
    $env:QQ_ADAPTER='llbot'
    Assert-Equal (Get-QqAdapterName) 'llbot' 'LLBot selection'
    $env:QQ_ADAPTER='invalid';Assert-Failure { Get-QqAdapterName } 'QQ_ADAPTER'
    $settings=@{napcatLauncherPath='D:/Old/NapCat.bat';qqLauncherPath=''}
    Assert-Equal (Get-QqLauncherPath $settings 'llbot') '' 'no legacy NapCat launch in LLBot mode'
    Assert-Equal (Get-QqLauncherPath $settings 'napcat') 'D:/Old/NapCat.bat' 'legacy launcher retained'
    $settings.qqLauncherPath='D:/LLBot/LLBot.exe'
    Assert-Equal (Get-QqLauncherPath $settings 'llbot') $settings.qqLauncherPath 'generic LLBot launcher'
    $secret='synthetic-command-secret'
    $callback=New-OneBotCallbackConfig 'llbot' $secret
    Assert-Equal $callback.type 'http-post' 'LLBot connection type'
    Assert-Equal $callback.messageFormat 'array' 'LLBot array messages'
    Assert-Equal $callback.url 'http://127.0.0.1:8080/api/qq/events' 'signed callback destination'
    Assert-Equal $callback.token $secret 'separate callback secret'
    Assert-Equal $callback.reportSelfMessage $false 'no own message loop'
    Assert-Equal (New-OneBotCallbackConfig 'napcat' $secret).messagePostFormat 'array' 'NapCat callback retained'

    $global:tcgOneBotTestOffline=$false;$global:tcgOneBotTestApiFailure=$false;$global:tcgOneBotTestRequests=0;$global:tcgOneBotTestAppName='LLOneBot'
    function Invoke-RestMethod {
        param($Method,$Uri,$Headers,$ContentType,$Body,$TimeoutSec)
        $global:tcgOneBotTestRequests++
        if($global:tcgOneBotTestApiFailure) { throw 'synthetic API failure with private upstream content' }
        if($Uri -like '*/get_version_info') { return @{status='ok';retcode=0;data=@{app_name=$global:tcgOneBotTestAppName;protocol_version='v11'}} }
        if($Uri -like '*/get_login_info') { return @{status='ok';retcode=0;data=@{user_id=123}} }
        if($Uri -like '*/get_status') { return @{status='ok';retcode=0;data=@{online=(-not $global:tcgOneBotTestOffline)}} }
        throw 'Unexpected action; setup must never send messages.'
    }
    Confirm-OneBotLogin ([Uri]'http://onebot.example') @{}
    Assert-Equal $global:tcgOneBotTestRequests 2 'login and live status both checked'
    $global:tcgOneBotTestAppName='NapCat'
    Assert-Failure { Confirm-OneBotLogin ([Uri]'http://onebot.example') @{} 'llbot' } 'QQ_ADAPTER_MISMATCH'
    $global:tcgOneBotTestAppName='LLOneBot'
    $global:tcgOneBotTestOffline=$true;Assert-Failure { Confirm-OneBotLogin ([Uri]'http://onebot.example') @{} } 'QQ_OFFLINE'
    $global:tcgOneBotTestOffline=$false;$global:tcgOneBotTestApiFailure=$true
    Assert-Failure { Confirm-OneBotLogin ([Uri]'http://onebot.example') @{} } 'QQ_UNAVAILABLE'

    $env:QQ_ADAPTER=''
    $null=New-Item -ItemType Directory -Path $testRoot
    foreach($name in @('common.ps1','onebot-config.ps1','onebot-preflight.ps1','enable-llbot.ps1','enable-group-subscriptions.ps1')) { Copy-Item -LiteralPath (Join-Path $PSScriptRoot $name) -Destination $testRoot }
    $configPath=Join-Path $testRoot 'config.env'
    $config=@('QQ_API_BASE_URL=http://onebot.example','QQ_ACCESS_TOKEN=synthetic-api-token',('QQ_COMMAND_SECRET='+$secret),
        'QQ_TEST_GROUP_ID=42','QQ_ENABLED=true','RSSHUB_POLLING_ENABLED=true','QQ_GROUP_SUBSCRIPTIONS_ENABLED=true',
        'GAME_STORAGE_SEPARATED_ENABLED=true','QQ_OCR_PROVIDER=napcat')
    [IO.File]::WriteAllLines($configPath,$config)
    $data=Join-Path $testRoot 'data';$null=New-Item -ItemType Directory -Path $data
    $stamp=Join-Path $data 'synthetic-stamps.txt';[IO.File]::WriteAllText($stamp,'keep-existing-sentAt-and-subscriptions')
    $before=(Get-FileHash $configPath).Hash;$dataBefore=(Get-FileHash $stamp).Hash
    function Get-NetTCPConnection { return $null }
    Assert-Failure { & (Join-Path $testRoot 'enable-llbot.ps1') } 'QQ_UNAVAILABLE'
    Assert-Equal (Get-FileHash $configPath).Hash $before 'failure preserves config'
    Assert-Equal (Test-Path (Join-Path $testRoot 'llbot-http-post.local.json')) $false 'failure creates no callback'
    $global:tcgOneBotTestApiFailure=$false
    $global:tcgOneBotTestAppName='NapCat'
    Assert-Failure { & (Join-Path $testRoot 'enable-llbot.ps1') } 'QQ_ADAPTER_MISMATCH'
    Assert-Equal (Get-FileHash $configPath).Hash $before 'wrong adapter preserves config'
    $global:tcgOneBotTestAppName='LLOneBot'
    & (Join-Path $testRoot 'enable-llbot.ps1')
    $after=[IO.File]::ReadAllLines($configPath)
    foreach($line in $config | Where-Object {$_ -notlike 'QQ_OCR_PROVIDER=*'}) { if($after -notcontains $line) { throw 'Existing setting lost during LLBot setup.' } }
    if($after -notcontains 'QQ_ADAPTER=llbot' -or $after -notcontains 'QQ_OCR_PROVIDER=onebot') { throw 'LLBot setup flags missing.' }
    Assert-Equal (Get-FileHash $stamp).Hash $dataBefore 'existing delivery data untouched'
    $private=[IO.File]::ReadAllText((Join-Path $testRoot 'llbot-http-post.local.json'))|ConvertFrom-Json
    Assert-Equal $private.token $secret 'existing callback key retained'
    # Existing group setup must also generate the LLBot dialect, retaining the secret.
    & (Join-Path $testRoot 'enable-group-subscriptions.ps1')
    $private=[IO.File]::ReadAllText((Join-Path $testRoot 'llbot-http-post.local.json'))|ConvertFrom-Json
    Assert-Equal $private.token $secret 'group setup retains LLBot secret'
    Assert-Equal (Test-Path (Join-Path $testRoot 'napcat-http-client.local.json')) $false 'no NapCat callback in LLBot mode'
    $config[-1]='QQ_OCR_PROVIDER=tesseract';[IO.File]::WriteAllLines($configPath,$config)
    & (Join-Path $testRoot 'enable-llbot.ps1')
    if([IO.File]::ReadAllLines($configPath) -notcontains 'QQ_OCR_PROVIDER=tesseract') { throw 'Local OCR choice changed.' }
    Write-Host 'PASS: adapter/launcher compatibility, online preflight, callback dialect, failure safety, migration preservation and group setup.'
} finally {
    Set-Location -LiteralPath $originalDirectory
    foreach($key in $keys) { [Environment]::SetEnvironmentVariable($key,$original[$key],'Process') }
    # Delete only the exact test-created temporary directory after verifying its parent/name.
    $resolved=[IO.Path]::GetFullPath($testRoot)
    if((Split-Path -Parent $resolved) -eq ([IO.Path]::GetTempPath().TrimEnd('\')) -and (Split-Path -Leaf $resolved) -match '^TCG-OneBot-Test-[a-f0-9]{32}$' -and (Test-Path -LiteralPath $resolved)) {
        Remove-Item -LiteralPath $resolved -Recurse -Force
    }
}
