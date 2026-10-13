$ErrorActionPreference='Stop'
$launcher=$env:TCG_QQ_LAUNCHER
if(-not $launcher -or -not [IO.Path]::IsPathRooted($launcher) -or -not (Test-Path -LiteralPath $launcher -PathType Leaf) -or [IO.Path]::GetExtension($launcher) -notin @('.bat','.cmd','.exe')) { throw 'Set an existing absolute QQ adapter launcher path.' }
Set-Location -LiteralPath (Split-Path -Parent $launcher)
& $launcher
