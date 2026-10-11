$ErrorActionPreference='Stop'
$launcher=$env:TCG_NAPCAT_LAUNCHER
if(-not $launcher -or -not (Test-Path -LiteralPath $launcher -PathType Leaf)) { throw 'NapCat launcher not found.' }
Set-Location -LiteralPath (Split-Path -Parent $launcher)
& $launcher
