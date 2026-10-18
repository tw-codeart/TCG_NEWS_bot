# Read-only status polling and explicitly requested local actions. Never return credentials.
. (Join-Path $PSScriptRoot 'operations-lib.ps1')
function Get-PanelConfig([string]$Directory) {
    $path=Join-Path $Directory 'config.env'
    if(-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw 'PANEL_CONFIG_MISSING' }
    $values=@{}
    foreach($line in [IO.File]::ReadAllLines($path)) {
        if(-not $line.Trim() -or $line.TrimStart().StartsWith('#')) { continue }
        $parts=$line -split '=',2
        if($parts.Count -ne 2) { throw 'PANEL_CONFIG_INVALID' }
        $values[$parts[0].Trim()]=$parts[1].Trim()
    }
    return $values
}
function Get-PanelSupervisorState([string]$Directory) {
    $mutex=$null;$acquired=$false
    try {
        $mutex=[Threading.Mutex]::OpenExisting(('Local\TCGNewsBot-'+(Get-BotInstanceKey $Directory)))
        try {$acquired=$mutex.WaitOne(0)} catch [Threading.AbandonedMutexException] {$acquired=$true}
        if($acquired) { return 'STOPPED' }
        return 'RUNNING'
    } catch [Threading.WaitHandleCannotBeOpenedException] { return 'STOPPED' }
    catch { return 'UNKNOWN' }
    finally {if($mutex){if($acquired){$mutex.ReleaseMutex()};$mutex.Dispose()}}
}
function Get-PanelTimestamp($Value) {
    $parsed=[DateTimeOffset]::MinValue
    if($Value -and [DateTimeOffset]::TryParse([string]$Value,[ref]$parsed)) {return $parsed.ToUniversalTime().ToString('o')}
    return $null
}
function Get-PanelCount($Value) {
    $count=0L
    if([long]::TryParse([string]$Value,[ref]$count) -and $count -ge 0){return $count}
    return 0L
}
function Get-ControlPanelSnapshot([string]$Directory) {
    $now=[DateTimeOffset]::UtcNow
    $result=[ordered]@{checkedAt=$now.ToString('o');configReady=$false;qqState='QQ_CONFIG_MISSING';javaReady=$false;
        supervisor=(Get-PanelSupervisorState $Directory);snapshotFresh=$false;polling='JAVA_UNAVAILABLE';lastFetch=$null;
        qqLastHealthyAt=$null;qqUnhealthySince=$null;napcatRecoveryState='DISABLED';napcatRecoveryAttempts=0L;fetched=0L;saved=0L;duplicates=0L;rows=@();canPreview=$false;canBackfill=$false;canBackfillWs=$false;canBackfillOcg=$false;ocgEnabled=$false;qqEnabled=$false}
    $supervisorData=$null
    try {
        $supervisorData=[IO.File]::ReadAllText((Join-Path $Directory 'operations-status.json'))|ConvertFrom-Json
        $stamp=Get-PanelTimestamp $supervisorData.updatedAt
        $result.snapshotFresh=$stamp -and [Math]::Abs(($now-[DateTimeOffset]::Parse($stamp)).TotalSeconds) -le 120
        $result.qqLastHealthyAt=Get-PanelTimestamp $supervisorData.qqLastHealthyAt
        $result.qqUnhealthySince=Get-PanelTimestamp $supervisorData.qqUnhealthySince
        $code=[string]$supervisorData.napcatRecoveryState
        if($code -match '^[A-Z_]{1,40}$'){$result.napcatRecoveryState=$code}
        $result.napcatRecoveryAttempts=Get-PanelCount $supervisorData.napcatRecoveryAttempts
    } catch { $supervisorData=$null }
    try {
        $settings=Get-PanelConfig $Directory
        $base=$null
        if(-not [Uri]::TryCreate($settings.QQ_API_BASE_URL,[UriKind]::Absolute,[ref]$base) -or $base.Scheme -notin @('http','https') -or $base.UserInfo -or $base.Query -or $base.Fragment){throw 'PANEL_CONFIG_INVALID'}
        $result.configReady=$true;$result.qqEnabled=$settings.QQ_ENABLED -eq 'true'
        if($result.qqEnabled) {
            $headers=@{};if($settings.QQ_ACCESS_TOKEN){$headers.Authorization='Bearer '+$settings.QQ_ACCESS_TOKEN}
            $result.qqState=Get-QqHealth $base $headers
        } else { $result.qqState='QQ_DISABLED' }
    } catch { $result.qqState=if(Test-Path (Join-Path $Directory 'config.env')){'QQ_CONFIG_INVALID'}else{'QQ_CONFIG_MISSING'} }
    try {
        $api=Invoke-RestMethod 'http://127.0.0.1:8080/api/status' -TimeoutSec 3
        if(-not $api.apiRevision -or $null -eq $api.storageReady){throw 'PANEL_API_INVALID'}
        $result.javaReady=$true
        $delivery=Invoke-RestMethod 'http://127.0.0.1:8080/api/notifications/status' -TimeoutSec 3
        $result.polling=Get-PollingHealth $delivery $now ([int]$api.pollingIntervalSeconds)
        if(-not $api.storageReady){$result.polling='STORAGE_NOT_READY'}
        $result.lastFetch=Get-PanelTimestamp $delivery.polling.lastCompletedAt
        foreach($name in @('fetched','saved','duplicates')){$result[$name]=Get-PanelCount $delivery.polling.$name}
        $rows=@()
        foreach($source in $delivery.sources) {
            if($source.sourceId -notin @('ws-jp-rsshub-card-reveals','ocg-jp-rsshub-card-reveals','ocg-jp-yugioh-jp-card-reveals')){continue}
            $reason=[string]$source.lastErrorReason
            if($reason -notmatch '^[A-Z_]{1,48}$'){$reason=''}
            $rows+=@{sourceId=$source.sourceId;pending=(Get-PanelCount $source.pendingItems);sent=(Get-PanelCount $source.sentItems);
                lastSent=(Get-PanelTimestamp $source.lastSentAt);reason=$reason}
        }
        $result.rows=$rows
        # Manual previews target the active API's fixed test group, only while this folder owns Java.
        $result.canPreview=$result.configReady -and $result.qqState -eq 'QQ_OK' -and $api.qqEnabled -eq $true -and $delivery.qqConfigured -eq $true -and
            $result.supervisor -eq 'RUNNING' -and $result.snapshotFresh -and $supervisorData.javaManaged -eq $true
        $result.ocgEnabled=$api.ocgEnabled -eq $true
        $result.canBackfill=$api.backfillRevision -eq 'calendar-history-v2' -and $result.configReady -and $api.qqEnabled -eq $true -and $delivery.qqConfigured -eq $true -and
            $result.supervisor -eq 'RUNNING' -and $result.snapshotFresh -and $supervisorData.javaManaged -eq $true
        $result.canBackfillWs=$result.canBackfill -and $api.sources -contains 'ws-jp-rsshub-card-reveals'
        $result.canBackfillOcg=$result.canBackfill -and $result.ocgEnabled -and $api.sources -contains 'ocg-jp-rsshub-card-reveals'
    } catch {if(-not $result.javaReady){$result.polling='JAVA_UNAVAILABLE'}else{$result.polling='DELIVERY_STATUS_UNAVAILABLE'}}
    return [pscustomobject]$result
}
function Start-PanelSupervisor([string]$Directory) {
    if((Get-PanelSupervisorState $Directory) -ne 'STOPPED'){throw 'PANEL_SUPERVISOR_NOT_STOPPED'}
    $settings=Get-PanelConfig $Directory
    if($settings.QQ_ENABLED -ne 'true' -or $settings.RSSHUB_POLLING_ENABLED -ne 'true' -or $settings.QQ_TEST_GROUP_ID -notmatch '^[1-9][0-9]*$' -or -not $settings.TWITTER_AUTH_TOKEN){throw 'PANEL_CONFIG_INCOMPLETE'}
    $script=Join-Path $Directory 'run-bot.ps1'
    if(-not(Test-Path -LiteralPath $script -PathType Leaf)){throw 'PANEL_FILES_MISSING'}
    $null=Start-Process 'powershell.exe' -ArgumentList @('-NoProfile','-ExecutionPolicy','Bypass','-File',('"'+$script+'"')) -WorkingDirectory $Directory -WindowStyle Hidden -PassThru
}
function Stop-PanelSupervisor([string]$Directory) {
    if((Get-PanelSupervisorState $Directory) -ne 'RUNNING'){throw 'PANEL_SUPERVISOR_NOT_RUNNING'}
    $status=[IO.File]::ReadAllText((Join-Path $Directory 'operations-status.json'))|ConvertFrom-Json
    $session=[Guid]::Empty
    if(-not [Guid]::TryParse([string]$status.session,[ref]$session)){throw 'PANEL_SESSION_INVALID'}
    [IO.File]::WriteAllText((Join-Path $Directory 'operations.stop'),$session.ToString(),(New-Object Text.UTF8Encoding($false)))
}
function Invoke-PanelPreview([string]$Directory,[string]$Game) {
    if($Game -notin @('WEISS_SCHWARZ','YUGIOH_OCG')){throw 'PANEL_GAME_INVALID'}
    if(-not (Get-ControlPanelSnapshot $Directory).canPreview){throw 'PANEL_PREVIEW_NOT_READY'}
    try {
        $response=Invoke-RestMethod -Method Post -Uri ('http://127.0.0.1:8080/api/notifications/posts?game='+$Game+'&limit=1') -TimeoutSec 120
        if($null -eq $response.attempted -or $null -eq $response.sent){throw 'PANEL_API_INVALID'}
        return [pscustomobject]@{attempted=(Get-PanelCount $response.attempted);sent=(Get-PanelCount $response.sent);failed=@($response.errors | Where-Object {$null -ne $_}).Count -gt 0}
    } catch {throw 'PANEL_PREVIEW_FAILED'}
}
function Get-PanelWebUiUrl([string]$Directory) {
    $url='http://127.0.0.1:6099/webui/'
    $path=Join-Path $Directory 'operations.local.json'
    if(Test-Path -LiteralPath $path){$settings=[IO.File]::ReadAllText($path)|ConvertFrom-Json;if($settings.qqWebUiUrl){$url=[string]$settings.qqWebUiUrl}}
    $uri=$null
    if(-not [Uri]::TryCreate($url,[UriKind]::Absolute,[ref]$uri) -or -not $uri.IsLoopback -or $uri.Scheme -notin @('http','https') -or $uri.UserInfo -or $uri.Query -or $uri.Fragment){throw 'PANEL_WEBUI_URL_INVALID'}
    return $uri.AbsoluteUri
}
function Get-PanelBackfillPreviewLines($Preview) {
    $from=([DateTimeOffset]::Parse([string]$Preview.from)).ToOffset([TimeSpan]::FromHours(9)).ToString('yyyy-MM-dd')
    $through=([DateTimeOffset]::Parse([string]$Preview.until)).ToOffset([TimeSpan]::FromHours(9)).AddDays(-1).ToString('yyyy-MM-dd')
    '日本时间 {0} 至 {1}，不含今天。' -f $from,$through
    '{0} 条推文，{1} 条卡图/资讯记录。' -f $Preview.posts,$Preview.records
    '可新增入队：{0}；已待发：{1}；已确认发送：{2}。' -f $Preview.toQueue,$Preview.alreadyPending,$Preview.alreadySent
    foreach($post in @($Preview.entries | Select-Object -First 8)) {
        $title=[string]$post.title
        if($title.Length -gt 65){$title=$title.Substring(0,65)+'…'}
        '• {0}（{1} 条记录）' -f $title,$post.records
    }
    if(@($Preview.sources | Where-Object {$_.errorId}).Count){'部分数据源检查失败，请查看日志，结果可能不完整。'}
    '仅能补到当前RSS和数据库里的内容。旧成功标记已删除时，曾发过的消息也可能重复。'
    '确认将选中的记录放入配置测试群的待发队列？自动抓取会按原有限额处理。'
}
function Invoke-PanelBackfillPreview([string]$Directory,[string]$Game) {
    if($Game -notin @('WEISS_SCHWARZ','YUGIOH_OCG')){throw 'PANEL_GAME_INVALID'}
    $snapshot=Get-ControlPanelSnapshot $Directory
    if(-not $snapshot.canBackfill){throw 'PANEL_BACKFILL_NOT_READY'}
    if($Game -eq 'YUGIOH_OCG' -and -not $snapshot.canBackfillOcg){throw 'PANEL_OCG_SOURCE_DISABLED'}
    if($Game -eq 'WEISS_SCHWARZ' -and -not $snapshot.canBackfillWs){throw 'PANEL_BACKFILL_NOT_READY'}
    try {return Invoke-RestMethod -Method Post -Uri ('http://127.0.0.1:8080/api/notifications/backfill/preview?game='+$Game+'&days=7') -TimeoutSec 120}
    catch {throw 'PANEL_BACKFILL_FAILED'}
}
function Invoke-PanelBackfillConfirm([string]$Directory,[string]$PreviewId) {
    $id=[Guid]::Empty
    if(-not [Guid]::TryParse($PreviewId,[ref]$id)){throw 'PANEL_BACKFILL_FAILED'}
    if(-not (Get-ControlPanelSnapshot $Directory).canBackfill){throw 'PANEL_BACKFILL_NOT_READY'}
    try{return Invoke-RestMethod -Method Post -Uri ('http://127.0.0.1:8080/api/notifications/backfill/confirm?previewId='+$id.ToString()) -TimeoutSec 20}
    catch {throw 'PANEL_BACKFILL_FAILED'}
}
