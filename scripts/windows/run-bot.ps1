. (Join-Path $PSScriptRoot 'common.ps1')
. (Join-Path $PSScriptRoot 'operations-lib.ps1')
. (Join-Path $PSScriptRoot 'onebot-config.ps1')
$mutex=New-Object Threading.Mutex($false,('Local\TCGNewsBot-'+(Get-BotInstanceKey $PSScriptRoot)))
$locked=$false
try { $locked=$mutex.WaitOne(0) } catch [Threading.AbandonedMutexException] { $locked=$true }
if(-not $locked) { $mutex.Dispose(); throw 'TCG supervisor is already running. Do not start it twice.' }
$workers=@{}
$session=[Guid]::NewGuid().ToString()
$stopFile=Join-Path $PSScriptRoot 'operations.stop'
$statusFile=Join-Path $PSScriptRoot 'operations-status.json'
$script:operationsSettings=@{qqLauncherPath='';preventSleep=$true;desktopAlerts=$true}
try {
    $settingsFile=Join-Path $PSScriptRoot 'operations.local.json'
    if(Test-Path -LiteralPath $settingsFile) { $script:operationsSettings=[IO.File]::ReadAllText($settingsFile) | ConvertFrom-Json }
    $adapter=Get-QqAdapterName
    if($env:QQ_ENABLED -ne 'true' -or $env:RSSHUB_POLLING_ENABLED -ne 'true') { throw 'Enable automatic QQ delivery first with start-auto.cmd; stop its Java before starting this supervisor.' }
    $base=$null
    if(-not [Uri]::TryCreate($env:QQ_API_BASE_URL,[UriKind]::Absolute,[ref]$base) -or $base.Scheme -notin @('http','https') -or $base.UserInfo -or $base.Query -or $base.Fragment) { throw 'Invalid QQ API configuration.' }
    $headers=@{}
    if($env:QQ_ACCESS_TOKEN) { $headers.Authorization='Bearer '+$env:QQ_ACCESS_TOKEN }
    if($script:operationsSettings.preventSleep) {
        Add-Type -TypeDefinition 'using System.Runtime.InteropServices; public static class TcgAwake { [DllImport("kernel32.dll")] public static extern uint SetThreadExecutionState(uint flags); }'
        $null=[TcgAwake]::SetThreadExecutionState([uint32]2147483649)
    }
    $launcher=Get-QqLauncherPath $script:operationsSettings $adapter
    if($launcher -and (Get-QqHealth $base $headers) -eq 'QQ_UNAVAILABLE') {
        if(-not [IO.Path]::IsPathRooted($launcher) -or -not (Test-Path -LiteralPath $launcher -PathType Leaf) -or [IO.Path]::GetExtension($launcher) -notin @('.bat','.cmd','.exe')) { throw 'Set an existing absolute QQ adapter launcher path.' }
        # Launch once. Never repeatedly spawn or kill QQ when its login session is expired.
        $env:TCG_QQ_LAUNCHER=$launcher
        $null=Start-Process 'powershell.exe' -ArgumentList @('-NoProfile','-ExecutionPolicy','Bypass','-File',('"'+(Join-Path $PSScriptRoot 'launch-qq.ps1')+'"')) -WorkingDirectory (Split-Path -Parent $launcher) -WindowStyle Hidden -PassThru
        Write-OperationEvent 'QQ_LAUNCH_REQUESTED'
    }
    foreach($worker in @(@{name='RSSHUB';port=1200;script='start-rsshub.ps1'},@{name='JAVA';port=8080;script='start-app.ps1'})) {
        $external=[bool](Get-NetTCPConnection -LocalPort $worker.port -State Listen -ErrorAction SilentlyContinue)
        $workers[$worker.name]=@{definition=$worker;process=$null;external=$external;failures=0;nextStart=[DateTimeOffset]::MinValue;startedAt=$null}
        if($external) { Write-OperationEvent ($worker.name+'_EXTERNAL_PROCESS_NOT_OWNED') }
    }
    Write-OperationEvent 'SUPERVISOR_STARTED'
    $previous='';$lastAlert=[DateTimeOffset]::MinValue;$started=[DateTimeOffset]::UtcNow
    while($true) {
        if(Test-BotStopRequest $stopFile $session) { break }
        $now=[DateTimeOffset]::UtcNow
        foreach($name in @('RSSHUB','JAVA')) {
            Update-BotWorker $workers[$name] $now
        }
        $codes=New-Object 'System.Collections.Generic.List[string]'
        $disk=Get-DiskHealth $PSScriptRoot
        if($disk -ne 'DISK_OK') { $codes.Add($disk) }
        foreach($name in @('RSSHUB','JAVA')) { if(-not $workers[$name].external -and -not $workers[$name].process) { $codes.Add($name+'_RESTART_PENDING') } }
        $qq=Get-QqHealth $base $headers
        if($qq -ne 'QQ_OK') { $codes.Add($qq) }
        try {
            $status=Invoke-RestMethod 'http://127.0.0.1:8080/api/status' -TimeoutSec 5
            if(-not $status.storageReady) { $codes.Add('STORAGE_NOT_READY') }
            $delivery=Invoke-RestMethod 'http://127.0.0.1:8080/api/notifications/status' -TimeoutSec 5
            $poll=Get-PollingHealth $delivery $now ([int]$status.pollingIntervalSeconds)
            if($poll -ne 'POLLING_OK') { $codes.Add($poll) }
        } catch { $codes.Add('JAVA_UNAVAILABLE') }
        $state=if($codes.Count -eq 0){'ALL_HEALTHY'}else{($codes | Sort-Object -Unique)-join ','}
        if($state -ne $previous) {
            Write-OperationEvent $state
            if(($now-$started).TotalSeconds -gt 90) { foreach($code in $codes) { Show-OperationAlert $code };if($state -eq 'ALL_HEALTHY'){Show-OperationAlert 'ALL_HEALTHY'};$lastAlert=$now }
            $previous=$state
        } elseif($codes.Count -gt 0 -and ($now-$lastAlert).TotalMinutes -ge 30 -and ($now-$started).TotalSeconds -gt 90) {
            foreach($code in $codes) { Show-OperationAlert $code };$lastAlert=$now
        }
        $public=[ordered]@{revision='windows-supervisor-v3';qqAdapter=$adapter;session=$session;updatedAt=$now.ToString('o');health=$state;javaManaged=(-not $workers.JAVA.external);rsshubManaged=(-not $workers.RSSHUB.external)}
        $null=Write-OperationStatus $statusFile $public
        Start-Sleep -Seconds 15
    }
} catch { Write-OperationEvent 'OPERATIONS_CONFIG_OR_PROCESS_ERROR';Show-OperationAlert 'OPERATIONS_CONFIG_ERROR';throw }
finally {
    foreach($worker in $workers.Values) {
        try { Stop-OwnedTree $worker.process }
        catch { Write-OperationEvent 'OPERATIONS_WORKER_STOP_FAILED' }
    }
    if('TcgAwake' -as [type]) { $null=[TcgAwake]::SetThreadExecutionState([uint32]2147483648) }
    Write-OperationEvent 'SUPERVISOR_STOPPED'
    try { if(Test-Path -LiteralPath $statusFile -PathType Leaf) { Remove-Item -LiteralPath $statusFile -Force } }
    catch { Write-OperationEvent 'OPERATIONS_STATUS_CLEANUP_FAILED' }
    if($locked) { $mutex.ReleaseMutex() };$mutex.Dispose()
}
