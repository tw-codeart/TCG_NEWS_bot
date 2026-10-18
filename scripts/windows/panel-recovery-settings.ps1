. (Join-Path $PSScriptRoot 'napcat-recovery.ps1')
function Save-PanelRecoverySettings([string]$Directory,[bool]$Enabled,[string]$NapCatDirectory,[string]$Account) {
    if((Get-PanelSupervisorState $Directory) -ne 'STOPPED'){throw 'Stop collection before changing recovery settings'}
    $config=Get-PanelConfig $Directory
    if($Enabled -and $config.QQ_ADAPTER -and $config.QQ_ADAPTER -ne 'napcat'){throw 'Recovery currently supports NapCat Node Shell only'}
    $path=Join-Path $Directory 'operations.local.json';$settings=@{}
    if(Test-Path -LiteralPath $path){
        $previous=[IO.File]::ReadAllText($path)|ConvertFrom-Json
        foreach($property in $previous.PSObject.Properties){$settings[$property.Name]=$property.Value}
    }else{$settings=@{qqLauncherPath='';qqWebUiUrl='http://127.0.0.1:6099/webui/';preventSleep=$true;desktopAlerts=$true}}
    foreach($entry in @{preventSleep=$true;desktopAlerts=$true;qqWebUiUrl='http://127.0.0.1:6099/webui/'}.GetEnumerator()){
        if(-not $settings.ContainsKey($entry.Key)){$settings[$entry.Key]=$entry.Value}
    }
    $settings.napcatAutoRecover=$Enabled;$settings.napcatDirectory=$NapCatDirectory.Trim();$settings.napcatQuickLoginAccount=$Account.Trim()
    if($Enabled -and (New-NapCatRecoveryState $settings 'napcat').code -eq 'CONFIG_INVALID'){throw 'Select the Node Shell folder containing node.exe and index.js, and enter the bot QQ number'}
    $temporary=$path+'.tmp'
    try{[IO.File]::WriteAllText($temporary,($settings|ConvertTo-Json -Depth 6),(New-Object Text.UTF8Encoding($false)));Move-Item -LiteralPath $temporary -Destination $path -Force}
    finally{if(Test-Path -LiteralPath $temporary -PathType Leaf){Remove-Item -LiteralPath $temporary}}
}
function New-PanelRecoverySettingsView {
    $form=New-Object Windows.Forms.Form;$form.Text='NapCat 自动恢复设置';$form.ClientSize=New-Object Drawing.Size(660,380)
    $form.StartPosition='CenterParent';$form.FormBorderStyle='FixedDialog';$form.MaximizeBox=$false;$form.MinimizeBox=$false
    $form.Font=New-Object Drawing.Font('Microsoft YaHei UI',10)
    $enable=New-Object Windows.Forms.CheckBox;$enable.Text='启用 NapCat Node Shell 自动恢复';$enable.SetBounds(22,20,600,30);$form.Controls.Add($enable)
    $label=New-Object Windows.Forms.Label;$label.Text='NapCat 文件夹（里面应有 node.exe 和 index.js）';$label.SetBounds(22,65,610,25);$form.Controls.Add($label)
    $directory=New-Object Windows.Forms.TextBox;$directory.SetBounds(22,95,510,30);$form.Controls.Add($directory)
    $browse=New-Object Windows.Forms.Button;$browse.Text='选择…';$browse.SetBounds(540,94,95,30);$form.Controls.Add($browse)
    $label=New-Object Windows.Forms.Label;$label.Text='机器人 QQ 号（不是群号；不用填写密码）';$label.SetBounds(22,145,610,25);$form.Controls.Add($label)
    $account=New-Object Windows.Forms.TextBox;$account.SetBounds(22,175,300,30);$form.Controls.Add($account)
    $hint=New-Object Windows.Forms.Label;$hint.Text="保存后先退出旧 NapCat，再启动采集，由监控托管。`r`n掉线持续2分钟后尝试恢复；重试间隔5分钟，连续最多3次。`r`n依赖已保存的登录状态；要求扫码或设备验证时仍需本人处理。";$hint.SetBounds(22,225,615,78);$form.Controls.Add($hint)
    $save=New-Object Windows.Forms.Button;$save.Text='保存';$save.SetBounds(430,325,95,32);$form.Controls.Add($save)
    $cancel=New-Object Windows.Forms.Button;$cancel.Text='取消';$cancel.DialogResult='Cancel';$cancel.SetBounds(540,325,95,32);$form.Controls.Add($cancel);$form.CancelButton=$cancel
    return @{form=$form;enable=$enable;directory=$directory;account=$account;browse=$browse;save=$save}
}
function Show-PanelRecoverySettings([string]$Directory,$Owner) {
    $settingsView=New-PanelRecoverySettingsView
    try {
        $path=Join-Path $Directory 'operations.local.json'
        if(Test-Path -LiteralPath $path){$settings=[IO.File]::ReadAllText($path)|ConvertFrom-Json;$settingsView.enable.Checked=$settings.napcatAutoRecover -eq $true;$settingsView.directory.Text=[string]$settings.napcatDirectory;$settingsView.account.Text=[string]$settings.napcatQuickLoginAccount}
        $settingsView.browse.Add_Click({$dialog=New-Object Windows.Forms.FolderBrowserDialog;try{if($dialog.ShowDialog($settingsView.form) -eq 'OK'){$settingsView.directory.Text=$dialog.SelectedPath}}finally{$dialog.Dispose()}})
        $settingsView.save.Add_Click({try{Save-PanelRecoverySettings $Directory $settingsView.enable.Checked $settingsView.directory.Text $settingsView.account.Text;$settingsView.form.DialogResult='OK';$settingsView.form.Close()}catch{$null=[Windows.Forms.MessageBox]::Show('保存失败：请先停止采集，确认选择的是含 node.exe 和 index.js 的 NapCat Node 目录，并填写机器人QQ号。','自动恢复设置')}})
        return $settingsView.form.ShowDialog($Owner)
    }finally{$settingsView.form.Dispose()}
}
