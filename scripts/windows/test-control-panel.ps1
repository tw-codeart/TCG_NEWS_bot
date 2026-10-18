param([string]$PreviewPath='')
# Local mocks only. Does not contact QQ, open a desktop window, or start the bot.
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'panel-lib.ps1')
function Assert-True($Value,[string]$Name){if(-not $Value){throw ('FAILED: '+$Name)}}
function Assert-Equal($Actual,$Expected,[string]$Name){if($Actual -ne $Expected){throw ('FAILED: '+$Name+' actual='+$Actual)}}
function Assert-Throws([scriptblock]$Action,[string]$Code){try{& $Action;throw 'NO_ERROR'}catch{Assert-Equal $_.Exception.Message $Code 'expected safe error'}}
$fixture=Join-Path ([IO.Path]::GetTempPath()) ('TCG-Panel-Test-'+[Guid]::NewGuid().ToString('N'))
$null=New-Item -ItemType Directory -Path $fixture
try {
    # Real JSON numbers must be formatted as text, not added to a string as integers.
    $previewJson=@'
{"from":"2026-10-15T15:00:00Z","until":"2026-10-17T15:00:00Z","posts":3,"records":7,"toQueue":7,"alreadyPending":0,"alreadySent":0,"entries":[{"title":"今日のカード","records":3}],"sources":[]}
'@
    $previewResult=$previewJson|ConvertFrom-Json
    $previewLines=@(Get-PanelBackfillPreviewLines $previewResult)
    Assert-Equal $previewLines[0] '日本时间 2026-10-16 至 2026-10-17，不含今天。' 'calendar dates in confirmation dialog'
    Assert-Equal $previewLines[1] '3 条推文，7 条卡图/资讯记录。' 'numeric JSON counts can render before confirmation'
    Assert-Equal $previewLines[2] '可新增入队：7；已待发：0；已确认发送：0。' 'queue counts rendered'
    Assert-Equal $previewLines[3] '• 今日のカード（3 条记录）' 'multi-image post count rendered'
    $previewResult.posts=0;$previewResult.records=0;$previewResult.toQueue=0;$previewResult.entries=@()
    Assert-Equal (@(Get-PanelBackfillPreviewLines $previewResult))[1] '0 条推文，0 条卡图/资讯记录。' 'empty result renders'
    $previewResult.sources=@([pscustomobject]@{errorId='synthetic-error-id'})
    Assert-True ((@(Get-PanelBackfillPreviewLines $previewResult) -join "`n") -match '部分数据源检查失败') 'partial failure remains visible'
    Assert-Equal (Get-PanelSupervisorState $fixture) 'STOPPED' 'no unrelated monitor ownership'
    $start=[DateTimeOffset]::Parse('2026-10-18T01:00:00Z')
    $observation=Get-QqObservation $null 'QQ_OK' $start
    Assert-True (-not $observation.qqUnhealthySince) 'online has no outage'
    $observation=Get-QqObservation $observation 'QQ_OFFLINE_RELOGIN_REQUIRED' $start.AddMinutes(1)
    Assert-Equal $observation.qqLastHealthyAt $start.ToString('o') 'retain last online'
    Assert-Equal $observation.qqUnhealthySince $start.AddMinutes(1).ToString('o') 'first observed outage'
    $observation=Get-QqObservation $observation 'QQ_UNAVAILABLE' $start.AddMinutes(2)
    Assert-Equal $observation.qqUnhealthySince $start.AddMinutes(1).ToString('o') 'reason changes retain outage start'
    $observation=Get-QqObservation $observation 'QQ_OK' $start.AddMinutes(3)
    Assert-True (-not $observation.qqUnhealthySince) 'recovery clears outage'
    Assert-Equal $observation.qqLastHealthyAt $start.AddMinutes(3).ToString('o') 'recovery records online time'
    function Get-PanelSupervisorState {return $script:supervisor}
    $script:supervisor='RUNNING';$script:online=$true;$script:qqError='';$script:postFailure=$false;$script:posts=0;$script:launches=0
    $script:now=[DateTimeOffset]::UtcNow
    $script:api=@{apiRevision='post-preview-v2';backfillRevision='calendar-history-v2';sources=@('ws-jp-rsshub-card-reveals','ocg-jp-rsshub-card-reveals');ocgEnabled=$true;storageReady=$true;pollingIntervalSeconds=300;qqEnabled=$true}
    $script:delivery=@{qqConfigured=$true;pollingEnabled=$true;polling=@{lastStartedAt=$script:now.ToString('o');lastCompletedAt=$script:now.ToString('o');lastFetchSuccessful=$true;fetched=7;saved=2;duplicates=5};sources=@(
        @{sourceId='ws-jp-rsshub-card-reveals';pendingItems=2;sentItems=9;lastSentAt=$script:now.ToString('o');lastErrorReason=$null},
        @{sourceId='ocg-jp-rsshub-card-reveals';pendingItems=0;sentItems=4;lastErrorReason='SYNTHETIC_SECRET_TEXT?token=hidden'},
        @{sourceId='ocg-jp-yugioh-jp-card-reveals';pendingItems=0;sentItems=0},
        @{sourceId='UNTRUSTED_SECRET_SOURCE';pendingItems=900})}
    function Invoke-RestMethod {
        param($Uri,$Method,$Headers,$ContentType,$Body,$TimeoutSec)
        if($Uri -like '*/get_status'){
            Assert-Equal $Headers.Authorization 'Bearer synthetic-private-token' 'local authorization applied'
            if($script:qqError -eq 'AUTH'){
                $errorObject=New-Object Exception('private upstream body')
                $errorObject|Add-Member NoteProperty Response ([pscustomobject]@{StatusCode=401})
                throw $errorObject
            }
            if($script:qqError -eq 'NETWORK'){throw 'private upstream failure'}
            return @{status='ok';retcode=0;data=@{online=$script:online}}
        }
        if($Uri -like '*/api/status'){return $script:api}
        if($Uri -like '*/api/notifications/status'){return $script:delivery}
        if($Uri -like '*/api/notifications/backfill/preview?*'){
            Assert-True ($Uri -match '^http://127\.0\.0\.1:8080/api/notifications/backfill/preview\?game=(WEISS_SCHWARZ|YUGIOH_OCG)&days=7$') 'weekly backfill preview without group override'
            return [pscustomobject]@{previewId='11111111-1111-4111-8111-111111111111';records=3;posts=1;toQueue=3}
        }
        if($Uri -like '*/api/notifications/backfill/confirm?*'){
            Assert-Equal $Uri 'http://127.0.0.1:8080/api/notifications/backfill/confirm?previewId=11111111-1111-4111-8111-111111111111' 'server preview selection only'
            return [pscustomobject]@{queued=3;alreadyPending=0;alreadySent=0}
        }
        if($Uri -like '*/api/notifications/posts?*'){
            Assert-Equal $Method 'Post' 'explicit preview action'
            Assert-True ($Uri -match '^http://127\.0\.0\.1:8080/api/notifications/posts\?game=(WEISS_SCHWARZ|YUGIOH_OCG)&limit=1$') 'fixed endpoint and one whole post'
            $script:posts++;if($script:postFailure){throw 'private remote failure'}
            return @{attempted=1;sent=1;errors=@()}
        }
        throw 'Unexpected HTTP request'
    }
    function Start-Process {param($FilePath,$ArgumentList,$WorkingDirectory,$WindowStyle,[switch]$PassThru);$script:launches++;Assert-Equal $WindowStyle 'Hidden' 'hidden monitor only'}
    $snapshot=Get-ControlPanelSnapshot $fixture
    Assert-Equal $snapshot.qqState 'QQ_CONFIG_MISSING' 'fresh deployment needs config'
    @'
QQ_ENABLED=true
QQ_API_BASE_URL=http://127.0.0.1:21837
QQ_ACCESS_TOKEN=synthetic-private-token
QQ_TEST_GROUP_ID=987654321
RSSHUB_POLLING_ENABLED=true
TWITTER_AUTH_TOKEN=synthetic-private-x-token
'@ | Set-Content -LiteralPath (Join-Path $fixture 'config.env') -Encoding ASCII
    $status=@{updatedAt=$script:now.ToString('o');javaManaged=$true;session=[Guid]::NewGuid().ToString();qqLastHealthyAt=$script:now.ToString('o');napcatRecoveryState='COOLDOWN';napcatRecoveryAttempts=2}
    $status|ConvertTo-Json|Set-Content -LiteralPath (Join-Path $fixture 'operations-status.json') -Encoding ASCII
    $snapshot=Get-ControlPanelSnapshot $fixture
    Assert-Equal $snapshot.qqState 'QQ_OK' 'live online check'
    Assert-Equal $snapshot.napcatRecoveryState 'COOLDOWN' 'recovery state without account details'
    Assert-Equal $snapshot.napcatRecoveryAttempts 2 'recovery retry count surfaced'
    Assert-True $snapshot.canPreview 'owned fresh healthy monitor permits preview'
    Assert-True $snapshot.canBackfill 'matching JAR revision enables backfill'
    Assert-True $snapshot.canBackfillWs 'WS source enables its own backfill control'
    Assert-True $snapshot.canBackfillOcg 'OCG source enables its own backfill control'
    Assert-Equal $snapshot.rows.Count 3 'known sources included'
    Assert-Equal $snapshot.rows[0].pending 2 'pending records surfaced'
    $json=$snapshot|ConvertTo-Json -Depth 6
    Assert-True ($json -notmatch 'synthetic-private|987654321|UNTRUSTED_SECRET|SYNTHETIC_SECRET|private upstream') 'snapshot contains no credentials or raw upstream text'
    $script:online=$false
    Assert-True (Get-ControlPanelSnapshot $fixture).canBackfill 'offline QQ still permits safe backfill queue preparation'
    $backfill=Invoke-PanelBackfillPreview $fixture 'WEISS_SCHWARZ'
    Assert-Equal (Invoke-PanelBackfillConfirm $fixture $backfill.previewId).queued 3 'explicit backfill confirmation'
    Assert-Throws {Invoke-PanelBackfillConfirm $fixture 'arbitrary&groupId=99'} 'PANEL_BACKFILL_FAILED'
    Assert-Equal (Get-ControlPanelSnapshot $fixture).qqState 'QQ_OFFLINE_RELOGIN_REQUIRED' 'offline despite live API'
    Assert-Throws {Invoke-PanelPreview $fixture 'WEISS_SCHWARZ'} 'PANEL_PREVIEW_NOT_READY'
    Assert-Equal $script:posts 0 'offline sends nothing'
    $script:api.Remove('backfillRevision')
    Assert-True (-not (Get-ControlPanelSnapshot $fixture).canBackfill) 'old JAR disables unsupported controls'
    $script:api.backfillRevision='manual-history-v1'
    Assert-True (-not (Get-ControlPanelSnapshot $fixture).canBackfill) 'rolling-hour JAR is not mistaken for the calendar revision'
    $script:api.backfillRevision='calendar-history-v2'
    $script:api.ocgEnabled=$false
    Assert-True (-not (Get-ControlPanelSnapshot $fixture).canBackfillOcg) 'disabled OCG source disables its backfill control'
    Assert-Throws {Invoke-PanelBackfillPreview $fixture 'YUGIOH_OCG'} 'PANEL_OCG_SOURCE_DISABLED'
    $script:api.ocgEnabled=$true
    $script:online=$true;$script:qqError='AUTH'
    Assert-Equal (Get-ControlPanelSnapshot $fixture).qqState 'QQ_AUTH_FAILED' 'authentication failure distinguished'
    $script:qqError='NETWORK'
    Assert-Equal (Get-ControlPanelSnapshot $fixture).qqState 'QQ_UNAVAILABLE' 'unavailable API safely reported'
    $script:qqError=''
    $status.updatedAt=$script:now.AddMinutes(-5).ToString('o')
    $status|ConvertTo-Json|Set-Content (Join-Path $fixture 'operations-status.json') -Encoding ASCII
    Assert-True (-not (Get-ControlPanelSnapshot $fixture).canPreview) 'stale ownership blocks preview'
    $status.updatedAt=$script:now.ToString('o');$status.javaManaged=$false
    $status|ConvertTo-Json|Set-Content (Join-Path $fixture 'operations-status.json') -Encoding ASCII
    Assert-True (-not (Get-ControlPanelSnapshot $fixture).canPreview) 'external Java blocks preview'
    $status.javaManaged=$true
    $status|ConvertTo-Json|Set-Content (Join-Path $fixture 'operations-status.json') -Encoding ASCII
    $script:delivery.polling.lastFetchSuccessful=$false
    Assert-Equal (Get-ControlPanelSnapshot $fixture).polling 'SOURCE_FETCH_FAILED' 'fetch failure visible'
    $script:delivery.polling.lastFetchSuccessful=$true
    $result=Invoke-PanelPreview $fixture 'WEISS_SCHWARZ'
    Assert-Equal $result.sent 1 'preview response'
    Assert-True (-not $result.failed) 'empty errors is successful'
    $null=Invoke-PanelPreview $fixture 'YUGIOH_OCG'
    Assert-Equal $script:posts 2 'both games reuse existing API'
    Assert-Throws {Invoke-PanelPreview $fixture 'ARBITRARY'} 'PANEL_GAME_INVALID'
    $script:postFailure=$true
    Assert-Throws {Invoke-PanelPreview $fixture 'YUGIOH_OCG'} 'PANEL_PREVIEW_FAILED'
    Assert-Throws {Start-PanelSupervisor $fixture} 'PANEL_SUPERVISOR_NOT_STOPPED'
    Assert-Equal $script:launches 0 'duplicate start blocked'
    Stop-PanelSupervisor $fixture
    Assert-Equal ([IO.File]::ReadAllText((Join-Path $fixture 'operations.stop'))) $status.session 'scoped stop session'
    $script:supervisor='STOPPED'
    Assert-Throws {Stop-PanelSupervisor $fixture} 'PANEL_SUPERVISOR_NOT_RUNNING'
    Set-Content (Join-Path $fixture 'run-bot.ps1') '# test placeholder' -Encoding ASCII
    Start-PanelSupervisor $fixture
    Assert-Equal $script:launches 1 'explicit start only'
    $settings=Get-PanelConfig $fixture;$settings.QQ_ENABLED='false'
    ($settings.GetEnumerator()|ForEach-Object {$_.Key+'='+$_.Value})|Set-Content (Join-Path $fixture 'config.env') -Encoding ASCII
    Assert-Equal (Get-ControlPanelSnapshot $fixture).qqState 'QQ_DISABLED' 'disabled transport'
    Assert-Throws {Start-PanelSupervisor $fixture} 'PANEL_CONFIG_INCOMPLETE'
    Assert-Equal (Get-PanelWebUiUrl $fixture) 'http://127.0.0.1:6099/webui/' 'default local management page'
    foreach($invalid in @('https://remote.example/webui/','http://127.0.0.1:6099/?token=secret','http://name:secret@localhost/')){
        @{qqWebUiUrl=$invalid}|ConvertTo-Json|Set-Content (Join-Path $fixture 'operations.local.json') -Encoding ASCII
        Assert-Throws {Get-PanelWebUiUrl $fixture} 'PANEL_WEBUI_URL_INVALID'
    }
    # An isolated runspace returns one safe snapshot, without any real network traffic.
    $worker=[Management.Automation.PowerShell]::Create()
    try {
        $null=$worker.AddScript('param($lib,$root) . $lib; function Invoke-RestMethod {throw "synthetic network failure"}; Get-ControlPanelSnapshot $root').AddArgument((Join-Path $PSScriptRoot 'panel-lib.ps1')).AddArgument($fixture)
        $pending=$worker.BeginInvoke();$values=@($worker.EndInvoke($pending))
        Assert-True (-not $worker.HadErrors) 'background polling has no leaked error stream'
        Assert-Equal $values.Count 1 'one runspace status object'
    } finally {$worker.Dispose()}
    . (Join-Path $PSScriptRoot 'panel-view.ps1')
    $view=New-ControlPanelView
    try {
        $snapshot.qqState='QQ_OFFLINE_RELOGIN_REQUIRED';$snapshot.canPreview=$false
        Set-ControlPanelView $view $snapshot
        Assert-True (-not $view.controls.ws.Enabled) 'offline UI prevents preview'
        $snapshot.qqState='QQ_OK';$snapshot.canPreview=$true
        Set-ControlPanelView $view $snapshot
        Assert-True $view.controls.ws.Enabled 'healthy UI permits explicit preview'
        Assert-Equal $view.controls.grid.Rows.Count 3 'visible source rows'
        Assert-True ($view.controls.backfillws.Text -match '前7天') 'weekly WS backfill control'
        Assert-True ($view.controls.backfillocg.Text -match '前7天') 'weekly OCG backfill control'
        Assert-True ($view.controls.recovery.Text -match '2/3') 'recovery attempts visible'
        if($PreviewPath){
            $null=$view.form.Handle;foreach($control in $view.form.Controls){$null=$control.Handle};$view.form.PerformLayout();$bitmap=New-Object Drawing.Bitmap($view.form.Width,$view.form.Height)
            try{$view.form.DrawToBitmap($bitmap,(New-Object Drawing.Rectangle(0,0,$bitmap.Width,$bitmap.Height)));$bitmap.Save($PreviewPath,[Drawing.Imaging.ImageFormat]::Png)}finally{$bitmap.Dispose()}
        }
    } finally {$view.form.Dispose()}
    Write-Host 'PASS: observations, online/offline/auth/unavailable/disabled, sanitized snapshots, ownership guards, scoped actions, both previews, background polling, WinForms rendering.'
} finally {
    $resolved=[IO.Path]::GetFullPath($fixture)
    if((Split-Path -Parent $resolved) -eq ([IO.Path]::GetTempPath().TrimEnd('\')) -and (Split-Path -Leaf $resolved) -match '^TCG-Panel-Test-[a-f0-9]{32}$'){Remove-Item -LiteralPath $resolved -Recurse -Force}
}
