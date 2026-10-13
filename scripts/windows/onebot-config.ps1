# Shared adapter configuration; no HTTP calls or private file writes when dot-sourced.
function Get-QqAdapterName {
    $name=$env:QQ_ADAPTER
    if([string]::IsNullOrWhiteSpace($name)) { return 'napcat' }
    if($name -notin @('napcat','llbot')) { throw 'QQ_ADAPTER must be napcat or llbot.' }
    return $name
}
function Get-QqLauncherPath($Settings,[string]$Adapter) {
    if($Settings.qqLauncherPath) { return [string]$Settings.qqLauncherPath }
    # An old NapCat path must never launch NapCat during an LLBot session.
    if($Adapter -eq 'napcat') { return [string]$Settings.napcatLauncherPath }
    return ''
}
function New-OneBotCallbackConfig([string]$Adapter,[string]$Secret) {
    if($Secret.Length -lt 16) { throw 'QQ_COMMAND_SECRET must have at least 16 characters.' }
    $common=@{enable=$true;url='http://127.0.0.1:8080/api/qq/events';reportSelfMessage=$false;token=$Secret;debug=$false}
    if($Adapter -eq 'llbot') {
        $common.type='http-post';$common.messageFormat='array'
        $common.reportOfflineMessage=$false;$common.enableHeart=$false;$common.heartInterval=60000
    } elseif($Adapter -eq 'napcat') {
        $common.name='tcg-news-events';$common.messagePostFormat='array'
    } else { throw 'Unsupported QQ adapter.' }
    return $common
}
function Write-OneBotCallbackConfig([string]$Directory,[string]$Adapter,[string]$Secret) {
    $client=New-OneBotCallbackConfig $Adapter $Secret
    $name=if($Adapter -eq 'llbot'){'llbot-http-post.local.json'}else{'napcat-http-client.local.json'}
    [IO.File]::WriteAllText((Join-Path $Directory $name),($client|ConvertTo-Json),(New-Object Text.UTF8Encoding($false)))
    return $name
}
