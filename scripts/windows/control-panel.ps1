$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Windows.Forms
try {
    . (Join-Path $PSScriptRoot 'panel-lib.ps1')
    . (Join-Path $PSScriptRoot 'panel-view.ps1')
    . (Join-Path $PSScriptRoot 'panel-recovery-settings.ps1')
    [Windows.Forms.Application]::EnableVisualStyles()
    $view=New-ControlPanelView
    $script:panelTask=$null;$script:panelNextPoll=[DateTimeOffset]::MinValue;$script:panelPreviousQq=''
    $script:panelObservedBadSince=$null;$script:panelObservedHealthyAt=$null
    function Show-PanelError([string]$Code) {
        if($Code -eq 'PANEL_OCG_SOURCE_DISABLED'){
            $null=[Windows.Forms.MessageBox]::Show('游戏王 RSSHub 源未启用。打开配置，设置 RSSHUB_OCG_ENABLED=true，保存后停止并重新启动采集。','TCG Bot')
            return
        }
        if($Code -eq 'PANEL_BACKFILL_NOT_READY'){$Code='PANEL_PREVIEW_NOT_READY'}
        if($Code -eq 'PANEL_BACKFILL_FAILED'){$Code='PANEL_PREVIEW_FAILED'}
        $messages=@{PANEL_CONFIG_MISSING='先打开配置，填写 config.env。';PANEL_CONFIG_INCOMPLETE='请填写 X 凭据和测试群号，并启用 QQ 与自动抓取。';PANEL_SUPERVISOR_NOT_STOPPED='监控正在运行或状态无法确认，请先检查状态。';PANEL_SUPERVISOR_NOT_RUNNING='没有发现本目录运行的监控。';PANEL_SESSION_INVALID='监控状态不可读，未发送停止命令。';PANEL_PREVIEW_NOT_READY='请先启动本目录的监控，确认 QQ 在线、Java 已就绪。';PANEL_PREVIEW_FAILED='测试推送失败。请检查待发状态和 logs/errors.log 中的错误编号。';PANEL_WEBUI_URL_INVALID='管理页地址必须是本机 http/https 地址，不能带 Token 查询参数。'}
        $text=$messages[$Code];if(-not $text){$text='操作失败，请检查配置、目录权限和日志。'}
        $null=[Windows.Forms.MessageBox]::Show($text,'TCG Bot',[Windows.Forms.MessageBoxButtons]::OK,[Windows.Forms.MessageBoxIcon]::Warning)
    }
    function Start-PanelTask([string]$Kind,[string]$Game='') {
        if($script:panelTask){return}
        $ps=[Management.Automation.PowerShell]::Create()
        $null=$ps.AddScript('param($root,$kind,$value) . (Join-Path $root "panel-lib.ps1"); switch($kind){"preview"{Invoke-PanelPreview $root $value};"backfill"{Invoke-PanelBackfillPreview $root $value};"backfill-confirm"{Invoke-PanelBackfillConfirm $root $value};default{Get-ControlPanelSnapshot $root}}').AddArgument($PSScriptRoot).AddArgument($Kind).AddArgument($Game)
        $script:panelTask=@{ps=$ps;handle=$ps.BeginInvoke();kind=$Kind}
        if($Kind -eq 'preview'){$view.controls.ws.Enabled=$false;$view.controls.ocg.Enabled=$false;$view.controls.hint.Text='正在发送测试推文，请稍候…'}
        if($Kind -like 'backfill*'){$view.controls.backfillws.Enabled=$false;$view.controls.backfillocg.Enabled=$false;$view.controls.hint.Text='正在检查补发记录，请稍候…'}
    }
    $view.controls.start.Add_Click({try{Start-PanelSupervisor $PSScriptRoot;$script:panelNextPoll=[DateTimeOffset]::MinValue}catch{Show-PanelError $_.Exception.Message}})
    $view.controls.stop.Add_Click({try{Stop-PanelSupervisor $PSScriptRoot;$view.controls.hint.Text='已请求停止监控管理的 Java/RSSHub 和托管 NapCat；手动启动的 QQ 保留。'}catch{Show-PanelError $_.Exception.Message}})
    $view.controls.recoverySettings.Add_Click({try{$null=Show-PanelRecoverySettings $PSScriptRoot $view.form;$script:panelNextPoll=[DateTimeOffset]::MinValue}catch{Show-PanelError 'PANEL_CONFIG_INVALID'}})
    $view.controls.webui.Add_Click({try{Start-Process (Get-PanelWebUiUrl $PSScriptRoot)}catch{Show-PanelError 'PANEL_WEBUI_URL_INVALID'}})
    $view.controls.config.Add_Click({try{
        $path=Join-Path $PSScriptRoot 'config.env';if(-not(Test-Path -LiteralPath $path)){Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'config.example.env') -Destination $path}
        Start-Process 'notepad.exe' -ArgumentList ('"'+$path+'"')
    }catch{Show-PanelError 'PANEL_CONFIG_INVALID'}})
    $view.controls.logs.Add_Click({$path=Join-Path $PSScriptRoot 'logs';if(-not(Test-Path -LiteralPath $path)){$path=$PSScriptRoot};Start-Process 'explorer.exe' -ArgumentList ('"'+$path+'"')})
    $view.controls.ws.Add_Click({Start-PanelTask 'preview' 'WEISS_SCHWARZ'})
    $view.controls.ocg.Add_Click({Start-PanelTask 'preview' 'YUGIOH_OCG'})
    $view.controls.backfillws.Add_Click({Start-PanelTask 'backfill' 'WEISS_SCHWARZ'})
    $view.controls.backfillocg.Add_Click({Start-PanelTask 'backfill' 'YUGIOH_OCG'})
    $timer=New-Object Windows.Forms.Timer;$timer.Interval=300
    $timer.Add_Tick({
        if($script:panelTask -and $script:panelTask.handle.IsCompleted){
            $task=$script:panelTask;$script:panelTask=$null
            try{
                $data=@($task.ps.EndInvoke($task.handle))
                if($task.ps.HadErrors -or $data.Count -ne 1){throw 'PANEL_TASK_FAILED'}
                if($task.kind -eq 'preview'){
                    $result=$data[0];$suffix=if($result.failed){'有发送失败，请检查日志。'}else{'请到群里核对。'}
                    $null=[Windows.Forms.MessageBox]::Show(('尝试：'+$result.attempted+'，接口确认发送：'+$result.sent+'。'+$suffix),'测试推送')
                    $script:panelNextPoll=[DateTimeOffset]::MinValue
                }elseif($task.kind -eq 'backfill'){
                    $result=$data[0]
                    $lines=@(Get-PanelBackfillPreviewLines $result)
                    if($result.toQueue -gt 0 -and [Windows.Forms.MessageBox]::Show(($lines -join "`r`n"),'补发预览',[Windows.Forms.MessageBoxButtons]::YesNo,[Windows.Forms.MessageBoxIcon]::Question) -eq [Windows.Forms.DialogResult]::Yes){
                        Start-PanelTask 'backfill-confirm' ([string]$result.previewId)
                    }elseif($result.toQueue -eq 0){$null=[Windows.Forms.MessageBox]::Show(($lines[0..($lines.Count-2)] -join "`r`n")+"`r`n没有可新增入队的记录。",'补发预览')}
                    $script:panelNextPoll=[DateTimeOffset]::MinValue
                }elseif($task.kind -eq 'backfill-confirm'){
                    $result=$data[0]
                    $null=[Windows.Forms.MessageBox]::Show(('新增入队：'+$result.queued+'；已待发：'+$result.alreadyPending+'；已发送：'+$result.alreadySent+'。等待下一轮自动抓取处理；入队不代表群已收到。'),'补发已入队')
                    $script:panelNextPoll=[DateTimeOffset]::MinValue
                }else{
                    $snapshot=$data[0];$bad=$snapshot.qqState -in @('QQ_OFFLINE_RELOGIN_REQUIRED','QQ_UNAVAILABLE','QQ_AUTH_FAILED','QQ_API_FAILURE')
                    if($snapshot.qqState -eq 'QQ_OK'){$script:panelObservedHealthyAt=$snapshot.checkedAt;$script:panelObservedBadSince=$null;$snapshot.qqUnhealthySince=$null}
                    elseif($bad -and -not $script:panelObservedBadSince){$script:panelObservedBadSince=$snapshot.checkedAt}
                    if(-not $snapshot.qqUnhealthySince -and $bad){$snapshot.qqUnhealthySince=$script:panelObservedBadSince}
                    if(-not $snapshot.qqLastHealthyAt){$snapshot.qqLastHealthyAt=$script:panelObservedHealthyAt}
                    if($bad -and $script:panelPreviousQq -ne $snapshot.qqState -and $view.controls.sound.Checked){[Media.SystemSounds]::Exclamation.Play()}
                    $script:panelPreviousQq=$snapshot.qqState
                    Set-ControlPanelView $view $snapshot
                    $script:panelNextPoll=[DateTimeOffset]::UtcNow.AddSeconds(5)
                }
            }catch{if($task.kind -eq 'preview' -or $task.kind -like 'backfill*'){Show-PanelError 'PANEL_PREVIEW_FAILED'}else{$view.controls.hint.Text='状态检查失败，请查看日志。'};$script:panelNextPoll=[DateTimeOffset]::UtcNow.AddSeconds(5)}
            finally{$task.ps.Dispose()}
        }
        if(-not $script:panelTask -and [DateTimeOffset]::UtcNow -ge $script:panelNextPoll){Start-PanelTask 'status'}
    })
    $view.form.Add_FormClosing({$timer.Stop();if($script:panelTask){$script:panelTask.ps.Stop();$script:panelTask.ps.Dispose();$script:panelTask=$null}})
    $timer.Start();[Windows.Forms.Application]::Run($view.form);$timer.Dispose();$view.form.Dispose()
} catch {
    $null=[Windows.Forms.MessageBox]::Show('控制面板无法启动。请完整解压程序，并检查 Windows PowerShell 5.1 和文件权限。','TCG Bot')
    exit 1
}
