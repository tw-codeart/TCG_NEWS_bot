# Windows PowerShell 5.1 compatible. All persisted messages are fixed codes, not upstream bodies.
. (Join-Path $PSScriptRoot 'port-check.ps1')
function Get-BotInstanceKey([string]$Directory) {
    $sha=[System.Security.Cryptography.SHA256]::Create()
    try { return ([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($Directory.ToLowerInvariant())))).Replace('-','').Substring(0,16) }
    finally { $sha.Dispose() }
}
function Write-OperationEvent([string]$Code) {
    $line=(Get-Date).ToUniversalTime().ToString('o')+' '+$Code
    $path=Join-Path $PSScriptRoot 'logs/operations.log'
    try {
        $null=New-Item -ItemType Directory -Path (Split-Path -Parent $path) -Force
        if((Test-Path -LiteralPath $path) -and (Get-Item -LiteralPath $path).Length -gt 1MB) {
            Move-Item -LiteralPath $path -Destination ($path+'.1') -Force
        }
        [IO.File]::AppendAllText($path,$line+[Environment]::NewLine,(New-Object Text.UTF8Encoding($false)))
    } catch {
        Write-Warning 'OPERATIONS_LOG_WRITE_FAILED: monitoring continues; check disk space and folder permissions.'
    }
    Write-Host $line
}
function Show-OperationAlert([string]$Code) {
    if(-not $script:operationsSettings.desktopAlerts) { return }
    $env:TCG_ALERT_CODE=$Code
    try {
        $null=Start-Process -FilePath 'powershell.exe' -ArgumentList @('-NoProfile','-ExecutionPolicy','Bypass','-File',('"'+(Join-Path $PSScriptRoot 'operation-alert.ps1')+'"')) -WindowStyle Hidden -PassThru
    } catch { Write-OperationEvent 'OPERATIONS_ALERT_START_FAILED' }
}
function Write-OperationStatus([string]$Path,$Status) {
    try {
        if(Test-Path -LiteralPath $Path -PathType Container) { throw 'Status path is a directory.' }
        [IO.File]::WriteAllText(($Path+'.tmp'),($Status|ConvertTo-Json),(New-Object Text.UTF8Encoding($false)))
        Move-Item -LiteralPath ($Path+'.tmp') -Destination $Path -Force
        return $true
    } catch {
        Write-OperationEvent 'OPERATIONS_STATUS_WRITE_FAILED'
        return $false
    }
}
function Test-BotStopRequest([string]$Path,[string]$Session) {
    try { return (Test-Path -LiteralPath $Path) -and ([IO.File]::ReadAllText($Path).Trim() -eq $Session) }
    catch { Write-OperationEvent 'OPERATIONS_STOP_READ_FAILED';return $false }
}
function Get-DiskHealth([string]$Directory) {
    try {
        $drive=New-Object IO.DriveInfo([IO.Path]::GetPathRoot($Directory))
        if(-not $drive.IsReady) { return 'DISK_CHECK_FAILED' }
        if($drive.AvailableFreeSpace -lt 512MB) { return 'DISK_SPACE_LOW' }
        return 'DISK_OK'
    } catch { return 'DISK_CHECK_FAILED' }
}
function Update-BotWorker($Worker,[DateTimeOffset]$Now) {
    $name=$Worker.definition.name
    if($Worker.external) { return }
    if($Worker.process -and $Worker.process.HasExited) {
        $Worker.failures++;$Worker.nextStart=$Now.AddSeconds((Get-RestartDelay $Worker.failures))
        $Worker.process=$null
        Write-OperationEvent ($name+'_EXITED_RESTART_BACKOFF')
    }
    if(-not $Worker.process -and $Now -ge $Worker.nextStart) {
        try {
            if(Test-BotPortOccupied $Worker.definition.port) {
                $Worker.external=$true;Write-OperationEvent ($name+'_PORT_TAKEN_RESTART_ABORTED');return
            }
            $Worker.process=Start-BotWorker $Worker.definition.script
            $Worker.startedAt=$Now
            Write-OperationEvent ($name+'_STARTED')
        } catch {
            $Worker.failures++;$Worker.nextStart=$Now.AddSeconds((Get-RestartDelay $Worker.failures))
            $Worker.process=$null
            if($_.Exception.Message -like 'PORT_CHECK_UNAVAILABLE:*') { Write-OperationEvent ($name+'_PORT_CHECK_FAILED_RETRY_PENDING') }
            else { Write-OperationEvent ($name+'_START_FAILED_RETRY_PENDING') }
        }
    }
    if($Worker.process -and ($Now-$Worker.startedAt).TotalMinutes -ge 10) { $Worker.failures=0 }
}
function Get-QqHealth([Uri]$BaseUri,[hashtable]$Headers) {
    try {
        $response=Invoke-RestMethod -Method Post -Uri ($BaseUri.AbsoluteUri.TrimEnd('/')+'/get_status') -Headers $Headers -ContentType 'application/json' -Body '{}' -TimeoutSec 5
        if($response.status -ne 'ok' -or $null -eq $response.retcode -or $response.retcode -ne 0) { return 'QQ_API_FAILURE' }
        if($response.data.online -isnot [bool] -or -not $response.data.online) { return 'QQ_OFFLINE_RELOGIN_REQUIRED' }
        return 'QQ_OK'
    } catch {
        if($_.Exception.Response -and [int]$_.Exception.Response.StatusCode -in @(401,403)) { return 'QQ_AUTH_FAILED' }
        return 'QQ_UNAVAILABLE'
    }
}
function Get-QqObservation($Previous,[string]$Health,[DateTimeOffset]$CheckedAt) {
    $healthy=$Previous.qqLastHealthyAt;$badSince=$Previous.qqUnhealthySince
    if($Health -eq 'QQ_OK'){$healthy=$CheckedAt.ToString('o');$badSince=$null}
    elseif(-not $badSince){$badSince=$CheckedAt.ToString('o')}
    return @{qqHealth=$Health;qqLastCheckedAt=$CheckedAt.ToString('o');qqLastHealthyAt=$healthy;qqUnhealthySince=$badSince}
}
function Get-PollingHealth($Status,[DateTimeOffset]$Now,[int]$IntervalSeconds) {
    if(-not $Status.pollingEnabled) { return 'POLLING_DISABLED' }
    if(-not $Status.polling.lastStartedAt) { return 'POLLING_NOT_STARTED' }
    if($Status.polling.lastFetchSuccessful -eq $false) { return 'SOURCE_FETCH_FAILED' }
    if($Status.polling.lastStartedAt) {
        $age=($Now-[DateTimeOffset]::Parse($Status.polling.lastStartedAt)).TotalSeconds
        # An image/OCR batch may take time. Report a stall; do not kill an in-flight send.
        if($age -gt [Math]::Max(1800,3*$IntervalSeconds)) { return 'POLLING_STALE' }
    }
    foreach($source in $Status.sources) { if($source.lastErrorReason) { return 'DELIVERY_PENDING_FAILURE' } }
    return 'POLLING_OK'
}
function Stop-OwnedTree($Process) {
    if($null -eq $Process -or $Process.HasExited) { return }
    # Snapshot only descendants of this exact process; never kill by executable/account name.
    $current=Get-Process -Id $Process.Id -ErrorAction SilentlyContinue
    if($null -eq $current -or $current.StartTime -ne $Process.StartTime) { return }
    $rows=@(Get-CimInstance Win32_Process)
    $ids=New-Object 'System.Collections.Generic.List[int]'
    $ids.Add($Process.Id)
    for($i=0;$i -lt $ids.Count;$i++) {
        foreach($row in $rows) { if($row.ParentProcessId -eq $ids[$i] -and -not $ids.Contains([int]$row.ProcessId)) { $ids.Add([int]$row.ProcessId) } }
    }
    for($i=$ids.Count-1;$i -ge 0;$i--) { Stop-Process -Id $ids[$i] -Force -ErrorAction SilentlyContinue }
}
function Start-BotWorker([string]$Name) {
    return Start-Process -FilePath 'powershell.exe' -ArgumentList @('-NoProfile','-ExecutionPolicy','Bypass','-File',('"'+(Join-Path $PSScriptRoot $Name)+'"')) -WorkingDirectory $PSScriptRoot -WindowStyle Hidden -PassThru
}
function Start-QqAdapterOnce([string]$Launcher) {
    if(-not [IO.Path]::IsPathRooted($Launcher) -or -not (Test-Path -LiteralPath $Launcher -PathType Leaf) -or [IO.Path]::GetExtension($Launcher) -notin @('.bat','.cmd','.exe')) {
        Write-OperationEvent 'QQ_LAUNCH_PATH_INVALID';return $false
    }
    try {
        $env:TCG_QQ_LAUNCHER=$Launcher
        $null=Start-Process 'powershell.exe' -ArgumentList @('-NoProfile','-ExecutionPolicy','Bypass','-File',('"'+(Join-Path $PSScriptRoot 'launch-qq.ps1')+'"')) -WorkingDirectory (Split-Path -Parent $Launcher) -WindowStyle Hidden -PassThru
        Write-OperationEvent 'QQ_LAUNCH_REQUESTED';return $true
    } catch {
        Write-OperationEvent 'QQ_LAUNCH_START_FAILED';return $false
    }
}
function Get-RestartDelay([int]$Failures) { return [int][Math]::Min(900,30*[Math]::Pow(2,[Math]::Min($Failures,5))) }
