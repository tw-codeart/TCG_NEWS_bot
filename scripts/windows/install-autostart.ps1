param([switch]$Remove)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'operations-lib.ps1')
$name='TCGNewsBot-'+(Get-BotInstanceKey $PSScriptRoot)
if($Remove) {
    Unregister-ScheduledTask -TaskName $name -Confirm:$false -ErrorAction SilentlyContinue
    Write-Host 'Removed this folder logon task. No application data was deleted.'
    exit
}
$settingsFile=Join-Path $PSScriptRoot 'operations.local.json'
if(-not (Test-Path -LiteralPath $settingsFile)) {
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'operations.example.json') -Destination $settingsFile
    Write-Host 'Created operations.local.json. Optionally set napcatLauncherPath to your existing launcher (use / in Windows JSON paths).'
}
$user=[Security.Principal.WindowsIdentity]::GetCurrent().Name
$action=New-ScheduledTaskAction -Execute 'powershell.exe' -Argument ('-NoProfile -ExecutionPolicy Bypass -File "'+(Join-Path $PSScriptRoot 'run-bot.ps1')+'"') -WorkingDirectory $PSScriptRoot
$trigger=New-ScheduledTaskTrigger -AtLogOn -User $user
$principal=New-ScheduledTaskPrincipal -UserId $user -LogonType Interactive -RunLevel Limited
$settings=New-ScheduledTaskSettingsSet -MultipleInstances IgnoreNew -RestartCount 3 -RestartInterval (New-TimeSpan -Minutes 1) -ExecutionTimeLimit ([TimeSpan]::Zero) -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries
$null=Register-ScheduledTask -TaskName $name -Action $action -Trigger $trigger -Principal $principal -Settings $settings -Force
Write-Host 'Installed current-user logon task. It does not start before Windows login and never stores a Windows password.'
Write-Host 'For immediate startup use start-bot.cmd. Do not also run start-auto.cmd/start-rsshub.cmd.'
