# Synthetic process/clock tests only. Never launches or logs in to a real QQ account.
param([string]$PreviewPath='')
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'operations-lib.ps1')
. (Join-Path $PSScriptRoot 'napcat-recovery.ps1')
. (Join-Path $PSScriptRoot 'panel-lib.ps1')
. (Join-Path $PSScriptRoot 'panel-recovery-settings.ps1')
function Assert-True($Value,[string]$Name){if(-not $Value){throw ('FAILED: '+$Name)}}
function Assert-Equal($Actual,$Expected,[string]$Name){if($Actual -ne $Expected){throw ('FAILED: '+$Name+' actual='+$Actual)}}
function Write-OperationEvent([string]$Code){$script:events.Add($Code)}
$script:events=New-Object 'System.Collections.Generic.List[string]'
$fixture=Join-Path ([IO.Path]::GetTempPath()) ('TCG-NapCat-Recovery-Test-'+[Guid]::NewGuid().ToString('N'))
$null=New-Item -ItemType Directory -Path $fixture
try {
    foreach($name in @('node.exe','index.js')){[IO.File]::WriteAllText((Join-Path $fixture $name),'synthetic placeholder')}
    $settings=@{napcatAutoRecover=$true;napcatDirectory=$fixture;napcatQuickLoginAccount='123456789'}
    $now=[DateTimeOffset]::Parse('2026-10-18T10:00:00Z');$base=[Uri]'http://127.0.0.1:3000'
    $script:starts=0;$script:stops=0;$script:external=$false
    function Start-NapCatRecoveryProcess($State){$script:starts++;return @{HasExited=$false;Id=$script:starts}}
    function Stop-OwnedTree($Process){$script:stops++;$Process.HasExited=$true}
    function Test-ExternalNapCatProcess([string]$Directory){return $script:external}
    $state=New-NapCatRecoveryState @{} 'napcat';Update-NapCatRecovery $state 'QQ_UNAVAILABLE' $base $now
    Assert-Equal $script:starts 0 'disabled starts no process'
    Assert-True (-not (New-NapCatRecoveryState $settings 'llbot').enabled) 'does not change LLBot lifecycle'
    $invalid=New-NapCatRecoveryState @{napcatAutoRecover=$true;napcatDirectory='relative';napcatQuickLoginAccount='123456789'} 'napcat'
    Update-NapCatRecovery $invalid 'QQ_UNAVAILABLE' $base $now
    Assert-Equal $invalid.code 'CONFIG_INVALID' 'invalid path fails closed'
    $state=New-NapCatRecoveryState $settings 'napcat'
    Update-NapCatRecovery $state 'QQ_UNAVAILABLE' $base $now
    Assert-Equal $script:starts 1 'starts missing NapCat'
    Assert-Equal $state.attempts 1 'start attempt recorded'
    Update-NapCatRecovery $state 'QQ_OFFLINE_RELOGIN_REQUIRED' $base $now.AddSeconds(119)
    Assert-Equal $script:stops 0 'grace period preserves startup/login'
    Update-NapCatRecovery $state 'QQ_OFFLINE_RELOGIN_REQUIRED' $base $now.AddSeconds(299)
    Assert-Equal $script:starts 1 'five-minute cooldown enforced'
    Update-NapCatRecovery $state 'QQ_OFFLINE_RELOGIN_REQUIRED' $base $now.AddSeconds(300)
    Assert-Equal $script:starts 2 'owned offline worker restarts'
    Assert-Equal $script:stops 1 'only owned worker stopped'
    $state.process.HasExited=$true
    Update-NapCatRecovery $state 'QQ_UNAVAILABLE' $base $now.AddSeconds(600)
    Assert-Equal $script:starts 3 'exited process recovers after cooldown'
    Update-NapCatRecovery $state 'QQ_OFFLINE_RELOGIN_REQUIRED' $base $now.AddSeconds(900)
    Assert-Equal $state.code 'MANUAL_LOGIN_REQUIRED' 'retry limit asks for real login'
    Assert-Equal $script:starts 3 'retry cap prevents login loop'
    Assert-True (-not $state.process.HasExited) 'last login page remains available for QR verification'
    Update-NapCatRecovery $state 'QQ_OK' $base $now.AddSeconds(901)
    Assert-Equal $state.attempts 3 'brief online observation does not reset the budget'
    Update-NapCatRecovery $state 'QQ_OK' $base $now.AddSeconds(1501)
    Assert-Equal $state.attempts 0 'ten minutes stable online reset retry budget'
    $script:external=$true;$externalState=New-NapCatRecoveryState $settings 'napcat'
    Update-NapCatRecovery $externalState 'QQ_UNAVAILABLE' $base $now
    Assert-Equal $externalState.code 'EXTERNAL_INSTANCE' 'external process with unavailable API is untouched'
    Assert-Equal $script:starts 3 'external process does not cause duplicate launch'
    $script:external=$false
    Update-NapCatRecovery $externalState 'QQ_OFFLINE_RELOGIN_REQUIRED' $base $now
    Assert-Equal $externalState.code 'EXTERNAL_INSTANCE' 'external offline API is not restarted'
    Update-NapCatRecovery $state 'QQ_AUTH_FAILED' $base $now.AddSeconds(1600)
    Assert-Equal $state.code 'AUTH_FAILED' 'wrong token never causes restart'
    Assert-Equal $script:stops 1 'auth failure did not stop managed worker'
    $remote=New-NapCatRecoveryState $settings 'napcat'
    Update-NapCatRecovery $remote 'QQ_UNAVAILABLE' ([Uri]'http://remote.example:3000') $now
    Assert-Equal $remote.code 'LOCAL_API_REQUIRED' 'remote API cannot restart a local account'
    $failed=New-NapCatRecoveryState $settings 'napcat'
    function Start-NapCatRecoveryProcess($State){throw 'synthetic secret-bearing start failure'}
    Update-NapCatRecovery $failed 'QQ_UNAVAILABLE' $base $now
    Assert-Equal $failed.code 'START_FAILED' 'launch failure isolated'
    Assert-Equal $failed.attempts 1 'failed launch consumes one attempt'
    Assert-True (($script:events -join '|') -notmatch 'secret-bearing|123456789') 'public logs contain fixed codes only'
    function Get-PanelSupervisorState {return $script:supervisor}
    $script:supervisor='STOPPED'
    [IO.File]::WriteAllText((Join-Path $fixture 'config.env'),"QQ_ADAPTER=napcat`nQQ_ACCESS_TOKEN=synthetic-private-token")
    $configHash=(Get-FileHash (Join-Path $fixture 'config.env')).Hash
    [IO.File]::WriteAllText((Join-Path $fixture 'operations.local.json'),'{"qqLauncherPath":"keep-this-path","preventSleep":false,"customSetting":"preserved"}')
    Save-PanelRecoverySettings $fixture $true $fixture '123456789'
    $saved=[IO.File]::ReadAllText((Join-Path $fixture 'operations.local.json'))|ConvertFrom-Json
    Assert-True $saved.napcatAutoRecover 'explicit setup enables recovery'
    Assert-Equal $saved.customSetting 'preserved' 'unrelated operator settings preserved'
    Assert-Equal $saved.qqLauncherPath 'keep-this-path' 'legacy launcher preserved'
    Assert-True (-not $saved.preventSleep) 'explicit sleep preference preserved'
    Assert-Equal (Get-FileHash (Join-Path $fixture 'config.env')).Hash $configHash 'API credentials never rewritten'
    $script:supervisor='RUNNING'
    try{Save-PanelRecoverySettings $fixture $false '' '';throw 'Expected rejection'}catch{Assert-True ($_.Exception.Message -match '^Stop collection') 'settings require a stopped supervisor'}
    Add-Type -AssemblyName System.Windows.Forms;Add-Type -AssemblyName System.Drawing
    $view=New-PanelRecoverySettingsView
    try{
        Assert-True ($view.enable.Text -match 'Node Shell') 'setup targets the verified launcher format'
        if($PreviewPath){
            $null=$view.form.Handle;foreach($control in $view.form.Controls){$null=$control.Handle};$view.form.PerformLayout()
            $bitmap=New-Object Drawing.Bitmap($view.form.Width,$view.form.Height)
            try{$view.form.DrawToBitmap($bitmap,(New-Object Drawing.Rectangle(0,0,$bitmap.Width,$bitmap.Height)));$bitmap.Save($PreviewPath,[Drawing.Imaging.ImageFormat]::Png)}finally{$bitmap.Dispose()}
        }
    }finally{$view.form.Dispose()}
    Write-Host 'PASS: disabled/LLBot, validation, grace, cooldown, process exit/offline recovery, three-attempt cap, stable reset, external ownership, token/remote guards, settings preservation and setup view.'
}finally{
    $resolved=[IO.Path]::GetFullPath($fixture)
    if((Split-Path -Parent $resolved) -eq ([IO.Path]::GetTempPath().TrimEnd('\')) -and (Split-Path -Leaf $resolved) -match '^TCG-NapCat-Recovery-Test-[a-f0-9]{32}$'){Remove-Item -LiteralPath $resolved -Recurse -Force}
}
