. (Join-Path $PSScriptRoot 'common.ps1')
. (Join-Path $PSScriptRoot 'onebot-config.ps1')
. (Join-Path $PSScriptRoot 'onebot-preflight.ps1')
Assert-FreePort 8080
$base=$null
if(-not [Uri]::TryCreate($env:QQ_API_BASE_URL,[UriKind]::Absolute,[ref]$base) -or $base.Scheme -notin @('http','https') -or $base.UserInfo -or $base.Query -or $base.Fragment) { throw 'Set QQ_API_BASE_URL to the LLBot OneBot 11 HTTP API address.' }
$headers=@{}
if($env:QQ_ACCESS_TOKEN) { $headers.Authorization='Bearer '+$env:QQ_ACCESS_TOKEN }
# Read-only checks first; on failure no configuration or delivery data is changed.
Confirm-OneBotLogin -BaseUri $base -Headers $headers -Adapter 'llbot'
if($env:QQ_GROUP_SUBSCRIPTIONS_ENABLED -eq 'true') {
    if($env:GAME_STORAGE_SEPARATED_ENABLED -ne 'true' -or $env:QQ_COMMAND_SECRET.Length -lt 16) { throw 'Preserve separated storage and a valid existing QQ_COMMAND_SECRET for group mode.' }
    $privateFile=Write-OneBotCallbackConfig $PSScriptRoot 'llbot' $env:QQ_COMMAND_SECRET
}
$lines=@([IO.File]::ReadAllLines($configFile))
$settings=@('QQ_ADAPTER=llbot')
if($env:QQ_OCR_PROVIDER -ne 'tesseract') { $settings+='QQ_OCR_PROVIDER=onebot' }
foreach($setting in $settings) {
    $key=($setting -split '=',2)[0];$found=$false
    for($i=0;$i -lt $lines.Count;$i++) { if($lines[$i] -match ('^\s*'+[regex]::Escape($key)+'\s*=')) { $lines[$i]=$setting;$found=$true } }
    if(-not $found) { $lines+=$setting }
}
[IO.File]::WriteAllLines($configFile,$lines,(New-Object Text.UTF8Encoding($false)))
Write-Host 'LLBot selected. Existing group ID, secrets, polling, databases and delivery stamps were retained.'
if($privateFile) { Write-Host ('Configure LLBot OneBot HTTP POST using '+$privateFile+' before starting Java. Do not share this private file.') }
Write-Host 'Clear any old qqLauncherPath or point it to LLBot. The legacy napcatLauncherPath is ignored for LLBot.'
Write-Host 'Start start-bot.cmd after callback setup. No group message was sent by this setup.'
