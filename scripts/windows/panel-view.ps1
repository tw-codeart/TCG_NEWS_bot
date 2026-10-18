Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
function New-ControlPanelView {
    $form=New-Object Windows.Forms.Form
    $form.Text='TCG Bot 测试控制面板';$form.ClientSize=New-Object Drawing.Size(910,660)
    $form.MinimumSize=New-Object Drawing.Size(926,699);$form.StartPosition='CenterScreen'
    $form.BackColor=[Drawing.Color]::FromArgb(242,245,249)
    $form.Font=New-Object Drawing.Font('Microsoft YaHei UI',10)
    $form.AutoScaleMode='Dpi'
    $controls=@{}
    function Add-PanelLabel([string]$Name,[string]$Text,[int]$X,[int]$Y,[int]$Width,[int]$Height,[int]$Size=10) {
        $label=New-Object Windows.Forms.Label;$label.Text=$Text;$label.Location=New-Object Drawing.Point($X,$Y)
        $label.Size=New-Object Drawing.Size($Width,$Height);$label.Font=New-Object Drawing.Font('Microsoft YaHei UI',$Size)
        $form.Controls.Add($label);$controls[$Name]=$label
    }
    Add-PanelLabel 'heading' 'TCG Bot · NapCat 测试版' 24 20 650 42 21
    Add-PanelLabel 'subtitle' 'WS / 游戏王 OCG · 只运行一个主 bot，备用账号暂不自动接管' 26 64 850 26
    Add-PanelLabel 'qq' 'QQ：正在检查…' 26 112 850 40 20
    Add-PanelLabel 'qqtime' '首次检测异常：—    最近在线：—' 28 156 850 27
    Add-PanelLabel 'recovery' 'NapCat 自动恢复：正在检查…' 28 184 850 25 9
    Add-PanelLabel 'services' '监控：—    Java：—    自动抓取：—' 28 211 850 30 12
    Add-PanelLabel 'fetch' '最近抓取：—    找到 / 新增 / 重复：—' 28 245 850 28
    Add-PanelLabel 'hint' '正在读取状态，后台检查不会阻塞窗口。' 28 276 850 43
    $recoveryButton=New-Object Windows.Forms.Button;$recoveryButton.Text='自动恢复设置'
    $recoveryButton.Location=New-Object Drawing.Point(710,24);$recoveryButton.Size=New-Object Drawing.Size(165,34)
    $form.Controls.Add($recoveryButton);$controls.recoverySettings=$recoveryButton
    $grid=New-Object Windows.Forms.DataGridView
    $grid.Location=New-Object Drawing.Point(28,327);$grid.Size=New-Object Drawing.Size(850,143)
    $grid.ReadOnly=$true;$grid.AllowUserToAddRows=$false;$grid.AllowUserToDeleteRows=$false
    $grid.RowHeadersVisible=$false;$grid.AutoSizeColumnsMode='Fill';$grid.BackgroundColor=[Drawing.Color]::White
    $grid.BorderStyle='None';$grid.SelectionMode='FullRowSelect';$grid.MultiSelect=$false
    foreach($column in @('游戏 / 数据源','待发送','已确认记录','最近确认发送','错误类别')){$null=$grid.Columns.Add($column,$column)}
    $form.Controls.Add($grid);$controls.grid=$grid
    $buttons=@(
        @{id='start';text='启动采集';x=28},@{id='stop';text='停止采集';x=173},
        @{id='webui';text='NapCat 管理页';x=318},@{id='config';text='打开配置';x=493},@{id='logs';text='打开日志目录';x=668})
    foreach($item in $buttons){
        $button=New-Object Windows.Forms.Button;$button.Text=$item.text;$button.Location=New-Object Drawing.Point($item.x,489)
        $button.Size=New-Object Drawing.Size(135,38);if($item.id -eq 'webui'){$button.Width=165}
        $button.FlatStyle='Flat';$button.BackColor=[Drawing.Color]::White;$form.Controls.Add($button);$controls[$item.id]=$button
    }
    foreach($item in @(@{id='ws';text='测试推送 WS';x=28},@{id='ocg';text='测试推送 OCG';x=203})){
        $button=New-Object Windows.Forms.Button;$button.Text=$item.text;$button.Location=New-Object Drawing.Point($item.x,541)
        $button.Size=New-Object Drawing.Size(165,38);$button.Enabled=$false;$form.Controls.Add($button);$controls[$item.id]=$button
    }
    foreach($item in @(@{id='backfillws';text='补发WS（前7天）';x=378},@{id='backfillocg';text='补发OCG（前7天）';x=533})){
        $button=New-Object Windows.Forms.Button;$button.Text=$item.text;$button.Location=New-Object Drawing.Point($item.x,541)
        $button.Size=New-Object Drawing.Size(145,38);$button.Enabled=$false;$form.Controls.Add($button);$controls[$item.id]=$button
    }
    $sound=New-Object Windows.Forms.CheckBox;$sound.Text='异常声音提醒';$sound.Checked=$true
    $sound.Location=New-Object Drawing.Point(698,550);$sound.Width=180;$form.Controls.Add($sound);$controls.sound=$sound
    Add-PanelLabel 'footer' '补发按日本时间的前7个完整自然日，不含今天，先预览再确认。关闭面板不会停止后台采集。' 28 599 850 40 9
    return [pscustomobject]@{form=$form;controls=$controls}
}
function Format-PanelTime($Value) {
    if(-not $Value){return '—'}
    try{return ([DateTimeOffset]::Parse($Value)).ToLocalTime().ToString('MM-dd HH:mm:ss')}catch{return '—'}
}
function Set-ControlPanelView($View,$Snapshot) {
    $c=$View.controls
    $labels=@{QQ_OK='QQ 已在线';QQ_OFFLINE_RELOGIN_REQUIRED='QQ 已离线，需要重新登录';QQ_UNAVAILABLE='无法连接 NapCat 接口';QQ_AUTH_FAILED='NapCat 接口 Token 不匹配';QQ_API_FAILURE='NapCat 接口返回异常';QQ_DISABLED='QQ 推送尚未启用';QQ_CONFIG_MISSING='尚未配置，请先打开配置';QQ_CONFIG_INVALID='配置无效，请检查接口地址'}
    $label=$labels[$Snapshot.qqState];if(-not $label){$label='QQ 状态未知'}
    $c.qq.Text=$label
    $c.qq.ForeColor=if($Snapshot.qqState -eq 'QQ_OK'){[Drawing.Color]::FromArgb(19,121,83)}else{[Drawing.Color]::FromArgb(179,47,47)}
    $c.qqtime.Text='首次检测异常：'+(Format-PanelTime $Snapshot.qqUnhealthySince)+'    最近在线：'+(Format-PanelTime $Snapshot.qqLastHealthyAt)
    $recoveryNames=@{DISABLED='未启用';READY='等待首次检查';ONLINE='QQ 在线';CONFIG_INVALID='配置不完整，请打开自动恢复设置';LOCAL_API_REQUIRED='仅支持本机 NapCat';AUTH_FAILED='接口 Token 错误，未重启';API_ERROR='接口异常，未重启';WAITING_FOR_LOGIN='等待快速登录';COOLDOWN='冷却中，5分钟后可重试';MANUAL_LOGIN_REQUIRED='已尝试3次，需要扫码或验证';EXTERNAL_INSTANCE='手动启动的实例，未接管';STOP_PENDING='等待托管进程退出';START_REQUESTED='已启动，正在尝试快速登录';START_FAILED='启动失败，等待重试';INSPECTION_OR_STOP_FAILED='进程检查或停止失败，请查看日志'}
    $recoveryNames.EXTERNAL_ONLINE='手动实例在线；退出旧实例后才可托管'
    $recovery=$recoveryNames[$Snapshot.napcatRecoveryState];if(-not $recovery){$recovery='状态未知'}
    if($Snapshot.napcatRecoveryState -eq 'COOLDOWN'){$recovery='等待重试间隔（5分钟）'}
    $c.recovery.Text='NapCat 自动恢复：'+$recovery+'；本轮尝试 '+$Snapshot.napcatRecoveryAttempts+'/3'
    $supervisor=@{RUNNING='运行中';STOPPED='未启动';UNKNOWN='无法确认'}[$Snapshot.supervisor]
    $java=if($Snapshot.javaReady){'可访问'}else{'未启动 / 不可访问'}
        $pollingNames=@{POLLING_OK='正常';POLLING_DISABLED='未启用';POLLING_NOT_STARTED='等待首次抓取';POLLING_STALE='长时间未完成';SOURCE_FETCH_FAILED='数据源抓取失败';DELIVERY_PENDING_FAILURE='存在待发失败';JAVA_UNAVAILABLE='Java 不可访问';DELIVERY_STATUS_UNAVAILABLE='发送状态不可访问';STORAGE_NOT_READY='数据库未就绪'}
    $polling=$pollingNames[$Snapshot.polling];if(-not $polling){$polling='状态未知'}
    $c.services.Text='监控：'+$supervisor+'    Java：'+$java+'    抓取：'+$polling
    $c.fetch.Text='最近抓取：'+(Format-PanelTime $Snapshot.lastFetch)+'    找到 / 新增 / 重复：'+$Snapshot.fetched+' / '+$Snapshot.saved+' / '+$Snapshot.duplicates
    $hint=if($Snapshot.qqState -eq 'QQ_OK'){'QQ 接口报告在线；是否实际收到消息，请用测试推送确认。'}else{'打开 NapCat 管理页检查登录。资讯可保留待发，恢复后由现有队列继续处理。'}
    if($Snapshot.supervisor -eq 'STOPPED'){$hint='监控尚未启动。先填好配置、登录 NapCat，再点击“启动采集”。'}
    elseif(-not $Snapshot.snapshotFresh){$hint+=' 监控快照缺失或过期，请检查日志。'}
    if($Snapshot.canBackfill -and -not $Snapshot.ocgEnabled){$hint+=' 游戏王未启用：在配置中设置 RSSHUB_OCG_ENABLED=true 后重启采集。'}
    $c.hint.Text=$hint
    $c.grid.Rows.Clear()
    foreach($row in $Snapshot.rows){
        $name=if($row.sourceId -eq 'ws-jp-rsshub-card-reveals'){'WS'}else{if($row.sourceId -eq 'ocg-jp-yugioh-jp-card-reveals'){'OCG 官网文章'}else{'游戏王 OCG'}}
        $null=$c.grid.Rows.Add($name,[string]$row.pending,[string]$row.sent,(Format-PanelTime $row.lastSent),[string]$row.reason)
    }
    $c.start.Enabled=$Snapshot.configReady -and $Snapshot.supervisor -eq 'STOPPED'
    $c.stop.Enabled=$Snapshot.supervisor -eq 'RUNNING'
    $c.recoverySettings.Enabled=$Snapshot.supervisor -eq 'STOPPED'
    $c.ws.Enabled=$Snapshot.canPreview;$c.ocg.Enabled=$Snapshot.canPreview
    $c.backfillws.Enabled=$Snapshot.canBackfillWs;$c.backfillocg.Enabled=$Snapshot.canBackfillOcg
}
