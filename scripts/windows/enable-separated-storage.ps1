. (Join-Path $PSScriptRoot 'common.ps1')
Assert-FreePort 8080
$lines = @([System.IO.File]::ReadAllLines($configFile))
$found = $false
for ($i=0; $i -lt $lines.Count; $i++) {
    if ($lines[$i] -match '^\s*GAME_STORAGE_SEPARATED_ENABLED\s*=') {
        $lines[$i] = 'GAME_STORAGE_SEPARATED_ENABLED=true'; $found = $true
    }
}
if (-not $found) { $lines += 'GAME_STORAGE_SEPARATED_ENABLED=true' }
[System.IO.File]::WriteAllLines($configFile,$lines,(New-Object System.Text.UTF8Encoding($false)))
Write-Host 'Separated storage enabled. On next Java startup, legacy data will be backed up and copied by game.'
Write-Host 'Keep the entire data folder, including legacy tcg-news.mv.db, ws-news.mv.db, ocg-news.mv.db and backups.'
Write-Host 'After migration, do not run an older JAR or turn separated storage off against this data folder.'
