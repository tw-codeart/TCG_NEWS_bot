# Recovery owns only the worker it starts. No QQ API reboot endpoint or profile edits.
function New-NapCatRecoveryState($Settings,[string]$Adapter) {
    $state=@{enabled=$false;process=$null;directory='';account='';attempts=0;lastAttempt=$null;
        unhealthySince=$null;healthySince=$null;code='DISABLED';graceSeconds=120;cooldownSeconds=300}
    if($Adapter -ne 'napcat' -or $Settings.napcatAutoRecover -ne $true){return $state}
    $state.enabled=$true
    try {
        $directory=[string]$Settings.napcatDirectory;$account=[string]$Settings.napcatQuickLoginAccount
        if(-not [IO.Path]::IsPathRooted($directory) -or $account -notmatch '^[1-9][0-9]{4,14}$'){throw 'Invalid recovery settings'}
        $directory=[IO.Path]::GetFullPath($directory)
        foreach($name in @('node.exe','index.js')){if(-not(Test-Path -LiteralPath (Join-Path $directory $name) -PathType Leaf)){throw 'Node Shell installation missing'}}
        $state.directory=$directory;$state.account=$account;$state.code='READY'
    }catch{$state.code='CONFIG_INVALID'}
    return $state
}
function Set-NapCatRecoveryCode($State,[string]$Code) {
    if($State.code -ne $Code){Write-OperationEvent ('NAPCAT_RECOVERY_'+$Code);$State.code=$Code}
}
function Test-ExternalNapCatProcess([string]$Directory) {
    $executable=Join-Path $Directory 'node.exe'
    foreach($process in @(Get-CimInstance Win32_Process -Filter "Name='node.exe'" -ErrorAction Stop)) {
        if(-not $process.ExecutablePath -or -not $process.CommandLine){throw 'Process inspection unavailable'}
        if($process.ExecutablePath -eq $executable -and $process.CommandLine -match '(?i)index\.js'){return $true}
    }
    return $false
}
function Start-NapCatRecoveryProcess($State) {
    $env:TCG_MANAGED_NAPCAT_DIRECTORY=$State.directory
    $env:TCG_MANAGED_NAPCAT_ACCOUNT=$State.account
    return Start-Process 'powershell.exe' -ArgumentList @('-NoProfile','-ExecutionPolicy','Bypass','-File',('"'+(Join-Path $PSScriptRoot 'run-managed-napcat.ps1')+'"')) -WorkingDirectory $PSScriptRoot -WindowStyle Hidden -PassThru
}
function Update-NapCatRecovery($State,[string]$Health,[Uri]$BaseUri,[DateTimeOffset]$Now) {
    if(-not $State.enabled -or $State.code -eq 'CONFIG_INVALID'){return}
    if($BaseUri.Host -notin @('localhost','127.0.0.1','[::1]','::1')){Set-NapCatRecoveryCode $State 'LOCAL_API_REQUIRED';return}
    try {
        if($State.process -and $State.process.HasExited){$State.process=$null}
        if($Health -eq 'QQ_OK') {
            $State.unhealthySince=$null
            if(-not $State.healthySince){$State.healthySince=$Now}
            if(($Now-$State.healthySince).TotalMinutes -ge 10){$State.attempts=0}
            $code=if($State.process){'ONLINE'}else{'EXTERNAL_ONLINE'}
            Set-NapCatRecoveryCode $State $code;return
        }
        $State.healthySince=$null
        if(-not $State.unhealthySince){$State.unhealthySince=$Now}
        if($Health -eq 'QQ_AUTH_FAILED'){Set-NapCatRecoveryCode $State 'AUTH_FAILED';return}
        if($Health -notin @('QQ_UNAVAILABLE','QQ_OFFLINE_RELOGIN_REQUIRED')){Set-NapCatRecoveryCode $State 'API_ERROR';return}
        if($State.process -and ($Now-$State.unhealthySince).TotalSeconds -lt $State.graceSeconds){Set-NapCatRecoveryCode $State 'WAITING_FOR_LOGIN';return}
        if($State.attempts -ge 3){Set-NapCatRecoveryCode $State 'MANUAL_LOGIN_REQUIRED';return}
        if($State.lastAttempt -and ($Now-$State.lastAttempt).TotalSeconds -lt $State.cooldownSeconds){Set-NapCatRecoveryCode $State 'COOLDOWN';return}
        if($State.process) {
            Stop-OwnedTree $State.process
            if(-not $State.process.HasExited){Set-NapCatRecoveryCode $State 'STOP_PENDING';return}
            $State.process=$null
        } else {
            if($Health -ne 'QQ_UNAVAILABLE'){Set-NapCatRecoveryCode $State 'EXTERNAL_INSTANCE';return}
            if(Test-ExternalNapCatProcess $State.directory){Set-NapCatRecoveryCode $State 'EXTERNAL_INSTANCE';return}
        }
        $State.attempts++;$State.lastAttempt=$Now;$State.unhealthySince=$Now
        try{$State.process=Start-NapCatRecoveryProcess $State;Set-NapCatRecoveryCode $State 'START_REQUESTED'}
        catch{Set-NapCatRecoveryCode $State 'START_FAILED'}
    }catch{Set-NapCatRecoveryCode $State 'INSPECTION_OR_STOP_FAILED'}
}
