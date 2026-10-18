param([Parameter(Mandatory=$true)][string]$NodeExecutable,[string]$ResultPath='')
# Real child processes and local HTTP only. No NapCat installation or QQ login is used.
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'operations-lib.ps1')
. (Join-Path $PSScriptRoot 'napcat-recovery.ps1')
$fixture=Join-Path ([IO.Path]::GetTempPath()) ('TCG-Recovery-Process-Test-'+[Guid]::NewGuid().ToString('N'))
$null=New-Item -ItemType Directory -Path $fixture
$directory=Join-Path $fixture 'NapCat 中文 空格';$null=New-Item -ItemType Directory -Path $directory
$state=$null
function Assert-True($Value,[string]$Name){if(-not $Value){throw ('FAILED: '+$Name)}}
function Wait-For([scriptblock]$Condition,[string]$Name) {
    $deadline=(Get-Date).AddSeconds(20)
    do{if(& $Condition){return};Start-Sleep -Milliseconds 250}while((Get-Date) -lt $deadline)
    throw ('Timed out: '+$Name)
}
try {
    Copy-Item -LiteralPath (Resolve-Path -LiteralPath $NodeExecutable).Path -Destination (Join-Path $directory 'node.exe')
    foreach($name in @('operations-lib.ps1','port-check.ps1','napcat-recovery.ps1','run-managed-napcat.ps1')){Copy-Item (Join-Path $PSScriptRoot $name) $fixture}
    $listener=New-Object Net.Sockets.TcpListener([Net.IPAddress]::Loopback,0);$listener.Start();$port=$listener.LocalEndpoint.Port;$listener.Stop()
    [IO.File]::WriteAllText((Join-Path $directory 'port.txt'),[string]$port)
    [IO.File]::WriteAllText((Join-Path $directory 'state.txt'),'offline')
    @'
import http from 'node:http';
import fs from 'node:fs';
import assert from 'node:assert/strict';
assert.deepEqual(process.argv.slice(2),['-q','123456789']);
assert.equal(process.env.NAPCAT_QUICK_ACCOUNT,'123456789');
for(const key of ['QQ_ACCESS_TOKEN','QQ_COMMAND_SECRET','TWITTER_AUTH_TOKEN','NAPCAT_QUICK_PASSWORD','NAPCAT_QUICK_PASSWORD_MD5'])assert.equal(process.env[key],undefined);
console.log('WebUi Token: synthetic-private-webui');console.error('synthetic-private-stderr');
http.createServer((req,res)=>{res.setHeader('Content-Type','application/json');res.end(JSON.stringify({status:'ok',retcode:0,data:{online:fs.readFileSync('state.txt','utf8')==='online'}}));}).listen(Number(fs.readFileSync('port.txt','utf8')),'127.0.0.1',()=>fs.writeFileSync('pid.txt',String(process.pid)));
'@ | Set-Content -LiteralPath (Join-Path $directory 'index.js') -Encoding ASCII
    [IO.File]::WriteAllText((Join-Path $directory 'package.json'),'{"type":"module"}')
    $base=[Uri]('http://127.0.0.1:'+$port);$now=[DateTimeOffset]::UtcNow
    # Use the copied production function so the launched helper belongs to this fixture.
    . (Join-Path $fixture 'napcat-recovery.ps1')
    $env:QQ_ACCESS_TOKEN='synthetic-private-api';$env:QQ_COMMAND_SECRET='synthetic-private-command';$env:TWITTER_AUTH_TOKEN='synthetic-private-x'
    $state=New-NapCatRecoveryState @{napcatAutoRecover=$true;napcatDirectory=$directory;napcatQuickLoginAccount='123456789'} 'napcat'
    Update-NapCatRecovery $state 'QQ_UNAVAILABLE' $base $now
    Wait-For {(Get-QqHealth $base @{}) -eq 'QQ_OFFLINE_RELOGIN_REQUIRED'} 'managed child HTTP startup'
    $first=[int][IO.File]::ReadAllText((Join-Path $directory 'pid.txt'))
    [IO.File]::WriteAllText((Join-Path $directory 'state.txt'),'online')
    Update-NapCatRecovery $state (Get-QqHealth $base @{}) $base $now.AddSeconds(1)
    Update-NapCatRecovery $state 'QQ_OK' $base $now.AddSeconds(601)
    Assert-True ($state.attempts -eq 0) 'stable connection resets budget'
    [IO.File]::WriteAllText((Join-Path $directory 'state.txt'),'offline')
    Update-NapCatRecovery $state (Get-QqHealth $base @{}) $base $now.AddSeconds(602)
    Wait-For {
        Update-NapCatRecovery $state (Get-QqHealth $base @{}) $base $now.AddSeconds(723)
        $next=[int][IO.File]::ReadAllText((Join-Path $directory 'pid.txt'))
        return $next -ne $first -and (Get-QqHealth $base @{}) -eq 'QQ_OFFLINE_RELOGIN_REQUIRED'
    } 'offline managed process is restarted'
    $second=[int][IO.File]::ReadAllText((Join-Path $directory 'pid.txt'))
    $fresh=New-NapCatRecoveryState @{napcatAutoRecover=$true;napcatDirectory=$directory;napcatQuickLoginAccount='123456789'} 'napcat'
    Update-NapCatRecovery $fresh 'QQ_UNAVAILABLE' $base $now
    Assert-True ($fresh.code -eq 'EXTERNAL_INSTANCE' -and $fresh.attempts -eq 0) 'matching external node is not duplicated'
    $child=Get-Process -Id $second -ErrorAction Stop
    if($child.Path -ne (Join-Path $directory 'node.exe')){throw 'Unexpected process path in test fixture'}
    Stop-Process -InputObject $child -Force
    Wait-For {$state.process.HasExited} 'test-owned crash simulation'
    $log=[IO.File]::ReadAllText((Join-Path $fixture 'logs/operations.log'))
    Assert-True ($log -match 'NAPCAT_MANAGED_EXIT_CODE_') 'native process exit code is logged'
    Assert-True ($log -notmatch 'synthetic-private|123456789') 'raw token/login output is not copied to bot logs'
    Wait-For {
        Update-NapCatRecovery $state (Get-QqHealth $base @{}) $base $now.AddSeconds(1024)
        $next=[int][IO.File]::ReadAllText((Join-Path $directory 'pid.txt'))
        return $next -ne $second -and (Get-QqHealth $base @{}) -eq 'QQ_OFFLINE_RELOGIN_REQUIRED'
    } 'crashed managed process recovers'
    $result=@{realQqSent=$false;chineseAndSpacePath=$true;quickLoginArguments=$true;secretEnvironmentRemoved=$true;offlineRestart=$true;crashRestart=$true;externalInstanceProtected=$true}
    if($ResultPath){[IO.File]::WriteAllText($ResultPath,($result|ConvertTo-Json))}
    Write-Host 'PASS: real child startup, quick-login parameters, secret stripping, Chinese/space paths, offline/crash recovery and external process protection.'
}finally{
    if($state -and $state.process){Stop-OwnedTree $state.process}
    foreach($name in @('QQ_ACCESS_TOKEN','QQ_COMMAND_SECRET','TWITTER_AUTH_TOKEN')){Remove-Item -LiteralPath ('Env:'+$name) -ErrorAction SilentlyContinue}
    $resolved=[IO.Path]::GetFullPath($fixture)
    if((Split-Path -Parent $resolved) -eq ([IO.Path]::GetTempPath().TrimEnd('\')) -and (Split-Path -Leaf $resolved) -match '^TCG-Recovery-Process-Test-[a-f0-9]{32}$'){Remove-Item -LiteralPath $resolved -Recurse -Force}
}
