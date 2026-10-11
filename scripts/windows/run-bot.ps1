. (Join-Path $PSScriptRoot 'common.ps1')
. (Join-Path $PSScriptRoot 'operations-lib.ps1')
$null=New-Item -ItemType Directory -Path (Join-Path $PSScriptRoot 'logs') -Force
$mutex=New-Object Threading.Mutex($false,('Local\TCGNewsBot-'+(Get-BotInstanceKey $PSScriptRoot)))
$locked=$false
try { $locked=$mutex.WaitOne(0) } catch [Threading.AbandonedMutexException] { $locked=$true }
if(-not $locked) { $mutex.Dispose(); throw 'TCG supervisor is already running. Do not start it twice.' }
$workers=@{}
$session=[Guid]::NewGuid().ToString()
$stopFile=Join-Path $PSScriptRoot 'operations.stop'
$statusFile=Join-Path $PSScriptRoot 'operations-status.json'
$script:operationsSettings=@{napcatLauncherPath='';preventSleep=$true;desktopAlerts=$true}
try {
    $settingsFile=Join-Path $PSScriptRoot 'operations.local.json'
    if(Test-Path -LiteralPath $settingsFile) { $script:operationsSettings=[IO.File]::ReadAllText($settingsFile) | ConvertFrom-Json }
    if($env:QQ_ENABLED -ne 'true' -or $env:RSSHUB_POLLING_ENABLED -ne 'true') { throw 'Enable automatic QQ delivery first with start-auto.cmd; stop its Java before starting this supervisor.' }
    $base=$null
    if(-not [Uri]::TryCreate($env:QQ_API_BASE_URL,[UriKind]::Absolute,[ref]$base) -or $base.Scheme -notin @('http','https') -or $base.UserInfo -or $base.Query -or $base.Fragment) { throw 'Invalid QQ API configuration.' }
    $headers=@{}
    if($env:QQ_ACCESS_TOKEN) { $headers.Authorization='Bearer '+$env:QQ_ACCESS_TOKEN }
    if($script:operationsSettings.preventSleep) {
        Add-Type -TypeDefinition 'using System.Runtime.InteropServices; public static class TcgAwake { [DllImport("kernel32.dll")] public static extern uint SetThreadExecutionState(uint flags); }'
        $null=[TcgAwake]::SetThreadExecutionState([uint32]2147483649)
    }
    $launcher=[string]$script:operationsSettings.napcatLauncherPath
    if($launcher -and (Get-QqHealth $base $headers) -eq 'QQ_UNAVAILABLE') {
        if(-not [IO.Path]::IsPathRooted($launcher) -or -not (Test-Path -LiteralPath $launcher -PathType Leaf) -or [IO.Path]::GetExtension($launcher) -notin @('.bat','.cmd','.exe')) { throw 'Set an existing absolute NapCat launcher path.' }
        # Launch once. Never repeatedly spawn or kill QQ when its login session is expired.
        $env:TCG_NAPCAT_LAUNCHER=$launcher
        $null=Start-Process 'powershell.exe' -ArgumentList @('-NoProfile','-ExecutionPolicy','Bypass','-File',('"'+(Join-Path $PSScriptRoot 'launch-napcat.ps1')+'"')) -WorkingDirectory (Split-Path -Parent $launcher) -WindowStyle Hidden -PassThru
        Write-OperationEvent 'NAPCAT_LAUNCH_REQUESTED'
    }
    foreach($worker in @(@{name='RSSHUB';port=1200;script='start-rsshub.ps1'},@{name='JAVA';port=8080;script='start-app.ps1'})) {
        $external=[bool](Get-NetTCPConnection -LocalPort $worker.port -State Listen -ErrorAction SilentlyContinue)
        $workers[$worker.name]=@{definition=$worker;process=$null;external=$external;failures=0;nextStart=[DateTimeOffset]::MinValue;startedAt=$null}
        if($external) { Write-OperationEvent ($worker.name+'_EXTERNAL_PROCESS_NOT_OWNED') }
    }
    Write-OperationEvent 'SUPERVISOR_STARTED'
    $previous='';$lastAlert=[DateTimeOffset]::MinValue;$started=[DateTimeOffset]::UtcNow
    while($true) {
        if((Test-Path -LiteralPath $stopFile) -and ([IO.File]::ReadAllText($stopFile).Trim() -eq $session)) { break }
        $now=[DateTimeOffset]::UtcNow
        foreach($name in @('RSSHUB','JAVA')) {
            $worker=$workers[$name]
            if($worker.external) { continue }
            if($worker.process -and $worker.process.HasExited) {
                $worker.failures++;$worker.nextStart=$now.AddSeconds((Get-RestartDelay $worker.failures))
                Write-OperationEvent ($name+'_EXITED_RESTART_BACKOFF')
                $worker.process=$null
            }
            if(-not $worker.process -and $now -ge $worker.nextStart) {
                if(Get-NetTCPConnection -LocalPort $worker.definition.port -State Listen -ErrorAction SilentlyContinue) {
                    $worker.external=$true;Write-OperationEvent ($name+'_PORT_TAKEN_RESTART_ABORTED');continue
                }
                $worker.process=Start-BotWorker $worker.definition.script;$worker.startedAt=$now
                Write-OperationEvent ($name+'_STARTED')
            }
            if($worker.process -and ($now-$worker.startedAt).TotalMinutes -ge 10) { $worker.failures=0 }
        }
        $codes=New-Object 'System.Collections.Generic.List[string]'
        $drive=New-Object IO.DriveInfo([IO.Path]::GetPathRoot($PSScriptRoot))
        if($drive.IsReady -and $drive.AvailableFreeSpace -lt 512MB) { $codes.Add('DISK_SPACE_LOW') }
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
        $public=[ordered]@{revision='windows-supervisor-v1';session=$session;updatedAt=$now.ToString('o');health=$state;javaManaged=(-not $workers.JAVA.external);rsshubManaged=(-not $workers.RSSHUB.external)}
        [IO.File]::WriteAllText(($statusFile+'.tmp'),($public|ConvertTo-Json),(New-Object Text.UTF8Encoding($false)))
        Move-Item -LiteralPath ($statusFile+'.tmp') -Destination $statusFile -Force
        Start-Sleep -Seconds 15
    }
} catch { Write-OperationEvent 'OPERATIONS_CONFIG_OR_PROCESS_ERROR';Show-OperationAlert 'OPERATIONS_CONFIG_ERROR';throw }
finally {
    foreach($worker in $workers.Values) { Stop-OwnedTree $worker.process }
    if('TcgAwake' -as [type]) { $null=[TcgAwake]::SetThreadExecutionState([uint32]2147483648) }
    Write-OperationEvent 'SUPERVISOR_STOPPED'
    if(Test-Path -LiteralPath $statusFile) { Remove-Item -LiteralPath $statusFile -Force }
    if($locked) { $mutex.ReleaseMutex() };$mutex.Dispose()
}
