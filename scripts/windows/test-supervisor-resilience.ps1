param([Parameter(Mandatory=$true)][string]$NodeExecutable,[string]$ResultPath='')
# Real Windows supervisor/process test against local Node HTTP stubs. No real QQ calls.
$ErrorActionPreference='Stop'
$node=(Resolve-Path -LiteralPath $NodeExecutable).Path
# Fail before launching anything when Windows process inspection is unavailable.
$null=Get-CimInstance Win32_Process -Filter ('ProcessId='+$PID) -ErrorAction Stop
$fixture=Join-Path ([IO.Path]::GetTempPath()) ('TCG-Supervisor-Test-'+[Guid]::NewGuid().ToString('N'))
$null=New-Item -ItemType Directory -Path $fixture
Get-ChildItem -LiteralPath $PSScriptRoot -File | Where-Object {$_.Extension -in @('.ps1','.cmd') -or $_.Name -eq 'config.example.env'} | Copy-Item -Destination $fixture
. (Join-Path $PSScriptRoot 'operations-lib.ps1')
foreach($port in @(8080,1200,21837)) { if(Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue) { throw ('Test port occupied; refusing to disturb it: '+$port) } }
$qq=$null;$supervisor=$null;$duplicate=$null
function Wait-For([scriptblock]$Condition,[int]$Seconds,[string]$Name) {
    $deadline=(Get-Date).AddSeconds($Seconds)
    do { if(& $Condition){return};Start-Sleep -Seconds 1 } while((Get-Date) -lt $deadline)
    throw ('Timed out: '+$Name)
}
function Assert-True($Condition,[string]$Name) { if(-not $Condition){throw ('FAILED: '+$Name)} }
function Read-TestText([string]$Path) {
    if(-not (Test-Path -LiteralPath $Path -PathType Leaf)) { return '' }
    $stream=[IO.File]::Open($Path,[IO.FileMode]::Open,[IO.FileAccess]::Read,[IO.FileShare]::ReadWrite)
    $reader=New-Object IO.StreamReader($stream)
    try { return $reader.ReadToEnd() } finally { $reader.Dispose() }
}
try {
    @'
QQ_ENABLED=true
QQ_ADAPTER=llbot
QQ_API_BASE_URL=http://127.0.0.1:21837
QQ_ACCESS_TOKEN=synthetic-api-token
QQ_TEST_GROUP_ID=42
RSSHUB_POLLING_ENABLED=true
TWITTER_AUTH_TOKEN=synthetic-x-token
'@ | Set-Content -LiteralPath (Join-Path $fixture 'config.env') -Encoding ASCII
    '{"qqLauncherPath":"","preventSleep":false,"desktopAlerts":true}' | Set-Content -LiteralPath (Join-Path $fixture 'operations.local.json') -Encoding ASCII
    'offline' | Set-Content -LiteralPath (Join-Path $fixture 'qq-state.txt') -Encoding ASCII
    @'
import http from 'node:http';
import fs from 'node:fs';
const kind=process.argv[2],port=kind==='java'?8080:kind==='rss'?1200:21837;
fs.writeFileSync(kind+'-pid.txt',String(process.pid));
http.createServer((req,res)=>{
  res.setHeader('Content-Type','application/json');
  if(kind==='qq'){
    if(req.url!=='/get_status'){res.statusCode=400;res.end('{}');return;}
    res.end(JSON.stringify({status:'ok',retcode:0,data:{online:fs.readFileSync('qq-state.txt','utf8').trim()==='online'}}));return;
  }
  if(req.url==='/api/status'){res.end(JSON.stringify({storageReady:true,pollingIntervalSeconds:300}));return;}
  res.end(JSON.stringify({pollingEnabled:true,polling:{lastStartedAt:new Date().toISOString(),lastFetchSuccessful:true},sources:[]}));
}).listen(port,'127.0.0.1');
'@ | Set-Content -LiteralPath (Join-Path $fixture 'fixture-service.mjs') -Encoding ASCII
    foreach($definition in @(@{file='start-app.ps1';kind='java'},@{file='start-rsshub.ps1';kind='rss'})) {
        $command='& '''+$node.Replace("'","''")+''' (Join-Path $PSScriptRoot ''fixture-service.mjs'') '''+$definition.kind+''''+[Environment]::NewLine+'exit $LASTEXITCODE'
        [IO.File]::WriteAllText((Join-Path $fixture $definition.file),$command)
    }
    # Only observation cadence/startup grace are accelerated. Production retry delay stays 60 seconds.
    $runPath=Join-Path $fixture 'run-bot.ps1'
    $run=[IO.File]::ReadAllText($runPath).Replace('Start-Sleep -Seconds 15','Start-Sleep -Seconds 1').Replace('TotalSeconds -gt 90','TotalSeconds -gt 1')
    [IO.File]::WriteAllText($runPath,$run)
    @'

function Start-Process {
    [CmdletBinding()]
    param([string]$FilePath,[string[]]$ArgumentList,[string]$WorkingDirectory,[string]$WindowStyle,[switch]$PassThru)
    $arguments=$ArgumentList -join ' '
    if($arguments -match 'operation-alert.ps1') {
        [IO.File]::AppendAllText((Join-Path $PSScriptRoot 'alert-attempts.txt'),"attempt`n")
        throw 'synthetic private alert failure'
    }
    $marker=Join-Path $PSScriptRoot 'java-first-start-failed.txt'
    if($arguments -match 'start-app.ps1' -and -not (Test-Path -LiteralPath $marker)) {
        [IO.File]::WriteAllText($marker,'synthetic first-start failure')
        throw 'synthetic private worker failure'
    }
    Microsoft.PowerShell.Management\Start-Process @PSBoundParameters
}
'@ | Add-Content -LiteralPath (Join-Path $fixture 'operations-lib.ps1') -Encoding ASCII
    $null=New-Item -ItemType Directory -Path (Join-Path $fixture 'logs/operations.log') -Force
    $statusPath=Join-Path $fixture 'operations-status.json'
    $null=New-Item -ItemType Directory -Path ($statusPath+'.tmp')
    $stdout=Join-Path $fixture 'supervisor-output.log';$stderr=Join-Path $fixture 'supervisor-error.log'
    $qq=Microsoft.PowerShell.Management\Start-Process $node -ArgumentList @('fixture-service.mjs','qq') -WorkingDirectory $fixture -WindowStyle Hidden -PassThru
    $supervisor=Microsoft.PowerShell.Management\Start-Process 'powershell.exe' -ArgumentList @('-NoProfile','-ExecutionPolicy','Bypass','-File',('"'+$runPath+'"')) -WorkingDirectory $fixture -WindowStyle Hidden -PassThru -RedirectStandardOutput $stdout -RedirectStandardError $stderr
    Wait-For { (Test-Path (Join-Path $fixture 'rss-pid.txt')) -and (Test-Path (Join-Path $fixture 'java-first-start-failed.txt')) -and ((Read-TestText $stdout) -match 'OPERATIONS_STATUS_WRITE_FAILED') } 30 'initial isolated failures'
    Assert-True (-not $supervisor.HasExited) 'supervisor survives log/status/worker failure'
    $rssPid=[int][IO.File]::ReadAllText((Join-Path $fixture 'rss-pid.txt'))
    Wait-For { Get-NetTCPConnection -LocalPort 1200 -State Listen -ErrorAction SilentlyContinue } 20 'RSS stub remains available'
    Remove-Item -LiteralPath ($statusPath+'.tmp') # Empty directory created by this test only.
    Wait-For { Test-Path -LiteralPath $statusPath -PathType Leaf } 20 'status publication recovers'
    Assert-True (([IO.File]::ReadAllText($statusPath)|ConvertFrom-Json).health -match 'JAVA_RESTART_PENDING') 'pending restart is visible'
    Wait-For { Test-Path (Join-Path $fixture 'java-pid.txt') } 100 'Java retries after real 60-second backoff'
    Assert-True ([int][IO.File]::ReadAllText((Join-Path $fixture 'rss-pid.txt')) -eq $rssPid) 'unrelated RSS worker was not restarted'
    Assert-True (-not $supervisor.HasExited) 'supervisor survives alert launch failures'
    'online' | Set-Content -LiteralPath (Join-Path $fixture 'qq-state.txt') -Encoding ASCII
    Wait-For { ([IO.File]::ReadAllText($statusPath)|ConvertFrom-Json).health -eq 'ALL_HEALTHY' } 20 'QQ/Java health recovers'
    Assert-True (Test-Path (Join-Path $fixture 'alert-attempts.txt')) 'alert failure actually injected'
    Assert-True ((Read-TestText $stdout) -match 'OPERATIONS_ALERT_START_FAILED') 'safe alert error reported'
    Assert-True (((Read-TestText $stdout)+(Read-TestText $stderr)) -notmatch 'synthetic private') 'private failure text not logged'
    $duplicate=Microsoft.PowerShell.Management\Start-Process 'powershell.exe' -ArgumentList @('-NoProfile','-ExecutionPolicy','Bypass','-File',('"'+$runPath+'"')) -WorkingDirectory $fixture -WindowStyle Hidden -PassThru -RedirectStandardError (Join-Path $fixture 'duplicate-error.log')
    Wait-For {$duplicate.HasExited} 20 'duplicate supervisor rejection'
    Assert-True ($duplicate.ExitCode -ne 0) 'single supervisor ownership preserved'
    $snapshot=[IO.File]::ReadAllText($statusPath)|ConvertFrom-Json
    [IO.File]::WriteAllText((Join-Path $fixture 'operations.stop'),$snapshot.session)
    Wait-For {$supervisor.HasExited} 30 'intentional scoped shutdown'
    foreach($port in @(8080,1200)) { Assert-True (-not (Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue)) 'owned stub stopped' }
    Assert-True (-not $qq.HasExited) 'external QQ stub not stopped'
    $result=@{powershell=$PSVersionTable.PSVersion.ToString();logFailureSurvived=$true;statusFailureSurvived=$true;alertFailureSurvived=$true;startRetrySeconds=60;unrelatedWorkerSurvived=$true;statusRecovered=$true;singletonPreserved=$true;ownedShutdownVerified=$true;realQqSent=$false}
    if($ResultPath) { [IO.File]::WriteAllText($ResultPath,($result|ConvertTo-Json));Copy-Item -LiteralPath $stdout -Destination ($ResultPath+'.stdout.log');Copy-Item -LiteralPath $stderr -Destination ($ResultPath+'.stderr.log') }
    Write-Host 'PASS: real supervisor survives injected failures, retries with backoff, retains unrelated worker, recovers status, rejects duplicates and stops only owned workers.'
} finally {
    foreach($process in @($supervisor,$qq,$duplicate)) {
        try { Stop-OwnedTree $process }
        catch { Write-Warning 'Test-owned process cleanup failed; inspect the test fixture before rerunning.' }
    }
    $resolved=[IO.Path]::GetFullPath($fixture)
    if((Split-Path -Parent $resolved) -eq ([IO.Path]::GetTempPath().TrimEnd('\')) -and (Split-Path -Leaf $resolved) -match '^TCG-Supervisor-Test-[a-f0-9]{32}$') { Remove-Item -LiteralPath $resolved -Recurse -Force }
}
