# Windows PowerShell 5.1 compatible. All persisted messages are fixed codes, not upstream bodies.
function Get-BotInstanceKey([string]$Directory) {
    $sha=[System.Security.Cryptography.SHA256]::Create()
    try { return ([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($Directory.ToLowerInvariant())))).Replace('-','').Substring(0,16) }
    finally { $sha.Dispose() }
}
function Write-OperationEvent([string]$Code) {
    $line=(Get-Date).ToUniversalTime().ToString('o')+' '+$Code
    $path=Join-Path $PSScriptRoot 'logs/operations.log'
    if((Test-Path -LiteralPath $path) -and (Get-Item -LiteralPath $path).Length -gt 1MB) {
        Move-Item -LiteralPath $path -Destination ($path+'.1') -Force
    }
    [IO.File]::AppendAllText($path,$line+[Environment]::NewLine,(New-Object Text.UTF8Encoding($false)))
    Write-Host $line
}
function Show-OperationAlert([string]$Code) {
    if(-not $script:operationsSettings.desktopAlerts) { return }
    $env:TCG_ALERT_CODE=$Code
    $null=Start-Process -FilePath 'powershell.exe' -ArgumentList @('-NoProfile','-ExecutionPolicy','Bypass','-File',('"'+(Join-Path $PSScriptRoot 'operation-alert.ps1')+'"')) -WindowStyle Hidden -PassThru
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
function Get-RestartDelay([int]$Failures) { return [int][Math]::Min(900,30*[Math]::Pow(2,[Math]::Min($Failures,5))) }
